import Constants from 'expo-constants';

import {
  clearStoredSession,
  loadStoredCredentials,
  type StoredCredentials,
  updateStoredCredentials,
} from '@/auth/session-storage';
import { shouldRefreshAfterUnauthorized } from '@/auth/auth-refresh-policy';
import {
  apiEndpointsFor,
  type ApiEnvironment,
} from '@/config/public-endpoints';
import {
  changeApiEnvironment,
  getCurrentApiEnvironment,
  initializeApiEnvironment,
  updateApiEnvironmentSettings as persistApiEnvironmentSettings,
} from '@/config/api-environment';
import { currentAppTelemetryNetworkType, recordAppApiTelemetry } from '@/observability/telemetry';
import { createTraceContext } from '@/observability/trace-context';
import { buildSignedRequestHeaders } from '@/security/request-signature';
import { ATTACHMENT_TOTAL_LIMIT_MESSAGE } from '@/utils/attachment-policy';

const FAILOVER_STICKY_MS = 10 * 60 * 1000;
const MOBILE_USER_AGENT = 'e-report-app/1.0 Android';
const NETWORK_UNAVAILABLE_MESSAGE = '网络连接失败，请确认设备已连接可访问互联网的无线网络或蜂窝网络，然后重试。';
const FAILOVER_RETRY_MESSAGE = '主线路暂时不可用，系统已切换到备用线路，请重新提交。';

export { FALLBACK_API_BASE_URL, PRIMARY_API_BASE_URL } from '@/config/public-endpoints';

let cachedCredentials: StoredCredentials | null | undefined;
let refreshPromise: Promise<StoredCredentials> | null = null;
let fallbackUntil = 0;

type QueryValue = string | number | boolean | undefined | null;

export type QueryParams = Record<string, QueryValue | QueryValue[]>;

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly payload?: unknown,
    public readonly requestId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type ApiFetchOptions = RequestInit & {
  query?: QueryParams;
  skipAuth?: boolean;
  timeoutMs?: number;
  telemetryExpectedStatuses?: readonly number[];
};

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const response = await rawApiFetch(path, options);
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export async function rawApiFetch(path: string, options: ApiFetchOptions = {}): Promise<Response> {
  const telemetryStartedAt = Date.now();
  const telemetryHeaders = new Headers(options.headers);
  const telemetryRequestId = telemetryHeaders.get('X-Request-Id') || createRequestId();
  const telemetryTraceContext = createTraceContext();
  telemetryHeaders.set('X-Request-Id', telemetryRequestId);
  telemetryHeaders.set('traceparent', telemetryTraceContext.traceparent);
  try {
    const response = await rawApiFetchInternal(path, { ...options, headers: telemetryHeaders });
    recordAppApiTelemetry({
      method: String(options.method || 'GET'),
      path,
      status: response.status,
      durationMs: Date.now() - telemetryStartedAt,
      requestId: telemetryRequestId,
      ...telemetryTraceContext,
      expectedStatuses: options.telemetryExpectedStatuses,
    });
    return response;
  } catch (error) {
    recordAppApiTelemetry({
      method: String(options.method || 'GET'),
      path,
      status: error instanceof ApiError ? error.status : 0,
      durationMs: Date.now() - telemetryStartedAt,
      requestId: error instanceof ApiError ? error.requestId || telemetryRequestId : telemetryRequestId,
      ...telemetryTraceContext,
      error,
      expectedStatuses: options.telemetryExpectedStatuses,
    });
    throw error;
  }
}

async function rawApiFetchInternal(path: string, options: ApiFetchOptions = {}): Promise<Response> {
  const environment = await initializeApiEnvironment();
  const endpoints = apiEndpointsFor(environment);
  const { query, skipAuth, headers, signal, timeoutMs, telemetryExpectedStatuses: _telemetryExpectedStatuses, ...requestOptions } = options;
  const credentials = skipAuth ? null : await getCachedCredentials();
  const nextHeaders = new Headers(headers);
  if (!nextHeaders.has('Accept')) {
    nextHeaders.set('Accept', 'application/json');
  }
  if (!nextHeaders.has('User-Agent')) {
    nextHeaders.set('User-Agent', MOBILE_USER_AGENT);
  }
  const requestId = nextHeaders.get('X-Request-Id') || createRequestId();
  nextHeaders.set('X-Request-Id', requestId);
  nextHeaders.set('X-Client-Platform', 'ANDROID');
  nextHeaders.set('X-Client-App-Version', Constants.nativeAppVersion || Constants.expoConfig?.version || '');
  nextHeaders.set('X-Client-App-Build', Constants.nativeBuildVersion || String(Constants.expoConfig?.android?.versionCode || ''));
  nextHeaders.set('X-Client-Network-Type', currentAppTelemetryNetworkType());
  const method = String(requestOptions.method || 'GET').toUpperCase();
  if (['POST', 'PATCH'].includes(method) && !nextHeaders.has('Idempotency-Key')) {
    nextHeaders.set('Idempotency-Key', requestId);
  }
  if (requestOptions.body && typeof requestOptions.body === 'string' && !nextHeaders.has('Content-Type')) {
    nextHeaders.set('Content-Type', 'application/json');
  }

  const preferredBaseUrl = selectPreferredBaseUrl(endpoints.apiBaseUrl, endpoints.fallbackApiBaseUrl);

  try {
    const response = await executeWithRefresh(
      path, query, requestOptions, nextHeaders, signal, timeoutMs, preferredBaseUrl, credentials,
    );
    markLineHealthy(preferredBaseUrl, endpoints.apiBaseUrl);
    if (!response.ok) {
      throw await toApiError(response, requestId, path);
    }
    return response;
  } catch (error) {
    if (shouldFailover(
      error, path, method, preferredBaseUrl, endpoints.apiBaseUrl, endpoints.fallbackApiBaseUrl,
    )) {
      try {
        const fallbackResponse = await executeWithRefresh(
          path, query, requestOptions, nextHeaders, signal, timeoutMs, endpoints.fallbackApiBaseUrl, credentials,
        );
        markFallbackHealthy();
        if (!fallbackResponse.ok) {
          throw await toApiError(fallbackResponse, requestId, path);
        }
        return fallbackResponse;
      } catch (fallbackError) {
        throw combineFailoverError(error, fallbackError, requestId);
      }
    }
    if (isUnsafeNetworkFailure(
      error, preferredBaseUrl, endpoints.apiBaseUrl, endpoints.fallbackApiBaseUrl,
    )) {
      markFallbackHealthy();
      throw new ApiError(FAILOVER_RETRY_MESSAGE, 0, errorPayload(error), requestId);
    }
    throw error;
  }
}

async function executeFetch(
  path: string,
  query: QueryParams | undefined,
  requestOptions: RequestInit,
  headers: Headers,
  signal: AbortSignal | null | undefined,
  timeoutMs: number | undefined,
  baseUrl: string,
  credentials: StoredCredentials | null,
) {
  const requestAbort = createRequestAbort(signal, timeoutMs);
  const url = buildUrlWithBaseUrl(path, query, baseUrl);
  let requestHeaders = new Headers(headers);
  const method = String(requestOptions.method || 'GET').toUpperCase();
  if (credentials) {
    requestHeaders.set('Authorization', `Bearer ${credentials.token}`);
    requestHeaders = await buildSignedRequestHeaders({
      url,
      method,
      body: requestOptions.body,
      sessionId: credentials.sessionId,
      headers: requestHeaders,
    });
  }
  try {
    return await fetch(url, {
      ...requestOptions,
      headers: requestHeaders,
      signal: requestAbort.signal,
    });
  } catch (error) {
    const message = requestAbort.didTimeout()
      ? timeoutFriendlyMessage(timeoutMs)
      : toFriendlyErrorMessage(error instanceof Error ? error.message : '', NETWORK_UNAVAILABLE_MESSAGE);
    throw new ApiError(message, 0, { ...errorPayload(error), timeoutMs, baseUrl }, requestHeaders.get('X-Request-Id') || undefined);
  } finally {
    requestAbort.cleanup();
  }
}

async function executeWithRefresh(
  path: string,
  query: QueryParams | undefined,
  requestOptions: RequestInit,
  headers: Headers,
  signal: AbortSignal | null | undefined,
  timeoutMs: number | undefined,
  baseUrl: string,
  credentials: StoredCredentials | null,
) {
  let activeCredentials = credentials ? await getCachedCredentials() : null;
  let response = await executeFetch(
    path, query, requestOptions, headers, signal, timeoutMs, baseUrl, activeCredentials,
  );
  if (response.status === 401 && shouldRefreshAfterUnauthorized(path, Boolean(activeCredentials))) {
    if (!activeCredentials) return response;
    activeCredentials = await refreshAuthCredentials(activeCredentials);
    response = await executeFetch(
      path, query, requestOptions, headers, signal, timeoutMs, baseUrl, activeCredentials,
    );
  }
  return response;
}

export function setApiAuthCredentials(credentials: StoredCredentials | null) {
  cachedCredentials = credentials;
}

async function getCachedCredentials() {
  if (cachedCredentials !== undefined) {
    return cachedCredentials;
  }
  cachedCredentials = await loadStoredCredentials();
  return cachedCredentials;
}

async function refreshAuthCredentials(current: StoredCredentials): Promise<StoredCredentials> {
  if (!refreshPromise) {
    refreshPromise = performRefresh(current).finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function performRefresh(current: StoredCredentials): Promise<StoredCredentials> {
  const url = buildUrlWithBaseUrl('/auth/refresh', undefined, selectPreferredBaseUrl());
  const requestId = createRequestId();
  const body = JSON.stringify({ refreshToken: current.refreshToken });
  const traceContext = createTraceContext();
  let headers = new Headers({
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Request-Id': requestId,
    'X-Client-Platform': 'ANDROID',
    'X-Client-App-Version': Constants.nativeAppVersion || Constants.expoConfig?.version || '',
    'X-Client-App-Build': Constants.nativeBuildVersion || String(Constants.expoConfig?.android?.versionCode || ''),
    traceparent: traceContext.traceparent,
  });
  headers = await buildSignedRequestHeaders({
    url,
    method: 'POST',
    body,
    sessionId: current.sessionId,
    headers,
  });
  let rotationMayHaveCompleted = false;
  try {
    const response = await fetch(url, { method: 'POST', headers, body });
    if (!response.ok) {
      throw await toApiError(response, requestId, '/auth/refresh');
    }
    rotationMayHaveCompleted = true;
    const payload = await response.json() as Partial<StoredCredentials>;
    if (!payload.token || !payload.refreshToken || payload.sessionId !== current.sessionId) {
      throw new ApiError('刷新登录状态返回数据不完整。', 502, payload, requestId);
    }
    const next: StoredCredentials = {
      token: payload.token,
      refreshToken: payload.refreshToken,
      sessionId: payload.sessionId,
    };
    await updateStoredCredentials(next);
    cachedCredentials = next;
    return next;
  } catch (error) {
    if (rotationMayHaveCompleted || shouldClearSessionAfterRefreshFailure(error)) {
      cachedCredentials = null;
      await clearStoredSession();
    }
    throw error;
  }
}

function shouldClearSessionAfterRefreshFailure(error: unknown) {
  return error instanceof ApiError && [400, 401, 403, 409].includes(error.status);
}

export function buildUrl(path: string, query?: QueryParams): string {
  return buildUrlWithBaseUrl(path, query, apiEndpointsFor(getCurrentApiEnvironment()).apiBaseUrl);
}

export function buildActiveUrl(path: string, query?: QueryParams): string {
  return buildUrlWithBaseUrl(path, query, selectPreferredBaseUrl());
}

export function buildFallbackUrlForPrimaryUrl(url: string): string | null {
  const endpoints = apiEndpointsFor(getCurrentApiEnvironment());
  if (!endpoints.fallbackApiBaseUrl || endpoints.fallbackApiBaseUrl === endpoints.apiBaseUrl) {
    return null;
  }
  if (url.startsWith(endpoints.apiBaseUrl)) {
    return `${endpoints.fallbackApiBaseUrl}${url.slice(endpoints.apiBaseUrl.length)}`;
  }
  const primaryOrigin = originOf(endpoints.apiBaseUrl);
  const fallbackOrigin = originOf(endpoints.fallbackApiBaseUrl);
  if (primaryOrigin && fallbackOrigin && url.startsWith(primaryOrigin)) {
    return `${fallbackOrigin}${url.slice(primaryOrigin.length)}`;
  }
  return null;
}

export function currentApiBaseUrl() {
  const endpoints = apiEndpointsFor(getCurrentApiEnvironment());
  return selectPreferredBaseUrl(endpoints.apiBaseUrl, endpoints.fallbackApiBaseUrl);
}

export function currentApiEndpoints() {
  return apiEndpointsFor(getCurrentApiEnvironment());
}

export async function switchApiEnvironment(environment: ApiEnvironment) {
  cachedCredentials = null;
  refreshPromise = null;
  fallbackUntil = 0;
  return changeApiEnvironment(environment);
}

export async function saveApiEnvironmentSettings(
  environment: ApiEnvironment,
  apiBaseUrlOverrides: Partial<Record<ApiEnvironment, string>>,
) {
  cachedCredentials = null;
  refreshPromise = null;
  fallbackUntil = 0;
  const changed = await persistApiEnvironmentSettings(environment, apiBaseUrlOverrides);
  return changed;
}

function buildUrlWithBaseUrl(path: string, query: QueryParams | undefined, baseUrl: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const url = new URL(`${baseUrl}${normalizedPath}`);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      const values = Array.isArray(value) ? value : [value];
      values.forEach((item) => {
        if (item !== undefined && item !== null && item !== '') {
          url.searchParams.append(key, String(item));
        }
      });
    });
  }
  return url.toString();
}

function selectPreferredBaseUrl(
  primaryApiBaseUrl = apiEndpointsFor(getCurrentApiEnvironment()).apiBaseUrl,
  fallbackApiBaseUrl = apiEndpointsFor(getCurrentApiEnvironment()).fallbackApiBaseUrl,
) {
  if (Date.now() < fallbackUntil && fallbackApiBaseUrl) {
    return fallbackApiBaseUrl;
  }
  return primaryApiBaseUrl;
}

function shouldFailover(
  error: unknown,
  path: string,
  method: string,
  baseUrl: string,
  primaryApiBaseUrl: string,
  fallbackApiBaseUrl: string,
) {
  return baseUrl === primaryApiBaseUrl
    && isFailoverConfigured(primaryApiBaseUrl, fallbackApiBaseUrl)
    && isNetworkApiError(error)
    && isFailoverSafeRequest(path, method);
}

function isFailoverConfigured(primaryApiBaseUrl: string, fallbackApiBaseUrl: string) {
  return Boolean(fallbackApiBaseUrl && fallbackApiBaseUrl !== primaryApiBaseUrl);
}

function isFailoverSafeRequest(path: string, method: string) {
  if (['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    return true;
  }
  return method === 'PUT' && path.startsWith('/file-transfers/uploads/') && path.includes('/chunks/');
}

function isUnsafeNetworkFailure(
  error: unknown,
  baseUrl: string,
  primaryApiBaseUrl: string,
  fallbackApiBaseUrl: string,
) {
  return baseUrl === primaryApiBaseUrl
    && isFailoverConfigured(primaryApiBaseUrl, fallbackApiBaseUrl)
    && isNetworkApiError(error);
}

function isNetworkApiError(error: unknown) {
  return error instanceof ApiError && error.status === 0;
}

function markLineHealthy(baseUrl: string, primaryApiBaseUrl: string) {
  if (baseUrl === primaryApiBaseUrl) {
    fallbackUntil = 0;
  }
}

function markFallbackHealthy() {
  fallbackUntil = Date.now() + FAILOVER_STICKY_MS;
}

function combineFailoverError(primaryError: unknown, fallbackError: unknown, requestId: string) {
  if (fallbackError instanceof ApiError) {
    return new ApiError(fallbackError.message, fallbackError.status, {
      primary: errorPayload(primaryError),
      fallback: fallbackError.payload,
    }, fallbackError.requestId || requestId);
  }
  return new ApiError(NETWORK_UNAVAILABLE_MESSAGE, 0, {
    primary: errorPayload(primaryError),
    fallback: errorPayload(fallbackError),
  }, requestId);
}

function errorPayload(error: unknown) {
  if (error instanceof ApiError) {
    return { message: error.message, status: error.status, payload: error.payload, requestId: error.requestId };
  }
  if (error instanceof Error) {
    return { message: error.message };
  }
  return { message: String(error || '') };
}

function originOf(url: string) {
  try {
    return new URL(url).origin;
  } catch {
    return '';
  }
}

async function toApiError(response: Response, requestId: string, path: string): Promise<ApiError> {
  const text = await response.text();
  let payload: unknown = text;
  let message = userFriendlyStatusMessage(response.status, text, path);
  try {
    payload = text ? JSON.parse(text) : undefined;
    if (payload && typeof payload === 'object' && 'message' in payload && typeof payload.message === 'string') {
      message = payload.message;
    } else {
      const fieldErrors = formatPayloadErrors(payload);
      if (fieldErrors) {
        message = fieldErrors;
      }
    }
  } catch {
    const title = extractHtmlTitle(text);
    if (title) {
      message = userFriendlyStatusMessage(response.status, title, path);
    }
  }
  return new ApiError(toFriendlyErrorMessage(message, userFriendlyStatusMessage(response.status, '', path), response.status, path), response.status, payload, requestId);
}

export function formatApiErrorMessage(error: unknown, fallback = '操作失败，请稍后重试。') {
  if (error instanceof ApiError) {
    const traceId = extractTraceId(error.payload) || error.requestId;
    const suffix = traceId ? `\n错误编号：${traceId}` : '';
    return `${toFriendlyErrorMessage(error.message, fallback, error.status)}${suffix}`;
  }
  if (error instanceof Error && error.message) {
    return toFriendlyErrorMessage(error.message, fallback);
  }
  return fallback;
}

function createRequestId() {
  const random = Math.random().toString(36).slice(2, 10);
  return `ereport-app-${Date.now()}-${random}`;
}

function createRequestAbort(externalSignal: AbortSignal | null | undefined, timeoutMs: number | undefined) {
  const controller = new AbortController();
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  let timeoutReached = false;
  const abortFromExternal = () => controller.abort();

  if (externalSignal?.aborted) {
    controller.abort();
  } else {
    externalSignal?.addEventListener('abort', abortFromExternal, { once: true });
  }

  if (timeoutMs && timeoutMs > 0) {
    timeoutId = setTimeout(() => {
      timeoutReached = true;
      controller.abort();
    }, timeoutMs);
  }

  return {
    signal: controller.signal,
    didTimeout: () => timeoutReached,
    cleanup: () => {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      externalSignal?.removeEventListener('abort', abortFromExternal);
    },
  };
}

function timeoutFriendlyMessage(timeoutMs: number | undefined) {
  const seconds = timeoutMs && timeoutMs > 0 ? Math.round(timeoutMs / 1000) : 0;
  if (seconds > 0) {
    return `请求等待超过${seconds}秒，请检查网络信号后重试。`;
  }
  return '请求等待时间过长，请检查网络信号后重试。';
}

function extractTraceId(payload: unknown) {
  if (payload && typeof payload === 'object' && 'traceId' in payload && typeof payload.traceId === 'string') {
    return payload.traceId;
  }
  return '';
}

function extractHtmlTitle(text: string) {
  const match = text.match(/<title>(.*?)<\/title>/i);
  return match?.[1]?.trim() || '';
}

function formatPayloadErrors(payload: unknown) {
  if (!payload || typeof payload !== 'object' || !('errors' in payload) || !payload.errors || typeof payload.errors !== 'object') {
    return '';
  }
  const messages = Object.values(payload.errors as Record<string, unknown>)
    .map((value) => {
      const rawMessage = Array.isArray(value) ? value.join('；') : String(value || '').trim();
      return toFriendlyErrorMessage(rawMessage, '字段填写不正确，请检查后重试。');
    })
    .filter(Boolean);
  return messages.join('\n');
}

function userFriendlyStatusMessage(status: number, rawText: string, path = '') {
  if (status === 413) {
    return ATTACHMENT_TOTAL_LIMIT_MESSAGE;
  }
  if (status === 401) {
    if (isLoginPath(path)) {
      return '用户名、密码或验证码不正确，请重新输入。';
    }
    return '登录状态已失效，请重新登录。';
  }
  if (status === 400) {
    return rawText || '提交内容有误，请检查表单后重试。';
  }
  if (status === 403) {
    return '当前账号没有执行该操作的权限。';
  }
  if (status === 404) {
    return '没有找到对应数据，可能已被删除或没有权限查看。';
  }
  if (status === 408) {
    return '请求超时，请检查网络后重试。';
  }
  if (status === 409) {
    return '当前数据状态已变化，请刷新后重试。';
  }
  if (status === 415) {
    return '当前应用版本过旧，无法使用新版上传接口。请升级到最新版本后重新提交。';
  }
  if (status === 422) {
    return rawText || '提交内容校验失败，请检查表单。';
  }
  if (status === 429) {
    return '操作过于频繁，请稍后再试。';
  }
  if (status >= 500) {
    return '服务器暂时无法处理请求，请稍后重试。';
  }
  return rawText || `请求失败：HTTP ${status}`;
}

function toFriendlyErrorMessage(rawMessage: string, fallback: string, status = 0, path = '') {
  const message = normalizeRawMessage(rawMessage);
  const lowerMessage = message.toLowerCase();

  if (!message) {
    return status ? userFriendlyStatusMessage(status, '', path) : fallback;
  }
  if (lowerMessage.includes('request entity too large') || lowerMessage.includes('413')) {
    return ATTACHMENT_TOTAL_LIMIT_MESSAGE;
  }
  if (lowerMessage.includes('unsupported formdatapart')) {
    return '附件上传失败，请更新应用后重试；如仍失败，请减少附件数量或重新选择图片。';
  }
  if (isTimeoutMessage(lowerMessage)) {
    return '请求超时，请检查网络信号后重试。';
  }
  if (isNetworkMessage(lowerMessage)) {
    return NETWORK_UNAVAILABLE_MESSAGE;
  }
  if (isLoginPath(path) && status === 401) {
    return '用户名、密码或验证码不正确，请重新输入。';
  }
  if (lowerMessage.includes('bad credentials') || lowerMessage.includes('invalid username') || lowerMessage.includes('invalid password')) {
    return '用户名或密码不正确，请重新输入。';
  }
  if (lowerMessage.includes('username cannot be blank')) {
    return '用户名不能为空。';
  }
  if (lowerMessage.includes('username must be between')) {
    return '用户名长度必须在3到50字符之间。';
  }
  if (lowerMessage.includes('password cannot be blank')) {
    return '密码不能为空。';
  }
  if (lowerMessage.includes('password must be between') || lowerMessage.includes('new password must be between')) {
    return '密码长度必须在8到100字符之间。';
  }
  if (lowerMessage.includes('new password cannot be blank')) {
    return '新密码不能为空。';
  }
  if (lowerMessage.includes('name cannot be blank')) {
    return '姓名不能为空。';
  }
  if (lowerMessage.includes('name cannot exceed')) {
    return '姓名不能超过100字符。';
  }
  if (lowerMessage.includes('config key cannot be blank')) {
    return '配置键不能为空。';
  }
  if (lowerMessage.includes('config key cannot exceed')) {
    return '配置键不能超过100字符。';
  }
  if (lowerMessage.includes('permission name cannot be blank')) {
    return '权限名称不能为空。';
  }
  if (lowerMessage.includes('permission name cannot exceed')) {
    return '权限名称不能超过100字符。';
  }
  if (lowerMessage.includes('description cannot exceed')) {
    return '描述不能超过255字符。';
  }
  if (lowerMessage.includes('captcha') || message.includes('验证码')) {
    if (lowerMessage.includes('invalid') || lowerMessage.includes('incorrect') || message.includes('错误') || message.includes('过期')) {
      return '验证码不正确或已过期，请刷新后重新输入。';
    }
  }
  if (lowerMessage.includes('account is disabled') || message.includes('账号已停用')) {
    return '账号已停用，请联系管理员。';
  }
  if (status && isGenericHttpMessage(lowerMessage)) {
    return userFriendlyStatusMessage(status, '', path);
  }
  if (status >= 500) {
    return '服务器暂时无法处理请求，请稍后重试。';
  }
  if (status && isTechnicalMessage(lowerMessage)) {
    return userFriendlyStatusMessage(status, '', path);
  }
  if (isTechnicalMessage(lowerMessage)) {
    return fallback;
  }
  return message;
}

function normalizeRawMessage(rawMessage: string) {
  const trimmed = String(rawMessage || '').trim();
  if (!trimmed) {
    return '';
  }
  const title = extractHtmlTitle(trimmed);
  return title || trimmed.replace(/\s+/g, ' ');
}

function isLoginPath(path: string) {
  return path.includes('/auth/login');
}

function isTimeoutMessage(message: string) {
  return message.includes('timeout')
    || message.includes('timed out')
    || message.includes('aborterror')
    || message.includes('aborted')
    || message.includes('econnaborted')
    || message.includes('etimedout');
}

function isNetworkMessage(message: string) {
  return message.includes('network request failed')
    || message.includes('failed to fetch')
    || message.includes('fetch failed')
    || message.includes('unknownhostexception')
    || message.includes('network is unreachable')
    || message.includes('enotfound')
    || message.includes('econnrefused')
    || message.includes('econnreset')
    || message.includes('err_network')
    || message.includes('internet disconnected');
}

function isTechnicalMessage(message: string) {
  return message.includes('<html')
    || message.includes('</html>')
    || message.includes('exception')
    || message.includes('java.')
    || message.includes('org.springframework')
    || message.includes('nginx/')
    || message.includes('http ')
    || message.includes('typeerror')
    || message.includes('json parse')
    || message.includes('unexpected token')
    || message.includes('request failed')
    || message.includes('failed:');
}

function isGenericHttpMessage(message: string) {
  return message === 'bad request'
    || message === 'unauthorized'
    || message === 'forbidden'
    || message === 'not found'
    || message === 'method not allowed'
    || message === 'unsupported media type'
    || message === 'internal server error'
    || message === 'bad gateway'
    || message === 'service unavailable'
    || message === 'gateway timeout';
}

import NetInfo from '@react-native-community/netinfo';
import Constants from 'expo-constants';
import * as Crypto from 'expo-crypto';
import { AppState, NativeModules, Platform } from 'react-native';

import { loadStoredCredentials } from '@/auth/session-storage';
import { getCurrentApiEnvironment, initializeApiEnvironment, subscribeApiEnvironmentChanged } from '@/config/api-environment';
import { apiEndpointsFor, type ApiEnvironment } from '@/config/public-endpoints';
import { buildSignedRequestHeaders } from '@/security/request-signature';
import { getDeviceIdentity, type DeviceSecurityContext } from '@/security/device-security';
import {
  clearTelemetryQueue,
  deleteTelemetryEvents,
  enqueueTelemetryEvent,
  readTelemetryBatch,
  telemetryQueueSize,
} from './telemetry-queue';
import {
  normalizeTelemetryCode,
  sanitizeTelemetryAttributes,
  sanitizeTelemetryRoute,
  sanitizeTelemetryText,
  sanitizeTelemetryValue,
} from './telemetry-sanitizer';
import { shouldDeleteTelemetryBatch } from './telemetry-delivery-policy';
import { isExpectedApiStatus } from './telemetry-api-policy';
import { shouldSampleOperationSuccess } from './operation-sampling';
import { buildMobileUserAgent } from './resource-attributes';
import { createTraceContext, isValidSpanId, isValidTraceId } from './trace-context';
import type {
  TelemetryBreadcrumb,
  TelemetryEvent,
  TelemetryOutcome,
  TelemetryPolicy,
  TelemetrySeverity,
} from './telemetry-types';

const MAX_BREADCRUMBS = 30;
const DEFAULT_POLICY: TelemetryPolicy = {
  enabled: true,
  warningEnabled: true,
  operationEnabled: true,
  operationSuccessSampleRatePercent: 20,
  batchSize: 20,
  flushIntervalSeconds: 15,
};
let telemetrySessionId = Crypto.randomUUID();

type NativeObservabilityModule = {
  getEnvironmentDiagnostics?(): Promise<Record<string, unknown>>;
  consumeLastCrash?(): Promise<Record<string, unknown> | null>;
};

type CaptureOptions = {
  category?: string;
  eventName?: string;
  message?: string;
  stackTrace?: string;
  route?: string;
  operationName?: string;
  outcome?: TelemetryOutcome;
  traceId?: string;
  spanId?: string;
  parentSpanId?: string;
  traceFlags?: number;
  requestId?: string;
  spanEvents?: TelemetryBreadcrumb[];
  severity?: TelemetrySeverity;
  fatal?: boolean;
  attributes?: Record<string, unknown>;
};

type OperationOptions = {
  category: string;
  eventName: string;
  operationName: string;
  route?: string;
  traceId?: string;
  spanId?: string;
  parentSpanId?: string;
  traceFlags?: number;
  requestId?: string;
  attributes?: Record<string, unknown>;
  forceCapture?: boolean;
};

type OperationStage = {
  occurredAt: string;
  name: string;
  elapsedMs: number;
  attributes: Record<string, unknown>;
};

export type AppTelemetryOperation = {
  traceId: string;
  spanId: string;
  stage(name: string, attributes?: Record<string, unknown>): void;
  success(attributes?: Record<string, unknown>): void;
  failure(error: unknown, attributes?: Record<string, unknown>): void;
};

type ApiTelemetry = {
  method: string;
  path: string;
  status: number;
  durationMs: number;
  requestId?: string;
  traceId?: string;
  spanId?: string;
  traceFlags?: number;
  error?: unknown;
  expectedStatuses?: readonly number[];
};

const NativeObservability = NativeModules.EReportObservability as NativeObservabilityModule | undefined;
let initialized = false;
let flushing = false;
let policy = DEFAULT_POLICY;
let currentRoute = '';
let breadcrumbs: TelemetryBreadcrumb[] = [];
let environmentCache: { expiresAt: number; value: Awaited<ReturnType<typeof collectEnvironment>> } | null = null;
let flushTimer: ReturnType<typeof setInterval> | null = null;
let latestNetworkType = 'unknown';
let policyGeneration = 0;

subscribeApiEnvironmentChanged(() => {
  telemetrySessionId = Crypto.randomUUID();
  currentRoute = '';
  breadcrumbs = [];
  environmentCache = null;
  policy = DEFAULT_POLICY;
  policyGeneration += 1;
  void loadPolicy().then(scheduleFlush);
});

export async function initializeAppTelemetry() {
  if (initialized) return;
  initialized = true;
  installGlobalErrorHandler();
  installConsoleCapture();
  NetInfo.addEventListener((state) => {
    latestNetworkType = state.type;
    if (state.isConnected) void flushAppTelemetry();
  });
  AppState.addEventListener('change', (state) => {
    if (state !== 'active') void flushAppTelemetry();
  });
  await loadPolicy();
  scheduleFlush();
  await capturePreviousNativeCrash();
  await flushAppTelemetry();
}

export function currentAppTelemetryNetworkType() {
  return latestNetworkType;
}

export function setAppTelemetryRoute(route: string) {
  currentRoute = sanitizeTelemetryRoute(route);
  addAppTelemetryBreadcrumb('UI', 'ROUTE_CHANGE', { route: currentRoute });
  recordAppTelemetryOperation({
    category: 'UI',
    eventName: 'ROUTE_VIEW',
    operationName: '打开页面',
    route: currentRoute,
    outcome: 'SUCCESS',
    attributes: { route: currentRoute },
  });
}

export function addAppTelemetryBreadcrumb(
  category: string,
  name: string,
  attributes: Record<string, unknown> = {},
  message = '',
) {
  breadcrumbs = [...breadcrumbs, {
    occurredAt: new Date().toISOString(),
    category: normalizeTelemetryCode(category, 'SYSTEM', 40),
    name: normalizeTelemetryCode(name, 'ACTION', 100),
    message: sanitizeTelemetryText(message, 500),
    attributes: sanitizeTelemetryAttributes(attributes),
  }].slice(-MAX_BREADCRUMBS);
}

export function captureAppTelemetryError(error: unknown, options: CaptureOptions = {}) {
  const normalized = normalizeError(error);
  void enqueue({
    eventType: 'ERROR',
    severity: options.fatal ? 'FATAL' : options.severity || 'ERROR',
    category: options.category || 'SYSTEM',
    eventName: options.eventName || 'UNHANDLED_ERROR',
    message: options.message || normalized.message,
    stackTrace: options.stackTrace || normalized.stack,
    route: options.route,
    operationName: options.operationName,
    outcome: options.outcome || 'FAILURE',
    traceId: options.traceId,
    spanId: options.spanId,
    parentSpanId: options.parentSpanId,
    traceFlags: options.traceFlags,
    requestId: options.requestId,
    attributes: options.attributes,
  });
}

export function captureAppTelemetryWarning(message: string, options: CaptureOptions = {}) {
  if (!policy.warningEnabled) return;
  void enqueue({
    eventType: 'WARNING',
    severity: 'WARN',
    category: options.category || 'SYSTEM',
    eventName: options.eventName || 'CLIENT_WARNING',
    message,
    stackTrace: options.stackTrace,
    route: options.route,
    operationName: options.operationName,
    outcome: options.outcome || 'UNKNOWN',
    traceId: options.traceId,
    spanId: options.spanId,
    parentSpanId: options.parentSpanId,
    traceFlags: options.traceFlags,
    requestId: options.requestId,
    attributes: options.attributes,
  });
}

export function captureAppTelemetryDiagnostic(
  eventName: string,
  attributes: Record<string, unknown>,
  options: CaptureOptions = {},
) {
  void enqueue({
    eventType: 'DIAGNOSTIC',
    severity: options.severity || 'INFO',
    category: options.category || 'SYSTEM',
    eventName,
    message: options.message || '用户主动上传运行诊断。',
    route: options.route,
    operationName: options.operationName,
    outcome: options.outcome || 'UNKNOWN',
    traceId: options.traceId,
    spanId: options.spanId,
    parentSpanId: options.parentSpanId,
    traceFlags: options.traceFlags,
    requestId: options.requestId,
    attributes,
  });
}

export function recordAppTelemetryOperation(input: OperationOptions & {
  outcome: 'SUCCESS' | 'FAILURE';
  message?: string;
}) {
  if (!policy.operationEnabled) return;
  if (input.outcome === 'SUCCESS'
      && !input.forceCapture
      && !shouldSampleOperationSuccess(policy.operationSuccessSampleRatePercent)) {
    return;
  }
  void enqueue({
    eventType: 'OPERATION',
    severity: input.outcome === 'FAILURE' ? 'WARN' : 'INFO',
    category: input.category,
    eventName: input.eventName,
    message: input.message || (input.outcome === 'SUCCESS' ? '操作成功。' : '操作失败。'),
    route: input.route,
    operationName: input.operationName,
    outcome: input.outcome,
    traceId: input.traceId,
    spanId: input.spanId,
    parentSpanId: input.parentSpanId,
    traceFlags: input.traceFlags,
    requestId: input.requestId,
    spanEvents: input.attributes?.stages as TelemetryBreadcrumb[] | undefined,
    attributes: input.attributes,
  });
}

export function startAppTelemetryOperation(options: OperationOptions): AppTelemetryOperation {
  const traceContext = createTraceContext(options.traceId);
  const traceId = traceContext.traceId;
  const spanId = options.spanId && isValidSpanId(options.spanId) ? options.spanId : traceContext.spanId;
  const startedAt = Date.now();
  const stages: OperationStage[] = [];
  let completed = false;
  const complete = (outcome: 'SUCCESS' | 'FAILURE', error?: unknown, attributes: Record<string, unknown> = {}) => {
    if (completed) return;
    completed = true;
    const normalizedError = outcome === 'FAILURE' ? normalizeError(error) : null;
    recordAppTelemetryOperation({
      ...options,
      traceId,
      spanId,
      traceFlags: options.traceFlags ?? traceContext.traceFlags,
      outcome,
      message: normalizedError?.message,
      forceCapture: outcome === 'FAILURE' || options.forceCapture,
      attributes: {
        ...options.attributes,
        ...attributes,
        durationMs: Date.now() - startedAt,
        stages: stages.map((stage) => ({
          occurredAt: stage.occurredAt,
          category: options.category,
          name: stage.name,
          attributes: { elapsedMs: stage.elapsedMs, ...stage.attributes },
        })),
      },
    });
  };
  return {
    traceId,
    spanId,
    stage(name, attributes = {}) {
      if (completed || stages.length >= 20) return;
      stages.push({
        occurredAt: new Date().toISOString(),
        name: normalizeTelemetryCode(name, 'STAGE', 100),
        elapsedMs: Date.now() - startedAt,
        attributes: sanitizeTelemetryAttributes(attributes),
      });
    },
    success(attributes = {}) {
      complete('SUCCESS', undefined, attributes);
    },
    failure(error, attributes = {}) {
      complete('FAILURE', error, attributes);
    },
  };
}

export function recordAppApiTelemetry(input: ApiTelemetry) {
  const method = String(input.method || 'GET').toUpperCase();
  const path = sanitizeTelemetryRoute(input.path);
  addAppTelemetryBreadcrumb('NETWORK', 'API_REQUEST', {
    method,
    path,
    status: input.status,
    durationMs: input.durationMs,
    requestId: input.requestId,
  });
  const expected = isExpectedApiStatus(input.status, input.expectedStatuses);
  const failed = Boolean(input.error) || (input.status >= 400 && !expected);
  recordAppTelemetryOperation({
    category: 'NETWORK',
    eventName: 'HTTP_CLIENT_REQUEST',
    operationName: `${method} ${path}`,
    traceId: input.traceId,
    spanId: input.spanId,
    traceFlags: input.traceFlags,
    requestId: input.requestId,
    outcome: failed ? 'FAILURE' : 'SUCCESS',
    message: failed ? `接口请求失败：HTTP ${input.status}` : '接口请求完成。',
    forceCapture: failed,
    attributes: {
      'http.request.method': method,
      'http.response.status_code': input.status,
      'url.path': path,
      'ereport.request.id': input.requestId,
      durationMs: input.durationMs,
      ...(input.error ? { 'exception.type': normalizeError(input.error).message } : {}),
    },
  });
}

export async function submitManualAppDiagnostic() {
  const apiEnvironment = await initializeApiEnvironment();
  const endpoints = apiEndpointsFor(apiEnvironment);
  const queueBefore = await telemetryQueueSize(apiEnvironment);
  const nativeEnvironment = await NativeObservability?.getEnvironmentDiagnostics?.().catch(() => null);
  const network = await NetInfo.fetch();
  await enqueue({
    eventType: 'DIAGNOSTIC',
    severity: 'INFO',
    category: 'SYSTEM',
    eventName: 'MANUAL_DIAGNOSTIC',
    message: '用户主动上传运行诊断。',
    outcome: 'SUCCESS',
    attributes: {
      queueBefore,
      activeApiLine: endpoints.apiBaseUrl,
      fallbackConfigured: Boolean(
        endpoints.fallbackApiBaseUrl && endpoints.fallbackApiBaseUrl !== endpoints.apiBaseUrl,
      ),
      network: networkDetails(network),
      nativeEnvironment,
    },
  });
  const uploaded = await flushAppTelemetry();
  return { uploaded, queued: await telemetryQueueSize() };
}

export async function flushAppTelemetry() {
  if (flushing || !policy.enabled) return false;
  const apiEnvironment = await initializeApiEnvironment();
  const network = await NetInfo.fetch();
  if (!network.isConnected) return false;
  const events = await readTelemetryBatch(policy.batchSize, apiEnvironment);
  if (events.length === 0) return true;
  flushing = true;
  try {
    const requestId = `ereport-app-telemetry:${Crypto.randomUUID()}`;
    const deviceIdentity = await getDeviceIdentity();
    const environment = await cachedEnvironment();
    const response = await telemetryFetch('/observability/events/batch', {
      method: 'POST',
      headers: await telemetryHeaders(requestId),
      body: JSON.stringify({
        schemaVersion: 2,
        platform: 'ANDROID',
        installationId: deviceIdentity.installationId,
        sessionId: telemetrySessionId,
        environment,
        resource: {
          attributes: appResourceAttributes(environment, deviceIdentity.installationId, apiEnvironment),
        },
        instrumentationScope: {
          name: 'e-report-android.telemetry',
          version: Constants.nativeAppVersion || Constants.expoConfig?.version || '',
          attributes: { 'ereport.telemetry.protocol': 'ereport-otel/1' },
        },
        events,
      }),
    }, apiEnvironment);
    if (shouldDeleteTelemetryBatch(response.status)) {
      await deleteTelemetryEvents(events.map((event) => event.clientEventId), apiEnvironment);
    }
    return response.ok;
  } catch {
    return false;
  } finally {
    flushing = false;
  }
}

async function enqueue(input: Omit<TelemetryEvent, 'clientEventId' | 'occurredAt' | 'attributes' | 'breadcrumbs' | 'route'> & {
  attributes?: Record<string, unknown>;
  route?: string;
}) {
  if (!policy.enabled) return;
  const isSpan = input.eventType === 'OPERATION';
  const generatedContext = isSpan ? createTraceContext(input.traceId) : null;
  const traceId = isValidTraceId(input.traceId) ? input.traceId : generatedContext?.traceId;
  const spanId = isValidSpanId(input.spanId) ? input.spanId : generatedContext?.spanId;
  const sanitizedAttributes = sanitizeTelemetryAttributes({
    ...input.attributes,
    'ereport.event.type': input.eventType,
    'ereport.event.category': input.category,
    'ereport.event.name': input.eventName,
    'ereport.operation.name': input.operationName,
    'ereport.operation.outcome': input.outcome,
    'ereport.request.id': input.requestId,
    ...(input.stackTrace ? {
      'exception.message': input.message,
      'exception.stacktrace': input.stackTrace,
    } : {}),
  });
  const event: TelemetryEvent = {
    ...input,
    clientEventId: `android-event:${Crypto.randomUUID()}`,
    category: normalizeTelemetryCode(input.category, 'SYSTEM', 40),
    eventName: normalizeTelemetryCode(input.eventName, 'CLIENT_EVENT', 100),
    message: sanitizeTelemetryText(input.message, 2000),
    stackTrace: sanitizeTelemetryText(input.stackTrace, 16000),
    route: sanitizeTelemetryRoute(input.route || currentRoute),
    operationName: sanitizeTelemetryText(input.operationName, 120),
    traceId,
    spanId,
    parentSpanId: isValidSpanId(input.parentSpanId) ? input.parentSpanId : undefined,
    traceFlags: input.traceFlags ?? generatedContext?.traceFlags,
    requestId: sanitizeTelemetryText(input.requestId, 100),
    signalType: isSpan ? 'SPAN' : 'LOG',
    severityNumber: severityNumber(input.severity),
    severityText: input.severity,
    body: sanitizeTelemetryText(input.message, 2000),
    spanName: isSpan ? sanitizeTelemetryText(input.operationName || input.eventName, 255) : undefined,
    spanKind: isSpan && input.category === 'NETWORK' ? 'CLIENT' : isSpan ? 'INTERNAL' : undefined,
    spanStatusCode: isSpan ? (input.outcome === 'FAILURE' ? 'ERROR' : input.outcome === 'SUCCESS' ? 'OK' : 'UNSET') : undefined,
    spanStatusMessage: isSpan && input.outcome === 'FAILURE' ? sanitizeTelemetryText(input.message, 255) : undefined,
    durationNanos: isSpan ? durationNanos(input.attributes?.durationMs) : undefined,
    spanEvents: isSpan ? (input.spanEvents || []).slice(0, 30) : undefined,
    occurredAt: new Date().toISOString(),
    attributes: sanitizedAttributes,
    breadcrumbs: breadcrumbs.map((breadcrumb) => sanitizeTelemetryValue(breadcrumb) as TelemetryBreadcrumb),
  };
  await enqueueTelemetryEvent(event);
  if (event.severity === 'ERROR' || event.severity === 'FATAL' || event.outcome === 'FAILURE') {
    void flushAppTelemetry();
  }
}

async function loadPolicy() {
  const apiEnvironment = await initializeApiEnvironment();
  const generation = ++policyGeneration;
  try {
    const response = await telemetryFetch('/observability/policy', {
      method: 'GET',
      headers: await telemetryHeaders(`ereport-app-policy:${Crypto.randomUUID()}`),
    }, apiEnvironment);
    if (generation !== policyGeneration) return;
    if (response.ok) {
      policy = { ...DEFAULT_POLICY, ...(await response.json() as Partial<TelemetryPolicy>) };
      policy.batchSize = Math.max(1, Math.min(policy.batchSize, 50));
      policy.flushIntervalSeconds = Math.max(5, Math.min(policy.flushIntervalSeconds, 300));
      policy.operationSuccessSampleRatePercent = Math.max(
        0,
        Math.min(policy.operationSuccessSampleRatePercent, 100),
      );
      if (!policy.enabled) await clearTelemetryQueue(apiEnvironment);
    }
  } catch {
    if (generation === policyGeneration) policy = DEFAULT_POLICY;
  }
}

function scheduleFlush() {
  if (flushTimer) clearInterval(flushTimer);
  flushTimer = setInterval(() => void flushAppTelemetry(), policy.flushIntervalSeconds * 1000);
}

async function telemetryFetch(
  path: string,
  init: RequestInit,
  apiEnvironment = getCurrentApiEnvironment(),
) {
  await initializeApiEnvironment();
  const endpoints = apiEndpointsFor(apiEnvironment);
  const primaryUrl = `${endpoints.apiBaseUrl}${path}`;
  try {
    const primary = await fetch(primaryUrl, await signedTelemetryInit(primaryUrl, init));
    if (primary.status < 500) return primary;
  } catch {
    // 幂等事件可以安全切换备用线路重试。
  }
  if (!endpoints.fallbackApiBaseUrl || endpoints.fallbackApiBaseUrl === endpoints.apiBaseUrl) {
    throw new Error('运行诊断上报线路不可用。');
  }
  const fallbackUrl = `${endpoints.fallbackApiBaseUrl}${path}`;
  return fetch(fallbackUrl, await signedTelemetryInit(fallbackUrl, init));
}

async function telemetryHeaders(requestId: string) {
  const traceContext = createTraceContext();
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'User-Agent': currentMobileUserAgent(),
    'X-Request-Id': requestId,
    'X-Client-Platform': 'ANDROID',
    'X-Client-App-Version': Constants.nativeAppVersion || Constants.expoConfig?.version || '',
    'X-Client-App-Build': Constants.nativeBuildVersion || '',
    traceparent: traceContext.traceparent,
  };
  return headers;
}

async function signedTelemetryInit(url: string, init: RequestInit): Promise<RequestInit> {
  const credentials = await loadStoredCredentials();
  if (!credentials) return init;
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${credentials.token}`);
  return {
    ...init,
    headers: await buildSignedRequestHeaders({
      url,
      method: String(init.method || 'GET'),
      body: init.body,
      sessionId: credentials.sessionId,
      headers,
    }),
  };
}

async function cachedEnvironment() {
  if (environmentCache && environmentCache.expiresAt > Date.now()) return environmentCache.value;
  const value = await collectEnvironment();
  environmentCache = { expiresAt: Date.now() + 5 * 60 * 1000, value };
  return value;
}

async function collectEnvironment() {
  const [network, nativeEnvironment, deviceIdentity] = await Promise.all([
    NetInfo.fetch(),
    NativeObservability?.getEnvironmentDiagnostics?.().catch(() => null),
    getDeviceIdentity().catch(() => null),
  ]);
  return {
    appVersion: Constants.nativeAppVersion || Constants.expoConfig?.version || '',
    appBuild: Constants.nativeBuildVersion || String(Constants.expoConfig?.android?.versionCode || ''),
    osName: Platform.OS,
    osVersion: String(Platform.Version),
    manufacturer: stringValue(nativeEnvironment?.manufacturer),
    deviceModel: stringValue(nativeEnvironment?.model),
    userAgent: currentMobileUserAgent(),
    networkType: network.type,
    attributes: sanitizeTelemetryAttributes({
      network: networkDetails(network),
      nativeEnvironment,
      deviceSecurity: diagnosticDeviceContext(deviceIdentity?.context),
      runtimeVersion: Constants.expoConfig?.runtimeVersion,
    }),
  };
}

function currentMobileUserAgent(): string {
  return buildMobileUserAgent(
    Constants.expoConfig?.version || Constants.nativeAppVersion,
    Platform.Version,
  );
}

function appResourceAttributes(
  environment: Awaited<ReturnType<typeof collectEnvironment>>,
  installationId: string,
  apiEnvironment: ApiEnvironment,
) {
  return sanitizeTelemetryAttributes({
    'service.name': 'e-report-android',
    'service.namespace': 'e-report',
    'service.version': environment.appVersion,
    'service.instance.id': installationId,
    'deployment.environment.name': appDeploymentEnvironmentName(apiEnvironment),
    'os.type': 'linux',
    'os.name': environment.osName,
    'os.version': environment.osVersion,
    'device.manufacturer': environment.manufacturer,
    'device.model.identifier': environment.deviceModel,
    'user_agent.original': environment.userAgent,
    'network.connection.type': environment.networkType,
    'app.build.id': environment.appBuild,
    'telemetry.sdk.name': 'e-report-internal-otel',
    'telemetry.sdk.language': 'react-native',
    'telemetry.sdk.version': environment.appVersion,
  });
}

function severityNumber(severity: TelemetrySeverity) {
  return { INFO: 9, WARN: 13, ERROR: 17, FATAL: 21 }[severity];
}

function appDeploymentEnvironmentName(apiEnvironment: ApiEnvironment) {
  return apiEnvironment;
}

function durationNanos(value: unknown) {
  const milliseconds = Number(value);
  return Number.isFinite(milliseconds) && milliseconds >= 0 ? Math.round(milliseconds * 1_000_000) : undefined;
}

function diagnosticDeviceContext(context?: DeviceSecurityContext) {
  if (!context) return null;
  const {
    installationId: _installationId,
    buildFingerprint: _buildFingerprint,
    appSignatureSha256: _appSignatureSha256,
    ...diagnosticContext
  } = context;
  return diagnosticContext;
}

async function capturePreviousNativeCrash() {
  const crash = await NativeObservability?.consumeLastCrash?.().catch(() => null);
  if (crash) {
    await enqueue({
      eventType: 'ERROR',
      severity: 'FATAL',
      category: 'SYSTEM',
      eventName: 'PREVIOUS_NATIVE_CRASH',
      message: '检测到应用上次运行发生原生崩溃。',
      outcome: 'FAILURE',
      attributes: crash,
    });
  }
}

function installGlobalErrorHandler() {
  const errorUtils = (globalThis as typeof globalThis & {
    ErrorUtils?: {
      getGlobalHandler(): (error: Error, isFatal?: boolean) => void;
      setGlobalHandler(handler: (error: Error, isFatal?: boolean) => void): void;
    };
  }).ErrorUtils;
  if (!errorUtils) return;
  const previous = errorUtils.getGlobalHandler();
  errorUtils.setGlobalHandler((error, isFatal) => {
    captureAppTelemetryError(error, { eventName: 'GLOBAL_JS_ERROR', fatal: Boolean(isFatal) });
    previous(error, isFatal);
  });
}

function installConsoleCapture() {
  const previousError = console.error.bind(console);
  const previousWarn = console.warn.bind(console);
  console.error = (...args: unknown[]) => {
    previousError(...args);
    const error = args.find((arg) => arg instanceof Error);
    captureAppTelemetryError(error || new Error(formatConsoleArgs(args)), { eventName: 'CONSOLE_ERROR' });
  };
  console.warn = (...args: unknown[]) => {
    previousWarn(...args);
    captureAppTelemetryWarning(formatConsoleArgs(args), { eventName: 'CONSOLE_WARNING' });
  };
}

function formatConsoleArgs(args: unknown[]) {
  return sanitizeTelemetryText(args.map((value) => {
    if (value instanceof Error) return value.message;
    if (typeof value === 'string') return value;
    try { return JSON.stringify(sanitizeTelemetryValue(value)); } catch { return String(value); }
  }).join(' '), 2000);
}

function normalizeError(error: unknown) {
  if (error instanceof Error) return { message: error.message || error.name, stack: error.stack || '' };
  if (typeof error === 'string') return { message: error, stack: '' };
  try { return { message: JSON.stringify(sanitizeTelemetryValue(error)), stack: '' }; } catch { return { message: '未知客户端错误', stack: '' }; }
}

function networkDetails(network: Awaited<ReturnType<typeof NetInfo.fetch>>) {
  const details = (network.details || {}) as unknown as Record<string, unknown>;
  return {
    type: network.type,
    isConnected: network.isConnected,
    isInternetReachable: network.isInternetReachable,
    isConnectionExpensive: details.isConnectionExpensive,
    cellularGeneration: details.cellularGeneration,
    strength: details.strength,
    frequency: details.frequency,
    linkSpeed: details.linkSpeed,
  };
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : '';
}

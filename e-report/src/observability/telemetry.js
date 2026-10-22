import { API_BASE_URL } from '../config.js';
import { getWebEnvironment } from '../security/webEnvironment.js';
import { createTraceContext, isValidSpanId, isValidTraceId } from './trace-context.js';

const QUEUE_STORAGE_KEY = 'e-report.telemetry.queue.v1';
const INSTALLATION_STORAGE_KEY = 'e-report.telemetry.installation.v1';
const SESSION_STORAGE_KEY = 'e-report.telemetry.session.v1';
const MAX_QUEUE_SIZE = 200;
const MAX_BREADCRUMBS = 30;
const DEFAULT_POLICY = Object.freeze({
    enabled: true,
    warningEnabled: true,
    operationEnabled: true,
    operationSuccessSampleRatePercent: 20,
    batchSize: 20,
    flushIntervalSeconds: 15,
});
const SENSITIVE_KEY_PARTS = [
    'password', 'passwd', 'token', 'authorization', 'cookie', 'captcha', 'phone', 'email', 'address',
    'latitude', 'longitude', 'androidid', 'imei', 'serial', 'macaddress', 'advertisingid', 'gaid', 'idfa',
    'bssid', 'ssid', 'ipaddress', 'gateway', 'subnet',
];

let initialized = false;
let flushing = false;
let policy = DEFAULT_POLICY;
let breadcrumbs = [];
let memoryQueue = [];
let flushTimer;
let originalConsoleError;
let originalConsoleWarn;
let browserDeviceEnvironment = {};
let browserDeviceEnvironmentPromise;

export function initializeTelemetry() {
    if (initialized || typeof window === 'undefined') return;
    initialized = true;
    browserDeviceEnvironmentPromise = loadBrowserDeviceEnvironment();
    installGlobalErrorHandlers();
    installConsoleCapture();
    window.addEventListener('online', () => flushTelemetry());
    window.addEventListener('pagehide', () => flushTelemetry({ keepalive: true }));
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden') {
            flushTelemetry({ keepalive: true });
        }
    });
    loadPolicy().finally(() => {
        scheduleFlush();
        flushTelemetry();
    });
}

export function addTelemetryBreadcrumb(category, name, attributes = {}, message = '') {
    breadcrumbs = [...breadcrumbs, {
        occurredAt: new Date().toISOString(),
        category: normalizeCode(category, 'SYSTEM', 40),
        name: normalizeCode(name, 'ACTION', 100),
        message: sanitizeTelemetryText(message, 500),
        attributes: sanitizeTelemetryValue(attributes),
    }].slice(-MAX_BREADCRUMBS);
}

export function captureTelemetryError(error, options = {}) {
    const normalized = normalizeError(error);
    enqueueEvent({
        eventType: 'ERROR',
        severity: options.fatal ? 'FATAL' : 'ERROR',
        category: options.category || 'SYSTEM',
        eventName: options.eventName || 'UNHANDLED_ERROR',
        message: options.message || normalized.message,
        stackTrace: options.stackTrace || normalized.stack,
        route: options.route || currentRoute(),
        operationName: options.operationName,
        outcome: 'FAILURE',
        traceId: options.traceId,
        attributes: options.attributes || {},
    });
}

export function captureTelemetryWarning(message, options = {}) {
    if (!policy.warningEnabled) return;
    enqueueEvent({
        eventType: 'WARNING',
        severity: 'WARN',
        category: options.category || 'SYSTEM',
        eventName: options.eventName || 'CLIENT_WARNING',
        message,
        stackTrace: options.stackTrace,
        route: options.route || currentRoute(),
        operationName: options.operationName,
        outcome: options.outcome || 'UNKNOWN',
        traceId: options.traceId,
        attributes: options.attributes || {},
    });
}

export function captureTelemetryDiagnostic(eventName, attributes = {}, options = {}) {
    enqueueEvent({
        eventType: 'DIAGNOSTIC',
        severity: options.severity || 'INFO',
        category: options.category || 'SYSTEM',
        eventName,
        message: options.message || '用户主动上传运行诊断。',
        route: options.route || currentRoute(),
        operationName: options.operationName,
        outcome: options.outcome || 'UNKNOWN',
        traceId: options.traceId,
        attributes,
    });
}

export function recordTelemetryOperation({
    category,
    eventName,
    operationName,
    outcome,
    message,
    route = currentRoute(),
    traceId,
    spanId,
    parentSpanId,
    traceFlags,
    requestId,
    attributes = {},
    forceCapture = false,
}) {
    if (!policy.operationEnabled) return;
    if (outcome === 'SUCCESS'
        && !forceCapture
        && !shouldSampleOperationSuccess(policy.operationSuccessSampleRatePercent)) {
        return;
    }
    const event = {
        eventType: 'OPERATION',
        severity: outcome === 'FAILURE' ? 'WARN' : 'INFO',
        category,
        eventName,
        message: message || (outcome === 'SUCCESS' ? '操作成功。' : '操作失败。'),
        route,
        operationName,
        outcome,
        traceId,
        spanId,
        parentSpanId,
        traceFlags,
        requestId,
        attributes,
    };
    if (outcome === 'SUCCESS') {
        enqueueWhenBrowserIdle(event);
    } else {
        enqueueEvent(event);
    }
}

export function startTelemetryOperation(options) {
    const traceContext = createTraceContext(options.traceId);
    const traceId = traceContext.traceId;
    const spanId = isValidSpanId(options.spanId) ? options.spanId : traceContext.spanId;
    const startedAt = Date.now();
    const stages = [];
    let completed = false;
    const complete = (outcome, error, attributes = {}) => {
        if (completed) return;
        completed = true;
        const normalizedError = outcome === 'FAILURE' ? normalizeError(error) : null;
        recordTelemetryOperation({
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
                stages: stages.map(stage => ({
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
                name: normalizeCode(name, 'STAGE', 100),
                elapsedMs: Date.now() - startedAt,
                attributes: sanitizeTelemetryValue(attributes),
            });
        },
        success(attributes = {}) {
            complete('SUCCESS', null, attributes);
        },
        failure(error, attributes = {}) {
            complete('FAILURE', error, attributes);
        },
    };
}

export function shouldSampleOperationSuccess(ratePercent, randomValue = Math.random()) {
    const normalizedRate = Number(ratePercent);
    const rate = Math.max(0, Math.min(Number.isFinite(normalizedRate) ? normalizedRate : 0, 100));
    return rate >= 100 || (rate > 0 && randomValue * 100 < rate);
}

export function recordApiTelemetry({ method, path, status, durationMs, requestId, traceId, spanId, traceFlags, error, expectedStatuses = [] }) {
    if (error?.code === 'ERR_CANCELED') return;
    const normalizedMethod = String(method || 'GET').toUpperCase();
    const route = sanitizeRoute(path);
    addTelemetryBreadcrumb('NETWORK', 'API_REQUEST', {
        method: normalizedMethod,
        path: route,
        status,
        durationMs,
        requestId,
    });
    const expected = isExpectedApiStatus(status, expectedStatuses);
    const failed = Boolean(error) || (Number(status) >= 400 && !expected);
    recordTelemetryOperation({
        category: 'NETWORK',
        eventName: 'HTTP_CLIENT_REQUEST',
        route: currentRoute(),
        operationName: `${normalizedMethod} ${route}`,
        traceId,
        spanId,
        traceFlags,
        requestId,
        outcome: failed ? 'FAILURE' : 'SUCCESS',
        message: failed ? `接口请求失败：HTTP ${status}` : '接口请求完成。',
        forceCapture: failed,
        attributes: {
            'http.request.method': normalizedMethod,
            'http.response.status_code': Number(status),
            'url.path': route,
            'ereport.request.id': requestId,
            durationMs,
            ...(error ? { 'exception.type': error?.name || 'Error', 'exception.message': error?.message || String(error) } : {}),
        },
    });
}

export function isExpectedApiStatus(status, expectedStatuses = []) {
    const normalizedStatus = Number(status);
    return Number.isInteger(normalizedStatus)
        && Array.isArray(expectedStatuses)
        && expectedStatuses.some(expectedStatus => Number(expectedStatus) === normalizedStatus);
}

export async function flushTelemetry({ keepalive = false } = {}) {
    if (flushing || !policy.enabled || typeof fetch !== 'function') return;
    if (typeof navigator !== 'undefined' && navigator.onLine === false) return;
    const queued = readQueue();
    if (queued.length === 0) return;
    flushing = true;
    const batch = queued.slice(0, Math.max(1, Math.min(policy.batchSize, 50)));
    try {
        await browserDeviceEnvironmentPromise;
        const requestId = createRequestId('ereport-web-telemetry');
        const headers = telemetryHeaders(requestId);
        const environment = browserEnvironment();
        const response = await fetch(`${API_BASE_URL}/observability/events/batch`, {
            method: 'POST',
            headers,
            body: JSON.stringify({
                schemaVersion: 2,
                platform: 'WEB',
                installationId: installationId(),
                sessionId: sessionId(),
                environment,
                resource: { attributes: webResourceAttributes(environment) },
                instrumentationScope: {
                    name: 'e-report-web.telemetry',
                    version: webVersion(),
                    attributes: { 'ereport.telemetry.protocol': 'ereport-otel/1' },
                },
                events: batch,
            }),
            keepalive,
            credentials: 'include',
        });
        if (response.ok) {
            removeEvents(batch.map(event => event.clientEventId));
        } else if (response.status >= 400 && response.status < 500 && response.status !== 429) {
            removeEvents(batch.map(event => event.clientEventId));
        }
    } catch {
        // 网络和 Redis 暂时不可用时保留本地队列，等待下一轮发送。
    } finally {
        flushing = false;
    }
}

export function sanitizeTelemetryValue(value, depth = 0) {
    if (value == null) return value;
    if (depth >= 6) return '[层级已截断]';
    if (Array.isArray(value)) return value.slice(0, 50).map(item => sanitizeTelemetryValue(item, depth + 1));
    if (typeof value === 'object') {
        const sanitized = {};
        Object.entries(value).slice(0, 50).forEach(([key, item]) => {
            sanitized[key] = isSensitiveKey(key) ? '[已脱敏]' : sanitizeTelemetryValue(item, depth + 1);
        });
        return sanitized;
    }
    if (typeof value === 'number' || typeof value === 'boolean') return value;
    return sanitizeTelemetryText(String(value), 1000);
}

export function sanitizeTelemetryText(value, maximumLength = 2000) {
    if (!value) return '';
    const sanitized = String(value)
        .replace(/bearer\s+[a-z0-9._~+/=-]+/gi, 'Bearer [已脱敏]')
        .replace(/(?<![a-z0-9_-])[a-z0-9_-]{10,}\.[a-z0-9_-]{10,}\.[a-z0-9_-]{10,}(?![a-z0-9_-])/gi, '[令牌已脱敏]')
        .replace(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/gi, '[邮箱已脱敏]')
        .replace(/(?<!\d)1[3-9]\d{9}(?!\d)/g, '[手机号已脱敏]')
        .replace(/\b(imei|android[_ -]?id|serial(?: number)?|mac(?: address)?)\s*[:=]\s*[^,;\s]+/gi, '$1=[已移除]')
        .replace(/(?<![0-9a-f])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f])/gi, '[MAC已移除]')
        .replace(/\b(latitude|longitude|lat|lng)\s*[:=]\s*-?\d{1,3}(?:\.\d+)?/gi, '$1=[坐标已脱敏]')
        .trim();
    return sanitized.slice(0, maximumLength);
}

function enqueueEvent(event) {
    if (!policy.enabled) return;
    const isSpan = event.eventType === 'OPERATION';
    const generatedContext = isSpan ? createTraceContext(event.traceId) : null;
    const traceId = isValidTraceId(event.traceId) ? event.traceId : generatedContext?.traceId;
    const spanId = isValidSpanId(event.spanId) ? event.spanId : generatedContext?.spanId;
    const attributes = sanitizeTelemetryValue({
        ...event.attributes,
        'ereport.event.type': event.eventType,
        'ereport.event.category': event.category,
        'ereport.event.name': event.eventName,
        'ereport.operation.name': event.operationName,
        'ereport.operation.outcome': event.outcome,
        'ereport.request.id': event.requestId,
        ...(event.stackTrace ? {
            'exception.message': event.message,
            'exception.stacktrace': event.stackTrace,
        } : {}),
    });
    const queuedEvent = {
        clientEventId: createRequestId('web-event'),
        eventType: event.eventType,
        severity: event.severity,
        category: normalizeCode(event.category, 'SYSTEM', 40),
        eventName: normalizeCode(event.eventName, 'CLIENT_EVENT', 100),
        message: sanitizeTelemetryText(event.message, 2000),
        stackTrace: sanitizeTelemetryText(event.stackTrace, 16000),
        route: sanitizeRoute(event.route),
        operationName: sanitizeTelemetryText(event.operationName, 120),
        outcome: event.outcome || 'UNKNOWN',
        traceId,
        spanId,
        parentSpanId: isValidSpanId(event.parentSpanId) ? event.parentSpanId : undefined,
        traceFlags: event.traceFlags ?? generatedContext?.traceFlags,
        requestId: sanitizeTelemetryText(event.requestId, 100),
        signalType: isSpan ? 'SPAN' : 'LOG',
        severityNumber: severityNumber(event.severity),
        severityText: event.severity,
        body: sanitizeTelemetryText(event.message, 2000),
        spanName: isSpan ? sanitizeTelemetryText(event.operationName || event.eventName, 255) : undefined,
        spanKind: isSpan && event.category === 'NETWORK' ? 'CLIENT' : isSpan ? 'INTERNAL' : undefined,
        spanStatusCode: isSpan ? (event.outcome === 'FAILURE' ? 'ERROR' : event.outcome === 'SUCCESS' ? 'OK' : 'UNSET') : undefined,
        spanStatusMessage: isSpan && event.outcome === 'FAILURE' ? sanitizeTelemetryText(event.message, 255) : undefined,
        durationNanos: isSpan ? durationNanos(event.attributes?.durationMs) : undefined,
        spanEvents: isSpan ? (event.attributes?.stages || []).slice(0, 30) : undefined,
        occurredAt: new Date().toISOString(),
        attributes,
        breadcrumbs: breadcrumbs.map(item => sanitizeTelemetryValue(item)),
    };
    const queue = readQueue();
    queue.push(queuedEvent);
    writeQueue(limitQueue(queue));
    if (queuedEvent.severity === 'FATAL' || queuedEvent.severity === 'ERROR' || queuedEvent.outcome === 'FAILURE') {
        queueMicrotask(() => flushTelemetry());
    }
}

function enqueueWhenBrowserIdle(event) {
    if (typeof window === 'undefined') {
        enqueueEvent(event);
        return;
    }
    if (typeof window.requestIdleCallback === 'function') {
        window.requestIdleCallback(() => enqueueEvent(event), { timeout: 1000 });
        return;
    }
    window.setTimeout(() => enqueueEvent(event), 0);
}

function limitQueue(queue) {
    if (queue.length <= MAX_QUEUE_SIZE) return queue;
    const priority = { FATAL: 4, ERROR: 3, WARN: 2, INFO: 1 };
    return queue
        .map((event, index) => ({ event, index }))
        .sort((left, right) => (priority[right.event.severity] || 0) - (priority[left.event.severity] || 0) || right.index - left.index)
        .slice(0, MAX_QUEUE_SIZE)
        .sort((left, right) => left.index - right.index)
        .map(item => item.event);
}

function readQueue() {
    if (typeof localStorage === 'undefined') return memoryQueue;
    try {
        const parsed = JSON.parse(localStorage.getItem(QUEUE_STORAGE_KEY) || '[]');
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return memoryQueue;
    }
}

function writeQueue(queue) {
    memoryQueue = queue;
    if (typeof localStorage === 'undefined') return;
    try {
        localStorage.setItem(QUEUE_STORAGE_KEY, JSON.stringify(queue));
    } catch {
        memoryQueue = queue;
    }
}

function removeEvents(clientEventIds) {
    const removed = new Set(clientEventIds);
    writeQueue(readQueue().filter(event => !removed.has(event.clientEventId)));
}

async function loadPolicy() {
    try {
        const response = await fetch(`${API_BASE_URL}/observability/policy`, {
            headers: telemetryHeaders(createRequestId('ereport-web-policy')),
            credentials: 'include',
        });
        if (response.ok) {
            policy = { ...DEFAULT_POLICY, ...(await response.json()) };
            policy.batchSize = Math.max(1, Math.min(Number(policy.batchSize) || DEFAULT_POLICY.batchSize, 50));
            policy.flushIntervalSeconds = Math.max(
                5,
                Math.min(Number(policy.flushIntervalSeconds) || DEFAULT_POLICY.flushIntervalSeconds, 300),
            );
            policy.operationSuccessSampleRatePercent = Math.max(
                0,
                Math.min(Number(policy.operationSuccessSampleRatePercent) || 0, 100),
            );
            if (!policy.enabled) writeQueue([]);
        }
    } catch {
        policy = DEFAULT_POLICY;
    }
}

function scheduleFlush() {
    if (typeof window === 'undefined') return;
    if (flushTimer) window.clearInterval(flushTimer);
    flushTimer = window.setInterval(() => flushTelemetry(), Math.max(5, policy.flushIntervalSeconds) * 1000);
}

function installGlobalErrorHandlers() {
    window.addEventListener('error', event => {
        captureTelemetryError(event.error || new Error(event.message || '浏览器资源或脚本加载失败。'), {
            category: event.error ? 'SYSTEM' : 'UI',
            eventName: event.error ? 'WINDOW_ERROR' : 'RESOURCE_LOAD_ERROR',
            attributes: { filename: event.filename, line: event.lineno, column: event.colno },
        });
    }, true);
    window.addEventListener('unhandledrejection', event => {
        captureTelemetryError(event.reason, { category: 'SYSTEM', eventName: 'UNHANDLED_PROMISE_REJECTION' });
    });
}

function installConsoleCapture() {
    originalConsoleError = console.error.bind(console);
    originalConsoleWarn = console.warn.bind(console);
    console.error = (...args) => {
        originalConsoleError(...args);
        const error = args.find(arg => arg instanceof Error);
        captureTelemetryError(error || new Error(formatConsoleArgs(args)), { eventName: 'CONSOLE_ERROR' });
    };
    console.warn = (...args) => {
        originalConsoleWarn(...args);
        captureTelemetryWarning(formatConsoleArgs(args), { eventName: 'CONSOLE_WARNING' });
    };
}

function formatConsoleArgs(args) {
    return sanitizeTelemetryText(args.map(value => {
        if (value instanceof Error) return value.message;
        if (typeof value === 'string') return value;
        try {
            return JSON.stringify(sanitizeTelemetryValue(value));
        } catch {
            return String(value);
        }
    }).join(' '), 2000);
}

function normalizeError(error) {
    if (error instanceof Error) return { message: error.message || error.name, stack: error.stack || '' };
    if (typeof error === 'string') return { message: error, stack: '' };
    try {
        return { message: JSON.stringify(sanitizeTelemetryValue(error)), stack: '' };
    } catch {
        return { message: '未知客户端错误', stack: '' };
    }
}

function telemetryHeaders(requestId) {
    const traceContext = createTraceContext();
    const headers = {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Request-Id': requestId,
        'X-Client-Platform': 'WEB',
        'X-Client-App-Version': webVersion(),
        'X-Client-Network-Type': networkType(),
        traceparent: traceContext.traceparent,
    };
    const csrfToken = cookieValue('XSRF-TOKEN');
    if (csrfToken) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrfToken);
    return headers;
}

function cookieValue(name) {
    if (typeof document === 'undefined') return '';
    const prefix = `${name}=`;
    return document.cookie.split(';')
        .map(value => value.trim())
        .find(value => value.startsWith(prefix))
        ?.slice(prefix.length) || '';
}

function browserEnvironment() {
    const screenInfo = typeof window === 'undefined' ? {} : {
        screenWidth: window.screen?.width,
        screenHeight: window.screen?.height,
        viewportWidth: window.innerWidth,
        viewportHeight: window.innerHeight,
        pixelRatio: window.devicePixelRatio,
        language: navigator.language,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        online: navigator.onLine,
    };
    return {
        appVersion: webVersion(),
        appBuild: import.meta.env?.VITE_APP_BUILD || '',
        osName: browserDeviceEnvironment.osName || '未知系统',
        osVersion: browserDeviceEnvironment.osVersion || '',
        manufacturer: browserDeviceEnvironment.manufacturer || '',
        deviceModel: browserDeviceEnvironment.deviceModel || '',
        userAgent: typeof navigator === 'undefined' ? '' : navigator.userAgent,
        networkType: networkType(),
        attributes: { ...screenInfo, ...browserDeviceEnvironment.attributes },
    };
}

function webResourceAttributes(environment) {
    return sanitizeTelemetryValue({
        'service.name': 'e-report-web',
        'service.namespace': 'e-report',
        'service.version': environment.appVersion,
        'service.instance.id': installationId(),
        'deployment.environment.name': deploymentEnvironmentName(),
        'os.type': String(environment.osName || '').toLowerCase(),
        'os.name': environment.osName,
        'os.version': environment.osVersion,
        'device.manufacturer': environment.manufacturer,
        'device.model.identifier': environment.deviceModel,
        'user_agent.original': environment.userAgent,
        'network.connection.type': environment.networkType,
        'app.build.id': environment.appBuild,
        'telemetry.sdk.name': 'e-report-internal-otel',
        'telemetry.sdk.language': 'webjs',
        'telemetry.sdk.version': environment.appVersion,
    });
}

function severityNumber(severity) {
    return { INFO: 9, WARN: 13, ERROR: 17, FATAL: 21 }[severity] || 0;
}

function deploymentEnvironmentName() {
    const configured = import.meta.env?.VITE_DEPLOYMENT_ENVIRONMENT;
    if (configured) return configured;
    if (typeof window !== 'undefined' && /(^|\.)[^.]*-test\./i.test(window.location.hostname)) return 'test';
    return 'production';
}

function durationNanos(value) {
    const milliseconds = Number(value);
    return Number.isFinite(milliseconds) && milliseconds >= 0 ? Math.round(milliseconds * 1_000_000) : undefined;
}

async function loadBrowserDeviceEnvironment() {
    if (typeof navigator === 'undefined') return;
    const environment = await getWebEnvironment();
    browserDeviceEnvironment = {
        osName: environment.osName,
        osVersion: environment.osVersion,
        manufacturer: environment.manufacturer,
        deviceModel: [environment.browserName, environment.browserVersion].filter(Boolean).join(' '),
        attributes: sanitizeTelemetryValue(environment.attributes),
    };
}

export function extractDeviceModelFromUserAgent(userAgent) {
    const match = String(userAgent || '').match(/Android[^;)]*;\s*(?:[a-z]{2}[-_][a-z]{2};\s*)?([^;)]+?)(?:\s+Build\/|[;)])/i);
    if (!match) return '';
    const model = match[1].trim();
    return /^(wv|mobile)$/i.test(model) ? '' : sanitizeTelemetryText(model, 100);
}

function webVersion() {
    return import.meta.env?.VITE_APP_VERSION || 'web';
}

function networkType() {
    if (typeof navigator === 'undefined') return 'unknown';
    const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
    return connection?.effectiveType || (navigator.onLine ? 'online' : 'offline');
}

function installationId() {
    return persistentUuid(INSTALLATION_STORAGE_KEY, typeof localStorage === 'undefined' ? null : localStorage);
}

function sessionId() {
    return persistentUuid(SESSION_STORAGE_KEY, typeof sessionStorage === 'undefined' ? null : sessionStorage);
}

function persistentUuid(key, storage) {
    if (storage) {
        try {
            const existing = storage.getItem(key);
            if (isUuid(existing)) return existing;
            const created = createUuid();
            storage.setItem(key, created);
            return created;
        } catch {
            return createUuid();
        }
    }
    return createUuid();
}

function createUuid() {
    if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, character => {
        const random = Math.floor(Math.random() * 16);
        const value = character === 'x' ? random : (random & 0x3) | 0x8;
        return value.toString(16);
    });
}

function createRequestId(prefix) {
    return `${prefix}:${createUuid()}`;
}

function isUuid(value) {
    return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function currentRoute() {
    return typeof window === 'undefined' ? '' : sanitizeRoute(window.location.pathname);
}

function sanitizeRoute(value) {
    return sanitizeTelemetryText(String(value || '').split(/[?#]/)[0], 255);
}

function normalizeCode(value, fallback, maximumLength) {
    const normalized = String(value || fallback).toUpperCase().replace(/[^A-Z0-9_.:-]/g, '_');
    return normalized.slice(0, maximumLength) || fallback;
}

function isSensitiveKey(key) {
    const normalized = String(key).toLowerCase().replace(/[-_]/g, '');
    return SENSITIVE_KEY_PARTS.some(part => normalized.includes(part.replace(/[-_]/g, '')))
        || normalized === 'lat'
        || normalized === 'lng'
        || normalized === 'mac';
}

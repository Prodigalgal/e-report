// src/services/apiClient.js
import axios from 'axios';
import { API_BASE_URL } from "../config.js";
import { recordApiTelemetry } from '../observability/telemetry.js';
import { createTraceContext } from '../observability/trace-context.js';

const apiClient = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    withXSRFToken: true,
    xsrfCookieName: 'XSRF-TOKEN',
    xsrfHeaderName: 'X-XSRF-TOKEN',
});

apiClient.interceptors.request.use(
    (config) => {
        const requestId = `ereport-web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
        config.headers = config.headers || {};
        if (!config.headers['X-Request-Id']) {
            config.headers['X-Request-Id'] = requestId;
        }
        const traceContext = createTraceContext();
        config.headers.traceparent = traceContext.traceparent;
        config.telemetryTraceContext = traceContext;
        config.headers['X-Client-Platform'] = 'WEB';
        config.headers['X-Client-App-Version'] = import.meta.env?.VITE_APP_VERSION || 'web';
        const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
        config.headers['X-Client-Network-Type'] = connection?.effectiveType || (navigator.onLine ? 'online' : 'offline');
        config.telemetryStartedAt = performance.now();
        const method = String(config.method || 'get').toUpperCase();
        if (['POST', 'PATCH'].includes(method) && !config.headers['Idempotency-Key']) {
            config.headers['Idempotency-Key'] = requestId;
        }
        return config;
    },
    (error) => {
        recordApiTelemetry({ method: 'UNKNOWN', path: '', status: 0, durationMs: 0, error });
        return Promise.reject(error);
    }
);

apiClient.interceptors.response.use(
    response => {
        recordApiTelemetry({
            method: response.config.method,
            path: response.config.url,
            status: response.status,
            durationMs: response.config.telemetryStartedAt ? Math.round(performance.now() - response.config.telemetryStartedAt) : undefined,
            requestId: response.config.headers?.['X-Request-Id'],
            ...response.config.telemetryTraceContext,
            expectedStatuses: response.config.telemetryExpectedStatuses,
        });
        return response;
    },
    error => {
        const config = error.config || error.response?.config || {};
        recordApiTelemetry({
            method: config.method,
            path: config.url,
            status: error.response?.status || 0,
            durationMs: config.telemetryStartedAt ? Math.round(performance.now() - config.telemetryStartedAt) : undefined,
            requestId: config.headers?.['X-Request-Id'],
            ...config.telemetryTraceContext,
            error,
            expectedStatuses: config.telemetryExpectedStatuses,
        });
        if (error.response && error.response.status === 401) {
            if (!error.response.config.url.endsWith('/auth/login')) {
                window.dispatchEvent(new Event('ereport-auth-expired'));
            }
        }
        return Promise.reject(error);
    }
);

export const fetchPermissionsAPI = (params) => apiClient.get('/admin/permissions', { params });

export default apiClient;

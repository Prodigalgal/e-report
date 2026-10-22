export type TelemetryPlatform = 'ANDROID';
export type TelemetryEventType = 'ERROR' | 'WARNING' | 'OPERATION' | 'DIAGNOSTIC';
export type TelemetrySeverity = 'INFO' | 'WARN' | 'ERROR' | 'FATAL';
export type TelemetryOutcome = 'SUCCESS' | 'FAILURE' | 'UNKNOWN';
export type TelemetrySignalType = 'LOG' | 'SPAN';
export type TelemetrySpanKind = 'INTERNAL' | 'CLIENT' | 'SERVER';
export type TelemetrySpanStatusCode = 'UNSET' | 'OK' | 'ERROR';

export type TelemetryBreadcrumb = {
  occurredAt: string;
  category: string;
  name: string;
  message?: string;
  attributes?: Record<string, unknown>;
};

export type TelemetryEvent = {
  clientEventId: string;
  eventType: TelemetryEventType;
  severity: TelemetrySeverity;
  category: string;
  eventName: string;
  message?: string;
  stackTrace?: string;
  route?: string;
  operationName?: string;
  outcome: TelemetryOutcome;
  traceId?: string;
  spanId?: string;
  parentSpanId?: string;
  traceFlags?: number;
  requestId?: string;
  signalType?: TelemetrySignalType;
  severityNumber?: number;
  severityText?: string;
  body?: string;
  spanName?: string;
  spanKind?: TelemetrySpanKind;
  spanStatusCode?: TelemetrySpanStatusCode;
  spanStatusMessage?: string;
  durationNanos?: number;
  spanEvents?: TelemetryBreadcrumb[];
  occurredAt: string;
  attributes: Record<string, unknown>;
  breadcrumbs: TelemetryBreadcrumb[];
};

export type TelemetryPolicy = {
  enabled: boolean;
  warningEnabled: boolean;
  operationEnabled: boolean;
  operationSuccessSampleRatePercent: number;
  batchSize: number;
  flushIntervalSeconds: number;
};

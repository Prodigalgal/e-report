const TRACE_ID_PATTERN = /^[0-9a-f]{32}$/;
const SPAN_ID_PATTERN = /^[0-9a-f]{16}$/;

export type TelemetryTraceContext = {
  traceId: string;
  spanId: string;
  traceFlags: number;
  traceparent: string;
};

export function createTraceContextFromUuids(
  traceUuid: string,
  spanUuid: string,
  parentTraceId?: string,
): TelemetryTraceContext {
  const traceId = isValidTraceId(parentTraceId) ? String(parentTraceId) : uuidHex(traceUuid);
  const spanId = uuidHex(spanUuid).slice(0, 16);
  return { traceId, spanId, traceFlags: 1, traceparent: `00-${traceId}-${spanId}-01` };
}

export function parseTraceparent(value?: string | null) {
  const match = String(value || '').trim().toLowerCase()
    .match(/^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$/);
  if (!match || !isValidTraceId(match[1]) || !isValidSpanId(match[2])) return null;
  return { traceId: match[1], spanId: match[2], traceFlags: Number.parseInt(match[3], 16) };
}

export function isValidTraceId(value?: string | null) {
  return TRACE_ID_PATTERN.test(String(value || '')) && !/^0+$/.test(String(value));
}

export function isValidSpanId(value?: string | null) {
  return SPAN_ID_PATTERN.test(String(value || '')) && !/^0+$/.test(String(value));
}

function uuidHex(value: string) {
  const normalized = value.replaceAll('-', '').toLowerCase();
  if (!/^[0-9a-f]{32}$/.test(normalized) || /^0+$/.test(normalized)) {
    throw new Error('随机 UUID 不符合 Trace Context 要求。');
  }
  return normalized;
}

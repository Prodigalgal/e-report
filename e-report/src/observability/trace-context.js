const TRACE_ID_PATTERN = /^[0-9a-f]{32}$/;
const SPAN_ID_PATTERN = /^[0-9a-f]{16}$/;

export function createTraceContext(parentTraceId) {
    const traceId = isValidTraceId(parentTraceId) ? parentTraceId : randomHex(16);
    const spanId = randomHex(8);
    const traceFlags = 1;
    return {
        traceId,
        spanId,
        traceFlags,
        traceparent: `00-${traceId}-${spanId}-01`,
    };
}

export function parseTraceparent(value) {
    const match = String(value || '').trim().toLowerCase()
        .match(/^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$/);
    if (!match || !isValidTraceId(match[1]) || !isValidSpanId(match[2])) return null;
    return { traceId: match[1], spanId: match[2], traceFlags: Number.parseInt(match[3], 16) };
}

export function isValidTraceId(value) {
    return TRACE_ID_PATTERN.test(String(value || '')) && !/^0+$/.test(value);
}

export function isValidSpanId(value) {
    return SPAN_ID_PATTERN.test(String(value || '')) && !/^0+$/.test(value);
}

function randomHex(byteLength) {
    const bytes = new Uint8Array(byteLength);
    if (globalThis.crypto?.getRandomValues) {
        globalThis.crypto.getRandomValues(bytes);
    } else {
        for (let index = 0; index < bytes.length; index += 1) {
            bytes[index] = Math.floor(Math.random() * 256);
        }
    }
    const value = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
    return /^0+$/.test(value) ? `${'0'.repeat(value.length - 1)}1` : value;
}


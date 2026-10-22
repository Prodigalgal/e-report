import assert from 'node:assert/strict';
import test from 'node:test';

import { createTraceContext, parseTraceparent } from './trace-context.js';

test('网页端生成符合 W3C Trace Context 的标识', () => {
    const context = createTraceContext();
    assert.match(context.traceId, /^[0-9a-f]{32}$/);
    assert.match(context.spanId, /^[0-9a-f]{16}$/);
    assert.deepEqual(parseTraceparent(context.traceparent), {
        traceId: context.traceId,
        spanId: context.spanId,
        traceFlags: 1,
    });
});

test('网页端拒绝全零和非 W3C traceparent', () => {
    assert.equal(parseTraceparent('00-00000000000000000000000000000000-0000000000000000-01'), null);
    assert.equal(parseTraceparent('web-operation:not-a-trace-id'), null);
});


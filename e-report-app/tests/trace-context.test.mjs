import assert from 'node:assert/strict';
import test from 'node:test';

import { createTraceContextFromUuids, parseTraceparent } from '../src/observability/trace-context-core.ts';

test('安卓端生成符合 W3C Trace Context 的标识', () => {
  const context = createTraceContextFromUuids(
    'de305d54-75b4-431b-adb2-eb6b9e546014',
    '7d444840-9dc0-11d1-b245-5ffdce74fad2',
  );
  assert.match(context.traceId, /^[0-9a-f]{32}$/);
  assert.match(context.spanId, /^[0-9a-f]{16}$/);
  assert.deepEqual(parseTraceparent(context.traceparent), {
    traceId: context.traceId,
    spanId: context.spanId,
    traceFlags: 1,
  });
});

test('安卓端拒绝全零 traceparent', () => {
  assert.equal(parseTraceparent('00-00000000000000000000000000000000-0000000000000000-01'), null);
});

import assert from 'node:assert/strict';
import test from 'node:test';

import { isExpectedApiStatus } from '../src/observability/telemetry-api-policy.ts';

test('仅显式声明的业务状态码不会生成安卓接口告警', () => {
  assert.equal(isExpectedApiStatus(401, [401]), true);
  assert.equal(isExpectedApiStatus(403, [401]), false);
  assert.equal(isExpectedApiStatus(500, [401]), false);
  assert.equal(isExpectedApiStatus(401), false);
});

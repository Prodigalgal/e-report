import assert from 'node:assert/strict';
import test from 'node:test';

import { shouldDeleteTelemetryBatch } from '../src/observability/telemetry-delivery-policy.ts';

test('成功响应和永久无效批次会从本机队列删除', () => {
  for (const status of [200, 201, 204, 400, 409, 413, 422]) {
    assert.equal(shouldDeleteTelemetryBatch(status), true, `HTTP ${status}`);
  }
});

test('认证、发布窗口、限流和服务异常会保留本机队列', () => {
  for (const status of [0, 401, 403, 404, 405, 429, 500, 502, 503, 504]) {
    assert.equal(shouldDeleteTelemetryBatch(status), false, `HTTP ${status}`);
  }
});

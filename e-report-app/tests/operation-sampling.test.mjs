import assert from 'node:assert/strict';
import test from 'node:test';

import { shouldSampleOperationSuccess } from '../src/observability/operation-sampling.ts';

test('成功操作采样率会限制在 0 到 100', () => {
  assert.equal(shouldSampleOperationSuccess(-1, 0), false);
  assert.equal(shouldSampleOperationSuccess(0, 0), false);
  assert.equal(shouldSampleOperationSuccess(100, 0.999), true);
  assert.equal(shouldSampleOperationSuccess(101, 0.999), true);
});

test('成功操作按配置百分比采样', () => {
  assert.equal(shouldSampleOperationSuccess(20, 0.199), true);
  assert.equal(shouldSampleOperationSuccess(20, 0.2), false);
});

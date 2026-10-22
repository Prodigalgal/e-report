import assert from 'node:assert/strict';
import test from 'node:test';

import { buildMobileUserAgent } from '../src/observability/resource-attributes.ts';

test('APP User-Agent 使用当前应用与安卓版本而不是历史常量', () => {
  assert.equal(buildMobileUserAgent('5.3.2', 36), 'e-report-app/5.3.2 Android/36');
  assert.doesNotMatch(buildMobileUserAgent('5.3.2', 36), /e-report-app\/1\.0/);
});

test('APP User-Agent 对缺失和空白字段使用稳定占位', () => {
  assert.equal(buildMobileUserAgent('', null), 'e-report-app/unknown Android/unknown');
  assert.equal(buildMobileUserAgent('5.3.2 acceptance', 'Android 16'), 'e-report-app/5.3.2_acceptance Android/Android_16');
});

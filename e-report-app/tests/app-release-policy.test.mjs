import assert from 'node:assert/strict';
import test from 'node:test';

import { normalizeAppReleasePolicy } from '../src/features/admin/app-release-policy.ts';

test('APP 发布策略归一化合法输入', () => {
  assert.deepEqual(normalizeAppReleasePolicy({
    forceUpdate: true,
    minSupportedVersionCode: '37',
    releaseNotes: '  更新说明  ',
  }, 40), {
    forceUpdate: true,
    minSupportedVersionCode: 37,
    releaseNotes: '更新说明',
  });
});

test('APP 发布策略拒绝非法最低构建号和超长说明', () => {
  assert.throws(() => normalizeAppReleasePolicy({ forceUpdate: false, minSupportedVersionCode: '-1', releaseNotes: '' }, 40), /大于或等于0/);
  assert.throws(() => normalizeAppReleasePolicy({ forceUpdate: false, minSupportedVersionCode: '40.5', releaseNotes: '' }, 40), /整数/);
  assert.throws(() => normalizeAppReleasePolicy({ forceUpdate: false, minSupportedVersionCode: '41', releaseNotes: '' }, 40), /不能高于/);
  assert.throws(() => normalizeAppReleasePolicy({ forceUpdate: false, minSupportedVersionCode: '40', releaseNotes: 'a'.repeat(2001) }, 40), /2000/);
});

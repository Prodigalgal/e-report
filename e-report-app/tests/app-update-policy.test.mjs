import assert from 'node:assert/strict';
import test from 'node:test';
import { shouldPromptNativeUpgrade } from '../src/features/app-updates/app-update-policy.ts';

const available = { updateAvailable: true, versionCode: 5011000, forceUpdate: false, nativeUpgradeRequired: false };

test('兼容安装通过 OTA 更新业务，不弹出重复安装提示', () => {
  assert.equal(shouldPromptNativeUpgrade(available), false);
  assert.equal(shouldPromptNativeUpgrade(undefined), false);
  assert.equal(shouldPromptNativeUpgrade({ ...available, updateAvailable: false, nativeUpgradeRequired: true }), false);
});

test('不兼容运行时或管理员明确强制升级时提示安装 APK', () => {
  assert.equal(shouldPromptNativeUpgrade({ ...available, nativeUpgradeRequired: true }), true);
  assert.equal(shouldPromptNativeUpgrade({ ...available, forceUpdate: true }), true);
});

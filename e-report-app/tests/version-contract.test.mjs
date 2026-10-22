import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { calculateAndroidVersionCode, verifySourceVersionContract } from '../scripts/verify-version-contract.mjs';

function fixture({ version = '1.0.39', versionCode = 39, runtimeVersion = 'android-1.0.39-native39' } = {}) {
  const root = path.join(tmpdir(), `e-report-version-${crypto.randomUUID()}`);
  mkdirSync(path.join(root, 'android', 'app'), { recursive: true });
  writeFileSync(path.join(root, 'package.json'), JSON.stringify({ version }));
  writeFileSync(path.join(root, 'package-lock.json'), JSON.stringify({ version, packages: { '': { version } } }));
  writeFileSync(path.join(root, 'app.json'), JSON.stringify({
    expo: { version, runtimeVersion, android: { versionCode, package: 'com.mnnu.ereport' } },
  }));
  writeFileSync(path.join(root, 'native-release.json'), JSON.stringify({
    version, versionCode, runtimeVersion, applicationId: 'com.mnnu.ereport',
  }));
  writeFileSync(path.join(root, 'android', 'app', 'build.gradle'), `versionCode ${versionCode}\nversionName "${version}"\n`);
  return root;
}

test('接受 versionName、versionCode、runtimeVersion 完全对齐的配置', () => {
  assert.deepEqual(verifySourceVersionContract({ appRoot: fixture() }), {
    versionCode: 39,
    versionName: '1.0.39',
    runtimeVersion: 'android-1.0.39-native39',
  });
});

test('按 x.y.z 计算全局递增的 Android versionCode', () => {
  assert.equal(calculateAndroidVersionCode('1.0.47'), 47);
  assert.equal(calculateAndroidVersionCode('1.1.0'), 1000);
  assert.equal(calculateAndroidVersionCode('2.0.0'), 1000000);
});

test('接受 2.0.0 的 major 版本契约', () => {
  assert.deepEqual(verifySourceVersionContract({
    appRoot: fixture({
      version: '2.0.0',
      versionCode: 1000000,
      runtimeVersion: 'android-2.0.0-native1000000',
    }),
  }), {
    versionCode: 1000000,
    versionName: '2.0.0',
    runtimeVersion: 'android-2.0.0-native1000000',
  });
});

test('拒绝基础包 versionName 与计算所得 versionCode 不一致', () => {
  assert.throws(
    () => verifySourceVersionContract({ appRoot: fixture({ version: '1.0.38' }) }),
    /app.json expo.android.versionCode 不一致/,
  );
});

test('纯 JS 业务升级保留原生基础包身份和 runtime', () => {
  const root = fixture();
  writeFileSync(path.join(root, 'package.json'), JSON.stringify({ version: '1.1.0' }));
  writeFileSync(path.join(root, 'package-lock.json'), JSON.stringify({ version: '1.1.0', packages: { '': { version: '1.1.0' } } }));
  writeFileSync(path.join(root, 'app.json'), JSON.stringify({ expo: {
    version: '1.1.0', runtimeVersion: 'android-1.0.39-native39', android: { versionCode: 39, package: 'com.mnnu.ereport' },
  } }));
  assert.equal(verifySourceVersionContract({ appRoot: root }).versionName, '1.0.39');
});

test('拒绝非 x.y.z 或超出编码范围的版本', () => {
  assert.throws(() => calculateAndroidVersionCode('2.0'), /x.y.z/);
  assert.throws(() => calculateAndroidVersionCode('2.1000.0'), /0 <= y,z < 1000/);
});

test('拒绝发布参数与源码版本不一致', () => {
  assert.throws(
    () => verifySourceVersionContract({ appRoot: fixture(), expectedVersionCode: 40 }),
    /发布参数 VersionCode 不一致/,
  );
});

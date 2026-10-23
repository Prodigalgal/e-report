import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { zipSync } from 'fflate';

import { calculateAndroidVersionCode, validateEmbeddedRelease, verifyEmbeddedRelease, verifySourceVersionContract } from '../scripts/verify-version-contract.mjs';

function fixture({ version = '1.0.39', versionCode = 39, runtimeVersion = 'android-1.0.39-native39' } = {}) {
  const root = path.join(tmpdir(), `e-report-version-${crypto.randomUUID()}`);
  mkdirSync(path.join(root, 'android', 'app'), { recursive: true });
  writeFileSync(path.join(root, 'package.json'), JSON.stringify({ version }));
  writeFileSync(path.join(root, 'package-lock.json'), JSON.stringify({ version, packages: { '': { version } } }));
  writeFileSync(path.join(root, 'app.json'), JSON.stringify({
    expo: { version, runtimeVersion, android: { versionCode, package: 'com.mnnu.ereport' } },
  }));
  writeFileSync(path.join(root, 'native-runtime.json'), JSON.stringify({
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

test('业务升级必须更新 APK 身份，同时保留兼容运行时', () => {
  const root = fixture();
  writeFileSync(path.join(root, 'package.json'), JSON.stringify({ version: '1.1.0' }));
  writeFileSync(path.join(root, 'package-lock.json'), JSON.stringify({ version: '1.1.0', packages: { '': { version: '1.1.0' } } }));
  writeFileSync(path.join(root, 'app.json'), JSON.stringify({ expo: {
    version: '1.1.0', runtimeVersion: 'android-1.0.39-native39', android: { versionCode: 1000, package: 'com.mnnu.ereport' },
  } }));
  assert.throws(() => verifySourceVersionContract({ appRoot: root }), /Gradle versionCode/);
  writeFileSync(path.join(root, 'android', 'app', 'build.gradle'), 'versionCode 1000\nversionName "1.1.0"\n');
  assert.deepEqual(verifySourceVersionContract({ appRoot: root }), {
    versionCode: 1000, versionName: '1.1.0', runtimeVersion: 'android-1.0.39-native39',
  });
});

test('APK 内置版本和源码必须属于当前发布候选', () => {
  const current = { version: '6.11.0', sourceSha: 'a'.repeat(40), runtimeVersion: 'android-6.8.2-native5008002' };
  assert.deepEqual(validateEmbeddedRelease(current, current), current);
  for (const change of [{ version: '6.8.2' }, { sourceSha: 'b'.repeat(40) }, { runtimeVersion: 'other' }]) {
    assert.throws(() => validateEmbeddedRelease({ ...current, ...change }, current), /内置业务版本/);
  }
});

test('APK 资源必须包含当前 Expo 配置和非空业务 bundle，不能仅伪装发布清单', () => {
  const root = fixture();
  const apk = path.join(root, 'synthetic.apk');
  const expected = { version: '6.11.0', sourceSha: 'a'.repeat(40), runtimeVersion: 'android-6.8.2-native5008002' };
  const entries = {
    'assets/e-report-release.json': Buffer.from(JSON.stringify(expected)),
    'assets/app.config': Buffer.from(JSON.stringify({ version: expected.version, runtimeVersion: expected.runtimeVersion })),
    'assets/app.manifest': Buffer.from(JSON.stringify({ assets: [{ key: 'synthetic-bundle' }] })),
    'assets/index.android.bundle': Buffer.from('synthetic embedded JS'),
  };
  writeFileSync(apk, zipSync(entries));
  assert.deepEqual(verifyEmbeddedRelease(apk, expected), expected);
  writeFileSync(apk, zipSync({ ...entries, 'assets/app.config': Buffer.from(JSON.stringify({ version: '6.8.2', runtimeVersion: expected.runtimeVersion })) }));
  assert.throws(() => verifyEmbeddedRelease(apk, expected), /APK 内置 Expo 业务版本/);
  writeFileSync(apk, zipSync({ ...entries, 'assets/index.android.bundle': Buffer.alloc(0) }));
  assert.throws(() => verifyEmbeddedRelease(apk, expected), /APK 缺少/);
});

test('拒绝非 x.y.z 或超出编码范围的版本', () => {
  assert.throws(() => calculateAndroidVersionCode('2.0'), /x.y.z/);
  assert.throws(() => calculateAndroidVersionCode('2.1000.0'), /0 <= y,z < 1000/);
  assert.throws(() => calculateAndroidVersionCode('6.011.0'), /x.y.z/);
  assert.throws(() => calculateAndroidVersionCode('3000.0.0'), /构建号可用范围/);
});

test('拒绝发布参数与源码版本不一致', () => {
  assert.throws(
    () => verifySourceVersionContract({ appRoot: fixture(), expectedVersionCode: 40 }),
    /发布参数 VersionCode 不一致/,
  );
});

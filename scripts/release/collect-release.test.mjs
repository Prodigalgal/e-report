import assert from 'node:assert/strict';
import test from 'node:test';
import { createHash } from 'node:crypto';
import { validateNativeArtifact } from './collect-release.mjs';

test('Native 发布必须是本次源码及业务版本的完整 APK', () => {
  const bytes = Buffer.from('signed APK fixture');
  const contract = { version: '6.8.1', versionCode: 5008001, runtimeVersion: 'android-6.8.0-native5008000', applicationId: 'com.mnnu.ereport' };
  const manifest = { ...contract, edition: 'NATIVE', nativeFingerprint: 'f'.repeat(64), businessVersion: '6.8.1', sourceSha: 'a'.repeat(40),
    artifactSourceSha: 'a'.repeat(40), size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') };
  assert.doesNotThrow(() => validateNativeArtifact(manifest, contract, 'f'.repeat(64), 'a'.repeat(40), '6.8.1', bytes));
  for (const change of [{ artifactSourceSha: 'b'.repeat(40) }, { edition: 'LITE' }, { version: '6.8.0' },
    { runtimeVersion: 'wrong' }, { sourceSha: 'b'.repeat(40) }, { businessVersion: '6.8.0' },
    { applicationId: 'com.mnnu.ereport.lite' }, { nativeFingerprint: '0'.repeat(64) }, { sha256: '0'.repeat(64) }]) {
    assert.throws(() => validateNativeArtifact({ ...manifest, ...change }, contract, 'f'.repeat(64), 'a'.repeat(40), '6.8.1', bytes), /契约不一致/);
  }
});

test('Lite 仍可复用相同壳的已验证不可变 APK', () => {
  const bytes = Buffer.from('signed Lite APK fixture');
  const contract = { version: '6.8.1', versionCode: 5008001, runtimeVersion: 'android-lite-6.8.1-native5008001', applicationId: 'com.mnnu.ereport.lite' };
  const manifest = { ...contract, edition: 'LITE', nativeFingerprint: 'f'.repeat(64), businessVersion: '6.11.0', sourceSha: 'a'.repeat(40),
    artifactSourceSha: 'b'.repeat(40), size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') };
  assert.doesNotThrow(() => validateNativeArtifact(manifest, contract, 'f'.repeat(64), 'a'.repeat(40), '6.11.0', bytes));
});

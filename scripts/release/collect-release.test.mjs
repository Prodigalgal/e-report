import assert from 'node:assert/strict';
import test from 'node:test';
import { createHash } from 'node:crypto';
import { validateNativeArtifact } from './collect-release.mjs';

test('a JS-only business release reuses a verified native foundation from an earlier source', () => {
  const bytes = Buffer.from('signed APK fixture');
  const contract = { version: '6.8.0', versionCode: 5008000, runtimeVersion: 'android-6.8.0-native5008000', applicationId: 'com.mnnu.ereport' };
  const manifest = { ...contract, nativeFingerprint: 'f'.repeat(64), businessVersion: '6.8.1', sourceSha: 'a'.repeat(40),
    artifactSourceSha: 'b'.repeat(40), size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') };
  assert.doesNotThrow(() => validateNativeArtifact(manifest, contract, 'f'.repeat(64), 'a'.repeat(40), '6.8.1', bytes));
  for (const change of [{ runtimeVersion: 'wrong' }, { sourceSha: 'b'.repeat(40) }, { businessVersion: '6.8.0' },
    { applicationId: 'com.mnnu.ereport.lite' }, { nativeFingerprint: '0'.repeat(64) }, { sha256: '0'.repeat(64) }]) {
    assert.throws(() => validateNativeArtifact({ ...manifest, ...change }, contract, 'f'.repeat(64), 'a'.repeat(40), '6.8.1', bytes), /契约不一致/);
  }
});

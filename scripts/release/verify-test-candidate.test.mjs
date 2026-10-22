import assert from 'node:assert/strict';
import test from 'node:test';
import { validateCandidateApk } from './verify-test-candidate.mjs';
import { validateOtaIdentity } from './verify-ota-endpoint.mjs';

test('production promotion requires the same edition, native identity and APK hash as the accepted candidate', () => {
  const contract = { version: '6.8.0', versionCode: 5008000, runtimeVersion: 'android-lite-6.8.0-native5008000' };
  const published = { ...contract, versionName: contract.version, edition: 'LITE', sha256: 'a'.repeat(64) };
  assert.doesNotThrow(() => validateCandidateApk(published, contract, 'a'.repeat(64), 'LITE'));
  for (const changes of [{ edition: 'NATIVE' }, { sha256: 'b'.repeat(64) }, { versionCode: 5007002 }, { runtimeVersion: 'other' }]) {
    assert.throws(() => validateCandidateApk({ ...published, ...changes }, contract, 'a'.repeat(64), 'LITE'), /候选不一致/);
  }
});

test('OTA promotion rejects another environment, source, runtime or business candidate even with a valid signature', () => {
  const expected = { kind: 'UPDATE', environment: 'test', runtimeVersion: 'android-6.8.0-native5008000', version: '6.8.1', sourceSha: 'a'.repeat(40) };
  const manifest = { id: 'update-id', runtimeVersion: expected.runtimeVersion, metadata: { ...expected, id: 'update-id' } };
  assert.doesNotThrow(() => validateOtaIdentity(manifest, expected));
  for (const changes of [{ environment: 'production' }, { sourceSha: 'b'.repeat(40) }, { runtimeVersion: 'other' }, { version: '6.8.0' }]) {
    assert.throws(() => validateOtaIdentity({ ...manifest, metadata: { ...manifest.metadata, ...changes } }, expected), /验收候选不一致/);
  }
});

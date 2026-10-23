import assert from 'node:assert/strict';
import test from 'node:test';
import { verifyEmbeddedApkUpdatePolicy } from './verify-ota-endpoint.mjs';

const expected = { kind: 'UPDATE', version: '6.11.0', environment: 'test', runtimeVersion: 'android-6.8.2-native5008002' };

test('发布验收要求同版本完整 APK 不接收重复 OTA', async () => {
  await verifyEmbeddedApkUpdatePolicy({ apiBaseUrl: 'https://deployment.example.test/api', expected,
    fetchImpl: async (url, { headers }) => {
      assert.equal(headers['e-report-embedded-version'], expected.version);
      assert.equal(headers['expo-runtime-version'], expected.runtimeVersion);
      return new Response(null, { status: 204, headers: { 'expo-protocol-version': '1' } });
    } });
  for (const status of [200, 404, 503]) {
    await assert.rejects(verifyEmbeddedApkUpdatePolicy({ apiBaseUrl: 'https://deployment.example.test/api', expected,
      fetchImpl: async () => new Response('', { status }) }), /重复下发同版本 OTA/);
  }
});

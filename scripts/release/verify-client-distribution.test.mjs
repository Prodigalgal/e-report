import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import test from 'node:test';
import { verifyPublishedApk } from './verify-client-distribution.mjs';

const bytes = Buffer.alloc(2048, 42);
const published = { edition: 'LITE', versionName: '6.8.1', apkSize: bytes.length,
  sha256: createHash('sha256').update(bytes).digest('hex'), apkUrl: 'https://test.example/download/android-lite' };
const headers = { 'content-type': 'application/vnd.android.package-archive', 'content-length': String(bytes.length) };
const fixture = overrides => async (url, options) => {
  if (url.includes('/download/')) return new Response(null, { status: 302, headers: { location: 'https://cdn.example/lite.apk' } });
  if (options.method === 'HEAD') return overrides?.head ?? new Response(null, { headers });
  if (options.headers?.Range) return overrides?.range ?? new Response(bytes.subarray(0, 1024), { status: 206, headers: { 'content-range': `bytes 0-1023/${bytes.length}` } });
  return overrides?.body ?? new Response(bytes, { headers });
};

test('verifies public redirects, Range and the real complete APK digest', async () => {
  await verifyPublishedApk(published, { fetchImpl: fixture() });
});
test('rejects an HTTP 200 web fallback before treating it as a published APK', async () => {
  await assert.rejects(verifyPublishedApk(published, { fetchImpl: fixture({ head: new Response(null, { headers: { ...headers, 'content-type': 'text/html' } }) }) }), /返回了网页/);
});
test('rejects an invalid partial response', async () => {
  await assert.rejects(verifyPublishedApk(published, { fetchImpl: fixture({ range: new Response(bytes) }) }), /分片下载响应无效/);
});
test('rejects modified or truncated public bytes even when release metadata matches', async () => {
  for (const body of [Buffer.alloc(2048, 43), bytes.subarray(0, 2000)]) {
    await assert.rejects(verifyPublishedApk(published, { fetchImpl: fixture({ body: new Response(body) }) }), /摘要与不可变候选不一致/);
  }
});
test('rejects redirects to cleartext and overlarge metadata', async () => {
  await assert.rejects(verifyPublishedApk(published, { fetchImpl: async () => new Response(null, { status: 302, headers: { location: 'http://cdn.example/file' } }) }), /HTTPS/);
  await assert.rejects(verifyPublishedApk({ ...published, apkSize: 301 * 1024 * 1024 }, { fetchImpl: fixture() }), /元数据无效/);
});

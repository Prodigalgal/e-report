import assert from 'node:assert/strict';
import test from 'node:test';

import {
  canonicalRequestPayload,
  canonicalTarget,
} from '../src/security/request-signature-canonical.ts';

test('设备签名规范化目标会保留编码并按原始查询参数排序', () => {
  assert.equal(
    canonicalTarget('https://example.test/api/work-orders/search?status=OPEN&page=1&status=WAITING'),
    '/api/work-orders/search?page=1&status=OPEN&status=WAITING',
  );
});

test('设备签名载荷绑定方法、目标、正文摘要、时间、nonce 与会话', () => {
  assert.equal(canonicalRequestPayload({
    url: 'https://example.test/auth/password',
    method: 'post',
    bodyDigest: 'a'.repeat(64),
    created: 100,
    expires: 160,
    nonce: 'nonce-value',
    sessionId: '018f12d0-0000-7000-8000-000000000001',
  }), [
    'e-report-request-v1',
    '@method:POST',
    '@target-uri:/auth/password',
    `content-sha256:${'a'.repeat(64)}`,
    'created:100',
    'expires:160',
    'nonce:nonce-value',
    'session-id:018f12d0-0000-7000-8000-000000000001',
  ].join('\n'));
});

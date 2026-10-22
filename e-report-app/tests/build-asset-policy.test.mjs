import assert from 'node:assert/strict';
import test from 'node:test';

import { detectBlockedImageFormat } from '../scripts/verify-build-assets.mjs';

test('构建资产策略按魔数拒绝 ICNS、JXL 与 HEIF/AVIF', () => {
  assert.equal(detectBlockedImageFormat(Buffer.from('icns0000', 'ascii')), 'ICNS');
  assert.equal(detectBlockedImageFormat(Buffer.from([0xff, 0x0a, 0, 0])), 'JXL');
  assert.equal(detectBlockedImageFormat(Buffer.concat([
    Buffer.from([0, 0, 0, 12]), Buffer.from('JXL ', 'ascii'), Buffer.from([0x0d, 0x0a, 0x87, 0x0a]),
  ])), 'JXL');
  assert.equal(detectBlockedImageFormat(Buffer.from('\0\0\0\u0018ftypheic\0\0\0\0mif1', 'binary')), 'HEIF/AVIF');
});

test('构建资产策略允许常规 PNG/JPEG 头', () => {
  assert.equal(detectBlockedImageFormat(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a])), null);
  assert.equal(detectBlockedImageFormat(Buffer.from([0xff, 0xd8, 0xff, 0xe0])), null);
});

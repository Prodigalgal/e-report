import assert from 'node:assert/strict';
import test from 'node:test';

import {
  attachmentMediaKind,
  buildAttachmentApiPath,
  buildLocalMediaFileName,
  supportsAttachmentPreview,
} from '../src/features/work-orders/attachment-media.ts';

test('classifies image and video attachments for preview and album storage', () => {
  assert.equal(attachmentMediaKind('image/jpeg'), 'photo');
  assert.equal(attachmentMediaKind(' VIDEO/MP4 '), 'video');
  assert.equal(attachmentMediaKind('application/pdf'), 'unsupported');
  assert.equal(supportsAttachmentPreview('image/png'), true);
  assert.equal(supportsAttachmentPreview('application/zip'), false);
});

test('keeps readable names and adds a media extension when missing', () => {
  assert.equal(buildLocalMediaFileName({ id: '1', fileName: '现场照片.jpg', fileType: 'image/jpeg' }), '现场照片.jpg');
  assert.equal(buildLocalMediaFileName({ id: '2', fileName: '处置视频', fileType: 'video/mp4' }), '处置视频.mp4');
  assert.equal(buildLocalMediaFileName({ id: '3', fileName: '非法/名称', fileType: 'image/jpeg' }), '非法_名称.jpg');
});

test('builds an attachment-id API path that is safe for gateway routing', () => {
  assert.equal(
    buildAttachmentApiPath('019ff4e4-1660-7dac-ade2-8fcff1959a82', '019ff4e4-1660-7dac-ade2-8fcff1959a83'),
    '/work-orders/attachments/019ff4e4-1660-7dac-ade2-8fcff1959a82/file/019ff4e4-1660-7dac-ade2-8fcff1959a83',
  );
  assert.throws(() => buildAttachmentApiPath('', 'photo.png'), /参数不完整/);
});

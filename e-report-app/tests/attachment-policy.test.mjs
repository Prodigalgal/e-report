import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildAttachmentBytesLimitMessage,
  buildAttachmentCountLimitMessage,
  MAX_ATTACHMENT_COUNT,
  MAX_ATTACHMENT_TOTAL_BYTES,
  MAX_ATTACHMENT_TOTAL_MB,
} from '../src/utils/attachment-policy.ts';

test('APP附件数量允许10个并明确拒绝第11个', () => {
  assert.equal(MAX_ATTACHMENT_COUNT, 10);
  assert.equal(buildAttachmentCountLimitMessage(9, 1), null);
  assert.match(buildAttachmentCountLimitMessage(9, 2), /当前已有9个.*本次选择2个.*达到11个/);
});

test('APP附件总量允许100兆字节并明确拒绝超限数据', () => {
  assert.equal(MAX_ATTACHMENT_TOTAL_MB, 100);
  assert.equal(buildAttachmentBytesLimitMessage(MAX_ATTACHMENT_TOTAL_BYTES), null);
  assert.match(buildAttachmentBytesLimitMessage(MAX_ATTACHMENT_TOTAL_BYTES + 100 * 1024), /100兆字节.*100\.1 兆字节/);
});

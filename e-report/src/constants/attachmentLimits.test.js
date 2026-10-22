import assert from 'node:assert/strict';
import test from 'node:test';

import {
    ATTACHMENT_TOTAL_LIMIT_MESSAGE,
    MAX_ATTACHMENT_FILES,
    MAX_ATTACHMENT_FILE_SIZE_MB,
    MAX_ATTACHMENT_TOTAL_MB,
} from './attachmentLimits.js';

test('工单附件限制统一为10个和100兆字节', () => {
    assert.equal(MAX_ATTACHMENT_FILES, 10);
    assert.equal(MAX_ATTACHMENT_FILE_SIZE_MB, 100);
    assert.equal(MAX_ATTACHMENT_TOTAL_MB, 100);
    assert.match(ATTACHMENT_TOTAL_LIMIT_MESSAGE, /100兆字节/);
});

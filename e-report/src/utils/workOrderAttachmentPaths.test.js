import assert from 'node:assert/strict';
import test from 'node:test';

import { buildWorkOrderAttachmentArchivePath, buildWorkOrderAttachmentPath } from './workOrderAttachmentPaths.js';

test('附件路径使用附件 UUID，避免存储路径中的目录分隔符触发网关重定向', () => {
    assert.equal(
        buildWorkOrderAttachmentPath('work-order-id', 'attachment-id'),
        '/work-orders/attachments/work-order-id/file/attachment-id',
    );
    assert.equal(
        buildWorkOrderAttachmentArchivePath('work-order-id'),
        '/work-orders/work-order-id/attachments/zip',
    );
});

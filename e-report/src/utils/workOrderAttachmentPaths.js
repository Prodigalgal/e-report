export const buildWorkOrderAttachmentPath = (workOrderId, attachmentId) => (
    `/work-orders/attachments/${encodeURIComponent(workOrderId)}/file/${encodeURIComponent(attachmentId)}`
);

export const buildWorkOrderAttachmentArchivePath = (workOrderId) => (
    `/work-orders/${encodeURIComponent(workOrderId)}/attachments/zip`
);

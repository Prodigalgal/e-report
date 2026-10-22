export type AttachmentMediaKind = 'photo' | 'video' | 'unsupported';

type AttachmentMediaMetadata = {
  id: string;
  fileName?: string | null;
  storedFileName?: string | null;
  fileType?: string | null;
};

const DEFAULT_EXTENSIONS: Record<Exclude<AttachmentMediaKind, 'unsupported'>, string> = {
  photo: '.jpg',
  video: '.mp4',
};

export function attachmentMediaKind(fileType?: string | null): AttachmentMediaKind {
  const normalizedFileType = fileType?.trim().toLowerCase() || '';
  if (normalizedFileType.startsWith('image/')) return 'photo';
  if (normalizedFileType.startsWith('video/')) return 'video';
  return 'unsupported';
}

export function buildLocalMediaFileName(attachment: AttachmentMediaMetadata): string {
  const mediaKind = attachmentMediaKind(attachment.fileType);
  const sourceName = attachment.fileName?.trim()
    || attachment.storedFileName?.trim()
    || `attachment-${attachment.id}`;
  const sanitizedName = sourceName
    .replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_')
    .replace(/[. ]+$/g, '')
    .trim() || `attachment-${attachment.id}`;

  if (/\.[A-Za-z0-9]{1,10}$/.test(sanitizedName) || mediaKind === 'unsupported') {
    return sanitizedName;
  }
  return `${sanitizedName}${DEFAULT_EXTENSIONS[mediaKind]}`;
}

export function supportsAttachmentPreview(fileType?: string | null): boolean {
  return attachmentMediaKind(fileType) !== 'unsupported';
}

export function buildAttachmentApiPath(workOrderId: string, attachmentId: string): string {
  const normalizedWorkOrderId = workOrderId.trim();
  const normalizedAttachmentId = attachmentId.trim();
  if (!normalizedWorkOrderId || !normalizedAttachmentId) {
    throw new Error('附件下载参数不完整。');
  }
  return `/work-orders/attachments/${encodeURIComponent(normalizedWorkOrderId)}/file/${encodeURIComponent(normalizedAttachmentId)}`;
}

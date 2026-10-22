import { File } from 'expo-file-system';

import type { LocalAttachment } from '@/types/api';
import {
  buildAttachmentBytesLimitMessage,
  buildAttachmentCountLimitMessage,
  formatAttachmentSize,
  MAX_ATTACHMENT_COUNT,
  MAX_ATTACHMENT_TOTAL_BYTES,
  MAX_ATTACHMENT_TOTAL_MB,
} from '@/utils/attachment-policy';

export {
  formatAttachmentSize,
  MAX_ATTACHMENT_COUNT,
  MAX_ATTACHMENT_TOTAL_BYTES,
  MAX_ATTACHMENT_TOTAL_MB,
};

export function getAttachmentSize(attachment: LocalAttachment): number {
  try {
    const file = new File(attachment.uri);
    if (typeof file.size === 'number' && Number.isFinite(file.size) && file.size > 0) {
      return file.size;
    }
  } catch {
    // 无法读取本地文件元数据时，回退到选择器返回的文件大小。
  }
  return typeof attachment.size === 'number' && Number.isFinite(attachment.size) && attachment.size > 0
    ? attachment.size
    : 0;
}

export function getAttachmentTotalSize(attachments: LocalAttachment[]): number {
  return attachments.reduce((total, attachment) => total + getAttachmentSize(attachment), 0);
}

export function buildAttachmentLimitMessage(current: LocalAttachment[], incoming: LocalAttachment[]): string | null {
  const countLimitMessage = buildAttachmentCountLimitMessage(current.length, incoming.length);
  if (countLimitMessage) {
    return countLimitMessage;
  }
  const totalBytes = getAttachmentTotalSize([...current, ...incoming]);
  return buildAttachmentBytesLimitMessage(totalBytes);
}

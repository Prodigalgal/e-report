export const MAX_ATTACHMENT_COUNT = 10;
export const MAX_ATTACHMENT_TOTAL_BYTES = 100 * 1024 * 1024;
export const MAX_ATTACHMENT_TOTAL_MB = 100;
export const ATTACHMENT_TOTAL_LIMIT_MESSAGE =
  `上传材料总大小不能超过${MAX_ATTACHMENT_TOTAL_MB}兆字节，请删除部分附件，或压缩后重新上传。`;

export function buildAttachmentCountLimitMessage(currentCount: number, incomingCount: number): string | null {
  const nextCount = currentCount + incomingCount;
  if (nextCount <= MAX_ATTACHMENT_COUNT) {
    return null;
  }
  return `附件最多上传${MAX_ATTACHMENT_COUNT}个，当前已有${currentCount}个，本次选择${incomingCount}个，选择后将达到${nextCount}个。请减少本次选择数量。`;
}

export function buildAttachmentBytesLimitMessage(totalBytes: number): string | null {
  if (totalBytes <= MAX_ATTACHMENT_TOTAL_BYTES) {
    return null;
  }
  return `上传材料总大小不能超过${MAX_ATTACHMENT_TOTAL_MB}兆字节，当前选择后合计${formatAttachmentSize(totalBytes)}。请删除部分附件，或压缩后重新上传。`;
}

export function formatAttachmentSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} 字节`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} 千字节`;
  return `${(bytes / 1024 / 1024).toFixed(1)} 兆字节`;
}

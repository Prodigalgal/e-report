import { File, FileMode, type FileHandle } from 'expo-file-system';

import { apiFetch } from '@/api/http';
import type {
  FileTransferChunkResponse,
  FileTransferPurpose,
  FileTransferSession,
  LocalAttachment,
} from '@/types/api';

const DEFAULT_UPLOAD_CHUNK_BYTES = 2 * 1024 * 1024;
const MAX_CHUNK_RETRIES = 5;

export type FileTransferProgress = {
  fileIndex: number;
  fileCount: number;
  fileName: string;
  uploadedBytes: number;
  totalBytes: number;
  retryCount: number;
  stage: 'uploading' | 'completed';
  message?: string;
};

export async function uploadLocalAttachmentsInChunks(
  attachments: LocalAttachment[],
  purpose: FileTransferPurpose,
  onProgress?: (progress: FileTransferProgress) => void,
) {
  const completedUploadIds: string[] = [];
  for (let index = 0; index < attachments.length; index += 1) {
    const attachment = attachments[index];
    const uploadId = await uploadSingleAttachment(attachment, purpose, {
      fileIndex: index,
      fileCount: attachments.length,
      onProgress,
    });
    completedUploadIds.push(uploadId);
  }
  return completedUploadIds;
}

async function uploadSingleAttachment(
  attachment: LocalAttachment,
  purpose: FileTransferPurpose,
  progressContext: {
    fileIndex: number;
    fileCount: number;
    onProgress?: (progress: FileTransferProgress) => void;
  },
) {
  const sourceFile = new File(attachment.uri);
  let handle: FileHandle;
  try {
    handle = sourceFile.open(FileMode.ReadOnly);
  } catch {
    throw new Error(`无法读取文件：${attachment.name}。请移除后重新拍摄或从相册选择。`);
  }

  const totalSize = positiveFileSize(handle.size) || positiveFileSize(sourceFile.size);
  if (!totalSize || totalSize <= 0) {
    handle.close();
    throw new Error(`无法读取文件大小：${attachment.name}`);
  }
  try {
    const session = await apiFetch<FileTransferSession>('/file-transfers/uploads', {
      method: 'POST',
      body: JSON.stringify({
        purpose,
        fileName: attachment.name,
        contentType: attachment.type || 'application/octet-stream',
        totalSize,
        chunkSize: DEFAULT_UPLOAD_CHUNK_BYTES,
      }),
    });

    const chunkSize = session.chunkSize || DEFAULT_UPLOAD_CHUNK_BYTES;
    let readOffset = 0;
    let uploadedBytes = 0;
    let chunkIndex = 0;
    while (readOffset < totalSize) {
      const nextChunkSize = Math.min(chunkSize, totalSize - readOffset);
      handle.offset = readOffset;
      const chunkBytes = handle.readBytes(nextChunkSize);
      if (chunkBytes.length !== nextChunkSize) {
        throw new Error(`文件内容读取不完整：${attachment.name}。请移除后重新拍摄或从相册选择。`);
      }
      const chunkStart = readOffset;
      const chunkEnd = readOffset + chunkBytes.length - 1;
      let retryCount = 0;
      while (true) {
        try {
          const chunkResult = await uploadChunk(session.uploadId, chunkIndex, chunkStart, chunkEnd, totalSize, chunkBytes);
          uploadedBytes = Math.max(uploadedBytes, chunkResult.receivedBytes);
          progressContext.onProgress?.({
            fileIndex: progressContext.fileIndex,
            fileCount: progressContext.fileCount,
            fileName: attachment.name,
            uploadedBytes,
            totalBytes: totalSize,
            retryCount,
            stage: 'uploading',
          });
          break;
        } catch (error) {
          retryCount += 1;
          if (retryCount > MAX_CHUNK_RETRIES) {
            throw new Error(`文件 ${attachment.name} 上传中断，系统已自动重试多次仍未成功，请检查网络后再次提交。`);
          }
          progressContext.onProgress?.({
            fileIndex: progressContext.fileIndex,
            fileCount: progressContext.fileCount,
            fileName: attachment.name,
            uploadedBytes,
            totalBytes: totalSize,
            retryCount,
            stage: 'uploading',
          });
          await delay(backoffMs(retryCount));
        }
      }
      readOffset = chunkEnd + 1;
      chunkIndex += 1;
    }

    const completedSession = await apiFetch<FileTransferSession>(`/file-transfers/uploads/${session.uploadId}/complete`, {
      method: 'POST',
    });
    progressContext.onProgress?.({
      fileIndex: progressContext.fileIndex,
      fileCount: progressContext.fileCount,
      fileName: attachment.name,
      uploadedBytes: totalSize,
      totalBytes: totalSize,
      retryCount: 0,
      stage: 'completed',
    });
    return completedSession.uploadId;
  } finally {
    handle.close();
  }
}

function uploadChunk(
  uploadId: string,
  chunkIndex: number,
  start: number,
  end: number,
  totalSize: number,
  chunkBytes: Uint8Array,
) {
  return apiFetch<FileTransferChunkResponse>(`/file-transfers/uploads/${uploadId}/chunks/${chunkIndex}`, {
    method: 'PUT',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/octet-stream',
      'Content-Range': `bytes ${start}-${end}/${totalSize}`,
    },
    body: chunkBytes as unknown as BodyInit,
    timeoutMs: 120000,
  });
}

function backoffMs(retryCount: number) {
  return Math.min(1200 * retryCount, 5000);
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function positiveFileSize(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : 0;
}

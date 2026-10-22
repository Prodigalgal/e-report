import { ActivityAction, startActivityAsync } from 'expo-intent-launcher';
import { File, FileMode, Paths } from 'expo-file-system';
import { sha256 } from 'js-sha256';
import { NativeModules, Platform } from 'react-native';

import { APP_PACKAGE_ID } from '@/api/app-update-api';
import type { AppUpdateCheckResponse } from '@/types/api';

const APK_MIME_TYPE = 'application/vnd.android.package-archive';
const FLAG_GRANT_READ_URI_PERMISSION = 1;
const HASH_CHUNK_BYTES = 1024 * 1024;
const DEFAULT_DOWNLOAD_CHUNK_BYTES = 2 * 1024 * 1024;
const DOWNLOAD_CONCURRENCY = 4;
const MAX_CHUNK_RETRIES = 5;

type DownloadRange = {
  start: number;
  end: number;
};

type DownloadedChunk = DownloadRange & {
  bytes: Uint8Array;
  retryCount: number;
};

export type AppUpdateProgress = {
  phase: 'download' | 'verify' | 'install';
  progress: number;
  label: string;
};

export async function downloadVerifyAndInstallAppUpdate(
  update: AppUpdateCheckResponse,
  onProgress: (progress: AppUpdateProgress) => void,
) {
  if (Platform.OS !== 'android') {
    throw new Error('当前升级方式仅支持安卓设备。');
  }
  if (!update.apkUrl || !update.versionCode || !update.sha256) {
    throw new Error('升级信息不完整，请联系管理员重新发布安装包。');
  }
  if (update.edition === 'LITE' || update.runtimeVersion?.startsWith('android-lite-')) {
    throw new Error('原生版仅支持原生版升级；切换 Lite 请先卸载，再下载安装 Lite。');
  }
  if (!update.apkSize || update.apkSize <= 0) {
    throw new Error('升级包大小信息缺失，请联系管理员重新发布安装包。');
  }

  const apkFile = new File(Paths.cache, `e-report-${update.versionCode}.apk`);
  onProgress({ phase: 'download', progress: 0, label: '正在下载安装包' });
  const downloadedFile = await downloadApkInChunks(update, apkFile, onProgress);

  verifyFileSize(downloadedFile, update.apkSize);
  onProgress({ phase: 'verify', progress: 0, label: '正在校验安装包' });
  await verifyFileSha256(downloadedFile, update.sha256, (progress) => {
    onProgress({ phase: 'verify', progress, label: '正在校验安装包' });
  });
  const verifier = NativeModules.EReportAppPackage as { verify(uri: string, versionCode: number): Promise<void> } | undefined;
  if (!verifier) throw new Error('当前安装包不支持应用身份校验，请从官方下载页重新安装原生版。');
  await verifier.verify(downloadedFile.uri, update.versionCode);

  onProgress({ phase: 'install', progress: 1, label: '正在打开系统安装器' });
  await startActivityAsync('android.intent.action.VIEW', {
    data: downloadedFile.contentUri,
    type: APK_MIME_TYPE,
    flags: FLAG_GRANT_READ_URI_PERMISSION,
  });
}

export async function openUnknownSourceSettings() {
  if (Platform.OS !== 'android') {
    return;
  }
  await startActivityAsync(ActivityAction.MANAGE_UNKNOWN_APP_SOURCES, {
    data: `package:${APP_PACKAGE_ID}`,
  });
}

export function friendlyAppUpdateError(error: unknown) {
  const message = error instanceof Error ? error.message : String(error || '');
  const lowerMessage = message.toLowerCase();
  if (message.includes('原生版') || message.includes('Lite') || message.includes('安装包签名')) return message;
  if (lowerMessage.includes('unabletodownload') || lowerMessage.includes('download') || lowerMessage.includes('http')) {
    return '安装包下载失败。系统已自动分片重试多次仍未成功，请检查网络后再次点击升级；如果多次失败，请联系管理员确认升级包是否已发布。';
  }
  if (lowerMessage.includes('sha-256') || lowerMessage.includes('sha256') || lowerMessage.includes('校验')) {
    return '安装包校验失败，文件可能下载不完整。请重新下载；如果仍失败，请联系管理员重新发布安装包。';
  }
  if (lowerMessage.includes('activity') || lowerMessage.includes('intent') || lowerMessage.includes('permission')) {
    return '无法打开系统安装器。请允许本应用安装未知来源应用，然后重新点击升级。';
  }
  if (message.includes('升级信息不完整') || message.includes('Android') || message.includes('安卓')) {
    return message;
  }
  return '升级失败，请稍后重试；如果问题持续，请联系管理员处理。';
}

async function downloadApkInChunks(
  update: AppUpdateCheckResponse,
  apkFile: File,
  onProgress: (progress: AppUpdateProgress) => void,
) {
  if (apkFile.exists) {
    apkFile.delete();
  }
  apkFile.create({ overwrite: true, intermediates: true });
  const totalSize = update.apkSize || 0;
  const chunkSize = normalizeChunkSize(update.downloadChunkSize);
  const handle = apkFile.open(FileMode.ReadWrite);
  let downloadedBytes = 0;
  try {
    while (downloadedBytes < totalSize) {
      const ranges = createDownloadBatch(downloadedBytes, totalSize, chunkSize);
      const settledChunks = await Promise.allSettled(
        ranges.map((range) =>
          downloadChunkWithRetry(update, range, totalSize, (retryCount) => {
            onProgress({
              phase: 'download',
              progress: Math.min(downloadedBytes / totalSize, 1),
              label: `网络波动，正在自动重试第${retryCount}次`,
            });
          }),
        ),
      );
      const failedChunk = settledChunks.find((result) => result.status === 'rejected');
      if (failedChunk?.status === 'rejected') {
        throw failedChunk.reason;
      }

      const downloadedChunks = settledChunks
        .filter((result): result is PromiseFulfilledResult<DownloadedChunk> => result.status === 'fulfilled')
        .map((result) => result.value)
        .sort((left, right) => left.start - right.start);
      for (const chunk of downloadedChunks) {
        handle.offset = chunk.start;
        handle.writeBytes(chunk.bytes);
        downloadedBytes = chunk.end + 1;
        onProgress({
          phase: 'download',
          progress: Math.min(downloadedBytes / totalSize, 1),
          label: chunk.retryCount > 0 ? `网络波动，已自动重试${chunk.retryCount}次` : '正在并发分片下载安装包',
        });
      }
    }
  } finally {
    handle.close();
  }
  return apkFile;
}

function createDownloadBatch(start: number, totalSize: number, chunkSize: number) {
  const ranges: DownloadRange[] = [];
  let nextStart = start;
  while (nextStart < totalSize && ranges.length < DOWNLOAD_CONCURRENCY) {
    const end = Math.min(nextStart + chunkSize - 1, totalSize - 1);
    ranges.push({ start: nextStart, end });
    nextStart = end + 1;
  }
  return ranges;
}

async function downloadChunkWithRetry(
  update: AppUpdateCheckResponse,
  range: DownloadRange,
  totalSize: number,
  onRetry: (retryCount: number) => void,
): Promise<DownloadedChunk> {
  let retryCount = 0;
  while (true) {
    try {
      const bytes = await fetchApkChunk(update, range.start, range.end, totalSize);
      return { ...range, bytes, retryCount };
    } catch (error) {
      retryCount += 1;
      if (retryCount > MAX_CHUNK_RETRIES) {
        throw new Error(`安装包分片下载失败：${error instanceof Error ? error.message : String(error || '')}`);
      }
      onRetry(retryCount);
      await delay(backoffMs(retryCount));
    }
  }
}

async function fetchApkChunk(update: AppUpdateCheckResponse, start: number, end: number, totalSize: number) {
  return fetchApkChunkFromUrl(update.apkUrl || '', start, end, totalSize);
}

async function fetchApkChunkFromUrl(url: string, start: number, end: number, totalSize: number) {
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Range: `bytes=${start}-${end}`,
      Accept: APK_MIME_TYPE,
      'X-Request-Id': `ereport-app-update-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    },
  });
  if (response.status !== 206) {
    throw new Error(`HTTP ${response.status}`);
  }
  const contentRange = response.headers.get('Content-Range') || '';
  if (contentRange !== `bytes ${start}-${end}/${totalSize}`) {
    throw new Error('服务器返回的分片大小不正确。');
  }
  const chunkBytes = new Uint8Array(await response.arrayBuffer());
  const expectedLength = end - start + 1;
  if (chunkBytes.length !== expectedLength) {
    throw new Error('服务器返回的分片内容不完整。');
  }
  return chunkBytes;
}

function normalizeChunkSize(chunkSize?: number) {
  if (!chunkSize || chunkSize <= 0) {
    return DEFAULT_DOWNLOAD_CHUNK_BYTES;
  }
  return Math.max(256 * 1024, Math.min(chunkSize, 8 * 1024 * 1024));
}

function backoffMs(retryCount: number) {
  return Math.min(1200 * retryCount, 5000);
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function verifyFileSize(file: File, expectedSize?: number) {
  if (!expectedSize || expectedSize <= 0) {
    return;
  }
  if (file.size !== expectedSize) {
    throw new Error(`安装包大小校验失败，期望 ${expectedSize} 字节，实际 ${file.size} 字节。`);
  }
}

async function verifyFileSha256(file: File, expectedSha256: string, onProgress: (progress: number) => void) {
  const normalizedExpectedSha256 = expectedSha256.trim().toLowerCase();
  const hasher = sha256.create();
  const fileSize = file.size;
  let readBytes = 0;
  const handle = file.open(FileMode.ReadOnly);
  try {
    while (readBytes < fileSize) {
      const chunkSize = Math.min(HASH_CHUNK_BYTES, fileSize - readBytes);
      const chunk = handle.readBytes(chunkSize);
      if (chunk.length === 0) {
        break;
      }
      hasher.update(chunk);
      readBytes += chunk.length;
      onProgress(fileSize > 0 ? Math.min(readBytes / fileSize, 1) : 1);
    }
  } finally {
    handle.close();
  }

  const actualSha256 = hasher.hex().toLowerCase();
  if (actualSha256 !== normalizedExpectedSha256) {
    throw new Error('安装包 SHA-256 校验失败。');
  }
}

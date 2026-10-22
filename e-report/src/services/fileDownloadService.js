import apiClient from './apiClient.js';
import { isLiteClient, saveBlobInLite } from './liteBridge.js';

const DEFAULT_CHUNK_BYTES = 2 * 1024 * 1024;
const DEFAULT_CONCURRENCY = 4;
const MAX_CONCURRENCY = 6;
const MAX_CHUNK_RETRIES = 5;
const REQUEST_TIMEOUT_MS = 120000;

export async function downloadFileInChunks(downloadUrl, fallbackFileName = 'download', options = {}) {
    const { signal, onProgress } = options;
    throwIfAborted(signal);
    onProgress?.({ stage: 'metadata', downloadedBytes: 0, totalBytes: 0, percent: 0 });
    const metadata = await readDownloadMetadata(downloadUrl, fallbackFileName, signal);
    const expectedSha256 = normalizeSha256(options.expectedSha256 || metadata.sha256);
    let fileBlob;

    if (metadata.rangeSupported) {
        fileBlob = await downloadChunksInParallel(downloadUrl, metadata, signal, onProgress, options.concurrency);
    } else {
        if (metadata.fileSize > metadata.chunkSize) {
            throw new Error('服务器未提供分片下载能力，为避免大文件卡死，本次下载已停止。');
        }
        fileBlob = await downloadWholeFile(downloadUrl, metadata, signal, onProgress);
    }

    onProgress?.({
        stage: 'verifying',
        downloadedBytes: metadata.fileSize,
        totalBytes: metadata.fileSize,
        percent: 100,
    });
    await verifyDownloadedFile(fileBlob, metadata.fileSize, expectedSha256, signal);
    throwIfAborted(signal);
    await triggerBrowserDownload(fileBlob, metadata.fileName);
    onProgress?.({
        stage: 'completed',
        downloadedBytes: metadata.fileSize,
        totalBytes: metadata.fileSize,
        percent: 100,
    });
    return {
        fileName: metadata.fileName,
        fileSize: metadata.fileSize,
        sha256: expectedSha256,
    };
}

export async function downloadGeneratedFile(downloadUrl, params, fallbackFileName, options = {}) {
    const { signal, onProgress } = options;
    throwIfAborted(signal);
    onProgress?.({ stage: 'preparing', downloadedBytes: 0, totalBytes: 0, percent: 0 });
    const response = await apiClient.get(downloadUrl, {
        params,
        paramsSerializer: { indexes: null },
        responseType: 'blob',
        signal,
        timeout: REQUEST_TIMEOUT_MS,
        onDownloadProgress: event => {
            const totalBytes = Number(event.total || 0);
            const downloadedBytes = Number(event.loaded || 0);
            onProgress?.({
                stage: 'downloading',
                downloadedBytes,
                totalBytes,
                percent: totalBytes > 0 ? Math.min(100, Math.round((downloadedBytes / totalBytes) * 100)) : null,
            });
        },
    });
    const fileName = filenameFromHeaders(response.headers, fallbackFileName);
    const expectedSize = Number(response.headers['content-length'] || response.data.size);
    const expectedSha256 = normalizeSha256(response.headers['x-file-sha256']);
    onProgress?.({
        stage: 'verifying',
        downloadedBytes: response.data.size,
        totalBytes: expectedSize,
        percent: 100,
    });
    await verifyDownloadedFile(response.data, expectedSize, expectedSha256, signal);
    await triggerBrowserDownload(response.data, fileName);
    onProgress?.({
        stage: 'completed',
        downloadedBytes: response.data.size,
        totalBytes: expectedSize,
        percent: 100,
    });
    return {
        fileName,
        fileSize: response.data.size,
        sha256: expectedSha256,
        rowCount: Number(response.headers['x-export-row-count'] || 0),
    };
}

async function readDownloadMetadata(downloadUrl, fallbackFileName, signal) {
    const response = await apiClient.head(downloadUrl, {
        signal,
        timeout: 30000,
    });
    const fileSize = Number(response.headers['content-length'] || 0);
    if (!Number.isSafeInteger(fileSize) || fileSize <= 0) {
        throw new Error('服务器未返回有效的文件大小，下载已停止。');
    }
    const declaredChunkSize = Number(response.headers['x-file-download-chunk-size'] || 0);
    return {
        fileName: filenameFromHeaders(response.headers, fallbackFileName),
        fileSize,
        rangeSupported: String(response.headers['accept-ranges'] || '').toLowerCase() === 'bytes',
        chunkSize: declaredChunkSize > 0 ? Math.min(declaredChunkSize, DEFAULT_CHUNK_BYTES) : DEFAULT_CHUNK_BYTES,
        contentType: response.headers['content-type'] || 'application/octet-stream',
        sha256: response.headers['x-file-sha256'],
    };
}

async function downloadWholeFile(downloadUrl, metadata, signal, onProgress) {
    const response = await apiClient.get(downloadUrl, {
        responseType: 'blob',
        signal,
        timeout: REQUEST_TIMEOUT_MS,
    });
    if (response.data.size !== metadata.fileSize) {
        throw new Error('下载文件大小与服务器声明不一致，请重新下载。');
    }
    onProgress?.({
        stage: 'downloading',
        downloadedBytes: metadata.fileSize,
        totalBytes: metadata.fileSize,
        percent: 100,
    });
    return new Blob([response.data], { type: metadata.contentType });
}

async function downloadChunksInParallel(downloadUrl, metadata, externalSignal, onProgress, requestedConcurrency) {
    const ranges = createRanges(metadata.fileSize, metadata.chunkSize);
    const chunks = new Array(ranges.length);
    const controller = new AbortController();
    const abortFromCaller = () => controller.abort();
    externalSignal?.addEventListener('abort', abortFromCaller, { once: true });
    if (externalSignal?.aborted) controller.abort();

    let nextRangeIndex = 0;
    let downloadedBytes = 0;
    const concurrency = Math.min(
        Math.max(1, Math.trunc(Number(requestedConcurrency) || DEFAULT_CONCURRENCY)),
        MAX_CONCURRENCY,
        ranges.length,
    );

    const worker = async () => {
        while (true) {
            throwIfAborted(controller.signal);
            const rangeIndex = nextRangeIndex;
            nextRangeIndex += 1;
            if (rangeIndex >= ranges.length) return;
            const range = ranges[rangeIndex];
            const chunk = await downloadChunkWithRetry(
                downloadUrl,
                range.start,
                range.end,
                metadata.fileSize,
                controller.signal,
            );
            chunks[rangeIndex] = chunk;
            downloadedBytes += chunk.size;
            onProgress?.({
                stage: 'downloading',
                downloadedBytes,
                totalBytes: metadata.fileSize,
                percent: Math.min(100, Math.round((downloadedBytes / metadata.fileSize) * 100)),
            });
        }
    };

    try {
        await Promise.all(Array.from({ length: concurrency }, () => worker()));
        throwIfAborted(externalSignal);
        return new Blob(chunks, { type: metadata.contentType });
    } catch (error) {
        controller.abort();
        throwIfAborted(externalSignal);
        throw error;
    } finally {
        externalSignal?.removeEventListener('abort', abortFromCaller);
    }
}

function createRanges(totalSize, chunkSize) {
    const ranges = [];
    for (let start = 0; start < totalSize; start += chunkSize) {
        ranges.push({ start, end: Math.min(start + chunkSize, totalSize) - 1 });
    }
    return ranges;
}

async function downloadChunkWithRetry(downloadUrl, start, end, totalSize, signal) {
    let retryCount = 0;
    while (true) {
        throwIfAborted(signal);
        try {
            const response = await apiClient.get(downloadUrl, {
                headers: { Range: `bytes=${start}-${end}` },
                responseType: 'blob',
                signal,
                timeout: REQUEST_TIMEOUT_MS,
                validateStatus: status => status === 206,
            });
            validateContentRange(response.headers['content-range'], start, end, totalSize);
            if (response.data.size !== end - start + 1) {
                throw new Error('服务器返回的文件分片长度不一致。');
            }
            return response.data;
        } catch (error) {
            throwIfAborted(signal);
            if (!isRetriable(error)) throw error;
            retryCount += 1;
            if (retryCount > MAX_CHUNK_RETRIES) {
                throw new Error('文件下载中断，系统已自动重试多次仍未成功，请检查网络后再次下载。');
            }
            await delay(backoffMs(retryCount), signal);
        }
    }
}

function validateContentRange(contentRange, expectedStart, expectedEnd, expectedTotal) {
    const match = String(contentRange || '').match(/^bytes\s+(\d+)-(\d+)\/(\d+)$/i);
    if (!match) {
        throw new Error('服务器未返回有效的分片下载范围。');
    }
    const [, startText, endText, totalText] = match;
    if (Number(startText) !== expectedStart
        || Number(endText) !== expectedEnd
        || Number(totalText) !== expectedTotal) {
        throw new Error('服务器返回的文件分片范围不一致，请稍后重试。');
    }
}

async function verifyDownloadedFile(blob, expectedSize, expectedSha256, signal) {
    if (blob.size !== expectedSize) {
        throw new Error('分片合并后的文件大小不一致，请重新下载。');
    }
    if (!expectedSha256) return;
    throwIfAborted(signal);
    if (!globalThis.crypto?.subtle) {
        throw new Error('当前浏览器不支持文件完整性校验，请升级浏览器后重试。');
    }
    const digest = await globalThis.crypto.subtle.digest('SHA-256', await blob.arrayBuffer());
    throwIfAborted(signal);
    const actualSha256 = [...new Uint8Array(digest)]
        .map(value => value.toString(16).padStart(2, '0'))
        .join('');
    if (actualSha256 !== expectedSha256) {
        throw new Error('文件完整性校验失败，已阻止保存损坏文件，请重新下载。');
    }
}

function normalizeSha256(value) {
    if (!value) return '';
    const normalized = String(value).trim().toLowerCase();
    if (!/^[a-f0-9]{64}$/.test(normalized)) {
        throw new Error('服务器返回的 SHA-256 校验值无效。');
    }
    return normalized;
}

function filenameFromHeaders(headers, fallbackFileName) {
    const contentDisposition = headers?.['content-disposition'];
    if (!contentDisposition) return fallbackFileName;
    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;\r\n]+)/i);
    const plainMatch = contentDisposition.match(/filename="?([^";\r\n]+)"?/i);
    const encodedName = utf8Match?.[1] || plainMatch?.[1];
    if (!encodedName) return fallbackFileName;
    try {
        return decodeURIComponent(encodedName);
    } catch {
        return encodedName;
    }
}

async function triggerBrowserDownload(blob, fileName) {
    if (isLiteClient()) return saveBlobInLite(blob, fileName);
    const objectUrl = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.style.display = 'none';
    anchor.href = objectUrl;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60000);
}

function isRetriable(error) {
    const status = Number(error?.response?.status || 0);
    if (status > 0) {
        return status === 408 || status === 425 || status === 429 || status >= 500;
    }
    return Boolean(error?.request) || ['ECONNABORTED', 'ETIMEDOUT', 'ERR_NETWORK'].includes(error?.code);
}

function throwIfAborted(signal) {
    if (signal?.aborted) {
        const error = new Error('操作已停止。');
        error.name = 'AbortError';
        throw error;
    }
}

function backoffMs(retryCount) {
    return Math.min(1200 * retryCount, 5000);
}

function delay(ms, signal) {
    return new Promise((resolve, reject) => {
        const timeoutId = globalThis.setTimeout(() => {
            signal?.removeEventListener('abort', onAbort);
            resolve();
        }, ms);
        const onAbort = () => {
            globalThis.clearTimeout(timeoutId);
            signal?.removeEventListener('abort', onAbort);
            const error = new Error('操作已停止。');
            error.name = 'AbortError';
            reject(error);
        };
        signal?.addEventListener('abort', onAbort, { once: true });
    });
}

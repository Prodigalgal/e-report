import apiClient from './apiClient.js';

const DEFAULT_CHUNK_BYTES = 2 * 1024 * 1024;
const MAX_CHUNK_RETRIES = 5;
const REQUEST_TIMEOUT_MS = 120000;

export async function uploadFilesInChunks(files = [], purpose = 'WORK_ORDER_ATTACHMENT', options = {}) {
    const uploadIds = [];
    for (let fileIndex = 0; fileIndex < files.length; fileIndex += 1) {
        const file = files[fileIndex];
        const uploadId = await uploadSingleFileInChunks(file, purpose, {
            ...options,
            onProgress: (progress) => options.onProgress?.({
                ...progress,
                fileIndex,
                fileCount: files.length,
            }),
        });
        uploadIds.push(uploadId);
    }
    return uploadIds;
}

export async function uploadSingleFileInChunks(file, purpose, options = {}) {
    validateFile(file);
    const {
        uploadId: resumableUploadId,
        onSessionCreated,
        onProgress,
        signal,
        verifySha256 = false,
    } = options;
    throwIfAborted(signal);

    let expectedSha256;
    if (verifySha256) {
        onProgress?.({ stage: 'hashing', uploadedBytes: 0, totalBytes: file.size, percent: 0 });
        expectedSha256 = await calculateSha256(file, signal);
    }

    let session = resumableUploadId
        ? await readUploadSession(resumableUploadId, signal)
        : await createUploadSession(file, purpose, expectedSha256, signal);
    validateResumableSession(session, file, purpose, expectedSha256);
    onSessionCreated?.(session.uploadId);

    if (session.status === 'COMPLETED') {
        onProgress?.({ stage: 'completed', uploadedBytes: file.size, totalBytes: file.size, percent: 100 });
        return session.uploadId;
    }

    const chunkSize = Number(session.chunkSize) || DEFAULT_CHUNK_BYTES;
    let uploadedBytes = Number(session.receivedBytes) || 0;
    if (uploadedBytes !== file.size && uploadedBytes % chunkSize !== 0) {
        throw new Error('服务端上传断点不完整，请终止该会话后重新选择文件。');
    }
    reportUploadProgress(onProgress, 'uploading', uploadedBytes, file.size);

    while (uploadedBytes < file.size) {
        throwIfAborted(signal);
        const start = uploadedBytes;
        const endExclusive = Math.min(start + chunkSize, file.size);
        const chunkIndex = Math.floor(start / chunkSize);
        const chunk = file.slice(start, endExclusive);
        uploadedBytes = await uploadChunkWithRetry({
            uploadId: session.uploadId,
            chunkIndex,
            start,
            endExclusive,
            totalSize: file.size,
            chunk,
            signal,
        });
        reportUploadProgress(onProgress, 'uploading', uploadedBytes, file.size);
    }

    onProgress?.({ stage: 'verifying', uploadedBytes: file.size, totalBytes: file.size, percent: 100 });
    session = await completeUploadWithRetry(session.uploadId, signal);
    onProgress?.({ stage: 'completed', uploadedBytes: file.size, totalBytes: file.size, percent: 100 });
    return session.uploadId;
}

export async function abortUploadSession(uploadId) {
    if (!uploadId) return;
    await apiClient.delete(`/file-transfers/uploads/${uploadId}`, { timeout: 30000 });
}

async function createUploadSession(file, purpose, sha256, signal) {
    const response = await apiClient.post('/file-transfers/uploads', {
        purpose,
        fileName: file.name || 'uploaded-file',
        contentType: file.type || 'application/octet-stream',
        totalSize: file.size,
        sha256,
        chunkSize: DEFAULT_CHUNK_BYTES,
    }, {
        signal,
        timeout: 30000,
    });
    return response.data;
}

async function readUploadSession(uploadId, signal) {
    const response = await apiClient.get(`/file-transfers/uploads/${uploadId}`, {
        signal,
        timeout: 30000,
    });
    return response.data;
}

async function uploadChunkWithRetry({ uploadId, chunkIndex, start, endExclusive, totalSize, chunk, signal }) {
    let retryCount = 0;
    while (true) {
        throwIfAborted(signal);
        try {
            const response = await apiClient.put(
                `/file-transfers/uploads/${uploadId}/chunks/${chunkIndex}`,
                chunk,
                {
                    headers: {
                        'Content-Type': 'application/octet-stream',
                        'Content-Range': `bytes ${start}-${endExclusive - 1}/${totalSize}`,
                    },
                    signal,
                    timeout: REQUEST_TIMEOUT_MS,
                },
            );
            return Math.max(endExclusive, Number(response.data.receivedBytes) || 0);
        } catch (error) {
            throwIfAborted(signal);
            if (!isRetriable(error)) throw error;
            const resumedBytes = await discoverReceivedBytes(uploadId, endExclusive, signal);
            if (resumedBytes >= endExclusive) return resumedBytes;
            retryCount += 1;
            if (retryCount > MAX_CHUNK_RETRIES) {
                throw new Error('文件上传中断，系统已自动重试多次仍未成功；上传进度已保留，可检查网络后继续。');
            }
            await delay(backoffMs(retryCount), signal);
        }
    }
}

async function discoverReceivedBytes(uploadId, expectedEnd, signal) {
    try {
        const session = await readUploadSession(uploadId, signal);
        const receivedBytes = Number(session.receivedBytes) || 0;
        return receivedBytes >= expectedEnd ? receivedBytes : 0;
    } catch (error) {
        throwIfAborted(signal);
        if (!isRetriable(error)) throw error;
        return 0;
    }
}

async function completeUploadWithRetry(uploadId, signal) {
    let retryCount = 0;
    while (true) {
        throwIfAborted(signal);
        try {
            const response = await apiClient.post(`/file-transfers/uploads/${uploadId}/complete`, null, {
                signal,
                timeout: REQUEST_TIMEOUT_MS,
            });
            return response.data;
        } catch (error) {
            throwIfAborted(signal);
            if (!isRetriable(error)) throw error;
            const session = await readUploadSession(uploadId, signal);
            if (session.status === 'COMPLETED') return session;
            retryCount += 1;
            if (retryCount > MAX_CHUNK_RETRIES) {
                throw new Error('文件已上传，但完整性校验暂未完成；上传进度已保留，请稍后继续。');
            }
            await delay(backoffMs(retryCount), signal);
        }
    }
}

function validateFile(file) {
    if (!file || !Number.isFinite(file.size) || file.size <= 0) {
        throw new Error('文件读取失败，请重新选择文件。');
    }
}

function validateResumableSession(session, file, purpose, expectedSha256) {
    if (!session?.uploadId || session.purpose !== purpose || Number(session.totalSize) !== file.size) {
        throw new Error('上传会话与当前文件不一致，请重新选择文件。');
    }
    if (!['INITIATED', 'COMPLETED'].includes(session.status)) {
        throw new Error('上传会话已经结束，请重新选择文件。');
    }
    if (expectedSha256 && session.sha256 && session.sha256.toLowerCase() !== expectedSha256) {
        throw new Error('上传会话的文件校验值不一致，请重新选择文件。');
    }
}

async function calculateSha256(file, signal) {
    throwIfAborted(signal);
    if (!globalThis.crypto?.subtle) {
        throw new Error('当前浏览器不支持文件完整性校验，请升级浏览器后重试。');
    }
    const digest = await globalThis.crypto.subtle.digest('SHA-256', await file.arrayBuffer());
    throwIfAborted(signal);
    return [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
}

function reportUploadProgress(onProgress, stage, uploadedBytes, totalBytes) {
    onProgress?.({
        stage,
        uploadedBytes,
        totalBytes,
        percent: Math.min(100, Math.round((uploadedBytes / totalBytes) * 100)),
    });
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

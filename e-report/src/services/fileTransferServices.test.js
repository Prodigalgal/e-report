import test, { afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, webcrypto } from 'node:crypto';

import apiClient from './apiClient.js';
import { downloadFileInChunks, downloadGeneratedFile } from './fileDownloadService.js';
import { uploadSingleFileInChunks } from './fileTransferService.js';

const originalApiMethods = {
    get: apiClient.get,
    head: apiClient.head,
    post: apiClient.post,
    put: apiClient.put,
};
const originalGlobals = {
    clearTimeout: globalThis.clearTimeout,
    crypto: globalThis.crypto,
    document: globalThis.document,
    setTimeout: globalThis.setTimeout,
    window: globalThis.window,
};

afterEach(() => {
    Object.assign(apiClient, originalApiMethods);
    globalThis.clearTimeout = originalGlobals.clearTimeout;
    globalThis.setTimeout = originalGlobals.setTimeout;
    restoreGlobal('crypto', originalGlobals.crypto);
    restoreGlobal('document', originalGlobals.document);
    restoreGlobal('window', originalGlobals.window);
});

test('分片下载会重试失败 Range、按原顺序合并并校验 SHA-256', async () => {
    const content = new TextEncoder().encode('hello');
    const expectedSha256 = createHash('sha256').update(content).digest('hex');
    const attempts = new Map();
    const progressEvents = [];
    let downloadedBlob;

    installWebCrypto();
    installImmediateTimers();
    globalThis.window = {
        URL: {
            createObjectURL(blob) {
                downloadedBlob = blob;
                return 'blob:test-download';
            },
            revokeObjectURL() {},
        },
        setTimeout: callback => {
            queueMicrotask(callback);
            return 1;
        },
    };
    globalThis.document = {
        body: { appendChild() {}, removeChild() {} },
        createElement: () => ({ style: {}, click() {} }),
    };

    apiClient.head = async () => ({
        headers: {
            'accept-ranges': 'bytes',
            'content-length': String(content.length),
            'content-type': 'text/plain',
            'content-disposition': 'attachment; filename="sample.txt"',
            'x-file-download-chunk-size': '2',
            'x-file-sha256': expectedSha256,
        },
    });
    apiClient.get = async (_url, config) => {
        const range = config.headers.Range;
        attempts.set(range, (attempts.get(range) || 0) + 1);
        if (range === 'bytes=2-3' && attempts.get(range) === 1) {
            const error = new Error('temporary network failure');
            error.code = 'ERR_NETWORK';
            error.request = {};
            throw error;
        }
        const [, startText, endText] = range.match(/^bytes=(\d+)-(\d+)$/);
        const start = Number(startText);
        const end = Number(endText);
        return {
            data: new Blob([content.slice(start, end + 1)], { type: 'text/plain' }),
            headers: { 'content-range': `bytes ${start}-${end}/${content.length}` },
        };
    };

    const result = await downloadFileInChunks('/files/sample', 'fallback.txt', {
        expectedSha256,
        concurrency: 3,
        onProgress: progress => progressEvents.push(progress),
    });

    assert.equal(result.fileName, 'sample.txt');
    assert.equal(attempts.get('bytes=2-3'), 2);
    assert.equal(await downloadedBlob.text(), 'hello');
    assert.equal(progressEvents.at(-1).stage, 'completed');
});

test('用户导入上传会读取服务端断点并只续传剩余分片', async () => {
    const content = new TextEncoder().encode('abcde');
    const file = new Blob([content], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    Object.defineProperty(file, 'name', { value: '用户导入.xlsx' });
    const expectedSha256 = createHash('sha256').update(content).digest('hex');
    const uploadedRanges = [];
    const progressEvents = [];

    installWebCrypto();
    apiClient.get = async () => ({
        data: {
            uploadId: 'upload-1',
            purpose: 'USER_IMPORT',
            status: 'INITIATED',
            fileName: file.name,
            totalSize: content.length,
            chunkSize: 2,
            receivedBytes: 2,
            sha256: expectedSha256,
        },
    });
    apiClient.put = async (_url, chunk, config) => {
        uploadedRanges.push(config.headers['Content-Range']);
        const end = Number(config.headers['Content-Range'].match(/^bytes \d+-(\d+)\//)[1]);
        assert.ok(chunk.size > 0);
        return { data: { receivedBytes: end + 1 } };
    };
    apiClient.post = async url => {
        assert.equal(url, '/file-transfers/uploads/upload-1/complete');
        return { data: { uploadId: 'upload-1', status: 'COMPLETED' } };
    };

    const uploadId = await uploadSingleFileInChunks(file, 'USER_IMPORT', {
        uploadId: 'upload-1',
        verifySha256: true,
        onProgress: progress => progressEvents.push(progress),
    });

    assert.equal(uploadId, 'upload-1');
    assert.deepEqual(uploadedRanges, ['bytes 2-3/5', 'bytes 4-4/5']);
    assert.equal(progressEvents.at(-1).stage, 'completed');
});

test('工单图片上传完成后立即返回上传编号且不等待后台安全处理', async () => {
    const file = new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xd9])], { type: 'image/jpeg' });
    Object.defineProperty(file, 'name', { value: '现场照片.jpg' });
    const progressEvents = [];
    apiClient.post = async url => {
        if (url === '/file-transfers/uploads') {
            return {
                data: {
                    uploadId: 'image-upload-1',
                    purpose: 'WORK_ORDER_ATTACHMENT',
                    status: 'INITIATED',
                    totalSize: file.size,
                    chunkSize: file.size,
                    receivedBytes: 0,
                },
            };
        }
        assert.equal(url, '/file-transfers/uploads/image-upload-1/complete');
        return {
            data: {
                uploadId: 'image-upload-1',
                purpose: 'WORK_ORDER_ATTACHMENT',
                status: 'COMPLETED',
                totalSize: file.size,
                imageRedactionStatus: 'QUEUED',
                imageRedactionMessage: '图片已进入安全处理队列。',
            },
        };
    };
    apiClient.put = async () => ({ data: { receivedBytes: file.size } });
    apiClient.get = async () => assert.fail('不应轮询后台图片安全处理状态');

    const uploadId = await uploadSingleFileInChunks(file, 'WORK_ORDER_ATTACHMENT', {
        onProgress: progress => progressEvents.push(progress),
    });

    assert.equal(uploadId, 'image-upload-1');
    assert.ok(!progressEvents.some(progress => progress.stage === 'sanitizing'));
    assert.equal(progressEvents.at(-1).stage, 'completed');
});

test('动态工单导出会传递多值筛选、校验文件并使用服务端中文文件名', async () => {
    const content = new TextEncoder().encode('xlsx-content');
    const expectedSha256 = createHash('sha256').update(content).digest('hex');
    let downloadedBlob;
    let downloadedFileName;

    installWebCrypto();
    globalThis.window = {
        URL: {
            createObjectURL(blob) {
                downloadedBlob = blob;
                return 'blob:work-order-export';
            },
            revokeObjectURL() {},
        },
        setTimeout: callback => {
            queueMicrotask(callback);
            return 1;
        },
    };
    globalThis.document = {
        body: { appendChild() {}, removeChild() {} },
        createElement: () => ({
            style: {},
            click() { downloadedFileName = this.download; },
        }),
    };
    apiClient.get = async (url, config) => {
        assert.equal(url, '/work-orders/export');
        assert.deepEqual(config.params.status, ['SUBMITTED', 'IN_PROGRESS']);
        assert.equal(config.paramsSerializer.indexes, null);
        config.onDownloadProgress({ loaded: content.length, total: content.length });
        return {
            data: new Blob([content], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
            headers: {
                'content-length': String(content.length),
                'content-disposition': "attachment; filename*=UTF-8''%E5%B7%A5%E5%8D%95%E5%AF%BC%E5%87%BA.xlsx",
                'x-file-sha256': expectedSha256,
                'x-export-row-count': '12',
            },
        };
    };

    const result = await downloadGeneratedFile(
        '/work-orders/export',
        { status: ['SUBMITTED', 'IN_PROGRESS'] },
        'fallback.xlsx',
    );

    assert.equal(result.fileName, '工单导出.xlsx');
    assert.equal(result.rowCount, 12);
    assert.equal(downloadedFileName, '工单导出.xlsx');
    assert.equal(await downloadedBlob.text(), 'xlsx-content');
});

function installWebCrypto() {
    if (!globalThis.crypto?.subtle) {
        Object.defineProperty(globalThis, 'crypto', { configurable: true, value: webcrypto });
    }
}

function installImmediateTimers() {
    globalThis.setTimeout = callback => {
        queueMicrotask(callback);
        return 1;
    };
    globalThis.clearTimeout = () => {};
}

function restoreGlobal(name, value) {
    if (value === undefined) {
        delete globalThis[name];
        return;
    }
    Object.defineProperty(globalThis, name, { configurable: true, writable: true, value });
}

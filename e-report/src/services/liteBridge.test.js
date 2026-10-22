import assert from 'node:assert/strict';
import { createHash, webcrypto } from 'node:crypto';
import { Buffer } from 'node:buffer';
import test, { afterEach } from 'node:test';
import { isLiteClient, saveBlobInLite, copyTextInLite, shareTextInLite } from './liteBridge.js';

const originalWindow = globalThis.window;
afterEach(() => { if (originalWindow === undefined) delete globalThis.window; else globalThis.window = originalWindow; });

function client(failChunk = false) {
    const chunks = []; const requests = [];
    const bridge = { postMessage(text) {
        const request = JSON.parse(text); requests.push(request);
        let error;
        if (request.action === 'download.chunk') {
            if (failChunk) error = '写入文件失败';
            else {
                assert.equal(request.payload.offset, chunks.reduce((sum, chunk) => sum + chunk.length, 0));
                const bytes = Buffer.from(request.payload.base64, 'base64');
                assert.ok(bytes.length <= 192 * 1024); chunks.push(bytes);
            }
        }
        queueMicrotask(() => bridge.onmessage({ data: JSON.stringify({ id: request.id, ok: !error, error,
            payload: request.action === 'download.begin' ? { transferId: 'test-transfer' } : {} }) }));
    } };
    globalThis.window = { eReportLite: bridge, crypto: webcrypto, btoa, setTimeout, clearTimeout };
    return { chunks, requests };
}

test('Lite 文件通过有序分片完整传递并绑定大小和 SHA-256', async () => {
    const native = client();
    const bytes = Buffer.alloc(192 * 1024 + 17, 0x5a);
    assert.equal(isLiteClient(), true);
    await saveBlobInLite(new Blob([bytes], { type: 'application/pdf' }), '现场材料.pdf');
    assert.deepEqual(Buffer.concat(native.chunks), bytes);
    const opened = native.requests[0].payload;
    assert.equal(opened.name, '现场材料.pdf'); assert.equal(opened.size, bytes.length);
    assert.equal(opened.sha256, createHash('sha256').update(bytes).digest('hex'));
    assert.equal(native.requests.at(-1).action, 'download.complete');
});

test('失败分片中止会话，阻止并发文件写入', async () => {
    const native = client(true);
    const first = saveBlobInLite(new Blob(['broken']), 'file.txt');
    await assert.rejects(saveBlobInLite(new Blob(['another']), 'other.txt'), /等待当前文件/);
    await assert.rejects(first, /写入文件失败/);
    assert.equal(native.requests.at(-1).action, 'download.cancel');
});

test('位置分享和敏感文本复制通过受信任原生桥接，保留完整文本', async () => {
    const native = client();
    await shareTextInLite('工单位置', '地点：南靖\nhttps://uri.amap.com/marker');
    await copyTextInLite('one-time-token', true);
    assert.deepEqual(native.requests.map(request => [request.action, request.payload]), [
        ['share.text', { title: '工单位置', text: '地点：南靖\nhttps://uri.amap.com/marker' }],
        ['clipboard.write', { text: 'one-time-token', sensitive: true }],
    ]);
});

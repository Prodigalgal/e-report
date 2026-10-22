import { sha256 } from 'js-sha256';

const CHUNK_BYTES = 192 * 1024;
let connection;
let downloading = false;

export function isLiteClient() {
    return typeof window !== 'undefined' && typeof window.eReportLite?.postMessage === 'function';
}

function request(action, payload = {}) {
    if (!isLiteClient()) return Promise.reject(new Error('Lite 客户端连接不可用。'));
    if (!connection || connection.bridge !== window.eReportLite) {
        const pending = new Map();
        const bridge = window.eReportLite;
        bridge.onmessage = event => {
            let response;
            try { response = JSON.parse(event.data); } catch { return; }
            const entry = pending.get(response.id);
            if (!entry) return;
            pending.delete(response.id);
            window.clearTimeout(entry.timer);
            if (response.ok) entry.resolve(response.payload);
            else entry.reject(new Error(response.error || 'Lite 客户端操作失败。'));
        };
        connection = { pending, bridge };
    }
    const id = window.crypto.randomUUID();
    return new Promise((resolve, reject) => {
        const timer = window.setTimeout(() => {
            connection.pending.delete(id);
            reject(new Error('Lite 客户端响应超时，请重试。'));
        }, 30000);
        connection.pending.set(id, { resolve, reject, timer });
        try { connection.bridge.postMessage(JSON.stringify({ id, action, payload })); }
        catch (error) { window.clearTimeout(timer); connection.pending.delete(id); reject(error); }
    });
}

export function liteClientInfo() { return request('info'); }
export function shareTextInLite(title, text) { return request('share.text', { title, text }); }
export function copyTextInLite(text, sensitive = false) { return request('clipboard.write', { text, sensitive }); }

export async function saveBlobInLite(blob, name) {
    if (downloading) throw new Error('请等待当前文件保存完成。');
    downloading = true;
    let transferId;
    try {
        const digest = sha256.create();
        for (let offset = 0; offset < blob.size; offset += CHUNK_BYTES) {
            digest.update(await blob.slice(offset, offset + CHUNK_BYTES).arrayBuffer());
        }
        const opened = await request('download.begin', {
            name, size: blob.size, contentType: blob.type.split(';')[0] || 'application/octet-stream', sha256: digest.hex(),
        });
        transferId = opened.transferId;
        for (let offset = 0; offset < blob.size; offset += CHUNK_BYTES) {
            const bytes = new Uint8Array(await blob.slice(offset, offset + CHUNK_BYTES).arrayBuffer());
            let binary = '';
            for (let index = 0; index < bytes.length; index += 8192) binary += String.fromCharCode(...bytes.subarray(index, index + 8192));
            await request('download.chunk', { transferId, offset, base64: window.btoa(binary) });
        }
        await request('download.complete', { transferId });
    } catch (error) {
        if (transferId) {
            try { await request('download.cancel', { transferId }); }
            catch (cancelError) {
                const cleanupFailure = new Error('保存失败且下载会话未能清理，请重新打开应用后重试。');
                cleanupFailure.cause = { downloadError: error, cancelError };
                throw cleanupFailure;
            }
        }
        throw error;
    } finally { downloading = false; }
}

import apiClient from './apiClient.js';
import { uploadSingleFileInChunks } from './fileTransferService.js';
import { buildTransferRowFailureParams } from './userTransferContract.js';

export async function createUserTransferTemplate(signal) {
    const response = await apiClient.post('/admin/user-transfers/templates', null, {
        signal,
        timeout: 120000,
    });
    return response.data;
}

export async function uploadUserTransferWorkbook(file, options = {}) {
    return uploadSingleFileInChunks(file, 'USER_IMPORT', {
        ...options,
        verifySha256: true,
    });
}

export async function createUserTransferImport(uploadId, signal) {
    const response = await apiClient.post('/admin/user-transfers/imports', { uploadId }, {
        headers: { 'Idempotency-Key': globalThis.crypto?.randomUUID?.() || fallbackKey() },
        signal,
        timeout: 30000,
    });
    return response.data;
}

export async function previewUserTransferExport(request, signal) {
    const response = await apiClient.post('/admin/user-transfers/exports/preview', request, {
        signal,
        timeout: 30000,
    });
    return response.data;
}

export async function createUserTransferExport(request, signal) {
    const response = await apiClient.post('/admin/user-transfers/exports', request, {
        headers: { 'Idempotency-Key': globalThis.crypto?.randomUUID?.() || fallbackKey('export') },
        signal,
        timeout: 30000,
    });
    return response.data;
}

export async function getUserTransferJob(jobId, signal) {
    const response = await apiClient.get(`/admin/user-transfers/${jobId}`, {
        signal,
        timeout: 30000,
    });
    return response.data;
}

export async function getUserTransferJobs(jobType = 'IMPORT', signal) {
    const response = await apiClient.get('/admin/user-transfers', {
        params: {
            jobType,
            page: 0,
            size: 8,
            sort: 'createdAt,desc',
        },
        signal,
        timeout: 30000,
    });
    return response.data;
}

export async function getUserTransferRows(jobId, page = 0, failureFilter = 'ALL', signal) {
    const response = await apiClient.get(`/admin/user-transfers/${jobId}/rows`, {
        params: {
            page,
            size: 50,
            sort: ['subjectKeyHash,asc', 'sheetName,asc', 'rowNumber,asc'],
            ...buildTransferRowFailureParams(failureFilter),
        },
        signal,
        timeout: 30000,
    });
    return response.data;
}

export async function executeUserTransfer(jobId, previewToken, signal) {
    const response = await apiClient.post(`/admin/user-transfers/${jobId}/execute`, {
        previewToken,
    }, {
        signal,
        timeout: 30000,
    });
    return response.data;
}

export async function cancelUserTransfer(jobId) {
    const response = await apiClient.post(`/admin/user-transfers/${jobId}/cancel`, null, {
        timeout: 30000,
    });
    return response.data;
}

function fallbackKey(prefix = 'web') {
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

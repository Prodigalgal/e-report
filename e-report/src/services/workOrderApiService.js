// src/services/workOrderApiService.js
import apiClient from './apiClient';
import { uploadFilesInChunks } from './fileTransferService';
import { downloadGeneratedFile } from './fileDownloadService';

export const submitWorkOrder = async (faultData, attachments = [], options = {}) => {
    const uploadedAttachmentIds = await uploadFilesInChunks(attachments, 'WORK_ORDER_ATTACHMENT', options);
    return apiClient.post('/work-orders', {
        ...faultData,
        uploadedAttachmentIds,
    });
};

export const searchWorkOrders = (params, options = {}) => {
    return apiClient.get('/work-orders/search', {
        params,
        paramsSerializer: { indexes: null },
        ...options,
    });
};

export const exportWorkOrders = (params, options = {}) => (
    downloadGeneratedFile('/work-orders/export', params, '工单导出.xlsx', options)
);

export const fetchWorkOrderStatistics = (countyOrgUnitId, options = {}) => {
    return apiClient.get('/work-orders/statistics', {
        ...options,
        params: {
            ...options.params,
            countyOrgUnitId: countyOrgUnitId || undefined,
        },
    });
};

export const fetchWorkOrderStatisticsScopes = (options = {}) => (
    apiClient.get('/work-orders/statistics/scopes', options)
);

export const respondWorkOrder = async (workOrderId, resolutionData, attachments = [], options = {}) => {
    const uploadedAttachmentIds = await uploadFilesInChunks(attachments, 'WORK_ORDER_ATTACHMENT', options);
    return apiClient.post(`/work-orders/${workOrderId}/responses`, {
        ...resolutionData,
        uploadedAttachmentIds,
    });
};

export const fetchWorkOrderById = (workOrderId) => {
    return apiClient.get(`/work-orders/${workOrderId}`);
};

export const claimWorkOrder = (workOrderId, expectedVersion) => (
    apiClient.post(`/work-orders/${workOrderId}/claims`, { expectedVersion })
);

export const fetchAssignmentCandidates = (workOrderId, searchTerm) => (
    apiClient.get(`/work-orders/${workOrderId}/assignment-candidates`, { params: { searchTerm } })
);

export const assignWorkOrder = (workOrderId, command) => (
    apiClient.post(`/work-orders/${workOrderId}/assignments`, command)
);

export const reassignWorkOrder = (workOrderId, command) => (
    apiClient.post(`/work-orders/${workOrderId}/reassignments`, command)
);

export const acceptAssignment = (workOrderId, assignmentId, expectedVersion) => (
    apiClient.post(`/work-orders/${workOrderId}/assignments/${assignmentId}/acceptance`, { expectedVersion })
);

export const rejectAssignment = (workOrderId, assignmentId, expectedVersion, reason) => (
    apiClient.post(`/work-orders/${workOrderId}/assignments/${assignmentId}/rejection`, { expectedVersion, reason })
);

export const verifyWorkOrder = (workOrderId, command) => (
    apiClient.post(`/work-orders/${workOrderId}/verifications`, command)
);

export const holdWorkOrder = (workOrderId, expectedVersion, reason) => (
    apiClient.post(`/work-orders/${workOrderId}/holds`, { expectedVersion, reason })
);

export const resumeWorkOrder = (workOrderId, expectedVersion) => (
    apiClient.post(`/work-orders/${workOrderId}/resumptions`, { expectedVersion })
);

export const cancelWorkOrder = (workOrderId, expectedVersion, reason) => (
    apiClient.post(`/work-orders/${workOrderId}/cancellations`, { expectedVersion, reason })
);

export const fetchWorkOrderEvents = (workOrderId, params = {}) => (
    apiClient.get(`/work-orders/${workOrderId}/events`, { params })
);

export const fetchWorkOrderResponses = (workOrderId) => (
    apiClient.get(`/work-orders/${workOrderId}/responses`)
);

export const fetchWorkOrderSlaPolicies = () => apiClient.get('/admin/sla-policies');

export const updateWorkOrderSlaPolicy = (policyId, policy) => (
    apiClient.put(`/admin/sla-policies/${policyId}`, policy)
);

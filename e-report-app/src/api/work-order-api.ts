import { uploadLocalAttachmentsInChunks, type FileTransferProgress } from '@/api/file-transfer-api';
import { apiFetch } from '@/api/http';
import type {
  AssignmentCandidate,
  PageResponse,
  RespondWorkOrderRequest,
  SubmitWorkOrderRequest,
  WorkOrder,
  WorkOrderListItem,
  WorkOrderStatistics,
  WorkOrderStatisticsScope,
  WorkOrderEvent,
  WorkOrderResponseHistory,
  WorkOrderSlaPolicy,
  WorkOrderSlaPolicyUpdate,
  WorkOrderTransfer,
  WorkOrderTransferTarget,
} from '@/types/api';

export function searchWorkOrders(params: { page?: number; size?: number; reportType?: string[]; status?: string[]; searchTerm?: string; queue?: string[] }) {
  return apiFetch<PageResponse<WorkOrderListItem>>('/work-orders/search', {
    query: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      reportType: params.reportType,
      status: params.status,
      searchTerm: params.searchTerm,
      queue: params.queue,
    },
  });
}

export function fetchWorkOrderStatistics() {
  return apiFetch<WorkOrderStatistics>('/work-orders/statistics');
}

export function fetchWorkOrderStatisticsByCounty(countyOrgUnitId?: string) {
  return apiFetch<WorkOrderStatistics>('/work-orders/statistics', {
    query: { countyOrgUnitId },
  });
}

export function fetchWorkOrderStatisticsScopes() {
  return apiFetch<WorkOrderStatisticsScope[]>('/work-orders/statistics/scopes');
}

export function fetchWorkOrderById(id: string) {
  return apiFetch<WorkOrder>(`/work-orders/${id}`);
}

export async function respondWorkOrder(
  id: string,
  request: RespondWorkOrderRequest,
  onUploadProgress?: (progress: FileTransferProgress) => void,
) {
  const { attachments = [], ...resolveData } = request;
  const uploadedAttachmentIds = await uploadLocalAttachmentsInChunks(attachments, 'WORK_ORDER_ATTACHMENT', onUploadProgress);
  return apiFetch<WorkOrder>(`/work-orders/${id}/responses`, {
    method: 'POST',
    body: JSON.stringify({ ...resolveData, uploadedAttachmentIds }),
  });
}

export function claimWorkOrder(id: string, expectedVersion: number) {
  return apiFetch<WorkOrder>(`/work-orders/${id}/claims`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion }),
  });
}

export function fetchAssignmentCandidates(id: string, searchTerm: string) {
  return apiFetch<AssignmentCandidate[]>(`/work-orders/${id}/assignment-candidates`, {
    query: { searchTerm },
  });
}

export function assignWorkOrder(id: string, command: { assigneeMembershipId: string; responsibleOrgId: string; reason: string; expectedVersion: number }) {
  return apiFetch<WorkOrder>(`/work-orders/${id}/assignments`, { method: 'POST', body: JSON.stringify(command) });
}

export function reassignWorkOrder(id: string, command: { assigneeMembershipId: string; responsibleOrgId: string; reason: string; expectedVersion: number }) {
  return apiFetch<WorkOrder>(`/work-orders/${id}/reassignments`, { method: 'POST', body: JSON.stringify(command) });
}

export function acceptAssignment(id: string, assignmentId: string, expectedVersion: number) {
  return apiFetch<WorkOrder>(`/work-orders/${id}/assignments/${assignmentId}/acceptance`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion }),
  });
}

export function rejectAssignment(id: string, assignmentId: string, expectedVersion: number, reason: string) {
  return apiFetch<WorkOrder>(`/work-orders/${id}/assignments/${assignmentId}/rejection`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, reason }),
  });
}

export function verifyWorkOrder(id: string, command: {
  responseId: string;
  decision: 'APPROVED' | 'REJECTED';
  comment?: string;
  expectedVersion: number;
}) {
  return apiFetch<WorkOrder>(`/work-orders/${id}/verifications`, { method: 'POST', body: JSON.stringify(command) });
}

export function holdWorkOrder(id: string, expectedVersion: number, reason: string) {
  return apiFetch<WorkOrder>(`/work-orders/${id}/holds`, { method: 'POST', body: JSON.stringify({ expectedVersion, reason }) });
}

export function resumeWorkOrder(id: string, expectedVersion: number) {
  return apiFetch<WorkOrder>(`/work-orders/${id}/resumptions`, { method: 'POST', body: JSON.stringify({ expectedVersion }) });
}

export function cancelWorkOrder(id: string, expectedVersion: number, reason: string) {
  return apiFetch<WorkOrder>(`/work-orders/${id}/cancellations`, { method: 'POST', body: JSON.stringify({ expectedVersion, reason }) });
}

export function fetchWorkOrderEvents(id: string, query: { page?: number; size?: number } = {}) {
  return apiFetch<PageResponse<WorkOrderEvent>>(`/work-orders/${id}/events`, {
    query: { page: query.page ?? 0, size: query.size ?? 50 },
  });
}

export function fetchWorkOrderResponses(id: string) {
  return apiFetch<WorkOrderResponseHistory[]>(`/work-orders/${id}/responses`);
}

export function fetchWorkOrderTransfers(id: string) {
  return apiFetch<WorkOrderTransfer[]>(`/work-orders/${id}/organization-transfers`);
}

export function fetchWorkOrderTransferTargets() {
  return apiFetch<WorkOrderTransferTarget[]>('/work-orders/organization-transfer-targets');
}

export function requestWorkOrderTransfer(id: string, targetOrgUnitId: string, expectedWorkOrderVersion: number, reason: string) {
  return apiFetch<WorkOrderTransfer>(`/work-orders/${id}/organization-transfers`, {
    method: 'POST', body: JSON.stringify({ targetOrgUnitId, expectedWorkOrderVersion, reason }),
  });
}

export function decideWorkOrderTransfer(id: string, transferId: string, action: 'accept' | 'reject' | 'cancel', expectedWorkOrderVersion: number, comment: string) {
  return apiFetch<WorkOrderTransfer>(`/work-orders/${id}/organization-transfers/${transferId}/${action}`, {
    method: 'POST', body: JSON.stringify({ expectedWorkOrderVersion, comment }),
  });
}

export async function submitWorkOrder(
  request: SubmitWorkOrderRequest,
  onUploadProgress?: (progress: FileTransferProgress) => void,
) {
  const uploadedAttachmentIds = await uploadLocalAttachmentsInChunks(
    request.attachments,
    'WORK_ORDER_ATTACHMENT',
    onUploadProgress,
  );
  return apiFetch<WorkOrder>('/work-orders', {
    method: 'POST',
    body: JSON.stringify({ ...request.data, uploadedAttachmentIds }),
  });
}

export function fetchWorkOrderSlaPolicies() {
  return apiFetch<WorkOrderSlaPolicy[]>('/admin/sla-policies');
}

export function updateWorkOrderSlaPolicy(id: string, policy: WorkOrderSlaPolicyUpdate) {
  return apiFetch<WorkOrderSlaPolicy>(`/admin/sla-policies/${id}`, {
    method: 'PUT',
    body: JSON.stringify(policy),
  });
}

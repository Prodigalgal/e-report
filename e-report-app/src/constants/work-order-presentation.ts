import sharedPresentation from '../../../shared/work-order-presentation.json' with { type: 'json' };

export type PresentationTone = {
  label: string;
  foreground: string;
  surface: string;
  border: string;
  rail?: string;
};

type PresentationContract = {
  reportTypes: Record<string, PresentationTone>;
  priorities: Record<string, PresentationTone>;
  slaRisks: Record<string, PresentationTone>;
  statuses: Record<string, PresentationTone>;
  durationBuckets: Record<string, PresentationTone>;
};

const presentation = sharedPresentation as PresentationContract;
const fallbackTone: PresentationTone = {
  label: '未知',
  foreground: '#475569',
  surface: '#F8FAFC',
  border: '#CBD5E1',
  rail: '#64748B',
};

const statusByLabel: Record<string, string> = {
  已提交: 'SUBMITTED',
  待处理: 'SUBMITTED',
  已派单: 'ASSIGNED',
  处理中: 'IN_PROGRESS',
  待验收: 'PENDING_VERIFICATION',
  已解决: 'PENDING_VERIFICATION',
  已挂起: 'ON_HOLD',
  已闭环: 'CLOSED',
  已关闭: 'CLOSED',
  已取消: 'CANCELLED',
};

export function reportTypePresentation(reportType?: string) {
  return presentation.reportTypes[reportType || ''] || fallbackTone;
}

export function priorityPresentation(priority?: string) {
  return presentation.priorities[priority || ''] || presentation.priorities.ROUTINE;
}

export function slaRiskPresentation(riskLevel?: string) {
  return presentation.slaRisks[riskLevel || ''] || presentation.slaRisks.NORMAL;
}

export function durationBucketPresentation(bucket?: string) {
  return presentation.durationBuckets[bucket || ''] || fallbackTone;
}

export function statusPresentation(workOrderOrStatus?: {
  statusCode?: string;
  statusLabel?: string;
  status?: string;
  phaseCode?: string;
} | string) {
  if (typeof workOrderOrStatus === 'object' && workOrderOrStatus) {
    if (workOrderOrStatus.phaseCode === 'PENDING_ACCEPTANCE' || workOrderOrStatus.phaseCode === 'REASSIGNING') {
      return presentation.statuses.ASSIGNED;
    }
    const statusCode = workOrderOrStatus.statusCode
      || statusByLabel[workOrderOrStatus.statusLabel || '']
      || statusByLabel[workOrderOrStatus.status || ''];
    return presentation.statuses[statusCode || ''] || fallbackTone;
  }
  const statusCode = presentation.statuses[workOrderOrStatus || '']
    ? workOrderOrStatus
    : statusByLabel[workOrderOrStatus || ''];
  return presentation.statuses[statusCode || ''] || fallbackTone;
}

export function statusDisplayLabel(workOrderOrStatus?: {
  statusCode?: string;
  statusLabel?: string;
  status?: string;
  phaseCode?: string;
} | string) {
  const tone = statusPresentation(workOrderOrStatus);
  if (tone !== fallbackTone) {
    return tone.label;
  }
  if (typeof workOrderOrStatus === 'object' && workOrderOrStatus) {
    return workOrderOrStatus.statusLabel
      || workOrderOrStatus.status
      || workOrderOrStatus.statusCode
      || fallbackTone.label;
  }
  return workOrderOrStatus || fallbackTone.label;
}

import presentation from '../../../shared/work-order-presentation.json' with { type: 'json' };

const FALLBACK_TOKEN = Object.freeze({
    label: '未知',
    foreground: '#475569',
    surface: '#F8FAFC',
    border: '#CBD5E1',
    rail: '#64748B',
});

const STATUS_BY_LABEL = Object.freeze({
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
});

export const WORK_ORDER_PRESENTATION = presentation;

export const reportTypePresentation = (reportType) => (
    presentation.reportTypes[reportType] || FALLBACK_TOKEN
);

export const priorityPresentation = (priority) => (
    presentation.priorities[priority] || presentation.priorities.ROUTINE
);

export const slaRiskPresentation = (riskLevel) => (
    presentation.slaRisks[riskLevel] || presentation.slaRisks.NORMAL
);

export const durationBucketPresentation = (bucket) => (
    presentation.durationBuckets[bucket] || FALLBACK_TOKEN
);

export function statusPresentation(workOrderOrStatus) {
    if (typeof workOrderOrStatus === 'object' && workOrderOrStatus !== null) {
        if (workOrderOrStatus.phaseCode === 'PENDING_ACCEPTANCE'
                || workOrderOrStatus.phaseCode === 'REASSIGNING') {
            return presentation.statuses.ASSIGNED;
        }
        const statusCode = workOrderOrStatus.statusCode
            || STATUS_BY_LABEL[workOrderOrStatus.statusLabel]
            || STATUS_BY_LABEL[workOrderOrStatus.status];
        return presentation.statuses[statusCode] || FALLBACK_TOKEN;
    }
    const statusCode = presentation.statuses[workOrderOrStatus]
        ? workOrderOrStatus
        : STATUS_BY_LABEL[workOrderOrStatus];
    return presentation.statuses[statusCode] || FALLBACK_TOKEN;
}

export function statusDisplayLabel(workOrderOrStatus) {
    const token = statusPresentation(workOrderOrStatus);
    if (token !== FALLBACK_TOKEN) {
        return token.label;
    }
    if (typeof workOrderOrStatus === 'object' && workOrderOrStatus !== null) {
        return workOrderOrStatus.statusLabel
            || workOrderOrStatus.status
            || workOrderOrStatus.statusCode
            || FALLBACK_TOKEN.label;
    }
    return workOrderOrStatus || FALLBACK_TOKEN.label;
}

export const presentationChipSx = (token) => ({
    height: 28,
    minWidth: 0,
    color: token.foreground,
    backgroundColor: token.surface,
    border: 'none',
    borderRadius: '6px',
    fontWeight: 500,
    '& .MuiChip-label': {
        px: 1,
        lineHeight: '26px',
        whiteSpace: 'nowrap',
    },
});

export const WORK_ORDER_PRIORITIES = [
    { value: 'URGENT', label: '紧急' },
    { value: 'HIGH', label: '高优先' },
    { value: 'ROUTINE', label: '常规' },
    { value: 'LOW', label: '低优先' },
];

export function formatDuration(totalSeconds) {
    const seconds = Math.max(0, Number(totalSeconds || 0));
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (days > 0) return `${days}天${hours}小时`;
    if (hours > 0) return `${hours}小时${minutes}分钟`;
    return `${minutes}分钟`;
}

export function slaCountdown(workOrder) {
    if (workOrder?.slaStage === 'COMPLETED') return '已结束';
    if (workOrder?.slaPaused) return `剩余 ${formatDuration(workOrder.slaRemainingSeconds)}`;
    if (workOrder?.slaBreached) return `超时 ${formatDuration(workOrder.slaBreachSeconds)}`;
    return `剩余 ${formatDuration(workOrder?.slaRemainingSeconds)}`;
}

export function slaSummaryLabel(workOrder) {
    if (!workOrder
            || workOrder.slaStage === 'COMPLETED'
            || ['CLOSED', 'CANCELLED'].includes(workOrder.statusCode)) {
        return null;
    }
    if (workOrder.slaBreached || workOrder.slaRiskLevel === 'BREACHED') {
        return `已超时 ${formatDuration(workOrder.slaBreachSeconds)}`;
    }
    if (workOrder.slaPaused || workOrder.slaRiskLevel === 'PAUSED') {
        return `已暂停 · 剩余 ${formatDuration(workOrder.slaRemainingSeconds)}`;
    }
    if (workOrder.slaRiskLevel === 'DUE_SOON') {
        return `临期 · 剩余 ${formatDuration(workOrder.slaRemainingSeconds)}`;
    }
    return `剩余 ${formatDuration(workOrder.slaRemainingSeconds)}`;
}

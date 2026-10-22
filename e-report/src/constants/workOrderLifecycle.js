export const WORK_ORDER_STATUS_OPTIONS = [
    { value: 'all', label: '全部状态' },
    { value: 'SUBMITTED', label: '已提交' },
    { value: 'IN_PROGRESS', label: '处理中' },
    { value: 'PENDING_VERIFICATION', label: '待验收' },
    { value: 'ON_HOLD', label: '已挂起' },
    { value: 'CLOSED', label: '已闭环' },
    { value: 'CANCELLED', label: '已取消' },
];

export const WORK_ORDER_QUEUE_OPTIONS = [
    { value: 'all', label: '全部工单' },
    { value: 'CLAIMABLE', label: '可接单' },
    { value: 'ASSIGNED_TO_ME', label: '待我接收' },
    { value: 'PROCESSING_BY_ME', label: '我的处理中' },
    { value: 'PENDING_VERIFICATION', label: '待我验收' },
    { value: 'REPORTED_BY_ME', label: '我提交的' },
];

export const workOrderStatusLabel = (workOrder) => (
    workOrder?.statusLabel || workOrder?.status || '未知状态'
);

export const hasAllowedAction = (workOrder, action) => (
    Array.isArray(workOrder?.allowedActions) && workOrder.allowedActions.includes(action)
);

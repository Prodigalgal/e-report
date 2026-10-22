import sharedWorkOrderCatalog from '../../../shared/work-order-catalog.json' with { type: 'json' };

export const WORK_ORDER_TYPE_CODES = {
    NETWORK_FAULT: 'NETWORK_FAULT',
    BOX_DOOR_OPEN: 'BOX_DOOR_OPEN',
    DAMAGED_BOX_REPLACEMENT: 'DAMAGED_BOX_REPLACEMENT',
};

export const WORK_ORDER_CATALOG = sharedWorkOrderCatalog;
export const WORK_ORDER_TYPES = WORK_ORDER_CATALOG;

export const WORK_ORDER_TASKS = {
    REPORT: {
        key: 'report',
        label: '我要报单',
        description: '选择网络故障、箱门未关或破损箱体更换。',
        path: '/report',
    },
    SEARCH: {
        key: 'search',
        label: '工单查询',
        description: '集中查询本人或权限范围内的工单，并直接查看或处理。',
        path: '/work-orders/search',
    },
    ADMIN: {
        key: 'admin',
        label: '管理后台',
        description: '查看统计，并维护用户、岗位授权和系统配置。',
        path: '/admin',
    },
};

export const WORK_ORDER_TYPE_OPTIONS = [
    { value: 'all', label: '所有类型' },
    ...WORK_ORDER_CATALOG.map(item => ({ value: item.reportType, label: item.typeLabel })),
];

export const REPORT_PAGE_CONFIGS = Object.fromEntries(
    WORK_ORDER_CATALOG.map(item => [item.reportType, item.form]),
);

export const findWorkOrderType = (reportType) => (
    WORK_ORDER_CATALOG.find(item => item.reportType === reportType) || null
);

export const getReportTypeLabel = (reportType, fallback = '网络故障') => (
    findWorkOrderType(reportType)?.typeLabel || fallback
);

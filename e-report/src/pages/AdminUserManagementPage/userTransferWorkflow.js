export const USER_TRANSFER_COMMAND_LABELS = {
    CREATE: '创建用户',
    UPDATE: '更新资料',
    ORG_MOVE: '调整组织',
    DEPARTMENT_MOVE: '调整部门',
    GRANT_PROFILE: '追加岗位模板',
    GRANT_PERMISSION: '追加单条权限',
    ADJUST_PERMISSION: '调整原子权限',
    REVOKE_PERMISSION: '撤销原子权限',
};

export function groupTransferRows(rows = []) {
    const groups = [];
    for (const row of rows) {
        const subjectKey = row.subjectKey || `row-${row.id}`;
        const current = groups.at(-1);
        if (current?.subjectKey === subjectKey) {
            current.rows.push(row);
            continue;
        }
        groups.push({
            subjectKey,
            subjectDisplay: row.subjectDisplay || '历史任务用户',
            rows: [row],
        });
    }
    return groups;
}

export function transferRowHasChanges(row) {
    return Boolean(row?.fieldChanges?.length
        || row?.authorizationDiff?.changes?.length
        || row?.authorizationDiff?.summary);
}

export function buildUserExportRequest(filters) {
    return {
        ...(filters.searchTerm ? { searchTerm: filters.searchTerm } : {}),
        ...(filters.orgUnitId ? { orgUnitId: filters.orgUnitId } : {}),
        ...(filters.departmentId ? { departmentId: filters.departmentId } : {}),
        ...(filters.status ? { status: filters.status } : {}),
        includeDeleted: filters.status === 'DELETED',
    };
}

export function bindUserExportPreview(request, preview) {
    if (!preview?.previewToken) {
        throw new Error('导出预检已失效，请重新预览。');
    }
    return { ...request, previewToken: preview.previewToken };
}

export { buildTransferRowFailureParams } from '../../services/userTransferContract.js';

export function exportFilterLabels(filters = {}) {
    return [
        filters.searchTerm ? `关键词：${filters.searchTerm}` : null,
        filters.organizationName ? `组织：${filters.organizationName}` : null,
        filters.departmentName ? `部门：${filters.departmentName}` : null,
        filters.statusLabel ? `状态：${filters.statusLabel}` : null,
        filters.includesDeleted ? '包含已删除账号' : null,
    ].filter(Boolean);
}

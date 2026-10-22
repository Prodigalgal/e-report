import { formatDateTime } from '../../utils/formatDate.js';

export function filterAuthorizationGrants(grants, searchTerm = '') {
    const keyword = searchTerm.trim().toLocaleLowerCase('zh-CN');
    return (grants || []).filter(grant => (
        !keyword || `${grant.permissionDisplayName} ${grant.permissionName} ${grant.issuerType} ${grant.status}`
            .toLocaleLowerCase('zh-CN')
            .includes(keyword)
    ));
}

export function filterPermissionOptions(permissions, searchTerm = '', limit = 50) {
    const keyword = searchTerm.trim().toLocaleLowerCase('zh-CN');
    return (permissions || []).filter(permission => (
        !keyword || `${permission.displayName} ${permission.name}`
            .toLocaleLowerCase('zh-CN')
            .includes(keyword)
    )).slice(0, limit);
}

export const AUTHORIZATION_DIFF_KIND_META = {
    ADD: { label: '新增', color: 'success' },
    UPDATE: { label: '调整', color: 'primary' },
    REMOVE: { label: '撤销', color: 'error' },
};

export const AUTHORIZATION_DIFF_FIELD_LABELS = {
    permission: '原子权限',
    parentGrantId: '授权链上游',
    grantOption: '可转授权',
    status: '状态',
    validFrom: '开始时间',
    expiresAt: '有效期',
};

const STATUS_LABELS = {
    ACTIVE: '有效',
    REVOKED: '已撤销',
    SUSPENDED_UPSTREAM: '上游失效已暂停',
};

export function formatAuthorizationDiffValue(_row, field, value, parentGrants = []) {
    if (field === 'grantOption') return value ? '允许继续转授' : '不允许继续转授';
    if (field === 'status') return STATUS_LABELS[value] || value || '未设置';
    if (field === 'parentGrantId') {
        if (!value) return '无（系统根授权）';
        const parent = parentGrants.find(grant => grant.grantId === value);
        return parent ? `${parent.permissionDisplayName} · ${parent.grantId}` : value;
    }
    if (field === 'validFrom' || field === 'expiresAt') {
        if (!value) return field === 'expiresAt' ? '长期有效' : '立即生效';
        const formatted = formatDateTime(value);
        return formatted === '-' ? String(value) : formatted;
    }
    return value ?? '未设置';
}

const DIFF_FIELD_KEYS = ['permission', 'parentGrantId', 'grantOption', 'status', 'validFrom', 'expiresAt'];

function diffFieldValue(snapshot, field) {
    if (!snapshot) return null;
    if (field === 'permission') return `${snapshot.permissionDisplayName} · ${snapshot.permissionName}`;
    return snapshot[field] ?? null;
}

export function buildAuthorizationDiffRows(plan) {
    return (plan?.operations || []).flatMap(operation => operation.permissionChanges || []).map((change, index) => {
        const snapshot = change.after || change.before;
        const fields = DIFF_FIELD_KEYS
            .map(field => ({
                field,
                before: diffFieldValue(change.before, field),
                after: diffFieldValue(change.after, field),
            }))
            .filter(entry => (
                change.kind === 'UPDATE'
                    ? entry.before !== entry.after
                    : entry.field !== 'permission'
            ));
        return {
            key: `${change.kind}-${snapshot.permissionName}-${index}`,
            kind: change.kind,
            permissionName: snapshot.permissionName,
            permissionDisplayName: snapshot.permissionDisplayName,
            fields,
        };
    });
}

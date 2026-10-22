export const ORGANIZATION_MOVE_STRATEGIES = [
    {
        value: 'REVOKE_ALL',
        label: '撤销全部可撤销授权',
        description: '撤销全部由管理员维护的授权；系统基线授权由系统继续维护。',
    },
    {
        value: 'KEEP',
        label: '保留全部现有授权',
        description: '只改变用户所属组织，不调整任何权限。工单数据视野会按新组织树自然重新计算，但原子权限能力保持不变。',
    },
];

export const ORGANIZATION_MOVE_STRATEGY_BY_VALUE = Object.fromEntries(
    ORGANIZATION_MOVE_STRATEGIES.map(strategy => [strategy.value, strategy]),
);

export const AUTHORIZATION_CHANGE_LABELS = {
    ADD: '新增',
    UPDATE: '调整',
    REMOVE: '撤销',
};

export function authorizationChangeCounts(changes = []) {
    return changes.reduce((counts, change) => ({
        ...counts,
        [change.kind]: (counts[change.kind] || 0) + 1,
    }), { ADD: 0, UPDATE: 0, REMOVE: 0 });
}

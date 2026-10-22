import test from 'node:test';
import assert from 'node:assert/strict';

import {
    ORGANIZATION_MOVE_STRATEGY_BY_VALUE,
    authorizationChangeCounts,
} from './organizationMoveWorkflow.js';

test('组织迁移策略只公开当前支持的选择', () => {
    assert.deepEqual(Object.keys(ORGANIZATION_MOVE_STRATEGY_BY_VALUE).sort(), ['KEEP', 'REVOKE_ALL']);
    assert.match(ORGANIZATION_MOVE_STRATEGY_BY_VALUE.REVOKE_ALL.description, /系统基线授权由系统继续维护/);
    assert.match(ORGANIZATION_MOVE_STRATEGY_BY_VALUE.KEEP.description, /按新组织树自然重新计算/);
});

test('组织迁移授权 diff 按新增、调整和撤销分别统计', () => {
    assert.deepEqual(authorizationChangeCounts([
        { kind: 'UPDATE' },
        { kind: 'REMOVE' },
        { kind: 'UPDATE' },
        { kind: 'ADD' },
    ]), { ADD: 1, UPDATE: 2, REMOVE: 1 });
});

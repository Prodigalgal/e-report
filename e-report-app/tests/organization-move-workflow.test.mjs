import test from 'node:test';
import assert from 'node:assert/strict';

import {
  ORGANIZATION_MOVE_STRATEGY_BY_VALUE,
  authorizationChangeCounts,
} from '../src/features/admin/organization-move-workflow.ts';

test('APP 组织迁移策略明确展示撤销和最终保留规则', () => {
  assert.equal(ORGANIZATION_MOVE_STRATEGY_BY_VALUE.REVOKE_SENSITIVE, undefined);
  assert.match(ORGANIZATION_MOVE_STRATEGY_BY_VALUE.REVOKE_ALL.description, /系统基线授权由系统继续维护/);
  assert.match(ORGANIZATION_MOVE_STRATEGY_BY_VALUE.KEEP.description, /按新组织树自然重新计算/);
});

test('APP 组织迁移 diff 分别统计新增、调整和撤销', () => {
  assert.deepEqual(authorizationChangeCounts([
    { kind: 'REMOVE', before: null },
    { kind: 'UPDATE', before: null, after: null },
    { kind: 'UPDATE', before: null, after: null },
  ]), { ADD: 0, UPDATE: 2, REMOVE: 1 });
});

import assert from 'node:assert/strict';
import test from 'node:test';

import { resolveUserAccessSummary, userIsAdmin } from '../src/auth/admin-access.ts';

test('具有任一真实管理动作权限即可进入对应管理区域', () => {
  assert.equal(userIsAdmin(null), false);
  assert.equal(userIsAdmin({ permissions: [] }), false);
  assert.equal(userIsAdmin({ permissions: ['user:manage:view'] }), true);
  assert.equal(userIsAdmin({ permissions: ['work-order:network-fault:view'] }), true);
  assert.equal(userIsAdmin({ permissions: ['account:self:view'] }), false);
});

test('账号摘要只表达当前有效权限数量，不推导角色身份', () => {
  assert.equal(resolveUserAccessSummary({ permissions: [] }), '暂无有效权限');
  assert.equal(resolveUserAccessSummary({ permissions: ['a', 'b', 'c'] }), '已授予 3 项权限');
});

import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildAuthorizationDiffRows,
  filterAuthorizationGrants,
  filterPermissionOptions,
} from '../src/features/admin/authorization-editor-workflow.ts';

test('APP 授权编辑器直接展示 Grant 清单并支持权限与状态搜索', () => {
  const grants = [
    { permissionName: 'account:self:view', permissionDisplayName: '个人信息查看', issuerType: 'SYSTEM_SELF_BASELINE', status: 'ACTIVE' },
    { permissionName: 'work-order:view', permissionDisplayName: '工单查看', issuerType: 'USER', status: 'ACTIVE' },
    { permissionName: 'user:view', permissionDisplayName: '用户查看', issuerType: 'USER', status: 'SUSPENDED_UPSTREAM' },
  ];
  assert.deepEqual(filterAuthorizationGrants(grants).map(item => item.permissionName), [
    'account:self:view', 'work-order:view', 'user:view',
  ]);
  assert.deepEqual(filterAuthorizationGrants(grants, '工单').map(item => item.permissionName), ['work-order:view']);
  assert.deepEqual(filterAuthorizationGrants(grants, 'suspended').map(item => item.permissionName), ['user:view']);
});

test('APP 原子权限搜索限制长列表渲染数量', () => {
  const permissions = Array.from({ length: 60 }, (_, index) => ({
    name: `work-order:test:${index}`,
    displayName: index === 55 ? '验收闭环' : `测试权限${index}`,
  }));
  assert.equal(filterPermissionOptions(permissions).length, 50);
  assert.deepEqual(filterPermissionOptions(permissions, '验收').map(item => item.name), ['work-order:test:55']);
});

test('APP 预览按逐权限 Diff 展开且调整只显示变化字段', () => {
  const before = {
    grantId: 'grant-1', parentGrantId: 'parent-1', permissionName: 'work-order:view',
    permissionDisplayName: '工单查看', status: 'ACTIVE',
    grantOption: false, validFrom: null, expiresAt: null,
  };
  const rows = buildAuthorizationDiffRows({
    operations: [{ permissionChanges: [
      { kind: 'ADD', before: null, after: before },
      { kind: 'UPDATE', before, after: { ...before, parentGrantId: 'parent-2', grantOption: true } },
      { kind: 'REMOVE', before, after: null },
    ] }],
  });
  assert.equal(rows.length, 3);
  assert.deepEqual(rows[1].fields.map(field => field.field), ['parentGrantId', 'grantOption']);
});

import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildApiTokenCreatePayload,
  filterApiTokenPermissionOptions,
} from '../src/features/profile/api-token-workflow.ts';

test('APP API Token 长期与限时创建载荷使用明确 UTC 时间', () => {
  const now = Date.parse('2026-08-16T00:00:00.000Z');
  assert.deepEqual(buildApiTokenCreatePayload(' AI 查询 ', false, 30, ['read:a', 'read:a'], now), {
    name: 'AI 查询',
    expiresAt: '2026-09-15T00:00:00.000Z',
    permissionNames: ['read:a'],
  });
  assert.equal(buildApiTokenCreatePayload('长期', true, 0, ['read:a'], now).expiresAt, null);
});

test('APP API Token 权限搜索同时匹配中文名、编码和说明', () => {
  const options = [
    { name: 'work-order:view', displayName: '工单查看', description: '查询组织范围工单', accessMode: 'R', riskLevel: 'MEDIUM' },
    { name: 'app-update:delete', displayName: '安装包删除', description: '删除安装包', accessMode: 'W', riskLevel: 'CRITICAL' },
  ];
  assert.deepEqual(filterApiTokenPermissionOptions(options, '组织').map(option => option.name), ['work-order:view']);
  assert.deepEqual(filterApiTokenPermissionOptions(options, 'app-update').map(option => option.name), ['app-update:delete']);
});

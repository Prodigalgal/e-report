import type { LoginResponse } from '../types/api';
import { MANAGEMENT_ENTRY_PERMISSIONS, userHasAnyPermission } from '../constants/work-orders.ts';

export function userIsAdmin(user: LoginResponse | null | undefined) {
  return userHasAnyPermission(user, MANAGEMENT_ENTRY_PERMISSIONS);
}

export function resolveUserAccessSummary(user: LoginResponse | null | undefined) {
  const permissionCount = Array.isArray(user?.permissions) ? user.permissions.length : 0;
  return permissionCount > 0 ? `已授予 ${permissionCount} 项权限` : '暂无有效权限';
}

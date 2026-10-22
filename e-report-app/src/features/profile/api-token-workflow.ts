import type { ApiTokenPermissionOption } from '@/types/api';

export const MAX_API_TOKEN_EXPIRY_DAYS = 3650;

export function buildApiTokenCreatePayload(
  name: string,
  longLived: boolean,
  expiryDays: number,
  permissionNames: readonly string[],
  now = Date.now(),
) {
  const normalizedName = name.trim();
  const normalizedPermissions = [...new Set(permissionNames.map(value => value.trim()).filter(Boolean))];
  if (!normalizedName) throw new Error('Token 名称不能为空。');
  if (normalizedPermissions.length === 0) throw new Error('请至少选择一项 API 权限。');
  if (!longLived && (!Number.isInteger(expiryDays) || expiryDays < 1 || expiryDays > MAX_API_TOKEN_EXPIRY_DAYS)) {
    throw new Error(`有效天数必须为 1-${MAX_API_TOKEN_EXPIRY_DAYS}。`);
  }
  return {
    name: normalizedName,
    expiresAt: longLived ? null : new Date(now + expiryDays * 24 * 60 * 60 * 1000).toISOString(),
    permissionNames: normalizedPermissions,
  };
}

export function filterApiTokenPermissionOptions(
  options: readonly ApiTokenPermissionOption[],
  search: string,
) {
  const keyword = search.trim().toLowerCase();
  if (!keyword) return [...options];
  return options.filter(option => [option.displayName, option.name, option.description]
    .some(value => value.toLowerCase().includes(keyword)));
}

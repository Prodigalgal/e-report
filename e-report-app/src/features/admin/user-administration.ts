import type { OrgUnit, UserStatus } from '@/types/api';

export const USER_STATUSES: UserStatus[] = ['ACTIVE', 'INACTIVE', 'DISABLED', 'LOCKED'];

export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: '启用',
  INACTIVE: '停用',
  DISABLED: '禁用',
  LOCKED: '锁定',
  DELETED: '已删除',
};

export function defaultOrganizationId(orgUnits: OrgUnit[]) {
  return orgUnits.find((organization) => organization.tenantRoot)?.id || orgUnits[0]?.id || '';
}

export function validateUserDetails(details: {
  name: string;
  phone: string;
  email: string;
  orgUnitId: string;
}) {
  if (!details.name.trim()) throw new Error('姓名不能为空。');
  if (!/^1[3-9][0-9]{9}$/.test(details.phone.trim())) throw new Error('请输入11位中国大陆手机号。');
  if (details.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(details.email.trim())) {
    throw new Error('请输入正确的邮箱地址。');
  }
  if (!details.orgUnitId) throw new Error('请选择所属组织。');
}

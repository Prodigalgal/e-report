import type { WorkOrderType, LoginResponse } from '@/types/api';
import sharedWorkOrderCatalog from '../../../shared/work-order-catalog.json' with { type: 'json' };

export const PENDING_WORK_ORDER_STATUS = '待处理';

export const ADMIN_PERMISSIONS = {
  SYSTEM_SUPER_ADMINISTRATOR: 'system:super-administrator',
  DASHBOARD_STATISTICS_VIEW: 'dashboard:statistics:view',
  USER_MANAGE_VIEW: 'user:manage:view',
  API_TOKEN_VIEW: 'api-token:view',
  API_TOKEN_CREATE: 'api-token:create',
  API_TOKEN_REVOKE: 'api-token:revoke',
  USER_MANAGE_CREATE: 'user:manage:create',
  USER_MANAGE_UPDATE_DETAILS: 'user:manage:details:update',
  USER_MANAGE_CONTACT_UPDATE: 'user:manage:contact:update',
  USER_MANAGE_ORGANIZATION_MOVE: 'user:manage:organization:move',
  USER_MANAGE_DEPARTMENT_MOVE: 'user:manage:department:move',
  USER_MANAGE_STATUS_ACTIVATE: 'user:manage:status:activate',
  USER_MANAGE_STATUS_DEACTIVATE: 'user:manage:status:deactivate',
  USER_MANAGE_STATUS_LOCK: 'user:manage:status:lock',
  USER_MANAGE_STATUS_UNLOCK: 'user:manage:status:unlock',
  USER_AUTHORIZATION_VIEW: 'user:authorization:view',
  USER_AUTHORIZATION_GRANT: 'user:authorization:grant',
  USER_AUTHORIZATION_ADJUST: 'user:authorization:adjust',
  USER_AUTHORIZATION_REVOKE: 'user:authorization:revoke',
  USER_AUTHORIZATION_REATTACH: 'user:authorization:reattach',
  USER_MANAGE_RESET_PASSWORD: 'user:manage:password:reset',
  USER_MANAGE_DELETE: 'user:manage:delete',
  USER_MANAGE_RESTORE: 'user:manage:restore',
  SYSTEM_CONFIG_VIEW: 'system:config:view',
  SYSTEM_CONFIG_CREATE: 'system:config:create',
  SYSTEM_CONFIG_UPDATE: 'system:config:update',
  SYSTEM_CONFIG_DELETE: 'system:config:delete',
  AUTHORIZATION_PROFILE_VIEW: 'authorization-template:view',
  AUTHORIZATION_PROFILE_CREATE: 'authorization-template:create',
  AUTHORIZATION_PROFILE_DRAFT_UPDATE: 'authorization-template:update',
  AUTHORIZATION_PROFILE_COPY: 'authorization-template:clone',
  AUTHORIZATION_PROFILE_PUBLISH: 'authorization-template:enable',
  AUTHORIZATION_PROFILE_RETIRE: 'authorization-template:disable',
  ORG_MANAGE_VIEW: 'organization:manage:view',
  DEPARTMENT_VIEW: 'department:view',
  APP_UPDATE_VIEW: 'app-update:view',
  APP_UPDATE_POLICY_UPDATE: 'app-update:policy-update',
  APP_UPDATE_DELETE: 'app-update:delete',
  TENANT_MANAGE_VIEW: 'tenant:manage:view',
  SLA_POLICY_VIEW: 'sla-policy:view',
  SLA_POLICY_UPDATE: 'sla-policy:update',
  OBSERVABILITY_VIEW: 'observability:view',
} as const;

type WorkOrderCatalogEntry = {
  reportType: WorkOrderType;
  slug: string;
  reportLabel: string;
  respondLabel: string;
  navLabel: string;
  locationLabel: string;
  reasonLabel: string;
  description: string;
  permissions: {
    report: string;
    responseSubmit: string;
    view: string;
    export: string;
    historyView: string;
    auditView: string;
    attachmentRead: string;
    claim: string;
    assignmentAccept: string;
    assignmentReject: string;
    assignmentCreate: string;
    assignmentReassign: string;
    verificationApprove: string;
    verificationReject: string;
    verificationSelfApprove: string;
    verificationSelfReject: string;
    cancel: string;
    hold: string;
    resume: string;
    withdraw: string;
    transferCreate: string;
    transferAccept: string;
    transferReject: string;
    transferCancel: string;
  };
};

export const WORK_ORDER_TYPES = sharedWorkOrderCatalog as readonly WorkOrderCatalogEntry[];

export const WORK_ORDER_TASKS = {
  REPORT: { key: 'report', label: '我要报单', path: '/report', description: '选择三类现场工单进行上报。' },
  SEARCH: { key: 'search', label: '工单查询', path: '/search', description: '集中查询本人或权限范围内的工单，并直接查看或处理。' },
  ADMIN: { key: 'admin', label: '管理后台', path: '/admin', description: '查看统计，并维护用户、岗位授权、配置和发布版本。' },
} as const;

export const WORK_ORDER_REPORT_PERMISSIONS = WORK_ORDER_TYPES.map((item) => item.permissions.report);
export const WORK_ORDER_RESPOND_PERMISSIONS = WORK_ORDER_TYPES.map((item) => item.permissions.responseSubmit);
export const WORK_ORDER_VIEW_PERMISSIONS = WORK_ORDER_TYPES.map((item) => item.permissions.view);
export const WORK_ORDER_CLAIM_PERMISSIONS = WORK_ORDER_TYPES.map((item) => item.permissions.claim);
export const WORK_ORDER_ASSIGN_PERMISSIONS = WORK_ORDER_TYPES.map((item) => item.permissions.assignmentCreate);
export const WORK_ORDER_REASSIGN_PERMISSIONS = WORK_ORDER_TYPES.map((item) => item.permissions.assignmentReassign);
export const WORK_ORDER_VERIFY_PERMISSIONS = WORK_ORDER_TYPES.flatMap((item) => [
  item.permissions.verificationApprove,
  item.permissions.verificationReject,
]);
export const WORK_ORDER_TRANSFER_PERMISSIONS = WORK_ORDER_TYPES.flatMap((item) => [
  item.permissions.transferCreate,
  item.permissions.transferAccept,
  item.permissions.transferReject,
  item.permissions.transferCancel,
]);
export const WORK_ORDER_MANAGEMENT_PERMISSIONS = [
  ...WORK_ORDER_VIEW_PERMISSIONS,
  ...WORK_ORDER_RESPOND_PERMISSIONS,
  ...WORK_ORDER_CLAIM_PERMISSIONS,
  ...WORK_ORDER_ASSIGN_PERMISSIONS,
  ...WORK_ORDER_REASSIGN_PERMISSIONS,
  ...WORK_ORDER_VERIFY_PERMISSIONS,
  ...WORK_ORDER_TRANSFER_PERMISSIONS,
];
export const ADMIN_SETTINGS_PERMISSIONS = [
  ADMIN_PERMISSIONS.SYSTEM_SUPER_ADMINISTRATOR,
  ADMIN_PERMISSIONS.APP_UPDATE_VIEW,
  ADMIN_PERMISSIONS.USER_MANAGE_VIEW,
  ADMIN_PERMISSIONS.SYSTEM_CONFIG_VIEW,
  ADMIN_PERMISSIONS.AUTHORIZATION_PROFILE_VIEW,
  ADMIN_PERMISSIONS.ORG_MANAGE_VIEW,
  ADMIN_PERMISSIONS.DEPARTMENT_VIEW,
  ADMIN_PERMISSIONS.TENANT_MANAGE_VIEW,
  ADMIN_PERMISSIONS.SLA_POLICY_VIEW,
  ADMIN_PERMISSIONS.OBSERVABILITY_VIEW,
];

export const DASHBOARD_STATISTICS_PERMISSIONS = [
  ADMIN_PERMISSIONS.DASHBOARD_STATISTICS_VIEW,
];

export const MANAGEMENT_ENTRY_PERMISSIONS = [...new Set([
  ...ADMIN_SETTINGS_PERMISSIONS,
  ...DASHBOARD_STATISTICS_PERMISSIONS,
  ...WORK_ORDER_MANAGEMENT_PERMISSIONS,
])];

export function findWorkOrderType(reportType?: string) {
  return WORK_ORDER_TYPES.find((item) => item.reportType === reportType);
}

export function findWorkOrderTypeBySlug(slug?: string) {
  return WORK_ORDER_TYPES.find((item) => item.slug === slug);
}

export function reportTypeToSlug(reportType: WorkOrderType) {
  return findWorkOrderType(reportType)?.slug || 'network';
}

export function canRespondToReportType(reportType: string, hasPermission: (permission: string) => boolean) {
  const item = findWorkOrderType(reportType);
  return item ? hasPermission(item.permissions.responseSubmit) : false;
}

export function hasAllowedAction(workOrder: { allowedActions?: readonly string[] }, action: string) {
  return Array.isArray(workOrder.allowedActions) && workOrder.allowedActions.includes(action);
}

export function userHasAnyPermission(user: LoginResponse | null | undefined, permissions: readonly string[]) {
  const userPermissions = Array.isArray(user?.permissions) ? user.permissions : [];
  return permissions.some((permission) => userPermissions.includes(permission));
}

export function getVisibleAdminWorkOrderTypes(hasPermission: (permission: string) => boolean) {
  return WORK_ORDER_TYPES.filter((item) => hasPermission(item.permissions.view) || hasPermission(item.permissions.responseSubmit));
}

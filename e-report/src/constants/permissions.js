import { WORK_ORDER_CATALOG } from './workOrderCatalog.js';

export const PERMISSIONS = {
    SYSTEM_SUPER_ADMINISTRATOR: 'system:super-administrator',
    DASHBOARD_STATISTICS_VIEW: 'dashboard:statistics:view',
    USER_MANAGE_VIEW: 'user:manage:view',
    USER_IMPORT_TEMPLATE_DOWNLOAD: 'user:import:template:download',
    USER_IMPORT_PREVIEW: 'user:import:preview',
    USER_IMPORT_EXECUTE: 'user:import:execute',
    USER_IMPORT_CANCEL: 'user:import:cancel',
    USER_IMPORT_TASK_VIEW: 'user:import:task:view',
    USER_IMPORT_ERROR_REPORT_DOWNLOAD: 'user:import:error-report:download',
    USER_EXPORT_CREATE: 'user:export:create',
    USER_EXPORT_TASK_VIEW: 'user:export:task:view',
    USER_EXPORT_CANCEL: 'user:export:cancel',
    USER_EXPORT_DOWNLOAD: 'user:export:download',
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
    USER_MANAGE_RESET_PASSWORD: 'user:manage:password:reset',
    USER_MANAGE_DELETE: 'user:manage:delete',
    USER_MANAGE_RESTORE: 'user:manage:restore',
    SYSTEM_CONFIG_VIEW: 'system:config:view',
    SYSTEM_CONFIG_CREATE: 'system:config:create',
    SYSTEM_CONFIG_UPDATE: 'system:config:update',
    SYSTEM_CONFIG_DELETE: 'system:config:delete',
    NOTIFICATION_CONFIG_VIEW: 'notification:config:view',
    EMAIL_DELIVERY_VIEW: 'notification:email-delivery:view',
    NOTIFICATION_CHANNEL_UPDATE: 'notification:channel:update',
    NOTIFICATION_SCHEDULE_UPDATE: 'notification:schedule:update',
    NOTIFICATION_SENDER_UPDATE: 'notification:sender:update',
    AUTHORIZATION_PROFILE_VIEW: 'authorization-template:view',
    AUTHORIZATION_PROFILE_CREATE: 'authorization-template:create',
    AUTHORIZATION_PROFILE_DRAFT_UPDATE: 'authorization-template:update',
    AUTHORIZATION_PROFILE_COPY: 'authorization-template:clone',
    AUTHORIZATION_PROFILE_PUBLISH: 'authorization-template:enable',
    AUTHORIZATION_PROFILE_RETIRE: 'authorization-template:disable',
    ORG_MANAGE_VIEW: 'organization:manage:view',
    ORG_MANAGE_CREATE: 'organization:manage:create',
    ORG_MANAGE_UPDATE: 'organization:manage:update',
    ORG_MANAGE_MOVE: 'organization:manage:move',
    ORG_MANAGE_ENABLE: 'organization:manage:enable',
    ORG_MANAGE_DISABLE: 'organization:manage:disable',
    DEPARTMENT_VIEW: 'department:view',
    DEPARTMENT_CREATE: 'department:create',
    DEPARTMENT_UPDATE: 'department:update',
    DEPARTMENT_MOVE: 'department:move',
    DEPARTMENT_ENABLE: 'department:enable',
    DEPARTMENT_DISABLE: 'department:disable',
    DEPARTMENT_MERGE: 'department:merge',
    DEPARTMENT_DELETE: 'department:delete',
    APP_UPDATE_VIEW: 'app-update:view',
    APP_UPDATE_PUBLISH: 'app-update:publish',
    APP_UPDATE_POLICY_UPDATE: 'app-update:policy-update',
    APP_UPDATE_STORAGE_VIEW: 'app-update:storage:view',
    APP_UPDATE_STORAGE_SYNC: 'app-update:storage:sync',
    APP_UPDATE_STORAGE_DELETE: 'app-update:storage:delete',
    TENANT_MANAGE_VIEW: 'tenant:manage:view',
    SLA_POLICY_VIEW: 'sla-policy:view',
    SLA_POLICY_UPDATE: 'sla-policy:update',
    OBSERVABILITY_VIEW: 'observability:view',
};

export const WORK_ORDER_PERMISSIONS_BY_TYPE = Object.fromEntries(
    WORK_ORDER_CATALOG.map(item => [item.reportType, item.permissions]),
);

export const WORK_ORDER_REPORT_PERMISSIONS = Object.values(WORK_ORDER_PERMISSIONS_BY_TYPE).map(permissions => permissions.report);
export const WORK_ORDER_RESPOND_PERMISSIONS = Object.values(WORK_ORDER_PERMISSIONS_BY_TYPE).map(permissions => permissions.responseSubmit);
export const WORK_ORDER_VIEW_PERMISSIONS = Object.values(WORK_ORDER_PERMISSIONS_BY_TYPE).map(permissions => permissions.view);
export const WORK_ORDER_EXPORT_PERMISSIONS = Object.values(WORK_ORDER_PERMISSIONS_BY_TYPE).map(permissions => permissions.export);
export const WORK_ORDER_CLAIM_PERMISSIONS = Object.values(WORK_ORDER_PERMISSIONS_BY_TYPE).map(permissions => permissions.claim);
export const WORK_ORDER_ASSIGN_PERMISSIONS = Object.values(WORK_ORDER_PERMISSIONS_BY_TYPE).map(permissions => permissions.assignmentCreate);
export const WORK_ORDER_REASSIGN_PERMISSIONS = Object.values(WORK_ORDER_PERMISSIONS_BY_TYPE).map(permissions => permissions.assignmentReassign);
export const WORK_ORDER_VERIFY_PERMISSIONS = Object.values(WORK_ORDER_PERMISSIONS_BY_TYPE)
    .flatMap(permissions => [permissions.verificationApprove, permissions.verificationReject]);
export const WORK_ORDER_TRANSFER_PERMISSIONS = Object.values(WORK_ORDER_PERMISSIONS_BY_TYPE)
    .flatMap(permissions => [permissions.transferCreate, permissions.transferAccept, permissions.transferReject, permissions.transferCancel]);
export const WORK_ORDER_MANAGEMENT_PERMISSIONS = [
    ...WORK_ORDER_VIEW_PERMISSIONS,
    ...WORK_ORDER_EXPORT_PERMISSIONS,
    ...WORK_ORDER_RESPOND_PERMISSIONS,
    ...WORK_ORDER_CLAIM_PERMISSIONS,
    ...WORK_ORDER_ASSIGN_PERMISSIONS,
    ...WORK_ORDER_REASSIGN_PERMISSIONS,
    ...WORK_ORDER_VERIFY_PERMISSIONS,
    ...WORK_ORDER_TRANSFER_PERMISSIONS,
];
export const MANAGEMENT_SETTINGS_PERMISSIONS = [
    PERMISSIONS.SYSTEM_SUPER_ADMINISTRATOR,
    PERMISSIONS.USER_MANAGE_VIEW,
    PERMISSIONS.SYSTEM_CONFIG_VIEW,
    PERMISSIONS.NOTIFICATION_CONFIG_VIEW,
    PERMISSIONS.EMAIL_DELIVERY_VIEW,
    PERMISSIONS.AUTHORIZATION_PROFILE_VIEW,
    PERMISSIONS.ORG_MANAGE_VIEW,
    PERMISSIONS.DEPARTMENT_VIEW,
    PERMISSIONS.TENANT_MANAGE_VIEW,
    PERMISSIONS.SLA_POLICY_VIEW,
    PERMISSIONS.OBSERVABILITY_VIEW,
];
export const OBSERVABILITY_VIEW_PERMISSIONS = [PERMISSIONS.OBSERVABILITY_VIEW];
export const AUTHORIZATION_PROFILE_VIEW_PERMISSIONS = [PERMISSIONS.AUTHORIZATION_PROFILE_VIEW];
export const APP_UPDATE_VIEW_PERMISSIONS = [
    PERMISSIONS.SYSTEM_SUPER_ADMINISTRATOR,
    PERMISSIONS.APP_UPDATE_VIEW,
];
export const ORG_MANAGEMENT_VIEW_PERMISSIONS = [PERMISSIONS.ORG_MANAGE_VIEW, PERMISSIONS.DEPARTMENT_VIEW];
export const TENANT_MANAGEMENT_VIEW_PERMISSIONS = [PERMISSIONS.TENANT_MANAGE_VIEW];
export const SLA_POLICY_VIEW_PERMISSIONS = [PERMISSIONS.SLA_POLICY_VIEW];
export const DASHBOARD_STATISTICS_PERMISSIONS = [
    PERMISSIONS.DASHBOARD_STATISTICS_VIEW,
];
export const CONFIG_CENTER_VIEW_PERMISSIONS = [
    PERMISSIONS.SYSTEM_CONFIG_VIEW,
    PERMISSIONS.NOTIFICATION_CONFIG_VIEW,
];
export const MANAGEMENT_ENTRY_PERMISSIONS = [...new Set([
    ...DASHBOARD_STATISTICS_PERMISSIONS,
    ...WORK_ORDER_MANAGEMENT_PERMISSIONS,
    ...MANAGEMENT_SETTINGS_PERMISSIONS,
    ...APP_UPDATE_VIEW_PERMISSIONS,
])];

export const hasAnyPermission = (user, permissions) => {
    const userPermissions = Array.isArray(user?.permissions) ? user.permissions : [];
    return permissions.some(permission => userPermissions.includes(permission));
};

export const getWorkOrderPermissions = (reportType) => WORK_ORDER_PERMISSIONS_BY_TYPE[reportType] || null;

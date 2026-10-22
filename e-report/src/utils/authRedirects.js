import {
    APP_UPDATE_VIEW_PERMISSIONS,
    DASHBOARD_STATISTICS_PERMISSIONS,
    OBSERVABILITY_VIEW_PERMISSIONS,
    ORG_MANAGEMENT_VIEW_PERMISSIONS,
    PERMISSIONS,
    AUTHORIZATION_PROFILE_VIEW_PERMISSIONS,
    SLA_POLICY_VIEW_PERMISSIONS,
    TENANT_MANAGEMENT_VIEW_PERMISSIONS,
    WORK_ORDER_MANAGEMENT_PERMISSIONS,
    WORK_ORDER_REPORT_PERMISSIONS,
    WORK_ORDER_RESPOND_PERMISSIONS,
    getWorkOrderPermissions,
    hasAnyPermission,
} from '../constants/permissions.js';
import { WORK_ORDER_TYPES } from '../constants/workOrderCatalog.js';
import { isAdminUser } from './adminAccess.js';

export const ADMIN_ROUTE_ACCESS_RULES = [
    { path: '/admin/dashboard', requiredAnyPermissions: DASHBOARD_STATISTICS_PERMISSIONS },
    { path: '/admin/work-orders/search', requiredAnyPermissions: WORK_ORDER_MANAGEMENT_PERMISSIONS },
    { path: '/admin/observability', requiredAnyPermissions: OBSERVABILITY_VIEW_PERMISSIONS },
    { path: '/admin/user-management', requiredAnyPermissions: [PERMISSIONS.USER_MANAGE_VIEW] },
    { path: '/admin/settings/configs', requiredAnyPermissions: [
        PERMISSIONS.SYSTEM_CONFIG_VIEW,
        PERMISSIONS.NOTIFICATION_CONFIG_VIEW,
    ] },
    { path: '/admin/settings/authorization-profiles', requiredAnyPermissions: AUTHORIZATION_PROFILE_VIEW_PERMISSIONS },
    { path: '/admin/settings/app-releases', requiredAnyPermissions: APP_UPDATE_VIEW_PERMISSIONS },
    { path: '/admin/settings/org-units', requiredAnyPermissions: ORG_MANAGEMENT_VIEW_PERMISSIONS },
    { path: '/admin/settings/tenants', requiredAnyPermissions: TENANT_MANAGEMENT_VIEW_PERMISSIONS },
    { path: '/admin/settings/sla-policies', requiredAnyPermissions: SLA_POLICY_VIEW_PERMISSIONS },
];

const findFirstWorkOrderPath = (user, permissionAction, pathKey) => {
    const workOrderType = WORK_ORDER_TYPES.find(item => {
        const permissions = getWorkOrderPermissions(item.reportType);
        return permissions && hasAnyPermission(user, [permissions[permissionAction]]);
    });
    return workOrderType?.[pathKey] || null;
};

export const resolveDefaultAuthenticatedPath = user => {
    if (!user) return '/';
    if (isAdminUser(user)) return resolveFirstAccessibleAdminPath(user);
    if (hasAnyPermission(user, WORK_ORDER_REPORT_PERMISSIONS)) {
        return findFirstWorkOrderPath(user, 'report', 'reportPath') || '/';
    }
    if (hasAnyPermission(user, WORK_ORDER_RESPOND_PERMISSIONS)) {
        return '/work-orders/search?status=待处理';
    }
    return '/work-orders/search';
};

export const resolveRequestedPath = from => {
    const pathname = from?.pathname;
    if (!pathname || !pathname.startsWith('/') || pathname === '/login' || pathname === '/admin/login') {
        return null;
    }
    return `${pathname}${from.search || ''}${from.hash || ''}`;
};

export const canAccessAdminPath = (user, pathname) => {
    if (!isAdminUser(user) || !pathname?.startsWith('/admin/')) return false;
    const rule = ADMIN_ROUTE_ACCESS_RULES.find(candidate => (
        pathname === candidate.path || pathname.startsWith(`${candidate.path}/`)
    ));
    return Boolean(rule && hasAnyPermission(user, rule.requiredAnyPermissions));
};

export const resolveFirstAccessibleAdminPath = user => (
    ADMIN_ROUTE_ACCESS_RULES.find(rule => hasAnyPermission(user, rule.requiredAnyPermissions))?.path || '/'
);

export const resolveUserLoginDestination = (user, from) => (
    resolveRequestedPath(from) || resolveDefaultAuthenticatedPath(user)
);

export const resolveAdminLoginDestination = (user, from) => {
    if (!isAdminUser(user)) {
        return resolveDefaultAuthenticatedPath(user);
    }
    const requestedPath = resolveRequestedPath(from);
    if (requestedPath && canAccessAdminPath(user, from.pathname)) {
        return requestedPath;
    }
    return resolveFirstAccessibleAdminPath(user);
};

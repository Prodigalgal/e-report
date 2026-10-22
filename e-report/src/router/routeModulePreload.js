export const routeModuleLoaders = {
    appInfo: () => import('../pages/AppInfoPage/AppInfoPage'),
    home: () => import('../pages/HomePage/HomePage'),
    profile: () => import('../pages/UserProfilePage/UserProfilePage'),
    userLogin: () => import('../pages/UserLoginPage/UserLoginPage'),
    adminLogin: () => import('../pages/AdminLoginPage/AdminLoginPage'),
    workOrderSearch: () => import('../pages/WorkOrderSearchPage/WorkOrderSearchPage'),
    report: () => import('../pages/CreateWorkOrderPage/CreateWorkOrderPage'),
    reportSelection: () => import('../pages/WorkOrderTypeSelectionPage/WorkOrderTypeSelectionPage'),
    respond: () => import('../pages/RespondWorkOrderPage/RespondWorkOrderPage'),
    adminLayout: () => import('../components/layout/AdminLayout'),
    adminDashboard: () => import('../pages/AdminMainDashboardPage/AdminMainDashboardPage'),
    adminWorkOrderDetail: () => import('../pages/AdminWorkOrderDetailPage/AdminWorkOrderDetailPage'),
    adminUserManagement: () => import('../pages/AdminUserManagementPage/AdminUserManagementPage'),
    adminUserDetail: () => import('../pages/AdminUserDetailPage/AdminUserDetailPage'),
    adminSystemConfig: () => import('../pages/AdminSystemConfigPage/AdminSystemConfigPage'),
    adminAuthorizationProfiles: () => import('../pages/AdminAuthorizationProfilePage/AdminAuthorizationProfilePage'),
    adminAppReleases: () => import('../pages/AdminAppReleasePage/AdminAppReleasePage'),
    adminOrgUnits: () => import('../pages/AdminOrgUnitPage/AdminOrgUnitPage'),
    adminTenants: () => import('../pages/AdminTenantPage/AdminTenantPage'),
    adminSlaPolicies: () => import('../pages/AdminSlaPolicyPage/AdminSlaPolicyPage'),
    adminObservability: () => import('../pages/AdminObservabilityPage/AdminObservabilityPage'),
};

const exactRouteLoaders = new Map([
    ['/app-info', routeModuleLoaders.appInfo],
    ['/', routeModuleLoaders.home],
    ['/profile', routeModuleLoaders.profile],
    ['/login', routeModuleLoaders.userLogin],
    ['/admin/login', routeModuleLoaders.adminLogin],
    ['/work-orders/search', routeModuleLoaders.workOrderSearch],
    ['/report', routeModuleLoaders.reportSelection],
    ['/report/network', routeModuleLoaders.report],
    ['/report/box-door-open', routeModuleLoaders.report],
    ['/report/damaged-box-replacement', routeModuleLoaders.report],
    ['/respond/network', routeModuleLoaders.respond],
    ['/respond/box-door-open', routeModuleLoaders.respond],
    ['/respond/damaged-box-replacement', routeModuleLoaders.respond],
    ['/admin', routeModuleLoaders.adminLayout],
    ['/admin/dashboard', routeModuleLoaders.adminDashboard],
    ['/admin/observability', routeModuleLoaders.adminObservability],
    ['/admin/work-orders/search', routeModuleLoaders.workOrderSearch],
    ['/admin/user-management', routeModuleLoaders.adminUserManagement],
    ['/admin/settings', routeModuleLoaders.adminSystemConfig],
    ['/admin/settings/configs', routeModuleLoaders.adminSystemConfig],
    ['/admin/settings/authorization-profiles', routeModuleLoaders.adminAuthorizationProfiles],
    ['/admin/settings/app-releases', routeModuleLoaders.adminAppReleases],
    ['/admin/settings/org-units', routeModuleLoaders.adminOrgUnits],
    ['/admin/settings/tenants', routeModuleLoaders.adminTenants],
    ['/admin/settings/sla-policies', routeModuleLoaders.adminSlaPolicies],
]);

const prefixRouteLoaders = [
    ['/admin/user-management/', routeModuleLoaders.adminUserDetail],
    ['/admin/work-orders/', routeModuleLoaders.adminWorkOrderDetail],
    ['/work-orders/', routeModuleLoaders.adminWorkOrderDetail],
    ['/admin/settings/', routeModuleLoaders.adminSystemConfig],
];

const preloadPromises = new WeakMap();

const normalizeRoutePath = (path) => {
    const normalizedPath = String(path || '/').split(/[?#]/)[0] || '/';
    if (normalizedPath !== '/' && normalizedPath.endsWith('/')) {
        return normalizedPath.slice(0, -1);
    }
    return normalizedPath;
};

export function preloadRouteModule(path) {
    const normalizedPath = normalizeRoutePath(path);
    const exactLoader = exactRouteLoaders.get(normalizedPath);
    if (exactLoader) {
        preloadLoader(exactLoader);
        return;
    }

    const prefixMatch = prefixRouteLoaders.find(([prefix]) => normalizedPath.startsWith(prefix));
    if (prefixMatch) {
        preloadLoader(prefixMatch[1]);
    }
}

function preloadLoader(loader) {
    if (!preloadPromises.has(loader)) {
        preloadPromises.set(loader, loader().catch((error) => {
            preloadPromises.delete(loader);
            throw error;
        }));
    }
    return preloadPromises.get(loader);
}

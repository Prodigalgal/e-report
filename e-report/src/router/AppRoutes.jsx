import React, { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Paper, Typography, Button, Container } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';

import ProtectedRoute from './ProtectedRoute';
import RouteLoadingFallback from './RouteLoadingFallback';
import { routeModuleLoaders } from './routeModulePreload';
import {
    DASHBOARD_STATISTICS_PERMISSIONS,
    APP_UPDATE_VIEW_PERMISSIONS,
    ORG_MANAGEMENT_VIEW_PERMISSIONS,
    PERMISSIONS,
    AUTHORIZATION_PROFILE_VIEW_PERMISSIONS,
    TENANT_MANAGEMENT_VIEW_PERMISSIONS,
    SLA_POLICY_VIEW_PERMISSIONS,
    OBSERVABILITY_VIEW_PERMISSIONS,
    MANAGEMENT_ENTRY_PERMISSIONS,
    WORK_ORDER_MANAGEMENT_PERMISSIONS,
    WORK_ORDER_PERMISSIONS_BY_TYPE,
} from '../constants/permissions';
import { WORK_ORDER_TYPES } from '../constants/workOrderCatalog';
import { resolveFirstAccessibleAdminPath } from '../utils/authRedirects';
import { useAuth } from '../hooks/useAuth';

const HomePage = lazy(routeModuleLoaders.home);
const UserProfilePage = lazy(routeModuleLoaders.profile);
const AppInfoPage = lazy(routeModuleLoaders.appInfo);
const CreateWorkOrderPage = lazy(routeModuleLoaders.report);
const WorkOrderTypeSelectionPage = lazy(routeModuleLoaders.reportSelection);
const RespondWorkOrderPage = lazy(routeModuleLoaders.respond);
const AdminLoginPage = lazy(routeModuleLoaders.adminLogin);
const UserLoginPage = lazy(routeModuleLoaders.userLogin);
const WorkOrderSearchPage = lazy(routeModuleLoaders.workOrderSearch);
const AdminLayout = lazy(routeModuleLoaders.adminLayout);
const AdminMainDashboardPage = lazy(routeModuleLoaders.adminDashboard);
const AdminUserManagementPage = lazy(routeModuleLoaders.adminUserManagement);
const AdminUserDetailPage = lazy(routeModuleLoaders.adminUserDetail);
const AdminWorkOrderDetailPage = lazy(routeModuleLoaders.adminWorkOrderDetail);
const AdminSystemConfigPage = lazy(routeModuleLoaders.adminSystemConfig);
const AdminAuthorizationProfilePage = lazy(routeModuleLoaders.adminAuthorizationProfiles);
const AdminAppReleasePage = lazy(routeModuleLoaders.adminAppReleases);
const AdminOrgUnitPage = lazy(routeModuleLoaders.adminOrgUnits);
const AdminTenantPage = lazy(routeModuleLoaders.adminTenants);
const AdminSlaPolicyPage = lazy(routeModuleLoaders.adminSlaPolicies);
const AdminObservabilityPage = lazy(routeModuleLoaders.adminObservability);

function LazyRoute({ children }) {
    return (
        <Suspense fallback={<RouteLoadingFallback />}>
            {children}
        </Suspense>
    );
}

function AdminDefaultRedirect() {
    const { currentUser } = useAuth();
    return <Navigate to={resolveFirstAccessibleAdminPath(currentUser)} replace />;
}

function AppRoutes() {
    return (
        <Routes>
            {/* Public Routes */}
            <Route path="/app-info" element={<LazyRoute><AppInfoPage /></LazyRoute>} />
            <Route path="/" element={
                <ProtectedRoute>
                    <LazyRoute><HomePage /></LazyRoute>
                </ProtectedRoute>
            } />
            <Route path="/login" element={<LazyRoute><UserLoginPage /></LazyRoute>} />
            <Route path="/admin/login" element={<LazyRoute><AdminLoginPage /></LazyRoute>} />

            <Route path="/profile" element={
                <ProtectedRoute>
                    <LazyRoute><UserProfilePage /></LazyRoute>
                </ProtectedRoute>
            } />

            {/* User Protected Routes */}
            <Route path="/report" element={
                <ProtectedRoute>
                    <LazyRoute><WorkOrderTypeSelectionPage /></LazyRoute>
                </ProtectedRoute>
            } />
            {WORK_ORDER_TYPES.map(item => (
                <Route key={`${item.reportType}-report`} path={item.reportPath} element={
                    <ProtectedRoute requiredPermissions={[WORK_ORDER_PERMISSIONS_BY_TYPE[item.reportType].report]}>
                        <LazyRoute>
                            <CreateWorkOrderPage reportType={item.reportType} />
                        </LazyRoute>
                    </ProtectedRoute>
                } />
            ))}
            {WORK_ORDER_TYPES.map(item => (
                <Route key={`${item.reportType}-respond`} path={item.respondPath} element={
                    <ProtectedRoute requiredPermissions={[WORK_ORDER_PERMISSIONS_BY_TYPE[item.reportType].responseSubmit]}>
                        <LazyRoute>
                            <RespondWorkOrderPage
                                reportType={item.reportType}
                                title={`${item.navLabel}回单`}
                            />
                        </LazyRoute>
                    </ProtectedRoute>
                } />
            ))}
            <Route path="/work-orders/search" element={
                <ProtectedRoute>
                    <LazyRoute>
                        <WorkOrderSearchPage />
                    </LazyRoute>
                </ProtectedRoute>
            } />
            <Route path="/work-orders/:workOrderId" element={
                <ProtectedRoute>
                    <LazyRoute>
                        <AdminWorkOrderDetailPage />
                    </LazyRoute>
                </ProtectedRoute>
            } />
            {/* Admin Protected Routes with AdminLayout */}
            <Route
                path="/admin"
                element={
                    <ProtectedRoute requiredAnyPermissions={MANAGEMENT_ENTRY_PERMISSIONS}>
                        <LazyRoute>
                            <AdminLayout />
                        </LazyRoute>
                    </ProtectedRoute>
                }
            >
                <Route index element={<AdminDefaultRedirect />} />
                <Route path="dashboard" element={
                    <ProtectedRoute requiredAnyPermissions={DASHBOARD_STATISTICS_PERMISSIONS}>
                        <LazyRoute>
                            <AdminMainDashboardPage />
                        </LazyRoute>
                    </ProtectedRoute>
                } />
                <Route path="work-orders/search" element={
                    <ProtectedRoute requiredAnyPermissions={WORK_ORDER_MANAGEMENT_PERMISSIONS}>
                        <LazyRoute>
                            <WorkOrderSearchPage />
                        </LazyRoute>
                    </ProtectedRoute>
                } />
                <Route path="observability" element={
                    <ProtectedRoute requiredAnyPermissions={OBSERVABILITY_VIEW_PERMISSIONS}>
                        <LazyRoute>
                            <AdminObservabilityPage />
                        </LazyRoute>
                    </ProtectedRoute>
                } />
                <Route path="work-orders/:workOrderId" element={
                    <ProtectedRoute requiredAnyPermissions={WORK_ORDER_MANAGEMENT_PERMISSIONS}>
                        <LazyRoute>
                            <AdminWorkOrderDetailPage />
                        </LazyRoute>
                    </ProtectedRoute>
                } />
                {WORK_ORDER_TYPES.map(item => (
                    <Route key={`${item.reportType}-admin-respond`} path={`work-orders/respond/${item.reportType}`} element={
                        <ProtectedRoute requiredPermissions={[WORK_ORDER_PERMISSIONS_BY_TYPE[item.reportType].responseSubmit]}>
                            <LazyRoute>
                                <RespondWorkOrderPage
                                    reportType={item.reportType}
                                    title={`${item.navLabel}回单`}
                                    searchPath="/admin/work-orders/search"
                                />
                            </LazyRoute>
                        </ProtectedRoute>
                    } />
                ))}
                <Route path="user-management" element={
                    <ProtectedRoute requiredPermissions={[PERMISSIONS.USER_MANAGE_VIEW]}>
                        <LazyRoute>
                            <AdminUserManagementPage />
                        </LazyRoute>
                    </ProtectedRoute>
                } />
                <Route path="user-management/:userId" element={
                    <ProtectedRoute requiredPermissions={[PERMISSIONS.USER_MANAGE_VIEW]}>
                        <LazyRoute>
                            <AdminUserDetailPage />
                        </LazyRoute>
                    </ProtectedRoute>
                } />
                <Route path="*" element={
                    <Container maxWidth="sm" sx={{mt:4}}><Paper sx={{p:3, textAlign:'center'}}><Typography variant="h5">管理区域页面未找到</Typography><Button component={RouterLink} to="/admin/dashboard">返回仪表盘</Button></Paper></Container>
                } />

                <Route path="settings"> {/* /admin/settings */}
                    <Route index element={<Navigate to="configs" replace />} />
                    <Route path="configs" element={
                        <ProtectedRoute requiredAnyPermissions={[
                            PERMISSIONS.SYSTEM_CONFIG_VIEW,
                            PERMISSIONS.NOTIFICATION_CONFIG_VIEW,
                        ]}>
                            <LazyRoute>
                                <AdminSystemConfigPage />
                            </LazyRoute>
                        </ProtectedRoute>
                    } />
                    <Route path="authorization-profiles" element={
                        <ProtectedRoute requiredAnyPermissions={AUTHORIZATION_PROFILE_VIEW_PERMISSIONS}>
                            <LazyRoute>
                                <AdminAuthorizationProfilePage />
                            </LazyRoute>
                        </ProtectedRoute>
                    } />
                    <Route path="app-releases" element={
                        <ProtectedRoute requiredAnyPermissions={APP_UPDATE_VIEW_PERMISSIONS}>
                            <LazyRoute>
                                <AdminAppReleasePage />
                            </LazyRoute>
                        </ProtectedRoute>
                    } />
                    <Route path="org-units" element={
                        <ProtectedRoute requiredAnyPermissions={ORG_MANAGEMENT_VIEW_PERMISSIONS}>
                            <LazyRoute>
                                <AdminOrgUnitPage />
                            </LazyRoute>
                        </ProtectedRoute>
                    } />
                    <Route path="tenants" element={
                        <ProtectedRoute requiredAnyPermissions={TENANT_MANAGEMENT_VIEW_PERMISSIONS}>
                            <LazyRoute>
                                <AdminTenantPage />
                            </LazyRoute>
                        </ProtectedRoute>
                    } />
                    <Route path="sla-policies" element={
                        <ProtectedRoute requiredAnyPermissions={SLA_POLICY_VIEW_PERMISSIONS}>
                            <LazyRoute>
                                <AdminSlaPolicyPage />
                            </LazyRoute>
                        </ProtectedRoute>
                    } />
                </Route>

            </Route>

            <Route path="*" element={
                <ProtectedRoute>
                    <Container maxWidth="sm" sx={{mt:4}}><Paper sx={{p: {xs: 2, sm: 3, md: 4}, textAlign: 'center'}}>
                        <Typography variant="h3" component="h1" gutterBottom>404</Typography>
                        <Typography variant="h5" color="text.secondary" gutterBottom>页面未找到</Typography>
                        <Typography color="text.secondary">抱歉，您要查找的页面不存在。</Typography>
                        <Button component={RouterLink} to="/" variant="contained" size="large" sx={{mt:3}}>返回首页</Button>
                    </Paper></Container>
                </ProtectedRoute>
            } />
        </Routes>
    );
}

export default AppRoutes;

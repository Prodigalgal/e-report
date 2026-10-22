import React from 'react';
import { Navigate, useLocation, Link as RouterLink } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth'; // useAuth 已经从 AuthProvider 获取 context
import { Typography, Paper, Button, Container } from '@mui/material';
import BrandLoading from '../components/Brand/BrandLoading';

function ProtectedRoute({ children, requiredPermissions, requiredAnyPermissions }) {
    const { currentUser, isAuthenticated, loadingAuth, hasPermission } = useAuth();
    const location = useLocation();

    if (loadingAuth) {
        return <BrandLoading compact minHeight="calc(100vh - 120px)" message="加载认证信息..." />;
    }

    if (!isAuthenticated()) {
        // 根据当前路径判断是导向普通用户登录还是管理员登录
        const loginPath = location.pathname.startsWith('/admin') ? '/admin/login' : '/login';
        return <Navigate to={loginPath} state={{ from: location }} replace />;
    }

    let permissionCheckPassed = true;
    if (requiredPermissions && Array.isArray(requiredPermissions) && requiredPermissions.length > 0) {
        permissionCheckPassed = requiredPermissions.every(permission => hasPermission(permission));
    }

    if (currentUser?.passwordChangeRequired && location.pathname !== '/profile') {
        return <Navigate to="/profile" replace />;
    }

    let anyPermissionCheckPassed = true;
    if (requiredAnyPermissions && Array.isArray(requiredAnyPermissions) && requiredAnyPermissions.length > 0) {
        anyPermissionCheckPassed = requiredAnyPermissions.some(permission => hasPermission(permission));
    }

    const isAuthorized = permissionCheckPassed && anyPermissionCheckPassed;

    if (!isAuthorized) {
        const missingPerms = requiredPermissions?.filter(p => !hasPermission(p)).join(', ') || '';
        const missingAnyPerms = requiredAnyPermissions && !anyPermissionCheckPassed ? requiredAnyPermissions.join(', ') : '';
        let denialReason = "未知原因";
        if (missingPerms && missingAnyPerms) {
            denialReason = `需要权限 (${missingPerms})，并且需要至少一个权限 (${missingAnyPerms})`;
        } else if (missingPerms) {
            denialReason = `需要权限 (${missingPerms})`;
        } else if (missingAnyPerms) {
            denialReason = `需要至少一个权限 (${missingAnyPerms})`;
        }

        return (
            <Container maxWidth="sm">
                <Paper sx={{ p: { xs: 2, sm: 3, md: 4 }, mt: 4, textAlign: 'center' }}>
                    <Typography variant="h4" component="h1" color="error" gutterBottom>
                        访问被拒绝（403）
                    </Typography>
                    <Typography variant="body1" color="text.secondary" paragraph>
                        抱歉，您没有足够的权限访问此页面。
                    </Typography>
                    <Typography variant="body2" color="text.secondary" paragraph>
                        {`详情: ${denialReason}.`}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        如果您认为这是一个错误，请联系系统管理员。
                    </Typography>
                    <Button component={RouterLink} to="/" variant="contained" size="large" sx={{ mt: 3 }}>
                        返回首页
                    </Button>
                </Paper>
            </Container>
        );
    }

    return children;
}

export default ProtectedRoute;

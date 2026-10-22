// src/components/layout/MainLayout.jsx
import React from 'react';
import { Box, Container as MuiContainer } from '@mui/material'; // 重命名 Container 为 MuiContainer 避免冲突
import AppHeader from './AppHeader'; // 这个是公共页面的 Header
import AppFooter from './AppFooter';
import BrandLoading from '../Brand/BrandLoading';
import { useAuth } from '../../hooks/useAuth';
import { useLocation } from 'react-router-dom'; // 用于判断是否是 admin 路径
import { pageGutters, uiLayout } from '../../ui/layout';

function MainLayout({ children }) {
    const { isAuthenticated, loadingAuth } = useAuth();
    const location = useLocation();
    const isAdminPath = location.pathname.startsWith('/admin'); // 判断是否是 admin 路径
    const isStandalonePublicPath = location.pathname === '/login'
        || location.pathname === '/admin/login'
        || location.pathname === '/app-info';

    if (isStandalonePublicPath) {
        return <>{children}</>;
    }

    if (loadingAuth && !isAdminPath) { // 仅非 admin 路径显示全局加载
        return <BrandLoading message="正在加载账号信息..." />;
    }

    if (!isAuthenticated()) {
        return <>{children}</>;
    }
    // 如果是 admin 路径，AdminLayout 内部会有自己的加载和布局逻辑，这里直接渲染 children (即 AdminLayout)
    // 或者如果 AdminLayout 的 ProtectedRoute 也处理 loadingAuth，则这里可能不需要额外判断

    // 如果是 admin 路径，并且 AdminLayout 已经渲染，则不渲染 MainLayout 的 Header 和 Footer
    if (isAdminPath) {
        return <>{children}</>; // AdminLayout 会提供自己的完整布局
    }

    return (
        <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', bgcolor: 'background.default' }}>
            <AppHeader />
            <MuiContainer
                component="main"
                maxWidth={false}
                disableGutters
                sx={{
                    flexGrow: 1,
                    minWidth: 0,
                    py: `${uiLayout.pageGutter}px`,
                    px: pageGutters,
                }}
            >
                {children}
            </MuiContainer>
            <AppFooter />
        </Box>
    );
}

export default MainLayout;

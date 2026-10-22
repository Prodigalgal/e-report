// src/components/layout/AdminLayout.jsx
import React, { useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigate, Link as RouterLink } from 'react-router-dom';
import { Box, CssBaseline, AppBar, Toolbar, Typography, IconButton, Drawer, List, ListItemButton, ListItemIcon, ListItemText, Divider, Tooltip, Avatar, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import MenuIcon from '@mui/icons-material/Menu';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import SpeedIcon from '@mui/icons-material/Speed';
import PeopleAltIcon from '@mui/icons-material/PeopleAlt';
import SettingsApplicationsIcon from '@mui/icons-material/SettingsApplications';
import ExitToAppIcon from '@mui/icons-material/ExitToApp';
import HomeIcon from '@mui/icons-material/Home';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import SearchIcon from '@mui/icons-material/Search';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import BusinessIcon from '@mui/icons-material/Business';
import TimerIcon from '@mui/icons-material/Timer';
import TroubleshootIcon from '@mui/icons-material/Troubleshoot';

import BrandLogo from '../Brand/BrandLogo';
import { useAuth } from '../../hooks/useAuth';
import { APP_NAME } from '../../constants/appMetadata';
import { APP_UPDATE_VIEW_PERMISSIONS, DASHBOARD_STATISTICS_PERMISSIONS, PERMISSIONS, WORK_ORDER_MANAGEMENT_PERMISSIONS } from '../../constants/permissions';
import { preloadRouteModule } from '../../router/routeModulePreload';
import { pageGutters, uiLayout } from '../../ui/layout';

const drawerWidth = 232;
const collapsedDrawerWidth = 64;

const adminNavGroups = [
    {
        label: '工作台',
        items: [
            { text: '仪表盘', icon: <SpeedIcon />, path: '/admin/dashboard', requiredAnyPermissions: DASHBOARD_STATISTICS_PERMISSIONS },
            { text: '运行诊断', icon: <TroubleshootIcon />, path: '/admin/observability', requiredPermission: PERMISSIONS.OBSERVABILITY_VIEW },
        ],
    },
    {
        label: '工单',
        items: [
            { text: '工单查询', icon: <SearchIcon />, path: '/admin/work-orders/search', requiredAnyPermissions: WORK_ORDER_MANAGEMENT_PERMISSIONS },
        ],
    },
    {
        label: '系统',
        items: [
            { text: '用户管理', icon: <PeopleAltIcon />, path: '/admin/user-management', requiredPermission: PERMISSIONS.USER_MANAGE_VIEW },
            { text: '组织与部门', icon: <AccountTreeIcon />, path: '/admin/settings/org-units', requiredAnyPermissions: [PERMISSIONS.ORG_MANAGE_VIEW, PERMISSIONS.DEPARTMENT_VIEW] },
            { text: '市公司租户', icon: <BusinessIcon />, path: '/admin/settings/tenants', requiredPermission: PERMISSIONS.TENANT_MANAGE_VIEW },
            { text: '配置中心', icon: <SettingsApplicationsIcon />, path: '/admin/settings/configs', requiredAnyPermissions: [PERMISSIONS.SYSTEM_CONFIG_VIEW, PERMISSIONS.NOTIFICATION_CONFIG_VIEW] },
            { text: '时限策略', icon: <TimerIcon />, path: '/admin/settings/sla-policies', requiredPermission: PERMISSIONS.SLA_POLICY_VIEW },
            { text: '岗位权限模板', icon: <ManageAccountsIcon />, path: '/admin/settings/authorization-profiles', requiredPermission: PERMISSIONS.AUTHORIZATION_PROFILE_VIEW },
            { text: '安装包发布', icon: <CloudUploadIcon />, path: '/admin/settings/app-releases', requiredAnyPermissions: APP_UPDATE_VIEW_PERMISSIONS },
        ],
    },
];

function AdminLayout() {
    const { currentUser, logout, hasPermission } = useAuth(); // 从 useAuth 获取 logout
    const navigate = useNavigate();
    const location = useLocation();
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('md'));
    const [desktopOpen, setDesktopOpen] = useState(true);
    const [mobileOpen, setMobileOpen] = useState(false);
    const open = isMobile ? mobileOpen : desktopOpen;
    const drawerWidthTransition = theme.transitions.create('width', {
        easing: theme.transitions.easing.easeInOut,
        duration: theme.transitions.duration.shorter,
    });
    const drawerContentTransition = theme.transitions.create(
        ['opacity', 'max-width', 'margin', 'padding', 'transform', 'height'],
        {
            easing: theme.transitions.easing.easeInOut,
            duration: theme.transitions.duration.shorter,
        },
    );

    useEffect(() => {
        if (!isMobile) setMobileOpen(false);
    }, [isMobile]);

    const handleDrawerToggle = () => {
        if (isMobile) setMobileOpen(value => !value);
        else setDesktopOpen(value => !value);
    };
    const handleDrawerClose = () => setMobileOpen(false);
    const handleDrawerNavClick = () => {
        if (isMobile) {
            setMobileOpen(false);
        }
    };
    const buildPreloadHandlers = (path) => ({
        onMouseEnter: () => preloadRouteModule(path),
        onFocus: () => preloadRouteModule(path),
        onTouchStart: () => preloadRouteModule(path),
    });

    // 管理员登出处理函数
    const handleAdminLogout = async () => {
        try {
            await logout(); // 调用 AuthProvider 中的 logout 方法
            navigate('/admin/login'); // 管理员登出后导航到管理员登录页
        } catch (error) {
            console.error("Admin logout failed:", error);
            alert("登出失败，请稍后再试。");
        }
    };

    const filteredAdminNavGroups = adminNavGroups.map(group => ({
        ...group,
        items: group.items.filter(item => {
        if (item.requiredAnyPermissions && item.requiredAnyPermissions.length > 0) {
            return item.requiredAnyPermissions.some(permission => hasPermission(permission));
        }
        return item.requiredPermission ? hasPermission(item.requiredPermission) : true;
        }),
    })).filter(group => group.items.length > 0);
    const filteredAdminNavItems = filteredAdminNavGroups.flatMap(group => group.items);
    const currentNavItem = filteredAdminNavItems.find(item =>
        location.pathname === item.path || (item.path !== '/admin/dashboard' && location.pathname.startsWith(item.path))
    );

    return (
        <Box sx={{ display: 'flex', minHeight: '100vh' }}>
            <CssBaseline />
            <AppBar
                position="fixed"
                sx={{
                    zIndex: (muiTheme) => muiTheme.zIndex.drawer + 1,
                    pt: 'env(safe-area-inset-top)',
                }}
            >
                <Toolbar variant="dense" sx={{ minHeight: 56, px: pageGutters }}>
                    <IconButton
                        color="inherit"
                        aria-label={open ? '收起导航菜单' : '展开导航菜单'}
                        onClick={handleDrawerToggle}
                        edge="start"
                        sx={{ mr: { xs: 1, md: 2 } }}
                    >
                        <MenuIcon />
                    </IconButton>
                    <BrandLogo size={32} sx={{ mr: { xs: 1, sm: 1.5 }, width: { xs: 24, sm: 32 }, height: { xs: 24, sm: 32 } }} />
                    <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                        <Typography
                            variant="h6"
                            noWrap
                            component="div"
                            sx={{ fontSize: { xs: '1rem', sm: '1.0625rem' }, fontWeight: 650 }}
                        >
                            {APP_NAME}
                        </Typography>
                        <Typography variant="caption" noWrap color="text.secondary" sx={{ display: { xs: 'none', sm: 'block' } }}>
                            {currentUser?.tenantName || '默认市公司'} · {currentNavItem?.text || '管理后台'}
                        </Typography>
                    </Box>
                    <Tooltip title="返回用户首页">
                        <IconButton color="inherit" component={RouterLink} to="/" sx={{ width: 44, height: 44 }}>
                            <HomeIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="个人信息">
                        <IconButton
                            color="inherit"
                            component={RouterLink}
                            to="/profile"
                            sx={{ width: 44, height: 44, ml: { xs: 0, sm: 0.5 } }}
                            {...buildPreloadHandlers('/profile')}
                        >
                            <Avatar sx={{ bgcolor: '#EEF3FF', color: 'primary.main', fontSize: 14, fontWeight: 600, width: { xs: 30, sm: 32 }, height: { xs: 30, sm: 32 } }}>
                                {currentUser?.name?.charAt(0)?.toUpperCase() || currentUser?.username?.charAt(0)?.toUpperCase() || 'A'}
                            </Avatar>
                        </IconButton>
                    </Tooltip>
                    <Typography
                        noWrap
                        sx={{
                            mx: 1.5,
                            maxWidth: { sm: 180, md: 260 },
                            display: { xs: 'none', sm: 'block' },
                        }}
                    >
                        {currentUser?.name || currentUser?.username}
                    </Typography>
                    <Tooltip title="退出登录">
                        <IconButton color="inherit" onClick={handleAdminLogout} sx={{ width: 44, height: 44 }}> {/* 调用 handleAdminLogout */}
                            <ExitToAppIcon />
                        </IconButton>
                    </Tooltip>
                </Toolbar>
            </AppBar>
            <Drawer
                variant={isMobile ? 'temporary' : 'permanent'}
                onClose={handleDrawerClose}
                ModalProps={{ keepMounted: true }}
                sx={{
                    width: { xs: 0, md: open ? drawerWidth : collapsedDrawerWidth },
                    flexShrink: 0,
                    transition: isMobile ? 'none' : drawerWidthTransition,
                    willChange: isMobile ? 'auto' : 'width',
                    '& .MuiDrawer-paper': {
                        width: { xs: 'min(82vw, 280px)', md: open ? drawerWidth : collapsedDrawerWidth },
                        boxSizing: 'border-box',
                        overflowX: 'hidden',
                        whiteSpace: 'nowrap',
                        backgroundColor: '#FAFAFB',
                        borderColor: 'divider',
                        transition: isMobile ? 'none' : drawerWidthTransition,
                        willChange: isMobile ? 'auto' : 'width',
                    },
                }}
                open={open}
            >
                <Toolbar
                    variant="dense"
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        position: 'relative',
                        minHeight: 56,
                        pt: 'env(safe-area-inset-top)',
                        boxSizing: 'content-box',
                        px: 0,
                    }}
                >
                    <Typography
                        variant="subtitle1"
                        noWrap
                        sx={{
                            position: 'absolute',
                            left: 16,
                            maxWidth: open ? 148 : 0,
                            opacity: open ? 1 : 0,
                            overflow: 'hidden',
                            transition: drawerContentTransition,
                        }}
                    >
                        导航菜单
                    </Typography>
                    <Tooltip title={open ? '收起导航菜单' : '展开导航菜单'} placement="right">
                        <IconButton
                            aria-label={open ? '收起导航菜单' : '展开导航菜单'}
                            onClick={handleDrawerToggle}
                            sx={{
                                width: 40,
                                height: 40,
                                position: 'absolute',
                                left: isMobile ? 'calc(100% - 52px)' : open ? drawerWidth - 52 : 12,
                                transform: open ? 'rotate(0deg)' : 'rotate(180deg)',
                                transition: theme.transitions.create(['left', 'transform'], {
                                    easing: theme.transitions.easing.easeInOut,
                                    duration: theme.transitions.duration.shorter,
                                }),
                            }}
                        >
                            <ChevronLeftIcon />
                        </IconButton>
                    </Tooltip>
                </Toolbar>
                <Divider />
                <List
                    component="nav"
                    sx={{
                        px: open ? 1.5 : 0.5,
                        py: 1.5,
                        transition: drawerContentTransition,
                    }}
                >
                    {filteredAdminNavGroups.map((group) => (
                        <React.Fragment key={group.label}>
                            <Typography
                                component="div"
                                variant="overline"
                                sx={{
                                    height: open ? 28 : 8,
                                    px: open ? 1.25 : 0,
                                    lineHeight: '28px',
                                    fontSize: 11,
                                    letterSpacing: '0.04em',
                                    color: 'text.secondary',
                                    opacity: open ? 1 : 0,
                                    overflow: 'hidden',
                                    transform: open ? 'translateX(0)' : 'translateX(-6px)',
                                    transition: drawerContentTransition,
                                }}
                            >
                                {group.label}
                            </Typography>
                            {group.items.map((item) => (
                                <Tooltip
                                    title={item.text}
                                    placement="right"
                                    disableHoverListener={open}
                                    key={item.text}
                                >
                                    <ListItemButton
                                        component={RouterLink}
                                        to={item.path}
                                        selected={location.pathname === item.path || (item.path !== '/admin/dashboard' && location.pathname.startsWith(item.path))}
                                        onClick={handleDrawerNavClick}
                                        aria-label={!open ? item.text : undefined}
                                        sx={{
                                            minHeight: { xs: 48, md: 40 },
                                            px: open ? 1.25 : 1.5,
                                            py: 0.5,
                                            mb: 0.5,
                                            borderRadius: '8px',
                                            justifyContent: 'flex-start',
                                            transition: drawerContentTransition,
                                        }}
                                        {...buildPreloadHandlers(item.path)}
                                    >
                                        <ListItemIcon
                                            sx={{
                                                minWidth: 0,
                                                width: 24,
                                                mr: open ? 1.5 : 0,
                                                flexShrink: 0,
                                                justifyContent: 'center',
                                                color: 'inherit',
                                                transition: drawerContentTransition,
                                                '& .MuiSvgIcon-root': { fontSize: 21 },
                                            }}
                                        >
                                            {item.icon}
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={item.text}
                                            slotProps={{
                                                primary: {
                                                    noWrap: true,
                                                    sx: { fontSize: 14 },
                                                },
                                            }}
                                            sx={{
                                                m: 0,
                                                maxWidth: open ? 154 : 0,
                                                opacity: open ? 1 : 0,
                                                overflow: 'hidden',
                                                transform: open ? 'translateX(0)' : 'translateX(-6px)',
                                                transition: drawerContentTransition,
                                            }}
                                        />
                                    </ListItemButton>
                                </Tooltip>
                            ))}
                        </React.Fragment>
                    ))}
                </List>
            </Drawer>
            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    bgcolor: 'background.default',
                    px: pageGutters,
                    py: `${uiLayout.pageGutter}px`,
                    mt: 'calc(56px + env(safe-area-inset-top))',
                    width: { xs: '100%', md: 0 },
                    flexBasis: { xs: 'auto', md: 0 },
                    minWidth: 0,
                }}
            >
                <Box sx={{ minWidth: 0 }}><Outlet /></Box>
            </Box>
        </Box>
    );
}

export default AdminLayout;

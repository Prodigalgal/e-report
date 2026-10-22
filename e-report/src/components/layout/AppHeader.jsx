// src/components/layout/AppHeader.jsx
import React from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import { AppBar, Toolbar, Typography, Button, Container, Box, IconButton, Menu, MenuItem, Divider, Tooltip, Avatar } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import HomeIcon from '@mui/icons-material/Home';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import LogoutIcon from '@mui/icons-material/Logout';
import AssignmentIcon from '@mui/icons-material/Assignment';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import ManageSearchIcon from '@mui/icons-material/ManageSearch';

import BrandLogo from '../Brand/BrandLogo';
import { useAuth } from '../../hooks/useAuth';
import { APP_NAME } from '../../constants/appMetadata';
import { WORK_ORDER_TASKS, WORK_ORDER_TYPES } from '../../constants/workOrderCatalog';
import { preloadRouteModule } from '../../router/routeModulePreload';
import { isAdminUser } from '../../utils/adminAccess';
import { pageGutters } from '../../ui/layout';

const PUBLIC_NAV_ITEMS = [
    { label: '用户首页', path: '/', IconComponent: HomeIcon, display: true },
];

function AppHeader() {
    const { isAuthenticated, currentUser, logout, hasPermission } = useAuth(); // 从 useAuth 获取 logout
    const navigate = useNavigate();
    const [anchorElNav, setAnchorElNav] = React.useState(null);
    const authenticated = isAuthenticated();

    const handleOpenNavMenu = (event) => setAnchorElNav(event.currentTarget);
    const handleCloseNavMenu = () => setAnchorElNav(null);

    const handleNavMenuClick = (path) => {
        navigate(path);
        handleCloseNavMenu();
    };

    const buildPreloadHandlers = React.useCallback((path) => ({
        onMouseEnter: () => preloadRouteModule(path),
        onFocus: () => preloadRouteModule(path),
        onTouchStart: () => preloadRouteModule(path),
    }), []);

    // 登出处理函数
    const handleLogout = async () => {
        handleCloseNavMenu(); // 窄屏菜单也需要在退出前关闭。
        try {
            await logout(); // 调用 AuthProvider 中的 logout 方法
            navigate('/login'); // 登出后导航到普通用户登录页
        } catch (error) {
            console.error("Logout failed:", error);
            // 可以在这里显示一个错误提示给用户，例如使用 Snackbar
            alert("登出失败，请稍后再试。");
        }
    };

    const authenticatedUserNavItems = React.useMemo(() => {
        if (!authenticated) {
            return [];
        }

        const nextNavItems = [];
        const canReport = WORK_ORDER_TYPES.some(item => hasPermission(item.permissions.report));
        if (canReport) {
            nextNavItems.push({ label: WORK_ORDER_TASKS.REPORT.label, path: WORK_ORDER_TASKS.REPORT.path, IconComponent: AssignmentIcon, display: true });
        }
        nextNavItems.push({ label: WORK_ORDER_TASKS.SEARCH.label, path: WORK_ORDER_TASKS.SEARCH.path, IconComponent: ManageSearchIcon, display: true });
        if (isAdminUser(currentUser)) {
            nextNavItems.push({ label: WORK_ORDER_TASKS.ADMIN.label, path: WORK_ORDER_TASKS.ADMIN.path, IconComponent: AdminPanelSettingsIcon, display: true });
        }
        return nextNavItems;
    }, [authenticated, currentUser, hasPermission]);

    const navItemsToDisplay = React.useMemo(() => [
        ...PUBLIC_NAV_ITEMS.filter(item => item.display),
        ...authenticatedUserNavItems.filter(item => item.display)
    ], [authenticatedUserNavItems]);

    const mobileMenuItems = [];
    navItemsToDisplay.forEach(item => {
        mobileMenuItems.push(
            <MenuItem
                key={`${item.label}-mobile-main`}
                onClick={() => handleNavMenuClick(item.path)}
                {...buildPreloadHandlers(item.path)}
            >
                {item.IconComponent && <item.IconComponent sx={{ mr: 1.5 }} />}
                <Typography sx={{ textAlign: 'center' }}>{item.label}</Typography>
            </MenuItem>
        );
    });

    mobileMenuItems.push(<Divider key="divider-auth-mobile" sx={{ my: 1 }} />);

    if (authenticated) {
        mobileMenuItems.push(
            <MenuItem key="profile-mobile" onClick={() => handleNavMenuClick('/profile')} {...buildPreloadHandlers('/profile')}>
                <AccountCircleIcon sx={{ mr: 1.5 }} />
                <Typography sx={{ textAlign: 'center' }}>个人信息</Typography>
            </MenuItem>
        );
        mobileMenuItems.push(
            <MenuItem key="welcome-mobile" disabled sx={{ opacity: '1 !important' }}>
                <AccountCircleIcon sx={{mr:1.5}} />
                <Typography sx={{ textAlign: 'center', fontWeight: 'bold' }}>{currentUser?.name || currentUser?.username}</Typography>
            </MenuItem>
        );
        mobileMenuItems.push(
            <MenuItem key="logout-mobile" onClick={handleLogout}>
                <LogoutIcon sx={{ mr: 1.5 }} />
                <Typography sx={{ textAlign: 'center' }}>退出登录</Typography>
            </MenuItem>
        );
    } else {
        mobileMenuItems.push(
            <MenuItem key="login-user-mobile" onClick={() => handleNavMenuClick('/login')} {...buildPreloadHandlers('/login')}>
                <VpnKeyIcon sx={{ mr: 1.5 }} />
                <Typography sx={{ textAlign: 'center' }}>用户登录</Typography>
            </MenuItem>
        );
        mobileMenuItems.push(
            <MenuItem key="login-admin-mobile" onClick={() => handleNavMenuClick('/admin/login')} {...buildPreloadHandlers('/admin/login')}>
                <AdminPanelSettingsIcon sx={{ mr: 1.5 }} />
                <Typography sx={{ textAlign: 'center' }}>管理员登录</Typography>
            </MenuItem>
        );
    }

    return (
        <AppBar position="sticky" sx={{ pt: 'env(safe-area-inset-top)' }}>
            <Container maxWidth={false} disableGutters sx={{ px: pageGutters }}>
                <Toolbar disableGutters sx={{ minHeight: { xs: 56, sm: 64 } }}>
                    {/* Logo */}
                    <BrandLogo sx={{ mr: 1.5, display: { xs: 'none', md: 'flex' } }} />
                    <Typography
                        variant="h6" noWrap component={RouterLink} to="/"
                        sx={{ mr: 2, display: { xs: 'none', md: 'flex' }, fontWeight: 700, letterSpacing: 0, color: 'inherit', textDecoration: 'none' }}
                    >
                        {APP_NAME}
                    </Typography>

                    <Box sx={{ flex: '0 0 auto', display: { xs: 'flex', md: 'none' } }}>
                        <IconButton size="large" aria-label="导航菜单" aria-controls="menu-appbar" aria-haspopup="true" onClick={handleOpenNavMenu} color="inherit" sx={{ width: 44, height: 44 }}>
                            <MenuIcon />
                        </IconButton>
                        <Menu
                            id="menu-appbar" anchorEl={anchorElNav}
                            anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
                            keepMounted transformOrigin={{ vertical: 'top', horizontal: 'left' }}
                            open={Boolean(anchorElNav)} onClose={handleCloseNavMenu}
                            sx={{ display: { xs: 'block', md: 'none' } }}
                            slotProps={{
                                paper: {
                                    sx: {
                                        mt: 0.75,
                                        width: 'calc(100vw - 24px)',
                                        maxWidth: 360,
                                        '& .MuiMenuItem-root': {
                                            minHeight: 48,
                                        },
                                    },
                                },
                            }}
                        >
                            {mobileMenuItems}
                        </Menu>
                    </Box>

                    <BrandLogo sx={{ mr: 1, display: { xs: 'flex', md: 'none' } }} />
                    <Typography
                        variant="h6" noWrap component={RouterLink} to="/"
                        sx={{ mr: 1, display: { xs: 'flex', md: 'none' }, flexGrow: 1, fontSize: '1rem', fontWeight: 700, letterSpacing: 0, color: 'inherit', textDecoration: 'none'}}
                    >
                        {APP_NAME}
                    </Typography>

                    {/* Desktop Menu Items */}
                    <Box sx={{ flexGrow: 1, display: { xs: 'none', md: 'flex' } }}>
                        {navItemsToDisplay.map((item) => (
                            <Button
                                key={`${item.label}-desktop`} component={RouterLink} to={item.path}
                                sx={{ my: 1, color: 'text.secondary', display: 'flex', mx: 0.25, px: 1.25, whiteSpace: 'nowrap' }}
                                startIcon={item.IconComponent && <item.IconComponent />}
                                {...buildPreloadHandlers(item.path)}
                            >
                                {item.label}
                            </Button>
                        ))}
                    </Box>

                    {/* Desktop Auth Section */}
                    <Box sx={{ flexGrow: 0, display: { xs: 'none', md: 'flex' }, alignItems: 'center' }}>
                        {authenticated ? (
                            <>
                                <Tooltip title="个人信息">
                                    <IconButton
                                        component={RouterLink}
                                        to="/profile"
                                        sx={{ color: 'text.secondary' }}
                                        {...buildPreloadHandlers('/profile')}
                                    >
                                        <AccountCircleIcon />
                                    </IconButton>
                                </Tooltip>
                                <Typography sx={{ color: 'text.secondary', mx: 1, display: 'flex', alignItems: 'center' }}>
                                    {currentUser?.name || currentUser?.username}
                                </Typography>
                                <Tooltip title="退出登录">
                                    <IconButton onClick={handleLogout} sx={{ color: 'text.secondary', ml: 0.5 }}> {/* 桌面端登出按钮 */}
                                        <LogoutIcon />
                                    </IconButton>
                                </Tooltip>
                            </>
                        ) : (
                            <>
                                <Button component={RouterLink} to="/login" sx={{ my: 2, display: 'block', mx: 0.5 }} startIcon={<VpnKeyIcon />} {...buildPreloadHandlers('/login')}>
                                    用户登录
                                </Button>
                                <Button component={RouterLink} to="/admin/login" variant="outlined" sx={{ my: 2, display: 'block' }} startIcon={<AdminPanelSettingsIcon />} {...buildPreloadHandlers('/admin/login')}>
                                    管理员登录
                                </Button>
                            </>
                        )}
                    </Box>
                </Toolbar>
            </Container>
        </AppBar>
    );
}
export default AppHeader;

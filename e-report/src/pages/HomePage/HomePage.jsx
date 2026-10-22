import PageTitle from '../../components/layout/PageTitle';
import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Box, Button, Container, Paper, Typography } from '@mui/material';
import AssignmentIcon from '@mui/icons-material/Assignment';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import ManageSearchIcon from '@mui/icons-material/ManageSearch';

import { APP_NAME } from '../../constants/appMetadata';
import { WORK_ORDER_TASKS, WORK_ORDER_TYPES } from '../../constants/workOrderCatalog';
import { useAuth } from '../../hooks/useAuth';
import { isAdminUser } from '../../utils/adminAccess';

function HomePage() {
    const { currentUser, isAuthenticated, loadingAuth, hasPermission } = useAuth();
    const authenticated = isAuthenticated();
    const reportableTypes = React.useMemo(
        () => WORK_ORDER_TYPES.filter(item => hasPermission(item.permissions.report)),
        [hasPermission],
    );
    const canOpenAdmin = authenticated && isAdminUser(currentUser);

    if (loadingAuth) {
        return null;
    }

    const tasks = [
        reportableTypes.length > 0 && {
            key: WORK_ORDER_TASKS.REPORT.key,
            label: WORK_ORDER_TASKS.REPORT.label,
            description: `${WORK_ORDER_TASKS.REPORT.description} 当前可用 ${reportableTypes.length} 类。`,
            icon: AssignmentIcon,
            path: WORK_ORDER_TASKS.REPORT.path,
            color: 'primary',
        },
        authenticated && {
            key: WORK_ORDER_TASKS.SEARCH.key,
            label: WORK_ORDER_TASKS.SEARCH.label,
            description: WORK_ORDER_TASKS.SEARCH.description,
            icon: ManageSearchIcon,
            path: WORK_ORDER_TASKS.SEARCH.path,
            color: 'primary',
        },
        canOpenAdmin && {
            key: WORK_ORDER_TASKS.ADMIN.key,
            label: WORK_ORDER_TASKS.ADMIN.label,
            description: WORK_ORDER_TASKS.ADMIN.description,
            icon: AdminPanelSettingsIcon,
            path: WORK_ORDER_TASKS.ADMIN.path,
            color: 'primary',
        },
    ].filter(Boolean);

    return (
        <Container maxWidth="md" disableGutters>
            <Paper elevation={2} sx={{ p: { xs: 2, sm: 3.5 }, borderRadius: 1 }}>
                <PageTitle>
                    {authenticated ? '工作台' : `欢迎使用${APP_NAME}`}
                </PageTitle>
                <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                    {authenticated ? '按任务进入，系统会根据当前权限自动隐藏无权限入口。' : '请选择您的操作。'}
                </Typography>

                <Box sx={{ display: 'grid', gap: 1.5, mt: 3 }}>
                    {tasks.map(({ key, label, description, icon: TaskIcon, path, color }) => (
                        <Button
                            key={key}
                            component={RouterLink}
                            to={path}
                            color={color}
                            variant={key === WORK_ORDER_TASKS.REPORT.key ? 'contained' : 'outlined'}
                            startIcon={React.createElement(TaskIcon)}
                            fullWidth
                            sx={{
                                minHeight: 78,
                                justifyContent: 'flex-start',
                                alignItems: 'flex-start',
                                textAlign: 'left',
                                px: { xs: 1.5, sm: 2 },
                                py: 1.5,
                                '& .MuiButton-startIcon': { mt: 0.2 },
                            }}
                        >
                            <Box sx={{ minWidth: 0 }}>
                                <Typography component="span" sx={{ display: 'block', fontWeight: 800 }}>
                                    {label}
                                </Typography>
                                <Typography component="span" variant="body2" sx={{ display: 'block', mt: 0.25, opacity: 0.82 }}>
                                    {description}
                                </Typography>
                            </Box>
                        </Button>
                    ))}
                </Box>

                {authenticated && tasks.length === 0 && (
                    <Typography color="text.secondary" sx={{ mt: 3 }}>
                        当前账号尚未分配可用工作权限，请联系管理员。
                    </Typography>
                )}
            </Paper>
        </Container>
    );
}

export default HomePage;

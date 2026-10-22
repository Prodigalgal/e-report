import PageHeading from '../../components/layout/PageHeading';
import ListResultsBar, { ListPagination } from '../../components/layout/ListResultsBar';
import ViewportTableContainer from '../../components/layout/ViewportTableContainer';
import SearchableSelectField from '../../components/SearchInput/SearchableSelectField';
import { PageTabs, PageTabPanel } from '../../components/layout/PageTabs';
// src/pages/AdminSystemConfigPage/AdminSystemConfigPage.jsx
import React, { useState, useEffect, useCallback } from 'react';
import {
    Container, Paper, Typography, Box, Button, TextField, Table, TableBody,
    TableCell, TableHead, TableRow, IconButton, Tooltip,
    CircularProgress, Alert, Dialog, DialogActions,
    DialogContent, DialogTitle, Snackbar, FormHelperText, FormControl,
    InputLabel, Select, MenuItem, Chip, FormControlLabel, Switch
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';

import apiClient from '../../services/apiClient';
import { formatDate } from '../../utils/formatDate'; // 你可能需要它
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import SearchInput from '../../components/SearchInput/SearchInput';
import NotificationCenterSettings from '../../components/Admin/NotificationCenterSettings';
import PasswordPolicySettings from '../../components/Admin/PasswordPolicySettings.jsx';
import { useAuth } from '../../hooks/useAuth';
import { PERMISSIONS } from '../../constants/permissions';

const TENANT_SCOPE = 'tenant';
const IMAGE_REDACTION_SUBMISSION_CONFIG_KEY = 'media.image-redaction.submission-enabled';

const toOrgScope = (orgUnitId) => `org:${orgUnitId}`;

const selectedOrgUnitId = (scope) => {
    if (!scope || scope === TENANT_SCOPE) return undefined;
    const orgUnitId = scope.replace('org:', '').trim();
    return orgUnitId || undefined;
};

const sourceLabel = (config) => {
    if (config.sourceScope === 'ORGANIZATION_OVERRIDE') {
        return config.sourceOrgUnitName ? `${config.sourceOrgUnitName}配置` : '组织配置';
    }
    if (config.sourceScope === 'TENANT_OVERRIDE') return '全市默认';
    return '平台默认';
};

function ConfigValue({ config }) {
    if (config.configKey === IMAGE_REDACTION_SUBMISSION_CONFIG_KEY) {
        const enabled = ['true', '1', 'yes'].includes(String(config.configValue || '').toLowerCase());
        return (
            <Chip
                size="small"
                label={enabled ? '允许提交新任务' : '已停止提交新任务'}
                color={enabled ? 'success' : 'warning'}
                variant="outlined"
            />
        );
    }
    if (!config.sensitive) {
        return <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>{config.configValue || '空值'}</Typography>;
    }
    return (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Chip size="small" label="敏感配置" color="warning" variant="outlined" />
            <Typography variant="body2" color="text.secondary">
                {config.configured ? '已配置（内容不可回显）' : '未配置'}
            </Typography>
        </Box>
    );
}

function ConfigSourceChip({ config }) {
    return (
        <Chip
            size="small"
            label={sourceLabel(config)}
            color={config.inherited ? 'default' : 'primary'}
            variant={config.inherited ? 'outlined' : 'filled'}
        />
    );
}

function ConfigActions({ config, canUpdate, canDelete, disabled, onEdit, onDelete }) {
    return (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
            {canUpdate && (
                <Tooltip title={config.inherited ? '创建当前作用域覆盖' : '编辑'}>
                    <IconButton size="small" onClick={onEdit} disabled={disabled}>
                        <EditIcon fontSize="inherit" />
                    </IconButton>
                </Tooltip>
            )}
            {canDelete && (
                <Tooltip title={config.inherited ? '继承配置不可删除' : '删除当前作用域覆盖'}>
                    <span>
                        <IconButton size="small" color="error" onClick={onDelete} disabled={disabled || config.inherited}>
                            <DeleteIcon fontSize="inherit" />
                        </IconButton>
                    </span>
                </Tooltip>
            )}
        </Box>
    );
}

// --- Config Form Dialog ---
function ConfigFormDialog({ open, onClose, onSubmit, initialData, isEditMode, serverErrors, isLoading }) {
    const [formData, setFormData] = useState({ configKey: '', configValue: '', description: '' });
    const [errors, setErrors] = useState({});

    useEffect(() => {
        if (open) {
            setFormData(initialData || { configKey: '', configValue: '', description: '' });
            setErrors({});
        }
    }, [initialData, open]);

    useEffect(() => {
        if (serverErrors) {
            const newErrors = {};
            if (serverErrors.errors && typeof serverErrors.errors === 'object') {
                for (const field in serverErrors.errors) { newErrors[field] = serverErrors.errors[field]; }
            } else if (serverErrors.message) { newErrors.general = serverErrors.message; }
            else if (typeof serverErrors === 'string') { newErrors.general = serverErrors; }
            setErrors(newErrors);
        } else {
            setErrors({});
        }
    }, [serverErrors]);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        if (errors[name]) setErrors(prev => ({ ...prev, [name]: null }));
        if (errors.general) setErrors(prev => ({ ...prev, general: null}));
    };

    const validate = () => {
        const newErrors = {};
        if (!formData.configKey.trim() && !isEditMode) newErrors.configKey = "配置键不能为空";
        // configValue 可以为空
        setErrors(prev => ({...prev, ...newErrors}));
        return Object.keys(newErrors).filter(k => newErrors[k] !== null).length === 0;
    };

    const handleSubmitDialog = (e) => {
        e.preventDefault();
        setErrors(prev => ({ general: null, configKey: prev.configKey, configValue: prev.configValue, description: prev.description }));
        if (validate()) {
            onSubmit(formData); // 提交整个 formData，后端根据是创建还是更新来处理
        }
    };

    return (
        <Dialog open={open} onClose={isLoading ? () => {} : onClose} maxWidth="sm" fullWidth>
            <DialogTitle>{isEditMode ? `编辑配置: ${initialData?.configKey}` : '添加新配置'}</DialogTitle>
            <DialogContent>
                {errors.general && <Alert severity="error" sx={{ mb: 2 }}>{errors.general}</Alert>}
                <TextField
                    autoFocus
                    margin="dense"
                    name="configKey"
                    label="配置键"
                    type="text"
                    fullWidth
                    variant="outlined"
                    value={formData.configKey}
                    onChange={handleChange}
                    error={!!errors.configKey}
                    helperText={errors.configKey}
                    required
                    disabled={isEditMode || isLoading} // 编辑时键不可改
                />
                {formData.configKey === IMAGE_REDACTION_SUBMISSION_CONFIG_KEY ? (
                    <Box sx={{ mt: 1.5, mb: 1 }}>
                        <FormControlLabel
                            control={(
                                <Switch
                                    checked={['true', '1', 'yes'].includes(String(formData.configValue || '').toLowerCase())}
                                    onChange={(event) => {
                                        setFormData(prev => ({
                                            ...prev,
                                            configValue: event.target.checked ? 'true' : 'false',
                                        }));
                                        if (errors.configValue) {
                                            setErrors(prev => ({ ...prev, configValue: null }));
                                        }
                                    }}
                                    disabled={isLoading}
                                />
                            )}
                            label="允许主体服务提交新的图片脱敏任务"
                        />
                        <FormHelperText>
                            关闭后，已提交给图像服务的任务继续处理；新图片先记录为待提交，不会暴露原图。
                        </FormHelperText>
                    </Box>
                ) : (
                    <TextField
                        margin="dense"
                        name="configValue"
                        label={isEditMode && initialData?.sensitive ? '新配置值' : '配置值'}
                        type="text"
                        fullWidth
                        variant="outlined"
                        value={formData.configValue}
                        onChange={handleChange}
                        multiline
                        rows={3}
                        error={!!errors.configValue}
                        helperText={errors.configValue || (isEditMode && initialData?.sensitive
                            ? '敏感值不会从服务端回显；留空将保持现有值，输入内容将替换现有值。'
                            : '')}
                        disabled={isLoading}
                    />
                )}
                <TextField
                    margin="dense"
                    name="description"
                    label="描述"
                    type="text"
                    fullWidth
                    variant="outlined"
                    value={formData.description}
                    onChange={handleChange}
                    multiline
                    rows={2}
                    error={!!errors.description}
                    helperText={errors.description}
                    disabled={isLoading}
                />
            </DialogContent>
            <DialogActions sx={{position: 'relative'}}>
                <Button onClick={onClose} disabled={isLoading}>取消</Button>
                <Button onClick={handleSubmitDialog} variant="contained" disabled={isLoading}>
                    {isLoading && <CircularProgress size={20} color="inherit" sx={{ mr: 1 }} />}
                    {isEditMode ? '保存更改' : '创建配置'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}


// --- 主页面组件 AdminSystemConfigPage ---
function AdminSystemConfigPage() {
    const { currentUser, hasPermission } = useAuth();
    const tenantScopeLabel = `${currentUser?.tenantName || '当前市公司'}默认配置`;
    const canViewSystemConfig = hasPermission(PERMISSIONS.SYSTEM_CONFIG_VIEW);
    const canViewNotificationConfig = hasPermission(PERMISSIONS.NOTIFICATION_CONFIG_VIEW);
    const canCreate = hasPermission(PERMISSIONS.SYSTEM_CONFIG_CREATE);
    const canUpdate = hasPermission(PERMISSIONS.SYSTEM_CONFIG_UPDATE);
    const canDelete = hasPermission(PERMISSIONS.SYSTEM_CONFIG_DELETE);
    const canUpdateNotificationChannels = hasPermission(PERMISSIONS.NOTIFICATION_CHANNEL_UPDATE);
    const canUpdateNotificationSchedule = hasPermission(PERMISSIONS.NOTIFICATION_SCHEDULE_UPDATE);
    const canUpdateNotificationSender = hasPermission(PERMISSIONS.NOTIFICATION_SENDER_UPDATE);
    const [configs, setConfigs] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [pageError, setPageError] = useState(null);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [searchTerm, setSearchTerm] = useState('');
    const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('');
    const [configScopes, setConfigScopes] = useState({ tenantWide: false, organizations: [] });
    const [selectedScope, setSelectedScope] = useState(null);
    const [isLoadingScopes, setIsLoadingScopes] = useState(true);
    const [scopeError, setScopeError] = useState(null);
    const [activeTab, setActiveTab] = useState(() => canViewSystemConfig ? 0 : 1);
    const notificationScope = activeTab === 1 || activeTab === 3;

    const [openConfigDialog, setOpenConfigDialog] = useState(false);
    const [editingConfig, setEditingConfig] = useState(null);
    const [configDialogKey, setConfigDialogKey] = useState(0);
    const [configFormServerErrors, setConfigFormServerErrors] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' });

    useEffect(() => {
        let active = true;
        const fetchScopes = async () => {
            setIsLoadingScopes(true);
            setScopeError(null);
            try {
                const scopesEndpoint = !notificationScope
                    ? '/admin/configs/scopes'
                    : '/admin/notifications/settings/scopes';
                const response = await apiClient.get(scopesEndpoint);
                if (!active) return;
                const organizations = Array.isArray(response.data?.organizations) ? response.data.organizations : [];
                const tenantWide = response.data?.tenantWide === true;
                setConfigScopes({ tenantWide, organizations });
                const currentOrg = organizations.find((org) => org.id === currentUser?.orgUnitId);
                const initialScope = tenantWide
                    ? TENANT_SCOPE
                    : currentOrg
                        ? toOrgScope(currentOrg.id)
                        : organizations[0]
                            ? toOrgScope(organizations[0].id)
                            : null;
                setSelectedScope(initialScope);
                if (!initialScope) {
                    setScopeError('当前账号没有可访问的配置作用域。');
                }
            } catch (err) {
                if (!active) return;
                setScopeError(formatApiErrorMessage(err, '加载配置作用域失败，请稍后重试。'));
            } finally {
                if (active) setIsLoadingScopes(false);
            }
        };
        fetchScopes();
        return () => { active = false; };
    }, [notificationScope, currentUser?.orgUnitId]);

    useEffect(() => {
        const handler = setTimeout(() => {
            setPage(0);
            setDebouncedSearchTerm(searchTerm.trim());
        }, 400);
        return () => clearTimeout(handler);
    }, [searchTerm]);

    const fetchConfigs = useCallback(async (currentPage, currentSearchTerm) => {
        if (selectedScope === null || activeTab !== 0) return;
        setIsLoading(true);
        setPageError(null);
        try {
            const params = {
                page: currentPage,
                size: 10,
                sort: 'configKey,asc',
                searchTerm: currentSearchTerm || undefined,
                orgUnitId: selectedOrgUnitId(selectedScope),
                includeNotification: false,
            };
            const response = await apiClient.get('/admin/configs', { params });
            if (response.data && response.data.content) {
                setConfigs(response.data.content);
                setTotalPages(response.data.totalPages);
                setTotalElements(response.data.totalElements);
                setPage(response.data.number);
            } else {
                setConfigs([]); setTotalPages(0); setTotalElements(0);
            }
        } catch (err) {
            console.error("Failed to fetch configs:", err);
            setPageError(formatApiErrorMessage(err, "加载配置列表失败，请稍后重试。"));
        } finally {
            setIsLoading(false);
        }
    }, [activeTab, selectedScope]);

    useEffect(() => {
        if (selectedScope !== null && activeTab === 0) fetchConfigs(page, debouncedSearchTerm);
    }, [activeTab, fetchConfigs, page, debouncedSearchTerm, selectedScope]);

    const handleScopeChange = (scope) => {
        setSelectedScope(scope);
        setPage(0);
        setOpenConfigDialog(false);
        setEditingConfig(null);
    };

    const handlePageChange = (event, value) => { setPage(value - 1); };

    const handleTabChange = (nextTab) => {
        if (notificationScope !== (nextTab === 1 || nextTab === 3)) setSelectedScope(null);
        setActiveTab(nextTab);
        setScopeError(null);
        setOpenConfigDialog(false);
        setEditingConfig(null);
    };

    const handleOpenAddConfigDialog = () => {
        setEditingConfig(null); setConfigFormServerErrors(null);
        setConfigDialogKey(prev => prev + 1); setOpenConfigDialog(true);
    };

    const handleOpenEditConfigDialog = (config) => {
        setEditingConfig(config); setConfigFormServerErrors(null);
        setConfigDialogKey(prev => prev + 1); setOpenConfigDialog(true);
    };

    const handleCloseConfigDialog = () => {
        if(isSubmitting) return;
        setOpenConfigDialog(false); setEditingConfig(null); setConfigFormServerErrors(null);
    };

    const handleConfigFormSubmit = async (formData) => {
        setIsSubmitting(true); setConfigFormServerErrors(null);
        try {
            const requestConfig = { params: { orgUnitId: selectedOrgUnitId(selectedScope) } };
            if (editingConfig) {
                // 后端 PUT /admin/configs/{configKey} 只需要 value 和 description
                const retainSensitiveValue = editingConfig.sensitive && formData.configValue === '';
                const updatePayload = {
                    configValue: retainSensitiveValue ? null : formData.configValue,
                    description: formData.description,
                    retainSensitiveValue,
                };
                await apiClient.put(`/admin/configs/${editingConfig.configKey}`, updatePayload, requestConfig);
            } else {
                // 后端 POST /admin/configs 需要 key, value, description
                await apiClient.post('/admin/configs', formData, requestConfig);
            }
            handleCloseConfigDialog();
            fetchConfigs(editingConfig ? page : 0, debouncedSearchTerm);
            setSnackbar({ open: true, message: `配置项 ${formData.configKey} ${editingConfig ? '更新' : '创建'}成功！`, severity: 'success' });
        } catch (err) {
            console.error("Failed to save config:", err.response?.data || err);
            const responseData = err.response?.data;
            const errorData = responseData && typeof responseData === 'object' ? { ...responseData } : {};
            if (!errorData.message) {
                errorData.message = formatApiErrorMessage(err, "保存配置失败，请稍后重试。");
            }
            setConfigFormServerErrors(errorData);
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleDeleteConfig = async (config) => {
        if (config.inherited) {
            setSnackbar({ open: true, message: '继承配置没有本地覆盖项，不能删除。', severity: 'warning' });
            return;
        }
        const configKey = config.configKey;
        if (!window.confirm(`您确定要删除配置项 "${configKey}" 吗？`)) return;
        setIsSubmitting(true); // 可以用一个全局的 isOperating state
        try {
            await apiClient.delete(`/admin/configs/${configKey}`, {
                params: { orgUnitId: selectedOrgUnitId(selectedScope) },
            });
            setSnackbar({ open: true, message: `配置项 ${configKey} 已删除。`, severity: 'success' });
            fetchConfigs(page, debouncedSearchTerm); // 重新加载当前页，或者如果删除了最后一项导致页数减少则需要调整
        } catch (err) {
            console.error("Failed to delete config:", err);
            setSnackbar({ open: true, message: `删除配置失败：${formatApiErrorMessage(err, "删除配置失败，请稍后重试。")}`, severity: 'error' });
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleSnackbarClose = (event, reason) => {
        if (reason === 'clickaway') return;
        setSnackbar(prev => ({ ...prev, open: false }));
    };

    const handleNotificationConfigSaved = (message) => {
        setSnackbar({ open: true, message, severity: 'success' });
        fetchConfigs(page, debouncedSearchTerm);
    };

    return (
        <Container maxWidth={false} disableGutters sx={{ mt: 0, mb: 2 }}>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                    <PageHeading title="配置中心" description="按组织管理系统参数、安全策略与邮件服务。" />
                    {canCreate && activeTab === 0 && (
                        <Button variant="contained" startIcon={<AddIcon />} onClick={handleOpenAddConfigDialog} disabled={isSubmitting || isLoading || selectedScope === null}>
                            添加配置
                        </Button>
                    )}
                </Box>

            <Paper variant="outlined" sx={{ p: 1.5, borderRadius: '14px' }}>
                <PageTabs id="configuration" label="配置分类" value={activeTab} onChange={handleTabChange}
                    options={[
                        ...(canViewSystemConfig ? [{ value: 0, label: '系统参数' }, { value: 2, label: '密码策略' }] : []),
                        ...(canViewNotificationConfig ? [{ value: 1, label: '通知与汇总' }, { value: 3, label: '邮件发件池' }] : []),
                    ]} />

                <Box sx={{ display: 'grid', gridTemplateColumns: activeTab === 0 ? { xs: '1fr', md: 'minmax(220px, 320px) 1fr' } : 'minmax(0, 320px)', gap: 1.5, mb: 1.5 }}>
                    <SearchableSelectField label="配置作用域" value={selectedScope || ''} onChange={handleScopeChange}
                        disabled={isLoadingScopes || isSubmitting || selectedScope === null}
                        options={[
                            ...(configScopes.tenantWide ? [{ value: TENANT_SCOPE, label: tenantScopeLabel }] : []),
                            ...configScopes.organizations.map(org => ({ value: toOrgScope(org.id), label: org.name })),
                        ]} />
                    {activeTab === 0 && <SearchInput
                        label="搜索配置"
                        variant="outlined"
                        size="small"
                        value={searchTerm}
                        onChange={(event) => setSearchTerm(event.target.value)}
                        onClear={() => setSearchTerm('')}
                        fullWidth
                        autoComplete="off"
                        placeholder="配置键、非敏感值、说明"
                        disabled={isLoading || isSubmitting}
                    />}
                </Box>

                <PageTabPanel id="configuration" value={activeTab} selected={activeTab}>
                    {notificationScope && selectedScope !== null && (
                        <NotificationCenterSettings
                            view={activeTab === 3 ? 'sender' : 'channels'}
                            key={selectedScope}
                            orgUnitId={selectedOrgUnitId(selectedScope)}
                            scopeLabel={selectedScope === TENANT_SCOPE
                                ? tenantScopeLabel
                                : configScopes.organizations.find((org) => toOrgScope(org.id) === selectedScope)?.name || '区县配置'}
                            tenantName={currentUser?.tenantName || '当前市公司'}
                            canUpdateChannels={canUpdateNotificationChannels}
                            canUpdateSchedule={canUpdateNotificationSchedule}
                            canUpdateSender={canUpdateNotificationSender}
                            disabled={isSubmitting}
                            onSaved={handleNotificationConfigSaved}
                        />
                    )}

                    {!notificationScope && selectedScope !== null && (
                        <Box sx={{ display: activeTab === 2 ? 'block' : 'none' }}>
                        <PasswordPolicySettings
                            key={`password-policy-${selectedScope}`}
                            orgUnitId={selectedOrgUnitId(selectedScope)}
                            scopeLabel={selectedScope === TENANT_SCOPE
                                ? tenantScopeLabel
                                : configScopes.organizations.find((org) => toOrgScope(org.id) === selectedScope)?.name || '当前组织'}
                            canUpdate={canUpdate}
                            canDelete={canDelete}
                            disabled={isSubmitting}
                            onSaved={handleNotificationConfigSaved}
                        />
                        </Box>
                    )}

                    {scopeError && <Alert severity="error" sx={{ mb: 2 }}>{scopeError}</Alert>}
                    {(activeTab === 0 && isLoading && configs.length === 0) && <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}><CircularProgress /><Typography sx={{ ml: 2 }}>加载中...</Typography></Box>}
                    {activeTab === 0 && pageError && <Alert severity="error" sx={{ mb: 2 }}>{pageError}</Alert>}
                    {activeTab === 0 && !isLoading && !pageError && configs.length === 0 && <Typography sx={{ textAlign: 'center', p: 3 }}>未找到配置项。</Typography>}

                    {activeTab === 0 && <ListResultsBar summary={`共 ${totalElements} 项配置`}>
                        {totalPages > 1 && <ListPagination count={totalPages} page={page + 1} onChange={handlePageChange} disabled={isLoading} />}
                    </ListResultsBar>}
                    {activeTab === 0 && configs.length > 0 && (
                        <ViewportTableContainer sx={{ display: { xs: 'none', md: 'block' } }}>
                            <Table size="small" stickyHeader sx={{ minWidth: 1040 }}>
                                <TableHead><TableRow sx={{ '& th': { fontWeight: 'bold' } }}>
                                    <TableCell sx={{width: '25%'}}>配置键</TableCell>
                                    <TableCell sx={{width: '35%'}}>配置值</TableCell>
                                    <TableCell sx={{width: '25%'}}>描述</TableCell>
                                    <TableCell sx={{width: '140px'}}>配置来源</TableCell>
                                    <TableCell>最后更新</TableCell>
                                    <TableCell align="center">操作</TableCell>
                                </TableRow></TableHead>
                                <TableBody>
                                    {configs.map((config) => (
                                        <TableRow hover key={config.configKey}>
                                            <TableCell sx={{wordBreak: 'break-all'}}>{config.configKey}</TableCell>
                                            <TableCell sx={{wordBreak: 'break-all'}}>
                                                <ConfigValue config={config} />
                                            </TableCell>
                                            <TableCell sx={{wordBreak: 'break-all'}}>{config.description}</TableCell>
                                            <TableCell><ConfigSourceChip config={config} /></TableCell>
                                            <TableCell>{config.lastUpdatedTime ? formatDate(config.lastUpdatedTime) : '暂无'}</TableCell>
                                            <TableCell align="center" sx={{whiteSpace: 'nowrap'}}>
                                                <ConfigActions
                                                    config={config}
                                                    canUpdate={canUpdate}
                                                    canDelete={canDelete}
                                                    disabled={isSubmitting}
                                                    onEdit={() => handleOpenEditConfigDialog(config)}
                                                    onDelete={() => handleDeleteConfig(config)}
                                                />
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </ViewportTableContainer>
                    )}
                    {activeTab === 0 && configs.length > 0 && (
                        <Box sx={{ display: { xs: 'block', md: 'none' } }}>
                            {configs.map((config) => (
                                <Box key={config.configKey} sx={{ py: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
                                    <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1, mb: 1 }}>
                                        <Typography variant="subtitle2" sx={{ fontWeight: 800, wordBreak: 'break-all', minWidth: 0 }}>
                                            {config.configKey}
                                        </Typography>
                                        <Box sx={{ flexShrink: 0 }}><ConfigSourceChip config={config} /></Box>
                                    </Box>
                                    <ConfigValue config={config} />
                                    {config.description && (
                                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1, wordBreak: 'break-word' }}>
                                            {config.description}
                                        </Typography>
                                    )}
                                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, mt: 0.75 }}>
                                        <Typography variant="caption" color="text.secondary">
                                            {config.lastUpdatedTime ? formatDate(config.lastUpdatedTime) : '暂无更新时间'}
                                        </Typography>
                                        <ConfigActions
                                            config={config}
                                            canUpdate={canUpdate}
                                            canDelete={canDelete}
                                            disabled={isSubmitting}
                                            onEdit={() => handleOpenEditConfigDialog(config)}
                                            onDelete={() => handleDeleteConfig(config)}
                                        />
                                    </Box>
                                </Box>
                            ))}
                        </Box>
                    )}
                </PageTabPanel>
            </Paper>

            <ConfigFormDialog
                key={`config-dialog-${configDialogKey}`}
                open={openConfigDialog}
                onClose={handleCloseConfigDialog}
                onSubmit={handleConfigFormSubmit}
                initialData={editingConfig ? {
                    configKey: editingConfig.configKey,
                    configValue: editingConfig.sensitive ? '' : (editingConfig.configValue || ''),
                    description: editingConfig.description,
                    sensitive: editingConfig.sensitive,
                } : undefined}
                isEditMode={!!editingConfig}
                serverErrors={configFormServerErrors}
                isLoading={isSubmitting}
            />
            <Snackbar open={snackbar.open} autoHideDuration={6000} onClose={handleSnackbarClose} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
                <Alert onClose={handleSnackbarClose} severity={snackbar.severity} sx={{ width: '100%' }} variant="filled">{snackbar.message}</Alert>
            </Snackbar>
        </Container>
    );
}

export default AdminSystemConfigPage;

import PageTitle from '../../components/layout/PageTitle';
import React, { useCallback, useEffect, useState } from 'react';
import {
    Alert, Avatar, Box, Button, Chip, CircularProgress, Container, Dialog,
    DialogActions, DialogContent, DialogTitle, Divider, FormControl,
    Grid, IconButton, InputLabel, MenuItem, Paper, Select, Snackbar, Stack,
    TextField, Tooltip, Typography,
} from '@mui/material';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EditIcon from '@mui/icons-material/Edit';
import LockResetIcon from '@mui/icons-material/LockReset';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import ToggleOnIcon from '@mui/icons-material/ToggleOn';
import MailOutlineIcon from '@mui/icons-material/MailOutlined';
import { useNavigate, useParams } from 'react-router-dom';

import apiClient from '../../services/apiClient';
import { PERMISSIONS } from '../../constants/permissions';
import { useAuth } from '../../hooks/useAuth';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDateTime } from '../../utils/formatDate.js';
import { validateUserProfile } from '../../utils/userProfileValidation';
import PasswordPolicyHint from '../../components/Auth/PasswordPolicyHint.jsx';
import EmailNotificationPreferencesPanel from '../../components/Profile/EmailNotificationPreferencesPanel.jsx';
import AuthorizationEditorDialog from '../../components/Admin/AuthorizationEditorDialog.jsx';
import { filterAuthorizationGrants } from '../../components/Admin/authorizationEditorWorkflow.js';
import { evaluatePassword, passwordsMatch } from '../../utils/passwordPolicy.js';
import {
    AUTHORIZATION_CHANGE_LABELS,
    ORGANIZATION_MOVE_STRATEGIES,
    ORGANIZATION_MOVE_STRATEGY_BY_VALUE,
    authorizationChangeCounts,
} from './organizationMoveWorkflow.js';

const USER_STATUSES = ['ACTIVE', 'INACTIVE', 'DISABLED', 'LOCKED'];
const USER_STATUS_LABELS = {
    ACTIVE: '启用',
    INACTIVE: '停用',
    DISABLED: '禁用',
    LOCKED: '锁定',
    DELETED: '已删除',
};
const USER_ACTION_BUTTON_SX = {
    minWidth: { xs: 0, sm: 148 },
    minHeight: 40,
    justifyContent: 'flex-start',
    px: 1.5,
    width: '100%',
};
function formatNumber(value, suffix = '') {
    const number = Number(value || 0);
    return (Number.isFinite(number) ? number.toLocaleString('zh-CN') : '0') + suffix;
}

function grantExpiryLabel(grant) {
    return grant.expiresAt ? `有效期至 ${formatDateTime(grant.expiresAt)}` : '长期有效';
}

function grantIssuerLabel(issuerType) {
    const labels = {
        USER: '人工授权',
        IMPORT: '批量导入',
        MIGRATION: '历史迁移',
        SYSTEM_ROOT: '系统根授权',
        SYSTEM_BASELINE: '组织基线',
        SYSTEM_SELF_BASELINE: '个人基础权限',
    };
    return labels[issuerType] || issuerType;
}

const AUTHORIZATION_GRANT_PREVIEW_LIMIT = 8;

function Metric({ label, value, secondary }) {
    return (
        <Box sx={{ minWidth: 0, py: 0.5 }}>
            <Typography variant="caption" color="text.secondary">{label}</Typography>
            <Typography variant="h6" sx={{ fontWeight: 700, letterSpacing: 0, mt: 0.25 }}>{value}</Typography>
            {secondary ? <Typography variant="caption" color="text.secondary">{secondary}</Typography> : null}
        </Box>
    );
}

function ReadOnlyField({ label, value }) {
    return (
        <Box sx={{ minWidth: 0 }}>
            <Typography variant="caption" color="text.secondary">{label}</Typography>
            <Typography variant="body2" sx={{ mt: 0.25, overflowWrap: 'anywhere' }}>{value || '-'}</Typography>
        </Box>
    );
}

function SectionHeader({ title, subtitle }) {
    return (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' }, mb: 1.5 }}>
            <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{title}</Typography>
                {subtitle ? <Typography variant="body2" color="text.secondary">{subtitle}</Typography> : null}
            </Box>
        </Stack>
    );
}

function CapabilityButton({ decision, children, ...buttonProps }) {
    const disabled = buttonProps.disabled || !decision?.allowed;
    const button = <Button {...buttonProps} disabled={disabled}>{children}</Button>;
    if (!disabled || !decision?.message) return button;
    return <Tooltip title={decision.message}><span>{button}</span></Tooltip>;
}

function PersonalDetailsDialog({ open, user, saving, error, canUpdateDetails, canUpdateContact, onClose, onSave }) {
    const [draft, setDraft] = useState({ name: '', phone: '', email: '' });
    const [validationError, setValidationError] = useState('');

    useEffect(() => {
        if (!open || !user) return;
        setDraft({
            name: user.name || '',
            phone: user.phone || '',
            email: user.email || '',
        });
        setValidationError('');
    }, [open, user]);

    const submit = () => {
        const validation = validateUserProfile(draft);
        if (!validation.valid) return setValidationError(`${Object.values(validation.errors)[0]}。`);
        setValidationError('');
        onSave({
            details: {
                ...validation.normalized,
                email: validation.normalized.email || null,
            },
        });
    };

    return (
        <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
            <DialogTitle>编辑个人信息</DialogTitle>
            <DialogContent dividers>
                {error || validationError ? <Alert severity="error" sx={{ mb: 2 }}>{validationError || error}</Alert> : null}
                <Grid container spacing={2} sx={{ pt: 0.5 }}>
                        <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth label="姓名" value={draft.name} disabled={saving || !canUpdateDetails}
                                       onChange={event => setDraft(current => ({ ...current, name: event.target.value }))} />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth label="手机号" value={draft.phone} disabled={saving || !canUpdateContact} inputMode="tel"
                                       onChange={event => setDraft(current => ({ ...current, phone: event.target.value }))} />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth label="邮箱" value={draft.email} disabled={saving || !canUpdateContact} type="email"
                                       onChange={event => setDraft(current => ({ ...current, email: event.target.value }))} />
                        </Grid>
                </Grid>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={saving}>取消</Button>
                <Button variant="contained" onClick={submit} disabled={saving || (!canUpdateDetails && !canUpdateContact)} loading={saving}>
                    {saving ? '保存中...' : '保存个人信息'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

function OrganizationMoveDialog({
    open, user, orgUnits, loading, previewing, saving, error, onClose, onPreview, onSave,
}) {
    const [targetOrgUnitId, setTargetOrgUnitId] = useState('');
    const [strategy, setStrategy] = useState('REVOKE_ALL');
    const [preview, setPreview] = useState(null);
    const [validationError, setValidationError] = useState('');

    useEffect(() => {
        if (!open || !user) return;
        setTargetOrgUnitId(user.orgUnitId || '');
        setStrategy('REVOKE_ALL');
        setPreview(null);
        setValidationError('');
    }, [open, user]);

    const previewChanges = async () => {
        if (!targetOrgUnitId || targetOrgUnitId === user.orgUnitId) {
            return setValidationError('请选择不同于当前归属的目标组织。');
        }
        if (!strategy) return setValidationError('请选择既有授权处理策略。');
        setValidationError('');
        const result = await onPreview({
            targetOrgUnitId,
            strategy,
            expectedAuthorizationVersion: user.authorizationVersion,
        });
        if (result) setPreview(result);
    };

    const confirm = () => onSave({
        targetOrgUnitId,
        strategy,
        expectedAuthorizationVersion: preview.expectedAuthorizationVersion,
    });

    const selectedStrategy = ORGANIZATION_MOVE_STRATEGY_BY_VALUE[strategy];
    const authorizationChanges = preview?.authorization?.changes || [];
    const changeCounts = authorizationChangeCounts(authorizationChanges);

    return (
        <Dialog open={open} onClose={saving || previewing ? undefined : onClose} fullWidth maxWidth="md">
            <DialogTitle>调整所属组织</DialogTitle>
            <DialogContent dividers>
                {error || validationError ? <Alert severity="error" sx={{ mb: 2 }}>{validationError || error}</Alert> : null}
                {loading || previewing ? <Box sx={{ minHeight: 220, display: 'grid', placeItems: 'center', gap: 1 }}>
                    <CircularProgress />
                    <Typography variant="body2" color="text.secondary">{previewing ? '正在计算组织、授权和工单影响…' : '正在加载组织…'}</Typography>
                </Box> : !preview ? (
                    <Stack spacing={2}>
                        <Alert severity="info">
                            先选择目标组织和授权处理方式，再由服务端计算实际变化；预览不会写入任何数据。
                        </Alert>
                        <FormControl fullWidth disabled={saving}>
                            <InputLabel>目标组织</InputLabel>
                            <Select label="目标组织" value={targetOrgUnitId} onChange={event => {
                                setTargetOrgUnitId(event.target.value);
                                setPreview(null);
                            }}>
                                {orgUnits.map(org => <MenuItem key={org.id} value={org.id}>{org.name}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <FormControl fullWidth disabled={saving}>
                            <InputLabel>授权处理策略</InputLabel>
                            <Select label="授权处理策略" value={strategy} onChange={event => {
                                setStrategy(event.target.value);
                                setPreview(null);
                            }}>
                                {ORGANIZATION_MOVE_STRATEGIES.map(option => (
                                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }}>
                            <Typography variant="subtitle2">{selectedStrategy?.label}</Typography>
                            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                {selectedStrategy?.description}
                            </Typography>
                        </Paper>
                    </Stack>
                ) : (
                    <Stack spacing={2}>
                        <Alert severity="success">预检完成。确认前请核对以下实际变化。</Alert>
                        <Paper variant="outlined" sx={{ p: 2, borderColor: 'warning.light', bgcolor: 'action.hover' }}>
                            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>授权处理结果：{selectedStrategy?.label}</Typography>
                            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                                {selectedStrategy?.description}
                            </Typography>
                        </Paper>
                        <Paper variant="outlined" sx={{ p: 2 }}>
                            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>所属组织</Typography>
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' }, mt: 1 }}>
                                <Chip label={preview.organization.beforeOrgUnitName} variant="outlined" />
                                <Typography color="text.secondary">变更为</Typography>
                                <Chip label={preview.organization.afterOrgUnitName} color="primary" />
                            </Stack>
                        </Paper>

                        <Paper variant="outlined" sx={{ p: 2 }}>
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
                                <Typography variant="subtitle1" sx={{ fontWeight: 700, flex: 1 }}>授权变化</Typography>
                                <Chip size="small" label={`调整 ${changeCounts.UPDATE}`} color="primary" variant="outlined" />
                                <Chip size="small" label={`新增 ${changeCounts.ADD}`} color="success" variant="outlined" />
                                <Chip size="small" label={`撤销 ${changeCounts.REMOVE}`} color="error" variant="outlined" />
                            </Stack>
                            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                                生效授权数量：{preview.authorization.beforeActiveGrantCount} → {preview.authorization.afterActiveGrantCount}
                            </Typography>
                            {authorizationChanges.length === 0 ? (
                                <Alert severity={strategy === 'KEEP' ? 'warning' : 'info'} sx={{ mt: 1.5 }}>
                                    {strategy === 'KEEP' ? '原子权限保持不变，数据视野将按新的所属组织重新计算。' : '当前没有需要调整的授权。'}
                                </Alert>
                            ) : (
                                <Stack spacing={1} sx={{ mt: 1.5 }}>
                                    {authorizationChanges.map((change, index) => {
                                        const snapshot = change.after || change.before;
                                        return (
                                            <Box key={`${snapshot?.permissionName || 'permission'}-${index}`} sx={{ p: 1.25, border: 1, borderColor: 'divider', borderRadius: 1 }}>
                                                <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                                                    <Chip size="small" label={AUTHORIZATION_CHANGE_LABELS[change.kind] || change.kind}
                                                          color={change.kind === 'REMOVE' ? 'error' : change.kind === 'ADD' ? 'success' : 'primary'} />
                                                    <Typography variant="subtitle2">{snapshot?.permissionDisplayName || snapshot?.permissionName}</Typography>
                                                </Stack>
                                                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                                                    {change.kind === 'REMOVE' ? '该 Grant 将被撤销' : '该 Grant 的授权事实将更新'}
                                                </Typography>
                                            </Box>
                                        );
                                    })}
                                </Stack>
                            )}
                        </Paper>

                        <Grid container spacing={1.5}>
                            <Grid size={{ xs: 12, md: 6 }}>
                                <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
                                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>工单影响</Typography>
                                    <Typography variant="body2" sx={{ mt: 1 }}>未闭环提单：{preview.workOrders.openReportedWorkOrders}</Typography>
                                    <Typography variant="body2">待接责任：{preview.workOrders.pendingAcceptanceAssignments}</Typography>
                                    <Typography variant="body2">处理中责任：{preview.workOrders.acceptedAssignments}</Typography>
                                    <Typography variant="caption" color="text.secondary">历史归属和当前处理责任不会自动迁移或改派。</Typography>
                                </Paper>
                            </Grid>
                            <Grid size={{ xs: 12, md: 6 }}>
                                <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
                                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>授权影响</Typography>
                                    <Typography variant="body2">有效权限：{preview.management.activeGrantCount}</Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        {preview.management.reauthorizationRecommended ? '迁移后建议按新职责重新核对授权。' : '当前迁移方向无需强制重授权。'}
                                    </Typography>
                                </Paper>
                            </Grid>
                        </Grid>

                        {preview.notices.map(notice => (
                            <Alert key={notice.code} severity={notice.severity === 'WARNING' ? 'warning' : 'info'}>
                                <Typography variant="subtitle2">{notice.title}</Typography>
                                <Typography variant="body2">{notice.detail}</Typography>
                            </Alert>
                        ))}
                    </Stack>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={saving || previewing}>取消</Button>
                {preview ? <Button onClick={() => setPreview(null)} disabled={saving}>返回修改</Button> : null}
                <Button variant="contained" color="warning"
                        onClick={preview ? confirm : previewChanges}
                        disabled={saving || previewing || loading}
                        loading={saving || previewing}>
                    {saving ? '迁移中...' : previewing ? '计算中...' : preview ? '确认迁移' : '预览变化'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

function DepartmentMoveDialog({ open, user, departments, loading, saving, error, onClose, onSave }) {
    const [targetDepartmentId, setTargetDepartmentId] = useState('');

    useEffect(() => {
        if (open && user) setTargetDepartmentId(user.departmentId || '');
    }, [open, user]);

    const options = departments.filter(department => (
        department.orgUnitId === user?.orgUnitId && department.status === 'ACTIVE'
    ));
    const changed = targetDepartmentId !== (user?.departmentId || '');

    return (
        <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
            <DialogTitle>调整所属部门</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={2}>
                    {error ? <Alert severity="error">{error}</Alert> : null}
                    <Alert severity="info">部门用于当前组织内部协作、派单和统计；不会改变用户所属组织或扩大数据范围。</Alert>
                    <FormControl fullWidth disabled={loading || saving}>
                        <InputLabel>所属部门</InputLabel>
                        <Select
                            label="所属部门"
                            value={targetDepartmentId}
                            onChange={event => setTargetDepartmentId(event.target.value)}
                        >
                            <MenuItem value="">不归属具体部门</MenuItem>
                            {options.map(department => (
                                <MenuItem key={department.id} value={department.id}>
                                    {department.name}（{department.code}）
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    {loading ? <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}><CircularProgress size={18} /><Typography variant="body2">正在加载部门目录…</Typography></Stack> : null}
                    {!loading && options.length === 0 ? <Alert severity="warning">该组织尚未建立可用部门，只能保持组织直属。</Alert> : null}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={saving}>取消</Button>
                <Button
                    variant="contained"
                    onClick={() => onSave({
                        targetDepartmentId: targetDepartmentId || null,
                        expectedAuthorizationVersion: user.authorizationVersion,
                    })}
                    disabled={loading || saving || !changed}
                >
                    {saving ? '调整中…' : '确认调整'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

function UserStatusDialog({ open, user, saving, error, onClose, onSave }) {
    const [status, setStatus] = useState('ACTIVE');

    useEffect(() => {
        if (open && user) setStatus(user.status);
    }, [open, user]);

    return (
        <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="xs">
            <DialogTitle>调整账号状态</DialogTitle>
            <DialogContent dividers>
                {error ? <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert> : null}
                <FormControl fullWidth sx={{ mt: 0.5 }} disabled={saving}>
                    <InputLabel>账号状态</InputLabel>
                    <Select label="账号状态" value={status} onChange={event => setStatus(event.target.value)}>
                        {USER_STATUSES.map(value => <MenuItem key={value} value={value}>{USER_STATUS_LABELS[value]}</MenuItem>)}
                    </Select>
                </FormControl>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={saving}>取消</Button>
                <Button variant="contained" onClick={() => onSave({ status })} disabled={saving || status === user?.status} loading={saving}>
                    {saving ? '保存中...' : '保存账号状态'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

function ResetPasswordDialog({ open, saving, error, onClose, onSave, context, policy }) {
    const [password, setPassword] = useState('');
    const [confirmation, setConfirmation] = useState('');
    const [validationError, setValidationError] = useState('');

    useEffect(() => {
        if (!open) return;
        setPassword('');
        setConfirmation('');
        setValidationError('');
    }, [open]);

    const submit = () => {
        if (!evaluatePassword(password, context, policy).valid) {
            return setValidationError('新密码不符合安全策略，请按提示调整。');
        }
        if (!passwordsMatch(password, confirmation)) {
            return setValidationError('两次输入的新密码不一致。');
        }
        setValidationError('');
        onSave(password, confirmation);
    };

    return (
        <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="xs">
            <DialogTitle>重置登录密码</DialogTitle>
            <DialogContent dividers>
                {error || validationError ? <Alert severity="error" sx={{ mb: 2 }}>{validationError || error}</Alert> : null}
                <Stack spacing={1.5}>
                    <TextField fullWidth type="password" label="新密码" value={password} disabled={saving}
                               onChange={event => setPassword(event.target.value)} sx={{ mt: 0.5 }}
                               slotProps={{ htmlInput: { minLength: policy.minLength, maxLength: policy.maxLength } }} />
                    <PasswordPolicyHint password={password} context={context} policy={policy} />
                    <TextField fullWidth type="password" label="确认新密码" value={confirmation} disabled={saving}
                               onChange={event => { setConfirmation(event.target.value); setValidationError(''); }}
                               error={confirmation.length > 0 && !passwordsMatch(password, confirmation)}
                               helperText={confirmation.length > 0 && !passwordsMatch(password, confirmation) ? '两次输入的新密码不一致' : '请再次输入新密码'}
                               slotProps={{ htmlInput: { minLength: policy.minLength, maxLength: policy.maxLength } }} />
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={saving}>取消</Button>
                <Button variant="contained" onClick={submit} disabled={saving || !passwordsMatch(password, confirmation)} loading={saving}>
                    {saving ? '重置中...' : '确认重置'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

export default function AdminUserDetailPage() {
    const { userId } = useParams();
    const navigate = useNavigate();
    const { hasPermission, passwordPolicy } = useAuth();
    const canUpdateDetails = hasPermission(PERMISSIONS.USER_MANAGE_UPDATE_DETAILS);
    const canUpdateContact = hasPermission(PERMISSIONS.USER_MANAGE_CONTACT_UPDATE);
    const canMoveOrganization = hasPermission(PERMISSIONS.USER_MANAGE_ORGANIZATION_MOVE);
    const canMoveDepartment = hasPermission(PERMISSIONS.USER_MANAGE_DEPARTMENT_MOVE);
    const canUpdateStatus = [
        PERMISSIONS.USER_MANAGE_STATUS_ACTIVATE,
        PERMISSIONS.USER_MANAGE_STATUS_DEACTIVATE,
        PERMISSIONS.USER_MANAGE_STATUS_LOCK,
        PERMISSIONS.USER_MANAGE_STATUS_UNLOCK,
    ].some(hasPermission);
    const canManageAuthorization = [
        PERMISSIONS.USER_AUTHORIZATION_GRANT,
        PERMISSIONS.USER_AUTHORIZATION_ADJUST,
        PERMISSIONS.USER_AUTHORIZATION_REVOKE,
    ].some(hasPermission);
    const canViewAuthorization = hasPermission(PERMISSIONS.USER_AUTHORIZATION_VIEW);
    const canResetPassword = hasPermission(PERMISSIONS.USER_MANAGE_RESET_PASSWORD);

    const [detail, setDetail] = useState(null);
    const [authorization, setAuthorization] = useState(null);
    const [orgUnits, setOrgUnits] = useState([]);
    const [departments, setDepartments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [pageError, setPageError] = useState('');
    const [activeDialog, setActiveDialog] = useState('');
    const [editorDataLoading, setEditorDataLoading] = useState(false);
    const [dialogSaving, setDialogSaving] = useState(false);
    const [organizationMovePreviewing, setOrganizationMovePreviewing] = useState(false);
    const [dialogError, setDialogError] = useState('');
    const [showAllAuthorizationGrants, setShowAllAuthorizationGrants] = useState(false);
    const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' });

    const loadDetail = useCallback(async () => {
        setLoading(true);
        setPageError('');
        try {
            const [detailResponse, authorizationResponse] = await Promise.all([
                apiClient.get('/admin/users/' + userId + '/detail'),
                canViewAuthorization ? apiClient.get(`/admin/users/${userId}/authorization`) : Promise.resolve({ data: null }),
            ]);
            setDetail(detailResponse.data);
            setAuthorization(authorizationResponse.data);
        } catch (error) {
            setPageError(formatApiErrorMessage(error, '加载用户详情失败，请稍后重试。'));
        } finally {
            setLoading(false);
        }
    }, [canViewAuthorization, userId]);

    useEffect(() => {
        loadDetail();
    }, [loadDetail]);

    const ensureEditorData = useCallback(async (kind) => {
        const needOrganizations = kind === 'organization' && orgUnits.length === 0;
        const needDepartments = kind === 'department' && departments.length === 0;
        if (!needOrganizations && !needDepartments) return;
        setEditorDataLoading(true);
        try {
            if (needOrganizations) {
                const response = await apiClient.get('/admin/org-units');
                setOrgUnits(response.data || []);
            }
            if (needDepartments) {
                const response = await apiClient.get('/admin/departments');
                setDepartments(response.data || []);
            }
        } catch (error) {
            setDialogError(formatApiErrorMessage(error, '加载编辑选项失败，请稍后重试。'));
        } finally {
            setEditorDataLoading(false);
        }
    }, [departments.length, orgUnits.length]);

    const openDialog = (dialogName) => {
        setDialogError('');
        setActiveDialog(dialogName);
        if (dialogName === 'organization' || dialogName === 'department') {
            ensureEditorData(dialogName);
        }
    };

    const closeDialog = () => {
        if (dialogSaving) return;
        setActiveDialog('');
        setDialogError('');
    };

    const saveUserPatch = async (payload, successMessage) => {
        setDialogSaving(true);
        setDialogError('');
        try {
            const response = await apiClient.put('/admin/users/' + userId + '/detail', payload);
            setDetail(response.data);
            setActiveDialog('');
            setSnackbar({ open: true, message: successMessage, severity: 'success' });
        } catch (error) {
            setDialogError(formatApiErrorMessage(error, error.message || '保存用户失败，请稍后重试。'));
        } finally {
            setDialogSaving(false);
        }
    };

    const previewOrganizationMove = async payload => {
        setOrganizationMovePreviewing(true);
        setDialogError('');
        try {
            const response = await apiClient.post(
                `/admin/users/${userId}/organization-move/preview`, payload,
            );
            return response.data;
        } catch (error) {
            setDialogError(formatApiErrorMessage(error, '组织迁移预检失败，请核对目标组织和授权处理策略。'));
            return null;
        } finally {
            setOrganizationMovePreviewing(false);
        }
    };

    const executeOrganizationMove = async payload => {
        setDialogSaving(true);
        setDialogError('');
        try {
            const response = await apiClient.post(
                `/admin/users/${userId}/organization-move`, payload,
            );
            setDetail(response.data);
            setActiveDialog('');
            setSnackbar({ open: true, message: '所属组织及相关授权已更新。', severity: 'success' });
        } catch (error) {
            setDialogError(formatApiErrorMessage(error, '组织迁移执行失败，请重新预览后再试。'));
        } finally {
            setDialogSaving(false);
        }
    };

    const executeDepartmentMove = async payload => {
        setDialogSaving(true);
        setDialogError('');
        try {
            const response = await apiClient.post(`/admin/users/${userId}/department-move`, payload);
            setDetail(response.data);
            setActiveDialog('');
            setSnackbar({ open: true, message: '所属部门已更新。', severity: 'success' });
        } catch (error) {
            setDialogError(formatApiErrorMessage(error, '调整所属部门失败，请刷新用户信息后重试。'));
        } finally {
            setDialogSaving(false);
        }
    };

    const resetPassword = async (newPassword, confirmPassword) => {
        setDialogSaving(true);
        setDialogError('');
        try {
            await apiClient.post('/admin/users/' + userId + '/reset-password', { newPassword, confirmPassword });
            setActiveDialog('');
            setSnackbar({ open: true, message: '用户密码已重置。', severity: 'success' });
        } catch (error) {
            setDialogError(formatApiErrorMessage(error, '重置密码失败，请稍后重试。'));
        } finally {
            setDialogSaving(false);
        }
    };

    if (loading) {
        return <Box sx={{ display: 'grid', placeItems: 'center', minHeight: 280 }}><CircularProgress /></Box>;
    }
    if (pageError || !detail) {
        return (
            <Container maxWidth={false} disableGutters>
                <Alert severity="error">{pageError || '用户详情不存在。'}</Alert>
                <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/admin/user-management')} sx={{ mt: 2 }}>
                    返回用户列表
                </Button>
            </Container>
        );
    }

    const performance = detail.performance || {};
    const user = detail.user;
    const editable = user.status !== 'DELETED';
    const capability = action => detail.capabilities?.actions?.[action] || {
        allowed: false,
        reasonCode: 'CAPABILITY_UNAVAILABLE',
        message: '服务端未返回该管理动作的判定结果。',
    };
    const editableActions = [
        'UPDATE_DETAILS', 'UPDATE_IDENTITY', 'MOVE_ORGANIZATION', 'MOVE_DEPARTMENT', 'UPDATE_STATUS',
        'RESET_PASSWORD', 'MANAGE_ACCESS', 'UPDATE_CONTACT',
    ];
    const targetEditable = editable && editableActions.some(action => capability(action).allowed);
    const authorizationGrants = filterAuthorizationGrants(authorization?.grants);
    const visibleAuthorizationGrants = showAllAuthorizationGrants
        ? authorizationGrants
        : authorizationGrants.slice(0, AUTHORIZATION_GRANT_PREVIEW_LIMIT);
    const hiddenAuthorizationGrantCount = Math.max(
        0,
        authorizationGrants.length - AUTHORIZATION_GRANT_PREVIEW_LIMIT,
    );
    return (
        <Container maxWidth={false} disableGutters sx={{ pb: 3 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: "center", mb: 1.25 }}>
                <Tooltip title="返回用户列表">
                    <IconButton onClick={() => navigate('/admin/user-management')}><ArrowBackIcon /></IconButton>
                </Tooltip>
                <Typography variant="body2" color="text.secondary">用户管理 / {user.username}</Typography>
            </Stack>

            <Paper variant="outlined" sx={{ p: { xs: 1.5, sm: 2 }, mb: 1.5 }}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' } }}>
                    <Avatar variant="rounded" sx={{ width: 68, height: 68, bgcolor: user.systemProtected ? '#0F766E' : 'primary.main', fontSize: 24, fontWeight: 800 }}>
                        {(user.name || user.username || '?').slice(0, 1)}
                    </Avatar>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                            <PageTitle>{user.name || user.username}</PageTitle>
                            <Chip size="small" label={USER_STATUS_LABELS[user.status] || user.status} color={user.status === 'ACTIVE' ? 'success' : 'default'} variant="outlined" />
                        </Stack>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                            {[user.username, user.orgUnitName || '未分配组织', user.phone || '未填写手机号', `授权版本 ${user.authorizationVersion ?? '-'}`].join(' · ')}
                        </Typography>
                    </Box>
                </Stack>
                {targetEditable ? (
                    <>
                        <Divider sx={{ my: 1.75 }} />
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
                            <Typography variant="subtitle2" sx={{ minWidth: 72 }}>用户操作</Typography>
                            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', sm: 'repeat(auto-fill, 148px)' }, gap: 1, width: '100%' }}>
                                {(canUpdateDetails || canUpdateContact) ? <CapabilityButton decision={{
                                    allowed: canUpdateDetails && capability('UPDATE_DETAILS').allowed
                                        || canUpdateContact && capability('UPDATE_CONTACT').allowed,
                                    message: capability('UPDATE_CONTACT').message || capability('UPDATE_DETAILS').message,
                                }} size="small" variant="outlined" sx={USER_ACTION_BUTTON_SX} aria-label="编辑个人信息" startIcon={<EditIcon />} onClick={() => openDialog('details')}>编辑个人信息</CapabilityButton> : null}
                                {canUpdateContact ? <CapabilityButton decision={capability('UPDATE_CONTACT')} size="small" variant="outlined" sx={USER_ACTION_BUTTON_SX} aria-label="修改邮件通知偏好" startIcon={<MailOutlineIcon />} onClick={() => openDialog('email-notifications')}>邮件通知偏好</CapabilityButton> : null}
                                {canMoveOrganization ? <CapabilityButton decision={capability('MOVE_ORGANIZATION')} size="small" variant="outlined" sx={USER_ACTION_BUTTON_SX} aria-label="调整所属组织" startIcon={<ManageAccountsIcon />} onClick={() => openDialog('organization')}>调整所属组织</CapabilityButton> : null}
                                {canMoveDepartment ? <CapabilityButton decision={capability('MOVE_DEPARTMENT')} size="small" variant="outlined" sx={USER_ACTION_BUTTON_SX} aria-label="调整所属部门" startIcon={<AccountTreeIcon />} onClick={() => openDialog('department')}>调整所属部门</CapabilityButton> : null}
                                {canManageAuthorization && canViewAuthorization ? (
                                    <CapabilityButton decision={capability('MANAGE_ACCESS')} size="small" variant="outlined" sx={USER_ACTION_BUTTON_SX} aria-label="调整用户授权" startIcon={<AdminPanelSettingsIcon />} onClick={() => openDialog('authorization')}>调整授权</CapabilityButton>
                                ) : null}
                                {canUpdateStatus ? <CapabilityButton decision={capability('UPDATE_STATUS')} size="small" variant="outlined" sx={USER_ACTION_BUTTON_SX} aria-label="调整账号状态" startIcon={<ToggleOnIcon />} onClick={() => openDialog('status')}>调整账号状态</CapabilityButton> : null}
                                {canResetPassword ? <CapabilityButton decision={capability('RESET_PASSWORD')} size="small" variant="outlined" sx={USER_ACTION_BUTTON_SX} aria-label="重置登录密码" startIcon={<LockResetIcon />} onClick={() => openDialog('password')}>重置登录密码</CapabilityButton> : null}
                            </Box>
                        </Stack>
                    </>
                ) : null}
            </Paper>

            {!targetEditable ? (
                <Alert severity="warning" sx={{ mb: 1.5 }}>
                    {capability('MANAGE_ACCESS').message || '该账号由上级管理域维护，当前账号仅可查看。'}
                </Alert>
            ) : null}

            <Paper variant="outlined" sx={{ mb: 1.5, overflow: 'hidden' }}>
                <Grid container>
                    {[
                        ['累计提单', formatNumber(performance.reportedTotal), `近30天 ${formatNumber(performance.reportedLast30Days)}`],
                        ['未闭环提单', formatNumber(performance.reportedOpen), `已闭环 ${formatNumber(performance.reportedClosed)}`],
                        ['参与处理工单', formatNumber(performance.assignedTotal), `当前在办 ${formatNumber(performance.activeAssignments)}`],
                        ['累计回单', formatNumber(performance.responsesSubmitted), `近30天 ${formatNumber(performance.responsesLast30Days)}`],
                        ['24小时内回单率', formatNumber(performance.responseWithin24HourRate, '%'), `${formatNumber(performance.responsesWithin24Hours)} 次`],
                        ['平均回单历时', formatNumber(performance.averageResponseMinutes, ' 分钟'), ''],
                        ['验收通过率', formatNumber(performance.verificationApprovalRate, '%'), `${formatNumber(performance.verificationsCompleted)} 次验收`],
                        ['提单平均闭环', formatNumber(performance.averageReportedClosureHours, ' 小时'), ''],
                    ].map(([label, value, secondary], index) => (
                        <Grid key={label} size={{ xs: 6, sm: 3 }} sx={{ px: 2, py: 1.25, borderRight: index % 4 === 3 ? 0 : 1, borderBottom: index < 4 ? 1 : 0, borderColor: 'divider' }}>
                            <Metric label={label} value={value} secondary={secondary} />
                        </Grid>
                    ))}
                </Grid>
            </Paper>

            <Grid container spacing={1.5} sx={{ alignItems: 'stretch' }}>
                <Grid size={{ xs: 12, lg: 4 }}>
                    <Paper variant="outlined" sx={{ height: '100%', p: 2 }}>
                        <SectionHeader title="账号与联系方式" />
                        <Stack spacing={1.5} divider={<Divider flexItem />}>
                            <ReadOnlyField label="用户标识" value={user.id} />
                            <ReadOnlyField label="用户名" value={user.username} />
                            <ReadOnlyField label="手机号" value={user.phone} />
                            <ReadOnlyField label="邮箱" value={user.email} />
                            <ReadOnlyField label="所属组织" value={user.orgUnitName} />
                            <ReadOnlyField label="所属部门" value={user.departmentName || '组织直属'} />
                            <ReadOnlyField label="账号状态" value={`${USER_STATUS_LABELS[user.status] || user.status}${user.systemProtected ? ' · 系统保护' : ''}`} />
                            <ReadOnlyField label="创建时间" value={formatDateTime(user.createdAt)} />
                            <ReadOnlyField label="最后更新" value={formatDateTime(user.updatedAt)} />
                        </Stack>
                    </Paper>
                </Grid>
                <Grid size={{ xs: 12, lg: 8 }}>
                    <Paper variant="outlined" sx={{ height: '100%', p: 2 }}>
                        <SectionHeader
                            title="当前权限"
                            subtitle={authorization ? `${authorization.effectivePermissionCount} 项有效权限，共 ${authorizationGrants.length} 条可见授权` : '无权查看授权详情'}
                        />
                        {!authorization ? (
                            <Typography color="text.secondary">当前账号没有用户授权查看权限。</Typography>
                        ) : authorizationGrants.length === 0 ? (
                            <Typography color="text.secondary">没有当前账号可见的权限。</Typography>
                        ) : (
                            <Stack divider={<Divider flexItem />}>
                                {visibleAuthorizationGrants.map(grant => (
                                    <Box key={grant.id} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'minmax(250px, 1.35fr) minmax(150px, .75fr) minmax(180px, .9fr)' }, gap: 1.5, alignItems: 'center', py: 1.25 }}>
                                        <Box sx={{ minWidth: 0 }}>
                                            <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{grant.permissionDisplayName}</Typography>
                                                {!grant.manageable ? <Chip size="small" label="只读" variant="outlined" /> : null}
                                                {grant.grantOption ? <Chip size="small" color="primary" label="可转授" variant="outlined" /> : null}
                                            </Stack>
                                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflowWrap: 'anywhere' }}>{grant.permissionName}</Typography>
                                        </Box>
                                        <ReadOnlyField label="状态" value={grant.effective ? '有效' : grant.status} />
                                        <Box sx={{ minWidth: 0 }}>
                                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflowWrap: 'anywhere' }}>{grantIssuerLabel(grant.issuerType)}</Typography>
                                            <Stack direction="row" spacing={0.75} sx={{ alignItems: "center", flexWrap: "wrap", mt: 0.4 }}>
                                                <Typography variant="caption" color="text.secondary">{grantExpiryLabel(grant)}</Typography>
                                            </Stack>
                                        </Box>
                                    </Box>
                                ))}
                                {hiddenAuthorizationGrantCount > 0 ? (
                                    <Box sx={{ pt: 1.25, textAlign: 'center' }}>
                                        <Button
                                            size="small"
                                            onClick={() => setShowAllAuthorizationGrants(current => !current)}
                                            aria-expanded={showAllAuthorizationGrants}
                                        >
                                            {showAllAuthorizationGrants
                                                ? '收起权限清单'
                                                : `展开其余 ${hiddenAuthorizationGrantCount} 条权限`}
                                        </Button>
                                    </Box>
                                ) : null}
                            </Stack>
                        )}
                        {authorization?.grants?.some(grant => !grant.manageable) ? (
                            <Alert severity="info" sx={{ mt: 1.5 }}>系统基线和非当前授权链来源可查看但不能在此入口调整。</Alert>
                        ) : null}
                    </Paper>
                </Grid>
            </Grid>

            {activeDialog === 'email-notifications' && canUpdateContact && editable && capability('UPDATE_CONTACT').allowed && (
                <EmailNotificationPreferencesPanel
                    key={user.id} targetUserId={user.id}
                    targetName={`${user.name || user.username}（${user.username}）`}
                    onClose={closeDialog}
                />
            )}
            <PersonalDetailsDialog
                open={activeDialog === 'details'} user={user}
                saving={dialogSaving} error={dialogError}
                canUpdateDetails={canUpdateDetails && capability('UPDATE_DETAILS').allowed}
                canUpdateContact={canUpdateContact && capability('UPDATE_CONTACT').allowed}
                onClose={closeDialog}
                onSave={payload => saveUserPatch(payload, '个人信息已保存。')}
            />
            <OrganizationMoveDialog
                open={activeDialog === 'organization'} user={user} orgUnits={orgUnits}
                loading={editorDataLoading} previewing={organizationMovePreviewing}
                saving={dialogSaving} error={dialogError}
                onClose={closeDialog}
                onPreview={previewOrganizationMove}
                onSave={executeOrganizationMove}
            />
            <DepartmentMoveDialog
                open={activeDialog === 'department'} user={user} departments={departments}
                loading={editorDataLoading} saving={dialogSaving} error={dialogError}
                onClose={closeDialog} onSave={executeDepartmentMove}
            />
            <UserStatusDialog
                open={activeDialog === 'status'} user={user} saving={dialogSaving} error={dialogError}
                onClose={closeDialog}
                onSave={payload => saveUserPatch(payload, '账号状态已保存。')}
            />
            <AuthorizationEditorDialog
                open={activeDialog === 'authorization'}
                userId={userId}
                onClose={closeDialog}
                onApplied={nextAuthorization => {
                    setAuthorization(nextAuthorization);
                    setDetail(current => ({
                        ...current,
                        user: { ...current.user, authorizationVersion: nextAuthorization.authorizationVersion },
                    }));
                    setActiveDialog('');
                    setSnackbar({ open: true, message: '用户授权已按预览计划更新。', severity: 'success' });
                }}
            />
            <ResetPasswordDialog
                open={activeDialog === 'password'} saving={dialogSaving} error={dialogError}
                onClose={closeDialog} onSave={resetPassword} context={detail?.user} policy={passwordPolicy}
            />

            <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar(current => ({ ...current, open: false }))}>
                <Alert severity={snackbar.severity} onClose={() => setSnackbar(current => ({ ...current, open: false }))}>{snackbar.message}</Alert>
            </Snackbar>
        </Container>
    );
}

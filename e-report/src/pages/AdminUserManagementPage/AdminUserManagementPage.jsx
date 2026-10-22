import PageTitle from '../../components/layout/PageTitle';
import ListResultsBar, { ListPagination } from '../../components/layout/ListResultsBar';
import ViewportTableContainer from '../../components/layout/ViewportTableContainer';
import SearchableSelectField from '../../components/SearchInput/SearchableSelectField';
import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
    Container, Paper, Typography, Box, Button, TextField, Table, TableBody,
    TableCell, TableHead, TableRow, IconButton, Tooltip,
    CircularProgress, Alert, Chip, Dialog, DialogActions,
    DialogContent, DialogContentText, DialogTitle, Select, MenuItem, FormControl, InputLabel,
    FormHelperText, Snackbar, Menu,
    FormControlLabel, Switch, LinearProgress, Stack
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import LockResetIcon from '@mui/icons-material/LockReset';
import ToggleOnIcon from '@mui/icons-material/ToggleOn';
import ToggleOffIcon from '@mui/icons-material/ToggleOff';
import RestoreFromTrashIcon from '@mui/icons-material/RestoreFromTrash';
import VisibilityIcon from '@mui/icons-material/Visibility';
import DownloadIcon from '@mui/icons-material/Download';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import FilterAltOffIcon from '@mui/icons-material/FilterAltOff';
import CancelIcon from '@mui/icons-material/Cancel';
import HistoryIcon from '@mui/icons-material/History';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import { useNavigate } from 'react-router-dom';

import apiClient from '../../services/apiClient';
import { downloadFileInChunks } from '../../services/fileDownloadService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDate, formatDateTime } from '../../utils/formatDate.js';
import SearchInput from '../../components/SearchInput/SearchInput';
import { useAuth } from '../../hooks/useAuth';
import { PERMISSIONS } from '../../constants/permissions';
import PasswordPolicyHint from '../../components/Auth/PasswordPolicyHint.jsx';
import { evaluatePassword, passwordsMatch } from '../../utils/passwordPolicy.js';
import UserTransferDialog from './UserTransferDialog.jsx';
import UserExportTaskDialog from './UserExportTaskDialog.jsx';
import {
    cancelUserTransfer,
    createUserTransferExport,
    getUserTransferJob,
    previewUserTransferExport,
} from '../../services/userTransferService.js';
import {
    buildUserExportRequest,
    bindUserExportPreview,
    exportFilterLabels,
} from './userTransferWorkflow.js';

const USER_STATUSES = ["ACTIVE", "INACTIVE", "DISABLED", "LOCKED"]; // DELETED 状态不由表单直接设置
const USER_STATUS_LABELS = {
    ACTIVE: '启用',
    INACTIVE: '停用',
    DISABLED: '禁用',
    LOCKED: '锁定',
    DELETED: '已删除',
};
const USER_STATUS_FILTERS = [
    { value: '', label: '全部状态' },
    { value: 'ACTIVE', label: '启用' },
    { value: 'INACTIVE', label: '停用' },
    { value: 'DISABLED', label: '禁用' },
    { value: 'LOCKED', label: '锁定' },
    { value: 'DELETED', label: '已删除' },
];
const PHONE_PATTERN = /^1[3-9][0-9]{9}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// --- 子组件：添加/编辑用户对话框 (UserFormDialog) ---
function UserFormDialog({
    open,
    onClose,
    onSubmit,
    initialData,
    orgUnits,
    departments,
    isEditMode,
    canUpdateDetails,
    canUpdateStatus,
    passwordPolicy,
    serverErrors,
    isLoading,
}) {
    const [formData, setFormData] = useState({
        username: '',
        name: '',
        phone: '',
        email: '',
        password: '',
        status: 'ACTIVE',
        orgUnitId: '',
        departmentId: '',
    });
    const [errors, setErrors] = useState({}); // 前端校验错误

    useEffect(() => {
        if (open) {
            const defaultOrgUnitId = orgUnits.find(orgUnit => orgUnit.tenantRoot)?.id || orgUnits[0]?.id || '';
            const defaultData = {
                username: '',
                name: '',
                phone: '',
                email: '',
                password: '',
                status: 'ACTIVE',
                orgUnitId: defaultOrgUnitId,
                departmentId: '',
            };
            // 编辑模式下，密码字段不预填充，但保留以便用户可以输入新密码（如果表单设计如此）
            // 但当前后端 UserUpdateRequestDTO 不支持密码，所以这里 password 字段主要用于新建
            if (initialData) {
                const selectedOrgUnitId = initialData.orgUnitId || defaultOrgUnitId;
                setFormData({
                    ...defaultData,
                    ...initialData,
                    password: '',
                    orgUnitId: selectedOrgUnitId,
                });
            } else {
                setFormData(defaultData);
            }
            setErrors({});
        }
    }, [initialData, open, orgUnits]);

    useEffect(() => {
        if (serverErrors) {
            const newErrors = {};
            if (serverErrors.errors && typeof serverErrors.errors === 'object') {
                for (const field in serverErrors.errors) {
                    newErrors[field] = serverErrors.errors[field];
                }
            } else if (serverErrors.message) {
                const knownFields = ['username', 'name', 'phone', 'email', 'password', 'status', 'orgUnitId', 'departmentId'];
                let isFieldSpecificError = false;
                if (serverErrors.field) {
                    newErrors[serverErrors.field] = serverErrors.message;
                    isFieldSpecificError = true;
                }
                // 如果有 message 但不是特定字段错误, 或者 serverErrors 就是一个简单的 {message: "..."} 对象
                if (!isFieldSpecificError && !Object.keys(serverErrors).some(k => knownFields.includes(k) && k !== 'message')) {
                    newErrors.general = serverErrors.message;
                } else if (serverErrors.message && !isFieldSpecificError && !newErrors.general) {
                    newErrors.general = serverErrors.message;
                }
            } else if (typeof serverErrors === 'string') {
                newErrors.general = serverErrors;
            }
            setErrors(newErrors);
        } else {
            setErrors({});
        }
    }, [serverErrors]);


    const updateField = (name, value) => {
        setFormData(prev => ({
            ...prev,
            [name]: value,
            ...(name === 'orgUnitId' ? { departmentId: '' } : {}),
        }));
        if (errors[name]) setErrors(prev => ({ ...prev, [name]: null }));
        if (errors.general) setErrors(prev => ({ ...prev, general: null }));
    };
    const handleChange = event => updateField(event.target.name, event.target.value);

    const validate = () => {
        const newErrors = {};
        if (!isEditMode && !formData.username.trim()) newErrors.username = "用户名不能为空";
        if ((!isEditMode || canUpdateDetails) && !formData.name.trim()) newErrors.name = "姓名不能为空";
        if ((!isEditMode || canUpdateDetails) && !PHONE_PATTERN.test(formData.phone.trim())) newErrors.phone = "请输入11位中国大陆手机号";
        if ((!isEditMode || canUpdateDetails) && formData.email.trim() && !EMAIL_PATTERN.test(formData.email.trim())) newErrors.email = "请输入正确的邮箱地址";
        if (!isEditMode) {
            if (!formData.password) newErrors.password = "密码不能为空";
            else if (!evaluatePassword(formData.password, formData, passwordPolicy).valid) newErrors.password = "密码不符合安全策略";
        }
        if ((!isEditMode || canUpdateDetails) && !formData.orgUnitId) newErrors.orgUnitId = "请选择所属组织";
        if ((!isEditMode || canUpdateStatus) && !formData.status) newErrors.status = "请选择用户状态";
        setErrors(prev => ({...prev, ...newErrors})); // 合并新旧错误，以防 general 错误被覆盖
        return Object.keys(newErrors).filter(k => newErrors[k] !== null).length === 0; // 只计算实际有错误的字段
    };

    const handleSubmitDialog = (e) => {
        e.preventDefault();
        setErrors(prev => ({
            general: null,
            username: prev.username,
            name: prev.name,
            phone: prev.phone,
            email: prev.email,
            password: prev.password,
            status: prev.status,
            orgUnitId: prev.orgUnitId,
            departmentId: prev.departmentId,
        }));
        if (validate()) {
            const dataToSubmit = {
                name: formData.name,
                phone: formData.phone.trim(),
                email: formData.email.trim() || null,
                status: formData.status,
                orgUnitId: formData.orgUnitId,
                departmentId: formData.departmentId || null,
            };
            if (!isEditMode) {
                dataToSubmit.username = formData.username;
                dataToSubmit.password = formData.password;
            }
            onSubmit(dataToSubmit);
        }
    };

    return (
        <Dialog open={open} onClose={isLoading ? () => {} : onClose} maxWidth="sm" fullWidth>
            <DialogTitle>{isEditMode ? `编辑用户: ${initialData?.username || ''}` : '添加新用户'}</DialogTitle>
            <DialogContent>
                {errors.general && <Alert severity="error" sx={{ mb: 2 }}>{errors.general}</Alert>}
                {!isEditMode && (
                    <TextField
                        autoFocus
                        margin="dense"
                        name="username"
                        label="用户名"
                        type="text"
                        fullWidth
                        variant="outlined"
                        value={formData.username}
                        onChange={handleChange}
                        error={!!errors.username}
                        helperText={errors.username}
                        required
                        disabled={isLoading}
                    />
                )}
                <SearchableSelectField label="所属组织" margin="dense" value={formData.orgUnitId ? String(formData.orgUnitId) : ''}
                    onChange={value => updateField('orgUnitId', value)} disabled={isLoading || (isEditMode && !canUpdateDetails)}
                    error={!!errors.orgUnitId} helperText={errors.orgUnitId}
                    options={orgUnits.map(orgUnit => ({ value: String(orgUnit.id), label: orgUnit.name }))} />
                <SearchableSelectField label="所属部门（可选）" margin="dense" value={formData.departmentId || ''}
                    onChange={value => updateField('departmentId', value)} disabled={isLoading || isEditMode}
                    options={[
                        { value: '', label: '组织直属' },
                        ...departments.filter(department => department.orgUnitId === formData.orgUnitId && department.status === 'ACTIVE')
                            .map(department => ({ value: department.id, label: department.name })),
                    ]} />
                <TextField
                    margin="dense"
                    name="name"
                    label="姓名/昵称"
                    type="text"
                    fullWidth
                    variant="outlined"
                    value={formData.name}
                    onChange={handleChange}
                    error={!!errors.name}
                    helperText={errors.name}
                    required
                    sx={{ mt: (isEditMode && !errors.general) ? 1 : (!isEditMode ? 1: 0) }}
                    autoFocus={isEditMode || !!errors.general || !!errors.name}
                    disabled={isLoading || (isEditMode && !canUpdateDetails)}
                />
                <TextField
                    margin="dense"
                    name="phone"
                    label="手机号"
                    type="tel"
                    fullWidth
                    value={formData.phone}
                    onChange={handleChange}
                    error={!!errors.phone}
                    helperText={errors.phone}
                    required
                    slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 11 } }}
                    disabled={isLoading || (isEditMode && !canUpdateDetails)}
                />
                <TextField
                    margin="dense"
                    name="email"
                    label="邮箱"
                    type="email"
                    fullWidth
                    value={formData.email}
                    onChange={handleChange}
                    error={!!errors.email}
                    helperText={errors.email || '可选；邮件通知优先使用此地址'}
                    disabled={isLoading || (isEditMode && !canUpdateDetails)}
                />
                {!isEditMode && (
                    <Box sx={{ mt: 1, display: 'grid', gap: 1 }}>
                        <TextField
                            name="password"
                            label="初始密码"
                            type="password"
                            fullWidth
                            variant="outlined"
                            value={formData.password}
                            onChange={handleChange}
                            error={!!errors.password}
                            helperText={errors.password}
                            slotProps={{ htmlInput: { minLength: passwordPolicy.minLength, maxLength: passwordPolicy.maxLength } }}
                            required
                            disabled={isLoading}
                        />
                        <PasswordPolicyHint password={formData.password} context={formData} policy={passwordPolicy} />
                    </Box>
                )}
                <FormControl fullWidth margin="dense" error={!!errors.status} disabled={isLoading || (isEditMode && !canUpdateStatus)}>
                    <InputLabel id="status-select-label">状态</InputLabel>
                    <Select
                        labelId="status-select-label"
                        name="status"
                        value={formData.status}
                        label="状态"
                        onChange={handleChange}
                    >
                        {USER_STATUSES.map((status) => (
                            <MenuItem key={status} value={status}>{USER_STATUS_LABELS[status] || status}</MenuItem>
                        ))}
                    </Select>
                    {errors.status && <FormHelperText>{errors.status}</FormHelperText>}
                </FormControl>
            </DialogContent>
            <DialogActions sx={{ position: 'relative' }}>
                <Button onClick={onClose} disabled={isLoading}>取消</Button>
                <Button onClick={handleSubmitDialog} variant="contained" disabled={isLoading}>
                    {isLoading && <CircularProgress size={20} color="inherit" sx={{ mr: 1 }} />}
                    {isEditMode ? '保存更改' : '创建用户'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

function formatFileSize(bytes) {
    const value = Number(bytes || 0);
    if (!Number.isFinite(value) || value <= 0) return '未知';
    if (value < 1024 * 1024) return `${Math.max(1, Math.round(value / 1024))} KiB`;
    return `${(value / 1024 / 1024).toFixed(1)} MiB`;
}

function UserExportPreviewDialog({ open, preview, loading, error, exporting, onClose, onConfirm }) {
    const filterLabels = exportFilterLabels(preview?.filters);
    return (
        <Dialog open={open} onClose={loading || exporting ? undefined : onClose} maxWidth="md" fullWidth>
            <DialogTitle>确认导出范围</DialogTitle>
            <DialogContent>
                {loading ? (
                    <Box sx={{ py: 4 }}>
                        <LinearProgress />
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>正在核对可见数据范围...</Typography>
                    </Box>
                ) : null}
                {error ? <Alert severity="error" sx={{ mb: 1.5 }}>{error}</Alert> : null}
                {preview ? (
                    <Stack spacing={1.5}>
                        <Box>
                            <Typography variant="overline" color="text.secondary">预计导出用户</Typography>
                            <Typography variant="h5" component="p" sx={{ fontWeight: 800 }}>{preview.userCount} 人</Typography>
                            <Typography variant="caption" color="text.secondary">单次上限 {preview.maximumUserCount} 人</Typography>
                        </Box>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                            <Paper variant="outlined" sx={{ p: 1.25, flex: 1 }}>
                                <Typography variant="caption" color="text.secondary">授权来源</Typography>
                                <Typography variant="h6">{preview.assignmentCount} 条</Typography>
                            </Paper>
                            <Paper variant="outlined" sx={{ p: 1.25, flex: 1 }}>
                                <Typography variant="caption" color="text.secondary">原子权限</Typography>
                                <Typography variant="h6">{preview.grantCount} 条</Typography>
                            </Paper>
                            <Paper variant="outlined" sx={{ p: 1.25, flex: 1 }}>
                                <Typography variant="caption" color="text.secondary">预计文件</Typography>
                                <Typography variant="h6">{formatFileSize(preview.estimatedFileSize)}</Typography>
                            </Paper>
                        </Stack>
                        <Box>
                            <Typography variant="subtitle2" sx={{ mb: 0.75 }}>筛选范围</Typography>
                            <Stack direction="row" spacing={0.75} useFlexGap sx={{ flexWrap: "wrap" }}>
                                {filterLabels.length
                                    ? filterLabels.map(label => <Chip key={label} size="small" variant="outlined" label={label} />)
                                    : <Chip size="small" variant="outlined" label="全部可见用户" />}
                            </Stack>
                        </Box>
                        <Box>
                            <Typography variant="subtitle2" sx={{ mb: 0.75 }}>文件内容</Typography>
                            <Stack spacing={0.75}>
                                {preview.sheets.map(sheet => (
                                    <Paper key={sheet.name} variant="outlined" sx={{ p: 1.25 }}>
                                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={0.5} sx={{ justifyContent: "space-between" }}>
                                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{sheet.name}</Typography>
                                            <Typography variant="caption" color="text.secondary">{sheet.fields.length} 个字段</Typography>
                                        </Stack>
                                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5, overflowWrap: 'anywhere' }}>
                                            {sheet.fields.join('、')}
                                        </Typography>
                                    </Paper>
                                ))}
                            </Stack>
                        </Box>
                        {preview.userCount === 0 ? <Alert severity="warning">当前筛选范围没有可导出的用户。</Alert> : null}
                        <Alert severity="info">
                            文件仅包含当前账号有权查看和管理的数据，不包含密码、验证码或会话凭据。预检结果将在
                            {preview.previewExpiresAt ? ` ${formatDateTime(preview.previewExpiresAt)} ` : '短时间内'}失效。
                        </Alert>
                    </Stack>
                ) : null}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={loading || exporting}>取消</Button>
                <Button variant="contained" onClick={onConfirm} disabled={loading || exporting || !preview || preview.userCount === 0} loading={exporting}>
                    生成并下载
                </Button>
            </DialogActions>
        </Dialog>
    );
}

// --- 主页面组件 AdminUserManagementPage ---
function AdminUserManagementPage() {
    const navigate = useNavigate();
    const { hasPermission, passwordPolicy } = useAuth();
    const canView = hasPermission(PERMISSIONS.USER_MANAGE_VIEW);
    const canExport = hasPermission(PERMISSIONS.USER_EXPORT_CREATE);
    const canViewExportTasks = hasPermission(PERMISSIONS.USER_EXPORT_TASK_VIEW);
    const canCancelExport = hasPermission(PERMISSIONS.USER_EXPORT_CANCEL);
    const canDownloadExport = hasPermission(PERMISSIONS.USER_EXPORT_DOWNLOAD);
    const canCreate = hasPermission(PERMISSIONS.USER_MANAGE_CREATE);
    const canUpdateDetails = hasPermission(PERMISSIONS.USER_MANAGE_UPDATE_DETAILS);
    const canUpdateStatus = [
        PERMISSIONS.USER_MANAGE_STATUS_ACTIVATE,
        PERMISSIONS.USER_MANAGE_STATUS_DEACTIVATE,
        PERMISSIONS.USER_MANAGE_STATUS_LOCK,
        PERMISSIONS.USER_MANAGE_STATUS_UNLOCK,
    ].some(hasPermission);
    const canResetPassword = hasPermission(PERMISSIONS.USER_MANAGE_RESET_PASSWORD);
    const canDelete = hasPermission(PERMISSIONS.USER_MANAGE_DELETE);
    const canRestore = hasPermission(PERMISSIONS.USER_MANAGE_RESTORE);
    const canPreviewImport = hasPermission(PERMISSIONS.USER_IMPORT_PREVIEW);
    const canViewImportTasks = hasPermission(PERMISSIONS.USER_IMPORT_TASK_VIEW);
    const canImport = canView && (canPreviewImport || canViewImportTasks);
    const [users, setUsers] = useState([]);
    const [isLoading, setIsLoading] = useState(false); // 用于表格数据加载
    const [isSubmitting, setIsSubmitting] = useState(false); // 用于表单提交或行操作
    const [pageError, setPageError] = useState(null);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [searchTerm, setSearchTerm] = useState('');
    const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('');
    const [orgUnitFilter, setOrgUnitFilter] = useState('');
    const [departmentFilter, setDepartmentFilter] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [orgUnits, setOrgUnits] = useState([]);
    const [departments, setDepartments] = useState([]);
    const [orgUnitsLoadError, setOrgUnitsLoadError] = useState(null);
    const [importDialogOpen, setImportDialogOpen] = useState(false);
    const [isExporting, setIsExporting] = useState(false);
    const [exportProgress, setExportProgress] = useState(null);
    const [exportPreviewOpen, setExportPreviewOpen] = useState(false);
    const [exportPreview, setExportPreview] = useState(null);
    const [exportPreviewLoading, setExportPreviewLoading] = useState(false);
    const [exportPreviewError, setExportPreviewError] = useState('');
    const [exportRequest, setExportRequest] = useState(null);
    const [exportTasksOpen, setExportTasksOpen] = useState(false);
    const exportControllerRef = useRef(null);
    const activeExportJobRef = useRef(null);
    const exportPreviewControllerRef = useRef(null);

    const [openUserDialog, setOpenUserDialog] = useState(false);
    const [actionsAnchor, setActionsAnchor] = useState(null);
    const [userDialogKey, setUserDialogKey] = useState(0);
    const [userFormServerErrors, setUserFormServerErrors] = useState(null);

    const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' });

    const [openResetPasswordDialog, setOpenResetPasswordDialog] = useState(false);
    const [userForPasswordReset, setUserForPasswordReset] = useState(null);
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [passwordResetError, setPasswordResetError] = useState('');
    const [isResettingPassword, setIsResettingPassword] = useState(false); // 单独的加载状态

    const [confirmDeleteDialogOpen, setConfirmDeleteDialogOpen] = useState(false);
    const [userToDelete, setUserToDelete] = useState(null);
    const [isDeletingUser, setIsDeletingUser] = useState(false); // 单独的加载状态

    useEffect(() => {
        const handler = setTimeout(() => {
            setPage(0);
            setDebouncedSearchTerm(searchTerm);
        }, 500);
        return () => clearTimeout(handler);
    }, [searchTerm]);

    const fetchUsers = useCallback(async (currentPage, currentSearch, currentOrgUnit, currentDepartment, currentStatus) => {
        if (!canView) return;
        setIsLoading(true);
        setPageError(null);
        try {
            const params = {
                page: currentPage,
                size: 10,
                sort: 'username,asc',
                searchTerm: currentSearch || undefined,
                orgUnitId: currentOrgUnit || undefined,
                departmentId: currentDepartment || undefined,
                status: currentStatus || undefined,
                includeDeleted: currentStatus === 'DELETED',
            };
            const response = await apiClient.get('/admin/users', { params });
            if (response.data && response.data.content) {
                setUsers(response.data.content);
                setTotalPages(response.data.totalPages);
                setTotalElements(response.data.totalElements);
                setPage(response.data.number);
            } else {
                setUsers([]);
                setTotalPages(0);
                setTotalElements(0);
            }
        } catch (err) {
            console.error("Failed to fetch users:", err);
            setPageError(formatApiErrorMessage(err, "加载用户列表失败，请稍后重试。"));
        } finally {
            setIsLoading(false);
        }
    }, [canView]);

    const fetchOrgUnits = useCallback(async () => {
        setOrgUnitsLoadError(null);
        try {
            const [organizationResponse, departmentResponse] = await Promise.all([
                apiClient.get('/admin/org-units'),
                apiClient.get('/admin/departments'),
            ]);
            setOrgUnits(organizationResponse.data || []);
            setDepartments(departmentResponse.data || []);
        } catch (err) {
            console.error("Failed to fetch organization units:", err);
            setOrgUnitsLoadError(formatApiErrorMessage(err, "加载组织目录失败，请稍后重试。"));
            setOrgUnits([]);
            setDepartments([]);
        }
    }, []);

    useEffect(() => {
        fetchUsers(page, debouncedSearchTerm, orgUnitFilter, departmentFilter, statusFilter);
    }, [page, debouncedSearchTerm, fetchUsers, orgUnitFilter, departmentFilter, statusFilter]);

    useEffect(() => {
        if (canView) {
            fetchOrgUnits();
        }
    }, [canView, fetchOrgUnits]);

    useEffect(() => () => {
        exportControllerRef.current?.abort();
        exportPreviewControllerRef.current?.abort();
    }, []);

    const handlePageChange = (event, value) => {
        setPage(value - 1);
    };

    const handleSearchInputChange = (event) => {
        setSearchTerm(event.target.value);
    };

    const clearFilters = () => {
        setOrgUnitFilter('');
        setDepartmentFilter('');
        setStatusFilter('');
        setPage(0);
    };

    const openExportPreview = async () => {
        exportPreviewControllerRef.current?.abort();
        const controller = new AbortController();
        exportPreviewControllerRef.current = controller;
        const request = buildUserExportRequest({
            searchTerm: debouncedSearchTerm,
            orgUnitId: orgUnitFilter,
            departmentId: departmentFilter,
            status: statusFilter,
        });
        setExportRequest(request);
        setExportPreviewOpen(true);
        setExportPreview(null);
        setExportPreviewError('');
        setExportPreviewLoading(true);
        try {
            setExportPreview(await previewUserTransferExport(request, controller.signal));
        } catch (error) {
            if (!controller.signal.aborted) {
                setExportPreviewError(formatApiErrorMessage(error, '读取导出范围失败，请稍后重试。'));
            }
        } finally {
            if (exportPreviewControllerRef.current === controller) exportPreviewControllerRef.current = null;
            if (!controller.signal.aborted) setExportPreviewLoading(false);
        }
    };

    const closeExportPreview = () => {
        exportPreviewControllerRef.current?.abort();
        exportPreviewControllerRef.current = null;
        setExportPreviewOpen(false);
        setExportPreviewLoading(false);
        setExportPreviewError('');
        setExportPreview(null);
        setExportRequest(null);
    };

    const handleExportUsers = async () => {
        if (!exportRequest || !exportPreview) return;
        const controller = new AbortController();
        exportControllerRef.current = controller;
        setExportPreviewOpen(false);
        setIsExporting(true);
        setExportProgress(null);
        try {
            let job = await createUserTransferExport(
                    bindUserExportPreview(exportRequest, exportPreview), controller.signal);
            activeExportJobRef.current = job;
            while (['QUEUED', 'EXECUTING'].includes(job?.status)) {
                await new Promise((resolve, reject) => {
                    const timeoutId = globalThis.setTimeout(resolve, 800);
                    controller.signal.addEventListener('abort', () => {
                        globalThis.clearTimeout(timeoutId);
                        reject(new DOMException('操作已停止。', 'AbortError'));
                    }, { once: true });
                });
                job = await getUserTransferJob(job.id, controller.signal);
                activeExportJobRef.current = job;
            }
            if (job?.status !== 'COMPLETED') {
                throw new Error(job?.failureMessage || '用户与岗位授权导出任务失败。');
            }
            const exportFile = job.resultFile;
            if (!exportFile?.downloadUrl || !exportFile?.sha256) {
                throw new Error('服务器未返回完整的导出文件信息。');
            }
            await downloadFileInChunks(exportFile.downloadUrl, exportFile.fileName, {
                expectedSha256: exportFile.sha256,
                signal: controller.signal,
                concurrency: 4,
                onProgress: setExportProgress,
            });
            const exportedCount = Number(job.succeededRows);
            setSnackbar({
                open: true,
                severity: 'success',
                message: Number.isFinite(exportedCount) ? `已导出 ${exportedCount} 个用户及其原子权限授权。` : '用户与授权已导出。',
            });
        } catch (error) {
            setSnackbar({
                open: true,
                severity: controller.signal.aborted ? 'info' : 'error',
                message: controller.signal.aborted
                    ? '用户数据导出已停止。'
                    : formatApiErrorMessage(error, error?.message || '导出用户与授权失败，请稍后重试。'),
            });
        } finally {
            if (exportControllerRef.current === controller) exportControllerRef.current = null;
            activeExportJobRef.current = null;
            setIsExporting(false);
            setExportProgress(null);
            setExportRequest(null);
            setExportPreview(null);
        }
    };

    const stopExportUsers = async () => {
        const activeJob = activeExportJobRef.current;
        exportControllerRef.current?.abort();
        if (activeJob?.status === 'QUEUED') {
            try {
                await cancelUserTransfer(activeJob.id);
            } catch {
                // 任务可能已经开始执行；任务中心会显示服务端最终状态。
            }
        }
        setExportTasksOpen(true);
    };

    const handleOpenAddUserDialog = () => {
        setUserFormServerErrors(null);
        setUserDialogKey(prev => prev + 1);
        setOpenUserDialog(true);
    };

    const handleCloseUserDialog = () => {
        if (isSubmitting) return; // 防止在提交时关闭
        setOpenUserDialog(false);
        setUserFormServerErrors(null);
    };

    const handleOpenUserDetail = (user) => {
        navigate('/admin/user-management/' + user.id);
    };

    const handleUserFormSubmit = async (formDataFromDialog) => {
        setIsSubmitting(true);
        setUserFormServerErrors(null);
        try {
            const response = await apiClient.post('/admin/users', formDataFromDialog);
            setOpenUserDialog(false);
            setUserFormServerErrors(null);
            setSnackbar({ open: true, message: `用户 ${formDataFromDialog.username} 已创建，请继续配置岗位授权。`, severity: 'success' });
            navigate(`/admin/user-management/${response.data.id}`);
        } catch (err) {
            console.error("Failed to save user:", err.response?.data || err);
            const responseData = err.response?.data;
            const errorData = responseData && typeof responseData === 'object' ? { ...responseData } : {};
            if (!errorData.message) {
                errorData.message = formatApiErrorMessage(err, "保存用户失败，请稍后重试。");
            }
            setUserFormServerErrors(errorData);
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleToggleUserStatus = async (userToToggle) => {
        if (userToToggle.status === 'DELETED' || userToToggle.systemProtected) return;
        setIsSubmitting(true);
        const newStatus = userToToggle.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
        try {
            await apiClient.put(`/admin/users/${userToToggle.id}/status?status=${newStatus}`);
            setSnackbar({ open: true, message: `用户 ${userToToggle.username} 状态已更新为 ${newStatus}`, severity: 'success' });
            setUsers(prevUsers => prevUsers.map(u => u.id === userToToggle.id ? {...u, status: newStatus} : u));
        } catch (err) {
            console.error("Failed to update user status:", err);
            setSnackbar({ open: true, message: `状态更新失败：${formatApiErrorMessage(err, "用户状态更新失败，请稍后重试。")}`, severity: 'error' });
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleOpenResetPasswordDialog = (user) => {
        if (user.status === 'DELETED' || user.systemProtected) return;
        setUserForPasswordReset(user);
        setNewPassword('');
        setConfirmPassword('');
        setPasswordResetError('');
        setOpenResetPasswordDialog(true);
    };

    const handleCloseResetPasswordDialog = () => {
        if (isResettingPassword) return;
        setOpenResetPasswordDialog(false);
        setUserForPasswordReset(null);
    };

    const handlePasswordResetSubmit = async () => {
        if (!evaluatePassword(newPassword, userForPasswordReset || {}, passwordPolicy).valid) {
            setPasswordResetError("新密码不符合安全策略，请按提示调整。");
            return;
        }
        if (!passwordsMatch(newPassword, confirmPassword)) {
            setPasswordResetError("两次输入的新密码不一致。");
            return;
        }
        setPasswordResetError('');
        setIsResettingPassword(true);
        try {
            await apiClient.post(`/admin/users/${userForPasswordReset.id}/reset-password`, { newPassword, confirmPassword });
            setSnackbar({ open: true, message: `用户 ${userForPasswordReset.username} 的密码已重置。`, severity: 'success' });
            handleCloseResetPasswordDialog();
        } catch (err) {
            console.error("Failed to reset password:", err);
            setPasswordResetError(formatApiErrorMessage(err, "密码重置失败，请稍后重试。"));
        } finally {
            setIsResettingPassword(false);
        }
    };

    const handleOpenDeleteDialog = (user) => {
        if (user.systemProtected || user.status === 'DELETED') return;
        setUserToDelete(user);
        setConfirmDeleteDialogOpen(true);
    };

    const handleCloseDeleteDialog = () => {
        if (isDeletingUser) return;
        setConfirmDeleteDialogOpen(false);
        setUserToDelete(null);
    };

    const handleConfirmDelete = async () => {
        if (userToDelete) {
            setIsDeletingUser(true);
            try {
                await apiClient.delete(`/admin/users/${userToDelete.id}`);
                setSnackbar({ open: true, message: `用户 ${userToDelete.username} 已被软删除。`, severity: 'success' });
                fetchUsers(page, debouncedSearchTerm, orgUnitFilter, departmentFilter, statusFilter);
            } catch (err) {
                console.error("Failed to delete user:", err);
                setSnackbar({ open: true, message: `删除用户失败：${formatApiErrorMessage(err, "删除用户失败，请稍后重试。")}`, severity: 'error' });
            } finally {
                handleCloseDeleteDialog();
                setIsDeletingUser(false);
            }
        }
    };

    const handleRestoreUser = async (userId, username) => {
        // 可以创建一个MUI Dialog来确认，这里简化处理
        if (!window.confirm(`确定恢复用户“${username}”吗？恢复后该用户将处于启用状态。`)) {
            return;
        }
        setIsSubmitting(true); // 可以用一个更特定的 state，如 isRestoringUser
        try {
            await apiClient.patch(`/admin/users/${userId}/restore`);
            setSnackbar({ open: true, message: `用户 ${username} 已成功恢复。`, severity: 'success' });
            fetchUsers(page, debouncedSearchTerm, orgUnitFilter, departmentFilter, statusFilter);
        } catch (err) {
            console.error("Failed to restore user:", err);
            setSnackbar({ open: true, message: `恢复用户失败：${formatApiErrorMessage(err, "恢复用户失败，请稍后重试。")}`, severity: 'error' });
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleSnackbarClose = (event, reason) => {
        if (reason === 'clickaway') return;
        setSnackbar(prev => ({ ...prev, open: false }));
    };

    const getStatusChipColor = (status) => {
        switch (status) {
            case 'ACTIVE': return 'success';
            case 'INACTIVE': return 'default';
            case 'DISABLED': return 'warning';
            case 'LOCKED': return 'error';
            case 'DELETED': return 'error';
            default: return 'default';
        }
    };

    const renderUserActions = (user) => (
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', justifyContent: 'flex-end', '& .MuiIconButton-root': { width: { xs: 48, md: 40 }, height: { xs: 48, md: 40 } } }}>
                                            {user.status === 'DELETED' ? (
                                                canRestore ? <Tooltip title="恢复用户"><span>
                                                    <IconButton size="small" color="success" onClick={() => handleRestoreUser(user.id, user.username)} disabled={isSubmitting}>
                                                        <RestoreFromTrashIcon fontSize="inherit" />
                                                    </IconButton>
                                                </span></Tooltip> : null
                                            ) : (
                                                <>
                                                    <Tooltip title="查看用户详情"><span>
                                                        <IconButton size="small" onClick={() => handleOpenUserDetail(user)} disabled={isSubmitting}>
                                                            <VisibilityIcon fontSize="inherit" />
                                                        </IconButton>
                                                    </span></Tooltip>
                                                    {canUpdateStatus && <Tooltip title={user.status === 'ACTIVE' ? "禁用账户" : "激活账户"}><span>
                                                        <IconButton size="small" onClick={() => handleToggleUserStatus(user)} disabled={isSubmitting || user.systemProtected}>
                                                            {user.status === 'ACTIVE' ? <ToggleOffIcon fontSize="inherit" color="action"/> : <ToggleOnIcon fontSize="inherit" color="success"/>}
                                                        </IconButton>
                                                    </span></Tooltip>}
                                                    {canResetPassword && <Tooltip title="重置密码"><span>
                                                        <IconButton size="small" onClick={() => handleOpenResetPasswordDialog(user)} disabled={isSubmitting || user.systemProtected}>
                                                            <LockResetIcon fontSize="inherit" />
                                                        </IconButton>
                                                    </span></Tooltip>}
                                                    {canDelete && <Tooltip title="删除用户（可恢复）"><span>
                                                        <IconButton size="small" color="error" onClick={() => handleOpenDeleteDialog(user)} disabled={isSubmitting || user.systemProtected}>
                                                            <DeleteIcon fontSize="inherit" />
                                                        </IconButton>
                                                    </span></Tooltip>}
                                                </>
                                            )}
        </Box>
    );

    if (!canView) {
        return <Container maxWidth={false} disableGutters><Alert severity="warning">当前账号没有用户查看权限。</Alert></Container>;
    }

    return (
        <Container maxWidth={false} disableGutters sx={{ mt: 0, mb: 2 }}>
            <Paper elevation={1} sx={{ p: { xs: 1.5, md: 2 } }}>
                <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, gap: 1.5, justifyContent: 'space-between', alignItems: { xs: 'stretch', sm: 'center' }, mb: 1.5 }}>
                    <PageTitle>用户管理</PageTitle>
                    <Box sx={{ display: { xs: 'none', md: 'flex' }, gap: 1, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                        {(canExport || canViewExportTasks) && (
                            <>
                                {canExport && (
                                <Button
                                    variant="outlined"
                                    startIcon={isExporting ? <CircularProgress size={18} /> : <DownloadIcon />}
                                    onClick={openExportPreview}
                                    disabled={isSubmitting || isLoading || isExporting || totalElements === 0}
                                    sx={{ minWidth: 154 }}
                                >
                                    {isExporting
                                        ? (Number.isFinite(exportProgress?.percent) ? `下载中 ${exportProgress.percent}%` : '后台生成中')
                                        : '导出用户与授权'}
                                </Button>
                                )}
                                {canViewExportTasks && (
                                    <Button
                                        variant="text"
                                        startIcon={<HistoryIcon />}
                                        onClick={() => setExportTasksOpen(true)}
                                        disabled={isSubmitting || isLoading}
                                    >
                                        导出任务
                                    </Button>
                                )}
                                {canExport && isExporting && (
                                    <Tooltip title="停止导出">
                                        <IconButton color="error" onClick={stopExportUsers} aria-label="停止导出">
                                            <CancelIcon />
                                        </IconButton>
                                    </Tooltip>
                                )}
                            </>
                        )}
                        {canImport && (
                            <Button variant="outlined" startIcon={<UploadFileIcon />} onClick={() => setImportDialogOpen(true)} disabled={isSubmitting || isLoading}>
                                批量治理
                            </Button>
                        )}
                        {canCreate && (
                            <Button variant="contained" startIcon={<AddIcon />} onClick={handleOpenAddUserDialog} disabled={isSubmitting || isLoading || orgUnits.length === 0}>
                                添加用户
                            </Button>
                        )}
                    </Box>
                    <Box sx={{ display: { xs: 'flex', md: 'none' }, gap: 1, flexWrap: 'wrap' }}>
                        {canCreate && <Button variant="contained" startIcon={<AddIcon />} onClick={handleOpenAddUserDialog}
                            disabled={isSubmitting || isLoading || orgUnits.length === 0} sx={{ flex: 1 }}>添加用户</Button>}
                        {(canExport || canViewExportTasks || canImport) && <Button variant="outlined" startIcon={<MoreHorizIcon />}
                            onClick={event => setActionsAnchor(event.currentTarget)} aria-haspopup="menu" aria-expanded={Boolean(actionsAnchor)}
                            sx={{ flex: 1 }}>{isExporting ? '导出进行中' : '更多操作'}</Button>}
                    </Box>
                    <Menu anchorEl={actionsAnchor} open={Boolean(actionsAnchor)} onClose={() => setActionsAnchor(null)}>
                        {canExport && <MenuItem disabled={isSubmitting || isLoading || isExporting || totalElements === 0}
                            onClick={() => { setActionsAnchor(null); openExportPreview(); }}>导出用户与授权</MenuItem>}
                        {canViewExportTasks && <MenuItem disabled={isSubmitting || isLoading}
                            onClick={() => { setActionsAnchor(null); setExportTasksOpen(true); }}>导出任务</MenuItem>}
                        {canImport && <MenuItem disabled={isSubmitting || isLoading}
                            onClick={() => { setActionsAnchor(null); setImportDialogOpen(true); }}>批量治理</MenuItem>}
                        {canExport && isExporting && <MenuItem sx={{ color: 'error.main' }}
                            onClick={() => { setActionsAnchor(null); stopExportUsers(); }}>停止导出</MenuItem>}
                    </Menu>
                </Box>

                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'minmax(260px, 2fr) repeat(3, minmax(150px, 1fr)) auto' }, alignItems: 'center', mb: 1.5, gap: 1 }}>
                    <SearchInput
                        label="搜索用户" placeholder="账号、姓名、拼音/首字母、手机号或邮箱" variant="outlined" size="small"
                        value={searchTerm} onChange={handleSearchInputChange}
                        onClear={() => setSearchTerm('')}
                        sx={{ minWidth: 0, gridColumn: { xs: '1 / -1', md: 'auto' } }} autoComplete="off"
                        disabled={isLoading || isSubmitting}
                    />
                    <SearchableSelectField label="所属组织" value={orgUnitFilter} disabled={isLoading || isSubmitting}
                        onChange={value => { setOrgUnitFilter(value); setDepartmentFilter(''); setPage(0); }}
                        options={[{ value: '', label: '全部组织' }, ...orgUnits.map(org => ({ value: org.id, label: org.name }))]} />
                    <SearchableSelectField label="所属部门" value={departmentFilter} disabled={isLoading || isSubmitting}
                        onChange={value => { setDepartmentFilter(value); setPage(0); }}
                        options={[
                            { value: '', label: '全部部门' },
                            ...departments.filter(department => !orgUnitFilter || department.orgUnitId === orgUnitFilter)
                                .map(department => ({ value: department.id, label: department.name })),
                        ]} />
                    <FormControl size="small" fullWidth disabled={isLoading || isSubmitting}>
                        <InputLabel id="user-status-filter-label">账号状态</InputLabel>
                        <Select
                            labelId="user-status-filter-label"
                            label="账号状态"
                            value={statusFilter}
                            onChange={event => { setStatusFilter(event.target.value); setPage(0); }}
                        >
                            {USER_STATUS_FILTERS.map(option => <MenuItem key={option.value || 'all'} value={option.value}>{option.label}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <Button startIcon={<FilterAltOffIcon />} onClick={clearFilters}
                        disabled={isLoading || isSubmitting || (!orgUnitFilter && !departmentFilter && !statusFilter)}>清除筛选</Button>
                </Box>

                {(isLoading && users.length === 0) && ( // 修改为仅当用户列表为空时显示全屏加载
                    <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}><CircularProgress /><Typography sx={{ ml: 2 }}>加载中...</Typography></Box>
                )}
                {pageError && <Alert severity="error" sx={{ mb: 2 }}>{pageError}</Alert>}
                {orgUnitsLoadError && <Alert severity="warning" sx={{ mb: 2 }}>{orgUnitsLoadError}</Alert>}
                {!isLoading && !pageError && users.length === 0 && (
                    <Typography sx={{ textAlign: 'center', p: 3, color: 'text.secondary' }}>
                        未找到符合当前条件的用户。
                    </Typography>
                )}

                <ListResultsBar summary={`共 ${totalElements} 个用户`}>
                    {totalPages > 1 && <ListPagination count={totalPages} page={page + 1} onChange={handlePageChange} disabled={isLoading} />}
                </ListResultsBar>

                <Box sx={{ display: { xs: 'grid', md: 'none' }, gap: 1.5 }}>
                    {users.map(user => (
                        <Paper key={user.id} component="article" variant="outlined" sx={{ p: 1.5, minWidth: 0, opacity: user.status === 'DELETED' ? 0.6 : 1 }}>
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, mb: 1 }}>
                                <Box sx={{ minWidth: 0 }}>
                                    <Typography sx={{ fontWeight: 800, overflowWrap: 'anywhere' }}>{user.name || user.username}</Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{user.username}</Typography>
                                </Box>
                                <Chip label={USER_STATUS_LABELS[user.status] || user.status} color={getStatusChipColor(user.status)} size="small" />
                            </Box>
                            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1 }}>
                                {[
                                    ['所属组织', user.orgUnitName || '-'], ['所属部门', user.departmentName || '组织直属'],
                                    ['手机号', user.phone || '未补录'], ['邮箱', user.email || '未补录'],
                                    ['授权版本', `v${user.authorizationVersion ?? 0}`], ['创建时间', formatDate(user.createdAt, 'zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })],
                                ].map(([label, value]) => <Box key={label} sx={{ minWidth: 0 }}>
                                    <Typography variant="caption" color="text.secondary">{label}</Typography>
                                    <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{value}</Typography>
                                </Box>)}
                            </Box>
                            <Box sx={{ mt: 1 }}>{renderUserActions(user)}</Box>
                        </Paper>
                    ))}
                </Box>

                {users.length > 0 && (
                    <ViewportTableContainer sx={{ display: { xs: 'none', md: 'block' } }}>
                        <Table size="small" stickyHeader>
                            <TableHead>
                                <TableRow sx={{ '& th': { fontWeight: 'bold' } }}>
                                    <TableCell>用户名</TableCell><TableCell>姓名</TableCell><TableCell>联系方式</TableCell>
                                    <TableCell>所属组织</TableCell><TableCell>授权版本</TableCell><TableCell>状态</TableCell><TableCell>创建时间</TableCell>
                                    <TableCell align="center">操作</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {users.map((user) => (
                                    <TableRow hover key={user.id} sx={{ opacity: user.status === 'DELETED' ? 0.6 : 1, backgroundColor: user.status === 'DELETED' ? 'action.disabledBackground' : 'inherit' }}>
                                        <TableCell>{user.username}</TableCell>
                                        <TableCell>{user.name}</TableCell>
                                        <TableCell>
                                            <Typography variant="body2">{user.phone || '未补录'}</Typography>
                                            {user.email && <Typography variant="caption" color="text.secondary">{user.email}</Typography>}
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2">{user.orgUnitName || '-'}</Typography>
                                            <Typography variant="caption" color="text.secondary">{user.departmentName || '组织直属'}</Typography>
                                        </TableCell>
                                        <TableCell>v{user.authorizationVersion ?? 0}</TableCell>
                                        <TableCell><Chip label={USER_STATUS_LABELS[user.status] || user.status} color={getStatusChipColor(user.status)} size="small" variant={user.status === 'DELETED' ? "outlined" : "filled"}/></TableCell>
                                        <TableCell>{formatDate(user.createdAt, 'zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })}</TableCell>
                                        <TableCell align="center">
                                            {renderUserActions(user)}
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </ViewportTableContainer>
                )}

            </Paper>

            <UserFormDialog
                key={`user-dialog-${userDialogKey}`}
                open={openUserDialog}
                onClose={handleCloseUserDialog}
                onSubmit={handleUserFormSubmit}
                initialData={null}
                orgUnits={orgUnits}
                departments={departments}
                isEditMode={false}
                canUpdateDetails={canUpdateDetails}
                canUpdateStatus={canUpdateStatus}
                passwordPolicy={passwordPolicy}
                serverErrors={userFormServerErrors}
                isLoading={isSubmitting} // Pass submitting state to dialog
            />

            <UserTransferDialog
                open={importDialogOpen}
                onClose={() => setImportDialogOpen(false)}
                orgUnits={orgUnits}
                canViewTasks={canViewImportTasks}
                canDownloadTemplate={hasPermission(PERMISSIONS.USER_IMPORT_TEMPLATE_DOWNLOAD)}
                canPreviewImport={canPreviewImport}
                canExecuteImport={hasPermission(PERMISSIONS.USER_IMPORT_EXECUTE)}
                canCancelImport={hasPermission(PERMISSIONS.USER_IMPORT_CANCEL)}
                canDownloadErrorReport={hasPermission(PERMISSIONS.USER_IMPORT_ERROR_REPORT_DOWNLOAD)}
                onCompleted={(result) => {
                    setImportDialogOpen(false);
                    setPage(0);
                    fetchUsers(0, debouncedSearchTerm, orgUnitFilter, departmentFilter, statusFilter);
                    setSnackbar({
                        open: true,
                        severity: 'success',
                        message: `用户批量治理完成：成功执行 ${result.succeededRows} 条命令。`,
                    });
                }}
            />

            <UserExportTaskDialog
                open={exportTasksOpen}
                onClose={() => setExportTasksOpen(false)}
                canCreateExport={canExport}
                canCancelExport={canCancelExport}
                canDownloadExport={canDownloadExport}
                onNewExport={() => {
                    setExportTasksOpen(false);
                    openExportPreview();
                }}
            />

            <UserExportPreviewDialog
                open={exportPreviewOpen}
                preview={exportPreview}
                loading={exportPreviewLoading}
                error={exportPreviewError}
                exporting={isExporting}
                onClose={closeExportPreview}
                onConfirm={handleExportUsers}
            />

            <Dialog open={openResetPasswordDialog} onClose={handleCloseResetPasswordDialog} maxWidth="xs" fullWidth>
                <DialogTitle>重置用户密码：{userForPasswordReset?.username}</DialogTitle>
                <DialogContent>
                    {passwordResetError && <Alert severity="error" sx={{mb:2}}>{passwordResetError}</Alert>}
                    <Box sx={{ mt: 1, display: 'grid', gap: 1.5 }}>
                        <TextField
                            autoFocus name="newPassword" label="新密码" type="password" fullWidth variant="outlined"
                            value={newPassword} onChange={(e) => { setNewPassword(e.target.value); setPasswordResetError('');}}
                            error={!!passwordResetError}
                            required slotProps={{ htmlInput: { minLength: passwordPolicy.minLength, maxLength: passwordPolicy.maxLength } }}
                            disabled={isResettingPassword}
                        />
                        <PasswordPolicyHint password={newPassword} context={userForPasswordReset || {}} policy={passwordPolicy} />
                        <TextField
                            name="confirmPassword" label="确认新密码" type="password" fullWidth variant="outlined"
                            value={confirmPassword} onChange={(e) => { setConfirmPassword(e.target.value); setPasswordResetError(''); }}
                            error={confirmPassword.length > 0 && !passwordsMatch(newPassword, confirmPassword)}
                            helperText={confirmPassword.length > 0 && !passwordsMatch(newPassword, confirmPassword) ? '两次输入的新密码不一致' : '请再次输入新密码'}
                            required slotProps={{ htmlInput: { minLength: passwordPolicy.minLength, maxLength: passwordPolicy.maxLength } }}
                            disabled={isResettingPassword}
                        />
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleCloseResetPasswordDialog} disabled={isResettingPassword}>取消</Button>
                    <Button onClick={handlePasswordResetSubmit} variant="contained" disabled={isResettingPassword || !passwordsMatch(newPassword, confirmPassword)}>
                        {isResettingPassword ? <CircularProgress size={20} color="inherit" sx={{mr:1}} /> : null}
                        重置密码
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog open={confirmDeleteDialogOpen} onClose={handleCloseDeleteDialog} maxWidth="xs" fullWidth>
                <DialogTitle>确认删除用户</DialogTitle>
                <DialogContent><DialogContentText>确定删除用户 <strong>“{userToDelete?.username}”</strong> 吗？<br />删除后该用户将无法登录，后续可由管理员恢复。</DialogContentText></DialogContent>
                <DialogActions>
                    <Button onClick={handleCloseDeleteDialog} disabled={isDeletingUser}>取消</Button>
                    <Button onClick={handleConfirmDelete} color="error" variant="contained" disabled={isDeletingUser}>
                        {isDeletingUser ? <CircularProgress size={20} color="inherit" sx={{mr:1}} /> : null}
                        确认删除
                    </Button>
                </DialogActions>
            </Dialog>

            <Snackbar open={snackbar.open} autoHideDuration={6000} onClose={handleSnackbarClose} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
                <Alert onClose={handleSnackbarClose} severity={snackbar.severity} sx={{ width: '100%' }} variant="filled">
                    {snackbar.message}
                </Alert>
            </Snackbar>
        </Container>
    );
}

export default AdminUserManagementPage;

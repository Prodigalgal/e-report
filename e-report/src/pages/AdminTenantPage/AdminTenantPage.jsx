import PageTitle from '../../components/layout/PageTitle';
import React, { useCallback, useEffect, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from '@mui/material';
import AddBusinessIcon from '@mui/icons-material/AddBusiness';
import PauseCircleOutlinedIcon from '@mui/icons-material/PauseCircleOutlined';
import PlayCircleOutlinedIcon from '@mui/icons-material/PlayCircleOutlined';

import apiClient from '../../services/apiClient';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDateTime } from '../../utils/formatDate.js';
import PasswordPolicyHint from '../../components/Auth/PasswordPolicyHint.jsx';
import { evaluatePassword } from '../../utils/passwordPolicy.js';
import { useAuth } from '../../hooks/useAuth';

const emptyForm = {
    tenantGroupId: '',
    code: '',
    name: '',
    rootOrgCode: '',
    rootOrgName: '',
    adminUsername: '',
    adminPassword: '',
    adminName: '市公司管理员',
    adminPhone: '',
    adminEmail: '',
};

function AdminTenantPage() {
    const { passwordPolicy } = useAuth();
    const [tenants, setTenants] = useState([]);
    const [groups, setGroups] = useState([]);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [form, setForm] = useState(emptyForm);
    const [error, setError] = useState('');

    const loadData = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const [tenantResponse, groupResponse] = await Promise.all([
                apiClient.get('/admin/tenants'),
                apiClient.get('/admin/tenants/groups'),
            ]);
            setTenants(tenantResponse.data || []);
            setGroups(groupResponse.data || []);
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '加载市公司租户目录失败。'));
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { loadData(); }, [loadData]);

    const openCreate = () => {
        const activeGroup = groups.find(group => group.status === 'ACTIVE');
        setForm({ ...emptyForm, tenantGroupId: activeGroup ? String(activeGroup.id) : '' });
        setDialogOpen(true);
    };

    const submit = async () => {
        setSubmitting(true);
        setError('');
        try {
            await apiClient.post('/admin/tenants', {
                ...form,
                tenantGroupId: form.tenantGroupId,
                code: form.code.trim().toUpperCase(),
                name: form.name.trim(),
                rootOrgCode: form.rootOrgCode.trim().toUpperCase(),
                rootOrgName: form.rootOrgName.trim(),
                adminUsername: form.adminUsername.trim(),
                adminName: form.adminName.trim(),
                adminPhone: form.adminPhone.trim(),
                adminEmail: form.adminEmail.trim() || null,
            });
            setDialogOpen(false);
            await loadData();
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '创建市公司租户失败。'));
        } finally {
            setSubmitting(false);
        }
    };

    const updateStatus = async (tenant) => {
        const nextStatus = tenant.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
        if (!window.confirm(`确认${nextStatus === 'ACTIVE' ? '启用' : '停用'}市公司“${tenant.name}”吗？`)) return;
        setSubmitting(true);
        setError('');
        try {
            await apiClient.patch(`/admin/tenants/${tenant.id}/status`, null, { params: { status: nextStatus } });
            await loadData();
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '更新市公司租户状态失败。'));
        } finally {
            setSubmitting(false);
        }
    };

    const canSubmit = form.tenantGroupId
        && form.code.trim()
        && form.name.trim()
        && form.rootOrgCode.trim()
        && form.rootOrgName.trim()
        && form.adminUsername.trim().length >= 3
        && evaluatePassword(form.adminPassword, {
            username: form.adminUsername,
            name: form.adminName,
            phone: form.adminPhone,
            email: form.adminEmail,
        }, passwordPolicy).valid
        && form.adminName.trim()
        && /^1[3-9][0-9]{9}$/.test(form.adminPhone.trim())
        && (!form.adminEmail.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.adminEmail.trim()));

    return (
        <Box sx={{ display: 'grid', gap: 1.5 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1 }}>
                <Box>
                    <PageTitle>市公司租户</PageTitle>
                    <Typography variant="body2" color="text.secondary">创建独立市公司账号域、组织根节点和内置岗位权限模板。</Typography>
                </Box>
                <Button variant="contained" startIcon={<AddBusinessIcon />} onClick={openCreate} disabled={loading || groups.length === 0}>新增市公司</Button>
            </Box>
            {error && <Alert severity="error" onClose={() => setError('')}>{error}</Alert>}
            <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1 }}>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>市公司</TableCell>
                            <TableCell>租户编码</TableCell>
                            <TableCell>省级租户组</TableCell>
                            <TableCell>状态</TableCell>
                            <TableCell>创建时间</TableCell>
                            <TableCell align="right">操作</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {loading && <TableRow><TableCell colSpan={6} align="center"><CircularProgress size={26} /></TableCell></TableRow>}
                        {!loading && tenants.map(tenant => (
                            <TableRow key={tenant.id} hover>
                                <TableCell sx={{ fontWeight: 700 }}>{tenant.name}</TableCell>
                                <TableCell>{tenant.code}</TableCell>
                                <TableCell>{groups.find(group => group.id === tenant.tenantGroupId)?.name || '-'}</TableCell>
                                <TableCell>{tenant.status === 'ACTIVE' ? '启用' : '停用'}</TableCell>
                                <TableCell>{formatDateTime(tenant.createdAt)}</TableCell>
                                <TableCell align="right">
                                    <Button
                                        size="small"
                                        color={tenant.status === 'ACTIVE' ? 'warning' : 'success'}
                                        startIcon={tenant.status === 'ACTIVE' ? <PauseCircleOutlinedIcon /> : <PlayCircleOutlinedIcon />}
                                        onClick={() => updateStatus(tenant)}
                                        disabled={submitting || tenant.defaultTenant}
                                    >
                                        {tenant.status === 'ACTIVE' ? '停用' : '启用'}
                                    </Button>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>

            <Dialog open={dialogOpen} onClose={submitting ? undefined : () => setDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>新增市公司租户</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={2}>
                        <FormControl fullWidth required>
                            <InputLabel>省级租户组</InputLabel>
                            <Select value={form.tenantGroupId} label="省级租户组" onChange={(event) => setForm(current => ({ ...current, tenantGroupId: event.target.value }))}>
                                {groups.filter(group => group.status === 'ACTIVE').map(group => <MenuItem key={group.id} value={String(group.id)}>{group.name}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <TextField label="租户编码" value={form.code} onChange={(event) => setForm(current => ({ ...current, code: event.target.value }))} required helperText="系统内部唯一编码，例如 ZZ_CITY" />
                        <TextField label="市公司名称" value={form.name} onChange={(event) => setForm(current => ({ ...current, name: event.target.value }))} required />
                        <TextField label="根组织编码" value={form.rootOrgCode} onChange={(event) => setForm(current => ({ ...current, rootOrgCode: event.target.value }))} required helperText="该市公司行政区划或组织编码，例如 3506" />
                        <TextField label="根组织名称" value={form.rootOrgName} onChange={(event) => setForm(current => ({ ...current, rootOrgName: event.target.value }))} required />
                        <TextField label="初始管理员用户名" value={form.adminUsername} onChange={(event) => setForm(current => ({ ...current, adminUsername: event.target.value }))} required />
                        <TextField label="初始管理员姓名" value={form.adminName} onChange={(event) => setForm(current => ({ ...current, adminName: event.target.value }))} required />
                        <TextField label="初始管理员手机号" type="tel" value={form.adminPhone} onChange={(event) => setForm(current => ({ ...current, adminPhone: event.target.value }))} required slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 11 } }} />
                        <TextField label="初始管理员邮箱" type="email" value={form.adminEmail} onChange={(event) => setForm(current => ({ ...current, adminEmail: event.target.value }))} helperText="可选；邮件通知优先使用此地址" />
                        <TextField label="初始管理员密码" type="password" value={form.adminPassword} onChange={(event) => setForm(current => ({ ...current, adminPassword: event.target.value }))} required slotProps={{ htmlInput: { minLength: passwordPolicy.minLength, maxLength: passwordPolicy.maxLength } }} />
                        <PasswordPolicyHint password={form.adminPassword} context={{
                            username: form.adminUsername,
                            name: form.adminName,
                            phone: form.adminPhone,
                            email: form.adminEmail,
                        }} policy={passwordPolicy} />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDialogOpen(false)} disabled={submitting}>取消</Button>
                    <Button variant="contained" onClick={submit} disabled={!canSubmit || submitting}>创建</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}

export default AdminTenantPage;

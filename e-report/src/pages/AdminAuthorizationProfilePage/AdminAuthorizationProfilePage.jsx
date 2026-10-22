import PageHeading from '../../components/layout/PageHeading';
import SearchableSelectField from '../../components/SearchInput/SearchableSelectField';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Alert, Box, Button, Chip, CircularProgress, Container, Dialog, DialogActions,
    DialogContent, DialogTitle, Divider, Grid, InputAdornment,
    List, ListItemButton, ListItemText, Paper,
    Snackbar, Stack, TextField, Typography, MenuItem,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import EditIcon from '@mui/icons-material/Edit';
import PublishIcon from '@mui/icons-material/Publish';
import SearchIcon from '@mui/icons-material/Search';
import StopCircleIcon from '@mui/icons-material/StopCircle';

import apiClient from '../../services/apiClient';
import { PERMISSIONS } from '../../constants/permissions';
import { useAuth } from '../../hooks/useAuth';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';

const RISK_LABELS = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '关键' };
const RISK_COLORS = { LOW: 'default', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'error' };

function profileVersion(profile) {
    return profile.draftVersion || profile.currentVersion;
}

function emptyForm() {
    return {
        code: '',
        displayName: '',
        description: '',
        changeSummary: '',
        rules: [],
    };
}

function formFromProfile(profile) {
    const version = profileVersion(profile);
    return {
        code: profile.code,
        displayName: profile.displayName,
        description: profile.description || '',
        changeSummary: version?.changeSummary || '',
        rules: (version?.rules || []).map(rule => ({ permissionName: rule.permissionName })),
    };
}

export default function AdminAuthorizationProfilePage() {
    const { hasPermission } = useAuth();
    const canView = hasPermission(PERMISSIONS.AUTHORIZATION_PROFILE_VIEW);
    const canCreate = hasPermission(PERMISSIONS.AUTHORIZATION_PROFILE_CREATE);
    const canEdit = hasPermission(PERMISSIONS.AUTHORIZATION_PROFILE_DRAFT_UPDATE);
    const canCopy = hasPermission(PERMISSIONS.AUTHORIZATION_PROFILE_COPY);
    const canPublish = hasPermission(PERMISSIONS.AUTHORIZATION_PROFILE_PUBLISH);
    const canRetire = hasPermission(PERMISSIONS.AUTHORIZATION_PROFILE_RETIRE);
    const [profiles, setProfiles] = useState([]);
    const [permissions, setPermissions] = useState([]);
    const [selectedId, setSelectedId] = useState('');
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [editor, setEditor] = useState(null);
    const [saving, setSaving] = useState(false);
    const [snackbar, setSnackbar] = useState({ open: false, message: '' });

    const load = useCallback(async () => {
        if (!canView) return;
        setLoading(true);
        setError('');
        try {
            const [profileResponse, permissionResponse] = await Promise.all([
                apiClient.get('/admin/authorization-profiles'),
                apiClient.get('/admin/permissions', { params: { page: 0, size: 500, sort: 'name,asc' } }),
            ]);
            const nextProfiles = profileResponse.data || [];
            setProfiles(nextProfiles);
            setPermissions(permissionResponse.data?.content || []);
            setSelectedId(current => nextProfiles.some(profile => profile.id === current)
                ? current : nextProfiles[0]?.id || '');
        } catch (loadError) {
            setError(formatApiErrorMessage(loadError, '岗位权限模板加载失败，请稍后重试。'));
        } finally {
            setLoading(false);
        }
    }, [canView]);

    useEffect(() => { load(); }, [load]);

    const filtered = useMemo(() => {
        const keyword = search.trim().toLowerCase();
        if (!keyword) return profiles;
        return profiles.filter(profile => [profile.displayName, profile.code, profile.description]
            .some(value => String(value || '').toLowerCase().includes(keyword)));
    }, [profiles, search]);
    const selected = profiles.find(profile => profile.id === selectedId) || null;

    const saveDraft = async form => {
        setSaving(true);
        setError('');
        try {
            const payload = {
                ...form,
                code: form.code.trim().toUpperCase(),
                displayName: form.displayName.trim(),
                description: form.description.trim() || null,
                changeSummary: form.changeSummary.trim() || null,
            };
            const response = editor.mode === 'create'
                ? await apiClient.post('/admin/authorization-profiles', payload)
                : await apiClient.put(`/admin/authorization-profiles/${editor.profileId}/draft`, payload);
            setEditor(null);
            setSelectedId(response.data.id);
            setSnackbar({ open: true, message: editor.mode === 'create' ? '岗位模板草稿已创建。' : '岗位模板草稿已保存。' });
            await load();
        } catch (saveError) {
            setError(formatApiErrorMessage(saveError, '保存岗位模板草稿失败。'));
        } finally {
            setSaving(false);
        }
    };

    const copyProfile = async form => {
        setSaving(true);
        setError('');
        try {
            const response = await apiClient.post(`/admin/authorization-profiles/${editor.profileId}/copy`, {
                code: form.code.trim().toUpperCase(),
                displayName: form.displayName.trim(),
            });
            setEditor(null);
            setSelectedId(response.data.id);
            setSnackbar({ open: true, message: '岗位模板已复制为新的草稿。' });
            await load();
        } catch (copyError) {
            setError(formatApiErrorMessage(copyError, '复制岗位模板失败。'));
        } finally {
            setSaving(false);
        }
    };

    const runAction = async (path, message) => {
        setSaving(true);
        setError('');
        try {
            await apiClient.post(path);
            setSnackbar({ open: true, message });
            await load();
        } catch (actionError) {
            setError(formatApiErrorMessage(actionError, '岗位模板操作失败。'));
        } finally {
            setSaving(false);
        }
    };

    if (!canView) {
        return <Container maxWidth={false} disableGutters><Alert severity="warning">当前账号没有查看岗位权限模板的权限。</Alert></Container>;
    }

    return (
        <Container maxWidth={false} disableGutters>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' }, mb: 2 }}>
                <Box sx={{ flex: 1 }}>
                    <PageHeading title="岗位权限模板" description="模板只是批量添加原子权限的快捷清单；应用后与用户授权完全解耦。" />
                </Box>
                {canCreate ? <Button startIcon={<AddIcon />} variant="contained" onClick={() => setEditor({ mode: 'create', form: emptyForm() })}>新建模板</Button> : null}
            </Stack>
            {error ? <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert> : null}
            {!loading && profiles.length === 0 ? (
                <Alert severity="warning" sx={{ mb: 2 }}>
                    当前账号没有可查看、可复制或可应用的岗位模板。岗位模板只展示其全部原子权限都处于当前账号
                    <code> grantOption=true </code>委派边界内的项目；请由上级补充对应转授权能力后重新登录。
                </Alert>
            ) : null}
            {loading ? <Box sx={{ minHeight: 280, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box> : (
                <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 4 }}>
                        <Paper sx={{ overflow: 'hidden' }}>
                            <Box sx={{ p: 1.5 }}>
                                <TextField
                                    fullWidth
                                    size="small"
                                    placeholder="搜索岗位模板"
                                    value={search}
                                    onChange={event => setSearch(event.target.value)}
                                    slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
                                />
                            </Box>
                            <Box sx={{ px: 1.5, pb: 1.5, display: { xs: 'block', md: 'none' } }}>
                                <SearchableSelectField label="岗位模板"
                                    value={filtered.some(profile => profile.id === selectedId) ? selectedId : ''}
                                    onChange={setSelectedId}
                                    options={filtered.map(profile => ({ value: profile.id, label: `${profile.displayName} · ${profile.active ? '启用' : '已退役'}` }))} />
                            </Box>
                            <Divider sx={{ display: { xs: 'none', md: 'block' } }} />
                            <List disablePadding sx={{ display: { xs: 'none', md: 'block' }, maxHeight: '65vh', overflowY: 'auto', overflowX: 'hidden' }}>
                                {filtered.map(profile => (
                                    <ListItemButton key={profile.id} selected={profile.id === selectedId} onClick={() => setSelectedId(profile.id)}>
                                        <ListItemText
                                            primary={profile.displayName}
                                            secondary={`${profile.code} · ${profile.active ? '启用' : '已退役'} · ${profile.draftVersion ? '有草稿' : `v${profile.currentVersion?.versionNo || '-'}`}`}
                                            slotProps={{ primary: { sx: { overflowWrap: 'anywhere' } }, secondary: { sx: { overflowWrap: 'anywhere' } } }}
                                        />
                                    </ListItemButton>
                                ))}
                                {filtered.length === 0 ? (
                                    <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
                                        {profiles.length === 0 ? '当前没有可委派的岗位模板。' : '没有匹配的岗位模板。'}
                                    </Typography>
                                ) : null}
                            </List>
                        </Paper>
                    </Grid>
                    <Grid size={{ xs: 12, md: 8 }}>
                        {selected ? <ProfileDetail
                            profile={selected}
                            saving={saving}
                            canEdit={canEdit}
                            canCopy={canCopy}
                            canPublish={canPublish}
                            canRetire={canRetire}
                            onEdit={() => setEditor({ mode: 'edit', profileId: selected.id, form: formFromProfile(selected) })}
                            onCopy={() => setEditor({ mode: 'copy', profileId: selected.id, form: { ...emptyForm(), code: `${selected.code}_COPY`, displayName: `${selected.displayName}副本` } })}
                            onPublish={() => runAction(`/admin/authorization-profiles/${selected.id}/publish`, '岗位模板新版本已发布；既有用户授权未自动变化。')}
                            onRetire={() => runAction(`/admin/authorization-profiles/${selected.id}/retire`, '岗位模板已退役；既有用户授权保持有效。')}
                        /> : <Paper sx={{ p: 3 }}><Typography color="text.secondary">请选择岗位模板。</Typography></Paper>}
                    </Grid>
                </Grid>
            )}
            <ProfileEditorDialog
                editor={editor}
                permissions={permissions}
                saving={saving}
                error={error}
                onClose={() => { if (!saving) setEditor(null); }}
                onSave={editor?.mode === 'copy' ? copyProfile : saveDraft}
            />
            <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar({ open: false, message: '' })}>
                <Alert severity="success" onClose={() => setSnackbar({ open: false, message: '' })}>{snackbar.message}</Alert>
            </Snackbar>
        </Container>
    );
}

function ProfileDetail({ profile, saving, canEdit, canCopy, canPublish, canRetire, onEdit, onCopy, onPublish, onRetire }) {
    const version = profileVersion(profile);
    return (
        <Paper sx={{ p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'flex-start' } }}>
                <Box sx={{ flex: 1 }}>
                    <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                        <Typography variant="h6" sx={{ fontWeight: 800 }}>{profile.displayName}</Typography>
                        {profile.builtIn ? <Chip size="small" label="内置" /> : <Chip size="small" color="info" label="自定义" />}
                        {!profile.active ? <Chip size="small" color="default" label="已退役" /> : null}
                        {profile.draftVersion ? <Chip size="small" color="warning" label={`草稿 v${profile.draftVersion.versionNo}`} /> : null}
                    </Stack>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, overflowWrap: 'anywhere' }}>{profile.code}</Typography>
                    <Typography variant="body2" sx={{ mt: 1 }}>{profile.description || '无描述'}</Typography>
                </Box>
                <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
                    {canCopy ? <Button size="small" startIcon={<ContentCopyIcon />} onClick={onCopy} disabled={saving}>复制</Button> : null}
                    {!profile.builtIn && canEdit ? <Button size="small" startIcon={<EditIcon />} onClick={onEdit} disabled={saving}>编辑草稿</Button> : null}
                    {!profile.builtIn && profile.draftVersion && canPublish ? <Button size="small" startIcon={<PublishIcon />} onClick={onPublish} disabled={saving}>发布</Button> : null}
                    {!profile.builtIn && profile.active && canRetire ? <Button size="small" color="error" startIcon={<StopCircleIcon />} onClick={onRetire} disabled={saving}>退役</Button> : null}
                </Stack>
            </Stack>
            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" sx={{ mb: 1 }}>当前展示版本：{version ? `v${version.versionNo} · ${version.status}` : '无'}</Typography>
            <Stack spacing={1}>
                {(version?.rules || []).map(rule => (
                    <Box key={rule.id || rule.permissionName} sx={{ py: 0.75, borderBottom: 1, borderColor: 'divider' }}>
                        <Box>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{rule.permissionDisplayName}</Typography>
                            <Typography variant="caption" color="text.secondary">{rule.permissionName}</Typography>
                        </Box>
                    </Box>
                ))}
                {!version?.rules?.length ? <Typography color="text.secondary">该岗位不包含业务权限。</Typography> : null}
            </Stack>
        </Paper>
    );
}

function ProfileEditorDialog({ editor, permissions, saving, error, onClose, onSave }) {
    const [form, setForm] = useState(emptyForm());
    const [permissionSearch, setPermissionSearch] = useState('');
    const [validationError, setValidationError] = useState('');
    useEffect(() => {
        if (!editor) return;
        setForm(editor.form);
        setPermissionSearch('');
        setValidationError('');
    }, [editor]);
    const filteredPermissions = useMemo(() => {
        const keyword = permissionSearch.trim().toLowerCase();
        if (!keyword) return permissions;
        return permissions.filter(permission => [permission.name, permission.displayName, permission.description]
            .some(value => String(value || '').toLowerCase().includes(keyword)));
    }, [permissionSearch, permissions]);
    const rulesByPermission = useMemo(() => new Map(form.rules.map(rule => [rule.permissionName, rule])), [form.rules]);
    const togglePermission = permission => {
        const existing = rulesByPermission.get(permission.name);
        setForm(current => ({
            ...current,
            rules: existing
                ? current.rules.filter(rule => rule.permissionName !== permission.name)
                : [...current.rules, { permissionName: permission.name }],
        }));
    };
    const submit = () => {
        if (!form.code.trim() || !/^[A-Z0-9_]+$/i.test(form.code.trim())) return setValidationError('模板编码只能包含字母、数字和下划线。');
        if (!form.displayName.trim()) return setValidationError('请填写岗位模板名称。');
        setValidationError('');
        onSave(form);
    };
    return (
        <Dialog open={Boolean(editor)} onClose={saving ? undefined : onClose} fullWidth maxWidth="lg">
            <DialogTitle>{editor?.mode === 'create' ? '新建岗位模板草稿' : editor?.mode === 'copy' ? '复制岗位模板' : '编辑岗位模板草稿'}</DialogTitle>
            <DialogContent dividers>
                {error || validationError ? <Alert severity="error" sx={{ mb: 2 }}>{validationError || error}</Alert> : null}
                <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 6 }}><TextField fullWidth label="模板编码" value={form.code} disabled={saving} onChange={event => setForm(current => ({ ...current, code: event.target.value.toUpperCase() }))} /></Grid>
                    <Grid size={{ xs: 12, sm: 6 }}><TextField fullWidth label="模板名称" value={form.displayName} disabled={saving} onChange={event => setForm(current => ({ ...current, displayName: event.target.value }))} /></Grid>
                    {editor?.mode !== 'copy' ? (
                        <>
                            <Grid size={{ xs: 12 }}><TextField fullWidth multiline minRows={2} label="职责说明" value={form.description} disabled={saving} onChange={event => setForm(current => ({ ...current, description: event.target.value }))} /></Grid>
                            <Grid size={{ xs: 12 }}><TextField fullWidth label="版本变更说明" value={form.changeSummary} disabled={saving} onChange={event => setForm(current => ({ ...current, changeSummary: event.target.value }))} /></Grid>
                            <Grid size={{ xs: 12 }}>
                                <TextField
                                    fullWidth
                                    size="small"
                                    placeholder="搜索原子权限"
                                    value={permissionSearch}
                                    onChange={event => setPermissionSearch(event.target.value)}
                                    slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
                                />
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                                <Box sx={{ maxHeight: 360, overflowY: 'auto', border: 1, borderColor: 'divider', borderRadius: 1 }}>
                                    {filteredPermissions.map(permission => {
                                        const rule = rulesByPermission.get(permission.name);
                                        return (
                                            <Box key={permission.id} sx={{ p: 1.25, borderBottom: 1, borderColor: 'divider', bgcolor: rule ? 'action.selected' : 'transparent' }}>
                                                <Box onClick={() => togglePermission(permission)} sx={{ cursor: 'pointer' }}>
                                                    <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                                                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{permission.displayName}</Typography>
                                                        <Chip size="small" color={RISK_COLORS[permission.riskLevel] || 'default'} label={`${RISK_LABELS[permission.riskLevel] || permission.riskLevel}风险`} />
                                                        {rule ? <Chip size="small" color="success" label="已选择" /> : null}
                                                    </Stack>
                                                    <Typography variant="caption" color="text.secondary">{permission.name}</Typography>
                                                </Box>
                                            </Box>
                                        );
                                    })}
                                </Box>
                            </Grid>
                        </>
                    ) : null}
                </Grid>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={saving}>取消</Button>
                <Button variant="contained" onClick={submit} disabled={saving} loading={saving}>{saving ? '保存中...' : editor?.mode === 'copy' ? '创建副本' : '保存草稿'}</Button>
            </DialogActions>
        </Dialog>
    );
}

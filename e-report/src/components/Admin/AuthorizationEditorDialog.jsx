import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Alert, Autocomplete, Box, Button, Chip, CircularProgress, Dialog, DialogActions,
    DialogContent, DialogTitle, Divider, FormControlLabel, Paper, Stack, Step, StepLabel,
    Stepper, Switch, TextField, Typography, useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import AddModeratorOutlinedIcon from '@mui/icons-material/AddModeratorOutlined';
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined';

import apiClient from '../../services/apiClient';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDateTime, formatDateTimeInput, parseDateTimeInput } from '../../utils/formatDate.js';
import {
    AUTHORIZATION_DIFF_FIELD_LABELS,
    AUTHORIZATION_DIFF_KIND_META,
    buildAuthorizationDiffRows,
    filterAuthorizationGrants,
    filterPermissionOptions,
    formatAuthorizationDiffValue,
} from './authorizationEditorWorkflow';

const STEPS = ['编辑权限清单', '确认变更'];
const OPERATION_LABELS = {
    APPLY_TEMPLATE: '追加岗位模板',
    GRANT_PERMISSION: '追加单条权限',
    ADJUST_PERMISSION: '调整权限',
    REVOKE_PERMISSION: '撤销权限',
    REATTACH_PERMISSION: '恢复授权链',
};
const STATUS_META = {
    ACTIVE: { label: '有效', color: 'success' },
    SUSPENDED_UPSTREAM: { label: '上游失效', color: 'warning' },
    REVOKED: { label: '已撤销', color: 'default' },
};
const ISSUER_LABELS = {
    USER: '人工授权',
    IMPORT: '批量导入',
    MIGRATION: '历史迁移',
    SYSTEM_ROOT: '系统根授权',
    SYSTEM_BASELINE: '组织基线',
    SYSTEM_SELF_BASELINE: '个人基础权限',
};

function toLocalDateTime(value) {
    return formatDateTimeInput(value);
}

function toApiDateTime(value) {
    return parseDateTimeInput(value);
}

function grantExpiryLabel(grant) {
    if (!grant.expiresAt) return '长期有效';
    const formatted = formatDateTime(grant.expiresAt);
    return formatted === '-' ? '有效期异常' : `有效至 ${formatted}`;
}

function createEmptyPermissionDraft() {
    return {
        permissionName: '',
        validFrom: '',
        expiresAt: '',
        grantOption: false,
        reason: '',
    };
}

function createEmptyTemplateDraft() {
    return { templateId: '', validFrom: '', expiresAt: '', grantOption: false, reason: '' };
}

function toApiOperation(queuedOperation) {
    const operation = { ...queuedOperation };
    delete operation.clientLabel;
    delete operation.clientPermissionNames;
    return operation;
}

export default function AuthorizationEditorDialog({ open, userId, onClose, onApplied }) {
    const theme = useTheme();
    const fullScreen = useMediaQuery(theme.breakpoints.down('md'));
    const [step, setStep] = useState(0);
    const [authorization, setAuthorization] = useState(null);
    const [options, setOptions] = useState(null);
    const [operations, setOperations] = useState([]);
    const [templateDraft, setTemplateDraft] = useState(createEmptyTemplateDraft);
    const [templateEditorOpen, setTemplateEditorOpen] = useState(false);
    const [permissionDraft, setPermissionDraft] = useState(createEmptyPermissionDraft);
    const [editingGrant, setEditingGrant] = useState(null);
    const [permissionEditorOpen, setPermissionEditorOpen] = useState(false);
    const [permissionSearch, setPermissionSearch] = useState('');
    const [reasonDialog, setReasonDialog] = useState(null);
    const [reasonDialogValue, setReasonDialogValue] = useState('');
    const [grantSearch, setGrantSearch] = useState('');
    const [plan, setPlan] = useState(null);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');

    const load = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const [authorizationResponse, optionsResponse] = await Promise.all([
                apiClient.get(`/admin/users/${userId}/authorization`),
                apiClient.get(`/admin/users/${userId}/authorization/options`),
            ]);
            setAuthorization(authorizationResponse.data);
            setOptions(optionsResponse.data);
        } catch (loadError) {
            setError(formatApiErrorMessage(loadError, '加载授权信息失败，请稍后重试。'));
        } finally {
            setLoading(false);
        }
    }, [userId]);

    useEffect(() => {
        if (!open) return;
        setStep(0);
        setOperations([]);
        setPlan(null);
        setTemplateDraft(createEmptyTemplateDraft());
        setTemplateEditorOpen(false);
        setPermissionDraft(createEmptyPermissionDraft());
        setEditingGrant(null);
        setPermissionEditorOpen(false);
        setPermissionSearch('');
        setReasonDialog(null);
        setReasonDialogValue('');
        setGrantSearch('');
        load();
    }, [load, open]);

    const templateById = useMemo(
        () => new Map((options?.templates || []).map(template => [template.id, template])),
        [options?.templates],
    );
    const permissionByName = useMemo(
        () => new Map((options?.permissions || []).map(permission => [permission.name, permission])),
        [options?.permissions],
    );
    const selectedTemplate = templateById.get(templateDraft.templateId) || null;
    const selectedPermission = permissionByName.get(permissionDraft.permissionName) || null;
    const visibleGrants = filterAuthorizationGrants(authorization?.grants, grantSearch);
    const filteredPermissions = filterPermissionOptions(options?.permissions, permissionSearch);
    const hasDelegablePermissions = (options?.permissions?.length || 0) > 0;
    const hasDelegableTemplates = (options?.templates?.length || 0) > 0;

    const stageTemplate = () => {
        if (!selectedTemplate) {
            setError('请选择岗位模板。');
            return;
        }
        setOperations(current => [...current, {
            type: 'APPLY_TEMPLATE',
            templateId: selectedTemplate.id,
            validFrom: toApiDateTime(templateDraft.validFrom),
            expiresAt: toApiDateTime(templateDraft.expiresAt),
            grantOption: templateDraft.grantOption,
            reason: templateDraft.reason.trim() || `应用岗位模板：${selectedTemplate.displayName}`,
            clientLabel: selectedTemplate.displayName,
            clientPermissionNames: selectedTemplate.permissionNames,
        }]);
        setTemplateDraft(createEmptyTemplateDraft());
        setTemplateEditorOpen(false);
        setError('');
    };

    const startAddPermission = () => {
        setEditingGrant(null);
        setPermissionDraft(createEmptyPermissionDraft());
        setPermissionSearch('');
        setPermissionEditorOpen(true);
        setError('');
    };

    const startAdjustPermission = grant => {
        setEditingGrant(grant);
        setPermissionDraft({
            permissionName: grant.permissionName,
            validFrom: toLocalDateTime(grant.validFrom),
            expiresAt: toLocalDateTime(grant.expiresAt),
            grantOption: grant.grantOption,
            reason: '',
        });
        setPermissionSearch('');
        setPermissionEditorOpen(true);
        setError('');
    };

    const stagePermission = () => {
        if (!selectedPermission) {
            setError('请选择原子权限。');
            return;
        }
        if (!permissionDraft.reason.trim()) {
            setError('单独授予或调整权限必须填写原因。');
            return;
        }
        setOperations(current => [...current, {
            type: editingGrant ? 'ADJUST_PERMISSION' : 'GRANT_PERMISSION',
            grantId: editingGrant?.id || null,
            permissionName: selectedPermission.name,
            validFrom: toApiDateTime(permissionDraft.validFrom),
            expiresAt: toApiDateTime(permissionDraft.expiresAt),
            grantOption: permissionDraft.grantOption,
            reason: permissionDraft.reason.trim(),
            clientLabel: selectedPermission.displayName,
        }]);
        setEditingGrant(null);
        setPermissionDraft(createEmptyPermissionDraft());
        setPermissionSearch('');
        setPermissionEditorOpen(false);
        setError('');
    };

    const startRevoke = grant => {
        setReasonDialog({ type: 'REVOKE_PERMISSION', grantId: grant.id, label: grant.permissionDisplayName });
        setReasonDialogValue('');
    };

    const startReattach = grant => {
        const parentCandidates = (options?.parentGrants || [])
            .filter(parent => parent.permissionName === grant.permissionName);
        setReasonDialog({
            type: 'REATTACH_PERMISSION',
            grantId: grant.id,
            parentGrantId: parentCandidates.length === 1 ? parentCandidates[0].grantId : '',
            parentCandidates,
            label: grant.permissionDisplayName,
        });
        setReasonDialogValue('');
    };

    const confirmReasonOperation = () => {
        const reason = reasonDialogValue.trim();
        if (!reason || !reasonDialog) return;
        if (reasonDialog.type === 'REATTACH_PERMISSION' && !reasonDialog.parentGrantId) return;
        setOperations(current => [...current, {
            type: reasonDialog.type,
            grantId: reasonDialog.grantId,
            parentGrantId: reasonDialog.parentGrantId || null,
            reason,
            clientLabel: reasonDialog.label,
        }]);
        setReasonDialog(null);
        setReasonDialogValue('');
        setError('');
    };

    const createPlan = async () => {
        if (operations.length === 0) {
            setError('请至少添加一项授权变更。');
            return;
        }
        setSaving(true);
        setError('');
        try {
            const response = await apiClient.post(`/admin/users/${userId}/authorization/plans`, {
                expectedAuthorizationVersion: authorization.authorizationVersion,
                operations: operations.map(toApiOperation),
            });
            setPlan(response.data);
            setStep(1);
        } catch (planError) {
            setError(formatApiErrorMessage(planError, '授权变更预检失败，请修正后重试。'));
        } finally {
            setSaving(false);
        }
    };

    const applyPlan = async () => {
        if (!plan?.planToken) return;
        setSaving(true);
        setError('');
        try {
            const idempotencyKey = globalThis.crypto?.randomUUID?.() || `auth-${Date.now()}-${Math.random()}`;
            const response = await apiClient.post(
                `/admin/users/${userId}/authorization/plans/${encodeURIComponent(plan.planToken)}/apply`,
                null,
                { headers: { 'Idempotency-Key': idempotencyKey } },
            );
            onApplied(response.data);
        } catch (applyError) {
            setError(formatApiErrorMessage(applyError, '授权变更执行失败，请刷新后重新预览。'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <>
            <Dialog
                open={open}
                onClose={saving ? undefined : onClose}
                fullScreen={fullScreen}
                fullWidth
                maxWidth="xl"
                slotProps={{ paper: { sx: { height: fullScreen ? '100%' : 'min(900px, 92vh)' } } }}
            >
                <DialogTitle>
                    <Typography variant="h6" component="div" sx={{ fontWeight: 750 }}>调整用户授权</Typography>
                </DialogTitle>
                <DialogContent dividers sx={{ px: { xs: 1.5, md: 2.5 } }}>
                    <Stepper activeStep={step} alternativeLabel sx={{ mb: 3 }}>
                        {STEPS.map(label => <Step key={label}><StepLabel>{label}</StepLabel></Step>)}
                    </Stepper>
                    {error ? <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert> : null}
                    {loading ? (
                        <Box sx={{ minHeight: 280, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>
                    ) : null}
                    {!loading && step === 0 ? (
                        <Stack spacing={2}>
                            {!hasDelegablePermissions && !hasDelegableTemplates ? (
                                <Alert severity="warning">
                                    当前账号具备授权页面访问权限，但没有任何 <code>grantOption=true</code> 的可转授权原子权限，
                                    因此不能向用户追加权限或岗位模板。请由上级先补充对应转授权能力。
                                </Alert>
                            ) : null}
                            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ alignItems: { md: 'center' } }}>
                                <TextField
                                    fullWidth size="small" label="搜索当前权限"
                                    value={grantSearch}
                                    onChange={event => setGrantSearch(event.target.value)}
                                    sx={{ flex: 1 }}
                                />
                                <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
                                    <Button variant="outlined" startIcon={<BadgeOutlinedIcon />} disabled={!hasDelegableTemplates} onClick={() => setTemplateEditorOpen(true)}>
                                        追加岗位模板
                                    </Button>
                                    <Button variant="outlined" startIcon={<AddModeratorOutlinedIcon />} disabled={!hasDelegablePermissions} onClick={startAddPermission}>
                                        追加单条权限
                                    </Button>
                                </Stack>
                            </Stack>
                            <StagedOperations
                                operations={operations}
                                permissionByName={permissionByName}
                                onRemove={index => setOperations(current => current.filter((_, itemIndex) => itemIndex !== index))}
                            />
                            {operations.length > 0 ? <Divider /> : null}
                            <AuthorizationGrantList
                                grants={visibleGrants}
                                onAdjust={startAdjustPermission}
                                onRevoke={startRevoke}
                                onReattach={startReattach}
                            />
                        </Stack>
                    ) : null}
                    {!loading && step === 1 ? (
                        <AuthorizationDiffPreview plan={plan} parentGrants={options?.parentGrants || []} />
                    ) : null}
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose} disabled={saving}>取消</Button>
                    {step === 1 ? (
                        <Button onClick={() => { setStep(0); setPlan(null); }} disabled={saving}>返回修改</Button>
                    ) : null}
                    {step === 0 ? (
                        <Button variant="contained" onClick={createPlan} disabled={saving || operations.length === 0}>
                            {saving ? '预检中…' : '预览变更'}
                        </Button>
                    ) : null}
                    {step === 1 ? (
                        <Button variant="contained" color="warning" onClick={applyPlan} disabled={saving}>
                            {saving ? '应用中…' : '应用变更'}
                        </Button>
                    ) : null}
                </DialogActions>
            </Dialog>
            <TemplateEditorDialog
                open={templateEditorOpen}
                templates={options?.templates || []}
                selectedTemplate={selectedTemplate}
                permissionByName={permissionByName}
                draft={templateDraft}
                onDraftChange={setTemplateDraft}
                onClose={() => setTemplateEditorOpen(false)}
                onConfirm={stageTemplate}
            />
            <PermissionEditorDialog
                open={permissionEditorOpen}
                editingGrant={editingGrant}
                permissions={filteredPermissions}
                selectedPermission={selectedPermission}
                permissionSearch={permissionSearch}
                onPermissionSearchChange={setPermissionSearch}
                draft={permissionDraft}
                onDraftChange={setPermissionDraft}
                onClose={() => {
                    setPermissionEditorOpen(false);
                    setEditingGrant(null);
                }}
                onConfirm={stagePermission}
            />
            <ReasonOperationDialog
                operation={reasonDialog}
                value={reasonDialogValue}
                onOperationChange={setReasonDialog}
                onChange={setReasonDialogValue}
                onClose={() => {
                    setReasonDialog(null);
                    setReasonDialogValue('');
                }}
                onConfirm={confirmReasonOperation}
            />
        </>
    );
}

function TemplateEditorDialog({
    open, templates, selectedTemplate, permissionByName, draft, onDraftChange, onClose, onConfirm,
}) {
    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
            <DialogTitle>追加岗位模板</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={2}>
                    <Alert severity="info">岗位模板只用于一次性批量追加原子权限；保存后不再与模板绑定。</Alert>
                    {templates.length === 0 ? (
                        <Alert severity="warning">当前账号没有能够完整委派的岗位模板。</Alert>
                    ) : null}
                    <Autocomplete
                        autoHighlight
                        options={templates}
                        value={selectedTemplate}
                        onChange={(_, template) => onDraftChange(current => ({ ...current, templateId: template?.id || '' }))}
                        getOptionLabel={template => `${template.displayName} · ${template.permissionNames.length} 项权限`}
                        isOptionEqualToValue={(option, value) => option.id === value.id}
                        renderInput={params => <TextField {...params} autoFocus size="small" label="搜索并选择岗位模板" />}
                    />
                    {selectedTemplate ? (
                        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
                            <Typography variant="subtitle2" sx={{ px: 1.5, py: 1, bgcolor: 'action.hover' }}>
                                将追加的权限（{selectedTemplate.permissionNames.length}）
                            </Typography>
                            <Stack divider={<Divider flexItem />} sx={{ maxHeight: 240, overflowY: 'auto' }}>
                                {selectedTemplate.permissionNames.map(permissionName => {
                                    const permission = permissionByName.get(permissionName);
                                    return (
                                        <Box key={permissionName} sx={{ px: 1.5, py: 1 }}>
                                            <Typography variant="body2" sx={{ fontWeight: 650 }}>
                                                {permission?.displayName || permissionName}
                                            </Typography>
                                            <Typography variant="caption" color="text.secondary">{permissionName}</Typography>
                                        </Box>
                                    );
                                })}
                            </Stack>
                        </Paper>
                    ) : null}
                    <ValidityFields draft={draft} onDraftChange={onDraftChange} />
                    <TextField
                        size="small" label="变更原因（选填）" multiline minRows={2}
                        value={draft.reason}
                        onChange={event => onDraftChange(current => ({ ...current, reason: event.target.value }))}
                    />
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button variant="contained" onClick={onConfirm} disabled={!selectedTemplate}>加入待变更清单</Button>
            </DialogActions>
        </Dialog>
    );
}

function PermissionEditorDialog({
    open, editingGrant, permissions, selectedPermission, permissionSearch,
    onPermissionSearchChange, draft, onDraftChange, onClose, onConfirm,
}) {
    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
            <DialogTitle>{editingGrant ? `调整权限 · ${editingGrant.permissionDisplayName}` : '追加单条权限'}</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={2}>
                    {permissions.length === 0 && !permissionSearch.trim() ? (
                        <Alert severity="warning">当前账号没有 <code>grantOption=true</code> 的可转授权原子权限。</Alert>
                    ) : null}
                    <Autocomplete
                        autoHighlight
                        options={permissions}
                        value={selectedPermission}
                        inputValue={permissionSearch}
                        onInputChange={(_, value) => onPermissionSearchChange(value)}
                        onChange={(_, permission) => onDraftChange(current => ({
                            ...current,
                            permissionName: permission?.name || '',
                        }))}
                        getOptionLabel={permission => `${permission.displayName} · ${permission.name}`}
                        isOptionEqualToValue={(option, value) => option.name === value.name}
                        renderInput={params => <TextField {...params} autoFocus size="small" label="搜索并选择原子权限" />}
                    />
                    {editingGrant ? (
                        <Alert severity="info">可以直接更换为另一项原子权限；预览会显示旧权限撤销与新权限增加。</Alert>
                    ) : null}
                    {selectedPermission ? (
                        <Paper variant="outlined" sx={{ p: 1.5 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{selectedPermission.displayName}</Typography>
                            <Typography variant="caption" color="text.secondary">{selectedPermission.description}</Typography>
                        </Paper>
                    ) : null}
                    <ValidityFields draft={draft} onDraftChange={onDraftChange} />
                    <TextField
                        size="small" label="变更原因" required multiline minRows={2}
                        value={draft.reason}
                        onChange={event => onDraftChange(current => ({ ...current, reason: event.target.value }))}
                    />
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button variant="contained" onClick={onConfirm} disabled={!selectedPermission || !draft.reason.trim()}>
                    {editingGrant ? '加入权限调整' : '加入待变更清单'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

function ValidityFields({ draft, onDraftChange }) {
    return (
        <Stack spacing={1.5}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                <TextField
                    fullWidth size="small" type="datetime-local" label="开始时间（留空为立即生效）"
                    value={draft.validFrom}
                    onChange={event => onDraftChange(current => ({ ...current, validFrom: event.target.value }))}
                    slotProps={{ inputLabel: { shrink: true } }}
                />
                <TextField
                    fullWidth size="small" type="datetime-local" label="结束时间（留空为长期有效）"
                    value={draft.expiresAt}
                    onChange={event => onDraftChange(current => ({ ...current, expiresAt: event.target.value }))}
                    slotProps={{ inputLabel: { shrink: true } }}
                />
            </Stack>
            <FormControlLabel
                control={<Switch
                    checked={draft.grantOption}
                    onChange={event => onDraftChange(current => ({ ...current, grantOption: event.target.checked }))}
                />}
                label="允许该用户继续转授此权限"
            />
        </Stack>
    );
}

function AuthorizationGrantList({ grants, onAdjust, onRevoke, onReattach }) {
    if (grants.length === 0) {
        return <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>没有匹配的当前权限。</Typography>;
    }
    return (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Typography variant="subtitle2" sx={{ px: 1.5, py: 1.25, bgcolor: 'action.hover', fontWeight: 750 }}>
                当前 Grant 清单（{grants.length}）
            </Typography>
            <Stack divider={<Divider flexItem />}>
                {grants.map(grant => {
                    const status = STATUS_META[grant.status] || { label: grant.status, color: 'default' };
                    return (
                        <Box
                            key={grant.id}
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: { xs: '1fr', md: 'minmax(250px, 1.4fr) minmax(170px, .8fr) minmax(160px, .8fr) auto' },
                                gap: 1.25,
                                alignItems: 'center',
                                px: 1.5,
                                py: 1.2,
                            }}
                        >
                            <Box sx={{ minWidth: 0 }}>
                                <Typography variant="body2" sx={{ fontWeight: 650 }}>{grant.permissionDisplayName}</Typography>
                                <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                                    {grant.permissionName}
                                </Typography>
                            </Box>
                            <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: "center", flexWrap: "wrap" }}>
                                <Chip size="small" color={status.color} variant="outlined" label={status.label} />
                                {grant.grantOption ? <Chip size="small" color="primary" variant="outlined" label="可转授" /> : null}
                            </Stack>
                            <Box>
                                <Typography variant="caption" sx={{ display: 'block' }}>{grantExpiryLabel(grant)}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                    {ISSUER_LABELS[grant.issuerType] || grant.issuerType}
                                </Typography>
                            </Box>
                            <Stack direction="row" spacing={0.5} sx={{ justifyContent: "flex-end" }}>
                                {grant.manageable && grant.status === 'ACTIVE' ? (
                                    <>
                                        <Button size="small" onClick={() => onAdjust(grant)}>调整</Button>
                                        <Button size="small" color="error" onClick={() => onRevoke(grant)}>撤销</Button>
                                    </>
                                ) : null}
                                {grant.manageable && grant.status === 'SUSPENDED_UPSTREAM' ? (
                                    <Button size="small" onClick={() => onReattach(grant)}>恢复</Button>
                                ) : null}
                                {!grant.manageable ? (
                                    <Chip size="small" variant="outlined" label={grant.manageabilityReason || '只读'} />
                                ) : null}
                            </Stack>
                        </Box>
                    );
                })}
            </Stack>
        </Paper>
    );
}

function ReasonOperationDialog({ operation, value, onOperationChange, onChange, onClose, onConfirm }) {
    if (!operation) return null;
    const reattach = operation.type === 'REATTACH_PERMISSION';
    return (
        <Dialog open fullWidth maxWidth="sm" onClose={onClose}>
            <DialogTitle>{reattach ? '恢复授权链' : '确认撤销权限'}</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={1.5}>
                    <Alert severity={reattach ? 'info' : 'warning'}>{operation.label}</Alert>
                    {reattach ? (
                        <Autocomplete
                            options={operation.parentCandidates}
                            value={operation.parentCandidates.find(parent => parent.grantId === operation.parentGrantId) || null}
                            onChange={(_, parent) => onOperationChange(current => ({
                                ...current,
                                parentGrantId: parent?.grantId || '',
                            }))}
                            getOptionLabel={parent => `${parent.permissionDisplayName} · ${grantExpiryLabel(parent)}`}
                            isOptionEqualToValue={(option, selected) => option.grantId === selected.grantId}
                            renderInput={params => <TextField {...params} label="选择新的上游授权" />}
                            noOptionsText="当前账号没有可用于恢复的上游授权"
                        />
                    ) : null}
                    <TextField
                        autoFocus={!reattach} required multiline minRows={3} label="变更原因"
                        value={value} onChange={event => onChange(event.target.value)}
                    />
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button
                    variant="contained"
                    color={reattach ? 'primary' : 'error'}
                    onClick={onConfirm}
                    disabled={!value.trim() || (reattach && !operation.parentGrantId)}
                >
                    加入待变更清单
                </Button>
            </DialogActions>
        </Dialog>
    );
}

function AuthorizationDiffPreview({ plan, parentGrants }) {
    const rows = buildAuthorizationDiffRows(plan);
    const counts = rows.reduce((result, row) => ({ ...result, [row.kind]: result[row.kind] + 1 }), {
        ADD: 0, UPDATE: 0, REMOVE: 0,
    });
    return (
        <Stack spacing={1.25}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
                <Typography variant="subtitle2" sx={{ flex: 1 }}>
                    共 {rows.length} 项变更：新增 {counts.ADD}、调整 {counts.UPDATE}、撤销 {counts.REMOVE}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                    应用后 {plan.resultingActiveGrantCount} 条有效权限
                </Typography>
            </Stack>
            {rows.map(row => {
                const kind = AUTHORIZATION_DIFF_KIND_META[row.kind];
                return (
                    <Paper key={row.key} variant="outlined" sx={{ overflow: 'hidden', borderLeft: 4, borderLeftColor: `${kind.color}.main` }}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: "center", px: 1.5, py: 1.1, bgcolor: 'action.hover' }}>
                            <Chip size="small" color={kind.color} label={kind.label} />
                            <Box sx={{ minWidth: 0 }}>
                                <Typography variant="body2" sx={{ fontWeight: 750 }}>{row.permissionDisplayName}</Typography>
                                <Typography variant="caption" color="text.secondary">{row.permissionName}</Typography>
                            </Box>
                        </Stack>
                        <Stack divider={<Divider flexItem />}>
                            {row.fields.map(field => (
                                <Box key={field.field} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '130px 1fr' }, gap: 0.5, px: 1.5, py: 0.85 }}>
                                    <Typography variant="caption" color="text.secondary">{AUTHORIZATION_DIFF_FIELD_LABELS[field.field]}</Typography>
                                    <Stack spacing={0.2}>
                                        {row.kind !== 'ADD' ? (
                                            <Typography variant="body2" color="error.main">- {formatAuthorizationDiffValue(row, field.field, field.before, parentGrants)}</Typography>
                                        ) : null}
                                        {row.kind !== 'REMOVE' ? (
                                            <Typography variant="body2" color="success.main">+ {formatAuthorizationDiffValue(row, field.field, field.after, parentGrants)}</Typography>
                                        ) : null}
                                    </Stack>
                                </Box>
                            ))}
                        </Stack>
                    </Paper>
                );
            })}
        </Stack>
    );
}

function StagedOperations({ operations, permissionByName, onRemove }) {
    if (operations.length === 0) return null;
    return (
        <Stack spacing={0.75}>
            <Typography variant="subtitle2">待预检变更（{operations.length}）</Typography>
            {operations.map((operation, index) => (
                <Box key={`${operation.type}-${operation.grantId || operation.templateId || operation.permissionName}-${index}`} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, px: 1.25, py: 1 }}>
                    <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                        <Chip size="small" variant="outlined" label={OPERATION_LABELS[operation.type] || operation.type} />
                        <Typography variant="body2" sx={{ flex: 1, fontWeight: 650 }}>{operation.clientLabel}</Typography>
                        <Button size="small" color="error" onClick={() => onRemove(index)}>移除</Button>
                    </Stack>
                    {(operation.clientPermissionNames || []).length > 0 ? (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75 }}>
                            {operation.clientPermissionNames.map(name => permissionByName.get(name)?.displayName || name).join('、')}
                        </Typography>
                    ) : null}
                </Box>
            ))}
        </Stack>
    );
}

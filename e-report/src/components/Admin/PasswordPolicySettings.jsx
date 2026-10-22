import { useEffect, useMemo, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    Divider,
    FormControlLabel,
    Switch,
    TextField,
    Typography,
} from '@mui/material';

import {
    deleteAdminPasswordPolicyAPI,
    fetchAdminPasswordPolicyAPI,
    updateAdminPasswordPolicyAPI,
} from '../../services/passwordPolicyApi.js';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage.js';

const COMPOSITION_FIELDS = [
    { key: 'requireUppercase', label: '必须包含大写字母' },
    { key: 'requireLowercase', label: '必须包含小写字母' },
    { key: 'requireDigit', label: '必须包含数字' },
    { key: 'requireSpecial', label: '必须包含特殊字符' },
];

export default function PasswordPolicySettings({
    orgUnitId,
    scopeLabel,
    canUpdate,
    canDelete,
    disabled = false,
    onSaved,
}) {
    const [policy, setPolicy] = useState(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        setLoading(true);
        setError('');
        fetchAdminPasswordPolicyAPI(orgUnitId)
            .then(response => {
                if (active) setPolicy(response.data);
            })
            .catch(requestError => {
                if (active) setError(formatApiErrorMessage(requestError, '密码策略加载失败，请稍后重试。'));
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => { active = false; };
    }, [orgUnitId]);

    const payload = useMemo(() => policy && ({
        minLength: Number(policy.minLength),
        requireUppercase: policy.requireUppercase,
        requireLowercase: policy.requireLowercase,
        requireDigit: policy.requireDigit,
        requireSpecial: policy.requireSpecial,
    }), [policy]);

    const source = policy?.sourceScope === 'ORGANIZATION_OVERRIDE'
        ? `${policy.sourceOrgUnitName || '当前组织'}配置`
        : policy?.sourceScope === 'TENANT_OVERRIDE' ? '全市默认配置' : '平台默认配置';

    const save = async () => {
        if (!payload || saving) return;
        if (!Number.isInteger(payload.minLength) || payload.minLength < 8 || payload.minLength > 100) {
            setError('密码最小长度必须是8到100之间的整数。');
            return;
        }
        setSaving(true);
        setError('');
        try {
            const response = await updateAdminPasswordPolicyAPI(payload, orgUnitId);
            setPolicy(response.data);
            onSaved?.('密码策略已保存。');
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '密码策略保存失败，请稍后重试。'));
        } finally {
            setSaving(false);
        }
    };

    const reset = async () => {
        if (!policy?.localOverride || saving || !canDelete) return;
        if (!window.confirm(`确认删除${scopeLabel}的密码策略覆盖并恢复继承吗？`)) return;
        setSaving(true);
        setError('');
        try {
            await deleteAdminPasswordPolicyAPI(orgUnitId);
            const response = await fetchAdminPasswordPolicyAPI(orgUnitId);
            setPolicy(response.data);
            onSaved?.('密码策略覆盖已删除，已恢复继承。');
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '恢复密码策略失败，请稍后重试。'));
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 2 }}><CircularProgress size={20} /><Typography color="text.secondary">加载密码策略...</Typography></Box>;
    }
    if (!policy) {
        return <Alert severity="error">{error || '密码策略不可用。'}</Alert>;
    }

    return (
        <Box sx={{ p: { xs: 1.5, md: 2 }, mb: 2, border: 1, borderColor: 'divider', borderRadius: 1.5, bgcolor: 'background.paper' }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap' }}>
                <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>密码策略</Typography>
                    <Typography variant="body2" color="text.secondary">{scopeLabel} · 当前生效：{source}{policy.inherited ? '（继承）' : ''}</Typography>
                </Box>
                {policy.localOverride && <Typography variant="caption" color="primary.main">已设置本地覆盖</Typography>}
            </Box>
            {error && <Alert severity="error" sx={{ mt: 1.5 }}>{error}</Alert>}
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '220px 1fr' }, gap: 2, mt: 2, alignItems: 'start' }}>
                <TextField
                    label="最小密码长度"
                    type="number"
                    value={policy.minLength}
                    onChange={event => setPolicy(current => ({ ...current, minLength: event.target.value }))}
                    slotProps={{ htmlInput: { min: 8, max: 100, step: 1 } }}
                    helperText="允许范围：8—100位；系统最大长度固定为100位"
                    disabled={!canUpdate || disabled || saving}
                />
                <Box sx={{ display: 'grid', gap: 0.5 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>字符组成要求</Typography>
                    {COMPOSITION_FIELDS.map(field => (
                        <FormControlLabel
                            key={field.key}
                            control={<Switch checked={policy[field.key] === true} onChange={event => setPolicy(current => ({ ...current, [field.key]: event.target.checked }))} disabled={!canUpdate || disabled || saving} />}
                            label={field.label}
                        />
                    ))}
                </Box>
            </Box>
            <Divider sx={{ my: 1.5 }} />
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                {canUpdate && <Button variant="contained" onClick={save} disabled={disabled || saving}>{saving ? <CircularProgress size={18} color="inherit" sx={{ mr: 1 }} /> : null}保存密码策略</Button>}
                {canDelete && policy.localOverride && <Button color="warning" variant="outlined" onClick={reset} disabled={disabled || saving}>恢复继承默认值</Button>}
                {!canUpdate && <Typography variant="body2" color="text.secondary" sx={{ alignSelf: 'center' }}>当前账号只有查看权限。</Typography>}
            </Box>
        </Box>
    );
}

import PageHeading from '../../components/layout/PageHeading';
import { PageTabs, PageTabPanel } from '../../components/layout/PageTabs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    FormControlLabel,
    Paper,
    FormControl, InputLabel, Select, MenuItem,
    Switch,
    TextField,
    Typography,
} from '@mui/material';
import SaveIcon from '@mui/icons-material/Save';

import { PERMISSIONS } from '../../constants/permissions';
import { useAuth } from '../../hooks/useAuth';
import { fetchWorkOrderSlaPolicies, updateWorkOrderSlaPolicy } from '../../services/workOrderApiService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';

const numericFields = [
    ['claimMinutes', '接单时限（分钟）'],
    ['assignmentAcceptanceMinutes', '派单接收时限（分钟）'],
    ['responseMinutes', '处理时限（分钟）'],
    ['verificationMinutes', '验收时限（分钟）'],
    ['reworkMinutes', '整改时限（分钟）'],
    ['maxSingleHoldMinutes', '单次挂起时限（分钟）'],
    ['maxTotalHoldMinutes', '累计挂起时限（分钟）'],
];

function editablePolicy(policy) {
    return Object.fromEntries([
        ...numericFields.map(([field]) => [field, String(policy[field] ?? '')]),
        ['pauseOnHold', Boolean(policy.pauseOnHold)],
    ]);
}

function AdminSlaPolicyPage() {
    const { hasPermission } = useAuth();
    const [policies, setPolicies] = useState([]);
    const [drafts, setDrafts] = useState({});
    const [loading, setLoading] = useState(true);
    const [savingId, setSavingId] = useState(null);
    const [message, setMessage] = useState(null);
    const [selectedType, setSelectedType] = useState('');
    const [selectedPriority, setSelectedPriority] = useState('ROUTINE');
    const canUpdate = hasPermission(PERMISSIONS.SLA_POLICY_UPDATE);

    const load = useCallback(async () => {
        setLoading(true);
        setMessage(null);
        try {
            const response = await fetchWorkOrderSlaPolicies();
            const rows = response.data || [];
            setPolicies(rows);
            setDrafts(Object.fromEntries(rows.map(policy => [policy.id, editablePolicy(policy)])));
        } catch (error) {
            setMessage({ severity: 'error', text: formatApiErrorMessage(error, '加载故障时限策略失败。') });
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { load(); }, [load]);

    const grouped = useMemo(() => {
        const groups = new Map();
        policies.forEach(policy => {
            const current = groups.get(policy.reportType) || { name: policy.reportTypeName, rows: [] };
            current.rows.push(policy);
            groups.set(policy.reportType, current);
        });
        return [...groups.entries()];
    }, [policies]);

    const activeType = grouped.some(([type]) => type === selectedType) ? selectedType : grouped[0]?.[0];

    const updateDraft = (id, field, value) => {
        setDrafts(current => ({ ...current, [id]: { ...current[id], [field]: value } }));
    };

    const save = async (policy) => {
        const draft = drafts[policy.id];
        const payload = { pauseOnHold: draft.pauseOnHold };
        for (const [field] of numericFields) {
            const value = Number(draft[field]);
            if (!Number.isInteger(value) || value < 0 || (!field.startsWith('max') && value < 1)) {
                setMessage({ severity: 'error', text: `${policy.reportTypeName} / ${policy.priorityName} 的时限输入不正确。` });
                return;
            }
            payload[field] = value;
        }
        setSavingId(policy.id);
        setMessage(null);
        try {
            const response = await updateWorkOrderSlaPolicy(policy.id, payload);
            setPolicies(current => current.map(item => item.id === policy.id ? response.data : item));
            setDrafts(current => ({ ...current, [policy.id]: editablePolicy(response.data) }));
            setMessage({ severity: 'success', text: `${policy.reportTypeName} / ${policy.priorityName} 已保存，新时限从下一次阶段切换开始生效。` });
        } catch (error) {
            setMessage({ severity: 'error', text: formatApiErrorMessage(error, '保存故障时限策略失败。') });
        } finally {
            setSavingId(null);
        }
    };

    if (loading) {
        return <Box sx={{ minHeight: 280, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>;
    }

    return (
        <Box sx={{ width: '100%', minWidth: 0 }}>
            <PageHeading title="故障时限策略" description="按工单类型和优先级设置接单、派单接收、处理、验收、整改及挂起时限。" sx={{ mb: 1.5 }} />
            {message && <Alert severity={message.severity} sx={{ mb: 1.5 }}>{message.text}</Alert>}
            <PageTabs id="sla-types" label="工单类型" value={activeType || false} onChange={setSelectedType}
                options={grouped.map(([value, group]) => ({ value, label: group.name }))} />
            <Box>
                {grouped.map(([reportType, group]) => (
                    <PageTabPanel key={reportType} id="sla-types" value={reportType} selected={activeType}>
                        <FormControl size="small" sx={{ width: { xs: '100%', sm: 280 }, mb: 2 }}>
                            <InputLabel id={`sla-priority-${reportType}`}>优先级</InputLabel>
                            <Select labelId={`sla-priority-${reportType}`} label="优先级"
                                value={group.rows.some(policy => policy.priority === selectedPriority) ? selectedPriority : group.rows[0]?.priority || ''}
                                onChange={event => setSelectedPriority(event.target.value)}>
                                {group.rows.map(policy => <MenuItem key={policy.id} value={policy.priority}>{policy.priorityName}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <Box>
                            {group.rows.map(policy => {
                                const draft = drafts[policy.id] || editablePolicy(policy);
                                return (
                                    <Paper key={policy.id} variant="outlined"
                                           sx={{ display: policy.priority === (group.rows.some(row => row.priority === selectedPriority) ? selectedPriority : group.rows[0]?.priority) ? 'block' : 'none', p: 2, borderRadius: 1 }}>
                                        <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>{policy.priorityName}</Typography>
                                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' }, gap: 2 }}>
                                            {numericFields.map(([field, label]) => (
                                                <TextField
                                                    key={field}
                                                    label={label}
                                                    type="number"
                                                    size="small"
                                                    value={draft[field]}
                                                    disabled={!canUpdate || savingId === policy.id}
                                                    slotProps={{ htmlInput: { min: field.startsWith('max') ? 0 : 1 } }}
                                                    onChange={event => updateDraft(policy.id, field, event.target.value)}
                                                />
                                            ))}
                                            <FormControlLabel
                                                control={<Switch checked={draft.pauseOnHold} onChange={event => updateDraft(policy.id, 'pauseOnHold', event.target.checked)} />}
                                                label="挂起时暂停计时"
                                                disabled={!canUpdate || savingId === policy.id}
                                            />
                                        </Box>
                                        {canUpdate && (
                                            <Button
                                                size="small"
                                                variant="contained"
                                                startIcon={savingId === policy.id ? <CircularProgress color="inherit" size={16} /> : <SaveIcon />}
                                                disabled={savingId !== null}
                                                onClick={() => save(policy)}
                                                sx={{ mt: 1.25 }}
                                            >
                                                保存{policy.priorityName}策略
                                            </Button>
                                        )}
                                    </Paper>
                                );
                            })}
                        </Box>
                    </PageTabPanel>
                ))}
            </Box>
        </Box>
    );
}

export default AdminSlaPolicyPage;

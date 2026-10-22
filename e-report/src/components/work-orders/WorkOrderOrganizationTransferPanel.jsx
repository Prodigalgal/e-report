import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
    DialogTitle, FormControl, InputLabel, MenuItem, Paper, Select, Stack, TextField,
    Typography,
} from '@mui/material';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';

import apiClient from '../../services/apiClient';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDate } from '../../utils/formatDate';

const STATUS_LABELS = {
    PENDING: '待目标组织确认',
    ACCEPTED: '已接受',
    REJECTED: '已拒绝',
    CANCELLED: '已撤销',
};

const STATUS_COLORS = {
    PENDING: 'warning',
    ACCEPTED: 'success',
    REJECTED: 'error',
    CANCELLED: 'default',
};

export default function WorkOrderOrganizationTransferPanel({ workOrder, onChanged }) {
    const [history, setHistory] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [dialog, setDialog] = useState(null);
    const [targets, setTargets] = useState([]);
    const [targetOrgUnitId, setTargetOrgUnitId] = useState('');
    const [comment, setComment] = useState('');
    const [saving, setSaving] = useState(false);

    const loadHistory = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const response = await apiClient.get(`/work-orders/${workOrder.id}/organization-transfers`);
            setHistory(response.data || []);
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '加载组织移交记录失败。'));
        } finally {
            setLoading(false);
        }
    }, [workOrder.id]);

    useEffect(() => { loadHistory(); }, [loadHistory, workOrder.version]);

    const pending = useMemo(() => history.find(item => item.status === 'PENDING'), [history]);
    const canCreate = workOrder.allowedActions?.includes('TRANSFER_CREATE') && !pending;

    const openCreate = async () => {
        setDialog({ type: 'CREATE' });
        setComment('');
        setTargetOrgUnitId('');
        setError('');
        try {
            const response = await apiClient.get('/work-orders/organization-transfer-targets');
            setTargets((response.data || []).filter(target => target.id !== workOrder.ownerOrgId));
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '加载可移交组织失败。'));
        }
    };

    const openDecision = (type, transfer) => {
        setDialog({ type, transfer });
        setComment('');
        setError('');
    };

    const submit = async () => {
        if (!dialog || !comment.trim()) return;
        setSaving(true);
        setError('');
        try {
            if (dialog.type === 'CREATE') {
                await apiClient.post(`/work-orders/${workOrder.id}/organization-transfers`, {
                    targetOrgUnitId,
                    expectedWorkOrderVersion: workOrder.version,
                    reason: comment.trim(),
                });
            } else {
                await apiClient.post(
                    `/work-orders/${workOrder.id}/organization-transfers/${dialog.transfer.id}/${dialog.type.toLowerCase()}`,
                    { expectedWorkOrderVersion: workOrder.version, comment: comment.trim() },
                );
            }
            setDialog(null);
            await onChanged();
            await loadHistory();
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '组织移交操作失败，请刷新工单后重试。'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Stack spacing={1.25}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: "space-between", alignItems: { sm: 'center' } }}>
                <Box>
                    <Typography variant="body2" color="text.secondary">完整移交会改变工单归属和责任组织，原归属组织永久保留查看权。</Typography>
                </Box>
                {canCreate ? <Button variant="outlined" startIcon={<SwapHorizIcon />} onClick={openCreate}>发起完整移交</Button> : null}
            </Stack>
            {error && !dialog ? <Alert severity="error">{error}</Alert> : null}
            {loading ? <Box sx={{ py: 2, textAlign: 'center' }}><CircularProgress size={24} /></Box> : null}
            {!loading && history.length === 0 ? <Alert severity="info">当前工单没有组织移交记录。</Alert> : null}
            {!loading && history.map(transfer => (
                <Paper key={transfer.id} variant="outlined" sx={{ p: 1.5 }}>
                    <Stack spacing={1}>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: "space-between", alignItems: { sm: 'center' } }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                                {transfer.sourceOrgUnitName} → {transfer.targetOrgUnitName}
                            </Typography>
                            <Chip size="small" variant="outlined" color={STATUS_COLORS[transfer.status]} label={STATUS_LABELS[transfer.status] || transfer.status} />
                        </Stack>
                        <Typography variant="body2">申请原因：{transfer.requestReason}</Typography>
                        <Typography variant="caption" color="text.secondary">申请时间：{formatDate(transfer.requestedAt)}</Typography>
                        {transfer.decisionComment ? <Typography variant="body2">处理意见：{transfer.decisionComment}</Typography> : null}
                        {transfer.allowedActions?.length ? (
                            <Stack direction="row" spacing={1}>
                                {transfer.allowedActions.includes('ACCEPT') ? <Button size="small" variant="contained" onClick={() => openDecision('ACCEPT', transfer)}>接受移交</Button> : null}
                                {transfer.allowedActions.includes('REJECT') ? <Button size="small" color="error" variant="outlined" onClick={() => openDecision('REJECT', transfer)}>拒绝移交</Button> : null}
                                {transfer.allowedActions.includes('CANCEL') ? <Button size="small" color="warning" variant="outlined" onClick={() => openDecision('CANCEL', transfer)}>撤销申请</Button> : null}
                            </Stack>
                        ) : null}
                    </Stack>
                </Paper>
            ))}

            <Dialog open={Boolean(dialog)} onClose={saving ? undefined : () => setDialog(null)} fullWidth maxWidth="sm">
                <DialogTitle>{dialog?.type === 'CREATE' ? '发起工单完整移交' : dialog?.type === 'ACCEPT' ? '接受完整移交' : dialog?.type === 'REJECT' ? '拒绝完整移交' : '撤销移交申请'}</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={2}>
                        {error ? <Alert severity="error">{error}</Alert> : null}
                        {dialog?.type === 'CREATE' ? (
                            <>
                                <Alert severity="warning">目标组织接受后，当前处理责任会被撤销，工单回到目标组织待领取状态。</Alert>
                                <FormControl fullWidth required>
                                    <InputLabel>目标组织</InputLabel>
                                    <Select label="目标组织" value={targetOrgUnitId} onChange={event => setTargetOrgUnitId(event.target.value)}>
                                        {targets.map(target => <MenuItem key={target.id} value={target.id}>{target.name}（{target.code}）</MenuItem>)}
                                    </Select>
                                </FormControl>
                            </>
                        ) : <Alert severity={dialog?.type === 'ACCEPT' ? 'warning' : 'info'}>{dialog?.transfer?.sourceOrgUnitName} → {dialog?.transfer?.targetOrgUnitName}</Alert>}
                        <TextField
                            label={dialog?.type === 'CREATE' ? '移交原因' : '处理意见'}
                            value={comment}
                            onChange={event => setComment(event.target.value)}
                            multiline minRows={3} required slotProps={{ htmlInput: { maxLength: 2000 } }}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDialog(null)} disabled={saving}>取消</Button>
                    <Button
                        variant="contained"
                        color={dialog?.type === 'REJECT' ? 'error' : dialog?.type === 'CANCEL' ? 'warning' : 'primary'}
                        onClick={submit}
                        disabled={saving || !comment.trim() || (dialog?.type === 'CREATE' && !targetOrgUnitId)}
                    >
                        {saving ? '处理中…' : '确认提交'}
                    </Button>
                </DialogActions>
            </Dialog>
        </Stack>
    );
}

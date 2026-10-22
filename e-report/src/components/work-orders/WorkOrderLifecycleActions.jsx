import React, { useMemo, useState } from 'react';
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
    TextField,
    Typography,
} from '@mui/material';
import AssignmentIndIcon from '@mui/icons-material/AssignmentInd';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DoNotDisturbAltIcon from '@mui/icons-material/DoNotDisturbAlt';
import ForwardToInboxIcon from '@mui/icons-material/ForwardToInbox';
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircle';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircle';
import RateReviewIcon from '@mui/icons-material/RateReview';
import RedoIcon from '@mui/icons-material/Redo';
import SearchIcon from '@mui/icons-material/Search';

import {
    acceptAssignment,
    assignWorkOrder,
    cancelWorkOrder,
    claimWorkOrder,
    fetchAssignmentCandidates,
    holdWorkOrder,
    reassignWorkOrder,
    rejectAssignment,
    resumeWorkOrder,
    verifyWorkOrder,
} from '../../services/workOrderApiService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { hasAllowedAction } from '../../constants/workOrderLifecycle';
import { buildVerificationCommand, isVerificationCommentRequired } from '../../utils/workOrderVerification';

const reasonDialogConfig = {
    REJECT_ASSIGNMENT: { title: '拒绝派单', label: '拒绝原因', actionLabel: '确认拒绝' },
    HOLD: { title: '挂起工单', label: '挂起原因', actionLabel: '确认挂起' },
    CANCEL: { title: '取消工单', label: '取消原因', actionLabel: '确认取消' },
    WITHDRAW: { title: '撤回工单', label: '撤回原因', actionLabel: '确认撤回' },
};

function WorkOrderLifecycleActions({ workOrder, onUpdated, onRespond }) {
    const [busyAction, setBusyAction] = useState('');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [reasonAction, setReasonAction] = useState('');
    const [reason, setReason] = useState('');
    const [dispatchOpen, setDispatchOpen] = useState(false);
    const [dispatchMode, setDispatchMode] = useState('ASSIGN');
    const [candidates, setCandidates] = useState([]);
    const [candidateSearchTerm, setCandidateSearchTerm] = useState('');
    const [candidateSearchPerformed, setCandidateSearchPerformed] = useState(false);
    const [candidateId, setCandidateId] = useState('');
    const [responsibleOrgId, setResponsibleOrgId] = useState('');
    const [dispatchReason, setDispatchReason] = useState('');
    const [verificationOpen, setVerificationOpen] = useState(false);
    const [verificationDecision, setVerificationDecision] = useState('APPROVED');
    const [verificationComment, setVerificationComment] = useState('');

    const allowedActions = useMemo(() => new Set(workOrder?.allowedActions || []), [workOrder]);
    const hasActions = allowedActions.size > 0;
    const selectedCandidate = useMemo(
        () => candidates.find(candidate => String(candidate.membershipId) === String(candidateId)) || null,
        [candidateId, candidates],
    );

    const execute = async (actionName, request, successMessage) => {
        setBusyAction(actionName);
        setError('');
        setSuccess('');
        try {
            const response = await request();
            onUpdated(response.data);
            setSuccess(successMessage);
            return true;
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '工单状态更新失败，请刷新后重试。'));
            return false;
        } finally {
            setBusyAction('');
        }
    };

    const runImmediate = async (action) => {
        if (action === 'CLAIM') {
            await execute(action, () => claimWorkOrder(workOrder.id, workOrder.version), '接单成功，当前工单由你负责。');
        } else if (action === 'ACCEPT_ASSIGNMENT') {
            await execute(
                action,
                () => acceptAssignment(workOrder.id, workOrder.pendingAssignment.id, workOrder.version),
                '派单已接受。',
            );
        } else if (action === 'RESUME') {
            await execute(action, () => resumeWorkOrder(workOrder.id, workOrder.version), '工单已恢复处理。');
        }
    };

    const openReasonDialog = (action) => {
        setReason('');
        setReasonAction(action);
    };

    const submitReasonAction = async () => {
        const normalizedReason = reason.trim();
        if (!normalizedReason) return;
        let request;
        let successMessage;
        if (reasonAction === 'REJECT_ASSIGNMENT') {
            request = () => rejectAssignment(
                workOrder.id,
                workOrder.pendingAssignment.id,
                workOrder.version,
                normalizedReason,
            );
            successMessage = '派单已拒绝并通知调度人员。';
        } else if (reasonAction === 'HOLD') {
            request = () => holdWorkOrder(workOrder.id, workOrder.version, normalizedReason);
            successMessage = '工单已挂起。';
        } else {
            request = () => cancelWorkOrder(workOrder.id, workOrder.version, normalizedReason);
            successMessage = reasonAction === 'WITHDRAW' ? '工单已撤回。' : '工单已取消。';
        }
        if (await execute(reasonAction, request, successMessage)) {
            setReasonAction('');
        }
    };

    const openDispatchDialog = (mode) => {
        setDispatchMode(mode);
        setCandidateSearchTerm('');
        setCandidateSearchPerformed(false);
        setCandidates([]);
        setCandidateId('');
        setResponsibleOrgId(String(workOrder.responsibleOrgId || ''));
        setDispatchReason('');
        setDispatchOpen(true);
        setError('');
    };

    const searchCandidates = async () => {
        const normalizedSearchTerm = candidateSearchTerm.trim();
        if (!normalizedSearchTerm) return;
        setBusyAction('CANDIDATES');
        setError('');
        setCandidateSearchPerformed(true);
        setCandidateId('');
        setResponsibleOrgId('');
        try {
            const response = await fetchAssignmentCandidates(workOrder.id, normalizedSearchTerm);
            setCandidates(response.data || []);
        } catch (requestError) {
            setCandidates([]);
            setError(formatApiErrorMessage(requestError, '无法加载合格处理人员。'));
        } finally {
            setBusyAction('');
        }
    };

    const submitDispatch = async () => {
        const command = {
            assigneeMembershipId: candidateId,
            responsibleOrgId,
            reason: dispatchReason.trim(),
            expectedVersion: workOrder.version,
        };
        const request = dispatchMode === 'ASSIGN'
            ? () => assignWorkOrder(workOrder.id, command)
            : () => reassignWorkOrder(workOrder.id, command);
        const message = dispatchMode === 'ASSIGN' ? '工单已派发，等待目标人员接收。' : '调单成功，新处理人员已立即接管工单。';
        if (await execute(dispatchMode, request, message)) {
            setDispatchOpen(false);
        }
    };

    const openVerificationDialog = (decision) => {
        setVerificationDecision(decision);
        setVerificationComment('');
        setVerificationOpen(true);
    };

    const submitVerification = async () => {
        const command = buildVerificationCommand({
            responseId: workOrder.currentResponseId,
            decision: verificationDecision,
            comment: verificationComment.trim(),
            expectedVersion: workOrder.version,
        });
        const message = verificationDecision === 'APPROVED' ? '验收通过，工单已闭环。' : '回单已驳回，工单进入返工处理。';
        if (await execute('VERIFY', () => verifyWorkOrder(workOrder.id, command), message)) {
            setVerificationOpen(false);
        }
    };

    return (
        <Paper elevation={1} sx={{ p: { xs: 1.25, sm: 1.5 }, borderRadius: 1 }}>
            <Stack spacing={1.25}>
                <Box>
                    <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 750 }}>工单操作</Typography>
                    <Typography variant="caption" color="text.secondary">
                        {workOrder.currentAssignment
                            ? `当前处理人：${workOrder.currentAssignment.name || workOrder.currentAssignment.username}`
                            : '当前无责任处理人'}
                        {workOrder.pendingAssignment
                            ? `；待接收：${workOrder.pendingAssignment.name || workOrder.pendingAssignment.username}`
                            : ''}
                    </Typography>
                </Box>

                {error && <Alert severity="error" onClose={() => setError('')}>{error}</Alert>}
                {success && <Alert severity="success" onClose={() => setSuccess('')}>{success}</Alert>}

                {hasActions ? (
                    <Box sx={{ display: { xs: 'grid', sm: 'flex' }, gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', flexWrap: 'wrap', gap: 1 }}>
                        {hasAllowedAction(workOrder, 'CLAIM') && (
                            <Button variant="contained" startIcon={<AssignmentIndIcon />} disabled={Boolean(busyAction)} onClick={() => runImmediate('CLAIM')} sx={{ gridColumn: { xs: '1 / -1', sm: 'auto' } }}>主动接单</Button>
                        )}
                        {hasAllowedAction(workOrder, 'ACCEPT_ASSIGNMENT') && (
                            <Button variant="contained" startIcon={<CheckCircleIcon />} disabled={Boolean(busyAction)} onClick={() => runImmediate('ACCEPT_ASSIGNMENT')} sx={{ gridColumn: { xs: '1 / -1', sm: 'auto' } }}>接受派单</Button>
                        )}
                        {hasAllowedAction(workOrder, 'REJECT_ASSIGNMENT') && (
                            <Button variant="outlined" color="warning" startIcon={<DoNotDisturbAltIcon />} disabled={Boolean(busyAction)} onClick={() => openReasonDialog('REJECT_ASSIGNMENT')}>拒绝派单</Button>
                        )}
                        {hasAllowedAction(workOrder, 'ASSIGN') && (
                            <Button variant={hasAllowedAction(workOrder, 'CLAIM') ? 'outlined' : 'contained'} startIcon={<ForwardToInboxIcon />} disabled={Boolean(busyAction)} onClick={() => openDispatchDialog('ASSIGN')}>派单</Button>
                        )}
                        {hasAllowedAction(workOrder, 'REASSIGN') && (
                            <Button variant="outlined" startIcon={<RedoIcon />} disabled={Boolean(busyAction)} onClick={() => openDispatchDialog('REASSIGN')}>调单</Button>
                        )}
                        {hasAllowedAction(workOrder, 'RESPOND') && (
                            <Button variant="contained" startIcon={<RateReviewIcon />} disabled={Boolean(busyAction)} onClick={onRespond} sx={{ gridColumn: { xs: '1 / -1', sm: 'auto' } }}>提交回单</Button>
                        )}
                        {hasAllowedAction(workOrder, 'VERIFY_APPROVE') && (
                            <Button variant="contained" color="success" startIcon={<CheckCircleIcon />} disabled={Boolean(busyAction)} onClick={() => openVerificationDialog('APPROVED')} sx={{ gridColumn: { xs: '1 / -1', sm: 'auto' } }}>验收通过</Button>
                        )}
                        {hasAllowedAction(workOrder, 'VERIFY_REJECT') && (
                            <Button variant="outlined" color="warning" startIcon={<RedoIcon />} disabled={Boolean(busyAction)} onClick={() => openVerificationDialog('REJECTED')}>驳回返工</Button>
                        )}
                        {hasAllowedAction(workOrder, 'HOLD') && (
                            <Button variant="outlined" startIcon={<PauseCircleOutlineIcon />} disabled={Boolean(busyAction)} onClick={() => openReasonDialog('HOLD')}>挂起</Button>
                        )}
                        {hasAllowedAction(workOrder, 'RESUME') && (
                            <Button variant="outlined" startIcon={<PlayCircleOutlineIcon />} disabled={Boolean(busyAction)} onClick={() => runImmediate('RESUME')} sx={{ gridColumn: { xs: '1 / -1', sm: 'auto' } }}>恢复处理</Button>
                        )}
                        {hasAllowedAction(workOrder, 'CANCEL') && (
                            <Button variant="outlined" color="error" startIcon={<DoNotDisturbAltIcon />} disabled={Boolean(busyAction)} onClick={() => openReasonDialog('CANCEL')}>取消工单</Button>
                        )}
                        {hasAllowedAction(workOrder, 'WITHDRAW') && (
                            <Button variant="outlined" color="warning" startIcon={<DoNotDisturbAltIcon />} disabled={Boolean(busyAction)} onClick={() => openReasonDialog('WITHDRAW')}>撤回工单</Button>
                        )}
                        {busyAction && <CircularProgress size={24} sx={{ alignSelf: 'center' }} />}
                    </Box>
                ) : (
                    <Alert severity="info">当前账号在此状态下没有可执行操作。</Alert>
                )}
            </Stack>

            <Dialog open={Boolean(reasonAction)} onClose={busyAction ? undefined : () => setReasonAction('')} maxWidth="sm" fullWidth>
                <DialogTitle>{reasonDialogConfig[reasonAction]?.title}</DialogTitle>
                <DialogContent dividers>
                    <TextField
                        fullWidth
                        multiline
                        minRows={3}
                        autoFocus
                        required
                        label={reasonDialogConfig[reasonAction]?.label}
                        value={reason}
                        onChange={(event) => setReason(event.target.value)}
                        slotProps={{ htmlInput: { maxLength: 2000 } }}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setReasonAction('')} disabled={Boolean(busyAction)}>取消</Button>
                    <Button variant="contained" color={reasonAction === 'CANCEL' ? 'error' : 'primary'} onClick={submitReasonAction} disabled={!reason.trim() || Boolean(busyAction)}>
                        {reasonDialogConfig[reasonAction]?.actionLabel}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog open={dispatchOpen} onClose={busyAction ? undefined : () => setDispatchOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>{dispatchMode === 'ASSIGN' ? '派发工单' : '调整处理人'}</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={2}>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: 'stretch' }}>
                            <TextField
                                fullWidth
                                autoFocus
                                label="搜索处理人员"
                                placeholder="输入姓名、拼音、首字母或账号"
                                value={candidateSearchTerm}
                                onChange={(event) => {
                                    setCandidateSearchTerm(event.target.value);
                                    setCandidateSearchPerformed(false);
                                    setCandidates([]);
                                    setCandidateId('');
                                    setResponsibleOrgId('');
                                }}
                                onKeyDown={(event) => {
                                    if (event.key === 'Enter') {
                                        event.preventDefault();
                                        searchCandidates();
                                    }
                                }}
                                slotProps={{ htmlInput: { maxLength: 50 } }}
                            />
                            <Button
                                variant="outlined"
                                startIcon={<SearchIcon />}
                                onClick={searchCandidates}
                                disabled={!candidateSearchTerm.trim() || busyAction === 'CANDIDATES'}
                                sx={{ minWidth: 108 }}
                            >
                                查询
                            </Button>
                        </Stack>
                        {candidates.length > 0 && (
                            <FormControl fullWidth disabled={busyAction === 'CANDIDATES'}>
                                <InputLabel>目标处理人员</InputLabel>
                                <Select
                                    value={candidateId}
                                    label="目标处理人员"
                                    onChange={(event) => {
                                        const nextCandidateId = event.target.value;
                                        const candidate = candidates.find(item => String(item.membershipId) === String(nextCandidateId));
                                        setCandidateId(nextCandidateId);
                                        setResponsibleOrgId(candidate?.orgUnitId ? String(candidate.orgUnitId) : '');
                                    }}
                                >
                                    {candidates.map(candidate => (
                                        <MenuItem key={candidate.membershipId} value={String(candidate.membershipId)}>
                                            {candidate.name || candidate.username} · {candidate.orgName} · 在办 {candidate.activeWorkOrderCount}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        )}
                        {selectedCandidate && (
                            <Typography variant="body2" color="text.secondary">
                                责任组织：{selectedCandidate.orgName}
                            </Typography>
                        )}
                        <TextField label="派调单原因" value={dispatchReason} onChange={(event) => setDispatchReason(event.target.value)} multiline minRows={3} required slotProps={{ htmlInput: { maxLength: 2000 } }} />
                        {busyAction === 'CANDIDATES' && <CircularProgress size={24} />}
                        {!candidateSearchPerformed && <Alert severity="info">请输入姓名、拼音、首字母或账号，查询后再选择处理人员。</Alert>}
                        {candidateSearchPerformed && !busyAction && candidates.length === 0 && <Alert severity="warning">没有找到符合条件的处理人员。</Alert>}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDispatchOpen(false)} disabled={Boolean(busyAction)}>取消</Button>
                    <Button variant="contained" onClick={submitDispatch} disabled={!candidateId || !responsibleOrgId || !dispatchReason.trim() || Boolean(busyAction)}>确认</Button>
                </DialogActions>
            </Dialog>

            <Dialog open={verificationOpen} onClose={busyAction ? undefined : () => setVerificationOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>{verificationDecision === 'APPROVED' ? '验收通过' : '驳回返工'}</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={2}>
                        <TextField
                            label={verificationDecision === 'APPROVED' ? '验收意见' : '整改意见'}
                            value={verificationComment}
                            onChange={(event) => setVerificationComment(event.target.value)}
                            multiline
                            minRows={3}
                            required={isVerificationCommentRequired(verificationDecision)}
                            slotProps={{ htmlInput: { maxLength: 2000 } }}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setVerificationOpen(false)} disabled={Boolean(busyAction)}>取消</Button>
                    <Button
                        variant="contained"
                        color={verificationDecision === 'APPROVED' ? 'success' : 'warning'}
                        onClick={submitVerification}
                        disabled={(isVerificationCommentRequired(verificationDecision) && !verificationComment.trim()) || Boolean(busyAction)}
                    >
                        确认提交
                    </Button>
                </DialogActions>
            </Dialog>
        </Paper>
    );
}

export default WorkOrderLifecycleActions;

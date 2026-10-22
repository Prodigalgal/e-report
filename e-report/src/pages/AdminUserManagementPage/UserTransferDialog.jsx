import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Checkbox,
    Chip,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormControlLabel,
    InputLabel,
    LinearProgress,
    Pagination,
    Paper,
    Select,
    MenuItem,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';
import CancelIcon from '@mui/icons-material/Cancel';
import DownloadIcon from '@mui/icons-material/Download';
import RefreshIcon from '@mui/icons-material/Refresh';
import UploadFileIcon from '@mui/icons-material/UploadFile';

import { downloadFileInChunks } from '../../services/fileDownloadService.js';
import {
    cancelUserTransfer,
    createUserTransferImport,
    createUserTransferTemplate,
    executeUserTransfer,
    getUserTransferJob,
    getUserTransferJobs,
    getUserTransferRows,
    uploadUserTransferWorkbook,
} from '../../services/userTransferService.js';
import { formatDate } from '../../utils/formatDate.js';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage.js';
import {
    AUTHORIZATION_DIFF_FIELD_LABELS,
    AUTHORIZATION_DIFF_KIND_META,
    buildAuthorizationDiffRows,
    formatAuthorizationDiffValue,
} from '../../components/Admin/authorizationEditorWorkflow.js';
import {
    groupTransferRows,
    transferRowHasChanges,
    USER_TRANSFER_COMMAND_LABELS,
} from './userTransferWorkflow.js';

const RUNNING_STATUSES = new Set(['QUEUED', 'UPLOADED', 'VALIDATING', 'EXECUTING']);
const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'STALE', 'CANCELLED']);
const STATUS_LABELS = {
    QUEUED: '等待执行',
    UPLOADED: '等待校验',
    VALIDATING: '正在校验',
    PREVIEW_READY: '待确认执行',
    EXECUTING: '正在执行',
    COMPLETED: '已完成',
    FAILED: '执行失败',
    STALE: '预览已失效',
    CANCELLED: '已取消',
};

function diffValue(value, emptyLabel = '未设置') {
    return value === null || value === undefined || value === '' ? emptyLabel : value;
}

function FieldDiff({ changes }) {
    if (!changes?.length) return null;
    return (
        <Stack spacing={0.75} sx={{ mt: 1 }}>
            {changes.map((change, index) => (
                <Box key={`${change.field}-${index}`} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '120px minmax(0, 1fr)' }, gap: 0.5 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ pt: 0.25 }}>{change.field}</Typography>
                    <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                        {change.beforeValue !== null && change.beforeValue !== undefined ? (
                            <Typography variant="body2" sx={{ color: 'error.main', overflowWrap: 'anywhere' }}>
                                - {diffValue(change.beforeValue)}
                            </Typography>
                        ) : null}
                        <Typography variant="body2" sx={{ color: 'success.dark', overflowWrap: 'anywhere' }}>
                            + {diffValue(change.afterValue, '清空')}
                        </Typography>
                    </Stack>
                </Box>
            ))}
        </Stack>
    );
}

function PermissionDiff({ authorizationDiff, orgUnits }) {
    const rows = buildAuthorizationDiffRows({
        operations: [{ permissionChanges: authorizationDiff?.changes || [] }],
    });
    if (!rows.length) return null;
    return (
        <Stack spacing={0.75} sx={{ mt: 1 }}>
            {rows.map(row => {
                const meta = AUTHORIZATION_DIFF_KIND_META[row.kind] || { label: row.kind, color: 'default' };
                return (
                    <Box key={row.key} sx={{ borderLeft: 3, borderColor: `${meta.color}.main`, pl: 1.25, py: 0.25 }}>
                        <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: "center", flexWrap: 'wrap' }}>
                            <Chip size="small" color={meta.color} label={meta.label} />
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.permissionDisplayName}</Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                                {row.permissionName}
                            </Typography>
                        </Stack>
                        <Stack spacing={0.4} sx={{ mt: 0.5 }}>
                            {row.fields.map(field => (
                                <Box key={field.field} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '120px minmax(0, 1fr)' }, gap: 0.5 }}>
                                    <Typography variant="caption" color="text.secondary">
                                        {AUTHORIZATION_DIFF_FIELD_LABELS[field.field]}
                                    </Typography>
                                    <Box sx={{ minWidth: 0 }}>
                                        {field.before !== null && field.before !== undefined ? (
                                            <Typography variant="body2" sx={{ color: 'error.main', overflowWrap: 'anywhere' }}>
                                                - {formatAuthorizationDiffValue(row, field.field, field.before, orgUnits)}
                                            </Typography>
                                        ) : null}
                                        {field.after !== null && field.after !== undefined ? (
                                            <Typography variant="body2" sx={{ color: 'success.dark', overflowWrap: 'anywhere' }}>
                                                + {formatAuthorizationDiffValue(row, field.field, field.after, orgUnits)}
                                            </Typography>
                                        ) : null}
                                    </Box>
                                </Box>
                            ))}
                        </Stack>
                    </Box>
                );
            })}
        </Stack>
    );
}

function TransferPreviewRow({ row, orgUnits }) {
    const hasChanges = transferRowHasChanges(row);
    const hasErrors = Boolean(row.validationErrors?.length);
    return (
        <Box sx={{ px: { xs: 1.25, sm: 1.5 }, py: 1.25, borderTop: 1, borderColor: 'divider' }}>
            <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: "center", flexWrap: 'wrap' }}>
                <Chip size="small" variant="outlined" label={USER_TRANSFER_COMMAND_LABELS[row.commandType] || row.commandType || '未知命令'} />
                <Typography variant="caption" color="text.secondary">{row.sheetName} · 第 {row.rowNumber} 行</Typography>
                <Chip
                    size="small"
                    color={hasErrors ? 'error' : hasChanges ? 'primary' : 'default'}
                    label={hasErrors ? '已阻断' : hasChanges ? '有变化' : '无变化'}
                />
            </Stack>
            {row.authorizationDiff?.summary ? (
                <Typography variant="body2" sx={{ fontWeight: 700, mt: 0.75 }}>{row.authorizationDiff.summary}</Typography>
            ) : null}
            <FieldDiff changes={row.fieldChanges} />
            <PermissionDiff authorizationDiff={row.authorizationDiff} orgUnits={orgUnits} />
            {!hasChanges && !hasErrors ? (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>该命令不会改变当前数据。</Typography>
            ) : null}
            {hasErrors ? <Alert severity="error" sx={{ mt: 1 }}>{row.validationErrors.join('；')}</Alert> : null}
            {!hasErrors && row.executionMessage ? (
                <Alert severity={row.executionStatus === 'FAILED' ? 'error' : 'success'} sx={{ mt: 1 }}>{row.executionMessage}</Alert>
            ) : null}
        </Box>
    );
}

function RecentJobCard({ task, selected, busy, onOpen }) {
    return (
        <Paper
            variant="outlined"
            sx={{
                p: 1.25,
                bgcolor: selected ? 'action.selected' : 'background.paper',
                borderColor: selected ? 'primary.main' : 'divider',
            }}
        >
            <Stack direction="row" spacing={1} sx={{ justifyContent: "space-between", alignItems: "flex-start" }}>
                <Box sx={{ minWidth: 0 }}>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{STATUS_LABELS[task.status] || task.status}</Typography>
                    <Typography variant="caption" color="text.secondary">{formatDate(task.createdAt)}</Typography>
                </Box>
                <Button size="small" onClick={() => onOpen(task)} disabled={busy && !selected}>查看</Button>
            </Stack>
            <Stack direction="row" spacing={0.75} useFlexGap sx={{ mt: 0.75, flexWrap: 'wrap' }}>
                <Chip size="small" variant="outlined" label={`命令 ${task.totalRows}`} />
                <Chip size="small" color="success" variant="outlined" label={`有效 ${task.validRows}`} />
                <Chip size="small" color={task.invalidRows ? 'error' : 'default'} variant="outlined" label={`错误 ${task.invalidRows}`} />
            </Stack>
        </Paper>
    );
}

export default function UserTransferDialog({
    open,
    onClose,
    onCompleted,
    orgUnits = [],
    canViewTasks = false,
    canDownloadTemplate = false,
    canPreviewImport = false,
    canExecuteImport = false,
    canCancelImport = false,
    canDownloadErrorReport = false,
}) {
    const [file, setFile] = useState(null);
    const [job, setJob] = useState(null);
    const [rows, setRows] = useState([]);
    const [rowsPage, setRowsPage] = useState(0);
    const [rowsTotalPages, setRowsTotalPages] = useState(0);
    const [failureFilter, setFailureFilter] = useState('ALL');
    const [confirmed, setConfirmed] = useState(false);
    const [busy, setBusy] = useState(false);
    const [operationLabel, setOperationLabel] = useState('');
    const [progress, setProgress] = useState(null);
    const [error, setError] = useState('');
    const [recentJobs, setRecentJobs] = useState([]);
    const [historyLoading, setHistoryLoading] = useState(false);
    const controllerRef = useRef(null);
    const rowGroups = useMemo(() => groupTransferRows(rows), [rows]);

    const canReadTasks = canViewTasks || canPreviewImport;

    const loadRecentJobs = useCallback(async signal => {
        if (!canReadTasks) {
            setRecentJobs([]);
            return;
        }
        setHistoryLoading(true);
        try {
            const data = await getUserTransferJobs('IMPORT', signal);
            setRecentJobs(data?.content || []);
        } finally {
            setHistoryLoading(false);
        }
    }, [canReadTasks]);

    const loadRows = useCallback(async (jobId, page = rowsPage, filter = failureFilter, signal) => {
        const data = await getUserTransferRows(jobId, page, filter, signal);
        setRows(data?.content || []);
        setRowsPage(data?.number || 0);
        setRowsTotalPages(data?.totalPages || 0);
    }, [failureFilter, rowsPage]);

    useEffect(() => {
        if (!open) {
            controllerRef.current?.abort();
            return;
        }
        setFile(null);
        setJob(null);
        setRows([]);
        setRowsPage(0);
        setRowsTotalPages(0);
        setFailureFilter('ALL');
        setConfirmed(false);
        setBusy(false);
        setOperationLabel('');
        setProgress(null);
        setError('');
        const controller = new AbortController();
        loadRecentJobs(controller.signal).catch(loadError => {
            if (!controller.signal.aborted) {
                setError(formatApiErrorMessage(loadError, '读取最近批量治理任务失败。'));
            }
        });
        return () => controller.abort();
    }, [loadRecentJobs, open]);

    useEffect(() => {
        if (!open || !job?.id || !RUNNING_STATUSES.has(job.status)) return undefined;
        let stopped = false;
        const poll = async () => {
            try {
                const latest = await getUserTransferJob(job.id);
                if (stopped) return;
                setJob(latest);
                setRecentJobs(current => current.map(item => item.id === latest.id ? latest : item));
                if (latest.status === 'PREVIEW_READY' || TERMINAL_STATUSES.has(latest.status)) {
                    const filter = latest.invalidRows > 0
                        ? 'VALIDATION_FAILED'
                        : latest.failedRows > 0 ? 'EXECUTION_FAILED' : 'ALL';
                    await loadRows(latest.id, 0, filter);
                    setFailureFilter(filter);
                    setBusy(false);
                    setOperationLabel('');
                    if (latest.status === 'COMPLETED') onCompleted?.(latest);
                    loadRecentJobs().catch(() => undefined);
                    return;
                }
                globalThis.setTimeout(poll, 1000);
            } catch (pollError) {
                if (!stopped) {
                    setBusy(false);
                    setOperationLabel('');
                    setError(formatApiErrorMessage(pollError, '读取任务进度失败，请稍后重试。'));
                }
            }
        };
        const timer = globalThis.setTimeout(poll, 500);
        return () => {
            stopped = true;
            globalThis.clearTimeout(timer);
        };
    }, [job?.id, job?.status, loadRecentJobs, loadRows, onCompleted, open]);

    const downloadTemplate = async () => {
        if (!canDownloadTemplate) return;
        const controller = new AbortController();
        controllerRef.current = controller;
        setBusy(true);
        setOperationLabel('正在生成并下载模板');
        setProgress({ percent: 0 });
        setError('');
        try {
            const template = await createUserTransferTemplate(controller.signal);
            await downloadFileInChunks(template.downloadUrl, template.fileName || '用户批量治理模板.xlsx', {
                expectedSha256: template.sha256,
                signal: controller.signal,
                onProgress: setProgress,
            });
        } catch (downloadError) {
            setError(controller.signal.aborted ? '模板下载已停止。'
                : formatApiErrorMessage(downloadError, '下载用户批量治理模板失败。'));
        } finally {
            if (controllerRef.current === controller) controllerRef.current = null;
            setBusy(false);
            setOperationLabel('');
        }
    };

    const chooseFile = event => {
        const selected = event.target.files?.[0] || null;
        if (selected && !selected.name.toLowerCase().endsWith('.xlsx')) {
            setFile(null);
            setError('仅支持 .xlsx 文件。');
        } else if (selected && selected.size > 20 * 1024 * 1024) {
            setFile(null);
            setError('文件不能超过 20 MiB。');
        } else {
            setFile(selected);
            setError('');
        }
        setJob(null);
        setRows([]);
        setConfirmed(false);
        setProgress(null);
        event.target.value = '';
    };

    const preview = async () => {
        if (!canPreviewImport || !file) return;
        const controller = new AbortController();
        controllerRef.current = controller;
        setBusy(true);
        setOperationLabel('正在校验并分片上传');
        setError('');
        try {
            const uploadId = await uploadUserTransferWorkbook(file, {
                signal: controller.signal,
                onProgress: setProgress,
            });
            setOperationLabel('后台正在校验用户与授权命令');
            setProgress(null);
            const created = await createUserTransferImport(uploadId, controller.signal);
            setJob(created);
            setRecentJobs(current => [created, ...current.filter(item => item.id !== created.id)].slice(0, 8));
            setConfirmed(false);
        } catch (operationError) {
            setBusy(false);
            setOperationLabel('');
            setError(controller.signal.aborted ? '当前操作已停止。'
                : formatApiErrorMessage(operationError, '用户批量治理文件处理失败。'));
        } finally {
            if (controllerRef.current === controller) controllerRef.current = null;
        }
    };

    const execute = async () => {
        if (!canExecuteImport || !job?.previewToken || !confirmed) return;
        const controller = new AbortController();
        controllerRef.current = controller;
        setBusy(true);
        setOperationLabel('后台正在按用户分批执行');
        setError('');
        try {
            const executing = await executeUserTransfer(job.id, job.previewToken, controller.signal);
            setJob(executing);
        } catch (operationError) {
            setBusy(false);
            setOperationLabel('');
            setError(formatApiErrorMessage(operationError, '确认执行失败，请重新预览后重试。'));
        } finally {
            if (controllerRef.current === controller) controllerRef.current = null;
        }
    };

    const cancel = async () => {
        controllerRef.current?.abort();
        if (!job?.id || !['UPLOADED', 'VALIDATING', 'PREVIEW_READY'].includes(job.status)) {
            setBusy(false);
            return;
        }
        if (!canCancelImport) {
            setBusy(false);
            return;
        }
        try {
            const cancelledJob = await cancelUserTransfer(job.id);
            setJob(cancelledJob);
            setRecentJobs(current => current.map(item => item.id === cancelledJob.id ? cancelledJob : item));
            setConfirmed(false);
            setBusy(false);
        } catch (cancelError) {
            setError(formatApiErrorMessage(cancelError, '取消任务失败，请刷新任务状态。'));
        }
    };

    const changeRowsPage = async (_, value) => {
        try {
            await loadRows(job.id, value - 1, failureFilter);
        } catch (loadError) {
            setError(formatApiErrorMessage(loadError, '加载预览结果失败。'));
        }
    };

    const changeFailureFilter = async event => {
        const filter = event.target.value;
        setFailureFilter(filter);
        try {
            await loadRows(job.id, 0, filter);
        } catch (loadError) {
            setError(formatApiErrorMessage(loadError, '加载预览结果失败。'));
        }
    };

    const openTask = async task => {
        setBusy(true);
        setOperationLabel('正在恢复任务状态');
        setError('');
        try {
            const latest = await getUserTransferJob(task.id);
            setJob(latest);
            setFile(null);
            setConfirmed(false);
            const filter = latest.invalidRows > 0
                ? 'VALIDATION_FAILED'
                : latest.failedRows > 0 ? 'EXECUTION_FAILED' : 'ALL';
            setFailureFilter(filter);
            if (latest.totalRows > 0) {
                await loadRows(latest.id, 0, filter);
            } else {
                setRows([]);
                setRowsPage(0);
                setRowsTotalPages(0);
            }
            setBusy(RUNNING_STATUSES.has(latest.status));
            setOperationLabel(RUNNING_STATUSES.has(latest.status) ? '后台任务仍在运行' : '');
        } catch (taskError) {
            setBusy(false);
            setOperationLabel('');
            setError(formatApiErrorMessage(taskError, '恢复任务失败，请稍后重试。'));
        }
    };

    const startNewTask = () => {
        setFile(null);
        setJob(null);
        setRows([]);
        setRowsPage(0);
        setRowsTotalPages(0);
        setFailureFilter('ALL');
        setConfirmed(false);
        setProgress(null);
        setError('');
    };

    const canExecute = job?.status === 'PREVIEW_READY'
        && job.invalidRows === 0
        && Boolean(job.previewToken)
        && canExecuteImport
        && confirmed;

    const downloadErrorResult = async () => {
        if (!canDownloadErrorReport || !job?.errorFile?.downloadUrl) return;
        const controller = new AbortController();
        controllerRef.current = controller;
        setBusy(true);
        setOperationLabel('正在下载失败明细');
        setProgress(null);
        setError('');
        try {
            await downloadFileInChunks(job.errorFile.downloadUrl,
                    job.errorFile.fileName || '用户导入失败明细.xlsx', {
                        expectedSha256: job.errorFile.sha256,
                        signal: controller.signal,
                        onProgress: setProgress,
                    });
        } catch (downloadError) {
            setError(controller.signal.aborted ? '失败明细下载已停止。'
                : formatApiErrorMessage(downloadError, '下载失败明细失败。'));
        } finally {
            if (controllerRef.current === controller) controllerRef.current = null;
            setBusy(false);
            setOperationLabel('');
            setProgress(null);
        }
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            maxWidth="lg"
            fullWidth
            sx={{
                '& .MuiDialog-paper': {
                    m: { xs: 1, sm: 4 },
                    width: { xs: 'calc(100% - 16px)', sm: 'calc(100% - 64px)' },
                    maxHeight: { xs: 'calc(100% - 16px)', sm: 'calc(100% - 64px)' },
                },
            }}
        >
            <DialogTitle>用户批量治理</DialogTitle>
            <DialogContent sx={{ px: { xs: 2, sm: 3 } }}>
                {error ? <Alert severity="error" sx={{ mb: 1.5 }}>{error}</Alert> : null}
                <Alert severity="info" sx={{ mb: 1.5 }}>
                    用户资料、岗位模板、原子权限与数据域分工作表管理。上传后先生成不可变预览，确认执行时会再次校验权限、数据范围和用户版本。
                </Alert>
                <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 0.75 }}>
                    <Typography variant="subtitle2">最近任务</Typography>
                    {canReadTasks ? <Button
                        size="small"
                        startIcon={historyLoading ? <CircularProgress size={16} /> : <RefreshIcon />}
                        onClick={() => loadRecentJobs().catch(loadError => setError(formatApiErrorMessage(loadError, '刷新任务列表失败。')))}
                        disabled={historyLoading}
                    >
                        刷新
                    </Button> : null}
                </Stack>
                {canReadTasks ? <Stack spacing={1} sx={{ display: { xs: 'flex', sm: 'none' }, mb: 1.5 }}>
                    {recentJobs.length ? recentJobs.map(task => (
                        <RecentJobCard
                            key={task.id}
                            task={task}
                            selected={task.id === job?.id}
                            busy={busy}
                            onOpen={openTask}
                        />
                    )) : (
                        <Typography variant="body2" color="text.secondary" align="center" sx={{ py: 2 }}>
                            暂无批量治理任务
                        </Typography>
                    )}
                </Stack> : null}
                {canReadTasks ? <TableContainer sx={{ display: { xs: 'none', sm: 'block' }, maxHeight: 210, border: 1, borderColor: 'divider', mb: 1.5 }}>
                    <Table stickyHeader size="small">
                        <TableHead><TableRow>
                            <TableCell>创建时间</TableCell>
                            <TableCell>状态</TableCell>
                            <TableCell align="right">命令</TableCell>
                            <TableCell align="right">有效 / 错误</TableCell>
                            <TableCell align="right">操作</TableCell>
                        </TableRow></TableHead>
                        <TableBody>
                            {recentJobs.length ? recentJobs.map(task => (
                                <TableRow key={task.id} selected={task.id === job?.id}>
                                    <TableCell>{formatDate(task.createdAt)}</TableCell>
                                    <TableCell>{STATUS_LABELS[task.status] || task.status}</TableCell>
                                    <TableCell align="right">{task.totalRows}</TableCell>
                                    <TableCell align="right">{task.validRows} / {task.invalidRows}</TableCell>
                                    <TableCell align="right">
                                        <Button size="small" onClick={() => openTask(task)} disabled={busy && task.id !== job?.id}>查看</Button>
                                    </TableCell>
                                </TableRow>
                            )) : (
                                <TableRow><TableCell colSpan={5} align="center">暂无批量治理任务</TableCell></TableRow>
                            )}
                        </TableBody>
                    </Table>
                </TableContainer> : null}
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
                    {canDownloadTemplate ? <Button variant="outlined" startIcon={<DownloadIcon />} onClick={downloadTemplate} disabled={busy}>
                        下载最新模板
                    </Button> : null}
                    {canPreviewImport ? <>
                        <Button component="label" variant="outlined" startIcon={<UploadFileIcon />} disabled={busy}>
                            选择文件
                            <input hidden type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={chooseFile} />
                        </Button>
                        <Typography variant="body2" color={file ? 'text.primary' : 'text.secondary'} sx={{ overflowWrap: 'anywhere' }}>
                            {file ? `${file.name} · ${(file.size / 1024).toFixed(1)} KiB` : '尚未选择文件'}
                        </Typography>
                    </> : null}
                </Stack>

                {busy ? (
                    <Box sx={{ mt: 1.5 }}>
                        <LinearProgress variant={Number.isFinite(progress?.percent) ? 'determinate' : 'indeterminate'} value={progress?.percent || 0} />
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                            {operationLabel}{Number.isFinite(progress?.percent) ? ` · ${progress.percent}%` : ''}
                        </Typography>
                    </Box>
                ) : null}

                {job ? (
                    <Box sx={{ mt: 2 }}>
                        <Stack direction="row" spacing={0.75} useFlexGap sx={{ mb: 1, flexWrap: 'wrap' }}>
                            <Chip size="small" label={STATUS_LABELS[job.status] || job.status} color={job.status === 'COMPLETED' ? 'success' : job.status === 'FAILED' || job.status === 'STALE' ? 'error' : 'default'} />
                            <Chip size="small" label={`用户 ${job.previewSummary?.userCount ?? '-'}`} />
                            <Chip size="small" color="primary" variant="outlined" label={`受影响 ${job.previewSummary?.affectedUserCount ?? '-'}`} />
                            <Chip size="small" label={`命令 ${job.totalRows}`} />
                            <Chip size="small" color={job.invalidRows ? 'error' : 'success'} label={`阻断 ${job.previewSummary?.blockedUserCount ?? job.invalidRows}`} />
                            {job.processedRows ? <Chip size="small" label={`已处理 ${job.processedRows}`} /> : null}
                        </Stack>
                        {job.previewSummary ? (
                            <Stack direction="row" spacing={0.75} useFlexGap sx={{ mb: 1, flexWrap: 'wrap' }}>
                                <Chip size="small" variant="outlined" label={`资料字段 ${job.previewSummary.fieldChangeCount}`} />
                                <Chip size="small" color="success" variant="outlined" label={`新增权限 ${job.previewSummary.addedPermissionCount}`} />
                                <Chip size="small" color="primary" variant="outlined" label={`调整权限 ${job.previewSummary.updatedPermissionCount}`} />
                                <Chip size="small" color="error" variant="outlined" label={`撤销权限 ${job.previewSummary.removedPermissionCount}`} />
                                <Chip size="small" variant="outlined" label={`无变化命令 ${job.previewSummary.unchangedCommandCount}`} />
                            </Stack>
                        ) : null}
                        {job.failureMessage ? <Alert severity={job.status === 'PREVIEW_READY' ? 'warning' : 'error'} sx={{ mb: 1 }}>{job.failureMessage}</Alert> : null}
                        {job.status === 'PREVIEW_READY' ? (
                            <Alert severity={job.invalidRows ? 'error' : 'success'} sx={{ mb: 1 }}>
                                {job.invalidRows ? '存在错误命令，请只看错误并修正工作簿后重新上传。' : '预览校验通过，可确认执行。'}
                            </Alert>
                        ) : null}
                        {rows.length || rowsTotalPages ? (
                            <>
                                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' }, mb: 1 }}>
                                    <FormControl size="small" sx={{ minWidth: 180 }}>
                                        <InputLabel id="user-transfer-row-filter-label">结果筛选</InputLabel>
                                        <Select labelId="user-transfer-row-filter-label" label="结果筛选"
                                                value={failureFilter} onChange={changeFailureFilter}>
                                            <MenuItem value="ALL">全部命令</MenuItem>
                                            <MenuItem value="VALIDATION_FAILED">仅校验失败</MenuItem>
                                            <MenuItem value="EXECUTION_FAILED">仅执行失败</MenuItem>
                                        </Select>
                                    </FormControl>
                                    {canDownloadErrorReport && job.errorFile ? (
                                        <Button size="small" variant="outlined" startIcon={<DownloadIcon />} onClick={downloadErrorResult} disabled={busy}>
                                            下载失败明细
                                        </Button>
                                    ) : null}
                                </Stack>
                                <Stack spacing={1} sx={{ maxHeight: 480, overflowY: 'auto', pr: 0.5 }}>
                                    {rowGroups.map(group => (
                                        <Paper key={group.subjectKey} variant="outlined" sx={{ overflow: 'hidden' }}>
                                            <Box sx={{ px: 1.5, py: 1, bgcolor: 'action.hover' }}>
                                                <Stack direction="row" spacing={1} sx={{ justifyContent: "space-between", alignItems: "center" }}>
                                                    <Typography variant="subtitle2">{group.subjectDisplay}</Typography>
                                                    <Typography variant="caption" color="text.secondary">{group.rows.length} 条命令</Typography>
                                                </Stack>
                                            </Box>
                                            {group.rows.map(row => <TransferPreviewRow key={row.id} row={row} orgUnits={orgUnits} />)}
                                        </Paper>
                                    ))}
                                </Stack>
                                {rowsTotalPages > 1 ? (
                                    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1 }}>
                                        <Pagination count={rowsTotalPages} page={rowsPage + 1} onChange={changeRowsPage} />
                                    </Box>
                                ) : null}
                            </>
                        ) : null}
                        {canExecuteImport && job.status === 'PREVIEW_READY' && job.invalidRows === 0 ? (
                            <FormControlLabel
                                sx={{ mt: 1 }}
                                control={<Checkbox checked={confirmed} onChange={event => setConfirmed(event.target.checked)} disabled={busy} />}
                                label="我已核对账号资料、岗位模板、逐条原子权限、数据范围与有效期，确认执行"
                            />
                        ) : null}
                    </Box>
                ) : null}
            </DialogContent>
            <DialogActions
                sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', sm: 'repeat(4, auto)' },
                    gap: 1,
                    px: { xs: 2, sm: 3 },
                    '& > :not(style) ~ :not(style)': { ml: 0 },
                    '& .MuiButton-root': { minWidth: 0 },
                }}
            >
                {controllerRef.current ? (
                    <Button color="error" startIcon={<CancelIcon />} onClick={cancel}>停止当前传输</Button>
                ) : canCancelImport && ['UPLOADED', 'VALIDATING', 'PREVIEW_READY'].includes(job?.status) ? (
                    <Button color="error" startIcon={<CancelIcon />} onClick={cancel}>取消任务</Button>
                ) : (
                    <Button onClick={onClose}>关闭</Button>
                )}
                {canPreviewImport && job && !busy ? <Button onClick={startNewTask}>新建任务</Button> : null}
                {canPreviewImport ? <Button variant="outlined" onClick={preview} disabled={busy || !file || Boolean(job)}>
                    {busy && !job ? <CircularProgress size={18} sx={{ mr: 1 }} /> : null}上传并预览
                </Button> : null}
                {canExecuteImport ? <Button variant="contained" onClick={execute} disabled={busy || !canExecute}>确认执行</Button> : null}
            </DialogActions>
        </Dialog>
    );
}

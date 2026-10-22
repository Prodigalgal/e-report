import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    LinearProgress,
    Paper,
    Stack,
    Typography,
} from '@mui/material';
import CancelIcon from '@mui/icons-material/Cancel';
import DownloadIcon from '@mui/icons-material/Download';
import RefreshIcon from '@mui/icons-material/Refresh';

import { downloadFileInChunks } from '../../services/fileDownloadService.js';
import {
    cancelUserTransfer,
    getUserTransferJob,
    getUserTransferJobs,
} from '../../services/userTransferService.js';
import { formatDate } from '../../utils/formatDate.js';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage.js';

const RUNNING = new Set(['QUEUED', 'EXECUTING']);
const STATUS_LABELS = {
    QUEUED: '等待生成',
    EXECUTING: '正在生成',
    COMPLETED: '可下载',
    FAILED: '生成失败',
    STALE: '预览已失效',
    CANCELLED: '已取消',
};

function statusColor(status) {
    if (status === 'COMPLETED') return 'success';
    if (status === 'FAILED' || status === 'STALE') return 'error';
    return 'default';
}

export default function UserExportTaskDialog({
    open,
    onClose,
    onNewExport,
    canCreateExport = false,
    canCancelExport = false,
    canDownloadExport = false,
}) {
    const [jobs, setJobs] = useState([]);
    const [selectedJob, setSelectedJob] = useState(null);
    const [loading, setLoading] = useState(false);
    const [downloading, setDownloading] = useState(false);
    const [progress, setProgress] = useState(null);
    const [error, setError] = useState('');
    const downloadControllerRef = useRef(null);

    const loadJobs = useCallback(async signal => {
        setLoading(true);
        try {
            const data = await getUserTransferJobs('EXPORT', signal);
            const current = data?.content || [];
            setJobs(current);
            setSelectedJob(previous => current.find(job => job.id === previous?.id) || current[0] || null);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (!open) return undefined;
        const controller = new AbortController();
        setError('');
        loadJobs(controller.signal).catch(loadError => {
            if (!controller.signal.aborted) {
                setError(formatApiErrorMessage(loadError, '加载导出任务失败。'));
            }
        });
        return () => controller.abort();
    }, [loadJobs, open]);

    useEffect(() => {
        if (!open || !selectedJob?.id || !RUNNING.has(selectedJob.status)) return undefined;
        let stopped = false;
        const poll = async () => {
            try {
                const latest = await getUserTransferJob(selectedJob.id);
                if (stopped) return;
                setSelectedJob(latest);
                setJobs(current => current.map(job => job.id === latest.id ? latest : job));
                if (RUNNING.has(latest.status)) globalThis.setTimeout(poll, 1200);
            } catch (pollError) {
                if (!stopped) setError(formatApiErrorMessage(pollError, '刷新导出任务失败。'));
            }
        };
        const timer = globalThis.setTimeout(poll, 800);
        return () => {
            stopped = true;
            globalThis.clearTimeout(timer);
        };
    }, [open, selectedJob?.id, selectedJob?.status]);

    const download = async () => {
        if (!canDownloadExport || !selectedJob?.resultFile?.downloadUrl) return;
        const controller = new AbortController();
        downloadControllerRef.current = controller;
        setDownloading(true);
        setProgress(null);
        setError('');
        try {
            await downloadFileInChunks(
                    selectedJob.resultFile.downloadUrl,
                    selectedJob.resultFile.fileName || '用户与授权导出.xlsx', {
                        expectedSha256: selectedJob.resultFile.sha256,
                        signal: controller.signal,
                        concurrency: 4,
                        onProgress: setProgress,
                    });
        } catch (downloadError) {
            setError(controller.signal.aborted ? '本次下载已停止，导出文件仍可在任务记录中继续下载。'
                : formatApiErrorMessage(downloadError, '下载导出文件失败。'));
        } finally {
            if (downloadControllerRef.current === controller) downloadControllerRef.current = null;
            setDownloading(false);
            setProgress(null);
        }
    };

    const cancel = async () => {
        if (!canCancelExport || selectedJob?.status !== 'QUEUED') return;
        setLoading(true);
        setError('');
        try {
            const cancelled = await cancelUserTransfer(selectedJob.id);
            setSelectedJob(cancelled);
            setJobs(current => current.map(job => job.id === cancelled.id ? cancelled : job));
        } catch (cancelError) {
            setError(formatApiErrorMessage(cancelError, '取消导出任务失败，请刷新后重试。'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Dialog open={open} onClose={downloading ? undefined : onClose} maxWidth="md" fullWidth>
            <DialogTitle>导出任务</DialogTitle>
            <DialogContent>
                {error ? <Alert severity="error" sx={{ mb: 1.5 }}>{error}</Alert> : null}
                <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}>
                    <Typography variant="body2" color="text.secondary">任务完成后保留 2 小时，可关闭页面后返回继续下载。</Typography>
                    <Button size="small" startIcon={loading ? <CircularProgress size={16} /> : <RefreshIcon />}
                            onClick={() => loadJobs().catch(loadError => setError(formatApiErrorMessage(loadError, '刷新导出任务失败。')))}
                            disabled={loading}>刷新</Button>
                </Stack>
                <Stack spacing={1}>
                    {jobs.length ? jobs.map(job => (
                        <Paper key={job.id} variant="outlined"
                               onClick={() => setSelectedJob(job)}
                               sx={{ p: 1.25, cursor: 'pointer', borderColor: selectedJob?.id === job.id ? 'primary.main' : 'divider',
                                   bgcolor: selectedJob?.id === job.id ? 'action.selected' : 'background.paper' }}>
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={0.75} sx={{ justifyContent: "space-between" }}>
                                <Box>
                                    <Stack direction="row" spacing={0.75} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
                                        <Chip size="small" color={statusColor(job.status)} label={STATUS_LABELS[job.status] || job.status} />
                                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{job.totalRows} 个用户</Typography>
                                    </Stack>
                                    <Typography variant="caption" color="text.secondary">{formatDate(job.createdAt)}</Typography>
                                </Box>
                                {job.failureMessage ? <Typography variant="caption" color="error" sx={{ maxWidth: 420 }}>{job.failureMessage}</Typography> : null}
                            </Stack>
                        </Paper>
                    )) : <Typography align="center" color="text.secondary" sx={{ py: 4 }}>暂无导出任务</Typography>}
                </Stack>
                {downloading ? (
                    <Box sx={{ mt: 1.5 }}>
                        <LinearProgress variant={Number.isFinite(progress?.percent) ? 'determinate' : 'indeterminate'} value={progress?.percent || 0} />
                        <Typography variant="caption" color="text.secondary">正在下载{Number.isFinite(progress?.percent) ? ` · ${progress.percent}%` : ''}</Typography>
                    </Box>
                ) : null}
                {selectedJob?.status === 'EXECUTING' ? <Alert severity="info" sx={{ mt: 1.5 }}>文件正在生成。关闭窗口不会中断后台任务。</Alert> : null}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={downloading}>关闭</Button>
                {canCreateExport ? <Button onClick={onNewExport} disabled={loading || downloading}>新建导出</Button> : null}
                {canCancelExport && selectedJob?.status === 'QUEUED' ? <Button color="error" startIcon={<CancelIcon />} onClick={cancel} disabled={loading}>取消任务</Button> : null}
                {downloading ? (
                    <Button color="error" startIcon={<CancelIcon />} onClick={() => downloadControllerRef.current?.abort()}>停止下载</Button>
                ) : canDownloadExport ? (
                    <Button variant="contained" startIcon={<DownloadIcon />} onClick={download}
                            disabled={selectedJob?.status !== 'COMPLETED' || !selectedJob?.resultFile}>下载文件</Button>
                ) : null}
            </DialogActions>
        </Dialog>
    );
}

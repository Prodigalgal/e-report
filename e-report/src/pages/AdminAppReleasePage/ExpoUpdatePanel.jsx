import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Stack, Typography } from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import apiClient from '../../services/apiClient';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDateTime } from '../../utils/formatDate';

const STATUS = { PUBLISHING: ['分发中', 'info'], PUBLISHED: ['已发布', 'success'], FAILED: ['发布失败', 'error'] };

export default function ExpoUpdatePanel({ canPublish }) {
    const [historyOpen, setHistoryOpen] = useState(false);
    const [records, setRecords] = useState([]);
    const [latest, setLatest] = useState(null);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState('');
    const [pendingId, setPendingId] = useState('');
    const request = useRef(null);
    const load = useCallback(async (requestedPage = page) => {
        request.current?.abort();
        const controller = new AbortController();
        request.current = controller;
        setLoading(true);
        try {
            const response = await apiClient.get('/admin/app-updates/ota', { params: { page: requestedPage, size: 10 }, signal: controller.signal });
            if (controller.signal.aborted) return;
            setRecords(response.data.content || []); setTotalPages(response.data.totalPages || 0);
            if (requestedPage === 0) setLatest(response.data.content?.[0] || null);
            setError('');
        } catch (requestError) { if (!controller.signal.aborted) setError(formatApiErrorMessage(requestError, '无法加载 OTA 发布记录。')); }
        finally { if (!controller.signal.aborted) setLoading(false); }
    }, [page]);
    useEffect(() => { void load(); return () => request.current?.abort(); }, [load]);
    const closeHistory = () => { setHistoryOpen(false); setPage(0); };
    useEffect(() => {
        if (!pendingId) return undefined;
        let active = true;
        let timer;
        const poll = async () => {
            try {
                const response = await apiClient.get(`/admin/app-updates/ota/${pendingId}`);
                if (!active) return;
                if (response.data.status === 'PUBLISHING') timer = window.setTimeout(poll, 4000);
                else { setPendingId(''); await load(); }
            } catch (requestError) {
                if (active) { setError(formatApiErrorMessage(requestError, '读取发布进度失败，可刷新发布记录查看。')); setPendingId(''); }
            }
        };
        timer = window.setTimeout(poll, 4000);
        return () => { active = false; window.clearTimeout(timer); };
    }, [load, pendingId]);
    const upload = async event => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (!file) return;
        if (!file.name.endsWith('.zip') || file.size > 50 * 1024 * 1024) { setError('请选择不超过 50MB 的已签名 OTA ZIP 归档。'); return; }
        setUploading(true); setError('');
        try {
            const body = new FormData(); body.append('archive', file);
            const response = await apiClient.post('/admin/app-updates/ota', body, { timeout: 180000 });
            setPendingId(response.data.status === 'PUBLISHING' ? response.data.id : '');
            setPage(0); await load(0);
        } catch (requestError) { setError(formatApiErrorMessage(requestError, 'OTA 归档校验或发布失败。')); }
        finally { setUploading(false); }
    };
    return <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ xs: 'stretch', md: 'center' }}>
            <Box sx={{ flex: 1 }}>
                <Typography component="h2" variant="subtitle1" fontWeight={750}>OTA 功能更新</Typography>
                <Typography variant="body2" color="text.secondary">
                    {pendingId ? '功能包正在分发，完成前客户端继续使用当前版本。' : latest ? `最近提交 ${latest.versionName} · ${STATUS[latest.status]?.[0] || latest.status}` : '兼容的功能更新无需安装 APK，由 GitHub Actions 签名发布。'}
                </Typography>
            </Box>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Button startIcon={<HistoryIcon />} onClick={() => setHistoryOpen(true)}>发布记录</Button>
                {canPublish && <Button component="label" variant="outlined" startIcon={<CloudUploadIcon />} disabled={uploading || Boolean(pendingId)}>
                    {uploading ? '正在校验归档' : '导入签名功能包'}
                    <input hidden type="file" accept=".zip,application/zip" onChange={upload} />
                </Button>}
            </Stack>
        </Stack>
        {error && <Alert severity="error" sx={{ mt: 1.5 }}>{error}</Alert>}
        <Dialog open={historyOpen} onClose={closeHistory} maxWidth="sm" fullWidth>
            <DialogTitle>OTA 发布记录</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={2}>
                    {!records.length && <Typography color="text.secondary">{loading ? '正在加载…' : '尚无功能包发布记录。'}</Typography>}
                    {records.map(record => <Box key={record.id} sx={{ pb: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
                        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
                            <Typography fontWeight={700}>{record.versionName} {record.kind === 'ROLLBACK' ? '回退内置功能包' : '功能包'}</Typography>
                            <Chip size="small" label={STATUS[record.status]?.[0] || record.status} color={STATUS[record.status]?.[1] || 'default'} />
                        </Stack>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1, overflowWrap: 'anywhere' }}>
                            {record.runtimeVersion}<br />{record.environment === 'test' ? '测试环境' : '生产环境'} · {formatDateTime(record.createdAt)}<br />源码 {record.sourceSha.slice(0, 12)}
                        </Typography>
                        {record.failureMessage && <Alert severity="error" sx={{ mt: 1 }}>{record.failureMessage}</Alert>}
                    </Box>)}
                    {totalPages > 1 && <Stack direction="row" alignItems="center" justifyContent="space-between">
                        <Button disabled={page === 0 || loading} onClick={() => setPage(value => value - 1)}>上一页</Button>
                        <Typography variant="body2">{page + 1} / {totalPages}</Typography>
                        <Button disabled={page + 1 >= totalPages || loading} onClick={() => setPage(value => value + 1)}>下一页</Button>
                    </Stack>}
                </Stack>
            </DialogContent>
            <DialogActions><Button onClick={() => { void load(); }} disabled={loading}>刷新</Button><Button onClick={closeHistory}>关闭</Button></DialogActions>
        </Dialog>
    </Paper>;
}

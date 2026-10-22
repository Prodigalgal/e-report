import { useEffect, useState } from 'react';
import {
    Alert, Box, Button, Chip, CircularProgress, Collapse, Container, Dialog, DialogActions,
    DialogContent, DialogTitle, LinearProgress, MenuItem, Paper, Table, TableBody,
    TableCell, TableHead, TableRow, TextField, Typography, useMediaQuery,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import TuneIcon from '@mui/icons-material/Tune';
import RefreshIcon from '@mui/icons-material/Refresh';
import options from '../../../../shared/email-delivery.json';
import { defaultEmailDeliveryRange, validateEmailDeliveryRange } from '../../../../shared/email-delivery.ts';
import PageHeading from '../../components/layout/PageHeading';
import ListResultsBar, { ListPagination } from '../../components/layout/ListResultsBar';
import ViewportTableContainer from '../../components/layout/ViewportTableContainer';
import { fetchEmailDelivery, fetchEmailDeliveryScopes, searchEmailDeliveries } from '../../services/emailDeliveryApiService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDate } from '../../utils/formatDate';

const initialFilters = () => ({ category: '', status: '', keyword: '', orgUnitId: '', ...defaultEmailDeliveryRange() });

function DeliveryStatus({ status }) {
    const presentation = options.statuses[status] || options.statuses.FAILED;
    return <Chip size="small" label={presentation.label} color={presentation.color} />;
}

export default function AdminEmailDeliveryPage() {
    const mobile = useMediaQuery('(max-width:899.95px)');
    const [draft, setDraft] = useState(initialFilters);
    const [filters, setFilters] = useState(initialFilters);
    const [page, setPage] = useState(0);
    const [refreshKey, setRefreshKey] = useState(0);
    const [expanded, setExpanded] = useState(false);
    const [result, setResult] = useState(null);
    const [scopes, setScopes] = useState([]);
    const [scopeError, setScopeError] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const [selectedId, setSelectedId] = useState(null);
    const [detail, setDetail] = useState(null);
    const [detailError, setDetailError] = useState('');

    useEffect(() => {
        const controller = new AbortController();
        fetchEmailDeliveryScopes(controller.signal).then(response => setScopes(response.data))
            .catch(failure => { if (!controller.signal.aborted) setScopeError(formatApiErrorMessage(failure, '组织范围加载失败。')); });
        return () => controller.abort();
    }, []);

    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        setError('');
        searchEmailDeliveries({ ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '')), page, size: 20 }, controller.signal)
            .then(response => setResult(response.data))
            .catch(failure => { if (!controller.signal.aborted) setError(formatApiErrorMessage(failure, '邮件发送记录加载失败。')); })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [filters, page, refreshKey]);

    useEffect(() => {
        if (!selectedId) return undefined;
        const controller = new AbortController();
        setDetail(null);
        setDetailError('');
        fetchEmailDelivery(selectedId, controller.signal).then(response => setDetail(response.data))
            .catch(failure => { if (!controller.signal.aborted) setDetailError(formatApiErrorMessage(failure, '邮件详情加载失败。')); });
        return () => controller.abort();
    }, [selectedId]);

    const change = event => setDraft(previous => ({ ...previous, [event.target.name]: event.target.value }));
    const submit = event => {
        event.preventDefault();
        const validation = validateEmailDeliveryRange(draft.from, draft.to);
        if (validation) { setError(validation); return; }
        setPage(0);
        setFilters({ ...draft, keyword: draft.keyword.trim() });
    };
    const summary = `共 ${result?.totalElements ?? 0} 次发送尝试 · ${filters.from} 至 ${filters.to}`;

    return <Container maxWidth={false} disableGutters>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 1, mb: 1.5 }}>
            <PageHeading title="邮件发送台账" description="按收件人追踪实际发送尝试" />
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => setRefreshKey(value => value + 1)} disabled={loading}>刷新</Button>
        </Box>
        <Paper variant="outlined" component="form" onSubmit={submit} sx={{ p: 1.5 }}>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'minmax(200px, 2fr) minmax(140px, 1fr) minmax(140px, 1fr) auto auto' }, gap: 1.5, alignItems: 'center' }}>
                <TextField label="关键词" name="keyword" value={draft.keyword} onChange={change} placeholder="主题、脱敏地址、记录或关联编号" slotProps={{ htmlInput: { maxLength: 160 } }} sx={{ gridColumn: { xs: '1 / -1', md: 'auto' } }} />
                <TextField select label="通知类型" name="category" value={draft.category} onChange={change}>
                    <MenuItem value="">全部类型</MenuItem>{Object.entries(options.categories).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
                </TextField>
                <TextField select label="发送状态" name="status" value={draft.status} onChange={change}>
                    <MenuItem value="">全部状态</MenuItem>{Object.entries(options.statuses).map(([value, presentation]) => <MenuItem key={value} value={value}>{presentation.label}</MenuItem>)}
                </TextField>
                <Button type="submit" variant="contained" startIcon={<SearchIcon />} disabled={loading}>查询</Button>
                <Button startIcon={<TuneIcon />} aria-expanded={expanded} aria-controls="email-delivery-filters" onClick={() => setExpanded(value => !value)}>更多条件</Button>
            </Box>
            <Collapse in={expanded}>
                <Box id="email-delivery-filters" sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'minmax(200px, 1fr) repeat(2, minmax(160px, 1fr)) auto' }, gap: 1.5, pt: 2 }}>
                    <TextField select label="归属组织" name="orgUnitId" value={draft.orgUnitId} onChange={change} disabled={Boolean(scopeError)} sx={{ gridColumn: { xs: '1 / -1', md: 'auto' } }}>
                        <MenuItem value="">全部职责范围</MenuItem>{scopes.map(scope => <MenuItem key={scope.id} value={scope.id}>{scope.name}</MenuItem>)}
                    </TextField>
                    <TextField type="date" label="发送日期从" name="from" value={draft.from} onChange={change} slotProps={{ inputLabel: { shrink: true } }} />
                    <TextField type="date" label="发送日期至" name="to" value={draft.to} onChange={change} slotProps={{ inputLabel: { shrink: true } }} />
                    <Button onClick={() => { const reset = initialFilters(); setDraft(reset); setFilters(reset); setPage(0); }}>重置条件</Button>
                </Box>
            </Collapse>
        </Paper>
        {scopeError && <Alert severity="warning" sx={{ mt: 1 }}>{scopeError}</Alert>}
        {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
        <ListResultsBar summary={summary}>
            {result?.totalPages > 1 && <ListPagination count={result.totalPages} page={page + 1} onChange={(_, value) => setPage(value - 1)} disabled={loading} />}
        </ListResultsBar>
        {loading && <LinearProgress aria-label="正在查询邮件发送记录" />}
        {!error && !loading && !result?.content.length && <Paper variant="outlined" sx={{ p: 3, textAlign: 'center' }}><Typography color="text.secondary">当前条件下没有发送记录。台账从功能启用后开始记录。</Typography></Paper>}
        {!error && Boolean(result?.content.length) && (mobile ? <Box sx={{ display: 'grid', gap: 1 }}>
            {result.content.map(delivery => <Paper component="article" variant="outlined" key={delivery.id} sx={{ p: 1.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1 }}><Typography variant="body2">{options.categories[delivery.category]}</Typography><DeliveryStatus status={delivery.status} /></Box>
                <Typography variant="body2" sx={{ fontWeight: 600, my: 0.75, overflowWrap: 'anywhere' }}>{delivery.subject}</Typography>
                <Typography variant="body2" color="text.secondary">收件：{delivery.recipientAddress}</Typography>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1 }}><Typography variant="caption" color="text.secondary">{formatDate(delivery.startedAt)}</Typography><Button onClick={() => setSelectedId(delivery.id)}>查看详情</Button></Box>
            </Paper>)}
        </Box> : <ViewportTableContainer component={Paper} variant="outlined">
            <Table stickyHeader size="small" aria-label="邮件发送记录" sx={{ minWidth: 920 }}>
                <TableHead><TableRow>{['时间 / 类型', '主题', '收件 / 发件', '归属组织', '发送状态', '操作'].map(label => <TableCell key={label}>{label}</TableCell>)}</TableRow></TableHead>
                <TableBody>{result.content.map(delivery => <TableRow hover key={delivery.id}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}><Typography variant="body2">{formatDate(delivery.startedAt)}</Typography><Typography variant="caption" color="text.secondary">{options.categories[delivery.category]}</Typography></TableCell>
                    <TableCell sx={{ maxWidth: 360, overflowWrap: 'anywhere' }}>{delivery.subject}</TableCell>
                    <TableCell><Typography variant="body2">{delivery.recipientAddress}</Typography><Typography variant="caption" color="text.secondary">发：{delivery.senderAddress || '未选定发件方'}</Typography></TableCell>
                    <TableCell>{delivery.orgUnitName}</TableCell><TableCell><DeliveryStatus status={delivery.status} /></TableCell>
                    <TableCell><Button onClick={() => setSelectedId(delivery.id)} sx={{ whiteSpace: 'nowrap' }}>详情</Button></TableCell>
                </TableRow>)}</TableBody>
            </Table>
        </ViewportTableContainer>)}
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>已发送表示 SMTP 发件服务器已接受邮件，不代表收件箱送达或已读；时间为 UTC+8。</Typography>
        <Dialog open={Boolean(selectedId)} onClose={() => setSelectedId(null)} fullWidth maxWidth="sm">
            <DialogTitle>邮件发送详情</DialogTitle>
            <DialogContent dividers>
                {detailError && <Alert severity="error">{detailError}</Alert>}
                {!detail && !detailError && <Box sx={{ p: 3, textAlign: 'center' }}><CircularProgress size={28} /></Box>}
                {detail && <Box sx={{ display: 'grid', gap: 1.5 }}>
                    <Box><DeliveryStatus status={detail.status} /></Box>
                    {detail.failureMessage && <Alert severity="error">{detail.failureMessage}</Alert>}
                    <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: '80px minmax(0, 1fr)', columnGap: 2, rowGap: 1, '& dt': { color: 'text.secondary' }, '& dd': { m: 0, overflowWrap: 'anywhere' } }}>
                        {Object.entries({ '通知类型': options.categories[detail.category], '主题': detail.subject, '归属组织': detail.orgUnitName, '收件地址': detail.recipientAddress, '发件地址': detail.senderAddress || '未选定发件方', '开始时间': formatDate(detail.startedAt), '结束时间': detail.completedAt ? formatDate(detail.completedAt) : '未记录', '耗时': detail.durationMs == null ? '-' : `${detail.durationMs} ms`, '失败分类': detail.failureCode || '-', '关联编号': detail.relatedId || '-', '记录编号': detail.id }).map(([label, value]) => <Box key={label} sx={{ display: 'contents' }}><Typography component="dt" variant="body2">{label}</Typography><Typography component="dd" variant="body2">{value}</Typography></Box>)}
                    </Box>
                </Box>}
            </DialogContent>
            <DialogActions><Button onClick={() => setSelectedId(null)}>关闭</Button></DialogActions>
        </Dialog>
    </Container>;
}

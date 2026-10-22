import PageHeading from '../../components/layout/PageHeading';
import ListResultsBar, { ListPagination } from '../../components/layout/ListResultsBar';
import ViewportTableContainer from '../../components/layout/ViewportTableContainer';
import React, { useCallback, useEffect, useId, useMemo, useState } from 'react';
import {
    Alert,
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Autocomplete,
    Box,
    Button,
    Chip,
    CircularProgress,
    Container,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Skeleton,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Tab,
    Tabs,
    TextField,
    Typography,
    useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import TroubleshootIcon from '@mui/icons-material/Troubleshoot';
import StorageIcon from '@mui/icons-material/Storage';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import TuneIcon from '@mui/icons-material/Tune';

import apiClient from '../../services/apiClient';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDateTime } from '../../utils/formatDate.js';

const PAGE_SIZE = 10;
const PERIODS = {
    '24h': { label: '最近24小时', milliseconds: 24 * 60 * 60 * 1000 },
    '7d': { label: '最近7天', milliseconds: 7 * 24 * 60 * 60 * 1000 },
    '30d': { label: '最近30天', milliseconds: 30 * 24 * 60 * 60 * 1000 },
};
const SEVERITY = {
    FATAL: { label: '严重错误', color: 'error' },
    ERROR: { label: '错误', color: 'error' },
    WARN: { label: '警告', color: 'warning' },
    INFO: { label: '主动诊断', color: 'info' },
};
const PLATFORM_LABELS = { WEB: '网页端', ANDROID: '安卓端', SERVER: '服务端' };
const TYPE_LABELS = { ERROR: '错误', WARNING: '警告', OPERATION: '操作', DIAGNOSTIC: '诊断' };
const CATEGORY_LABELS = {
    SYSTEM: '系统', UI: '界面', NETWORK: '网络', AUTH: '登录认证', LOCATION: '定位',
    WORK_ORDER: '工单', ATTACHMENT: '附件', UPDATE: '应用升级', ADMIN: '后台管理',
};
const VIEW_LABELS = {
    DIAGNOSTIC: { label: '故障诊断', description: '错误、警告、失败操作和主动诊断' },
    BEHAVIOR: { label: '操作轨迹', description: '按用户查看页面访问和关键操作过程' },
};

export default function AdminObservabilityPage() {
    const theme = useTheme();
    const mobile = useMediaQuery(theme.breakpoints.down('md'));
    const [view, setView] = useState('DIAGNOSTIC');
    const [page, setPage] = useState(0);
    const [period, setPeriod] = useState('24h');
    const [platform, setPlatform] = useState('');
    const [severity, setSeverity] = useState('');
    const [category, setCategory] = useState('');
    const [keywordDraft, setKeywordDraft] = useState('');
    const [keyword, setKeyword] = useState('');
    const [selectedUser, setSelectedUser] = useState(null);
    const [filtersExpanded, setFiltersExpanded] = useState(false);
    const [userKeyword, setUserKeyword] = useState('');
    const [userOptions, setUserOptions] = useState([]);
    const [userSearchLoading, setUserSearchLoading] = useState(false);
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [detail, setDetail] = useState(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState('');
    const [redisStatus, setRedisStatus] = useState(null);
    const [redisLoading, setRedisLoading] = useState(true);

    const loadEvents = useCallback(async () => {
        setLoading(true);
        setError('');
        const to = new Date();
        const from = new Date(to.getTime() - PERIODS[period].milliseconds);
        try {
            const response = await apiClient.get('/admin/observability/events', {
                params: {
                    page,
                    size: PAGE_SIZE,
                    from: from.toISOString(),
                    to: to.toISOString(),
                    platform: platform || undefined,
                    severity: severity || undefined,
                    view,
                    category: category || undefined,
                    keyword: keyword || undefined,
                    userId: selectedUser?.id || undefined,
                },
            });
            setResult(response.data);
        } catch (requestError) {
            setError(formatApiErrorMessage(requestError, '运行诊断加载失败，请稍后重试。'));
        } finally {
            setLoading(false);
        }
    }, [category, keyword, page, period, platform, selectedUser?.id, severity, view]);

    useEffect(() => {
        const normalizedKeyword = userKeyword.trim();
        if (!normalizedKeyword) {
            setUserOptions(selectedUser ? [selectedUser] : []);
            setUserSearchLoading(false);
            return undefined;
        }
        let active = true;
        const timer = window.setTimeout(async () => {
            setUserSearchLoading(true);
            try {
                const response = await apiClient.get('/admin/observability/events/users', {
                    params: { keyword: normalizedKeyword, limit: 20 },
                });
                if (active) setUserOptions(Array.isArray(response.data) ? response.data : []);
            } catch {
                if (active) setUserOptions(selectedUser ? [selectedUser] : []);
            } finally {
                if (active) setUserSearchLoading(false);
            }
        }, 350);
        return () => {
            active = false;
            window.clearTimeout(timer);
        };
    }, [selectedUser, userKeyword]);

    const loadRedisStatus = useCallback(async () => {
        setRedisLoading(true);
        try {
            const response = await apiClient.get('/admin/observability/redis');
            setRedisStatus(response.data);
        } catch (requestError) {
            setRedisStatus({
                available: false,
                error: formatApiErrorMessage(requestError, 'Redis 运行状态暂时无法读取。'),
            });
        } finally {
            setRedisLoading(false);
        }
    }, []);

    useEffect(() => {
        loadEvents();
    }, [loadEvents]);

    useEffect(() => {
        loadRedisStatus();
    }, [loadRedisStatus]);

    const refreshAll = useCallback(() => {
        void Promise.all([loadEvents(), loadRedisStatus()]);
    }, [loadEvents, loadRedisStatus]);

    const submitSearch = (event) => {
        event.preventDefault();
        setPage(0);
        setKeyword(keywordDraft.trim());
    };

    const updateFilter = (setter) => (event) => {
        setPage(0);
        setter(event.target.value);
    };

    const openDetail = async (eventId) => {
        setDetail(null);
        setDetailError('');
        setDetailLoading(true);
        try {
            const response = await apiClient.get(`/admin/observability/events/${eventId}`);
            setDetail(response.data);
        } catch (requestError) {
            setDetailError(formatApiErrorMessage(requestError, '诊断详情加载失败，请稍后重试。'));
        } finally {
            setDetailLoading(false);
        }
    };

    const events = useMemo(() => result?.content || [], [result]);
    const pageCount = Math.max(1, result?.totalPages || 1);

    return (
        <Container maxWidth={false} disableGutters sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, mb: 1.5 }}>
                <Box sx={{ minWidth: 0 }}>
                    <PageHeading title="运行诊断" description="追踪客户端异常，查看用户操作与系统运行状态。" />
                </Box>
                <Button variant="outlined" color="inherit" onClick={refreshAll} disabled={loading || redisLoading} startIcon={<RefreshIcon />} sx={{ flexShrink: 0 }}>刷新</Button>
            </Box>

            <RedisStatusBand status={redisStatus} loading={redisLoading} compact={mobile} />

            <Paper variant="outlined" sx={{ borderRadius: '14px', overflow: 'hidden' }}>
              <Box sx={{ px: { xs: 0.5, sm: 1 }, borderBottom: 1, borderColor: 'divider' }}>
                <Tabs
                    value={view}
                    onChange={(_, nextView) => { setPage(0); setSeverity(''); setView(nextView); }}
                    variant={mobile ? 'fullWidth' : 'standard'}
                    aria-label="观测数据视图"
                >
                    {Object.entries(VIEW_LABELS).map(([value, item]) => (
                        <Tab key={value} value={value} label={item.label} />
                    ))}
                </Tabs>
              </Box>

              <Box component="form" onSubmit={submitSearch} sx={{ p: { xs: 1.5, sm: 2 }, borderBottom: 1, borderColor: 'divider' }}>
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'minmax(180px, 2fr) minmax(140px, 1.2fr) 148px auto auto' }, gap: 1.5, alignItems: 'start' }}>
                    <TextField
                        size="small"
                        label="关键词或错误编号"
                        value={keywordDraft}
                        onChange={(event) => setKeywordDraft(event.target.value)}
                        placeholder="事件、消息、页面、请求编号"
                        sx={{ gridColumn: { xs: '1 / -1', md: 'auto' } }}
                    />
                    <Box sx={{ display: { xs: filtersExpanded ? 'block' : 'none', md: 'block' }, gridColumn: { xs: '1 / -1', md: 'auto' } }}>
                        <Autocomplete
                            size="small"
                            options={userOptions}
                            value={selectedUser}
                            loading={userSearchLoading}
                            filterOptions={(options) => options}
                            isOptionEqualToValue={(option, value) => option.id === value.id}
                            getOptionLabel={(option) => [option.name || option.username, option.username, option.orgUnitName].filter(Boolean).join(' · ')}
                            onInputChange={(_, value, reason) => {
                                if (reason === 'input' || reason === 'clear') setUserKeyword(value);
                            }}
                            onChange={(_, value) => { setPage(0); setSelectedUser(value); }}
                            renderInput={(params) => {
                                const inputSlotProps = params.slotProps?.input ?? {};
                                return (
                                    <TextField
                                        {...params}
                                        label="用户"
                                        placeholder="输入姓名、账号或拼音"
                                        slotProps={{
                                            ...params.slotProps,
                                            input: {
                                                ...inputSlotProps,
                                                endAdornment: <>{userSearchLoading ? <CircularProgress size={16} /> : null}{inputSlotProps.endAdornment}</>,
                                            },
                                        }}
                                    />
                                );
                            }}
                            sx={{ gridColumn: { xs: '1 / -1', md: 'auto' } }}
                        />
                    </Box>
                    <Box sx={{ gridColumn: { xs: '1 / -1', md: 'auto' } }}>
                      <FilterSelect label="时间范围" value={period} onChange={updateFilter(setPeriod)} options={Object.entries(PERIODS).map(([value, item]) => [value, item.label])} />
                    </Box>
                    <Button color="inherit" startIcon={<TuneIcon />} aria-expanded={filtersExpanded} aria-controls="diagnostic-filters" onClick={() => setFiltersExpanded(value => !value)} sx={{ whiteSpace: 'nowrap' }}>筛选{[platform, severity, category, selectedUser?.id].filter(Boolean).length > 0 ? ` (${[platform, severity, category, selectedUser?.id].filter(Boolean).length})` : ''}</Button>
                    <Button type="submit" variant="contained" startIcon={<SearchIcon />}>查询</Button>
                    <Box id="diagnostic-filters" sx={{ display: filtersExpanded ? 'grid' : 'none', gridColumn: '1 / -1', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'repeat(3, minmax(0, 1fr))' }, gap: 1.5, pt: 1 }}>
                        <FilterSelect label="平台" value={platform} onChange={updateFilter(setPlatform)} options={Object.entries(PLATFORM_LABELS)} includeAll />
                        <FilterSelect label="级别" value={severity} onChange={updateFilter(setSeverity)} options={Object.entries(SEVERITY).map(([value, item]) => [value, item.label])} includeAll />
                        <FilterSelect label="功能" value={category} onChange={updateFilter(setCategory)} options={Object.entries(CATEGORY_LABELS)} includeAll />
                    </Box>
                </Box>
              </Box>

            {error && <Alert severity="error" sx={{ mb: 1.5 }}>{error}</Alert>}
            {loading && !result && <Box sx={{ minHeight: 240, display: 'grid', placeItems: 'center' }}><CircularProgress size={32} /></Box>}

            {!loading && !error && events.length === 0 && (
                <Box sx={{ py: 8, textAlign: 'center' }}>
                    <TroubleshootIcon color="disabled" sx={{ fontSize: 48 }} />
                    <Typography color="text.secondary" sx={{ mt: 1 }}>当前条件下没有{VIEW_LABELS[view].label}记录。</Typography>
                </Box>
            )}

            {result && (
                <ListResultsBar summary={`共 ${result.totalElements} 条 · ${VIEW_LABELS[view].description}`} sx={{ px: { xs: 1.5, sm: 2 } }}>
                    {pageCount > 1 && <ListPagination page={page + 1} count={pageCount} onChange={(_, value) => setPage(value - 1)} />}
                </ListResultsBar>
            )}

            {events.length > 0 && (mobile
                ? <MobileEventList events={events} onOpen={openDetail} />
                : <DesktopEventTable events={events} onOpen={openDetail} />)}
            </Paper>

            <ObservationDetailDialog
                open={detailLoading || Boolean(detail) || Boolean(detailError)}
                detail={detail}
                loading={detailLoading}
                error={detailError}
                onClose={() => { setDetail(null); setDetailError(''); setDetailLoading(false); }}
            />
        </Container>
    );
}

function RedisStatusBand({ status, loading, compact }) {
    if (loading && !status) {
        return <Skeleton variant="rounded" height={56} sx={{ mb: 1.5, borderRadius: 1.5 }} />;
    }
    if (!status?.available) {
        return <Alert severity="warning" sx={{ mb: 1.5 }}>{status?.error || 'Redis 暂时不可用。'}</Alert>;
    }
    const metrics = [
        ['内存', `${formatBytes(status.usedMemoryBytes)} / ${formatBytes(status.maxMemoryBytes)}`],
        ['缓存命中率', `${formatNumber(status.hitRate, 2)}%`],
        ['连接数', formatNumber(status.connectedClients)],
        ['键数量', formatNumber(status.totalKeys)],
        ['持久化', status.aofEnabled && status.aofLastWriteStatus === 'ok' ? 'AOF 正常' : '请检查'],
        ['诊断队列', `${formatNumber(status.observationReadyBatches)} / ${formatNumber(status.observationProcessingBatches)} / ${formatNumber(status.observationDeadLetterBatches)}`],
    ];
    return (
        <Accordion sx={{ mb: 1.5 }}>
            <AccordionSummary expandIcon={<ExpandMoreIcon fontSize="small" />}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, width: '100%', minWidth: 0 }}>
                    <StorageIcon color="success" fontSize="small" />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>Redis 运行正常</Typography>
                    {!compact && <Typography variant="body2" color="text.secondary" sx={{ ml: 'auto', mr: 2 }}>内存 {formatBytes(status.usedMemoryBytes)} · 命中率 {formatNumber(status.hitRate, 2)}%</Typography>}
                </Box>
            </AccordionSummary>
            <AccordionDetails sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(6, minmax(0, 1fr))' }, gap: 2, pt: 1 }}>
                {metrics.map(([label, value]) => <Box key={label} sx={{ minWidth: 0 }}>
                    <Typography variant="caption" color="text.secondary">{label}</Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{value}</Typography>
                </Box>)}
                <Typography variant="caption" color="text.secondary" sx={{ gridColumn: '1 / -1' }}>Redis v{status.version || '-'}</Typography>
            </AccordionDetails>
        </Accordion>
    );
}

function FilterSelect({ label, value, onChange, options, includeAll = false }) {
    const id = useId();
    return (
        <FormControl size="small" fullWidth>
            <InputLabel id={`${id}-label`}>{label}</InputLabel>
            <Select labelId={`${id}-label`} id={id} label={label} value={value} onChange={onChange}>
                {includeAll && <MenuItem value="">全部</MenuItem>}
                {options.map(([optionValue, optionLabel]) => <MenuItem key={optionValue} value={optionValue}>{optionLabel}</MenuItem>)}
            </Select>
        </FormControl>
    );
}

function DesktopEventTable({ events, onOpen }) {
    return (
        <ViewportTableContainer>
            <Table size="small" aria-label="运行诊断列表" stickyHeader sx={{ tableLayout: 'fixed', minWidth: 880 }}>
                <colgroup><col style={{ width: 160 }} /><col style={{ width: 200 }} /><col /><col style={{ width: 200 }} /><col style={{ width: 140 }} /></colgroup>
                <TableHead>
                    <TableRow>
                        <TableCell>时间与级别</TableCell><TableCell>事件与来源</TableCell>
                        <TableCell>消息</TableCell><TableCell>用户与环境</TableCell><TableCell>错误编号</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {events.map(event => (
                        <TableRow key={event.id} hover onClick={() => onOpen(event.id)} onKeyDown={keyEvent => {
                            if (keyEvent.key === 'Enter' || keyEvent.key === ' ') { keyEvent.preventDefault(); onOpen(event.id); }
                        }} sx={{ cursor: 'pointer' }} tabIndex={0}>
                            <TableCell><Typography variant="body2" sx={{ whiteSpace: 'nowrap', mb: 0.75, fontVariantNumeric: 'tabular-nums' }}>{formatTime(event.clientOccurredAt || event.receivedAt)}</Typography><SeverityChip severity={event.severity} eventType={event.eventType} /></TableCell>
                            <TableCell><Typography variant="body2" sx={{ fontWeight: 600 }}>{CATEGORY_LABELS[event.category] || event.category} · {PLATFORM_LABELS[event.platform] || event.platform}</Typography><Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflowWrap: 'anywhere', mt: 0.5 }}>{event.eventName}</Typography></TableCell>
                            <TableCell><Typography variant="body2" title={event.message} sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', overflowWrap: 'anywhere' }}>{event.message || '-'}</Typography></TableCell>
                            <TableCell><Typography variant="body2" noWrap title={`${event.orgUnitName || '租户级'} / ${event.userName || '未登录'}`}>{event.orgUnitName || '租户级'} / {event.userName || '未登录'}</Typography><Typography variant="caption" color="text.secondary" noWrap title={environmentLabel(event)} sx={{ display: 'block', mt: 0.5 }}>{environmentLabel(event)}</Typography></TableCell>
                            <TableCell><Typography variant="caption" noWrap title={event.otelTraceId || event.traceId} sx={{ display: 'block', fontFamily: 'monospace' }}>{event.otelTraceId || event.traceId || '-'}</Typography></TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </ViewportTableContainer>
    );
}

function MobileEventList({ events, onOpen }) {
    return <Box>{events.map(event => (
        <Box key={event.id} component="button" type="button" onClick={() => onOpen(event.id)} sx={{ border: 0, borderTop: 1, borderColor: 'divider', p: 2, textAlign: 'left', bgcolor: 'background.paper', color: 'text.primary', width: '100%', cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' }, '&:focus-visible': { outline: '3px solid rgba(37, 99, 235, 0.28)', outlineOffset: -3 } }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}><SeverityChip severity={event.severity} eventType={event.eventType} /><Typography variant="caption" color="text.secondary">{formatTime(event.clientOccurredAt || event.receivedAt)}</Typography><Typography variant="caption" sx={{ ml: 'auto' }}>{PLATFORM_LABELS[event.platform] || event.platform}</Typography></Box>
            <Typography variant="body2" sx={{ mt: 1, fontWeight: 600, overflowWrap: 'anywhere' }}>{CATEGORY_LABELS[event.category] || event.category} · {event.eventName}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, overflowWrap: 'anywhere', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{event.message || '没有附加消息'}</Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75 }}>{environmentLabel(event)} · {event.orgUnitName || '租户级'} / {event.userName || '未登录'}</Typography>
            <Typography variant="caption" color="primary" sx={{ display: 'block', mt: 1 }}>查看详情 →</Typography>
        </Box>
    ))}</Box>;
}

function ObservationDetailDialog({ open, detail, loading, error, onClose }) {
    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
            <DialogTitle>运行诊断详情</DialogTitle>
            <DialogContent dividers>
                {loading && <Box sx={{ minHeight: 180, display: 'grid', placeItems: 'center' }}><CircularProgress size={30} /></Box>}
                {error && <Alert severity="error">{error}</Alert>}
                {detail && <Box sx={{ display: 'grid', gap: 2 }}>
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 1 }}>
                        <DetailValue label="发生时间" value={formatTime(detail.clientOccurredAt || detail.receivedAt)} />
                        <DetailValue label="级别 / 类型" value={`${SEVERITY[detail.severity]?.label || detail.severity} / ${TYPE_LABELS[detail.eventType] || detail.eventType}`} />
                        <DetailValue label="功能 / 事件" value={`${CATEGORY_LABELS[detail.category] || detail.category} / ${detail.eventName}`} />
                        <DetailValue label="平台" value={PLATFORM_LABELS[detail.platform] || detail.platform} />
                        <DetailValue label="页面或接口" value={detail.route || '-'} />
                        <DetailValue label="协议 / 信号" value={`${detail.observationSchemaVersion === 2 ? 'ereport-otel/1' : '旧协议'} / ${detail.signalType || '-'}`} />
                        <DetailValue label="请求编号" value={detail.requestId || '-'} />
                        <DetailValue label="Trace ID" value={detail.otelTraceId || '-'} />
                        <DetailValue label="Span ID / 父 Span" value={`${detail.otelSpanId || '-'} / ${detail.otelParentSpanId || '-'}`} />
                        <DetailValue label="Scope" value={[detail.scopeName, detail.scopeVersion].filter(Boolean).join(' / ') || '-'} />
                        <DetailValue label="Span / 状态" value={[detail.spanName, detail.spanKind, detail.spanStatusCode].filter(Boolean).join(' / ') || '-'} />
                        <DetailValue label="耗时" value={formatDurationNanos(detail.durationNanos)} />
                        <DetailValue label="组织 / 用户" value={`${detail.orgUnitName || '租户级'} / ${detail.userName || '未登录'}`} />
                        <DetailValue label="应用环境" value={environmentLabel(detail)} />
                        <DetailValue label="网络" value={detail.networkType || '-'} />
                        <DetailValue label="安装会话" value={detail.installationId || '-'} />
                    </Box>
                    <DetailBlock label="消息" value={detail.message} />
                    <DetailBlock label="错误堆栈" value={detail.stackTrace} monospace />
                    <DetailBlock label="Resource" value={formatJson(detail.resource)} monospace />
                    <DetailBlock label="Attributes" value={formatJson(detail.attributes)} monospace />
                    <DetailBlock label="Span Events" value={formatJson(detail.spanEvents)} monospace />
                    <DetailBlock label="诊断上下文" value={formatJson(detail.context)} monospace />
                    <DetailBlock label="最近操作" value={formatJson(detail.breadcrumbs)} monospace />
                </Box>}
            </DialogContent>
            <DialogActions><Button onClick={onClose}>关闭</Button></DialogActions>
        </Dialog>
    );
}

function SeverityChip({ severity, eventType }) {
    const item = eventType === 'OPERATION'
        ? (severity === 'INFO' ? { label: '成功', color: 'success' } : { label: '失败', color: 'warning' })
        : (SEVERITY[severity] || { label: severity || '未知', color: 'default' });
    return <Chip size="small" label={item.label} color={item.color} variant={severity === 'INFO' ? 'outlined' : 'filled'} />;
}

function DetailValue({ label, value }) {
    return <Box sx={{ minWidth: 0 }}><Typography variant="caption" color="text.secondary">{label}</Typography><Typography variant="body2" sx={{ mt: 0.25, overflowWrap: 'anywhere' }}>{value}</Typography></Box>;
}

function DetailBlock({ label, value, monospace = false }) {
    if (!value) return null;
    return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Box component="pre" sx={{ m: 0, mt: 0.5, p: 1.25, maxHeight: 260, overflow: 'auto', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', border: '1px solid', borderColor: 'divider', borderRadius: 1, bgcolor: 'action.hover', fontFamily: monospace ? 'monospace' : 'inherit', fontSize: 13 }}>{value}</Box></Box>;
}

function environmentLabel(event) {
    if (event.platform === 'WEB') {
        return [
            event.osVersion || event.osName || '',
            event.deviceModel ? `浏览器 ${event.deviceModel}` : '',
            event.appVersion && event.appVersion !== 'web' ? `应用 ${event.appVersion}` : '',
        ].filter(Boolean).join(' · ') || '-';
    }
    return [
        event.appVersion && event.appVersion !== 'web' ? `应用 ${event.appVersion}` : '',
        event.manufacturer ? `厂商 ${event.manufacturer}` : '',
        event.deviceModel ? `型号 ${event.deviceModel}` : '',
        event.osVersion ? `系统 ${event.osVersion}` : '',
    ].filter(Boolean).join(' · ') || '-';
}

function formatTime(value) {
    return formatDateTime(value);
}

function formatJson(value) {
    if (value == null) return '';
    try { return JSON.stringify(value, null, 2); } catch { return String(value); }
}

function formatBytes(value) {
    if (!Number.isFinite(Number(value)) || Number(value) <= 0) return '-';
    const units = ['B', 'KB', 'MB', 'GB'];
    let amount = Number(value);
    let unitIndex = 0;
    while (amount >= 1024 && unitIndex < units.length - 1) {
        amount /= 1024;
        unitIndex += 1;
    }
    return `${amount.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function formatNumber(value, maximumFractionDigits = 0) {
    const numericValue = Number(value);
    if (!Number.isFinite(numericValue)) return '-';
    return numericValue.toLocaleString('zh-CN', { maximumFractionDigits });
}

function formatDurationNanos(value) {
    const nanos = Number(value);
    if (!Number.isFinite(nanos) || nanos < 0) return '-';
    if (nanos < 1_000_000) return `${Math.round(nanos / 1_000)} 微秒`;
    if (nanos < 1_000_000_000) return `${(nanos / 1_000_000).toFixed(1)} 毫秒`;
    return `${(nanos / 1_000_000_000).toFixed(2)} 秒`;
}

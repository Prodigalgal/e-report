import PageHeading from '../../components/layout/PageHeading';
import React, { useEffect, useMemo, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    CircularProgress,
    Container,
    LinearProgress,
    FormControl,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';
import SummarizeIcon from '@mui/icons-material/Summarize';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { WORK_ORDER_TYPE_CODES, getReportTypeLabel } from '../../constants/workOrderCatalog';
import { DASHBOARD_STATISTICS_PERMISSIONS, hasAnyPermission } from '../../constants/permissions';
import { useAuth } from '../../hooks/useAuth';
import { fetchWorkOrderStatistics, fetchWorkOrderStatisticsScopes } from '../../services/workOrderApiService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDuration } from '../../constants/workOrderSla';
import {
    durationBucketPresentation,
    reportTypePresentation,
    slaRiskPresentation,
    statusPresentation,
} from '../../constants/workOrderPresentation';

const numberFormatter = new Intl.NumberFormat('zh-CN');
const DASHBOARD_STATISTICS_CACHE_TTL_MS = 60 * 1000;
const DASHBOARD_STATISTICS_CACHE_PREFIX = 'dashboardStatistics:v2';

function formatNumber(value) {
    return numberFormatter.format(Number(value || 0));
}

function formatPercent(value) {
    return `${Number(value || 0).toFixed(1)}%`;
}

function formatDateLabel(value) {
    if (!value) {
        return '';
    }
    const [, month = '', day = ''] = value.split('-');
    return `${month}/${day}`;
}

function handledCount(item) {
    return Number(item?.resolved || 0) + Number(item?.closed || 0);
}

function handledRate(item) {
    const total = Number(item?.total || 0);
    if (total <= 0) {
        return 0;
    }
    return Math.round((handledCount(item) * 1000) / total) / 10;
}

function buildStatisticsCacheKey(user, countyOrgUnitId) {
    if (!user?.username) {
        return null;
    }
    const permissionFingerprint = Array.isArray(user.permissions)
        ? [...user.permissions].sort().join('|')
        : '';
    return `${DASHBOARD_STATISTICS_CACHE_PREFIX}:${user.tenantId || 'default'}:${user.membershipId || user.username}:${user.authorizationVersion || 1}:${countyOrgUnitId || 'all'}:${permissionFingerprint}`;
}

function readCachedStatistics(cacheKey) {
    if (!cacheKey) {
        return null;
    }
    try {
        const rawCache = localStorage.getItem(cacheKey);
        if (!rawCache) {
            return null;
        }
        const cache = JSON.parse(rawCache);
        if (!cache?.savedAt || Date.now() - Number(cache.savedAt) > DASHBOARD_STATISTICS_CACHE_TTL_MS) {
            localStorage.removeItem(cacheKey);
            return null;
        }
        return cache.data || null;
    } catch {
        localStorage.removeItem(cacheKey);
        return null;
    }
}

function writeCachedStatistics(cacheKey, data) {
    if (!cacheKey || !data) {
        return;
    }
    try {
        localStorage.setItem(cacheKey, JSON.stringify({
            savedAt: Date.now(),
            data,
        }));
    } catch {
        // Dashboard cache is an optimization only; failing to persist it must not break rendering.
    }
}

function dashboardErrorMessage(error) {
    const status = error.response?.status;
    if (status === 401) {
        return '登录状态已过期，请重新登录后查看仪表盘。';
    }
    if (status === 403) {
        return '当前账号没有查看工单统计的权限。';
    }
    return formatApiErrorMessage(error, '工单统计加载失败，请稍后重试。');
}

const isCanceledRequest = (error) => error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError';

const TypeMetricCard = React.memo(function TypeMetricCard({ item }) {
    const total = Number(item?.total || 0);
    const pending = Number(item?.pending || 0);
    const handled = handledCount(item);
    const rate = handledRate(item);
    const typeName = item?.reportTypeName || getReportTypeLabel(item?.reportType);
    const color = reportTypePresentation(item?.reportType).foreground;
    const extraMetric = item?.reportType === WORK_ORDER_TYPE_CODES.BOX_DOOR_OPEN
        ? { label: '箱体数量', value: formatNumber(item?.boxQuantityTotal) }
        : item?.reportType === WORK_ORDER_TYPE_CODES.DAMAGED_BOX_REPLACEMENT
            ? {
                label: '换箱结果',
                value: `${formatNumber(item?.replacementCompleted)} / ${formatNumber(item?.replacementRequired)}`,
            }
            : { label: '已关闭', value: formatNumber(item?.closed) };
    const metricItems = [
        { label: '待处理', value: formatNumber(pending) },
        { label: '已处理', value: formatNumber(handled) },
        { label: '处理率', value: formatPercent(rate) },
        extraMetric,
    ];

    return (
        <Card variant="outlined" sx={{ height: '100%', borderRadius: '14px' }}>
            <CardContent
                sx={{
                    p: 2.5,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 2,
                    '&:last-child': { pb: 2.5 },
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        gap: 1,
                        alignItems: 'center',
                        minWidth: 0,
                    }}
                >
                    <Typography
                        variant="h6"
                        component="h2"
                        sx={{ fontWeight: 600, fontSize: '1rem', overflowWrap: 'anywhere' }}
                    >
                        {typeName}
                    </Typography>
                    <Box
                        sx={{
                            width: 36,
                            height: 36,
                            borderRadius: 1,
                            bgcolor: reportTypePresentation(item?.reportType).surface,
                            color,
                            display: 'grid',
                            placeItems: 'center',
                            flex: '0 0 auto',
                        }}
                    >
                        <SummarizeIcon sx={{ fontSize: 21 }} />
                    </Box>
                </Box>

                <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(90px, auto) minmax(0, 1fr)', gap: 1.25, alignItems: 'end' }}>
                    <Box>
                        <Typography component="div" sx={{ fontWeight: 600, lineHeight: 1.2, fontSize: '2.25rem', fontVariantNumeric: 'tabular-nums' }}>
                            {formatNumber(total)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">工单总量</Typography>
                    </Box>
                    <Box sx={{ pb: 0.4 }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, mb: 0.5 }}>
                            <Typography variant="caption" color="text.secondary">处理进度</Typography>
                            <Typography variant="caption" sx={{ fontWeight: 800, color }}>{formatPercent(rate)}</Typography>
                        </Box>
                        <LinearProgress
                            variant="determinate"
                            value={rate}
                            sx={{
                                height: 7,
                                borderRadius: 1,
                                bgcolor: 'grey.200',
                                '& .MuiLinearProgress-bar': { bgcolor: color },
                            }}
                        />
                    </Box>
                </Box>

                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', columnGap: 2, rowGap: 1.25, pt: 1.5, borderTop: 1, borderColor: 'divider' }}>
                    {metricItems.map(metric => (
                        <Box
                            key={metric.label}
                            sx={{
                                minWidth: 0,
                                display: 'flex',
                                alignItems: 'baseline',
                                justifyContent: 'space-between',
                                gap: 0.75,
                            }}
                        >
                            <Typography variant="caption" color="text.secondary" noWrap>{metric.label}</Typography>
                            <Typography component="div" noWrap sx={{ fontWeight: 600, lineHeight: 1.3, fontSize: '1rem', fontVariantNumeric: 'tabular-nums' }}>
                                {metric.value}
                            </Typography>
                        </Box>
                    ))}
                </Box>
            </CardContent>
        </Card>
    );
});

function SlaDurationSection({ statistics }) {
    const neutralTone = durationBucketPresentation('WITHIN_24_HOURS');
    const breachedTone = slaRiskPresentation('BREACHED');
    const metrics = [
        { label: '活动工单', value: formatNumber(statistics?.active), tone: neutralTone },
        { label: '24小时内', value: formatNumber(statistics?.within24Hours), tone: durationBucketPresentation('WITHIN_24_HOURS') },
        { label: '24-48小时', value: formatNumber(statistics?.from24To48Hours), tone: durationBucketPresentation('HOURS_24_TO_48') },
        { label: '48-72小时', value: formatNumber(statistics?.from48To72Hours), tone: durationBucketPresentation('HOURS_48_TO_72') },
        { label: '超过72小时', value: formatNumber(statistics?.over72Hours), tone: durationBucketPresentation('OVER_72_HOURS') },
        { label: '当前时限超时', value: formatNumber(statistics?.currentSlaBreached), tone: breachedTone },
        { label: '曾发生超时', value: formatNumber(statistics?.everSlaBreached), tone: breachedTone },
        { label: '最长未闭环', value: formatDuration(statistics?.longestActiveElapsedSeconds), tone: neutralTone },
    ];
    return (
        <Paper variant="outlined" sx={{ p: 2.5, mb: 2.5, borderRadius: '14px' }}>
            <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600, mb: 2.5 }}>故障历时与处理时限</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'repeat(4, minmax(0, 1fr))' }, gap: 2.5 }}>
                {metrics.map(metric => (
                    <Box key={metric.label} sx={{ minWidth: 0 }}>
                        <Typography variant="caption" color="text.secondary">{metric.label}</Typography>
                        <Typography variant="h6" sx={{ color: metric.value === '0' || metric.value === '0秒' ? 'text.primary' : metric.tone.foreground, fontSize: '1.5rem', fontWeight: 600, overflowWrap: 'anywhere', mt: 0.5, fontVariantNumeric: 'tabular-nums' }}>
                            {metric.value}
                        </Typography>
                    </Box>
                ))}
            </Box>
        </Paper>
    );
}

const ChartPanel = React.memo(function ChartPanel({ title, subtitle, children }) {
    return (
        <Paper
            elevation={1}
            sx={{
                height: '100%',
                minHeight: { xs: 210, sm: 220 },
                p: 2.5,
                borderRadius: '14px',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', columnGap: 1, rowGap: 0.25, mb: 1 }}>
                <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600 }}>
                    {title}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                    {subtitle}
                </Typography>
            </Box>
            <Box sx={{ flex: 1, minHeight: 0 }}>
                {children}
            </Box>
        </Paper>
    );
});

const WorkOrderTypeChart = React.memo(function WorkOrderTypeChart({ data }) {
    const total = data.reduce((sum, item) => sum + Number(item.total || 0), 0);

    return (
        <Box sx={{ display: 'grid', gap: 1 }}>
            {data.map(item => {
                const count = Number(item.total || 0);
                const percent = total > 0 ? Math.round((count * 1000) / total) / 10 : 0;
                const handled = Number(item.resolved || 0) + Number(item.closed || 0);
                return (
                    <Box key={item.reportType}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, mb: 0.75 }}>
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                {item.reportTypeName}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                                {formatNumber(count)} 单
                            </Typography>
                        </Box>
                        <LinearProgress
                            variant="determinate"
                            value={percent}
                            sx={{
                                height: 8,
                                borderRadius: 1,
                                bgcolor: 'grey.200',
                                '& .MuiLinearProgress-bar': {
                                    bgcolor: reportTypePresentation(item.reportType).foreground,
                                },
                            }}
                        />
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                            占比 {formatPercent(percent)} · 待处理 {formatNumber(item.pending)} · 已处理 {formatNumber(handled)}
                        </Typography>
                    </Box>
                );
            })}
        </Box>
    );
});

const StatusDonutChart = React.memo(function StatusDonutChart({ data, total }) {
    const normalizedTotal = Number(total || 0);
    let cursor = 0;
    const gradientStops = data
        .filter(item => Number(item.total || 0) > 0)
        .map(item => {
            const value = Number(item.total || 0);
            const start = cursor;
            const end = cursor + (value / normalizedTotal) * 360;
            cursor = end;
            const color = statusPresentation(item.status).foreground;
            return `${color} ${start}deg ${end}deg`;
        });
    const donutBackground = normalizedTotal > 0 && gradientStops.length > 0
        ? `conic-gradient(${gradientStops.join(', ')})`
        : 'conic-gradient(#e5e7eb 0deg 360deg)';

    return (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 2, sm: 3 }, flexWrap: 'wrap', justifyContent: { xs: 'center', sm: 'flex-start' } }}>
            <Box
                sx={{
                    width: { xs: 104, sm: 116 },
                    height: { xs: 104, sm: 116 },
                    borderRadius: '50%',
                    background: donutBackground,
                    position: 'relative',
                    flex: '0 0 auto',
                    '&::after': {
                        content: '""',
                        position: 'absolute',
                        inset: { xs: 23, sm: 26 },
                        bgcolor: 'background.paper',
                        borderRadius: '50%',
                    },
                }}
            >
                <Box
                    sx={{
                        position: 'absolute',
                        inset: 0,
                        zIndex: 1,
                        display: 'grid',
                        placeItems: 'center',
                        textAlign: 'center',
                    }}
                >
                    <Box>
                        <Typography variant="h5" sx={{ fontWeight: 700 }}>
                            {formatNumber(normalizedTotal)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            工单
                        </Typography>
                    </Box>
                </Box>
            </Box>
            <Box sx={{ display: 'grid', gap: 1, minWidth: { xs: '100%', sm: 180 }, flex: 1 }}>
                {data.map(item => {
                    const value = Number(item.total || 0);
                    const percent = normalizedTotal > 0 ? Math.round((value * 1000) / normalizedTotal) / 10 : 0;
                    return (
                        <Box key={item.status} sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                            <Box
                                sx={{
                                    width: 10,
                                    height: 10,
                                    borderRadius: '50%',
                                    bgcolor: statusPresentation(item.status).foreground,
                                    flex: '0 0 auto',
                                }}
                            />
                            <Typography variant="body2" sx={{ flex: 1 }}>
                                {item.status}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                                {formatNumber(value)} · {formatPercent(percent)}
                            </Typography>
                        </Box>
                    );
                })}
            </Box>
        </Box>
    );
});

const DailyTrendChart = React.memo(function DailyTrendChart({ data }) {
    const chartWidth = 520;
    const chartHeight = 150;
    const padding = 24;
    const values = data.map(item => Number(item.total || 0));
    const maxValue = Math.max(1, ...values);
    const usableWidth = chartWidth - padding * 2;
    const usableHeight = chartHeight - padding * 2;
    const points = data.map((item, index) => {
        const x = padding + (data.length === 1 ? usableWidth / 2 : (usableWidth / (data.length - 1)) * index);
        const y = padding + usableHeight - (Number(item.total || 0) / maxValue) * usableHeight;
        return { ...item, x, y };
    });
    const pointList = points.map(point => `${point.x},${point.y}`).join(' ');

    return (
        <Box sx={{ width: '100%', overflow: 'hidden' }}>
            <svg viewBox={`0 0 ${chartWidth} ${chartHeight}`} role="img" aria-label="近7天新增工单趋势" style={{ width: '100%', height: '130px' }}>
                {[0, 1, 2].map(line => {
                    const y = padding + (usableHeight / 2) * line;
                    return <line key={line} x1={padding} x2={chartWidth - padding} y1={y} y2={y} stroke="#e5e7eb" strokeWidth="1" />;
                })}
                <polyline points={pointList} fill="none" stroke={reportTypePresentation('NETWORK_FAULT').foreground} strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />
                {points.map(point => (
                    <g key={point.date}>
                        <circle cx={point.x} cy={point.y} r="4" fill="#2563eb" />
                        <text x={point.x} y={point.y - 9} textAnchor="middle" fontSize="11" fill="#475569">
                            {point.total}
                        </text>
                        <text x={point.x} y={chartHeight - 5} textAnchor="middle" fontSize="10" fill="#64748b">
                            {formatDateLabel(point.date)}
                        </text>
                    </g>
                ))}
            </svg>
        </Box>
    );
});

function CountyComparisonSection({ data, onSelect }) {
    if (!Array.isArray(data) || data.length === 0) {
        return null;
    }
    return (
        <Paper variant="outlined" sx={{ mb: 2.5, borderRadius: '14px', overflow: 'hidden' }}>
            <Box sx={{ px: 2.5, py: 2, borderBottom: 1, borderColor: 'divider' }}>
                <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600 }}>区县数据对比</Typography>
            </Box>
            <TableContainer sx={{ maxHeight: 420 }}>
                <Table stickyHeader size="small" sx={{ minWidth: 760 }}>
                    <TableHead>
                        <TableRow>
                            <TableCell>区县</TableCell>
                            <TableCell align="right">工单总量</TableCell>
                            <TableCell align="right">在途</TableCell>
                            <TableCell align="right">已闭环</TableCell>
                            <TableCell align="right">闭环率</TableCell>
                            <TableCell align="right">当前超时</TableCell>
                            <TableCell align="right">超过72小时</TableCell>
                            <TableCell align="right">操作</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.map(item => (
                            <TableRow hover key={item.countyOrgUnitId}>
                                <TableCell sx={{ fontWeight: 700 }}>{item.countyName}</TableCell>
                                <TableCell align="right">{formatNumber(item.total)}</TableCell>
                                <TableCell align="right">{formatNumber(item.active)}</TableCell>
                                <TableCell align="right">{formatNumber(item.closed)}</TableCell>
                                <TableCell align="right">{formatPercent(item.resolutionRate)}</TableCell>
                                <TableCell align="right" sx={{ color: Number(item.currentSlaBreached) > 0 ? slaRiskPresentation('BREACHED').foreground : 'text.primary' }}>{formatNumber(item.currentSlaBreached)}</TableCell>
                                <TableCell align="right" sx={{ color: Number(item.over72Hours) > 0 ? durationBucketPresentation('OVER_72_HOURS').foreground : 'text.primary' }}>{formatNumber(item.over72Hours)}</TableCell>
                                <TableCell align="right">
                                    <Button size="small" endIcon={<ArrowForwardIcon />} onClick={() => onSelect(item.countyOrgUnitId)}>
                                        查看
                                    </Button>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    );
}

function StatisticsSection({ statistics, loading, error, onCountySelect }) {
    const statusData = useMemo(() => {
        const source = Array.isArray(statistics?.byStatus) ? statistics.byStatus : [];
        if (source.length > 0) {
            return source;
        }
        return [{ status: '待处理', total: 0 }];
    }, [statistics]);
    const typeStatistics = useMemo(() => statistics?.byType || [], [statistics]);

    if (loading && !statistics) {
        return (
            <Paper elevation={1} sx={{ p: 3, mb: 3, borderRadius: 1, textAlign: 'center' }}>
                <CircularProgress size={28} />
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    正在加载工单统计
                </Typography>
            </Paper>
        );
    }

    return (
        <Box sx={{ width: '100%', minWidth: 0 }}>
            {error && <Alert severity="warning" sx={{ mb: 2 }}>{error}</Alert>}
            <SlaDurationSection statistics={statistics} />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(3, minmax(0, 1fr))' }, gap: 2.5, mb: 2.5, width: '100%' }}>
                {typeStatistics.map(item => (
                    <Box key={item.reportType} sx={{ minWidth: 0, width: '100%' }}>
                        <TypeMetricCard item={item} />
                    </Box>
                ))}
            </Box>

            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' },
                    gap: 2.5,
                    width: '100%',
                }}
            >
                <Box sx={{ minWidth: 0 }}>
                    <ChartPanel title="类型分布" subtitle="按三类工单统计数量和处理进度">
                        <WorkOrderTypeChart data={statistics?.byType || []} />
                    </ChartPanel>
                </Box>
                <Box sx={{ minWidth: 0 }}>
                    <ChartPanel title="状态分布" subtitle="当前权限范围内的工单状态结构">
                        <StatusDonutChart data={statusData} total={statistics?.total} />
                    </ChartPanel>
                </Box>
                <Box sx={{ minWidth: 0 }}>
                    <ChartPanel title="近7天新增" subtitle="按报单时间统计新增趋势">
                        <DailyTrendChart data={statistics?.dailyNewReports || []} />
                    </ChartPanel>
                </Box>
            </Box>
            <Box sx={{ mt: 2.5 }}>
                <CountyComparisonSection data={statistics?.countyComparison} onSelect={onCountySelect} />
            </Box>
        </Box>
    );
}

function AdminMainDashboardPage() {
    const { currentUser } = useAuth();
    const [statistics, setStatistics] = useState(null);
    const [statisticsLoading, setStatisticsLoading] = useState(true);
    const [statisticsError, setStatisticsError] = useState('');
    const [statisticsScopes, setStatisticsScopes] = useState([]);
    const [selectedCountyOrgUnitId, setSelectedCountyOrgUnitId] = useState('');
    const [scopesLoading, setScopesLoading] = useState(true);
    const [scopesError, setScopesError] = useState('');
    const canViewStatistics = useMemo(
        () => hasAnyPermission(currentUser, DASHBOARD_STATISTICS_PERMISSIONS),
        [currentUser]
    );
    const statisticsCacheKey = useMemo(
        () => buildStatisticsCacheKey(currentUser, selectedCountyOrgUnitId),
        [currentUser, selectedCountyOrgUnitId]
    );

    useEffect(() => {
        let isCancelled = false;
        const controller = new AbortController();
        const loadScopes = async () => {
            if (!canViewStatistics) {
                setStatisticsScopes([]);
                setScopesLoading(false);
                return;
            }
            setScopesLoading(true);
            setScopesError('');
            try {
                const response = await fetchWorkOrderStatisticsScopes({ signal: controller.signal });
                if (isCancelled) return;
                const scopes = Array.isArray(response.data) ? response.data : [];
                setStatisticsScopes(scopes);
                setSelectedCountyOrgUnitId(current => (
                    current && scopes.some(scope => scope.countyOrgUnitId === current) ? current : ''
                ));
            } catch (error) {
                if (!isCanceledRequest(error) && !controller.signal.aborted && !isCancelled) {
                    setScopesError(formatApiErrorMessage(error, '加载区县统计范围失败，请稍后重试。'));
                }
            } finally {
                if (!isCancelled) setScopesLoading(false);
            }
        };
        loadScopes();
        return () => {
            isCancelled = true;
            controller.abort();
        };
    }, [canViewStatistics, currentUser?.membershipId]);

    useEffect(() => {
        let isCancelled = false;

        const controller = new AbortController();

        const loadStatistics = async () => {
            if (!canViewStatistics) {
                setStatistics(null);
                setStatisticsLoading(false);
                setStatisticsError('当前账号没有查看工单统计的权限。');
                return;
            }

            const cachedStatistics = readCachedStatistics(statisticsCacheKey);
            if (cachedStatistics) {
                setStatistics(cachedStatistics);
                setStatisticsLoading(false);
            } else {
                setStatisticsLoading(true);
            }
            setStatisticsError('');
            try {
                const response = await fetchWorkOrderStatistics(
                    selectedCountyOrgUnitId || null,
                    { signal: controller.signal },
                );
                if (!isCancelled) {
                    setStatistics(response.data);
                    writeCachedStatistics(statisticsCacheKey, response.data);
                }
            } catch (error) {
                if (isCanceledRequest(error) || controller.signal.aborted) {
                    return;
                }
                if (!isCancelled) {
                    setStatisticsError(dashboardErrorMessage(error));
                }
            } finally {
                if (!isCancelled) {
                    setStatisticsLoading(false);
                }
            }
        };

        loadStatistics();
        return () => {
            isCancelled = true;
            controller.abort();
        };
    }, [canViewStatistics, selectedCountyOrgUnitId, statisticsCacheKey]);

    const selectedCountyName = statisticsScopes.find(
        scope => scope.countyOrgUnitId === selectedCountyOrgUnitId
    )?.countyName;
    const dashboardSubtitle = selectedCountyName
        ? `${selectedCountyName}工单数据概览`
        : !canViewStatistics || scopesLoading || scopesError
            ? '工单数据概览与趋势分析'
            : statisticsScopes.length > 0
                ? '全市工单数据概览与趋势分析'
                : '本区县工单数据概览与趋势分析';

    return (
        <Container maxWidth={false} disableGutters sx={{ width: '100%', mt: 0, mb: 2 }}>
            <Paper elevation={0} sx={{ p: 0, borderRadius: 1, backgroundColor: 'transparent', width: '100%', minWidth: 0 }}>
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: { xs: 'flex-start', sm: 'center' },
                        mb: 1.5,
                        gap: 2,
                        flexDirection: { xs: 'column', sm: 'row' },
                    }}
                >
                    <PageHeading title="管理仪表盘" description={dashboardSubtitle} />
                    {statisticsScopes.length > 0 && (
                        <FormControl size="small" sx={{ ml: { sm: 'auto' }, minWidth: { xs: '100%', sm: 220 } }} disabled={scopesLoading}>
                            <InputLabel id="dashboard-county-scope-label">统计范围</InputLabel>
                            <Select
                                labelId="dashboard-county-scope-label"
                                label="统计范围"
                                value={selectedCountyOrgUnitId}
                                onChange={(event) => setSelectedCountyOrgUnitId(event.target.value)}
                            >
                                <MenuItem value="">全市汇总</MenuItem>
                                {statisticsScopes.map(scope => (
                                    <MenuItem key={scope.countyOrgUnitId} value={scope.countyOrgUnitId}>{scope.countyName}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    )}
                </Box>

                {scopesError && <Alert severity="warning" sx={{ mb: 1.5 }}>{scopesError}</Alert>}

                <StatisticsSection
                    statistics={statistics}
                    loading={statisticsLoading}
                    error={statisticsError}
                    onCountySelect={setSelectedCountyOrgUnitId}
                />
            </Paper>
        </Container>
    );
}

export default AdminMainDashboardPage;

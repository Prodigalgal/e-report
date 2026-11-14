import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Container,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    Stack,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tab,
    Tabs,
    Tooltip,
    Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import ReportProblemIcon from '@mui/icons-material/ReportProblem';

import AmapMapCanvas from '../../components/map/AmapMapCanvas';
import { fetchSessionSituationMap } from '../../services/sessionLocationApiService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDateTime } from '../../utils/formatDate.js';
import { formatContractCoordinates } from '../../utils/coordinateStandards';

/** 位置超过该时长未更新时标注为可能过期，与后端 LOCATION_STALE_AFTER 保持一致。 */
const LOCATION_STALE_MINUTES = 30;
const ONLINE_USER_COLOR = '#1976d2';
const FAULT_POINT_COLOR = '#d32f2f';

const FAULT_POINT_TABS = [
    { value: 'all', label: '全部' },
    { value: 'withCoordinate', label: '有坐标' },
    { value: 'withoutCoordinate', label: '缺坐标' },
];

export default function AdminSessionMapPage() {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState('');
    const [showOnlineUsers, setShowOnlineUsers] = useState(true);
    const [showFaultPoints, setShowFaultPoints] = useState(true);
    const [listTab, setListTab] = useState('online');
    const [faultPointTab, setFaultPointTab] = useState('all');
    const [detailTarget, setDetailTarget] = useState(null);
    const [lastLoadedAt, setLastLoadedAt] = useState(null);
    const [autoRefresh, setAutoRefresh] = useState(false);
    const autoRefreshRef = useRef(null);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const response = await fetchSessionSituationMap();
            setData(response.data);
            setLastLoadedAt(new Date());
            setErrorMessage('');
        } catch (error) {
            setErrorMessage(formatApiErrorMessage(error, '加载登录态态势地图失败。'));
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void load();
    }, [load]);

    // 自动刷新让地图接近实时；关闭时清掉定时器，避免后台持续请求。
    useEffect(() => {
        if (!autoRefresh) {
            if (autoRefreshRef.current) {
                clearInterval(autoRefreshRef.current);
                autoRefreshRef.current = null;
            }
            return undefined;
        }
        autoRefreshRef.current = setInterval(() => { void load(); }, 60000);
        return () => {
            if (autoRefreshRef.current) {
                clearInterval(autoRefreshRef.current);
                autoRefreshRef.current = null;
            }
        };
    }, [autoRefresh, load]);

    // 每次取数后重算过期阈值，让「可能已过期」标注跟随刷新时间推进。
    const staleThreshold = useMemo(() => (
        new Date((lastLoadedAt || new Date()).getTime() - LOCATION_STALE_MINUTES * 60 * 1000)
    ), [lastLoadedAt]);

    // 稳定引用，避免每次渲染都重建标记数组导致地图重复重绘。
    const onlineUsers = useMemo(() => data?.onlineUsers || [], [data]);
    const faultPoints = useMemo(() => data?.faultPoints || [], [data]);

    const isStale = useCallback((locationReportedAt) => {
        if (!locationReportedAt) return true;
        const reportedAt = new Date(`${locationReportedAt}Z`);
        return !Number.isNaN(reportedAt.getTime()) && reportedAt < staleThreshold;
    }, [staleThreshold]);

    const markers = useMemo(() => {
        const result = [];
        if (showOnlineUsers) {
            onlineUsers.forEach(user => {
                if (user.longitude == null || user.latitude == null) return;
                result.push({
                    id: `user-${user.sessionId}`,
                    kind: 'onlineUser',
                    longitude: user.longitude,
                    latitude: user.latitude,
                    color: ONLINE_USER_COLOR,
                    zIndex: 200,
                    stale: isStale(user.locationReportedAt),
                    title: `${user.displayName || user.username || '未知用户'}（${user.clientType === 'ANDROID' ? '安卓端' : '网页端'}）`,
                    payload: user,
                });
            });
        }
        if (showFaultPoints) {
            faultPoints.forEach(point => {
                if (point.longitude == null || point.latitude == null) return;
                result.push({
                    id: `fault-${point.workOrderId}`,
                    kind: 'faultPoint',
                    longitude: point.longitude,
                    latitude: point.latitude,
                    color: FAULT_POINT_COLOR,
                    zIndex: 150,
                    title: `${point.workOrderNo} ${point.location || ''}`,
                    payload: point,
                });
            });
        }
        return result;
    }, [onlineUsers, faultPoints, showOnlineUsers, showFaultPoints, isStale]);

    const visibleFaultPoints = useMemo(() => {
        if (faultPointTab === 'withCoordinate') {
            return faultPoints.filter(point => point.longitude != null && point.latitude != null);
        }
        if (faultPointTab === 'withoutCoordinate') {
            return faultPoints.filter(point => point.longitude == null || point.latitude == null);
        }
        return faultPoints;
    }, [faultPoints, faultPointTab]);

    const summary = data?.summary;

    return (
        <Container maxWidth="xl" sx={{ py: 3 }}>
            <Stack spacing={2}>
                <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} spacing={1}>
                    <Stack spacing={0.5}>
                        <Typography variant="h5" component="h1">登录态态势地图</Typography>
                        <Typography variant="body2" color="text.secondary">
                            展示已登录人员的位置与授权范围内的工单故障点，坐标统一按国测局标准渲染。
                        </Typography>
                    </Stack>
                    <Stack direction="row" spacing={1} alignItems="center">
                        <FormControlLabel
                            control={<Switch checked={autoRefresh} onChange={(event) => setAutoRefresh(event.target.checked)} size="small" />}
                            label="每分钟自动刷新"
                        />
                        <Button onClick={() => void load()} startIcon={<RefreshIcon />} disabled={loading} variant="outlined">
                            刷新
                        </Button>
                    </Stack>
                </Stack>

                {errorMessage && <Alert severity="error" onClose={() => setErrorMessage('')}>{errorMessage}</Alert>}

                {summary && (
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip icon={<MyLocationIcon />} label={`在线 ${summary.onlineUserCount} 人`} color="primary" variant="outlined" />
                        <Chip label={`已定位 ${summary.locatedUserCount} 人`} variant="outlined" />
                        <Chip
                            label={`未定位 ${summary.unlocatedUserCount} 人`}
                            color={summary.unlocatedUserCount > 0 ? 'warning' : 'default'}
                            variant="outlined"
                        />
                        <Chip icon={<ReportProblemIcon />} label={`故障点 ${summary.faultPointCount} 个`} color="error" variant="outlined" />
                        {summary.faultPointWithoutCoordinateCount > 0 && (
                            <Chip
                                label={`缺坐标故障点 ${summary.faultPointWithoutCoordinateCount} 个`}
                                color="warning"
                                variant="outlined"
                            />
                        )}
                        <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>
                            更新于 {lastLoadedAt ? lastLoadedAt.toLocaleTimeString('zh-CN', { hour12: false }) : '-'}
                        </Typography>
                    </Stack>
                )}

                <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
                    <FormControlLabel
                        control={<Switch checked={showOnlineUsers} onChange={(event) => setShowOnlineUsers(event.target.checked)} size="small" />}
                        label="显示在线人员"
                    />
                    <FormControlLabel
                        control={<Switch checked={showFaultPoints} onChange={(event) => setShowFaultPoints(event.target.checked)} size="small" />}
                        label="显示故障点"
                    />
                    <Stack direction="row" spacing={0.5} alignItems="center">
                        <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: ONLINE_USER_COLOR }} />
                        <Typography variant="caption" color="text.secondary">在线人员</Typography>
                    </Stack>
                    <Stack direction="row" spacing={0.5} alignItems="center">
                        <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: FAULT_POINT_COLOR }} />
                        <Typography variant="caption" color="text.secondary">工单故障点</Typography>
                    </Stack>
                </Stack>

                {loading && !data ? (
                    <Stack alignItems="center" justifyContent="center" sx={{ height: 520 }}>
                        <CircularProgress />
                    </Stack>
                ) : (
                    <AmapMapCanvas
                        mapConfig={data?.mapConfig}
                        markers={markers}
                        fitToMarkers
                        height={520}
                        onMarkerClick={marker => setDetailTarget(marker)}
                    />
                )}

                <Tabs value={listTab} onChange={(event, value) => setListTab(value)} variant="scrollable">
                    <Tab value="online" label={`在线人员（${onlineUsers.length}）`} />
                    <Tab value="faultPoint" label={`故障点（${faultPoints.length}）`} />
                </Tabs>

                {listTab === 'online' && (
                    <OnlineUserTable
                        users={onlineUsers}
                        isStale={isStale}
                        onLocate={(user) => setDetailTarget({
                            kind: 'onlineUser',
                            payload: user,
                            longitude: user.longitude,
                            latitude: user.latitude,
                        })}
                    />
                )}

                {listTab === 'faultPoint' && (
                    <Stack spacing={1}>
                        <Tabs value={faultPointTab} onChange={(event, value) => setFaultPointTab(value)}>
                            {FAULT_POINT_TABS.map(tab => (
                                <Tab key={tab.value} value={tab.value} label={tab.label} />
                            ))}
                        </Tabs>
                        <FaultPointTable
                            points={visibleFaultPoints}
                            onLocate={(point) => setDetailTarget({
                                kind: 'faultPoint',
                                payload: point,
                                longitude: point.longitude,
                                latitude: point.latitude,
                            })}
                        />
                    </Stack>
                )}
            </Stack>

            <DetailDialog target={detailTarget} onClose={() => setDetailTarget(null)} />
        </Container>
    );
}

function OnlineUserTable({ users, isStale, onLocate }) {
    if (users.length === 0) {
        return <Alert severity="info">当前没有已登录并上报位置的人员。</Alert>;
    }
    return (
        <TableContainer sx={{ maxHeight: 460 }}>
            <Table size="small" stickyHeader>
                <TableHead>
                    <TableRow>
                        <TableCell>姓名 / 账号</TableCell>
                        <TableCell>组织 / 部门</TableCell>
                        <TableCell>端</TableCell>
                        <TableCell>设备</TableCell>
                        <TableCell>系统 / 版本</TableCell>
                        <TableCell>来源 IP</TableCell>
                        <TableCell>位置</TableCell>
                        <TableCell>定位时间</TableCell>
                        <TableCell>最后活跃</TableCell>
                        <TableCell>登录时间</TableCell>
                        <TableCell align="right">操作</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {users.map(user => {
                        const located = user.longitude != null && user.latitude != null;
                        const stale = located && isStale(user.locationReportedAt);
                        return (
                            <TableRow key={user.sessionId} hover>
                                <TableCell>
                                    <Stack spacing={0}>
                                        <Typography variant="body2">{user.displayName || '未命名'}</Typography>
                                        <Typography variant="caption" color="text.secondary">{user.username || '-'}</Typography>
                                    </Stack>
                                </TableCell>
                                <TableCell>
                                    <Stack spacing={0}>
                                        <Typography variant="body2">{user.organizationName || '-'}</Typography>
                                        <Typography variant="caption" color="text.secondary">{user.departmentName || '-'}</Typography>
                                    </Stack>
                                </TableCell>
                                <TableCell>
                                    <Chip size="small" label={user.clientType === 'ANDROID' ? '安卓端' : '网页端'} variant="outlined" />
                                </TableCell>
                                <TableCell>{user.deviceName || user.deviceModel || '-'}</TableCell>
                                <TableCell>
                                    <Stack spacing={0}>
                                        <Typography variant="caption">{user.osVersion || '-'}</Typography>
                                        <Typography variant="caption" color="text.secondary">{user.appVersion || '-'}</Typography>
                                    </Stack>
                                </TableCell>
                                <TableCell>{user.clientIp || '-'}</TableCell>
                                <TableCell>
                                    {located ? (
                                        <Stack spacing={0}>
                                            <Typography variant="caption">
                                                {formatMarkerCoordinates(user.longitude, user.latitude) || '-'}
                                            </Typography>
                                            {stale && <Chip size="small" color="warning" label="可能已过期" variant="outlined" />}
                                            {user.accuracyMeters != null && (
                                                <Typography variant="caption" color="text.secondary">
                                                    精度约 {Math.round(user.accuracyMeters)} 米
                                                </Typography>
                                            )}
                                        </Stack>
                                    ) : (
                                        <Typography variant="caption" color="text.secondary">未上报</Typography>
                                    )}
                                </TableCell>
                                <TableCell>{formatDateTime(user.locationFixedAt)}</TableCell>
                                <TableCell>{formatDateTime(user.lastSeenAt)}</TableCell>
                                <TableCell>{formatDateTime(user.loginAt)}</TableCell>
                                <TableCell align="right">
                                    <Button size="small" disabled={!located} onClick={() => onLocate(user)}>查看</Button>
                                </TableCell>
                            </TableRow>
                        );
                    })}
                </TableBody>
            </Table>
        </TableContainer>
    );
}

function FaultPointTable({ points, onLocate }) {
    if (points.length === 0) {
        return <Alert severity="info">当前筛选条件下没有工单故障点。</Alert>;
    }
    return (
        <TableContainer sx={{ maxHeight: 460 }}>
            <Table size="small" stickyHeader>
                <TableHead>
                    <TableRow>
                        <TableCell>工单编号</TableCell>
                        <TableCell>类型</TableCell>
                        <TableCell>状态</TableCell>
                        <TableCell>优先级</TableCell>
                        <TableCell>时限</TableCell>
                        <TableCell>地点</TableCell>
                        <TableCell>坐标</TableCell>
                        <TableCell>报单时间</TableCell>
                        <TableCell align="right">操作</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {points.map(point => {
                        const located = point.longitude != null && point.latitude != null;
                        return (
                            <TableRow key={point.workOrderId} hover>
                                <TableCell>{point.workOrderNo}</TableCell>
                                <TableCell>{point.reportTypeName || '-'}</TableCell>
                                <TableCell>{point.statusLabel || '-'}</TableCell>
                                <TableCell>{point.priorityName || '-'}</TableCell>
                                <TableCell>{point.slaRiskName || '-'}</TableCell>
                                <TableCell>
                                    <Tooltip title={point.location || ''}>
                                        <Typography variant="caption" noWrap sx={{ display: 'block', maxWidth: 220 }}>
                                            {point.location || '-'}
                                        </Typography>
                                    </Tooltip>
                                </TableCell>
                                <TableCell>
                                    {located
                                        ? formatMarkerCoordinates(point.longitude, point.latitude)
                                        : <Typography variant="caption" color="text.secondary">缺少坐标</Typography>}
                                </TableCell>
                                <TableCell>{formatDateTime(point.reportTime)}</TableCell>
                                <TableCell align="right">
                                    <Button size="small" disabled={!located} onClick={() => onLocate(point)}>查看</Button>
                                </TableCell>
                            </TableRow>
                        );
                    })}
                </TableBody>
            </Table>
        </TableContainer>
    );
}

function DetailDialog({ target, onClose }) {
    if (!target) return null;
    const { kind, payload } = target;
    const isUser = kind === 'onlineUser';
    return (
        <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
            <DialogTitle>{isUser ? '在线人员详情' : '工单故障点详情'}</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={1}>
                    {isUser ? (
                        <>
                            <DetailRow label="姓名" value={payload.displayName} />
                            <DetailRow label="账号" value={payload.username} />
                            <DetailRow label="手机号" value={payload.contactPhone} />
                            <DetailRow label="组织" value={payload.organizationName} />
                            <DetailRow label="部门" value={payload.departmentName} />
                            <DetailRow label="端类型" value={payload.clientType === 'ANDROID' ? '安卓端' : '网页端'} />
                            <DetailRow label="设备" value={payload.deviceName || payload.deviceModel} />
                            <DetailRow label="系统版本" value={payload.osVersion} />
                            <DetailRow label="应用版本" value={payload.appVersion} />
                            <DetailRow label="来源 IP" value={payload.clientIp} />
                            <DetailRow
                                label="坐标（渲染）"
                                value={formatMarkerCoordinates(payload.longitude, payload.latitude)}
                            />
                            <DetailRow label="原始坐标系" value={payload.sourceCoordinateSystem} />
                            <DetailRow label="定位来源" value={payload.locationProvider} />
                            <DetailRow
                                label="定位精度"
                                value={payload.accuracyMeters != null ? `约 ${Math.round(payload.accuracyMeters)} 米` : null}
                            />
                            <DetailRow label="定位时间" value={formatDateTime(payload.locationFixedAt)} />
                            <DetailRow label="上报时间" value={formatDateTime(payload.locationReportedAt)} />
                            <DetailRow label="登录时间" value={formatDateTime(payload.loginAt)} />
                            <DetailRow label="最后活跃" value={formatDateTime(payload.lastSeenAt)} />
                        </>
                    ) : (
                        <>
                            <DetailRow label="工单编号" value={payload.workOrderNo} />
                            <DetailRow label="类型" value={payload.reportTypeName} />
                            <DetailRow label="状态" value={payload.statusLabel} />
                            <DetailRow label="优先级" value={payload.priorityName} />
                            <DetailRow label="时限" value={payload.slaRiskName} />
                            <DetailRow label="地点" value={payload.location} />
                            <DetailRow
                                label="坐标（渲染）"
                                value={formatMarkerCoordinates(payload.longitude, payload.latitude)}
                            />
                            <DetailRow label="报单时间" value={formatDateTime(payload.reportTime)} />
                            <DetailRow label="最后更新" value={formatDateTime(payload.lastUpdatedTime)} />
                        </>
                    )}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>关闭</Button>
            </DialogActions>
        </Dialog>
    );
}

function DetailRow({ label, value }) {
    return (
        <Stack direction="row" spacing={2}>
            <Typography variant="body2" color="text.secondary" sx={{ minWidth: 96 }}>{label}</Typography>
            <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>{value || '-'}</Typography>
        </Stack>
    );
}

/**
 * 接口返回的坐标已是 GCJ-02（地图渲染标准），展示时按经度,纬度 顺序输出。
 */
const formatMarkerCoordinates = (longitude, latitude) => (
    Number.isFinite(longitude) && Number.isFinite(latitude)
        ? formatContractCoordinates(latitude, longitude)
        : null
);

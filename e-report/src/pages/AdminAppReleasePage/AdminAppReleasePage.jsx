import PageHeading from '../../components/layout/PageHeading';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    Alert,
    Accordion,
    AccordionDetails,
    AccordionSummary,
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
    IconButton,
    LinearProgress,
    MenuItem,
    Paper,
    Pagination,
    Snackbar,
    Stack,
    Tabs,
    Tab,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Switch,
    TextField,
    Tooltip,
    Typography,
    useMediaQuery,
} from '@mui/material';
import CloudDoneIcon from '@mui/icons-material/CloudDone';
import CloudOffIcon from '@mui/icons-material/CloudOff';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteIcon from '@mui/icons-material/Delete';
import GetAppIcon from '@mui/icons-material/GetApp';
import RefreshIcon from '@mui/icons-material/Refresh';
import SyncIcon from '@mui/icons-material/Sync';
import TuneIcon from '@mui/icons-material/Tune';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

import SearchInput from '../../components/SearchInput/SearchInput';
import { PERMISSIONS } from '../../constants/permissions';
import { useAuth } from '../../hooks/useAuth';
import apiClient from '../../services/apiClient';
import { uploadSingleFileInChunks } from '../../services/fileTransferService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDateTime } from '../../utils/formatDate.js';
import { deriveAndroidVersionMetadata } from '../../utils/appReleaseVersion';
import { normalizeAppReleasePolicy } from '../../utils/appReleasePolicy';
import { getPaginationItemAriaLabel } from '../../utils/paginationAriaLabel';
import ExpoUpdatePanel from './ExpoUpdatePanel';

const STATUS_PRESENTATION = {
    SYNCED: { label: '已同步', color: 'success' },
    MISSING: { label: '对象存储缺失', color: 'warning' },
    SIZE_MISMATCH: { label: '大小不一致', color: 'error' },
    SYNCING: { label: '同步中', color: 'info' },
    SYNC_FAILED: { label: '同步失败', color: 'error' },
    ORPHAN: { label: '未登记对象', color: 'default' },
    PENDING: { label: '待发布', color: 'info' },
    RECORD_ONLY: { label: '已登记', color: 'default' },
    DISABLED: { label: '对象存储未启用', color: 'default' },
};

const EMPTY_UPLOAD_DRAFT = Object.freeze({
    versionName: '',
    runtimeVersion: '',
    channel: 'stable',
    minSupportedVersionCode: '0',
    forceUpdate: false,
    releaseNotes: '',
});

function uploadStageLabel(progress) {
    if (progress.stage === 'hashing') return '正在计算安装包校验值';
    if (progress.stage === 'verifying') return '正在校验安装包完整性';
    if (progress.stage === 'registering') return '正在登记版本并提交对象存储同步';
    if (progress.stage === 'completed') return '上传完成';
    return '正在分片上传安装包';
}

function uploadResumeKey(file) {
    return `e-report:app-release-upload:${file.name}:${file.size}:${file.lastModified || 0}`;
}

function formatBytes(value) {
    if (value == null || Number.isNaN(Number(value))) return '-';
    const bytes = Number(value);
    if (bytes < 1024) return `${bytes} 字节`;
    if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} 千字节`;
    return `${(bytes / 1024 ** 2).toFixed(1)} 兆字节`;
}

function formatTimestamp(value) {
    return formatDateTime(value);
}

function formatReleaseChannel(channel) {
    if (!channel || channel === 'stable') return '稳定版';
    if (channel === 'beta') return '测试版';
    return channel;
}

function SummaryMetric({ label, value, tone = 'text.primary' }) {
    return (
        <Box sx={{ px: 1.25, py: 1, border: '1px solid', borderColor: 'divider', borderRadius: 1, bgcolor: 'background.paper' }}>
            <Typography variant="caption" color="text.secondary">{label}</Typography>
            <Typography variant="h6" sx={{ mt: 0.25, fontWeight: 800, color: tone }}>{value}</Typography>
        </Box>
    );
}

function AdminAppReleasePage() {
    const mobile = useMediaQuery(theme => theme.breakpoints.down('md'));
    const { hasPermission } = useAuth();
    const isSuperAdministrator = hasPermission(PERMISSIONS.SYSTEM_SUPER_ADMINISTRATOR);
    const canPublish = isSuperAdministrator || hasPermission(PERMISSIONS.APP_UPDATE_PUBLISH);
    const canUpdatePolicy = isSuperAdministrator || hasPermission(PERMISSIONS.APP_UPDATE_POLICY_UPDATE);
    const canViewStorage = isSuperAdministrator || hasPermission(PERMISSIONS.APP_UPDATE_STORAGE_VIEW);
    const canSynchronizeStorage = isSuperAdministrator || hasPermission(PERMISSIONS.APP_UPDATE_STORAGE_SYNC);
    const canDeleteStorage = isSuperAdministrator || hasPermission(PERMISSIONS.APP_UPDATE_STORAGE_DELETE);
    const [overview, setOverview] = useState(null);
    const [edition, setEdition] = useState('NATIVE');
    const [loading, setLoading] = useState(true);
    const [operatingKey, setOperatingKey] = useState('');
    const [error, setError] = useState('');
    const [searchTerm, setSearchTerm] = useState('');
    const [page, setPage] = useState(0);
    const [policyItem, setPolicyItem] = useState(null);
    const [policyDraft, setPolicyDraft] = useState({ forceUpdate: false, minSupportedVersionCode: '0', releaseNotes: '' });
    const [policySaving, setPolicySaving] = useState(false);
    const [policyError, setPolicyError] = useState('');
    const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' });
    const [uploadOpen, setUploadOpen] = useState(false);
    const [uploadFile, setUploadFile] = useState(null);
    const [uploadDraft, setUploadDraft] = useState({ ...EMPTY_UPLOAD_DRAFT });
    const [uploadProgress, setUploadProgress] = useState({ stage: '', percent: 0 });
    const [uploadError, setUploadError] = useState('');
    const [uploading, setUploading] = useState(false);
    const [uploadAbortController, setUploadAbortController] = useState(null);

    const overviewRequest = useRef(null);
    const loadOverview = useCallback(async () => {
        overviewRequest.current?.abort();
        const controller = new AbortController();
        overviewRequest.current = controller;
        setLoading(true);
        setError('');
        try {
            if (canViewStorage) {
                const response = await apiClient.get('/admin/app-updates/r2', { params: { edition }, timeout: 90000, signal: controller.signal });
                if (controller.signal.aborted) return;
                setOverview({ ...response.data, storageVisible: true });
            } else {
                const response = await apiClient.get('/admin/app-updates', {
                    params: { edition, page: 0, size: 100, sort: 'publishedAt,desc' },
                    timeout: 90000,
                    signal: controller.signal,
                });
                if (controller.signal.aborted) return;
                const releases = Array.isArray(response.data?.content) ? response.data.content : [];
                setOverview({
                    enabled: null,
                    storageVisible: false,
                    releaseCount: response.data?.totalElements ?? releases.length,
                    objectCount: null,
                    synchronizedCount: null,
                    missingCount: null,
                    mismatchCount: null,
                    orphanCount: null,
                    items: releases.map(release => ({
                        releaseId: release.id,
                        edition: release.edition,
                        versionName: release.versionName,
                        versionCode: release.versionCode,
                        runtimeVersion: release.runtimeVersion,
                        channel: release.channel,
                        forceUpdate: release.forceUpdate,
                        minSupportedVersionCode: release.minSupportedVersionCode,
                        releaseNotes: release.releaseNotes,
                        published: release.published,
                        current: false,
                        status: release.published ? 'RECORD_ONLY' : 'PENDING',
                        expectedSize: release.fileSize,
                        objectSize: release.fileSize,
                        lastModified: release.updatedAt || release.createdAt,
                        objectKey: '',
                    })),
                });
            }
        } catch (requestError) {
            if (controller.signal.aborted) return;
            setError(formatApiErrorMessage(requestError, '加载对象存储安装包状态失败，请稍后重试。'));
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, [canViewStorage, edition]);

    useEffect(() => {
        void loadOverview();
        return () => overviewRequest.current?.abort();
    }, [loadOverview]);

    useEffect(() => {
        const hasRunningTask = overview?.items?.some(item => item.status === 'SYNCING');
        if (!hasRunningTask) return undefined;
        const timer = window.setTimeout(loadOverview, 3000);
        return () => window.clearTimeout(timer);
    }, [loadOverview, overview]);

    const filteredItems = useMemo(() => {
        const normalizedSearch = searchTerm.trim().toLowerCase();
        const items = Array.isArray(overview?.items) ? overview.items : [];
        if (!normalizedSearch) return items;
        return items.filter(item => [
            item.versionName,
            item.versionCode,
            item.channel,
            formatReleaseChannel(item.channel),
            item.objectKey,
            item.sha256,
            STATUS_PRESENTATION[item.status]?.label,
        ].some(value => String(value ?? '').toLowerCase().includes(normalizedSearch)));
    }, [overview, searchTerm]);
    const pageCount = Math.max(1, Math.ceil(filteredItems.length / 10));
    const selectedPage = Math.min(page, pageCount - 1);
    const pageItems = filteredItems.slice(selectedPage * 10, (selectedPage + 1) * 10);

    const synchronizeRelease = async (item) => {
        if (!item.releaseId) return;
        const operationKey = `sync:${item.releaseId}`;
        setOperatingKey(operationKey);
        try {
            await apiClient.post(`/admin/app-updates/r2/releases/${item.releaseId}`, null, { timeout: 20 * 60 * 1000 });
            setSnackbar({ open: true, message: `构建号 ${item.versionCode} 的同步任务已提交。`, severity: 'info' });
            await loadOverview();
        } catch (requestError) {
            setSnackbar({
                open: true,
                message: formatApiErrorMessage(requestError, '同步安装包到对象存储失败，请稍后重试。'),
                severity: 'error',
            });
        } finally {
            setOperatingKey('');
        }
    };

    const deleteObject = async (item) => {
        if (!item.objectKey || item.current) return;
        if (!window.confirm(`确定删除对象存储文件“${item.objectKey}”吗？本操作不会删除服务器本地安装包和发布记录。`)) return;
        const operationKey = `delete:${item.objectKey}`;
        setOperatingKey(operationKey);
        try {
            await apiClient.delete('/admin/app-updates/r2/objects', { params: { key: item.objectKey }, timeout: 90000 });
            setSnackbar({ open: true, message: '对象存储文件已删除。', severity: 'success' });
            await loadOverview();
        } catch (requestError) {
            setSnackbar({
                open: true,
                message: formatApiErrorMessage(requestError, '删除对象存储文件失败，请稍后重试。'),
                severity: 'error',
            });
        } finally {
            setOperatingKey('');
        }
    };

    const openUploadDialog = () => {
        setUploadFile(null);
        setUploadDraft({ ...EMPTY_UPLOAD_DRAFT });
        setUploadProgress({ stage: '', percent: 0 });
        setUploadError('');
        setUploadOpen(true);
    };

    const closeUploadDialog = () => {
        if (uploading) return;
        setUploadOpen(false);
    };

    const selectUploadFile = event => {
        const file = event.target.files?.[0] || null;
        setUploadFile(file);
        setUploadError('');
        if (!file) return;
        const versionMatch = file.name.match(/(?:^|[-_])v?(\d+\.\d+\.\d+)(?:[-_.]|$)/i);
        if (versionMatch) {
            setUploadDraft(current => ({ ...current, versionName: versionMatch[1] }));
        }
    };

    const publishUpload = async () => {
        if (!uploadFile) {
            setUploadError('请先选择 APK 安装包。');
            return;
        }
        if (!uploadFile.name.toLowerCase().endsWith('.apk')) {
            setUploadError('只允许上传 .apk 安装包。');
            return;
        }
        let metadata;
        try {
            if (edition === 'NATIVE' && !uploadDraft.runtimeVersion.trim()) throw new Error('请填写 APK 构建清单中的原生兼容运行时。');
            metadata = deriveAndroidVersionMetadata(uploadDraft.versionName, edition, uploadDraft.runtimeVersion);
        } catch (validationError) {
            setUploadError(validationError.message);
            return;
        }
        const minimumVersionCode = Number(uploadDraft.minSupportedVersionCode);
        if (!Number.isInteger(minimumVersionCode) || minimumVersionCode < 0 || minimumVersionCode > metadata.versionCode) {
            setUploadError(`最低支持构建号必须是 0 到 ${metadata.versionCode} 之间的整数。`);
            return;
        }

        const abortController = new AbortController();
        const resumeKey = uploadResumeKey(uploadFile);
        const resumableUploadId = window.sessionStorage.getItem(resumeKey) || undefined;
        setUploadAbortController(abortController);
        setUploading(true);
        setUploadError('');
        try {
            const completedUploadId = await uploadSingleFileInChunks(uploadFile, 'APP_RELEASE', {
                uploadId: resumableUploadId,
                verifySha256: false,
                signal: abortController.signal,
                onSessionCreated: uploadId => window.sessionStorage.setItem(resumeKey, uploadId),
                onProgress: setUploadProgress,
            });
            setUploadProgress({ stage: 'registering', percent: 100 });
            await apiClient.post('/admin/app-updates', {
                edition,
                platform: 'ANDROID',
                channel: uploadDraft.channel,
                versionCode: metadata.versionCode,
                versionName: metadata.versionName,
                runtimeVersion: metadata.runtimeVersion,
                minSupportedVersionCode: minimumVersionCode,
                forceUpdate: uploadDraft.forceUpdate,
                releaseNotes: uploadDraft.releaseNotes,
                completedUploadId,
            }, { timeout: 45000 });
            window.sessionStorage.removeItem(resumeKey);
            setUploadOpen(false);
            setSnackbar({ open: true, message: `${metadata.versionName} 已上传，正在同步对象存储；同步完成后自动成为最新版本。`, severity: 'success' });
            await loadOverview();
        } catch (requestError) {
            if (requestError?.name === 'AbortError') {
                setUploadError('上传已暂停，重新选择同一文件后可以从断点继续。');
            } else {
                setUploadError(formatApiErrorMessage(requestError, '安装包上传或发布失败，已保留上传断点。'));
            }
        } finally {
            setUploading(false);
            setUploadAbortController(null);
        }
    };

    const openPolicyDialog = (item) => {
        setPolicyItem(item);
        setPolicyDraft({
            forceUpdate: Boolean(item.forceUpdate),
            minSupportedVersionCode: String(item.minSupportedVersionCode ?? 0),
            releaseNotes: item.releaseNotes || '',
        });
        setPolicyError('');
    };

    const closePolicyDialog = () => {
        if (policySaving) return;
        setPolicyItem(null);
        setPolicyError('');
    };

    const savePolicy = async () => {
        if (!policyItem?.releaseId) return;
        let payload;
        try {
            payload = normalizeAppReleasePolicy(policyDraft, policyItem.versionCode);
        } catch (validationError) {
            setPolicyError(validationError.message);
            return;
        }
        setPolicySaving(true);
        setPolicyError('');
        try {
            await apiClient.patch(`/admin/app-updates/${policyItem.releaseId}/policy`, payload);
            setPolicyItem(null);
            setSnackbar({ open: true, message: `构建号 ${policyItem.versionCode} 的升级策略已保存。`, severity: 'success' });
            await loadOverview();
        } catch (requestError) {
            setPolicyError(formatApiErrorMessage(requestError, '保存安装包升级策略失败，请稍后重试。'));
        } finally {
            setPolicySaving(false);
        }
    };

    const renderReleaseActions = (item) => {
        const syncKey = `sync:${item.releaseId}`;
        const deleteKey = `delete:${item.objectKey}`;
        const isOperating = operatingKey === syncKey || operatingKey === deleteKey;
        return <Stack direction="row" spacing={0.75} useFlexGap sx={{ justifyContent: 'flex-end', flexWrap: 'nowrap' }}>
            {item.releaseId && canUpdatePolicy && (
                <Tooltip title="编辑升级策略">
                    <span>
                        <IconButton size="small" onClick={() => openPolicyDialog(item)} disabled={Boolean(operatingKey)} aria-label="编辑升级策略">
                            <TuneIcon fontSize="small" />
                        </IconButton>
                    </span>
                </Tooltip>
            )}
            {item.releaseId && canSynchronizeStorage && (
                <Tooltip title={item.status === 'SYNCED' ? '重新上传并校验' : '同步到对象存储'}>
                    <span>
                        <IconButton size="small" onClick={() => synchronizeRelease(item)} disabled={!overview.enabled || Boolean(operatingKey)} aria-label="同步到对象存储">
                            {isOperating && operatingKey === syncKey ? <CircularProgress size={18} /> : <SyncIcon fontSize="small" />}
                        </IconButton>
                    </span>
                </Tooltip>
            )}
            {item.releaseId && item.published && item.objectSize != null && (
                <Tooltip title="下载此版本">
                    <IconButton size="small" component="a" href={`/api/app-updates/releases/${item.releaseId}/apk`} aria-label={`下载 ${item.versionName || item.versionCode}`}>
                        <GetAppIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
            )}
            {item.objectSize != null && canDeleteStorage && overview.storageVisible && (
                <Tooltip title={item.current ? '当前发布版不可删除' : '仅删除对象存储文件'}>
                    <span>
                        <IconButton color="error" size="small" onClick={() => deleteObject(item)} disabled={item.current || Boolean(operatingKey)} aria-label="删除对象存储文件">
                            {isOperating && operatingKey === deleteKey ? <CircularProgress size={18} /> : <DeleteIcon fontSize="small" />}
                        </IconButton>
                    </span>
                </Tooltip>
            )}
        </Stack>;
    };

    return (
        <Container maxWidth={false} disableGutters sx={{ mt: 0, mb: 2 }}>
            <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, alignItems: { xs: 'stretch', sm: 'center' }, justifyContent: 'space-between', gap: 1.5, mb: 1.5 }}>
                <Box>
                    <PageHeading title="应用发布中心" description="原生版支持 OTA 功能更新，Lite 复用网页。两款 APK 独立发布和升级。" />
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 0.75 }}>
                    {canPublish && (
                        <Button variant="contained" startIcon={<CloudUploadIcon />} onClick={openUploadDialog} disabled={loading || overview?.enabled === false} sx={{ whiteSpace: 'nowrap', flexGrow: { xs: 1, sm: 0 } }}>
                            上传新版本
                        </Button>
                    )}
                    <Button component="a" href={edition === 'LITE' ? '/download/android-lite' : '/download/android'} startIcon={<GetAppIcon />} sx={{ display: { xs: 'none', sm: 'inline-flex' } }}>
                        下载最新版
                    </Button>
                    <Button startIcon={<RefreshIcon />} onClick={loadOverview} disabled={loading || Boolean(operatingKey)} sx={{ display: { xs: 'none', sm: 'inline-flex' } }}>
                        刷新状态
                    </Button>
                    <Tooltip title="下载最新版">
                        <IconButton component="a" href={edition === 'LITE' ? '/download/android-lite' : '/download/android'} sx={{ display: { xs: 'inline-flex', sm: 'none' } }} aria-label="下载最新版">
                            <GetAppIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="刷新状态">
                        <span>
                            <IconButton onClick={loadOverview} disabled={loading || Boolean(operatingKey)} sx={{ display: { xs: 'inline-flex', sm: 'none' } }} aria-label="刷新状态">
                                <RefreshIcon />
                            </IconButton>
                        </span>
                    </Tooltip>
                </Box>
            </Box>

            <Tabs value={edition} onChange={(_, value) => { setEdition(value); setPage(0); setOverview(null); }} aria-label="客户端类型" sx={{ mb: 2 }}>
                <Tab value="NATIVE" label="原生版 APK" />
                <Tab value="LITE" label="Lite APK" />
            </Tabs>
            {edition === 'NATIVE' && <ExpoUpdatePanel canPublish={canPublish} />}

            {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
            {overview?.storageVisible && !overview.enabled && (
                <Alert severity="warning" sx={{ mb: 2 }}>
                    当前运行环境未接入安装包对象存储，暂时只能查看历史记录，不能上传或发布新版本。请由系统运维人员核对环境配置。
                </Alert>
            )}

            {overview && (
                <>
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(5, minmax(0, 1fr))' }, gap: 1, mb: 1.5 }}>
                        <SummaryMetric label="发布记录" value={overview.releaseCount} />
                        <SummaryMetric label="存储文件" value={overview.storageVisible ? overview.objectCount : '无查看权限'} />
                        <SummaryMetric label="已同步" value={overview.storageVisible ? overview.synchronizedCount : '无查看权限'} tone="success.main" />
                        <SummaryMetric label="缺失 / 不一致" value={overview.storageVisible ? `${overview.missingCount} / ${overview.mismatchCount}` : '无查看权限'} tone={(overview.missingCount || overview.mismatchCount) ? 'warning.main' : 'text.primary'} />
                        <SummaryMetric label="未登记对象" value={overview.storageVisible ? overview.orphanCount : '无查看权限'} tone={overview.orphanCount ? 'warning.main' : 'text.primary'} />
                    </Box>

                    {overview.storageVisible ? (
                        <Alert icon={overview.enabled ? <CloudDoneIcon /> : <CloudOffIcon />} severity={overview.enabled ? 'info' : 'warning'} sx={{ mb: 1.5, py: 0.25 }}>
                            存储桶：{overview.bucketName || '-'}；下载入口：{edition === 'LITE' ? '/download/android-lite' : '/download/android'}
                        </Alert>
                    ) : (
                        <Alert severity="info" sx={{ mb: 1.5, py: 0.25 }}>
                            当前账号没有对象存储查看权限；发布记录和版本下载仍按已授予的权限提供。
                        </Alert>
                    )}
                </>
            )}

            <Paper elevation={1} sx={{ p: { xs: 1.25, md: 1.5 } }}>
                <SearchInput
                    label="搜索安装包"
                    size="small"
                    value={searchTerm}
                    onChange={(event) => { setPage(0); setSearchTerm(event.target.value); }}
                    onClear={() => { setPage(0); setSearchTerm(''); }}
                    resultCount={filteredItems.length}
                    fullWidth
                    placeholder="版本号、通道、对象路径或 SHA-256"
                    disabled={loading}
                />

                {loading && !overview && (
                    <Box sx={{ minHeight: 220, display: 'grid', placeItems: 'center' }}>
                        <Box sx={{ textAlign: 'center' }}><CircularProgress size={32} /><Typography color="text.secondary" sx={{ mt: 1 }}>读取对象存储状态...</Typography></Box>
                    </Box>
                )}

                {!loading && overview && filteredItems.length === 0 && (
                    <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>没有匹配的安装包记录。</Typography>
                )}

                {overview && filteredItems.length > 0 && <Box sx={{ mt: 1.5, display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
                    <Typography variant="body2" color="text.secondary">共 {filteredItems.length} 条记录</Typography>
                    {pageCount > 1 && <Pagination count={pageCount} page={selectedPage + 1} onChange={(_, value) => setPage(value - 1)} size={mobile ? 'small' : 'medium'} color="primary" getItemAriaLabel={getPaginationItemAriaLabel} />}
                </Box>}

                {overview && filteredItems.length > 0 && (mobile ? <Box sx={{ mt: 1.5 }}>
                    {pageItems.map(item => {
                        const status = STATUS_PRESENTATION[item.status] || STATUS_PRESENTATION.DISABLED;
                        return <Accordion key={`${item.releaseId ?? 'orphan'}:${item.objectKey}`} disableGutters>
                            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                <Box sx={{ minWidth: 0 }}>
                                    <Typography sx={{ fontWeight: 800 }}>{item.versionName || '未登记'} · {formatReleaseChannel(item.channel)}</Typography>
                                    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', mt: 0.5 }}>
                                        <Chip size="small" label={status.label} color={status.color} variant="outlined" />
                                        {item.current && <Typography variant="caption" color="primary.main">当前发布版</Typography>}
                                    </Stack>
                                </Box>
                            </AccordionSummary>
                            <AccordionDetails>
                                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1.5, mb: 1.5 }}>
                                    {[
                                        ['构建号', item.versionCode || '-'],
                                        ['升级策略', item.releaseId ? item.forceUpdate ? '强制更新' : '普通更新' : '-'],
                                        ['最低支持构建号', item.releaseId ? item.minSupportedVersionCode ?? 0 : '-'],
                                        ['文件大小', `${formatBytes(item.objectSize)}${item.expectedSize != null && item.objectSize !== item.expectedSize ? `（应为 ${formatBytes(item.expectedSize)}）` : ''}`],
                                        ['存储更新时间', formatTimestamp(item.lastModified)],
                                        ['对象路径', item.objectKey || '-'],
                                    ].map(([label, value]) => <Box key={label} sx={{ minWidth: 0 }}>
                                        <Typography variant="caption" color="text.secondary">{label}</Typography>
                                        <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{value}</Typography>
                                    </Box>)}
                                </Box>
                                {item.operationMessage && <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>{item.operationMessage}</Typography>}
                                {renderReleaseActions(item)}
                            </AccordionDetails>
                        </Accordion>;
                    })}
                </Box> : (
                    <TableContainer sx={{ mt: 1.5, maxHeight: 'max(280px, calc(100dvh - 450px))' }}>
                        <Table size="small" aria-label="对象存储安装包列表" stickyHeader sx={{ tableLayout: 'fixed', minWidth: 960 }}>
                            <colgroup><col style={{ width: 148 }} /><col style={{ width: 192 }} /><col /><col style={{ width: 168 }} /><col style={{ width: 224 }} /></colgroup>
                            <TableHead>
                                <TableRow sx={{ '& th': { fontWeight: 750, whiteSpace: 'nowrap' } }}>
                                    <TableCell>版本与通道</TableCell>
                                    <TableCell>状态与升级策略</TableCell>
                                    <TableCell>文件与位置</TableCell>
                                    <TableCell>存储更新时间</TableCell>
                                    <TableCell align="right">操作</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {pageItems.map(item => {
                                    const status = STATUS_PRESENTATION[item.status] || STATUS_PRESENTATION.DISABLED;
                                    return (
                                        <TableRow key={`${item.releaseId ?? 'orphan'}:${item.objectKey}`} hover>
                                            <TableCell>
                                                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                                    {item.versionName || '未登记'}
                                                </Typography>
                                                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>{formatReleaseChannel(item.channel)}{item.versionCode ? ` · ${item.versionCode}` : ''}</Typography>
                                                {item.current && <Typography variant="caption" color="primary.main">当前发布版</Typography>}
                                            </TableCell>
                                            <TableCell>
                                                <Chip size="small" label={status.label} color={status.color} variant={item.status === 'SYNCED' ? 'filled' : 'outlined'} />
                                                {item.releaseId ? (
                                                    <Box sx={{ mt: 0.75 }}>
                                                        <Typography variant="caption" sx={{ color: item.forceUpdate ? 'error.main' : 'text.secondary' }}>{item.forceUpdate ? '强制更新' : '普通更新'}</Typography>
                                                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                                                            最低支持构建号 {item.minSupportedVersionCode ?? 0}
                                                        </Typography>
                                                    </Box>
                                                ) : '-'}
                                                {item.operationMessage && <Typography variant="caption" title={item.operationMessage} color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', mt: 0.5 }}>{item.operationMessage}</Typography>}
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2">{formatBytes(item.objectSize)}</Typography>
                                                {item.expectedSize != null && item.objectSize !== item.expectedSize && (
                                                    <Typography variant="caption" color="text.secondary">应为 {formatBytes(item.expectedSize)}</Typography>
                                                )}
                                                <Tooltip title={item.objectKey}><Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', mt: 0.75 }}>{item.objectKey}</Typography></Tooltip>
                                            </TableCell>
                                            <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatTimestamp(item.lastModified)}</TableCell>
                                            <TableCell align="right">{renderReleaseActions(item)}</TableCell>
                                        </TableRow>
                                    );
                                })}
                            </TableBody>
                        </Table>
                    </TableContainer>
                ))}
            </Paper>

            <Dialog open={uploadOpen} onClose={closeUploadDialog} fullWidth maxWidth="sm">
                <DialogTitle>上传并发布{edition === 'LITE' ? ' Lite' : '原生'} APK</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={2}>
                        {uploadError && <Alert severity="error">{uploadError}</Alert>}
                        <Alert severity="info">
                            安装包先分片上传，服务端完成 SHA-256 校验后再同步到对象存储；同步完成前不会推送给客户端。
                        </Alert>
                        <Button component="label" variant="outlined" startIcon={<CloudUploadIcon />} disabled={uploading}>
                            {uploadFile ? '重新选择安装包' : '选择 APK 安装包'}
                            <input hidden type="file" accept=".apk,application/vnd.android.package-archive" onChange={selectUploadFile} />
                        </Button>
                        {uploadFile && (
                            <Typography variant="body2" color="text.secondary">
                                {uploadFile.name} · {formatBytes(uploadFile.size)}
                            </Typography>
                        )}
                        <TextField
                            label="版本号"
                            placeholder="例如 2.4.3"
                            value={uploadDraft.versionName}
                            onChange={event => setUploadDraft(current => ({ ...current, versionName: event.target.value }))}
                            helperText="版本号与 APK 内置业务版本一致，Android 构建号自动计算"
                            disabled={uploading}
                            required
                            fullWidth
                        />
                        {edition === 'NATIVE' && <TextField
                            label="原生兼容运行时"
                            value={uploadDraft.runtimeVersion}
                            onChange={event => setUploadDraft(current => ({ ...current, runtimeVersion: event.target.value }))}
                            helperText="填写构建清单中的 runtimeVersion；兼容的业务更新共用同一运行时"
                            disabled={uploading}
                            required
                            fullWidth
                        />}
                        <TextField
                            select
                            label="发布通道"
                            value={uploadDraft.channel}
                            onChange={event => setUploadDraft(current => ({ ...current, channel: event.target.value }))}
                            disabled={uploading}
                            fullWidth
                        >
                            <MenuItem value="stable">稳定版</MenuItem>
                            <MenuItem value="beta">测试版</MenuItem>
                        </TextField>
                        <TextField
                            label="最低支持构建号"
                            type="number"
                            value={uploadDraft.minSupportedVersionCode}
                            onChange={event => setUploadDraft(current => ({ ...current, minSupportedVersionCode: event.target.value }))}
                            slotProps={{ htmlInput: { min: 0, step: 1 } }}
                            disabled={uploading}
                            fullWidth
                        />
                        <FormControlLabel
                            control={<Switch checked={uploadDraft.forceUpdate} onChange={event => setUploadDraft(current => ({ ...current, forceUpdate: event.target.checked }))} />}
                            label="将该版本设为强制更新"
                            disabled={uploading}
                        />
                        <TextField
                            label="更新说明"
                            value={uploadDraft.releaseNotes}
                            onChange={event => setUploadDraft(current => ({ ...current, releaseNotes: event.target.value }))}
                            multiline
                            minRows={4}
                            slotProps={{ htmlInput: { maxLength: 2000 } }}
                            helperText={`${uploadDraft.releaseNotes.length} / 2000`}
                            disabled={uploading}
                            fullWidth
                        />
                        {uploading && (
                            <Box>
                                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
                                    <Typography variant="body2">{uploadStageLabel(uploadProgress)}</Typography>
                                    <Typography variant="body2" color="text.secondary">{uploadProgress.percent || 0}%</Typography>
                                </Box>
                                <LinearProgress variant="determinate" value={uploadProgress.percent || 0} />
                            </Box>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    {uploading ? (
                        <Button color="warning" onClick={() => uploadAbortController?.abort()}>暂停上传</Button>
                    ) : (
                        <Button onClick={closeUploadDialog}>取消</Button>
                    )}
                    <Button variant="contained" onClick={publishUpload} disabled={uploading || !uploadFile || overview?.enabled === false}>
                        {uploading ? '正在发布...' : '上传并发布'}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog open={Boolean(policyItem)} onClose={closePolicyDialog} fullWidth maxWidth="sm">
                <DialogTitle>编辑升级策略</DialogTitle>
                <DialogContent dividers>
                    {policyError && <Alert severity="error" sx={{ mb: 2 }}>{policyError}</Alert>}
                    {policyItem && (
                        <Stack spacing={2}>
                            <Box>
                                <Typography sx={{ fontWeight: 700 }}>{policyItem.versionName}（构建号 {policyItem.versionCode}）</Typography>
                                <Typography variant="body2" color="text.secondary">
                                    {formatReleaseChannel(policyItem.channel)} · {policyItem.runtimeVersion || '未登记运行时版本'}
                                </Typography>
                            </Box>
                            <FormControlLabel
                                control={<Switch checked={policyDraft.forceUpdate} onChange={event => setPolicyDraft(current => ({ ...current, forceUpdate: event.target.checked }))} />}
                                label="将该版本设为强制更新"
                                disabled={policySaving}
                            />
                            <TextField
                                label="最低支持构建号"
                                type="number"
                                value={policyDraft.minSupportedVersionCode}
                                onChange={event => setPolicyDraft(current => ({ ...current, minSupportedVersionCode: event.target.value }))}
                                slotProps={{ htmlInput: { min: 0, max: policyItem.versionCode, step: 1 } }}
                                disabled={policySaving}
                                fullWidth
                            />
                            {!policyDraft.forceUpdate && Number(policyDraft.minSupportedVersionCode) >= policyItem.versionCode && (
                                <Alert severity="info">低于最低支持构建号的客户端仍会被强制升级。</Alert>
                            )}
                            <TextField
                                label="更新说明"
                                value={policyDraft.releaseNotes}
                                onChange={event => setPolicyDraft(current => ({ ...current, releaseNotes: event.target.value }))}
                                disabled={policySaving}
                                multiline
                                minRows={4}
                                slotProps={{ htmlInput: { maxLength: 2000 } }}
                                helperText={`${policyDraft.releaseNotes.length} / 2000`}
                                fullWidth
                            />
                        </Stack>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={closePolicyDialog} disabled={policySaving}>取消</Button>
                    <Button variant="contained" onClick={savePolicy} disabled={policySaving} loading={policySaving}>
                        {policySaving ? '保存中...' : '保存升级策略'}
                    </Button>
                </DialogActions>
            </Dialog>

            <Snackbar
                open={snackbar.open}
                autoHideDuration={5000}
                onClose={() => setSnackbar(current => ({ ...current, open: false }))}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
            >
                <Alert severity={snackbar.severity} variant="filled" onClose={() => setSnackbar(current => ({ ...current, open: false }))}>
                    {snackbar.message}
                </Alert>
            </Snackbar>
        </Container>
    );
}

export default AdminAppReleasePage;

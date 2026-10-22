import PageTitle from '../../components/layout/PageTitle';
import { PageTabs, PageTabPanel } from '../../components/layout/PageTabs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Container,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    IconButton,
    Paper,
    Stack,
    Tooltip,
    Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import BuildIcon from '@mui/icons-material/Build';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CloseIcon from '@mui/icons-material/Close';
import DownloadIcon from '@mui/icons-material/Download';
import GpsFixedIcon from '@mui/icons-material/GpsFixed';
import ImageIcon from '@mui/icons-material/Image';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import NavigationIcon from '@mui/icons-material/Navigation';
import PersonIcon from '@mui/icons-material/Person';
import PhoneIcon from '@mui/icons-material/Phone';
import ReportProblemIcon from '@mui/icons-material/ReportProblem';
import ShareIcon from '@mui/icons-material/Share';
import SpeedIcon from '@mui/icons-material/Speed';

import { WORK_ORDER_TYPE_CODES, findWorkOrderType, getReportTypeLabel } from '../../constants/workOrderCatalog';
import { fetchWorkOrderById } from '../../services/workOrderApiService';
import { downloadFileInChunks } from '../../services/fileDownloadService';
import { formatDate } from '../../utils/formatDate';
import { formatFileSize } from '../../utils/formatFileSize';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { buildAmapNavigationUrl, shareWorkOrderLocation } from '../../utils/mapNavigation';
import WorkOrderLifecycleActions from '../../components/work-orders/WorkOrderLifecycleActions';
import WorkOrderHistory from '../../components/work-orders/WorkOrderHistory';
import WorkOrderOrganizationTransferPanel from '../../components/work-orders/WorkOrderOrganizationTransferPanel.jsx';
import { WorkOrderSlaIndicator, WorkOrderStatusBadge } from '../../components/work-orders/WorkOrderStateBadges';
import { formatDuration, slaCountdown } from '../../constants/workOrderSla';
import {
    presentationChipSx,
    priorityPresentation,
    reportTypePresentation,
    slaRiskPresentation,
    statusDisplayLabel,
} from '../../constants/workOrderPresentation';
import AuthenticatedAttachmentMedia from '../../components/work-orders/AuthenticatedAttachmentMedia';
import { buildWorkOrderAttachmentPath } from '../../utils/workOrderAttachmentPaths';

const WORK_ORDER_DETAIL_DATE_OPTIONS = { second: '2-digit' };

const buildAttachmentUrl = (workOrderId, attachment) =>
    buildWorkOrderAttachmentPath(workOrderId, attachment.id);

const isImageAttachment = (attachment) => attachment.fileType?.startsWith('image/');
const isVideoAttachment = (attachment) => attachment.fileType?.startsWith('video/');
const getAttachmentStageLabel = (attachment) => attachment.attachmentStage === 'RESPONSE' ? '回单材料' : '报单材料';

function InfoItem({ icon, label, value }) {
    return (
        <Box sx={{ display: 'grid', gridTemplateColumns: '28px minmax(0, 1fr)', gap: 1, alignItems: 'start' }}>
            <Box sx={{ color: 'primary.main', display: 'flex', pt: 0.25 }}>{icon}</Box>
            <Box sx={{ minWidth: 0 }}>
                <Typography variant="caption" color="text.secondary">
                    {label}
                </Typography>
                <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                    {value || '暂无'}
                </Typography>
            </Box>
        </Box>
    );
}

function SectionPanel({ title, children, sx }) {
    return (
        <Paper
            elevation={1}
            sx={{
                p: { xs: 1.25, sm: 1.5 },
                borderRadius: 1,
                height: '100%',
                minWidth: 0,
                ...sx,
            }}
        >
            <Typography variant="subtitle1" component="h2" sx={{ mb: 1, fontWeight: 750 }}>
                {title}
            </Typography>
            {children}
        </Paper>
    );
}

function TextBlock({ children, minHeight = 120 }) {
    return (
        <Box
            sx={{
                minHeight,
                p: 1.5,
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                bgcolor: 'background.default',
                whiteSpace: 'pre-wrap',
                overflowWrap: 'anywhere',
            }}
        >
            <Typography variant="body2">{children || '暂无'}</Typography>
        </Box>
    );
}

function AttachmentPreviewCard({ workOrderId, attachment, onPreview, onDownload }) {
    const attachmentUrl = buildAttachmentUrl(workOrderId, attachment);
    const redactionPending = Boolean(attachment.imageRedactionStatus)
        && attachment.imageRedactionStatus !== 'READY';
    const image = !redactionPending && isImageAttachment(attachment);
    const video = !redactionPending && isVideoAttachment(attachment);

    return (
        <Paper
            variant="outlined"
            sx={{
                borderRadius: 1,
                overflow: 'hidden',
                minWidth: 0,
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            <Box
                sx={{
                    aspectRatio: '16 / 9',
                    minHeight: 168,
                    maxHeight: 260,
                    bgcolor: 'grey.100',
                    borderBottom: '1px solid',
                    borderColor: 'divider',
                    display: 'grid',
                    placeItems: 'center',
                    overflow: 'hidden',
                    cursor: image ? 'zoom-in' : 'default',
                }}
            >
                {image && (
                    <AuthenticatedAttachmentMedia
                        url={attachmentUrl}
                        alt={attachment.fileName}
                        onClick={() => onPreview(attachment)}
                        sx={{ p: 1, cursor: 'zoom-in' }}
                    />
                )}
                {video && (
                    <AuthenticatedAttachmentMedia
                        url={attachmentUrl}
                        mediaType="video"
                        alt={attachment.fileName}
                    />
                )}
                {!image && !video && (
                    <Stack spacing={1} sx={{ alignItems: 'center', color: 'text.secondary' }}>
                        <AttachFileIcon fontSize="large" />
                        <Typography variant="caption">
                            {redactionPending
                                ? (attachment.imageRedactionMessage || '附件正在后台进行安全处理')
                                : '文件附件'}
                        </Typography>
                    </Stack>
                )}
            </Box>
            <Box sx={{ p: 1.25, display: 'flex', flexDirection: 'column', gap: 0.75, flex: 1 }}>
                <Tooltip title={attachment.fileName || ''}>
                    <Typography
                        variant="body2"
                        sx={{
                            fontWeight: 600,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                        }}
                    >
                        {attachment.fileName || attachment.storedFileName}
                    </Typography>
                </Tooltip>
                <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                    <Chip
                        size="small"
                        color={attachment.attachmentStage === 'RESPONSE' ? 'warning' : 'primary'}
                        variant="outlined"
                        label={getAttachmentStageLabel(attachment)}
                    />
                    <Typography variant="caption" color="text.secondary">
                        {attachment.fileType || '未知类型'} · {formatFileSize(attachment.fileSize)}
                    </Typography>
                </Stack>
                <Box sx={{ flex: 1 }} />
                <Button
                    fullWidth
                    size="small"
                    variant="outlined"
                    startIcon={<DownloadIcon />}
                    sx={{ mt: 1 }}
                    onClick={() => onDownload(attachment)}
                    disabled={redactionPending}
                >
                    {redactionPending ? '处理中' : '下载'}
                </Button>
            </Box>
        </Paper>
    );
}

function AttachmentGroup({ title, description, attachments, color, workOrderId, onPreview, onDownload }) {
    if (attachments.length === 0) return null;
    return (
        <Paper variant="outlined" sx={{ p: 1.25, borderRadius: 1, borderTopWidth: 3, borderTopColor: `${color}.main` }}>
            <Box sx={{ mb: 1.25, display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
                <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 750 }}>{title}</Typography>
                    <Typography variant="caption" color="text.secondary">{description}</Typography>
                </Box>
                <Chip size="small" color={color} label={`${attachments.length} 份`} />
            </Box>
            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: {
                        xs: '1fr',
                        sm: 'repeat(2, minmax(0, 1fr))',
                        lg: 'repeat(3, minmax(0, 1fr))',
                        xl: 'repeat(4, minmax(0, 1fr))',
                    },
                    gap: 1.5,
                }}
            >
                {attachments.map((attachment) => (
                    <AttachmentPreviewCard
                        key={attachment.id}
                        workOrderId={workOrderId}
                        attachment={attachment}
                        onPreview={onPreview}
                        onDownload={onDownload}
                    />
                ))}
            </Box>
        </Paper>
    );
}

function AdminWorkOrderDetailPage() {
    const { workOrderId } = useParams();
    const navigate = useNavigate();
    const [workOrder, setWorkOrder] = useState(null);
    const [activeSection, setActiveSection] = useState('details');
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [previewAttachment, setPreviewAttachment] = useState(null);

    const fetchWorkOrderDetails = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const response = await fetchWorkOrderById(workOrderId);
            setWorkOrder(response.data);
        } catch (err) {
            console.error('Failed to fetch work order details:', err);
            setError(formatApiErrorMessage(err, `加载工单详情（标识：${workOrderId}）失败。`));
        } finally {
            setIsLoading(false);
        }
    }, [workOrderId]);

    useEffect(() => {
        if (workOrderId) {
            fetchWorkOrderDetails();
        }
    }, [workOrderId, fetchWorkOrderDetails]);

    const canViewHistory = workOrder?.allowedViews?.includes('HISTORY') === true;
    const canViewAttachments = workOrder?.allowedViews?.includes('ATTACHMENTS') === true;
    const attachments = useMemo(
        () => canViewAttachments ? (workOrder?.attachments || []) : [],
        [canViewAttachments, workOrder],
    );
    const reportAttachments = useMemo(
        () => attachments.filter(attachment => attachment.attachmentStage !== 'RESPONSE'),
        [attachments],
    );
    const responseAttachments = useMemo(
        () => attachments.filter(attachment => attachment.attachmentStage === 'RESPONSE'),
        [attachments],
    );
    const reportTypeLabel = workOrder?.reportTypeName || getReportTypeLabel(workOrder?.reportType);
    const typeToken = reportTypePresentation(workOrder?.reportType);
    const priorityToken = priorityPresentation(workOrder?.priority);
    const slaToken = slaRiskPresentation(workOrder?.slaRiskLevel);
    const activeOrganizationParticipations = (workOrder?.organizationParticipations || [])
        .filter(participation => participation.active);
    const organizationName = relationType => activeOrganizationParticipations
        .find(participation => participation.relationType === relationType)?.organizationName;
    const activeDepartmentParticipations = (workOrder?.departmentParticipations || [])
        .filter(participation => participation.active);
    const departmentName = relationType => activeDepartmentParticipations
        .find(participation => participation.relationType === relationType)?.departmentName;
    const navigationUrl = useMemo(
        () => buildAmapNavigationUrl(workOrder?.gpsCoordinates, workOrder?.location || `工单 ${workOrder?.id || ''}`),
        [workOrder],
    );

    const handleAttachmentDownload = useCallback(async (attachment) => {
        const downloadUrl = buildAttachmentUrl(workOrder.id, attachment);
        try {
            await downloadFileInChunks(downloadUrl, attachment.fileName || attachment.storedFileName);
        } catch (downloadError) {
            console.error('Error downloading attachment:', downloadError);
            alert(`下载附件 ${attachment.fileName || attachment.storedFileName} 失败：${formatApiErrorMessage(downloadError, '附件下载失败，请稍后重试。')}`);
        }
    }, [workOrder]);

    const handleLocationShare = useCallback(async () => {
        try {
            const result = await shareWorkOrderLocation(workOrder);
            if (result === 'copied') {
                window.alert('当前位置已复制，可粘贴到微信、企业微信或其他应用。');
            }
        } catch (shareError) {
            if (shareError?.name === 'AbortError') return;
            window.alert(formatApiErrorMessage(shareError, '位置分享失败，请稍后重试。'));
        }
    }, [workOrder]);

    if (isLoading) {
        return (
            <Container maxWidth={false} disableGutters sx={{ py: 3, display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
                <CircularProgress />
                <Typography sx={{ ml: 2 }}>加载工单详情...</Typography>
            </Container>
        );
    }

    if (error) {
        return (
            <Container maxWidth={false} disableGutters sx={{ py: 3 }}>
                <Alert
                    severity="error"
                    action={<Button color="inherit" size="small" onClick={fetchWorkOrderDetails}>重试</Button>}
                >
                    {error}
                </Alert>
            </Container>
        );
    }

    if (!workOrder) {
        return (
            <Container maxWidth={false} disableGutters sx={{ py: 3 }}>
                <Alert severity="warning">未找到工单信息。</Alert>
            </Container>
        );
    }

    return (
        <Container maxWidth={false} disableGutters sx={{ width: '100%', mt: 0, mb: 2 }}>
            <Box
                sx={{
                    display: 'flex',
                    alignItems: { xs: 'flex-start', sm: 'center' },
                    justifyContent: 'space-between',
                    gap: 1.5,
                    mb: 1.5,
                    flexDirection: { xs: 'column', sm: 'row' },
                }}
            >
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.25} sx={{ minWidth: 0, alignItems: { xs: 'flex-start', sm: 'center' } }}>
                    <Button
                        startIcon={<ArrowBackIcon />}
                        onClick={() => navigate(-1)}
                        variant="outlined"
                        size="small"
                        sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}
                    >
                        返回列表
                    </Button>
                    <Box sx={{ minWidth: 0 }}>
                        <PageTitle>
                            {workOrder.workOrderNo || `工单 #${workOrder.id}`}
                        </PageTitle>
                        <Typography variant="body2" color="text.secondary">
                            {reportTypeLabel} · {formatDate(workOrder.reportTime)}
                        </Typography>
                    </Box>
                </Stack>
                <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                    <WorkOrderStatusBadge workOrder={workOrder} />
                    <Chip label={reportTypeLabel} variant="outlined" sx={presentationChipSx(typeToken)} />
                    <Chip label={workOrder.priorityName || workOrder.priority || '常规'} variant="outlined" sx={presentationChipSx(priorityToken)} />
                    <WorkOrderSlaIndicator workOrder={workOrder} />
                </Stack>
            </Box>

            <Box
                sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 1.25,
                }}
            >
                <WorkOrderLifecycleActions
                    workOrder={workOrder}
                    onUpdated={setWorkOrder}
                    onRespond={() => {
                        const type = findWorkOrderType(workOrder.reportType);
                        if (type?.respondPath) {
                            navigate(`${type.respondPath}?workOrderId=${workOrder.id}`);
                        }
                    }}
                />
                <Box>
                    <PageTabs id="work-order" label="工单详情分类" value={activeSection} onChange={setActiveSection}
                        options={[
                            { value: 'details', label: '工单信息' },
                            ...(canViewAttachments ? [{ value: 'attachments', label: `附件 (${attachments.length})` }] : []),
                            ...(canViewHistory ? [{ value: 'history', label: '处理记录' }] : []),
                            { value: 'transfer', label: '组织移交' },
                        ]} />
                <PageTabPanel id="work-order" value="details" selected={activeSection}>
                    <Box sx={{ display: 'grid', gap: 1.5 }}>
                    <SectionPanel title="处理时限">
                        {workOrder.slaRiskLevel === 'BREACHED' && (
                            <Alert
                                severity="info"
                                sx={{ mb: 1.5, color: slaToken.foreground, bgcolor: slaToken.surface, borderColor: slaToken.border }}
                            >
                                当前{workOrder.slaStageName || '处理'}阶段已超时 {formatDuration(workOrder.slaBreachSeconds)}
                            </Alert>
                        )}
                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' },
                                gap: 1.5,
                            }}
                        >
                            <InfoItem icon={<SpeedIcon fontSize="small" />} label="当前阶段" value={workOrder.slaStageName} />
                            <InfoItem icon={<CalendarTodayIcon fontSize="small" />} label="阶段截止" value={workOrder.slaDueAt ? formatDate(workOrder.slaDueAt) : '已结束'} />
                            <InfoItem icon={<SpeedIcon fontSize="small" />} label="阶段进度" value={slaCountdown(workOrder)} />
                            <InfoItem icon={<CalendarTodayIcon fontSize="small" />} label="故障历时" value={formatDuration(workOrder.elapsedSeconds)} />
                        </Box>
                    </SectionPanel>
                    <SectionPanel title="工单概览">
                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: {
                                    xs: 'repeat(2, minmax(0, 1fr))',
                                    lg: 'repeat(4, minmax(0, 1fr))',
                                },
                                gap: 1.5,
                            }}
                        >
                            <InfoItem icon={<ReportProblemIcon fontSize="small" />} label="工单类型" value={reportTypeLabel} />
                            <InfoItem icon={<PersonIcon fontSize="small" />} label="当前归属组织" value={organizationName('OWNER')} />
                            <InfoItem icon={<PersonIcon fontSize="small" />} label="提单部门" value={departmentName('REPORTING') || '组织直属'} />
                            <InfoItem icon={<PersonIcon fontSize="small" />} label="当前责任部门" value={departmentName('RESPONSIBLE') || '组织公共工单池'} />
                            <InfoItem icon={<PersonIcon fontSize="small" />} label="当前处理部门" value={departmentName('PROCESSING') || '尚未进入处理'} />
                            {workOrder.reportType === WORK_ORDER_TYPE_CODES.BOX_DOOR_OPEN && (
                                <InfoItem icon={<BuildIcon fontSize="small" />} label="箱门未关数量" value={workOrder.boxQuantity} />
                            )}
                            <InfoItem icon={<LocationOnIcon fontSize="small" />} label="工单地点" value={workOrder.location} />
                            <InfoItem icon={<GpsFixedIcon fontSize="small" />} label="定位坐标（经度,纬度）" value={workOrder.gpsCoordinates} />
                            <InfoItem icon={<PersonIcon fontSize="small" />} label="联系人" value={workOrder.contactPerson} />
                            <InfoItem icon={<PhoneIcon fontSize="small" />} label="联系电话" value={workOrder.phone} />
                            <InfoItem
                                icon={<PersonIcon fontSize="small" />}
                                label="报单人"
                                value={`${workOrder.reportedBy?.name || '暂无'}（${workOrder.reportedBy?.username || '暂无'}）`}
                            />
                            <InfoItem
                                icon={<PersonIcon fontSize="small" />}
                                label={workOrder.handlerStateName || '处理人员'}
                                value={workOrder.handler
                                    ? `${workOrder.handler.name || '暂无'}（${workOrder.handler.username || '暂无'}）`
                                    : '暂无'}
                            />
                            <InfoItem icon={<CalendarTodayIcon fontSize="small" />} label="上报时间" value={formatDate(workOrder.reportTime, 'zh-CN', WORK_ORDER_DETAIL_DATE_OPTIONS)} />
                            <InfoItem icon={<CalendarTodayIcon fontSize="small" />} label="更新时间" value={formatDate(workOrder.lastUpdatedTime, 'zh-CN', WORK_ORDER_DETAIL_DATE_OPTIONS)} />
                        </Box>
                        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mt: 1.5 }}>
                            <Tooltip title={navigationUrl ? '使用工单定位坐标唤起地图应用' : '工单没有有效定位坐标'}>
                                <span>
                                    <Button
                                        variant="outlined"
                                        startIcon={<NavigationIcon />}
                                        disabled={!navigationUrl}
                                        href={navigationUrl || undefined}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                    >
                                        导航到现场
                                    </Button>
                                </span>
                            </Tooltip>
                            <Tooltip title={navigationUrl ? '分享到微信等应用' : '工单没有有效定位坐标'}>
                                <span>
                                    <Button
                                        variant="outlined"
                                        startIcon={<ShareIcon />}
                                        disabled={!navigationUrl}
                                        onClick={handleLocationShare}
                                    >
                                        分享位置链接
                                    </Button>
                                </span>
                            </Tooltip>
                        </Stack>
                    </SectionPanel>

                    <SectionPanel title="工单内容">
                        <Stack spacing={1.5}>
                            <Box>
                                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                                    报单内容
                                </Typography>
                                <TextBlock minHeight={140}>{workOrder.reason || '无描述'}</TextBlock>
                            </Box>

                            <Divider />

                            <Box>
                                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                                    处理情况
                                </Typography>
                                {(['PENDING_VERIFICATION', 'CLOSED'].includes(workOrder.statusCode)) ? (
                                    <Stack spacing={1.5}>
                                        <Box
                                            sx={{
                                                display: 'grid',
                                                gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' },
                                                gap: 1.5,
                                            }}
                                        >
                                            <InfoItem icon={<CheckCircleIcon fontSize="small" />} label="处理状态" value={statusDisplayLabel(workOrder)} />
                                            <InfoItem icon={<BuildIcon fontSize="small" />} label="处理方式/措施" value={workOrder.resolutionMethod} />
                                            <InfoItem icon={<CalendarTodayIcon fontSize="small" />} label="处理时间" value={formatDate(workOrder.resolutionTime)} />
                                            <InfoItem
                                                icon={<PersonIcon fontSize="small" />}
                                                label="处理人"
                                                value={`${workOrder.resolvedBy?.name || '暂无'}（${workOrder.resolvedBy?.username || '暂无'}）`}
                                            />
                                        </Box>

                                        {workOrder.reportType === WORK_ORDER_TYPE_CODES.DAMAGED_BOX_REPLACEMENT && (
                                            <>
                                                <Divider />
                                                <Box
                                                    sx={{
                                                        display: 'grid',
                                                        gridTemplateColumns: { xs: '1fr', lg: 'minmax(220px, 0.3fr) minmax(0, 0.7fr)' },
                                                        gap: 1.5,
                                                    }}
                                                >
                                                    <InfoItem
                                                        icon={<BuildIcon fontSize="small" />}
                                                        label="是否已换箱"
                                                        value={workOrder.replacementCompleted === true ? '已换箱' : (workOrder.replacementCompleted === false ? '未换箱' : '暂无')}
                                                    />
                                                    <TextBlock minHeight={88}>{workOrder.replacementResult || '暂无'}</TextBlock>
                                                </Box>
                                            </>
                                        )}

                                        {workOrder.materialUsages?.length > 0 && (
                                            <>
                                                <Divider />
                                                <Box>
                                                    <Typography variant="subtitle2" sx={{ mb: 1 }}>
                                                        消耗物料
                                                    </Typography>
                                                    <Box
                                                        sx={{
                                                            display: 'grid',
                                                            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' },
                                                            gap: 1,
                                                        }}
                                                    >
                                                        {workOrder.materialUsages.map((item, index) => (
                                                            <Paper key={`${item.category}-${item.specification}-${index}`} variant="outlined" sx={{ p: 1.25, borderRadius: 1 }}>
                                                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                                                    {item.categoryName || item.category}
                                                                </Typography>
                                                                <Typography variant="caption" color="text.secondary">
                                                                    {item.specification} · {item.quantity}
                                                                </Typography>
                                                            </Paper>
                                                        ))}
                                                    </Box>
                                                </Box>
                                            </>
                                        )}

                                        <TextBlock minHeight={110}>{workOrder.resolutionDetails || '暂无'}</TextBlock>
                                    </Stack>
                                ) : (
                                    <Alert severity="info">当前工单尚未回单处理。</Alert>
                                )}
                            </Box>
                        </Stack>
                    </SectionPanel>

                    </Box>
                </PageTabPanel>
                <PageTabPanel id="work-order" value="transfer" selected={activeSection}>
                    <SectionPanel title="组织移交">
                        <WorkOrderOrganizationTransferPanel workOrder={workOrder} onChanged={fetchWorkOrderDetails} />
                    </SectionPanel>
                </PageTabPanel>
                <PageTabPanel id="work-order" value="history" selected={activeSection}>
                    {canViewHistory && (
                        <SectionPanel title="处理与审计历史">
                            <WorkOrderHistory workOrderId={workOrder.id} version={workOrder.version} />
                        </SectionPanel>
                    )}

                </PageTabPanel>
                <PageTabPanel id="work-order" value="attachments" selected={activeSection}>
                    {canViewAttachments && (
                        <SectionPanel title={`附件预览 (${attachments.length})`}>
                            {attachments.length > 0 ? (
                                <Stack spacing={1.5}>
                                    <AttachmentGroup
                                        title="报单材料"
                                        description="提单时上传的现场情况与故障依据"
                                        attachments={reportAttachments}
                                        color="primary"
                                        workOrderId={workOrder.id}
                                        onPreview={setPreviewAttachment}
                                        onDownload={handleAttachmentDownload}
                                    />
                                    <AttachmentGroup
                                        title="回单材料"
                                        description="处理人员回单时上传的结果与佐证"
                                        attachments={responseAttachments}
                                        color="warning"
                                        workOrderId={workOrder.id}
                                        onPreview={setPreviewAttachment}
                                        onDownload={handleAttachmentDownload}
                                    />
                                </Stack>
                            ) : (
                                <Box sx={{ py: 4, textAlign: 'center', color: 'text.secondary' }}>
                                    <ImageIcon sx={{ fontSize: 40, mb: 1 }} />
                                    <Typography variant="body2">此工单暂无附件。</Typography>
                                </Box>
                            )}
                        </SectionPanel>
                    )}
                </PageTabPanel>
                </Box>
            </Box>

            <Dialog
                open={Boolean(previewAttachment)}
                onClose={() => setPreviewAttachment(null)}
                maxWidth="lg"
                fullWidth
            >
                <DialogTitle sx={{ pr: 6 }}>
                    {previewAttachment?.fileName || '图片预览'}
                    <IconButton
                        aria-label="关闭预览"
                        onClick={() => setPreviewAttachment(null)}
                        sx={{ position: 'absolute', right: 8, top: 8 }}
                    >
                        <CloseIcon />
                    </IconButton>
                </DialogTitle>
                <DialogContent dividers sx={{ bgcolor: 'grey.100' }}>
                    {previewAttachment && (
                        <AuthenticatedAttachmentMedia
                            url={buildAttachmentUrl(workOrder.id, previewAttachment)}
                            alt={previewAttachment.fileName}
                            sx={{
                                display: 'block',
                                width: 'auto',
                                height: 'auto',
                                maxWidth: '100%',
                                maxHeight: '76vh',
                                mx: 'auto',
                                objectFit: 'contain',
                            }}
                        />
                    )}
                </DialogContent>
            </Dialog>

        </Container>
    );
}

export default AdminWorkOrderDetailPage;

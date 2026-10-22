import PageTitle from '../../components/layout/PageTitle';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
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
    IconButton,
    MenuItem,
    Paper,
    Snackbar,
    Stack,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import DescriptionIcon from '@mui/icons-material/Description';
import DownloadIcon from '@mui/icons-material/GetApp';
import ImageIcon from '@mui/icons-material/Image';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import CloseIcon from '@mui/icons-material/Close';

import FileUploadWithPreview from '../../components/FileUploadWithPreview/FileUploadWithPreview';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { WORK_ORDER_TYPE_CODES, getReportTypeLabel } from '../../constants/workOrderCatalog';
import { findMaterialOption, MATERIAL_OPTIONS } from '../../constants/materials';
import { fetchWorkOrderById, respondWorkOrder } from '../../services/workOrderApiService';
import { downloadFileInChunks } from '../../services/fileDownloadService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatFileSize } from '../../utils/formatFileSize';
import { hasAllowedAction, workOrderStatusLabel } from '../../constants/workOrderLifecycle';
import AuthenticatedAttachmentMedia from '../../components/work-orders/AuthenticatedAttachmentMedia';
import { buildWorkOrderAttachmentPath } from '../../utils/workOrderAttachmentPaths';
import {
    MAX_ATTACHMENT_FILES,
    MAX_ATTACHMENT_FILE_SIZE_MB,
    MAX_ATTACHMENT_TOTAL_MB,
} from '../../constants/attachmentLimits';

const createInitialResolutionData = () => ({
    resolutionDetails: '',
    resolutionMethod: '',
    replacementCompleted: '',
    replacementResult: '',
});

const createInitialMaterialDraft = () => ({
    category: MATERIAL_OPTIONS[0].value,
    specification: MATERIAL_OPTIONS[0].specifications[0],
    quantity: '1',
});

const buildAttachmentUrl = (workOrderId, attachment) =>
    buildWorkOrderAttachmentPath(workOrderId, attachment.id);

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function RespondWorkOrderPage({ reportType = WORK_ORDER_TYPE_CODES.NETWORK_FAULT, title = '工单回单', searchPath = '/work-orders/search' }) {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const requestedWorkOrderId = searchParams.get('workOrderId');
    const workOrderId = requestedWorkOrderId?.trim() || '';
    const hasValidWorkOrderId = UUID_PATTERN.test(workOrderId);
    const [loadError, setLoadError] = useState(null);

    const [selectedWorkOrder, setSelectedWorkOrder] = useState(null);
    const [isLoadingWorkOrderDetails, setIsLoadingWorkOrderDetails] = useState(false);
    const [previewAttachment, setPreviewAttachment] = useState(null);

    const [resolutionData, setResolutionData] = useState(createInitialResolutionData);
    const [responseFiles, setResponseFiles] = useState([]);
    const [responseFileError, setResponseFileError] = useState(null);
    const [materialDraft, setMaterialDraft] = useState(createInitialMaterialDraft);
    const [materialUsages, setMaterialUsages] = useState([]);

    const [isSubmitting, setIsSubmitting] = useState(false);
    const [submissionStage, setSubmissionStage] = useState('');
    const [submissionResult, setSubmissionResult] = useState(null);
    const [snackbarOpen, setSnackbarOpen] = useState(false);

    const selectedMaterialOption = useMemo(
        () => findMaterialOption(materialDraft.category),
        [materialDraft.category]
    );

    const resetResponseForm = useCallback(() => {
        setResolutionData(createInitialResolutionData());
        setResponseFiles([]);
        setResponseFileError(null);
        setMaterialDraft(createInitialMaterialDraft());
        setMaterialUsages([]);
    }, []);

    const loadWorkOrder = useCallback(async (id) => {
        setIsLoadingWorkOrderDetails(true);
        setSelectedWorkOrder(null);
        setLoadError(null);
        try {
            const response = await fetchWorkOrderById(id);
            if (response.data?.reportType !== reportType) {
                setLoadError('工单类型与当前回单页面不一致，请返回工单查询后重新进入。');
                return;
            }
            setSelectedWorkOrder(response.data);
            resetResponseForm();
            setSubmissionResult(null);
        } catch (error) {
            console.error('Error fetching work order details for response:', error);
            setLoadError(formatApiErrorMessage(error, `加载工单 #${id} 详情失败。`));
        } finally {
            setIsLoadingWorkOrderDetails(false);
        }
    }, [reportType, resetResponseForm]);

    useEffect(() => {
        if (!hasValidWorkOrderId) {
            return;
        }
        loadWorkOrder(workOrderId);
    }, [hasValidWorkOrderId, loadWorkOrder, workOrderId]);

    const handleResolutionChange = (event) => {
        const { name, value } = event.target;
        setResolutionData((current) => ({ ...current, [name]: value }));
    };

    const handleResponseFilesUpdate = (updatedFiles, uploadError) => {
        setResponseFiles(updatedFiles);
        setResponseFileError(uploadError);
    };

    const handleMaterialDraftChange = (field, value) => {
        if (field === 'category') {
            const nextOption = findMaterialOption(value);
            setMaterialDraft({
                category: nextOption.value,
                specification: nextOption.specifications[0],
                quantity: materialDraft.quantity,
            });
            return;
        }
        setMaterialDraft((current) => ({ ...current, [field]: value }));
    };

    const addMaterialUsage = () => {
        const option = findMaterialOption(materialDraft.category);
        const quantity = Number(materialDraft.quantity);
        if (!Number.isInteger(quantity) || quantity < 1) {
            setSubmissionResult({ success: false, message: '物料数量必须为大于 0 的整数。' });
            setSnackbarOpen(true);
            return;
        }
        if (!option.specifications.includes(materialDraft.specification)) {
            setSubmissionResult({ success: false, message: '物料规格不属于当前物料类别。' });
            setSnackbarOpen(true);
            return;
        }
        setMaterialUsages((current) => {
            const existingIndex = current.findIndex(
                (item) => item.category === option.value && item.specification === materialDraft.specification
            );
            if (existingIndex >= 0) {
                return current.map((item, index) =>
                    index === existingIndex ? { ...item, quantity: item.quantity + quantity } : item
                );
            }
            return [
                ...current,
                {
                    category: option.value,
                    categoryName: option.label,
                    specification: materialDraft.specification,
                    quantity,
                },
            ];
        });
        setMaterialDraft((current) => ({ ...current, quantity: '1' }));
    };

    const removeMaterialUsage = (indexToRemove) => {
        setMaterialUsages((current) => current.filter((_, index) => index !== indexToRemove));
    };

    const validateResolveForm = () => {
        if (!resolutionData.resolutionDetails.trim()) return '请填写处理情况描述。';
        if (!resolutionData.resolutionMethod.trim()) return '请填写处理方式/措施。';
        if (responseFileError) return responseFileError;
        if (responseFiles.length === 0) return '请至少上传一张回单照片。';

        const requiresReplacementResult = selectedWorkOrder.reportType === WORK_ORDER_TYPE_CODES.DAMAGED_BOX_REPLACEMENT;
        if (requiresReplacementResult && resolutionData.replacementCompleted === '') {
            return '请先选择是否已换箱。';
        }
        if (
            requiresReplacementResult
            && resolutionData.replacementCompleted === 'true'
            && !resolutionData.replacementResult.trim()
        ) {
            return '已换箱时必须填写换箱后结果。';
        }
        return null;
    };

    const handleResolveSubmit = async (event) => {
        event.preventDefault();
        if (!selectedWorkOrder) return;

        const validationError = validateResolveForm();
        if (validationError) {
            setSubmissionResult({ success: false, message: validationError });
            setSnackbarOpen(true);
            return;
        }

        setIsSubmitting(true);
        setSubmissionStage('正在准备回单材料');
        setSubmissionResult(null);

        try {
            const requiresReplacementResult = selectedWorkOrder.reportType === WORK_ORDER_TYPE_CODES.DAMAGED_BOX_REPLACEMENT;
            const replacementCompleted = resolutionData.replacementCompleted === 'true'
                ? true
                : resolutionData.replacementCompleted === 'false'
                    ? false
                    : null;
            const payload = {
                expectedVersion: selectedWorkOrder.version,
                resolutionDetails: resolutionData.resolutionDetails.trim(),
                resolutionMethod: resolutionData.resolutionMethod.trim(),
                replacementCompleted: requiresReplacementResult ? replacementCompleted : null,
                replacementResult: requiresReplacementResult ? resolutionData.replacementResult.trim() : '',
                materialUsages: materialUsages.map(({ category, specification, quantity }) => ({
                    category,
                    specification,
                    quantity,
                })),
            };

            const response = await respondWorkOrder(selectedWorkOrder.id, payload, responseFiles, {
                onProgress: (progress) => setSubmissionStage(
                    `正在上传回单材料 ${progress.fileIndex + 1}/${progress.fileCount}`
                ),
            });
            setSubmissionResult({ success: true, message: `工单 #${selectedWorkOrder.id} 回单成功！` });
            setSnackbarOpen(true);
            setSelectedWorkOrder(response.data);
            resetResponseForm();
        } catch (error) {
            console.error('工单回单错误:', error.response?.data || error.message);
            setSubmissionResult({
                success: false,
                message: formatApiErrorMessage(error, '回单失败，请稍后重试。'),
            });
            setSnackbarOpen(true);
        } finally {
            setIsSubmitting(false);
            setSubmissionStage('');
        }
    };

    const handleAttachmentDownload = async (workOrderId, attachment) => {
        try {
            await downloadFileInChunks(buildAttachmentUrl(workOrderId, attachment), attachment.fileName || attachment.storedFileName);
        } catch (error) {
            console.error('Error downloading attachment:', error);
            setSubmissionResult({ success: false, message: `下载附件失败：${formatApiErrorMessage(error, '附件下载失败，请稍后重试。')}` });
            setSnackbarOpen(true);
        }
    };

    const getFileIcon = (fileType) => {
        if (!fileType) return <DescriptionIcon />;
        if (fileType.startsWith('image/')) return <ImageIcon />;
        if (fileType === 'application/pdf') return <PictureAsPdfIcon />;
        return <DescriptionIcon />;
    };

    const reportAttachments = selectedWorkOrder?.attachments?.filter(attachment => attachment.attachmentStage !== 'RESPONSE') || [];
    const previousResponseAttachments = selectedWorkOrder?.attachments?.filter(attachment => attachment.attachmentStage === 'RESPONSE') || [];
    const renderExistingAttachmentGroup = (groupTitle, description, attachments, color) => {
        if (attachments.length === 0) return null;
        return (
            <Paper variant="outlined" sx={{ p: 1.25, borderRadius: 1, borderTopWidth: 3, borderTopColor: `${color}.main` }}>
                <Box sx={{ mb: 1.25, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
                    <Box>
                        <Typography variant="subtitle2" sx={{ fontWeight: 750 }}>{groupTitle}</Typography>
                        <Typography variant="caption" color="text.secondary">{description}</Typography>
                    </Box>
                    <Chip size="small" color={color} label={`${attachments.length} 份`} />
                </Box>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                            xs: 'minmax(0, 1fr)',
                            sm: 'repeat(2, minmax(0, 1fr))',
                            md: 'repeat(3, minmax(0, 1fr))',
                            lg: 'repeat(4, minmax(0, 1fr))',
                        },
                        gap: 2,
                    }}
                >
                    {attachments.map((attachment) => {
                        const redactionPending = Boolean(attachment.imageRedactionStatus)
                            && attachment.imageRedactionStatus !== 'READY';
                        return (
                            <Paper key={attachment.id} variant="outlined" sx={{ minWidth: 0, overflow: 'hidden', borderRadius: 1 }}>
                            {!redactionPending && attachment.fileType?.startsWith('image/') ? (
                                <AuthenticatedAttachmentMedia
                                    url={buildAttachmentUrl(selectedWorkOrder.id, attachment)}
                                    alt={attachment.fileName}
                                    sx={{ width: '100%', height: 150, objectFit: 'contain', bgcolor: 'grey.50', display: 'block', cursor: 'zoom-in' }}
                                    onClick={() => setPreviewAttachment(attachment)}
                                />
                            ) : (
                                <Box sx={{ height: 150, display: 'grid', placeItems: 'center', bgcolor: 'grey.100' }}>
                                    {redactionPending
                                        ? <Typography variant="caption" color="text.secondary" sx={{ px: 2, textAlign: 'center' }}>{attachment.imageRedactionMessage || '附件正在后台进行安全处理'}</Typography>
                                        : getFileIcon(attachment.fileType)}
                                </Box>
                            )}
                            <Box sx={{ p: 1.25 }}>
                                <Chip size="small" color={color} variant="outlined" label={groupTitle} sx={{ mb: 0.75 }} />
                                <Tooltip title={attachment.fileName || ''}>
                                    <Typography variant="subtitle2" noWrap>{attachment.fileName}</Typography>
                                </Tooltip>
                                <Typography variant="caption" color="text.secondary">
                                    {attachment.fileType || '未知类型'} · {formatFileSize(attachment.fileSize)}
                                </Typography>
                                <Button
                                    fullWidth
                                    size="small"
                                    variant="outlined"
                                    startIcon={<DownloadIcon />}
                                    sx={{ mt: 1 }}
                                    onClick={() => handleAttachmentDownload(selectedWorkOrder.id, attachment)}
                                    disabled={redactionPending}
                                >
                                    {redactionPending ? '处理中' : '下载'}
                                </Button>
                            </Box>
                            </Paper>
                        );
                    })}
                </Box>
            </Paper>
        );
    };

    return (
        <Container maxWidth="lg" disableGutters>
            <PageTitle
                sx={{ mb: 2 }}
            >
                {title}
            </PageTitle>

            {!hasValidWorkOrderId && (
                <Paper elevation={1} sx={{ p: { xs: 2, sm: 3 }, mb: 3, borderRadius: 1 }}>
                    <Alert severity="info" sx={{ mb: 2 }}>
                        请先在工单查询中选择一条待处理工单，再进入回单。
                    </Alert>
                    <Button variant="contained" onClick={() => navigate(`${searchPath}?status=待处理&type=${reportType}`)}>
                        前往工单查询
                    </Button>
                </Paper>
            )}

            {loadError && (
                <Alert
                    severity="warning"
                    sx={{ mb: 2, whiteSpace: 'pre-wrap' }}
                    action={<Button color="inherit" onClick={() => navigate(searchPath)}>返回查询</Button>}
                >
                    {loadError}
                </Alert>
            )}

            {isLoadingWorkOrderDetails && (
                <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', my: 3 }}>
                    <CircularProgress />
                    <Typography sx={{ ml: 2 }}>加载工单详情...</Typography>
                </Box>
            )}

            {submissionResult?.success && (
                <Alert
                    severity="success"
                    sx={{ mb: 2 }}
                    action={<Button color="inherit" onClick={() => navigate(searchPath)}>返回查询</Button>}
                >
                    {submissionResult.message}
                </Alert>
            )}

            {!isLoadingWorkOrderDetails && selectedWorkOrder && !submissionResult?.success && (
                <Box component="form" onSubmit={handleResolveSubmit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    <Paper elevation={2} sx={{ p: { xs: 2, md: 3 }, borderRadius: 1 }}>
                        <Typography
 variant="h5"
 component="h2"
 gutterBottom
 sx={{ fontSize: { xs: '1.25rem', sm: '1.5rem' }, overflowWrap: 'anywhere' }}
 >
                            回单处理：<Typography component="span" color="primary.main" sx={{ fontWeight: "bold" }}>{selectedWorkOrder.location}</Typography>
                        </Typography>
                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
                                gap: 1,
                            }}
                        >
                            <Typography variant="body1"><strong>工单标识：</strong> {selectedWorkOrder.id}</Typography>
                            <Typography variant="body1"><strong>事件类型：</strong> {selectedWorkOrder.reportTypeName || getReportTypeLabel(selectedWorkOrder.reportType)}</Typography>
                            {selectedWorkOrder.reportType === WORK_ORDER_TYPE_CODES.BOX_DOOR_OPEN && (
                                <Typography variant="body1"><strong>箱门未关数量：</strong> {selectedWorkOrder.boxQuantity || '未填写'}</Typography>
                            )}
                            <Typography variant="body1"><strong>当前状态：</strong> {workOrderStatusLabel(selectedWorkOrder)}</Typography>
                        </Box>
                        <Typography variant="body1" sx={{ mt: 1.5, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                            <strong>问题描述：</strong> {selectedWorkOrder.reason || '未填写'}
                        </Typography>
                    </Paper>

                    <Paper elevation={2} sx={{ p: { xs: 2, md: 3 }, borderRadius: 1 }}>
                        <Typography variant="h6" gutterBottom>已有附件</Typography>
                        {selectedWorkOrder.attachments?.length > 0 ? (
                            <Stack spacing={1.5}>
                                {renderExistingAttachmentGroup('报单材料', '提单时上传的现场情况与故障依据', reportAttachments, 'primary')}
                                {renderExistingAttachmentGroup('回单材料', '历次回单时上传的处理结果与佐证', previousResponseAttachments, 'warning')}
                            </Stack>
                        ) : (
                            <Typography variant="body2" color="text.secondary">此工单暂无附件。</Typography>
                        )}
                    </Paper>

                    <Paper elevation={2} sx={{ p: { xs: 2, md: 3 }, borderRadius: 1 }}>
                        <Typography variant="h6" gutterBottom>回单内容</Typography>
                        <TextField
                            name="resolutionDetails"
                            label="处理情况描述"
                            multiline
                            rows={4}
                            value={resolutionData.resolutionDetails}
                            onChange={handleResolutionChange}
                            required
                            margin="normal"
                            fullWidth
                            disabled={isSubmitting}
                        />
                        <TextField
                            name="resolutionMethod"
                            label="处理方式/措施"
                            value={resolutionData.resolutionMethod}
                            onChange={handleResolutionChange}
                            required
                            margin="normal"
                            fullWidth
                            disabled={isSubmitting}
                        />
                        {selectedWorkOrder.reportType === WORK_ORDER_TYPE_CODES.DAMAGED_BOX_REPLACEMENT && (
                            <>
                                <TextField
                                    select
                                    name="replacementCompleted"
                                    label="是否已换箱"
                                    value={resolutionData.replacementCompleted}
                                    onChange={handleResolutionChange}
                                    required
                                    margin="normal"
                                    fullWidth
                                    disabled={isSubmitting}
                                >
                                    <MenuItem value="">请选择</MenuItem>
                                    <MenuItem value="true">已换箱</MenuItem>
                                    <MenuItem value="false">未换箱</MenuItem>
                                </TextField>
                                <TextField
                                    name="replacementResult"
                                    label={resolutionData.replacementCompleted === 'false' ? '未换箱原因' : '换箱后结果'}
                                    value={resolutionData.replacementResult}
                                    onChange={handleResolutionChange}
                                    required={resolutionData.replacementCompleted === 'true'}
                                    margin="normal"
                                    fullWidth
                                    multiline
                                    rows={3}
                                    disabled={isSubmitting}
                                />
                            </>
                        )}
                    </Paper>

                    <Paper elevation={2} sx={{ p: { xs: 2, md: 3 }, borderRadius: 1 }}>
                        <Typography variant="h6" gutterBottom>回单材料</Typography>
                        <FileUploadWithPreview
                            id={`response-attachments-${selectedWorkOrder.id}`}
                            key={`response-attachments-${selectedWorkOrder.id}`}
                            onFilesChange={handleResponseFilesUpdate}
                            maxFiles={MAX_ATTACHMENT_FILES}
                            maxFileSizeMB={MAX_ATTACHMENT_FILE_SIZE_MB}
                            maxTotalSizeMB={MAX_ATTACHMENT_TOTAL_MB}
                            accept="image/*,video/*"
                        />
                        {responseFileError && <Alert severity="warning" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>{responseFileError}</Alert>}
                    </Paper>

                    <Paper elevation={2} sx={{ p: { xs: 2, md: 3 }, borderRadius: 1 }}>
                        <Typography variant="h6" gutterBottom>消耗物料</Typography>
                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: { xs: '1fr', sm: '1.1fr 1.1fr 0.8fr auto' },
                                gap: 1.5,
                                alignItems: 'center',
                            }}
                        >
                            <TextField
                                select
                                label="物料类别"
                                value={materialDraft.category}
                                onChange={(event) => handleMaterialDraftChange('category', event.target.value)}
                                disabled={isSubmitting}
                            >
                                {MATERIAL_OPTIONS.map((option) => (
                                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                select
                                label="规格"
                                value={materialDraft.specification}
                                onChange={(event) => handleMaterialDraftChange('specification', event.target.value)}
                                disabled={isSubmitting}
                            >
                                {selectedMaterialOption.specifications.map((specification) => (
                                    <MenuItem key={specification} value={specification}>{specification}</MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                label="数量"
                                type="number"
                                value={materialDraft.quantity}
                                onChange={(event) => handleMaterialDraftChange('quantity', event.target.value)}
                                slotProps={{ htmlInput: { min: 1, step: 1 } }}
                                disabled={isSubmitting}
                            />
                            <Button
                                variant="outlined"
                                onClick={addMaterialUsage}
                                disabled={isSubmitting}
                                sx={{ minHeight: 56 }}
                            >
                                添加
                            </Button>
                        </Box>

                        {materialUsages.length > 0 && (
                            <Stack spacing={1} sx={{ mt: 2 }}>
                                {materialUsages.map((item, index) => (
                                    <Paper
                                        key={`${item.category}-${item.specification}`}
                                        variant="outlined"
                                        sx={{ p: 1.25, display: 'flex', alignItems: 'center', gap: 1, justifyContent: 'space-between' }}
                                    >
                                        <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                                            {item.categoryName} · {item.specification} · {item.quantity}
                                        </Typography>
                                        <IconButton
                                            aria-label="移除物料"
                                            onClick={() => removeMaterialUsage(index)}
                                            disabled={isSubmitting}
                                            size="small"
                                        >
                                            <DeleteIcon fontSize="small" />
                                        </IconButton>
                                    </Paper>
                                ))}
                            </Stack>
                        )}
                    </Paper>

                    {isSubmitting && submissionStage && (
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                            {submissionStage}
                        </Typography>
                    )}
                    <Button
                        type="submit"
                        fullWidth
                        variant="contained"
                        color="primary"
                        disabled={isSubmitting || !hasAllowedAction(selectedWorkOrder, 'RESPOND')}
                        sx={{ py: 1.5, fontSize: { xs: '1rem', sm: '1.1rem' }, minHeight: 50 }}
                        startIcon={isSubmitting && <CircularProgress size={24} color="inherit" />}
                    >
                        {isSubmitting ? '提交中...' : `提交回单 (当前状态: ${workOrderStatusLabel(selectedWorkOrder)})`}
                    </Button>

                    {!hasAllowedAction(selectedWorkOrder, 'RESPOND') && (
                        <Alert severity="info">当前工单为“{workOrderStatusLabel(selectedWorkOrder)}”，服务端未授权本账号提交回单。</Alert>
                    )}
                </Box>
            )}

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
                    {previewAttachment && selectedWorkOrder && (
                        <AuthenticatedAttachmentMedia
                            url={buildAttachmentUrl(selectedWorkOrder.id, previewAttachment)}
                            alt={previewAttachment.fileName}
                            sx={{ width: 'auto', height: 'auto', maxWidth: '100%', maxHeight: '76vh', mx: 'auto' }}
                        />
                    )}
                </DialogContent>
            </Dialog>

            <Snackbar
                open={snackbarOpen}
                autoHideDuration={6000}
                onClose={(event, reason) => {
                    if (reason !== 'clickaway') setSnackbarOpen(false);
                }}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
            >
                <Alert
                    onClose={() => setSnackbarOpen(false)}
                    severity={submissionResult?.success ? 'success' : 'error'}
                    sx={{ width: { xs: 'calc(100vw - 24px)', sm: '100%' }, whiteSpace: 'pre-wrap' }}
                >
                    {submissionResult?.message}
                </Alert>
            </Snackbar>
        </Container>
    );
}

export default RespondWorkOrderPage;

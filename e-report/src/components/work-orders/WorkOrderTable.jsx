import React from 'react';
import {
    Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper,
    Typography, Box, Button, Chip, Checkbox, IconButton, Tooltip, CircularProgress, Stack, useMediaQuery
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import DownloadIcon from '@mui/icons-material/GetApp';
import VisibilityIcon from '@mui/icons-material/Visibility';
import ArrowForwardIosIcon from '@mui/icons-material/ArrowForwardIos';
import { formatDate } from '../../utils/formatDate';
import { buildWorkOrderAttachmentArchivePath } from "../../utils/workOrderAttachmentPaths.js";
import { getReportTypeLabel } from '../../constants/workOrderCatalog';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { downloadFileInChunks } from '../../services/fileDownloadService';
import {
    presentationChipSx,
    priorityPresentation,
    reportTypePresentation,
} from '../../constants/workOrderPresentation';
import { WorkOrderSlaIndicator, WorkOrderStatusBadge } from './WorkOrderStateBadges';
import { togglePageWorkOrderSelection, toggleWorkOrderSelection } from '../../utils/workOrderSelection';

const getAttachmentCount = (workOrder) => Number(workOrder.attachmentCount || workOrder.attachments?.length || 0);

const getHandlerName = (workOrder) => workOrder.handler?.name || workOrder.handler?.username || '-';

const ACTION_ICON_BUTTON_SX = {
    width: 40,
    height: 40,
    p: 1,
    flexShrink: 0,
};

function MobileInfoRow({ label, value }) {
    return (
        <Box sx={{ minWidth: 0 }}>
            <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                {label}：<Box component="span" sx={{ color: 'text.primary' }}>{value || '-'}</Box>
            </Typography>
        </Box>
    );
}

function WorkOrderMobileCard({ workOrder, onOpen, onDownloadZip, selectionEnabled, selected, onToggleSelection }) {
    const attachmentCount = getAttachmentCount(workOrder);
    const priorityToken = priorityPresentation(workOrder.priority);
    const typeToken = reportTypePresentation(workOrder.reportType);

    return (
        <Paper
            component="article"
            variant="outlined"
            onClick={() => onOpen(workOrder)}
            sx={{
                p: 2,
                borderRadius: '14px',
                cursor: 'pointer',
                transition: (muiTheme) => muiTheme.transitions.create(['border-color', 'box-shadow', 'transform'], {
                    duration: muiTheme.transitions.duration.shortest,
                }),
                '&:hover': { borderColor: 'primary.light', boxShadow: 1 },
                '&:active': { bgcolor: 'action.selected', transform: 'translateY(1px)' },
            }}
        >
            <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
                {selectionEnabled && (
                    <Checkbox
                        checked={selected}
                        onChange={onToggleSelection}
                        onClick={event => event.stopPropagation()}
                        size="small"
                        sx={{ mt: -0.75, ml: -0.75, flexShrink: 0 }}
                        slotProps={{ input: { 'aria-label': `选择工单 ${workOrder.workOrderNo || workOrder.id}` } }}
                    />
                )}
                <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography variant="caption" sx={{ color: typeToken.foreground, fontWeight: 500 }}>
                        {workOrder.workOrderNo || `#${workOrder.id}`}
                    </Typography>
                    <Typography variant="subtitle1" title={workOrder.location || '未填写地点'} sx={{ mt: 0.5, fontWeight: 600, lineHeight: 1.5, overflowWrap: 'anywhere', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                        {workOrder.location || '未填写地点'}
                    </Typography>
                </Box>
                <WorkOrderStatusBadge workOrder={workOrder} />
            </Box>

            <Stack direction="row" spacing={0.75} useFlexGap sx={{ mt: 1, flexWrap: 'wrap', alignItems: 'center' }}>
                <Chip
                    label={workOrder.reportTypeName || getReportTypeLabel(workOrder.reportType)}
                    size="small"
                    variant="outlined"
                    sx={presentationChipSx(typeToken)}
                />
                <Chip
                    label={workOrder.priorityName || priorityToken.label}
                    size="small"
                    variant="outlined"
                    sx={presentationChipSx(priorityToken)}
                />
                <WorkOrderSlaIndicator workOrder={workOrder} />
            </Stack>
            <Typography
 variant="body2"
 color="text.secondary"
 sx={{
 mt: 1,
 display: '-webkit-box',
 WebkitBoxOrient: 'vertical',
 WebkitLineClamp: 2,
 overflow: 'hidden',
 }}
 >
                {workOrder.reason || '无工单内容'}
            </Typography>

            <Box
                sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    columnGap: 2,
                    rowGap: 0.5,
                    mt: 1.5,
                }}
            >
                <MobileInfoRow label="联系人" value={workOrder.contactPerson} />
                <MobileInfoRow label="电话" value={workOrder.phone} />
                <MobileInfoRow label="上报时间" value={formatDate(workOrder.reportTime)} />
                <MobileInfoRow label={workOrder.handlerStateName || '处理人员'} value={getHandlerName(workOrder)} />
            </Box>

            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, mt: 1.5 }}>
                <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                    报单人：{workOrder.reportedBy?.name || workOrder.reportedBy?.username || '-'}
                </Typography>
                <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                    {attachmentCount > 0 && (
                        <Tooltip title={`下载 ${attachmentCount} 个附件 (ZIP)`}>
                            <IconButton
                                size="small"
                                onClick={(event) => onDownloadZip(workOrder.id, event)}
                                color="primary"
                                aria-label={`下载工单 ${workOrder.id} 的附件`}
                                sx={ACTION_ICON_BUTTON_SX}
                            >
                                <DownloadIcon sx={{ fontSize: 20 }} />
                                <Typography variant="caption" sx={{ ml: 0.25 }}>({attachmentCount})</Typography>
                            </IconButton>
                        </Tooltip>
                    )}
                    <Button
                        size="small"
                        variant="text"
                        endIcon={<ArrowForwardIosIcon sx={{ fontSize: '14px !important' }} />}
                        onClick={(event) => {
                            event.stopPropagation();
                            onOpen(workOrder);
                        }}
                        sx={{ minWidth: 0, height: 40, px: 0.75, fontWeight: 700, whiteSpace: 'nowrap' }}
                    >
                        查看详情
                    </Button>
                </Stack>
            </Box>
        </Paper>
    );
}

function WorkOrderTable({ workOrders, isLoading, onRowClick, selectedWorkOrderIds = new Set(), onSelectionChange }) {
    const theme = useTheme();
    const isCompact = useMediaQuery(theme.breakpoints.down('md'));
    const selectionEnabled = typeof onSelectionChange === 'function';
    const selectedIds = selectedWorkOrderIds instanceof Set
        ? selectedWorkOrderIds
        : new Set(selectedWorkOrderIds || []);
    const visibleIds = (workOrders || [])
        .map(workOrder => String(workOrder?.id || '').trim())
        .filter(Boolean);
    const allVisibleSelected = selectionEnabled
        && visibleIds.length > 0
        && visibleIds.every(workOrderId => selectedIds.has(workOrderId));
    const someVisibleSelected = selectionEnabled
        && visibleIds.some(workOrderId => selectedIds.has(workOrderId));

    const handleToggleSelection = (workOrderId, event) => {
        event?.stopPropagation();
        onSelectionChange?.(toggleWorkOrderSelection(selectedIds, workOrderId));
    };

    const handleTogglePageSelection = (event) => {
        event.stopPropagation();
        onSelectionChange?.(togglePageWorkOrderSelection(selectedIds, visibleIds));
    };

    const handleDownloadZip = async (workOrderId, event) => {
        event.stopPropagation();
        const downloadUrl = buildWorkOrderAttachmentArchivePath(workOrderId);
        try {
            await downloadFileInChunks(downloadUrl, `work_order_${workOrderId}_attachments.zip`);
        } catch (error) {
            console.error('Error downloading the ZIP file:', error);
            alert(`下载附件失败：${formatApiErrorMessage(error, '附件下载失败，请稍后重试。')}`);
        }
    };

    const handleRowClickInternal = (workOrder, event) => {
        if (event.target.closest('a, button, input, svg, path, [role="button"]')) return;
        if (onRowClick) onRowClick(workOrder);
    };

    const handleOpenWorkOrder = (workOrder) => {
        if (onRowClick) onRowClick(workOrder);
    };

    if (isLoading && (!workOrders || workOrders.length === 0)) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
                <CircularProgress /><Typography sx={{ ml: 2 }}>加载工单数据中...</Typography>
            </Box>
        );
    }

    if (!workOrders || workOrders.length === 0) {
        return (
            <Paper sx={{ p: { xs: 2, sm: 3 }, textAlign: 'center', mt: 2, borderRadius: 2 }}>
                <Typography color="text.secondary">没有找到符合条件的工单。</Typography>
            </Paper>
        );
    }

    if (isCompact) {
        return (
            <Box sx={{ display: 'grid', gap: 1.5 }}>
                {workOrders.map((workOrder) => (
                    <WorkOrderMobileCard
                        key={workOrder.id}
                        workOrder={workOrder}
                        onOpen={handleOpenWorkOrder}
                        onDownloadZip={handleDownloadZip}
                        selectionEnabled={selectionEnabled}
                        selected={selectedIds.has(String(workOrder.id))}
                        onToggleSelection={event => handleToggleSelection(workOrder.id, event)}
                    />
                ))}
            </Box>
        );
    }

    return (
        <TableContainer component={Paper} elevation={0} variant="outlined" sx={{ borderRadius: 2, maxHeight: 'max(280px, calc(100dvh - 420px))' }}>
            <Table sx={{ minWidth: 960, tableLayout: 'fixed' }} aria-label="工单列表" size="small" stickyHeader>
                <colgroup>
                    {selectionEnabled && <col style={{ width: 52 }} />}
                    <col style={{ width: 210 }} />
                    <col />
                    <col style={{ width: 180 }} />
                    <col style={{ width: 174 }} />
                    <col style={{ width: 100 }} />
                </colgroup>
                <TableHead>
                    <TableRow sx={{ '& th': { height: 48, bgcolor: 'background.default', color: 'text.secondary', fontWeight: 800 } }}>
                        {selectionEnabled && (
                            <TableCell padding="checkbox" sx={{ width: 52, p: 0, textAlign: 'center' }}>
                                <Checkbox
                                    checked={allVisibleSelected}
                                    indeterminate={someVisibleSelected && !allVisibleSelected}
                                    onChange={handleTogglePageSelection}
                                    slotProps={{ input: { 'aria-label': allVisibleSelected ? '取消选择当前页工单' : '选择当前页工单' } }}
                                />
                            </TableCell>
                        )}
                        <TableCell sx={{ width: 210 }}>工单 / 上报时间</TableCell>
                        <TableCell>位置与内容</TableCell>
                        <TableCell sx={{ width: 180 }}>联系与责任</TableCell>
                        <TableCell align="center" sx={{ width: 174 }}>流程与时限</TableCell>
                        <TableCell align="center" sx={{ width: 100 }}>操作</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {workOrders.map((workOrder) => (
                        <TableRow
                            key={workOrder.id}
                            hover
                            onClick={(event) => handleRowClickInternal(workOrder, event)}
                            sx={{
                                cursor: onRowClick ? 'pointer' : 'default',
                                transition: (muiTheme) => muiTheme.transitions.create('background-color', {
                                    duration: muiTheme.transitions.duration.shortest,
                                }),
                                '& td, & th': { verticalAlign: 'top', overflowWrap: 'anywhere' },
                            }}
                        >
                            {selectionEnabled && (
                                <TableCell padding="checkbox" onClick={event => event.stopPropagation()} sx={{ p: 0, textAlign: 'center' }}>
                                    <Checkbox
                                        checked={selectedIds.has(String(workOrder.id))}
                                        onChange={event => handleToggleSelection(workOrder.id, event)}
                                        slotProps={{ input: { 'aria-label': `选择工单 ${workOrder.workOrderNo || workOrder.id}` } }}
                                    />
                                </TableCell>
                            )}
                            <TableCell component="th" scope="row" sx={{ borderLeft: '4px solid', borderLeftColor: priorityPresentation(workOrder.priority).rail }}>
                                <Typography variant="body2" sx={{ fontWeight: 800, mb: 0.75, whiteSpace: 'nowrap' }}>{workOrder.workOrderNo || `#${workOrder.id}`}</Typography>
                                <Stack
 direction="row"
 spacing={0.5}
 useFlexGap
 sx={{ alignItems: 'center', flexWrap: 'wrap' }}
 >
                                    <Chip
                                        label={workOrder.reportTypeName || getReportTypeLabel(workOrder.reportType)}
                                        size="small"
                                        variant="outlined"
                                        sx={presentationChipSx(reportTypePresentation(workOrder.reportType))}
                                    />
                                    <Chip
                                        label={workOrder.priorityName || priorityPresentation(workOrder.priority).label}
                                        size="small"
                                        variant="outlined"
                                        sx={presentationChipSx(priorityPresentation(workOrder.priority))}
                                    />
                                </Stack>
                                {workOrder.boxQuantity ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75 }}>
                                        箱体 {workOrder.boxQuantity}
                                    </Typography>
                                ) : null}
                                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75 }}>{formatDate(workOrder.reportTime)}</Typography>
                            </TableCell>
                            <TableCell>
                                <Tooltip title={workOrder.location || ''}>
                                    <Typography variant="body2" sx={{ fontWeight: 700, overflowWrap: 'anywhere', display: '-webkit-box', WebkitBoxOrient: 'vertical', WebkitLineClamp: 2, overflow: 'hidden', mb: 0.5 }}>{workOrder.location || '-'}</Typography>
                                </Tooltip>
                                <Tooltip title={workOrder.reason} placement="top-start">
                                    <Typography variant="body2" sx={{
 display: '-webkit-box', WebkitBoxOrient: 'vertical',
 WebkitLineClamp: 2, overflow: 'hidden',
 textOverflow: 'ellipsis', maxHeight: '2.8em', lineHeight: '1.4em'
 }}>
                                        {workOrder.reason}
                                    </Typography>
                                </Tooltip>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{workOrder.contactPerson || '-'}</Typography>
                                <Typography variant="caption" color="text.secondary">{workOrder.phone || '-'}</Typography>
                                <Typography variant="body2">
                                    <Box component="span" sx={{ color: 'text.secondary' }}>报单：</Box>
                                    {workOrder.reportedBy?.name || workOrder.reportedBy?.username || '-'}
                                </Typography>
                                <Typography variant="body2" sx={{ mt: 0.5 }}>
                                    <Box component="span" sx={{ color: 'text.secondary' }}>{workOrder.handlerStateName || '处理'}：</Box>
                                    {getHandlerName(workOrder)}
                                </Typography>
                            </TableCell>
                            <TableCell align="center" sx={{ minWidth: 174 }}>
                                <Stack spacing={0.5} sx={{ minWidth: 146, alignItems: 'center' }}>
                                    <WorkOrderStatusBadge workOrder={workOrder} />
                                    <WorkOrderSlaIndicator workOrder={workOrder} />
                                </Stack>
                            </TableCell>
                            <TableCell align="center">
                                <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'center', alignItems: 'center', minHeight: 40 }}>
                                {getAttachmentCount(workOrder) > 0 && (
                                    <Tooltip title={`下载 ${getAttachmentCount(workOrder)} 个附件`}>
                                        <IconButton sx={ACTION_ICON_BUTTON_SX} onClick={(event) => handleDownloadZip(workOrder.id, event)} color="primary" aria-label={`下载工单 ${workOrder.id} 的附件`}>
                                            <DownloadIcon sx={{ fontSize: 20 }} />
                                        </IconButton>
                                    </Tooltip>
                                )}
                                <Tooltip title="查看详情">
                                    <IconButton
                                        sx={ACTION_ICON_BUTTON_SX}
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            if (onRowClick) onRowClick(workOrder);
                                        }}
                                        color="info"
                                        aria-label={`查看工单 ${workOrder.id} 的详情`}
                                    >
                                        <VisibilityIcon sx={{ fontSize: 20 }} />
                                    </IconButton>
                                </Tooltip>
                                </Stack>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>
    );
}

export default WorkOrderTable;

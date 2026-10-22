import PageHeading from '../../components/layout/PageHeading';
import ListResultsBar, { ListPagination } from '../../components/layout/ListResultsBar';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, Container, LinearProgress, Paper, Snackbar, Typography, useMediaQuery } from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';

import WorkOrderSearchFilters from '../../components/work-orders/WorkOrderSearchFilters';
import WorkOrderTable from '../../components/work-orders/WorkOrderTable';
import { WORK_ORDER_TYPES } from '../../constants/workOrderCatalog';
import {
    WORK_ORDER_MANAGEMENT_PERMISSIONS,
    WORK_ORDER_EXPORT_PERMISSIONS,
    hasAnyPermission,
} from '../../constants/permissions';
import useWorkOrders, { buildWorkOrderExportParams } from '../../hooks/useWorkOrders';
import { useAuth } from '../../hooks/useAuth';
import { exportWorkOrders, searchWorkOrders } from '../../services/workOrderApiService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { readWorkOrderSearchParams, writeWorkOrderSearchParams } from '../../utils/workOrderSearchParams';
import { selectedIdsForExport } from '../../utils/workOrderSelection';

function WorkOrderSearchPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const { currentUser } = useAuth();
    const compact = useMediaQuery('(max-width:599.95px)');
    const [searchParams, setSearchParams] = useSearchParams();
    const serializedSearchParams = searchParams.toString();
    const hasManagementScope = hasAnyPermission(currentUser, WORK_ORDER_MANAGEMENT_PERMISSIONS);
    const canExport = hasAnyPermission(currentUser, WORK_ORDER_EXPORT_PERMISSIONS);
    const exportControllerRef = useRef(null);
    const [exportProgress, setExportProgress] = useState(null);
    const [exportMessage, setExportMessage] = useState({ open: false, message: '', severity: 'success' });
    const [selectedWorkOrderIds, setSelectedWorkOrderIds] = useState(() => new Set());
    const isAdminRoute = location.pathname.startsWith('/admin/');
    const workOrderBasePath = isAdminRoute ? '/admin/work-orders' : '/work-orders';
    const typeOptions = useMemo(() => [
        { value: 'all', label: '全部类型' },
        ...WORK_ORDER_TYPES.map(item => ({ value: item.reportType, label: item.typeLabel })),
    ], []);
    const initialFilters = useMemo(() => {
        const currentSearchParams = new URLSearchParams(serializedSearchParams);
        const parsedFilters = readWorkOrderSearchParams(currentSearchParams);
        const allowedTypes = new Set(typeOptions.filter(option => option.value !== 'all').map(option => option.value));
        return {
            ...parsedFilters,
            reportTypes: parsedFilters.reportTypes.filter(reportType => allowedTypes.has(reportType)),
        };
    }, [serializedSearchParams, typeOptions]);
    const cacheNamespace = `visible:${currentUser?.tenantId || 'default'}:${currentUser?.username || 'anonymous'}`;
    const {
        workOrders,
        isLoading,
        error,
        pagination,
        currentFilters,
        handleSearch,
        handlePageChange,
    } = useWorkOrders(initialFilters, 10, searchWorkOrders, cacheNamespace);

    const handleSearchWithUrl = useCallback((filters) => {
        setSelectedWorkOrderIds(new Set());
        setSearchParams(writeWorkOrderSearchParams(filters), { replace: true });
        handleSearch(filters);
    }, [handleSearch, setSearchParams]);

    useEffect(() => {
        setSelectedWorkOrderIds(new Set());
    }, [serializedSearchParams]);

    const handleViewDetails = (workOrder) => {
        if (workOrder?.id) {
            navigate(`${workOrderBasePath}/${workOrder.id}`);
        }
    };

    const handleExport = async () => {
        const selectedIds = selectedIdsForExport(selectedWorkOrderIds);
        if (!canExport || selectedIds.length < 1 || exportControllerRef.current) return;
        const controller = new AbortController();
        exportControllerRef.current = controller;
        setExportProgress({ stage: 'preparing', percent: 0 });
        try {
            const result = await exportWorkOrders(buildWorkOrderExportParams(currentFilters, selectedIds), {
                signal: controller.signal,
                onProgress: setExportProgress,
            });
            setExportMessage({
                open: true,
                severity: 'success',
                message: `已导出 ${result.rowCount || selectedIds.length} 条工单。`,
            });
        } catch (requestError) {
            if (requestError?.name !== 'AbortError' && requestError?.code !== 'ERR_CANCELED') {
                setExportMessage({
                    open: true,
                    severity: 'error',
                    message: formatApiErrorMessage(requestError, '导出工单失败，请稍后重试。'),
                });
            }
        } finally {
            exportControllerRef.current = null;
            setExportProgress(null);
        }
    };

    const selectedCount = selectedWorkOrderIds.size;

    return (
        <Container maxWidth={isAdminRoute ? false : 'xl'} disableGutters sx={{ mb: 2 }}>
            <PageHeading title="工单查询" description={hasManagementScope ? '集中查询本人上报及职责范围内的工单' : '集中查看本人提交的工单与处理进度'} sx={{ mb: 1.5 }} />

            {exportProgress && (
                <Box sx={{ mb: 1.5 }}>
                    <LinearProgress
                        variant={Number.isFinite(exportProgress.percent) ? 'determinate' : 'indeterminate'}
                        value={Number.isFinite(exportProgress.percent) ? exportProgress.percent : undefined}
                    />
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                        {exportProgress.stage === 'preparing' ? '正在生成电子表格...' : exportProgress.stage === 'verifying' ? '正在校验文件...' : `正在下载${Number.isFinite(exportProgress.percent) ? ` ${exportProgress.percent}%` : '...'}`}
                    </Typography>
                </Box>
            )}

            <Paper elevation={0} variant="outlined" sx={{ p: { xs: 1.5, sm: 2 }, mb: 0.5, borderRadius: '14px' }}>
                <WorkOrderSearchFilters
                    onSearch={handleSearchWithUrl}
                    isLoading={isLoading}
                    initialFilters={currentFilters}
                    availableReportTypes={typeOptions}
                    actions={canExport && (
                        <Button
                            type="button"
                            variant="outlined"
                            startIcon={exportProgress ? <CircularProgress size={16} color="inherit" /> : <DownloadIcon />}
                            onClick={handleExport}
                            disabled={isLoading || exportProgress !== null || selectedCount < 1}
                            aria-label={`导出已选${selectedCount > 0 ? ` (${selectedCount})` : ''}`}
                            title="导出已选工单"
                            sx={{ whiteSpace: 'nowrap', minHeight: { xs: 44, md: 40 }, minWidth: { xs: 44, sm: 'auto' }, px: { xs: 1, sm: 2 }, '& .MuiButton-startIcon': { m: { xs: 0, sm: undefined }, mr: { sm: 1 } }, gridArea: 'export' }}
                        >
                            <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>导出已选{selectedCount > 0 ? ` (${selectedCount})` : ''}</Box>
                        </Button>
                    )}
                />
            </Paper>

            {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

            {isLoading && workOrders.length > 0 && (
                <LinearProgress aria-label="正在刷新工单列表" sx={{ mb: 1, borderRadius: 999 }} />
            )}

            <ListResultsBar
                summary={<>
                    <span>
                        共 {pagination.totalElements} 条{compact ? '' : '工单'}{selectedCount > 0 ? ` · 已选 ${selectedCount} 条` : ''}
                        {canExport && selectedCount === 0 && <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}> · 勾选工单后可批量导出</Box>}
                    </span>
                    {canExport && selectedCount > 0 && (
                        <Button
                            variant="text"
                            aria-label="清空选择"
                            onClick={() => setSelectedWorkOrderIds(new Set())}
                            disabled={isLoading || exportProgress !== null}
                            sx={{ whiteSpace: 'nowrap' }}
                        >
                            {compact ? '清空' : '清空选择'}
                        </Button>
                    )}
                </>}
            >
                {workOrders.length > 0 && pagination.totalPages > 1 && (
                    <ListPagination
                        count={pagination.totalPages}
                        page={pagination.currentPage + 1}
                        onChange={(event, value) => handlePageChange(value - 1)}
                        showFirstButton
                        showLastButton
                        disabled={isLoading}
                    />
                )}
            </ListResultsBar>

            <WorkOrderTable
                workOrders={workOrders}
                isLoading={isLoading}
                onRowClick={handleViewDetails}
                selectedWorkOrderIds={selectedWorkOrderIds}
                onSelectionChange={canExport ? setSelectedWorkOrderIds : undefined}
            />


            <Snackbar
                open={exportMessage.open}
                autoHideDuration={5000}
                onClose={() => setExportMessage(previous => ({ ...previous, open: false }))}
            >
                <Alert severity={exportMessage.severity} variant="filled" onClose={() => setExportMessage(previous => ({ ...previous, open: false }))}>
                    {exportMessage.message}
                </Alert>
            </Snackbar>
        </Container>
    );
}

export default WorkOrderSearchPage;

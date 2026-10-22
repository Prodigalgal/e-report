import PageTitle from '../../components/layout/PageTitle';
// src/pages/CreateWorkOrderPage/CreateWorkOrderPage.jsx
import React, { useState } from 'react';
import { Container, Typography, Alert, Snackbar } from '@mui/material';
import WorkOrderForm from '../../components/WorkOrderForm/WorkOrderForm';
import { submitWorkOrder } from '../../services/workOrderApiService';
import { WORK_ORDER_TYPE_CODES, REPORT_PAGE_CONFIGS } from '../../constants/workOrderCatalog';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';

function CreateWorkOrderPage({ reportType = WORK_ORDER_TYPE_CODES.NETWORK_FAULT }) {
    const reportConfig = REPORT_PAGE_CONFIGS[reportType] || REPORT_PAGE_CONFIGS[WORK_ORDER_TYPE_CODES.NETWORK_FAULT];
    const [isLoading, setIsLoading] = useState(false);
    const [submissionResult, setSubmissionResult] = useState(null); // { success: boolean, message?: string, data?: any, error?: any }
    const [snackbarOpen, setSnackbarOpen] = useState(false);
    const [submissionStage, setSubmissionStage] = useState('');

    const handleReportSubmit = async (formDataFromForm, filesFromForm) => {
        setIsLoading(true);
        setSubmissionStage('正在准备现场材料');
        setSubmissionResult(null);

        const workOrderData = {
            reportType,
            priority: formDataFromForm.priority,
            location: formDataFromForm.location,
            gpsCoordinates: formDataFromForm.gpsCoordinates,
            reason: formDataFromForm.reason,
            phone: formDataFromForm.phone,
            contactPerson: formDataFromForm.contactPerson,
        };
        if (formDataFromForm.boxQuantity) {
            workOrderData.boxQuantity = Number(formDataFromForm.boxQuantity);
        }

        try {
            const response = await submitWorkOrder(workOrderData, filesFromForm || [], {
                onProgress: (progress) => setSubmissionStage(
                    `正在上传现场材料 ${progress.fileIndex + 1}/${progress.fileCount}`
                ),
            });
            setSubmissionResult({ success: true, message: reportConfig.successMessage || '故障已成功上报！', data: response.data });
            setSnackbarOpen(true);
            // WorkOrderForm will reset itself via submissionResult prop
        } catch (error) {
            console.error('工单报单错误:', error.response?.data || error.message);
            const errorMessage = formatApiErrorMessage(error, '上报失败，请稍后重试。');
            setSubmissionResult({ success: false, message: errorMessage, error: error });
            setSnackbarOpen(true);
        } finally {
            setIsLoading(false);
            setSubmissionStage('');
        }
    };

    const handleSnackbarClose = (event, reason) => {
        if (reason === 'clickaway') {
            return;
        }
        setSnackbarOpen(false);
    };

    return (
        <Container maxWidth="md" disableGutters>
            <PageTitle
                sx={{ mb: 2 }}
            >
                {reportConfig.pageTitle || '故障报单'}
            </PageTitle>
            <WorkOrderForm
                onSubmit={handleReportSubmit}
                isLoading={isLoading}
                submissionStage={submissionStage}
                submissionResult={submissionResult}
                reportType={reportType}
                config={reportConfig}
            />
            <Snackbar
                open={snackbarOpen}
                autoHideDuration={6000}
                onClose={handleSnackbarClose}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
            >
                <Alert onClose={handleSnackbarClose} severity={submissionResult?.success ? "success" : "error"} sx={{ width: { xs: 'calc(100vw - 24px)', sm: '100%' } }}>
                    {submissionResult?.message}
                </Alert>
            </Snackbar>
        </Container>
    );
}

export default CreateWorkOrderPage;

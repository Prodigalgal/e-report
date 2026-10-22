// src/components/WorkOrderForm/WorkOrderForm.jsx
import React, { useState, useEffect } from 'react';
import { Box, Button, Paper, Typography, CircularProgress, ToggleButton, ToggleButtonGroup } from '@mui/material';
import FormField from '../FormField/FormField';
import LocationInput from '../LocationInput/LocationInput';
import FileUploadWithPreview from '../FileUploadWithPreview/FileUploadWithPreview';
import { WORK_ORDER_TYPE_CODES, REPORT_PAGE_CONFIGS } from '../../constants/workOrderCatalog';
import { fetchReverseGeocode } from '../../services/locationApiService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { WORK_ORDER_PRIORITIES } from '../../constants/workOrderSla';
import { priorityPresentation } from '../../constants/workOrderPresentation';
import { useAuth } from '../../hooks/useAuth';
import {
    MAX_ATTACHMENT_FILES,
    MAX_ATTACHMENT_FILE_SIZE_MB,
    MAX_ATTACHMENT_TOTAL_MB,
} from '../../constants/attachmentLimits';

const createInitialFormState = (reportType) => ({
    reportType,
    priority: 'ROUTINE',
    location: '',
    gpsCoordinates: '',
    reason: '',
    phone: '',
    contactPerson: '',
    boxQuantity: reportType === WORK_ORDER_TYPE_CODES.BOX_DOOR_OPEN ? '1' : '',
});

function WorkOrderForm({
                             onSubmit,
                             isLoading,
                             submissionStage,
                             submissionResult,
                             reportType = WORK_ORDER_TYPE_CODES.NETWORK_FAULT,
                             config = REPORT_PAGE_CONFIGS[WORK_ORDER_TYPE_CODES.NETWORK_FAULT],
                         }) {
    const [formData, setFormData] = useState(() => createInitialFormState(reportType));
    const [files, setFiles] = useState([]); // Stores File objects for submission
    const [errors, setErrors] = useState({});
    const [fileUploadError, setFileUploadError] = useState(null); // Specific error from FileUploadWithPreview
    const [areaLookupState, setAreaLookupState] = useState({ loading: false, message: '' });
    const { currentUser } = useAuth();
    const [contactPersonCustomized, setContactPersonCustomized] = useState(false);
    const [phoneCustomized, setPhoneCustomized] = useState(false);

    useEffect(() => {
        if (submissionResult && submissionResult.success) {
            setFormData(createInitialFormState(reportType));
            setContactPersonCustomized(false);
            setPhoneCustomized(false);
            setFiles([]);
            setErrors({});
            setFileUploadError(null);
            setAreaLookupState({ loading: false, message: '' });
            // The key prop on FileUploadWithPreview will handle its visual reset
        } else if (submissionResult && submissionResult.error) {
            // If parent signals a submission error, display it (though alert is already shown)
            // This could be used for inline error display if not using alerts
            // setErrors(prev => ({ ...prev, formSubmission: submissionResult.error }));
        }
    }, [submissionResult, reportType]);

    useEffect(() => {
        setFormData(createInitialFormState(reportType));
        setContactPersonCustomized(false);
        setPhoneCustomized(false);
        setFiles([]);
        setErrors({});
        setFileUploadError(null);
        setAreaLookupState({ loading: false, message: '' });
    }, [reportType]);

    useEffect(() => {
        if (!contactPersonCustomized && !formData.contactPerson.trim() && currentUser?.name?.trim()) {
            setFormData(prev => ({ ...prev, contactPerson: currentUser.name.trim() }));
        }
        if (!phoneCustomized && !formData.phone.trim() && currentUser?.phone?.trim()) {
            setFormData(prev => ({ ...prev, phone: currentUser.phone.trim() }));
        }
    }, [contactPersonCustomized, currentUser?.name, currentUser?.phone, formData.contactPerson, formData.phone, phoneCustomized]);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        if (name === 'contactPerson') setContactPersonCustomized(true);
        if (name === 'phone') setPhoneCustomized(true);
        if (errors[name]) {
            setErrors(prev => ({ ...prev, [name]: null }));
        }
    };

    const handleLocationChange = async (gpsCoords, gpsErrorMsg) => {
        setFormData(prev => ({ ...prev, gpsCoordinates: gpsCoords }));
        if (gpsErrorMsg) {
            setErrors(prev => ({ ...prev, gpsCoordinates: gpsErrorMsg }));
            setAreaLookupState({ loading: false, message: '' });
        } else if (errors.gpsCoordinates) {
            setErrors(prev => ({ ...prev, gpsCoordinates: null }));
        }
        if (gpsCoords && !gpsErrorMsg) {
            return resolveAddress(gpsCoords);
        }
        return { reverseGeocodeSucceeded: false };
    };

    const handlePriorityChange = (_event, priority) => {
        if (priority) {
            setFormData(prev => ({ ...prev, priority }));
        }
    };

    const handleManualGpsChange = (value) => {
        setFormData(prev => ({ ...prev, gpsCoordinates: value }));
        if (errors.gpsCoordinates) {
            setErrors(prev => ({ ...prev, gpsCoordinates: null }));
        }
        setAreaLookupState({ loading: false, message: '' });
    };

    const resolveAddress = async (gpsCoords) => {
        setAreaLookupState({ loading: true, message: '' });
        try {
            const response = await fetchReverseGeocode(gpsCoords);
            const result = response.data || {};
            setFormData(prev => ({
                ...prev,
                gpsCoordinates: result.gpsCoordinates || prev.gpsCoordinates,
                location: result.formattedAddress || prev.location,
            }));
            setAreaLookupState({
                loading: false,
                message: result.formattedAddress
                    ? '定位和地址解析完成，请核对工单地点。'
                    : '定位坐标已获取，但没有解析到地址，请手动填写工单地点。',
            });
            return { reverseGeocodeSucceeded: Boolean(result.formattedAddress) };
        } catch (error) {
            setAreaLookupState({
                loading: false,
                message: formatApiErrorMessage(error, '定位坐标已保留，地址解析失败，请手动填写工单地点。'),
            });
            return { reverseGeocodeSucceeded: false };
        }
    };

    const handleFilesUpdate = (updatedFiles, uploadError) => {
        setFiles(updatedFiles);
        setFileUploadError(uploadError); // Store error from file uploader
        if (errors.attachments) { // Clear general attachment error if any
            setErrors(prev => ({...prev, attachments: null}));
        }
    };

    const validateForm = () => {
        const newErrors = {};
        if (!formData.priority) newErrors.priority = '工单优先级不能为空';
        if (!formData.location.trim()) newErrors.location = `${config.locationLabel || '故障地点'}不能为空`;
        if (!isValidGpsCoordinates(formData.gpsCoordinates)) {
            newErrors.gpsCoordinates = '请定位或按“经度,纬度”填写有效定位坐标';
        }
        if (!formData.reason.trim()) newErrors.reason = `${config.reasonLabel || '故障原因'}不能为空`;
        if (!formData.contactPerson.trim()) newErrors.contactPerson = '联系人不能为空';
        if (!formData.phone.trim()) {
            newErrors.phone = '联系电话不能为空';
        } else if (!/^\d{7,15}$/.test(formData.phone.replace(/\s+/g, ''))) {
            newErrors.phone = '联系电话格式不正确';
        }
        if (config.quantityRequired) {
            const quantity = Number(formData.boxQuantity);
            if (!Number.isInteger(quantity) || quantity < 1) {
                newErrors.boxQuantity = `${config.quantityLabel || '箱体数量'}必须为大于0的整数`;
            }
        }

        if (fileUploadError) { // Prioritize error from FileUploadWithPreview
            newErrors.attachments = fileUploadError;
        } else if (files.length === 0) {
            newErrors.attachments = '请至少上传一份现场附件';
        }

        setErrors(newErrors);
        return Object.values(newErrors).every(error => !error);
    };

    const handleSubmit = (event) => {
        event.preventDefault();
        if (errors.formSubmission) { // Clear previous global submission error
            setErrors(prev => ({...prev, formSubmission: null}));
        }
        if (!validateForm()) {
            // Alert is now handled by the parent CreateWorkOrderPage
            // But we can set a general form error if needed for inline display
            // setErrors(prev => ({ ...prev, formSubmission: '请检查表单中的红色标记的错误项！' }));
            return;
        }
        onSubmit(formData, files);
    };

    return (
        <Paper elevation={2} sx={{ p: { xs: 2, md: 3 }, borderRadius: 1 }}> {/* Use Paper for card-like appearance */}
            <Typography
                variant="h5"
                component="h2"
                gutterBottom
                align="center"
                sx={{ fontSize: { xs: '1.25rem', sm: '1.5rem' } }}
            >
                {config.formTitle || '填写故障详情'}
            </Typography>
            {/* errors.formSubmission && <Alert severity="error" sx={{ mb: 2 }}>{errors.formSubmission}</Alert> */}

            <Box component="form" onSubmit={handleSubmit} noValidate sx={{ mt: 1 }}>
                <Box sx={{ mb: 2 }}>
                    <Typography variant="body2" color={errors.priority ? 'error' : 'text.primary'} sx={{ mb: 0.75, fontWeight: 700 }}>工单优先级 *</Typography>
                    <ToggleButtonGroup
                        exclusive
                        fullWidth
                        size="small"
                        value={formData.priority}
                        onChange={handlePriorityChange}
                        aria-label="工单优先级"
                    >
                        {WORK_ORDER_PRIORITIES.map(item => {
                            const tone = priorityPresentation(item.value);
                            return (
                                <ToggleButton
                                    key={item.value}
                                    value={item.value}
                                    sx={{
                                        color: tone.foreground,
                                        borderColor: `${tone.border} !important`,
                                        '&.Mui-selected': {
                                            color: tone.foreground,
                                            bgcolor: tone.surface,
                                        },
                                    }}
                                >
                                    {item.label}
                                </ToggleButton>
                            );
                        })}
                    </ToggleButtonGroup>
                    {errors.priority && <Typography variant="caption" color="error">{errors.priority}</Typography>}
                </Box>
                <FormField
                    id="location"
                    name="location"
                    label={config.locationLabel || '故障地点'}
                    value={formData.location}
                    onChange={handleChange}
                    required
                    placeholder={config.locationPlaceholder || '例如：一号楼大厅'}
                    errorMessage={errors.location}
                    autoComplete="off"
                />

                <LocationInput
                    initialGpsCoordinates={formData.gpsCoordinates}
                    onLocationChange={handleLocationChange}
                    onManualGpsChange={handleManualGpsChange}
                    disabled={isLoading}
                    error={errors.gpsCoordinates}
                    label="定位坐标（经度,纬度） *"
                    buttonLabel="定位并填写地址"
                />
                {(areaLookupState.loading || areaLookupState.message) && (
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, mt: -0.5, mb: 1 }}>
                        {areaLookupState.loading && <CircularProgress size={16} />}
                        <Typography variant="caption" color={areaLookupState.loading ? 'text.secondary' : 'info.main'}>
                            {areaLookupState.loading ? '正在获取定位并解析详细地址...' : areaLookupState.message}
                        </Typography>
                    </Box>
                )}

                <FormField
                    id="reason"
                    name="reason"
                    label={config.reasonLabel || '故障原因'}
                    multiline
                    rows={4}
                    value={formData.reason}
                    onChange={handleChange}
                    required
                    errorMessage={errors.reason}
                />
                {config.quantityRequired && (
                    <FormField
                        id="boxQuantity"
                        name="boxQuantity"
                        label={config.quantityLabel || '箱体数量'}
                        type="number"
                        value={formData.boxQuantity}
                        onChange={handleChange}
                        required
                        errorMessage={errors.boxQuantity}
                    />
                )}
                <FormField
                    id="phone"
                    name="phone"
                    label="联系电话"
                    type="tel"
                    value={formData.phone}
                    onChange={handleChange}
                    required
                    errorMessage={errors.phone}
                    autoComplete="tel"
                    placeholder={currentUser?.phone ? `默认：${currentUser.phone}` : undefined}
                />
                <FormField
                    id="contactPerson"
                    name="contactPerson"
                    label="联系人"
                    value={formData.contactPerson}
                    onChange={handleChange}
                    required
                    errorMessage={errors.contactPerson}
                    autoComplete="name"
                    placeholder={currentUser?.name ? `默认：${currentUser.name}` : undefined}
                />

                {/* FileUploadWithPreview is now wrapped by FormField for label and error display */}
                <FormField
                    id="attachments-field" // ID for the FormField itself
                    label={config.attachmentLabel || '现场附件（图片或视频）'}
                    type='file'
                    required
                    errorMessage={errors.attachments || fileUploadError} // Display combined errors
                >
                    <FileUploadWithPreview
                        id="attachments-input" // Unique ID for the actual input inside
                        key={submissionResult && submissionResult.success ? `fu-${Date.now()}` : 'file-uploader'}
                        onFilesChange={handleFilesUpdate}
                        maxFiles={MAX_ATTACHMENT_FILES}
                        maxFileSizeMB={MAX_ATTACHMENT_FILE_SIZE_MB}
                        maxTotalSizeMB={MAX_ATTACHMENT_TOTAL_MB}
                        accept = "image/*,video/*"
                    />
                </FormField>

                {isLoading && submissionStage && (
                    <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center' }}>
                        {submissionStage}
                    </Typography>
                )}
                <Button
                    type="submit"
                    fullWidth
                    variant="contained"
                    color="primary"
                    disabled={isLoading}
                    sx={{ mt: 3, mb: { xs: 0.5, sm: 2 }, py: 1.5, fontSize: { xs: '1rem', sm: '1.1rem' }, minHeight: 50 }}
                    startIcon={isLoading && <CircularProgress size={24} color="inherit" />}
                >
                    {isLoading ? '提交中...' : (config.submitButtonText || '提交报单')}
                </Button>
            </Box>
        </Paper>
    );
}

export default WorkOrderForm;

function isValidGpsCoordinates(value) {
    if (!value?.trim()) return false;
    const parts = value.split(',').map(part => Number(part.trim()));
    return parts.length === 2
        && parts.every(Number.isFinite)
        && parts[0] >= -180
        && parts[0] <= 180
        && parts[1] >= -90
        && parts[1] <= 90;
}

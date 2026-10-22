// src/components/LocationInput/LocationInput.jsx
import React, { useState } from 'react';
import { TextField, Button, Box, CircularProgress, FormControl, FormLabel, FormHelperText } from '@mui/material';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import { captureTelemetryError, startTelemetryOperation } from '../../observability/telemetry.js';

function LocationInput({
                           initialGpsCoordinates = '',
                           onLocationChange, // (gpsCoordsString, errorMsgOrNull)
                           onManualGpsChange,
                           disabled = false,
                           error = null, // Error from parent form validation
                           label = "定位坐标（经度,纬度）*",
                           buttonLabel = "定位并填写地址"
                       }) {
    const [isGettingLocation, setIsGettingLocation] = useState(false);
    const [internalLocationError, setInternalLocationError] = useState('');

    const handleGetLocation = () => {
        const operation = startTelemetryOperation({
            category: 'LOCATION',
            eventName: 'REPORT_LOCATION_COLLECTION',
            operationName: '获取定位并解析地址',
            forceCapture: true,
        });
        operation.stage('LOCATION_BUTTON_PRESSED', {
            geolocationSupported: Boolean(navigator.geolocation),
            secureContext: window.isSecureContext,
            visibilityState: document.visibilityState,
        });
        if (!navigator.geolocation) {
            operation.failure(new Error('您的浏览器不支持自动定位。'), { failureStage: 'CAPABILITY_CHECK' });
            setInternalLocationError('您的浏览器不支持自动定位。');
            if (onLocationChange) onLocationChange('', '您的浏览器不支持自动定位。');
            return;
        }
        setIsGettingLocation(true);
        setInternalLocationError('');
        if (navigator.permissions?.query) {
            void navigator.permissions.query({ name: 'geolocation' }).then(permission => {
                operation.stage('PERMISSION_STATE_READ', { permissionState: permission.state });
            }).catch(() => {
                operation.stage('PERMISSION_STATE_UNAVAILABLE');
            });
        }

        navigator.geolocation.getCurrentPosition(
            async (position) => {
                try {
                    const { latitude, longitude, accuracy, altitudeAccuracy } = position.coords;
                    operation.stage('LOCATION_FIX_RECEIVED', {
                        accuracy,
                        altitudeAccuracy,
                        timestampAgeMs: Math.max(0, Date.now() - position.timestamp),
                    });
                    const coordsString = `${longitude.toFixed(6)}, ${latitude.toFixed(6)}`;
                    const result = onLocationChange ? await onLocationChange(coordsString, null) : null;
                    operation.success({
                        accuracy,
                        reverseGeocodeSucceeded: result?.reverseGeocodeSucceeded === true,
                    });
                } catch (callbackError) {
                    operation.failure(callbackError, { failureStage: 'ADDRESS_RESOLUTION' });
                    captureTelemetryError(callbackError, {
                        category: 'LOCATION',
                        eventName: 'LOCATION_ADDRESS_RESOLUTION_FAILURE',
                        operationName: '获取定位并解析地址',
                        traceId: operation.traceId,
                    });
                    setInternalLocationError('定位坐标已获取，但地址解析失败，请手动填写工单地点。');
                } finally {
                    setIsGettingLocation(false);
                }
            },
            (err) => {
                let msg = '';
                switch (err.code) {
                    case err.PERMISSION_DENIED: msg = "用户拒绝了位置请求。"; break;
                    case err.POSITION_UNAVAILABLE: msg = "位置信息不可用。"; break;
                    case err.TIMEOUT: msg = "获取位置信息超时。"; break;
                    default: msg = "获取位置信息时发生未知错误。"; break;
                }
                operation.failure(new Error(msg), {
                    failureStage: 'BROWSER_GEOLOCATION',
                    geolocationErrorCode: err.code,
                    permissionDenied: err.code === err.PERMISSION_DENIED,
                    timeout: err.code === err.TIMEOUT,
                });
                captureTelemetryError(new Error(msg), {
                    category: 'LOCATION',
                    eventName: 'BROWSER_GEOLOCATION_FAILURE',
                    operationName: '获取定位并解析地址',
                    traceId: operation.traceId,
                    attributes: { geolocationErrorCode: err.code },
                });
                setInternalLocationError(msg);
                if (onLocationChange) {
                    onLocationChange('', msg); // Pass error message
                }
                setIsGettingLocation(false);
            },
            { enableHighAccuracy: true, timeout: 20000, maximumAge: 0 }
        );
    };

    const handleInputChange = (e) => {
        if (onManualGpsChange) {
            onManualGpsChange(e.target.value);
        }
        if (internalLocationError) setInternalLocationError(''); // Clear internal error on manual input
    };

    const displayError = error || internalLocationError;

    return (
        <FormControl fullWidth margin="normal" error={!!displayError}>
            {label && <FormLabel component="legend" sx={{ mb: 1 }}>{label}</FormLabel>}
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'stretch',
                    gap: 1,
                    flexDirection: { xs: 'column', sm: 'row' },
                }}
            >
                <TextField
                    fullWidth
                    variant="outlined"
                    id="gpsCoordinates"
                    name="gpsCoordinates"
                    value={initialGpsCoordinates}
                    onChange={handleInputChange}
                    placeholder="经度, 纬度"
                    disabled={disabled || isGettingLocation}
                    error={!!displayError} // Show error state on TextField too
                    // helperText={displayError} // HelperText on TextField might be redundant if using FormHelperText below
                    sx={{ flexGrow: 1 }}
                />
                <Button
                    variant="outlined"
                    onClick={handleGetLocation}
                    disabled={disabled || isGettingLocation}
                    startIcon={isGettingLocation ? <CircularProgress size={20} color="inherit" /> : <MyLocationIcon />}
                    sx={{
                        whiteSpace: 'nowrap',
                        height: 56,
                        width: { xs: '100%', sm: 'auto' },
                        flexShrink: 0,
                    }}
                >
                    {isGettingLocation ? '正在定位并解析' : buttonLabel}
                </Button>
            </Box>
            {displayError && <FormHelperText>{displayError}</FormHelperText>}
        </FormControl>
    );
}

export default LocationInput;

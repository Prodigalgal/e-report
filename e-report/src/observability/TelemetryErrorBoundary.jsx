import React from 'react';
import { Alert, Box, Button, Typography } from '@mui/material';

import { captureTelemetryError } from './telemetry.js';

export default class TelemetryErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false };
    }

    static getDerivedStateFromError() {
        return { hasError: true };
    }

    componentDidCatch(error, errorInfo) {
        captureTelemetryError(error, {
            category: 'UI',
            eventName: 'REACT_RENDER_ERROR',
            fatal: true,
            attributes: { componentStack: errorInfo.componentStack },
        });
    }

    render() {
        if (!this.state.hasError) return this.props.children;
        return (
            <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
                <Box sx={{ width: '100%', maxWidth: 520 }}>
                    <Alert severity="error" sx={{ mb: 2 }}>页面运行异常，诊断信息已保存并将在网络可用时上传。</Alert>
                    <Typography variant="h6" component="h1" sx={{ fontWeight: 800, mb: 1 }}>当前页面无法继续显示</Typography>
                    <Typography color="text.secondary" sx={{ mb: 2 }}>请重新加载页面；如问题仍然存在，请向管理员提供发生时间。</Typography>
                    <Button variant="contained" onClick={() => window.location.reload()}>重新加载</Button>
                </Box>
            </Box>
        );
    }
}

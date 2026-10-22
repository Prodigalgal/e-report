import React, { useEffect, useState } from 'react';
import RefreshIcon from '@mui/icons-material/Refresh';
import { Box, Button, CircularProgress, Stack, Typography } from '@mui/material';

import apiClient from '../../services/apiClient';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';

function AuthenticatedAttachmentMedia({ url, mediaType = 'image', alt = '', onClick, onLoad, sx = {} }) {
    const [objectUrl, setObjectUrl] = useState('');
    const [error, setError] = useState('');
    const [reloadVersion, setReloadVersion] = useState(0);

    useEffect(() => {
        const controller = new AbortController();
        let createdObjectUrl = '';

        setObjectUrl('');
        setError('');
        apiClient.get(url, {
            responseType: 'blob',
            signal: controller.signal,
            timeout: 120000,
        }).then((response) => {
            if (controller.signal.aborted) return;
            createdObjectUrl = window.URL.createObjectURL(response.data);
            setObjectUrl(createdObjectUrl);
        }).catch((requestError) => {
            if (controller.signal.aborted) return;
            setError(formatApiErrorMessage(requestError, '附件预览加载失败，请重试。'));
        });

        return () => {
            controller.abort();
            if (createdObjectUrl) {
                window.URL.revokeObjectURL(createdObjectUrl);
            }
        };
    }, [reloadVersion, url]);

    if (error) {
        return (
            <Stack
                spacing={1}
                sx={{ width: '100%', height: '100%', p: 1.5, alignItems: 'center', justifyContent: 'center', textAlign: 'center', ...sx }}
            >
                <Typography variant="caption" color="error">{error}</Typography>
                <Button size="small" startIcon={<RefreshIcon />} onClick={() => setReloadVersion((current) => current + 1)}>
                    重新加载
                </Button>
            </Stack>
        );
    }

    if (!objectUrl) {
        return (
            <Box
                aria-label="正在加载附件预览"
                sx={{ width: '100%', height: '100%', display: 'grid', placeItems: 'center', ...sx }}
            >
                <CircularProgress size={28} />
            </Box>
        );
    }

    if (mediaType === 'video') {
        return (
            <Box
                component="video"
                src={objectUrl}
                aria-label={alt}
                controls
                preload="metadata"
                onError={() => setError('视频无法播放，请重新加载或下载后查看。')}
                sx={{ width: '100%', height: '100%', objectFit: 'contain', display: 'block', bgcolor: 'black', ...sx }}
            />
        );
    }

    return (
        <Box
            component="img"
            src={objectUrl}
            alt={alt}
            draggable={false}
            onClick={onClick}
            onLoad={onLoad}
            onError={() => setError('图片无法解析，请重新加载或下载后查看。')}
            sx={{ width: '100%', height: '100%', objectFit: 'contain', display: 'block', ...sx }}
        />
    );
}

export default AuthenticatedAttachmentMedia;

import React, { useEffect, useRef, useState } from 'react';
import { Alert, Box, CircularProgress, Stack, Typography } from '@mui/material';

import { loadAmap, AmapConfigurationError } from './amapLoader';

/**
 * 高德地图容器。
 *
 * 只负责地图生命周期与标记渲染，业务图层由调用方通过 markers 传入，
 * 便于在线用户地图与坐标提取器复用同一套渲染逻辑。
 *
 * @param {object} props
 * @param {{jsApiKey?: string, securityJsCode?: string, configured?: boolean}} props.mapConfig 后端下发的地图配置
 * @param {Array} props.markers 标记列表
 * @param {[number, number]|null} props.center 初始中心 [经度, 纬度]（GCJ-02）
 * @param {number} props.zoom 初始缩放层级
 * @param {boolean} props.fitToMarkers 是否自动缩放到全部标记
 * @param {function} props.onMapReady 地图实例就绪回调
 * @param {function} props.onMarkerClick 标记点击回调
 * @param {string} props.height 容器高度
 */
function AmapMapCanvas({
    mapConfig,
    markers = [],
    center = null,
    zoom = 11,
    fitToMarkers = false,
    onMapReady,
    onMarkerClick,
    height = 520,
}) {
    const containerRef = useRef(null);
    const mapRef = useRef(null);
    const overlayRef = useRef([]);
    const [status, setStatus] = useState('loading');
    const [errorMessage, setErrorMessage] = useState('');

    // 地图实例初始化。仅在配置变化时重建，避免每次标记更新都销毁地图导致视野跳动。
    useEffect(() => {
        let cancelled = false;
        setStatus('loading');
        setErrorMessage('');

        loadAmap(mapConfig)
            .then((AMap) => {
                if (cancelled || !containerRef.current) return;
                const options = {
                    zoom,
                    resizeEnable: true,
                    viewMode: '2D',
                };
                if (center) {
                    options.center = center;
                }
                const map = new AMap.Map(containerRef.current, options);
                mapRef.current = map;
                setStatus('ready');
                onMapReady?.(map, AMap);
            })
            .catch((error) => {
                if (cancelled) return;
                setStatus('failed');
                setErrorMessage(error instanceof AmapConfigurationError
                    ? error.message
                    : '地图加载失败，请稍后重试。');
            });

        return () => {
            cancelled = true;
            if (mapRef.current) {
                mapRef.current.destroy?.();
                mapRef.current = null;
            }
            overlayRef.current = [];
        };
        // center 只在首次初始化时生效；后续视野变化由 fitToMarkers 控制。
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mapConfig?.jsApiKey, mapConfig?.securityJsCode]);

    // 标记渲染。
    useEffect(() => {
        if (status !== 'ready' || !mapRef.current) return undefined;
        const map = mapRef.current;

        map.remove(overlayRef.current);
        overlayRef.current = [];

        const validMarkers = markers.filter(marker => (
            Number.isFinite(marker.longitude) && Number.isFinite(marker.latitude)
        ));
        if (validMarkers.length === 0) {
            return undefined;
        }

        const overlays = validMarkers.map((marker) => {
            const content = document.createElement('div');
            content.style.cssText = [
                'width:16px',
                'height:16px',
                'border-radius:50%',
                `background:${marker.color || '#1976d2'}`,
                'border:2px solid #ffffff',
                'box-shadow:0 0 0 2px rgba(0,0,0,0.16)',
                'cursor:pointer',
                marker.stale ? 'opacity:0.55' : '',
            ].filter(Boolean).join(';');
            if (marker.title) {
                content.title = marker.title;
            }
            const amapMarker = new window.AMap.Marker({
                position: [marker.longitude, marker.latitude],
                content,
                offset: new window.AMap.Pixel(-8, -8),
                zIndex: marker.zIndex || 100,
            });
            if (onMarkerClick) {
                amapMarker.on('click', () => onMarkerClick(marker));
            }
            return amapMarker;
        });

        map.add(overlays);
        overlayRef.current = overlays;

        if (fitToMarkers && validMarkers.length > 0) {
            map.setFitView(overlays, false, [60, 60, 60, 60], 16);
        }
        return undefined;
    }, [markers, status, fitToMarkers, onMarkerClick]);

    return (
        <Box sx={{ position: 'relative', height, width: '100%', borderRadius: 1, overflow: 'hidden' }}>
            <Box ref={containerRef} sx={{ height, width: '100%' }} />
            {status === 'loading' && (
                <Stack
                    spacing={1}
                    alignItems="center"
                    justifyContent="center"
                    sx={{ position: 'absolute', inset: 0, bgcolor: 'background.paper' }}
                >
                    <CircularProgress size={28} />
                    <Typography variant="body2" color="text.secondary">正在加载地图</Typography>
                </Stack>
            )}
            {status === 'failed' && (
                <Box sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', p: 2, bgcolor: 'background.paper' }}>
                    <Alert severity="warning" sx={{ width: '100%' }}>
                        {errorMessage}
                    </Alert>
                </Box>
            )}
        </Box>
    );
}

export default AmapMapCanvas;

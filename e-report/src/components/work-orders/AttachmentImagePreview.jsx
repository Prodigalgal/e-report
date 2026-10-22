import React, { useEffect, useId, useRef, useState } from 'react';
import { Box, Button, Stack, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import AuthenticatedAttachmentMedia from './AuthenticatedAttachmentMedia';
import {
    constrainImageTransform, DOUBLE_TAP_IMAGE_SCALE, fitImageToViewport,
    MAX_IMAGE_SCALE, MIN_IMAGE_SCALE, zoomImageAtPoint,
} from '../../../../shared/attachment-image-transform.ts';

const initialTransform = () => ({ scale: MIN_IMAGE_SCALE, x: 0, y: 0 });
const focusOf = points => ({
    x: points.reduce((sum, point) => sum + point.x, 0) / points.length,
    y: points.reduce((sum, point) => sum + point.y, 0) / points.length,
});
const distanceOf = points => points.length > 1 ? Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y) : 0;

function AttachmentImagePreview({ url, label }) {
    const hintId = useId();
    const viewportRef = useRef(null);
    const pointers = useRef(new Map());
    const gesture = useRef(null);
    const lastTap = useRef(null);
    const currentTransform = useRef(initialTransform());
    const [transform, setTransform] = useState(initialTransform);
    const [viewport, setViewport] = useState({ width: 0, height: 0 });
    const [naturalSize, setNaturalSize] = useState({ width: 0, height: 0 });
    const fitted = fitImageToViewport(naturalSize, viewport);
    const ready = fitted.width > 0 && fitted.height > 0;

    useEffect(() => {
        const observer = new ResizeObserver(([entry]) => {
            const { width, height } = entry.contentRect;
            setViewport(previous => previous.width === width && previous.height === height ? previous : { width, height });
        });
        observer.observe(viewportRef.current);
        return () => observer.disconnect();
    }, []);

    useEffect(() => {
        currentTransform.current = initialTransform();
        setTransform(initialTransform());
        pointers.current.clear();
        gesture.current = null;
        lastTap.current = null;
    }, [url, fitted.width, fitted.height]);

    const applyTransform = next => {
        currentTransform.current = next;
        setTransform(next);
    };
    const changeZoom = (scale, focal = { x: viewport.width / 2, y: viewport.height / 2 }) => {
        if (ready) applyTransform(zoomImageAtPoint(currentTransform.current, scale, focal, focal, fitted, viewport));
    };
    const seedGesture = () => {
        const points = [...pointers.current.values()];
        gesture.current = points.length ? {
            transform: currentTransform.current, origin: focusOf(points), distance: distanceOf(points), canTap: false, startedAt: performance.now(),
        } : null;
    };
    const pointOf = event => {
        const bounds = event.currentTarget.getBoundingClientRect();
        return { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
    };
    const startPointer = event => {
        if (!ready || event.target.closest('button') || (event.pointerType === 'mouse' && event.button !== 0)) return;
        event.currentTarget.setPointerCapture(event.pointerId);
        pointers.current.set(event.pointerId, pointOf(event));
        seedGesture();
        gesture.current.canTap = pointers.current.size === 1;
        if (pointers.current.size > 1) lastTap.current = null;
    };
    const movePointer = event => {
        if (!pointers.current.has(event.pointerId) || !gesture.current) return;
        pointers.current.set(event.pointerId, pointOf(event));
        const points = [...pointers.current.values()];
        const focal = focusOf(points);
        const start = gesture.current;
        if (points.length > 1 || Math.hypot(focal.x - start.origin.x, focal.y - start.origin.y) > 8) start.canTap = false;
        if (points.length > 1 && start.distance > 0) {
            applyTransform(zoomImageAtPoint(start.transform, start.transform.scale * distanceOf(points) / start.distance,
                start.origin, focal, fitted, viewport));
        } else {
            applyTransform(constrainImageTransform({ ...start.transform,
                x: start.transform.x + focal.x - start.origin.x, y: start.transform.y + focal.y - start.origin.y,
            }, fitted, viewport));
        }
    };
    const endPointer = event => {
        const start = gesture.current;
        if (event.type === 'pointerup' && pointers.current.size === 1 && start?.canTap && performance.now() - start.startedAt < 250) {
            const point = pointOf(event);
            const previous = lastTap.current;
            if (previous && previous.pointerType === event.pointerType && performance.now() - previous.time < 300
                && Math.hypot(point.x - previous.point.x, point.y - previous.point.y) < 20) {
                changeZoom(currentTransform.current.scale > 1 ? MIN_IMAGE_SCALE : DOUBLE_TAP_IMAGE_SCALE, point);
                lastTap.current = null;
            } else lastTap.current = { point, time: performance.now(), pointerType: event.pointerType };
        } else if (pointers.current.has(event.pointerId)) lastTap.current = null;
        pointers.current.delete(event.pointerId);
        seedGesture();
    };
    const handleKeyboard = event => {
        if (!ready) return;
        if (event.key === '+' || event.key === '=') changeZoom(transform.scale + 0.5);
        else if (event.key === '-') changeZoom(transform.scale - 0.5);
        else if (event.key === 'Home' || event.key === '0') changeZoom(MIN_IMAGE_SCALE);
        else if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
            applyTransform(constrainImageTransform({ ...transform,
                x: transform.x + (event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0) * viewport.width / 4,
                y: transform.y + (event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' ? 1 : 0) * viewport.height / 4,
            }, fitted, viewport));
        } else return;
        event.preventDefault();
    };

    return (
        <Stack spacing={1}>
            <Box ref={viewportRef} role="region" aria-label="图片缩放与拖动" aria-describedby={hintId} tabIndex={0}
                onPointerDown={startPointer} onPointerMove={movePointer} onPointerUp={endPointer}
                onPointerCancel={endPointer} onLostPointerCapture={endPointer} onKeyDown={handleKeyboard}
                sx={{ height: 'min(62vh, 560px)', width: '100%', overflow: 'hidden', position: 'relative',
                    display: 'grid', placeItems: 'center', touchAction: 'none', userSelect: 'none', bgcolor: 'grey.100',
                    cursor: ready && transform.scale > 1 ? 'grab' : 'default',
                    '&:focus-visible': { outline: '3px solid', outlineColor: 'primary.main', outlineOffset: -3 } }}>
                <Box sx={{ width: ready ? fitted.width : '100%', height: ready ? fitted.height : '100%',
                    transform: `translate(${transform.x}px, ${transform.y}px) scale(${transform.scale})` }}>
                    <AuthenticatedAttachmentMedia url={url} alt={label}
                        onLoad={event => setNaturalSize({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight })} />
                </Box>
            </Box>
            <Stack direction="row" alignItems="center" justifyContent="center" spacing={1}>
                <Button aria-label="缩小图片" variant="outlined" disabled={!ready || transform.scale <= MIN_IMAGE_SCALE}
                    onClick={() => changeZoom(transform.scale - 0.5)} sx={{ minWidth: 44, minHeight: 44 }}><RemoveIcon /></Button>
                <Typography aria-live="polite" sx={{ minWidth: 52, textAlign: 'center' }}>{Math.round(transform.scale * 100)}%</Typography>
                <Button aria-label="放大图片" variant="outlined" disabled={!ready || transform.scale >= MAX_IMAGE_SCALE}
                    onClick={() => changeZoom(transform.scale + 0.5)} sx={{ minWidth: 44, minHeight: 44 }}><AddIcon /></Button>
                <Button variant="outlined" startIcon={<RestartAltIcon />} disabled={!ready} onClick={() => changeZoom(MIN_IMAGE_SCALE)}>还原</Button>
            </Stack>
            <Typography id={hintId} variant="caption" color="text.secondary" textAlign="center">
                双指缩放，放大后拖动；双击或使用按钮调整，支持 1–5 倍。
            </Typography>
        </Stack>
    );
}

export default AttachmentImagePreview;

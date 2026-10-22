import { useLayoutEffect, useRef } from 'react';
import { TableContainer } from '@mui/material';
import { uiLayout } from '../../ui/layout';

export default function ViewportTableContainer({ sx, ...props }) {
    const containerRef = useRef(null);

    useLayoutEffect(() => {
        const container = containerRef.current;
        if (!container) return undefined;
        let frame;
        const updateHeight = () => {
            if (!container.getClientRects().length) return;
            const documentTop = container.getBoundingClientRect().top + window.scrollY;
            const availableHeight = Math.max(280, Math.floor(window.innerHeight - documentTop - uiLayout.pageGutter));
            container.style.setProperty('--table-viewport-height', `${availableHeight}px`);
        };
        const scheduleUpdate = () => {
            window.cancelAnimationFrame(frame);
            frame = window.requestAnimationFrame(updateHeight);
        };
        // 筛选展开、侧栏变化和提示出现都会改变表格起点。
        const observer = new ResizeObserver(scheduleUpdate);
        for (let ancestor = container.parentElement; ancestor; ancestor = ancestor.parentElement) {
            observer.observe(ancestor);
            if (ancestor.tagName === 'MAIN') break;
        }
        window.addEventListener('resize', scheduleUpdate);
        updateHeight();
        return () => {
            observer.disconnect();
            window.cancelAnimationFrame(frame);
            window.removeEventListener('resize', scheduleUpdate);
        };
    }, []);

    return <TableContainer ref={containerRef} {...props} sx={[{ maxHeight: 'var(--table-viewport-height, calc(100dvh - 320px))' }, ...(Array.isArray(sx) ? sx : [sx])]} />;
}

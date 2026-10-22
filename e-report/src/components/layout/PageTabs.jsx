import { Box, Tab, Tabs } from '@mui/material';
import { useLayoutEffect, useRef } from 'react';

export function PageTabs({ id, label, value, onChange, options }) {
    const rootRef = useRef(null);
    const selectedTabRef = useRef(null);

    useLayoutEffect(() => {
        const keepSelectedTabVisible = () => {
            const tab = selectedTabRef.current;
            const scroller = tab?.closest('.MuiTabs-scroller');
            if (!scroller) return;
            const tabBounds = tab.getBoundingClientRect();
            const scrollBounds = scroller.getBoundingClientRect();
            if (tabBounds.left < scrollBounds.left) scroller.scrollLeft -= scrollBounds.left - tabBounds.left;
            else if (tabBounds.right > scrollBounds.right) scroller.scrollLeft += tabBounds.right - scrollBounds.right;
        };
        keepSelectedTabVisible();
        const observer = new ResizeObserver(keepSelectedTabVisible);
        if (rootRef.current) observer.observe(rootRef.current);
        const scroller = rootRef.current?.querySelector('.MuiTabs-scroller');
        if (scroller) observer.observe(scroller);
        return () => observer.disconnect();
    }, [value]);

    return (
        <Tabs
            ref={rootRef}
            value={value}
            onChange={(_, nextValue) => onChange(nextValue)}
            aria-label={label}
            variant="scrollable"
            scrollButtons="auto"
            sx={{ borderBottom: 1, borderColor: 'divider', minWidth: 0, mb: 2 }}
        >
            {options.map(option => (
                <Tab key={option.value} value={option.value} label={option.label} ref={value === option.value ? selectedTabRef : undefined}
                     id={`${id}-tab-${option.value}`} aria-controls={value === option.value ? `${id}-panel-${option.value}` : undefined} />
            ))}
        </Tabs>
    );
}

export function PageTabPanel({ id, value, selected, children }) {
    return (
        <Box role="tabpanel" id={`${id}-panel-${value}`} aria-labelledby={`${id}-tab-${value}`}
             hidden={value !== selected} sx={{ display: value === selected ? 'block' : 'none', minWidth: 0 }}>
            {children}
        </Box>
    );
}

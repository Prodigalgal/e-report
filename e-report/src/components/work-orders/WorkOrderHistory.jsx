import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Box, CircularProgress, Paper, Typography } from '@mui/material';

import { fetchWorkOrderEvents, fetchWorkOrderResponses } from '../../services/workOrderApiService';
import { formatApiErrorMessage } from '../../utils/formatApiErrorMessage';
import { formatDate } from '../../utils/formatDate';
import { buildWorkOrderTimeline } from './workOrderTimeline';

const TIMELINE_TONES = {
    lifecycle: { marker: 'primary.main', border: 'primary.light' },
    response: { marker: 'warning.main', border: 'warning.light' },
    approved: { marker: 'success.main', border: 'success.light' },
    rejected: { marker: 'error.main', border: 'error.light' },
};

function WorkOrderHistory({ workOrderId, version }) {
    const [events, setEvents] = useState([]);
    const [responses, setResponses] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        setLoading(true);
        setError('');
        Promise.all([
            fetchWorkOrderEvents(workOrderId, { page: 0, size: 200 }),
            fetchWorkOrderResponses(workOrderId),
        ]).then(([eventResponse, responseResponse]) => {
            if (!active) return;
            setEvents(eventResponse.data?.content || []);
            setResponses(responseResponse.data || []);
        }).catch((requestError) => {
            if (active) setError(formatApiErrorMessage(requestError, '加载工单处理历史失败。'));
        }).finally(() => {
            if (active) setLoading(false);
        });
        return () => { active = false; };
    }, [version, workOrderId]);

    const timeline = useMemo(() => buildWorkOrderTimeline(events, responses), [events, responses]);

    if (loading) {
        return <Box sx={{ py: 3, display: 'grid', placeItems: 'center' }}><CircularProgress size={28} /></Box>;
    }
    if (error) return <Alert severity="error">{error}</Alert>;
    if (timeline.length === 0) {
        return <Typography variant="body2" color="text.secondary">暂无处理或审计记录。</Typography>;
    }

    return (
        <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                按发生时间从早到晚排列，共 {timeline.length} 条记录
            </Typography>
            {timeline.map((item, index) => {
                const tone = TIMELINE_TONES[item.tone] || TIMELINE_TONES.lifecycle;
                const last = index === timeline.length - 1;
                return (
                    <Box key={item.id} sx={{ display: 'grid', gridTemplateColumns: '24px minmax(0, 1fr)', gap: 1 }}>
                        <Box sx={{ position: 'relative', display: 'flex', justifyContent: 'center' }}>
                            {!last && (
                                <Box sx={{ position: 'absolute', top: 14, bottom: -12, width: 2, bgcolor: 'divider' }} />
                            )}
                            <Box
                                sx={{
                                    mt: 0.75,
                                    width: 12,
                                    height: 12,
                                    borderRadius: '50%',
                                    bgcolor: tone.marker,
                                    border: '3px solid',
                                    borderColor: 'background.paper',
                                    boxShadow: theme => `0 0 0 1px ${theme.palette.divider}`,
                                    zIndex: 1,
                                }}
                            />
                        </Box>
                        <Paper
                            variant="outlined"
                            sx={{
                                mb: last ? 0 : 1.5,
                                p: 1.25,
                                borderLeftWidth: 3,
                                borderLeftColor: tone.border,
                                minWidth: 0,
                            }}
                        >
                            <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', justifyContent: 'space-between', gap: 0.5 }}>
                                <Typography variant="body2" sx={{ fontWeight: 750 }}>{item.title}</Typography>
                                <Typography variant="caption" color="text.secondary">{formatDate(item.occurredAt)}</Typography>
                            </Box>
                            <Typography variant="caption" color="text.secondary">操作人：{item.actor}</Typography>
                            {item.meta && <Typography variant="body2" sx={{ mt: 0.75 }}>{item.meta}</Typography>}
                            {item.detail && <Typography variant="body2" sx={{ mt: 0.75, whiteSpace: 'pre-wrap' }}>{item.detail}</Typography>}
                        </Paper>
                    </Box>
                );
            })}
        </Box>
    );
}

export default WorkOrderHistory;

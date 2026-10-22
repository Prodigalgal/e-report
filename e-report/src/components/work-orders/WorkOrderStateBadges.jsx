import React from 'react';
import { Box, Chip, Typography } from '@mui/material';
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded';

import {
    presentationChipSx,
    slaRiskPresentation,
    statusDisplayLabel,
    statusPresentation,
} from '../../constants/workOrderPresentation';
import { slaSummaryLabel } from '../../constants/workOrderSla';

export function WorkOrderStatusBadge({ workOrder, size = 'small' }) {
    const token = statusPresentation(workOrder);
    return (
        <Chip
            label={statusDisplayLabel(workOrder)}
            size={size}
            variant="outlined"
            sx={{
                ...presentationChipSx(token),
                fontWeight: 500,
            }}
        />
    );
}

export function WorkOrderSlaIndicator({ workOrder }) {
    const label = slaSummaryLabel(workOrder);
    if (!label) {
        return null;
    }

    const token = slaRiskPresentation(workOrder.slaRiskLevel);
    return (
        <Box
            component="span"
            aria-label={`时限：${label}`}
            sx={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 0.5,
                minWidth: 0,
                minHeight: 24,
                px: 0.25,
                color: token.foreground,
            }}
        >
            <AccessTimeRoundedIcon aria-hidden="true" sx={{ fontSize: 15, flexShrink: 0 }} />
            <Typography
                component="span"
                variant="caption"
                sx={{ color: 'inherit', fontWeight: 500, lineHeight: 1.25, whiteSpace: 'nowrap' }}
            >
                {label}
            </Typography>
        </Box>
    );
}

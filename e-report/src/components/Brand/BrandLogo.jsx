import React from 'react';
import { Box } from '@mui/material';

import logoUrl from '../../assets/e-report-logo.svg';
import { APP_NAME } from '../../constants/appMetadata';

function BrandLogo({ size = 34, sx }) {
    return (
        <Box
            component="img"
            src={logoUrl}
            alt={APP_NAME}
            width={size}
            height={size}
            sx={{
                width: size,
                height: size,
                display: 'block',
                flexShrink: 0,
                ...sx,
            }}
        />
    );
}

export default BrandLogo;

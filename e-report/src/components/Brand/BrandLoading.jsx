import React from 'react';
import { Box, LinearProgress, Typography } from '@mui/material';

import { APP_NAME } from '../../constants/appMetadata';
import BrandLogo from './BrandLogo';

function BrandLoading({ message = '正在加载应用...', minHeight = '100vh', compact = false }) {
    const logoSize = compact ? 42 : 64;
    return (
        <Box
            sx={{
                minHeight,
                display: 'grid',
                placeItems: 'center',
                px: 2,
                py: compact ? 2 : 4,
                bgcolor: 'background.default',
                color: 'text.primary',
            }}
        >
            <Box
                sx={{
                    width: 'min(100%, 260px)',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: compact ? 1.25 : 1.75,
                }}
            >
                <BrandLogo size={logoSize} sx={{ filter: 'drop-shadow(0 10px 22px rgba(37,99,235,.22))' }} />
                <Typography
                    variant={compact ? 'body1' : 'h6'}
                    component="div"
                    sx={{ fontWeight: 700, textAlign: 'center', letterSpacing: 0 }}
                >
                    {APP_NAME}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center' }}>
                    {message}
                </Typography>
                <LinearProgress
                    sx={{
                        mt: 0.5,
                        width: compact ? 128 : 168,
                        height: 4,
                        borderRadius: 999,
                        bgcolor: 'rgba(37,99,235,.12)',
                        '& .MuiLinearProgress-bar': {
                            borderRadius: 999,
                        },
                    }}
                />
            </Box>
        </Box>
    );
}

export default BrandLoading;

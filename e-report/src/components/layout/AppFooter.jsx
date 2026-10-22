// src/components/layout/AppFooter.jsx
import React from 'react';
import { Box, Typography } from '@mui/material';
import { APP_NAME } from '../../constants/appMetadata';
import { displayYear } from '../../utils/formatDate.js';

function AppFooter() {
    return (
        <Box
            component="footer"
            sx={{
                py: 3,
                px: 2,
                mt: 'auto', // This pushes the footer to the bottom of the page
                backgroundColor: (theme) =>
                    theme.palette.mode === 'light' ? theme.palette.grey[200] : theme.palette.grey[800],
                textAlign: 'center',
                borderTop: (theme) => `1px solid ${theme.palette.divider}`,
            }}
        >
            <Typography variant="body2" color="text.secondary">
                © {displayYear()} {APP_NAME}。保留所有权利。
            </Typography>
        </Box>
    );
}

export default AppFooter;

import { Typography } from '@mui/material';

import { uiLayout } from '../../ui/layout';

export default function PageTitle({ children, sx, ...props }) {
    return (
        <Typography
            component="h1"
            variant="h5"
            {...props}
            sx={{
                minWidth: 0,
                fontSize: { xs: uiLayout.pageTitleSize, sm: 28 },
                lineHeight: { xs: `${uiLayout.pageTitleLineHeight}px`, sm: '38px' },
                fontWeight: 650,
                letterSpacing: '-0.03em',
                overflowWrap: 'anywhere',
                ...sx,
            }}
        >
            {children}
        </Typography>
    );
}

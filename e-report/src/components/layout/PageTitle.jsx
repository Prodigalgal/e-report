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
                fontSize: uiLayout.pageTitleSize,
                lineHeight: `${uiLayout.pageTitleLineHeight}px`,
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

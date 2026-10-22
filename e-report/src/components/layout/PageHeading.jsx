import { Box, Typography } from '@mui/material';

import PageTitle from './PageTitle';

export default function PageHeading({ title, description, sx }) {
    return (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', columnGap: 2, rowGap: 0.5, minWidth: 0, ...sx }}>
            <PageTitle>{title}</PageTitle>
            {description && <Typography variant="body2" color="text.secondary">{description}</Typography>}
        </Box>
    );
}

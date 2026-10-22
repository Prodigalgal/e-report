import { Box, Pagination, Typography, useMediaQuery } from '@mui/material';

import { getPaginationItemAriaLabel } from '../../utils/paginationAriaLabel';

export function ListPagination({ showFirstButton = false, showLastButton = false, ...props }) {
    const compact = useMediaQuery('(max-width:599.95px)');
    return <Pagination
        {...props}
        getItemAriaLabel={getPaginationItemAriaLabel}
        color="primary"
        showFirstButton={!compact && showFirstButton}
        showLastButton={!compact && showLastButton}
        boundaryCount={compact ? 0 : 1}
        siblingCount={compact ? 0 : 1}
        size={compact ? 'small' : 'medium'}
    />;
}

export default function ListResultsBar({ summary, children, sx }) {
    return (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 1, minWidth: 0, py: 0.5, ...sx }}>
            <Typography component="div" variant="body2" color="text.secondary" aria-live="polite" sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', columnGap: 1, minWidth: 0 }}>
                {summary}
            </Typography>
            {children && <Box sx={{ display: 'flex', justifyContent: 'flex-end', minWidth: 0, ml: 'auto' }}>{children}</Box>}
        </Box>
    );
}

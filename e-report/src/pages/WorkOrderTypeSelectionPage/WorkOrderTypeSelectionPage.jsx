import PageTitle from '../../components/layout/PageTitle';
import React, { useMemo } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Box, Button, Card, CardContent, Container, Typography } from '@mui/material';
import AssignmentIcon from '@mui/icons-material/Assignment';
import ConstructionIcon from '@mui/icons-material/Construction';
import Inventory2Icon from '@mui/icons-material/Inventory2';

import { WORK_ORDER_TYPE_CODES, WORK_ORDER_TYPES } from '../../constants/workOrderCatalog';
import { reportTypePresentation } from '../../constants/workOrderPresentation';
import { getWorkOrderPermissions } from '../../constants/permissions';
import { useAuth } from '../../hooks/useAuth';

const TYPE_ICONS = {
    [WORK_ORDER_TYPE_CODES.NETWORK_FAULT]: AssignmentIcon,
    [WORK_ORDER_TYPE_CODES.BOX_DOOR_OPEN]: Inventory2Icon,
    [WORK_ORDER_TYPE_CODES.DAMAGED_BOX_REPLACEMENT]: ConstructionIcon,
};

function WorkOrderTypeSelectionPage() {
    const { hasPermission } = useAuth();
    const reportTypes = useMemo(
        () => WORK_ORDER_TYPES.filter((item) => hasPermission(getWorkOrderPermissions(item.reportType)?.report)),
        [hasPermission],
    );

    return (
        <Container maxWidth="md" disableGutters>
            <Box sx={{ mb: 2.5 }}>
                <PageTitle>
                    我要报单
                </PageTitle>
                <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                    先选择现场情况，再填写位置、原因和材料。
                </Typography>
            </Box>

            {reportTypes.length > 0 ? (
                <Box sx={{ display: 'grid', gap: 1.5 }}>
                    {reportTypes.map((item) => {
                        const TypeIcon = TYPE_ICONS[item.reportType] || AssignmentIcon;
                        const typeToken = reportTypePresentation(item.reportType);
                        return (
                            <Button
                                key={item.reportType}
                                component={RouterLink}
                                to={item.reportPath}
                                variant="outlined"
                                fullWidth
                                startIcon={<TypeIcon />}
                                sx={{
                                    justifyContent: 'flex-start',
                                    alignItems: 'flex-start',
                                    textAlign: 'left',
                                    minHeight: 82,
                                    px: 2,
                                    py: 1.5,
                                    borderLeftWidth: 5,
                                    borderLeftColor: typeToken.foreground,
                                    color: typeToken.foreground,
                                    borderColor: typeToken.border,
                                    backgroundColor: typeToken.surface,
                                    '& .MuiButton-startIcon': { mt: 0.25 },
                                }}
                            >
                                <Box sx={{ minWidth: 0 }}>
                                    <Typography component="span" sx={{ display: 'block', fontWeight: 800 }}>
                                        {item.reportLabel}
                                    </Typography>
                                    <Typography component="span" variant="body2" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
                                        {item.dashboardDescription}
                                    </Typography>
                                </Box>
                            </Button>
                        );
                    })}
                </Box>
            ) : (
                <Card variant="outlined">
                    <CardContent>
                        <Typography color="text.secondary">当前账号没有可用的报单权限。</Typography>
                    </CardContent>
                </Card>
            )}
        </Container>
    );
}

export default WorkOrderTypeSelectionPage;

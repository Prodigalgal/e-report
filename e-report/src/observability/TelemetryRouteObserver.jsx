import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

import { addTelemetryBreadcrumb, recordTelemetryOperation } from './telemetry.js';

export default function TelemetryRouteObserver() {
    const location = useLocation();

    useEffect(() => {
        addTelemetryBreadcrumb('UI', 'ROUTE_CHANGE', { path: location.pathname });
        recordTelemetryOperation({
            category: 'UI',
            eventName: 'ROUTE_VIEW',
            operationName: '打开页面',
            outcome: 'SUCCESS',
            route: location.pathname,
            attributes: { route: location.pathname },
        });
    }, [location.pathname]);

    return null;
}

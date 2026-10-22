import React from 'react';

import BrandLoading from '../components/Brand/BrandLoading';

function RouteLoadingFallback() {
    return <BrandLoading compact minHeight={{ xs: 240, sm: 320 }} message="页面加载中..." />;
}

export default RouteLoadingFallback;

import { isLiteClient, shareTextInLite } from '../services/liteBridge.js';
import { copyText } from './clipboard.js';

export const parseGpsCoordinates = (gpsCoordinates) => {
    if (typeof gpsCoordinates !== 'string') return null;
    const parts = gpsCoordinates.split(',').map(value => Number(value.trim()));
    if (parts.length !== 2 || parts.some(value => !Number.isFinite(value))) return null;
    const [longitude, latitude] = parts;
    if (Math.abs(longitude) > 180 || Math.abs(latitude) > 90) return null;
    return { longitude, latitude };
};
export const buildAmapNavigationUrl = (gpsCoordinates, destinationName = '工单现场') => {
    const point = parseGpsCoordinates(gpsCoordinates);
    if (!point) return null;
    const destination = `${point.longitude},${point.latitude},${destinationName}`;
    const query = new URLSearchParams({
        to: destination,
        mode: 'car',
        coordinate: 'wgs84',
        callnative: '1',
        src: 'e-report',
    });
    return `https://uri.amap.com/navigation?${query.toString()}`;
};

export const buildAmapMarkerUrl = (gpsCoordinates, destinationName = '工单现场') => {
    const point = parseGpsCoordinates(gpsCoordinates);
    if (!point) return null;
    const query = new URLSearchParams({
        position: `${point.longitude},${point.latitude}`,
        name: destinationName,
        coordinate: 'wgs84',
        callnative: '1',
        src: 'e-report',
    });
    return `https://uri.amap.com/marker?${query.toString()}`;
};

export const shareWorkOrderLocation = async ({ id, gpsCoordinates, location }) => {
    const markerUrl = buildAmapMarkerUrl(gpsCoordinates, location || '工单现场');
    if (!markerUrl) {
        throw new Error('工单没有有效定位坐标，请先补录经度、纬度后再分享。');
    }
    const title = `工单 #${id} 现场位置`;
    const text = `${title}\n地点：${location || '未填写'}\n定位坐标：${gpsCoordinates}`;
    if (isLiteClient()) {
        await shareTextInLite(title, `${text}\n位置：${markerUrl}`);
        return 'shared';
    }
    if (navigator.share) {
        await navigator.share({ title, text, url: markerUrl });
        return 'shared';
    }
    await copyText(`${text}\n位置：${markerUrl}`);
    return 'copied';
};

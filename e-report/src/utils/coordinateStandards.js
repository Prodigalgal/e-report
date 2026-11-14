/**
 * 坐标系标准与本地换算。
 *
 * 系统契约：存储一律 WGS84，地图渲染一律 GCJ-02。
 * 这里只实现界面需要的换算，识别与纠偏由服务端统一裁决，避免两端算法漂移。
 */

export const COORDINATE_STANDARDS = {
    WGS84: 'wgs84',
    GCJ02: 'gcj02',
    BD09: 'bd09',
};

export const COORDINATE_STANDARD_OPTIONS = [
    { value: COORDINATE_STANDARDS.WGS84, label: '国际通用坐标（GPS 原始）' },
    { value: COORDINATE_STANDARDS.GCJ02, label: '国测局坐标（高德、腾讯）' },
    { value: COORDINATE_STANDARDS.BD09, label: '百度坐标' },
];

const PI = Math.PI;
const AXIS = 6378245.0;
// 椭球第一偏心率的平方 ee = (a^2 - b^2) / a^2。
// 后端 MapService 写的是 0.00669342162296594323，JavaScript 双精度无法精确表示该字面量，
// 这里使用可精确往返的最短表示，避免运行时被静默截断后与后端产生差异。
const ECCENTRICITY_SQUARED = 0.006693421622965943;
const BD09_X_PI = (PI * 3000.0) / 180.0;
const BD09_Z_PI = 0.0065;
const BD09_Z = 0.006;

const outOfChina = (latitude, longitude) => (
    longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271
);

const transformLatitude = (x, y) => {
    let result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
    result += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
    result += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0) / 3.0;
    result += ((160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0;
    return result;
};

const transformLongitude = (x, y) => {
    let result = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
    result += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
    result += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0) / 3.0;
    result += ((150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0) / 3.0;
    return result;
};

export const wgs84ToGcj02 = (latitude, longitude) => {
    if (outOfChina(latitude, longitude)) {
        return [latitude, longitude];
    }
    const deltaLatitude = transformLatitude(longitude - 105.0, latitude - 35.0);
    const deltaLongitude = transformLongitude(longitude - 105.0, latitude - 35.0);
    const radianLatitude = (latitude / 180.0) * PI;
    let magic = Math.sin(radianLatitude);
    magic = 1 - ECCENTRICITY_SQUARED * magic * magic;
    const sqrtMagic = Math.sqrt(magic);
    const latitudeOffset = (deltaLatitude * 180.0)
        / (((AXIS * (1 - ECCENTRICITY_SQUARED)) / (magic * sqrtMagic)) * PI);
    const longitudeOffset = (deltaLongitude * 180.0)
        / ((AXIS / sqrtMagic) * Math.cos(radianLatitude) * PI);
    return [latitude + latitudeOffset, longitude + longitudeOffset];
};

export const gcj02ToWgs84 = (latitude, longitude) => {
    if (outOfChina(latitude, longitude)) {
        return [latitude, longitude];
    }
    let currentLatitude = latitude;
    let currentLongitude = longitude;
    for (let iteration = 0; iteration < 30; iteration += 1) {
        const [approxLatitude, approxLongitude] = wgs84ToGcj02(currentLatitude, currentLongitude);
        const latitudeDelta = approxLatitude - latitude;
        const longitudeDelta = approxLongitude - longitude;
        if (Math.abs(latitudeDelta) < 1e-9 && Math.abs(longitudeDelta) < 1e-9) {
            break;
        }
        currentLatitude -= latitudeDelta;
        currentLongitude -= longitudeDelta;
    }
    return [currentLatitude, currentLongitude];
};

export const gcj02ToBd09 = (latitude, longitude) => {
    const z = Math.sqrt(longitude * longitude + latitude * latitude)
        + 0.00002 * Math.sin(latitude * BD09_X_PI);
    const theta = Math.atan2(latitude, longitude) + 0.000003 * Math.cos(longitude * BD09_X_PI);
    return [z * Math.sin(theta) + BD09_Z, z * Math.cos(theta) + BD09_Z_PI];
};

export const bd09ToGcj02 = (latitude, longitude) => {
    let x = longitude - BD09_Z_PI;
    let y = latitude - BD09_Z;
    const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * BD09_X_PI);
    const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * BD09_X_PI);
    // 与后端一致：以近似解为初值迭代逼近，消除公开近似式的十米级残差。
    let currentLatitude = z * Math.sin(theta);
    let currentLongitude = z * Math.cos(theta);
    for (let iteration = 0; iteration < 30; iteration += 1) {
        const [approxLatitude, approxLongitude] = gcj02ToBd09(currentLatitude, currentLongitude);
        const latitudeDelta = approxLatitude - latitude;
        const longitudeDelta = approxLongitude - longitude;
        if (Math.abs(latitudeDelta) < 1e-9 && Math.abs(longitudeDelta) < 1e-9) {
            break;
        }
        currentLatitude -= latitudeDelta;
        currentLongitude -= longitudeDelta;
    }
    return [currentLatitude, currentLongitude];
};

/**
 * 把指定标准的坐标转换为 WGS84（系统存储契约）。
 */
export const toWgs84 = (standard, latitude, longitude) => {
    switch (standard) {
        case COORDINATE_STANDARDS.GCJ02:
            return gcj02ToWgs84(latitude, longitude);
        case COORDINATE_STANDARDS.BD09: {
            const [gcjLatitude, gcjLongitude] = bd09ToGcj02(latitude, longitude);
            return gcj02ToWgs84(gcjLatitude, gcjLongitude);
        }
        default:
            return [latitude, longitude];
    }
};

/**
 * 把 WGS84 坐标转换为指定标准。
 */
export const fromWgs84 = (standard, latitude, longitude) => {
    switch (standard) {
        case COORDINATE_STANDARDS.GCJ02:
            return wgs84ToGcj02(latitude, longitude);
        case COORDINATE_STANDARDS.BD09: {
            const [gcjLatitude, gcjLongitude] = wgs84ToGcj02(latitude, longitude);
            return gcj02ToBd09(gcjLatitude, gcjLongitude);
        }
        default:
            return [latitude, longitude];
    }
};

export const formatContractCoordinates = (latitude, longitude) => (
    `${Number(longitude).toFixed(6)},${Number(latitude).toFixed(6)}`
);

export const parseContractCoordinates = (value) => {
    if (typeof value !== 'string') return null;
    const parts = value.split(',').map(part => Number(part.trim()));
    if (parts.length !== 2 || parts.some(part => !Number.isFinite(part))) return null;
    const [longitude, latitude] = parts;
    if (Math.abs(longitude) > 180 || Math.abs(latitude) > 90) return null;
    return { longitude, latitude };
};

/**
 * 两点间近似距离（米），用于界面提示坐标差异量级。
 */
export const distanceMeters = (latitudeA, longitudeA, latitudeB, longitudeB) => {
    const meanLatitude = ((latitudeA + latitudeB) / 2) * (PI / 180);
    const deltaLatitude = (latitudeB - latitudeA) * (PI / 180);
    const deltaLongitude = (longitudeB - longitudeA) * (PI / 180);
    const x = deltaLongitude * Math.cos(meanLatitude);
    return Math.sqrt(x * x + deltaLatitude * deltaLatitude) * 6371000;
};

export const formatDistance = (meters) => {
    if (!Number.isFinite(meters)) return '';
    if (meters < 1000) return `约 ${Math.round(meters)} 米`;
    return `约 ${(meters / 1000).toFixed(2)} 公里`;
};

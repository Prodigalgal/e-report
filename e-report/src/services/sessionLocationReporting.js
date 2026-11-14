import { reportSessionLocation } from '../services/sessionLocationApiService';

/**
 * WEB 端登录态位置上报。
 *
 * 与 APP 的前台服务对应：网页端在登录期间按间隔上报浏览器定位，使后台态势地图同样能看到
 * 使用桌面端的在岗人员。浏览器定位精度依赖网络与授权，失败时如实不上报，不伪造坐标。
 *
 * 边界：仅在登录态运行；退出登录立即停止并清理定时器。
 */

const REPORT_INTERVAL_MS = 5 * 60 * 1000;
const GEOLOCATION_TIMEOUT_MS = 20000;

let intervalId = null;
let running = false;

const getCurrentPosition = () => new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
        reject(new Error('浏览器不支持定位。'));
        return;
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true,
        timeout: GEOLOCATION_TIMEOUT_MS,
        maximumAge: 60000,
    });
});

/**
 * 采集并上报一次当前位置。失败时静默返回 false，不干扰用户体验。
 */
export const reportCurrentLocationOnce = async () => {
    try {
        const position = await getCurrentPosition();
        const { latitude, longitude, accuracy } = position.coords;
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
            return false;
        }
        await reportSessionLocation({
            latitude,
            longitude,
            accuracyMeters: Number.isFinite(accuracy) ? accuracy : undefined,
            provider: 'browser-geolocation',
        });
        return true;
    } catch {
        return false;
    }
};

/**
 * 启动周期性上报。重复调用只保留一个定时器。
 */
export const startSessionLocationReporting = () => {
    if (running) return;
    running = true;
    void reportCurrentLocationOnce();
    intervalId = setInterval(() => { void reportCurrentLocationOnce(); }, REPORT_INTERVAL_MS);
};

/**
 * 停止上报并清理定时器。退出登录时必须调用。
 */
export const stopSessionLocationReporting = () => {
    running = false;
    if (intervalId) {
        clearInterval(intervalId);
        intervalId = null;
    }
};

export const isSessionLocationReporting = () => running;

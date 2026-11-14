import { NativeModules, Platform } from 'react-native';
import { reportSessionLocation } from '@/api/session-report-api';

/**
 * 登录态位置跟踪编排。
 *
 * 分工：原生前台服务负责持续采集并持有最新位置（锁屏与切后台后仍能更新），
 * JS 层负责按间隔读取并上报。上报走 JS 的签名 HTTP 客户端——
 * Android 侧的每个请求都需要设备签名与 nonce 防重放，在原生层重复实现会形成第二套安全逻辑。
 */

type LatestFix = {
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
  provider?: string;
  fixedAtMillis?: number;
  gcjLatitude?: number;
  gcjLongitude?: number;
};

type NativeLocationTracker = {
  isTracking(): Promise<boolean>;
  startTracking(): Promise<boolean>;
  stopTracking(): Promise<boolean>;
  getLatestFix(): Promise<LatestFix | null>;
};

const REPORT_INTERVAL_MS = 5 * 60 * 1000;

const NativeTracker = NativeModules.EReportLocationTracker as NativeLocationTracker | undefined;

let intervalId: ReturnType<typeof setInterval> | null = null;
let started = false;
let lastReportedAtMillis = 0;

const isSupported = () => Platform.OS === 'android' && Boolean(NativeTracker);

/**
 * 上报一次最新位置。
 *
 * 没有可用定位时如实跳过，不构造或推算坐标——地图上的假点位比缺失点位更有害。
 */
export async function reportLatestLocationOnce(): Promise<boolean> {
  if (!isSupported()) {
    return false;
  }
  try {
    const fix = await NativeTracker!.getLatestFix();
    if (!fix || !Number.isFinite(fix.latitude) || !Number.isFinite(fix.longitude)) {
      return false;
    }
    if (fix.latitude === 0 && fix.longitude === 0) {
      return false;
    }
    await reportSessionLocation({
      latitude: fix.latitude,
      longitude: fix.longitude,
      accuracyMeters: Number.isFinite(fix.accuracyMeters) ? fix.accuracyMeters : undefined,
      provider: fix.provider,
    });
    lastReportedAtMillis = Date.now();
    return true;
  } catch {
    // 上报失败不阻断业务：网络恢复或下次定时会重试。
    return false;
  }
}

/**
 * 启动位置跟踪。登录成功后调用；重复调用只保留一个定时器。
 */
export async function startLocationTracking(): Promise<boolean> {
  if (!isSupported() || started) {
    return started;
  }
  try {
    await NativeTracker!.startTracking();
    started = true;
    void reportLatestLocationOnce();
    intervalId = setInterval(() => { void reportLatestLocationOnce(); }, REPORT_INTERVAL_MS);
    return true;
  } catch {
    return false;
  }
}

/**
 * 停止位置跟踪。退出登录、会话失效时调用。
 */
export async function stopLocationTracking(): Promise<void> {
  if (intervalId) {
    clearInterval(intervalId);
    intervalId = null;
  }
  if (!isSupported()) {
    started = false;
    return;
  }
  try {
    await NativeTracker!.stopTracking();
  } catch {
    // 停止失败不改变本地状态：定时器已清理，不会继续上报。
  }
  started = false;
  lastReportedAtMillis = 0;
}

export const isLocationTrackingStarted = () => started;

export const lastLocationReportedAt = () => lastReportedAtMillis;

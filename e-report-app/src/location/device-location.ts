import * as Location from 'expo-location';
import { NativeModules, Platform } from 'react-native';
import { addAppTelemetryBreadcrumb, captureAppTelemetryError } from '@/observability/telemetry';

export type DeviceLocationProvider = 'android-native' | 'amap' | 'expo-location';
export type DeviceLocationCoordinateSystem = 'wgs84' | 'gcj02' | 'unknown';

export type DeviceLocationFix = {
  provider: DeviceLocationProvider;
  source: string;
  coordinateSystem: DeviceLocationCoordinateSystem;
  longitude: number;
  latitude: number;
  accuracy?: number;
  timestamp?: number;
  rawProvider?: string;
  rawLongitude?: number;
  rawLatitude?: number;
  rawCoordinateSystem?: DeviceLocationCoordinateSystem;
  fallbackReason?: string;
  address?: string;
  province?: string;
  city?: string;
  district?: string;
  street?: string;
  streetNum?: string;
  poiName?: string;
  aoiName?: string;
};

export type LastKnownLocationDiagnostics = {
  provider?: string;
  available?: boolean;
  longitude?: number;
  latitude?: number;
  timestamp?: number;
  ageMs?: number;
  accuracy?: number;
};

export type DeviceLocationDiagnostics = {
  finePermissionGranted?: boolean;
  coarsePermissionGranted?: boolean;
  locationManagerAvailable?: boolean;
  locationServicesEnabled?: boolean;
  gpsProviderEnabled?: boolean;
  networkProviderEnabled?: boolean;
  passiveProviderEnabled?: boolean;
  amapConfigured?: boolean;
  androidVersion?: string;
  sdkInt?: number;
  manufacturer?: string;
  model?: string;
  enabledProviders?: string[];
  lastKnownGps?: LastKnownLocationDiagnostics;
  lastKnownNetwork?: LastKnownLocationDiagnostics;
};

type DeviceLocationRequest = {
  timeoutMs: number;
  requiredAccuracyMeters: number;
  cacheMaxAgeMs?: number;
};

type NativeEReportLocationModule = {
  isAmapConfigured(): Promise<boolean>;
  getLocationDiagnostics?(): Promise<DeviceLocationDiagnostics>;
  getGpsProviderCurrentPosition?(options: DeviceLocationRequest): Promise<DeviceLocationFix>;
  getNativeCurrentPosition(options: DeviceLocationRequest): Promise<DeviceLocationFix>;
  getAmapCurrentPosition(options: DeviceLocationRequest): Promise<DeviceLocationFix>;
};

type DualRouteLocationOptions = {
  gpsProviderTimeoutMs: number;
  nativeTimeoutMs: number;
  amapTimeoutMs: number;
  requiredAccuracyMeters: number;
  onStageChange?: (message: string) => void;
  onTelemetryStage?: (name: string, attributes?: Record<string, unknown>) => void;
  traceId?: string;
};

const GPS_PROVIDER_CACHE_MAX_AGE_MS = 15_000;
const NATIVE_PROVIDER_CACHE_MAX_AGE_MS = 30_000;

const NativeEReportLocation = NativeModules.EReportLocation as NativeEReportLocationModule | undefined;

export async function getDeviceLocationDiagnostics(): Promise<DeviceLocationDiagnostics | null> {
  if (Platform.OS !== 'android' || !NativeEReportLocation?.getLocationDiagnostics) {
    return null;
  }
  return NativeEReportLocation.getLocationDiagnostics().catch(() => null);
}

export async function getDualRouteLocation(options: DualRouteLocationOptions): Promise<DeviceLocationFix> {
  const startedAt = Date.now();
  try {
    const fix = await resolveDualRouteLocation(options);
    options.onTelemetryStage?.('LOCATION_FIX_RECEIVED', {
      provider: fix.provider,
      source: fix.source,
      coordinateSystem: fix.coordinateSystem,
      accuracy: fix.accuracy,
      fallbackUsed: Boolean(fix.fallbackReason),
    });
    addAppTelemetryBreadcrumb('LOCATION', 'LOCATION_SUCCESS', {
      provider: fix.provider,
      source: fix.source,
      coordinateSystem: fix.coordinateSystem,
      accuracy: fix.accuracy,
      durationMs: Date.now() - startedAt,
      fallbackReason: fix.fallbackReason,
    });
    return fix;
  } catch (error) {
    const diagnostics = await getDeviceLocationDiagnostics();
    captureAppTelemetryError(error, {
      category: 'LOCATION',
      eventName: 'LOCATION_FAILURE',
      operationName: '获取定位并解析地址',
      traceId: options.traceId,
      attributes: {
        durationMs: Date.now() - startedAt,
        diagnostics,
      },
    });
    throw error;
  }
}

async function resolveDualRouteLocation(options: DualRouteLocationOptions): Promise<DeviceLocationFix> {
  if (Platform.OS !== 'android' || !NativeEReportLocation?.getNativeCurrentPosition) {
    options.onTelemetryStage?.('EXPO_FALLBACK_STARTED', { reason: 'NATIVE_MODULE_UNAVAILABLE' });
    options.onStageChange?.('当前安装包不支持原生定位诊断，正在使用兼容定位。');
    return getExpoLocationFallback({
      timeoutMs: options.nativeTimeoutMs,
      requiredAccuracyMeters: options.requiredAccuracyMeters,
    });
  }

  const initialDiagnostics = await getDeviceLocationDiagnostics();
  options.onTelemetryStage?.('NATIVE_DIAGNOSTICS_READ', { diagnostics: initialDiagnostics });
  const initialDiagnosticsSummary = formatLocationDiagnosticsSummary(initialDiagnostics);
  if (initialDiagnosticsSummary) {
    options.onStageChange?.(initialDiagnosticsSummary);
  }

  const gpsProviderRequest = {
    timeoutMs: options.gpsProviderTimeoutMs,
    requiredAccuracyMeters: options.requiredAccuracyMeters,
    cacheMaxAgeMs: GPS_PROVIDER_CACHE_MAX_AGE_MS,
  };
  const nativeRequest = {
    timeoutMs: options.nativeTimeoutMs,
    requiredAccuracyMeters: options.requiredAccuracyMeters,
    cacheMaxAgeMs: NATIVE_PROVIDER_CACHE_MAX_AGE_MS,
  };

  let gpsProviderMessage = '当前安装包没有提供卫星定位采集接口。';
  if (NativeEReportLocation.getGpsProviderCurrentPosition) {
    try {
      options.onStageChange?.('正在优先通过系统卫星定位获取坐标。');
      options.onTelemetryStage?.('GPS_PROVIDER_STARTED');
      const fix = await NativeEReportLocation.getGpsProviderCurrentPosition(gpsProviderRequest);
      options.onTelemetryStage?.('GPS_PROVIDER_SUCCEEDED', { accuracy: fix.accuracy, source: fix.source });
      return fix;
    } catch (gpsProviderError) {
      gpsProviderMessage = formatDeviceLocationError(gpsProviderError);
      const diagnostics = await getDeviceLocationDiagnostics();
      options.onTelemetryStage?.('GPS_PROVIDER_FAILED', {
        reason: gpsProviderMessage,
        diagnostics,
      });
      options.onStageChange?.(
        `系统卫星定位未返回有效坐标，正在尝试手机综合定位。原因：${gpsProviderMessage}${formatDiagnosticsSuffix(diagnostics)}`,
      );
    }
  }

  try {
    options.onStageChange?.('正在通过手机原生综合定位获取坐标。若仍失败，将自动切换高德定位。');
    options.onTelemetryStage?.('NATIVE_PROVIDER_STARTED');
    const nativeFix = await NativeEReportLocation.getNativeCurrentPosition(nativeRequest);
    options.onTelemetryStage?.('NATIVE_PROVIDER_SUCCEEDED', {
      accuracy: nativeFix.accuracy,
      source: nativeFix.source,
    });
    return {
      ...nativeFix,
      fallbackReason: gpsProviderMessage,
    };
  } catch (nativeError) {
    const nativeMessage = formatDeviceLocationError(nativeError);
    const diagnostics = await getDeviceLocationDiagnostics();
    options.onTelemetryStage?.('NATIVE_PROVIDER_FAILED', {
      reason: nativeMessage,
      diagnostics,
    });
    options.onStageChange?.(
      `手机原生综合定位未返回有效坐标，正在自动切换高德定位。原生原因：${nativeMessage}${formatDiagnosticsSuffix(diagnostics)}`,
    );

    const amapConfigured = await NativeEReportLocation.isAmapConfigured().catch(() => false);
    if (!amapConfigured) {
      options.onTelemetryStage?.('AMAP_UNAVAILABLE');
      throw new Error(`系统卫星定位和手机综合定位都失败，且当前安装包未配置地图服务密钥。卫星定位：${gpsProviderMessage}；综合定位：${nativeMessage}${formatDiagnosticsSuffix(diagnostics)}`);
    }

    try {
      options.onTelemetryStage?.('AMAP_PROVIDER_STARTED');
      const amapFix = await NativeEReportLocation.getAmapCurrentPosition({
        timeoutMs: options.amapTimeoutMs,
        requiredAccuracyMeters: options.requiredAccuracyMeters,
      });
      options.onTelemetryStage?.('AMAP_PROVIDER_SUCCEEDED', {
        accuracy: amapFix.accuracy,
        source: amapFix.source,
      });
      return {
        ...amapFix,
      fallbackReason: `卫星定位：${gpsProviderMessage}；系统综合定位：${nativeMessage}`,
      };
    } catch (amapError) {
      const amapMessage = formatDeviceLocationError(amapError);
      const latestDiagnostics = await getDeviceLocationDiagnostics();
      options.onTelemetryStage?.('AMAP_PROVIDER_FAILED', {
        reason: amapMessage,
        diagnostics: latestDiagnostics,
      });
      throw new Error(`系统卫星定位、手机综合定位和地图定位都失败。卫星定位：${gpsProviderMessage}；综合定位：${nativeMessage}；地图定位：${amapMessage}${formatDiagnosticsSuffix(latestDiagnostics)}`);
    }
  }
}

export function formatDeviceLocationSource(fix: DeviceLocationFix) {
  const sourceLabel = fix.source.startsWith('android-gps-provider')
    ? '系统卫星定位'
    : fix.provider === 'amap'
      ? '高德定位'
      : fix.provider === 'android-native'
        ? '手机原生综合定位'
        : '兼容定位';
  const cacheLabel = fix.source.endsWith('-cache') ? '缓存' : '';
  const coordinateSystemLabel = fix.coordinateSystem === 'gcj02'
    ? '国测局坐标'
    : fix.coordinateSystem === 'wgs84'
      ? '国际通用坐标'
      : '未知坐标系';
  return `${sourceLabel}${cacheLabel}（${coordinateSystemLabel}）`;
}

export function formatDeviceLocationError(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message.trim();
  }
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = String((error as { message?: unknown }).message ?? '').trim();
    if (message) {
      return message;
    }
  }
  return '定位服务没有返回明确错误。';
}

export function formatLocationDiagnosticsSummary(diagnostics: DeviceLocationDiagnostics | null | undefined) {
  if (!diagnostics) {
    return '';
  }
  const permission = diagnostics.finePermissionGranted
    ? '精确定位已授权'
    : diagnostics.coarsePermissionGranted
      ? '仅检测到模糊定位权限'
      : '定位权限未授权';
  const service = diagnostics.locationServicesEnabled ? '定位总开关已开' : '定位总开关未开';
  const gps = diagnostics.gpsProviderEnabled ? '卫星定位已开' : '卫星定位未开';
  const network = diagnostics.networkProviderEnabled ? '网络定位已开' : '网络定位未开';
  const gpsCache = formatLastKnownDiagnostics('最近卫星定位', diagnostics.lastKnownGps);
  const networkCache = formatLastKnownDiagnostics('最近网络定位', diagnostics.lastKnownNetwork);
  const device = [diagnostics.manufacturer, diagnostics.model]
    .filter((part) => typeof part === 'string' && part.trim())
    .join(' ');
  const android = diagnostics.androidVersion ? `安卓 ${diagnostics.androidVersion}` : '';
  const deviceText = [device, android].filter(Boolean).join('，');
  return [
    `诊断：${permission}`,
    service,
    gps,
    network,
    gpsCache,
    networkCache,
    deviceText ? `设备：${deviceText}` : '',
  ].filter(Boolean).join('；');
}

async function getExpoLocationFallback(request: DeviceLocationRequest): Promise<DeviceLocationFix> {
  const position = await withTimeout(
    Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Highest,
      mayShowUserSettingsDialog: true,
      timeInterval: 1000,
      distanceInterval: 0,
    }),
    request.timeoutMs,
    '兼容定位获取超时，请到室外或窗边重试；如仍失败，请手动填写经纬度和地址。',
  );
  return {
    provider: 'expo-location',
    source: 'expo-location',
    coordinateSystem: 'wgs84',
    longitude: position.coords.longitude,
    latitude: position.coords.latitude,
    accuracy: position.coords.accuracy ?? undefined,
    timestamp: position.timestamp,
    rawProvider: 'expo-location',
  };
}

function formatDiagnosticsSuffix(diagnostics: DeviceLocationDiagnostics | null | undefined) {
  const summary = formatLocationDiagnosticsSummary(diagnostics);
  return summary ? `。${summary}` : '';
}

function formatLastKnownDiagnostics(label: string, diagnostics: LastKnownLocationDiagnostics | undefined) {
  if (!diagnostics?.available) {
    return `${label}无有效缓存`;
  }
  const parts = [`${label}有缓存`];
  if (typeof diagnostics.accuracy === 'number') {
    parts.push(`精度约${Math.round(diagnostics.accuracy)}米`);
  }
  if (typeof diagnostics.ageMs === 'number') {
    parts.push(`${formatAge(diagnostics.ageMs)}前`);
  }
  return parts.join('，');
}

function formatAge(ageMs: number) {
  if (ageMs < 60_000) {
    return `${Math.max(1, Math.round(ageMs / 1000))}秒`;
  }
  return `${Math.round(ageMs / 60_000)}分钟`;
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, timeoutMessage: string) {
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  const timeoutPromise = new Promise<T>((_, reject) => {
    timeoutId = setTimeout(() => reject(new Error(timeoutMessage)), timeoutMs);
  });
  return Promise.race([promise, timeoutPromise]).finally(() => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
  });
}

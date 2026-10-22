import Constants from 'expo-constants';
import { Platform } from 'react-native';
import * as Application from 'expo-application';
import * as Updates from 'expo-updates';

import { apiFetch } from '@/api/http';
import type { AppUpdateCheckResponse } from '@/types/api';

const DEFAULT_UPDATE_CHANNEL = 'stable';
const runtimeEnv = process.env as Record<string, string | undefined>;

export const APP_PACKAGE_ID = Application.applicationId || Constants.expoConfig?.android?.package || 'com.mnnu.ereport';
export const APP_VERSION_NAME = Application.nativeApplicationVersion || Constants.expoConfig?.version || '1.0.0';
export const APP_BUSINESS_VERSION = Constants.expoConfig?.version || APP_VERSION_NAME;
export const APP_VERSION_CODE = currentAndroidVersionCode();
export const APP_RUNTIME_VERSION = runtimeVersion();
export const APP_UPDATE_CHANNEL = runtimeEnv.EXPO_PUBLIC_APP_UPDATE_CHANNEL || DEFAULT_UPDATE_CHANNEL;

export async function checkAppUpdate() {
  const update = await apiFetch<AppUpdateCheckResponse>('/app-updates/latest', {
    skipAuth: true,
    timeoutMs: 45000,
    query: {
      platform: 'ANDROID',
      edition: 'NATIVE',
      channel: APP_UPDATE_CHANNEL,
      currentVersionCode: APP_VERSION_CODE,
      runtimeVersion: APP_RUNTIME_VERSION,
    },
  });
  if (update.edition === 'LITE') throw new Error('更新服务返回了其他客户端的安装包，请联系管理员。');
  return update;
}

function currentAndroidVersionCode() {
  if (Platform.OS !== 'android') {
    return 0;
  }
  const installedVersionCode = Number(Application.nativeBuildVersion);
  if (Number.isSafeInteger(installedVersionCode) && installedVersionCode > 0) return installedVersionCode;
  const platformConstants = Constants.platform as { android?: { versionCode?: number } } | null;
  const nativeVersionCode = platformConstants?.android?.versionCode;
  if (typeof nativeVersionCode === 'number' && Number.isFinite(nativeVersionCode)) {
    return nativeVersionCode;
  }
  const configuredVersionCode = Constants.expoConfig?.android?.versionCode;
  return typeof configuredVersionCode === 'number' && Number.isFinite(configuredVersionCode) ? configuredVersionCode : 0;
}

function runtimeVersion() {
  if (Updates.runtimeVersion) return Updates.runtimeVersion;
  const configuredRuntimeVersion = Constants.expoConfig?.runtimeVersion;
  if (typeof configuredRuntimeVersion === 'string' && configuredRuntimeVersion.trim()) {
    return configuredRuntimeVersion.trim();
  }
  return `android-${APP_VERSION_NAME}-${APP_VERSION_CODE}`;
}

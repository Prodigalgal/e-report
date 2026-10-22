import * as SecureStore from 'expo-secure-store';

import { clearStoredSession } from '@/auth/session-storage';
import { synchronizeOtaEnvironment } from '@/features/app-updates/ota-environment';
import {
  API_ENDPOINTS,
  apiEndpointsFor,
  DEFAULT_API_ENVIRONMENT,
  getApiBaseUrlOverrides,
  normalizeApiBaseUrl,
  setApiBaseUrlOverrides,
  type ApiBaseUrlOverrides,
  type ApiEnvironment,
} from '@/config/public-endpoints';

const API_ENVIRONMENT_KEY = 'e_report_api_environment_v1';
const API_SETTINGS_KEY = 'e_report_api_settings_v1';

type StoredApiSettings = {
  environment?: unknown;
  apiBaseUrlOverrides?: unknown;
};

let currentEnvironment = DEFAULT_API_ENVIRONMENT;
let initialized = false;
let initialization: Promise<ApiEnvironment> | null = null;
const environmentListeners = new Set<(environment: ApiEnvironment) => void>();

export function initializeApiEnvironment(): Promise<ApiEnvironment> {
  if (initialized) return Promise.resolve(currentEnvironment);
  if (!initialization) {
    initialization = Promise.all([
      SecureStore.getItemAsync(API_SETTINGS_KEY),
      SecureStore.getItemAsync(API_ENVIRONMENT_KEY),
    ])
      .then(async ([storedSettings, storedEnvironment]) => {
        const settings = parseStoredSettings(storedSettings);
        currentEnvironment = isApiEnvironment(settings.environment)
          ? settings.environment
          : isApiEnvironment(storedEnvironment)
            ? storedEnvironment
            : DEFAULT_API_ENVIRONMENT;
        setApiBaseUrlOverrides(settings.apiBaseUrlOverrides);
        await synchronizeOtaEnvironment(currentEnvironment, apiEndpointsFor(currentEnvironment).apiBaseUrl);
        initialized = true;
        return currentEnvironment;
      })
      .finally(() => {
        initialization = null;
      });
  }
  return initialization;
}

export function getCurrentApiEnvironment(): ApiEnvironment {
  return currentEnvironment;
}

export async function changeApiEnvironment(nextEnvironment: ApiEnvironment): Promise<boolean> {
  return updateApiEnvironmentSettings(nextEnvironment, getApiBaseUrlOverrides());
}

export async function updateApiEnvironmentSettings(
  nextEnvironment: ApiEnvironment,
  apiBaseUrlOverrides: ApiBaseUrlOverrides,
): Promise<boolean> {
  if (!isApiEnvironment(nextEnvironment)) {
    throw new Error('服务环境无效。');
  }
  await initializeApiEnvironment();

  const normalizedOverrides = normalizeApiBaseUrlOverrides(apiBaseUrlOverrides);
  const currentOverrides = getApiBaseUrlOverrides();
  const environmentChanged = nextEnvironment !== currentEnvironment;
  const endpointChanged = !sameApiBaseUrlOverrides(currentOverrides, normalizedOverrides);
  if (!environmentChanged && !endpointChanged) return false;

  await clearStoredSession();
  await SecureStore.setItemAsync(API_SETTINGS_KEY, JSON.stringify({
    environment: nextEnvironment,
    apiBaseUrlOverrides: normalizedOverrides,
  }));
  await SecureStore.deleteItemAsync(API_ENVIRONMENT_KEY).catch(() => undefined);

  setApiBaseUrlOverrides(normalizedOverrides);
  currentEnvironment = nextEnvironment;
  await synchronizeOtaEnvironment(nextEnvironment, apiEndpointsFor(nextEnvironment).apiBaseUrl);
  environmentListeners.forEach((listener) => listener(currentEnvironment));
  return true;
}

export function subscribeApiEnvironmentChanged(listener: (environment: ApiEnvironment) => void) {
  environmentListeners.add(listener);
  return () => environmentListeners.delete(listener);
}

function isApiEnvironment(value: unknown): value is ApiEnvironment {
  return value === 'production' || value === 'test';
}

function parseStoredSettings(value: string | null): {
  environment: ApiEnvironment | null;
  apiBaseUrlOverrides: ApiBaseUrlOverrides;
} {
  if (!value) return { environment: null, apiBaseUrlOverrides: {} };

  try {
    const parsed = JSON.parse(value) as StoredApiSettings;
    return {
      environment: isApiEnvironment(parsed.environment) ? parsed.environment : null,
      apiBaseUrlOverrides: normalizeApiBaseUrlOverrides(
        isRecord(parsed.apiBaseUrlOverrides)
          ? parsed.apiBaseUrlOverrides as Partial<Record<ApiEnvironment, unknown>>
          : {},
        true,
      ),
    };
  } catch {
    return { environment: null, apiBaseUrlOverrides: {} };
  }
}

function normalizeApiBaseUrlOverrides(
  overrides: Partial<Record<ApiEnvironment, unknown>>,
  ignoreInvalid = false,
): ApiBaseUrlOverrides {
  const normalized: ApiBaseUrlOverrides = {};
  for (const environment of ['production', 'test'] as const) {
    const value = overrides[environment];
    if (typeof value !== 'string' || !value.trim()) continue;

    try {
      const apiBaseUrl = normalizeApiBaseUrl(value);
      if (apiBaseUrl && apiBaseUrl !== API_ENDPOINTS[environment].apiBaseUrl) {
        normalized[environment] = apiBaseUrl;
      }
    } catch (error) {
      if (!ignoreInvalid) throw error;
    }
  }
  return normalized;
}

function sameApiBaseUrlOverrides(left: ApiBaseUrlOverrides, right: ApiBaseUrlOverrides) {
  return (['production', 'test'] as const).every((environment) => (
    left[environment] === right[environment]
  ));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

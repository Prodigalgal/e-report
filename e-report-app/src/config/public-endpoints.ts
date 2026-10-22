const DEFAULT_PUBLIC_ORIGIN = 'https://e-report.fantong.eu.org:60443';
const DEFAULT_TEST_PUBLIC_ORIGIN = 'https://e-report-test.fantong.eu.org:60443';
const DEFAULT_FALLBACK_ORIGIN = '';

export type ApiEnvironment = 'production' | 'test';

export type ApiEndpointConfiguration = {
  environment: ApiEnvironment;
  label: string;
  publicOrigin: string;
  apiBaseUrl: string;
  fallbackApiBaseUrl: string;
  androidDownloadUrl: string;
};

export type ApiBaseUrlOverrides = Partial<Record<ApiEnvironment, string>>;

const productionOrigin = normalizeBaseUrl(process.env.EXPO_PUBLIC_ORIGIN || DEFAULT_PUBLIC_ORIGIN);
const productionFallbackOrigin = normalizeBaseUrl(
  process.env.EXPO_PUBLIC_FALLBACK_ORIGIN || DEFAULT_FALLBACK_ORIGIN,
);
const testOrigin = normalizeBaseUrl(process.env.EXPO_PUBLIC_TEST_ORIGIN || DEFAULT_TEST_PUBLIC_ORIGIN);
const testFallbackOrigin = normalizeBaseUrl(process.env.EXPO_PUBLIC_TEST_FALLBACK_ORIGIN || '');

export const DEFAULT_API_ENVIRONMENT: ApiEnvironment = 'production';

export const API_ENDPOINTS: Readonly<Record<ApiEnvironment, ApiEndpointConfiguration>> = {
  production: {
    environment: 'production',
    label: '生产环境',
    publicOrigin: productionOrigin,
    apiBaseUrl: normalizeBaseUrl(process.env.EXPO_PUBLIC_API_BASE_URL || `${productionOrigin}/api`),
    fallbackApiBaseUrl: normalizeBaseUrl(
      process.env.EXPO_PUBLIC_API_FALLBACK_BASE_URL
        || (productionFallbackOrigin ? `${productionFallbackOrigin}/api` : ''),
    ),
    androidDownloadUrl: `${productionOrigin}/download/android`,
  },
  test: {
    environment: 'test',
    label: '测试环境',
    publicOrigin: testOrigin,
    apiBaseUrl: normalizeBaseUrl(process.env.EXPO_PUBLIC_TEST_API_BASE_URL || `${testOrigin}/api`),
    fallbackApiBaseUrl: normalizeBaseUrl(
      process.env.EXPO_PUBLIC_TEST_API_FALLBACK_BASE_URL
        || (testFallbackOrigin ? `${testFallbackOrigin}/api` : ''),
    ),
    androidDownloadUrl: `${testOrigin}/download/android`,
  },
};

export const PUBLIC_ORIGIN = API_ENDPOINTS.production.publicOrigin;
export const PRIMARY_API_BASE_URL = API_ENDPOINTS.production.apiBaseUrl;
export const FALLBACK_API_BASE_URL = API_ENDPOINTS.production.fallbackApiBaseUrl;
export const ANDROID_DOWNLOAD_URL = API_ENDPOINTS.production.androidDownloadUrl;

let customApiBaseUrlOverrides: ApiBaseUrlOverrides = {};

export function apiEndpointsFor(environment: ApiEnvironment): ApiEndpointConfiguration {
  const defaults = API_ENDPOINTS[environment];
  const apiBaseUrl = customApiBaseUrlOverrides[environment];
  if (!apiBaseUrl) return defaults;

  const publicOrigin = new URL(apiBaseUrl).origin;
  return {
    ...defaults,
    publicOrigin,
    apiBaseUrl,
    fallbackApiBaseUrl: '',
    androidDownloadUrl: `${publicOrigin}/download/android`,
  };
}

export function normalizeApiBaseUrl(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return '';
  if (trimmed.length > 512) {
    throw new Error('服务 API 地址不能超过 512 个字符。');
  }

  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    throw new Error('请输入有效的 HTTPS 服务 API 地址。');
  }

  if (parsed.protocol !== 'https:') {
    throw new Error('服务 API 地址必须使用 HTTPS。');
  }
  if (!parsed.hostname || parsed.username || parsed.password) {
    throw new Error('服务 API 地址不能包含账号或密码。');
  }
  if (parsed.search || parsed.hash) {
    throw new Error('服务 API 地址不能包含查询参数或片段。');
  }

  const pathname = parsed.pathname.replace(/\/+$/, '');
  return `${parsed.origin}${pathname === '/' ? '' : pathname}`;
}

export function setApiBaseUrlOverrides(overrides: ApiBaseUrlOverrides) {
  const nextOverrides: ApiBaseUrlOverrides = {};
  for (const environment of ['production', 'test'] as const) {
    const value = overrides[environment];
    if (value === undefined) continue;
    const normalized = normalizeApiBaseUrl(value);
    if (normalized && normalized !== API_ENDPOINTS[environment].apiBaseUrl) {
      nextOverrides[environment] = normalized;
    }
  }
  customApiBaseUrlOverrides = nextOverrides;
}

export function getApiBaseUrlOverrides(): ApiBaseUrlOverrides {
  return { ...customApiBaseUrlOverrides };
}

function normalizeBaseUrl(value: string) {
  return value.trim().replace(/\/+$/, '');
}

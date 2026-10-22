import assert from 'node:assert/strict';
import test from 'node:test';

import {
  ANDROID_DOWNLOAD_URL,
  API_ENDPOINTS,
  FALLBACK_API_BASE_URL,
  PRIMARY_API_BASE_URL,
  PUBLIC_ORIGIN,
  apiEndpointsFor,
  getApiBaseUrlOverrides,
  normalizeApiBaseUrl,
  setApiBaseUrlOverrides,
} from '../src/config/public-endpoints.ts';

test('APP 默认配置生产环境，保留既有主线路和下载地址', () => {
  assert.equal(PUBLIC_ORIGIN, 'https://e-report.fantong.eu.org:60443');
  assert.equal(PRIMARY_API_BASE_URL, 'https://e-report.fantong.eu.org:60443/api');
  assert.equal(ANDROID_DOWNLOAD_URL, 'https://e-report.fantong.eu.org:60443/download/android');
  assert.equal(FALLBACK_API_BASE_URL, '');
});

test('单个 APK 可选择测试或生产 API、下载地址与线路配置', () => {
  assert.equal(apiEndpointsFor('test').apiBaseUrl, 'https://e-report-test.fantong.eu.org:60443/api');
  assert.equal(apiEndpointsFor('test').androidDownloadUrl, 'https://e-report-test.fantong.eu.org:60443/download/android');
  assert.equal(apiEndpointsFor('test').fallbackApiBaseUrl, '');
  assert.equal(API_ENDPOINTS.production.environment, 'production');
  assert.equal(API_ENDPOINTS.test.environment, 'test');
});

test('规范化自定义 HTTPS API 地址并拒绝不安全地址', () => {
  assert.equal(normalizeApiBaseUrl('  https://api.example.com/api///  '), 'https://api.example.com/api');
  assert.equal(normalizeApiBaseUrl(''), '');
  assert.throws(() => normalizeApiBaseUrl('http://api.example.com/api'), /必须使用 HTTPS/);
  assert.throws(() => normalizeApiBaseUrl('https://user:secret@api.example.com/api'), /不能包含账号或密码/);
  assert.throws(() => normalizeApiBaseUrl('https://api.example.com/api?token=secret'), /不能包含查询参数或片段/);
  assert.throws(() => normalizeApiBaseUrl('https://api.example.com/api#login'), /不能包含查询参数或片段/);
});

test('按环境应用自定义 API 地址，恢复默认后沿用内置线路配置', () => {
  const previousOverrides = getApiBaseUrlOverrides();
  try {
    setApiBaseUrlOverrides({
      production: 'https://prod.example.net/api/',
      test: 'https://test.example.net/api',
    });
    assert.equal(apiEndpointsFor('production').apiBaseUrl, 'https://prod.example.net/api');
    assert.equal(apiEndpointsFor('production').fallbackApiBaseUrl, '');
    assert.equal(apiEndpointsFor('production').androidDownloadUrl, 'https://prod.example.net/download/android');
    assert.equal(apiEndpointsFor('test').apiBaseUrl, 'https://test.example.net/api');

    setApiBaseUrlOverrides({ production: API_ENDPOINTS.production.apiBaseUrl });
    assert.equal(apiEndpointsFor('production').apiBaseUrl, API_ENDPOINTS.production.apiBaseUrl);
    assert.equal(apiEndpointsFor('production').fallbackApiBaseUrl, API_ENDPOINTS.production.fallbackApiBaseUrl);
    assert.equal(apiEndpointsFor('test').apiBaseUrl, API_ENDPOINTS.test.apiBaseUrl);
  } finally {
    setApiBaseUrlOverrides(previousOverrides);
  }
});

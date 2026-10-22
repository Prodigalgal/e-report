#!/usr/bin/env node

import { createHash } from 'node:crypto';

import {
  assertProductionReadOnlyTarget,
  assertReadOnlyBusinessMethod,
} from './lib/e2e-environment-guard.mjs';

const baseUrl = assertProductionReadOnlyTarget(process.env.E_REPORT_PRODUCTION_BASE_URL);
const adminUsername = process.env.E_REPORT_PRODUCTION_ADMIN_USERNAME;
const adminPassword = process.env.E_REPORT_PRODUCTION_ADMIN_PASSWORD;
const clientInstanceId = `production-read-only-smoke-${process.pid}`;
const webOrigin = new URL(baseUrl).origin;
const webFingerprintHash = createHash('sha256')
  .update('e-report-production-read-only-smoke:web-env-v1')
  .digest('hex');
const webEnvironment = Object.freeze({
  schemaVersion: 1,
  fingerprintHash: webFingerprintHash,
  browserName: 'e-report-production-read-only-smoke',
  browserVersion: '1.0',
  osName: process.platform,
  osVersion: process.version,
  deviceName: '生产只读巡检工具',
  manufacturer: '',
  model: 'Node.js',
  localeTag: Intl.DateTimeFormat().resolvedOptions().locale,
  timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  appVersion: 'production-read-only-smoke-1.0',
  canvasHash: '',
  webglHash: '',
  audioHash: '',
  fontHash: '',
  securitySignals: [],
});

if (!adminUsername || !adminPassword) {
  throw new Error('必须通过 E_REPORT_PRODUCTION_ADMIN_USERNAME 和 E_REPORT_PRODUCTION_ADMIN_PASSWORD 提供生产巡检账号。');
}

const checks = [];
const businessRequests = [];

class ApiError extends Error {
  constructor(message, status, payload) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.payload = payload;
  }
}

function check(condition, message, details) {
  if (!condition) {
    throw new Error(`${message}${details === undefined ? '' : `: ${JSON.stringify(details)}`}`);
  }
  checks.push(message);
  console.log(`[通过] ${message}`);
}

async function parseResponse(response, method) {
  const responseText = method === 'HEAD' ? '' : await response.text();
  if (!responseText) {
    return '';
  }
  try {
    return JSON.parse(responseText);
  } catch {
    return responseText;
  }
}

async function readApi(path, { sessionCookie, method = 'GET', expected = [200], redirect = 'follow' } = {}) {
  const safeMethod = assertReadOnlyBusinessMethod(method);
  businessRequests.push(`${safeMethod} ${path}`);
  const headers = { Accept: 'application/json' };
  if (sessionCookie) {
    headers.Cookie = sessionCookie;
  }
  const response = await fetch(`${baseUrl}${path}`, {
    method: safeMethod,
    headers,
    redirect,
    signal: AbortSignal.timeout(45_000),
  });
  const payload = await parseResponse(response, safeMethod);
  if (!expected.includes(response.status)) {
    throw new ApiError(`${safeMethod} ${path} 返回 HTTP ${response.status}`, response.status, payload);
  }
  return { status: response.status, headers: response.headers, payload };
}

function captchaAnswer(question) {
  const match = question.match(/(\d+)\s*([+-])\s*(\d+)/);
  if (!match) {
    throw new Error(`无法解析数学验证码: ${question}`);
  }
  const left = Number(match[1]);
  const right = Number(match[3]);
  return String(match[2] === '+' ? left + right : left - right);
}

function optionalCaptchaAnswer(challenge) {
  return challenge.captchaRequired ? captchaAnswer(challenge.captchaQuestion) : null;
}

function solvePow(challenge) {
  const target = '0'.repeat(Number(challenge.powDifficulty) || 0);
  for (let nonce = 0; nonce < Number.MAX_SAFE_INTEGER; nonce += 1) {
    const hash = createHash('sha256')
      .update(`${challenge.challengeId}:${challenge.powPrefix}:${nonce}`)
      .digest('hex');
    if (hash.startsWith(target)) {
      return String(nonce);
    }
  }
  throw new Error('安全挑战计算失败。');
}

async function login() {
  const challengeResponse = await fetch(`${baseUrl}/auth/challenge`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Origin: webOrigin,
      'X-Client-Platform': 'WEB',
    },
    body: JSON.stringify({
      username: adminUsername,
      clientType: 'WEB',
      clientInstanceId,
      webFingerprintHash,
      webSecuritySignals: webEnvironment.securitySignals,
    }),
    signal: AbortSignal.timeout(45_000),
  });
  const challenge = await parseResponse(challengeResponse, 'POST');
  if (challengeResponse.status !== 200) {
    throw new ApiError(`POST /auth/challenge 返回 HTTP ${challengeResponse.status}`, challengeResponse.status, challenge);
  }
  const response = await fetch(`${baseUrl}/auth/login`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Origin: webOrigin,
      'X-Client-Platform': 'WEB',
    },
    body: JSON.stringify({
      username: adminUsername,
      password: adminPassword,
      challengeId: challenge.challengeId,
      captchaAnswer: optionalCaptchaAnswer(challenge),
      powNonce: solvePow(challenge),
      clientType: 'WEB',
      clientInstanceId,
      webEnvironment,
    }),
    signal: AbortSignal.timeout(45_000),
  });
  const payload = await parseResponse(response, 'POST');
  if (response.status !== 200) {
    throw new ApiError(`POST /auth/login 返回 HTTP ${response.status}`, response.status, payload);
  }
  const sessionCookie = response.headers.get('set-cookie')?.split(';', 1)[0];
  check(Boolean(sessionCookie), '生产巡检账号登录成功');
  check(payload.passwordChangeRequired === false, '生产巡检账号密码状态正常');
  return { payload, sessionCookie };
}

async function logout(sessionCookie) {
  const csrfResponse = await fetch(`${baseUrl}/auth/csrf`, {
    headers: { Accept: 'application/json', Cookie: sessionCookie },
    signal: AbortSignal.timeout(45_000),
  });
  const csrf = await parseResponse(csrfResponse, 'GET');
  if (csrfResponse.status !== 200) {
    throw new ApiError(`GET /auth/csrf 返回 HTTP ${csrfResponse.status}`, csrfResponse.status, csrf);
  }
  const csrfCookie = csrfResponse.headers.get('set-cookie')?.split(';', 1)[0];
  check(Boolean(csrfCookie) && Boolean(csrf.token), '生产巡检会话 CSRF 保护正常');
  const response = await fetch(`${baseUrl}/auth/logout`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Cookie: `${sessionCookie}; ${csrfCookie}`,
      Origin: webOrigin,
      'X-Client-Platform': 'WEB',
      [csrf.headerName]: csrf.token,
    },
    body: '{}',
    signal: AbortSignal.timeout(45_000),
  });
  if (response.status !== 204) {
    throw new ApiError(`POST /auth/logout 返回 HTTP ${response.status}`, response.status, await parseResponse(response, 'POST'));
  }
  check(true, '生产巡检会话已主动注销');
}

async function main() {
  console.log('[开始] 生产只读 smoke');
  const session = await login();
  const { sessionCookie } = session;

  try {

  const me = (await readApi('/auth/me', { sessionCookie })).payload;
  check(me.username === adminUsername, '当前账号身份读取正常');

  const users = (await readApi('/admin/users?page=0&size=1&sort=username,asc', { sessionCookie })).payload;
  check(Array.isArray(users.content), '用户分页读取正常');

  const orgUnits = (await readApi('/admin/org-units', { sessionCookie })).payload;
  check(Array.isArray(orgUnits) && orgUnits.length > 0, '组织树读取正常');

  const profiles = (await readApi('/admin/authorization-profiles', { sessionCookie })).payload;
  check(Array.isArray(profiles), '岗位模板列表读取正常');

  const statistics = (await readApi('/work-orders/statistics', { sessionCookie })).payload;
  check(statistics && typeof statistics === 'object', '工单仪表盘统计读取正常');

  const workOrders = (await readApi('/work-orders/search?page=0&size=1&sort=reportTime,desc', { sessionCookie })).payload;
  check(Array.isArray(workOrders.content), '工单分页读取正常');
  if (workOrders.content.length > 0) {
    const workOrderId = workOrders.content[0].id;
    const detail = (await readApi(`/work-orders/${workOrderId}`, { sessionCookie })).payload;
    check(detail.id === workOrderId, '工单详情读取正常');
  }

  const update = (await readApi(
    '/app-updates/latest?platform=ANDROID&channel=stable&currentVersionCode=38&runtimeVersion=android-1.0.37-native38',
  )).payload;
  check(update.platform === 'ANDROID', 'APP 更新检查读取正常');

  const download = await readApi('/app-updates/latest/apk?platform=ANDROID&channel=stable', {
    method: 'HEAD',
    expected: [302],
    redirect: 'manual',
  });
  check(Boolean(download.headers.get('location')), '统一 APK 下载入口返回 R2 重定向');

  check(businessRequests.every((request) => /^(GET|HEAD) /.test(request)), '全部业务巡检请求均为 GET/HEAD');
  } finally {
    await logout(sessionCookie);
  }
  console.log(`READ_ONLY_SMOKE_SUMMARY=${JSON.stringify({
    baseUrl,
    passed: true,
    checkCount: checks.length,
    requestCount: businessRequests.length,
    businessRequests,
  })}`);
}

await main();

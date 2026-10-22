#!/usr/bin/env node

import { createHash, randomUUID } from 'node:crypto';

const baseUrl = String(process.env.EREPORT_BASE_URL || 'https://e-report-test.fantong.eu.org:60443/api')
  .replace(/\/$/, '');
const origin = new URL(baseUrl).origin;
const username = process.env.EREPORT_TEST_USERNAME;
const password = process.env.EREPORT_TEST_PASSWORD;
const sequentialSamples = positiveInteger(process.env.EREPORT_SEQUENTIAL_SAMPLES, 30);
const concurrentRequests = positiveInteger(process.env.EREPORT_CONCURRENT_REQUESTS, 100);
const concurrency = positiveInteger(process.env.EREPORT_CONCURRENCY, 20);
const endpointNameFilter = String(process.env.EREPORT_ENDPOINT || '').trim();

if (!username || !password) {
  throw new Error('必须通过 EREPORT_TEST_USERNAME 和 EREPORT_TEST_PASSWORD 提供测试账号。');
}

const commonHeaders = {
  Origin: origin,
  Referer: `${origin}/`,
  'Content-Type': 'application/json',
  'X-Client-Platform': 'WEB',
  'X-Client-App-Version': '2.2.0',
  'X-Client-Network-Type': 'baseline',
};

const loginMetrics = await createAuthenticatedSession();
const authenticatedHeaders = {
  ...commonHeaders,
  Cookie: loginMetrics.cookie,
};
const endpoints = [
  { name: 'auth.me', path: '/auth/me' },
  { name: 'admin.users', path: '/admin/users?page=0&size=10&sort=username,asc' },
  { name: 'admin.orgUnits', path: '/admin/org-units' },
  { name: 'admin.authorizationProfileSummaries', path: '/admin/authorization-profiles/summaries' },
  { name: 'admin.authorizationProfiles', path: '/admin/authorization-profiles' },
  { name: 'workOrders.search', path: '/work-orders/search?page=0&size=20&sort=reportTime,desc' },
  { name: 'workOrders.statisticsScopes', path: '/work-orders/statistics/scopes' },
  { name: 'workOrders.statistics', path: '/work-orders/statistics' },
  { name: 'userTransfers.exports', path: '/admin/user-transfers?jobType=EXPORT&page=0&size=20&sort=createdAt,desc' },
].filter((endpoint) => !endpointNameFilter || endpoint.name === endpointNameFilter);

if (endpoints.length === 0) {
  throw new Error(`没有匹配的压测端点：${endpointNameFilter}`);
}

const endpointResults = [];
for (const endpoint of endpoints) {
  for (let warmup = 0; warmup < 5; warmup += 1) {
    await timedGet(endpoint.path);
  }
  const sequential = [];
  for (let sample = 0; sample < sequentialSamples; sample += 1) {
    sequential.push(await timedGet(endpoint.path));
  }
  const concurrent = await runConcurrent(endpoint.path, concurrentRequests, concurrency);
  endpointResults.push({
    name: endpoint.name,
    path: endpoint.path,
    sequential: summarize(sequential),
    concurrent: summarize(concurrent),
  });
}

console.log(JSON.stringify({
  capturedAt: new Date().toISOString(),
  baseUrl,
  sequentialSamples,
  concurrentRequests,
  concurrency,
  login: {
    challengeMs: loginMetrics.challengeMs,
    powMs: loginMetrics.powMs,
    loginMs: loginMetrics.loginMs,
    csrfMs: loginMetrics.csrfMs,
    powDifficulty: loginMetrics.powDifficulty,
    captchaRequired: loginMetrics.captchaRequired,
    permissionCount: loginMetrics.permissionCount,
  },
  endpoints: endpointResults,
}, null, 2));

async function createAuthenticatedSession() {
  const clientInstanceId = `performance-${randomUUID()}`;
  const fingerprintHash = createHash('sha256').update(clientInstanceId).digest('hex');
  const challengeStartedAt = performance.now();
  const challengeResponse = await fetchJson('/auth/challenge', {
    method: 'POST',
    body: JSON.stringify({
      username,
      clientType: 'WEB',
      clientInstanceId,
      webFingerprintHash: fingerprintHash,
      webSecuritySignals: [],
    }),
  });
  const challengeMs = performance.now() - challengeStartedAt;

  const powStartedAt = performance.now();
  const powNonce = solvePow(challengeResponse.body);
  const powMs = performance.now() - powStartedAt;
  const captchaAnswer = solveCaptcha(challengeResponse.body);
  const loginStartedAt = performance.now();
  const loginResponse = await fetchJson('/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      username,
      password,
      challengeId: challengeResponse.body.challengeId,
      captchaAnswer,
      powNonce,
      clientType: 'WEB',
      clientInstanceId,
      webEnvironment: {
        schemaVersion: 1,
        fingerprintHash,
        browserName: 'Node Performance Baseline',
        browserVersion: process.version,
        osName: process.platform,
        localeTag: 'zh-CN',
        timeZone: 'Asia/Shanghai',
        appVersion: '2.2.0',
        securitySignals: [],
      },
    }),
  });
  const loginMs = performance.now() - loginStartedAt;
  const cookie = extractSessionCookie(loginResponse.response.headers);
  const csrfStartedAt = performance.now();
  await fetchJson('/auth/csrf', { headers: { Cookie: cookie } });
  const csrfMs = performance.now() - csrfStartedAt;
  return {
    cookie,
    challengeMs,
    powMs,
    loginMs,
    csrfMs,
    powDifficulty: challengeResponse.body.powDifficulty,
    captchaRequired: challengeResponse.body.captchaRequired,
    permissionCount: Array.isArray(loginResponse.body.permissions) ? loginResponse.body.permissions.length : 0,
  };
}

async function fetchJson(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: { ...commonHeaders, ...(options.headers || {}) },
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${path} -> ${response.status}: ${text.slice(0, 500)}`);
  }
  return { response, body: text ? JSON.parse(text) : null };
}

async function timedGet(path) {
  const startedAt = performance.now();
  try {
    const response = await fetch(`${baseUrl}${path}`, { headers: authenticatedHeaders });
    const content = await response.arrayBuffer();
    return {
      durationMs: performance.now() - startedAt,
      status: response.status,
      bytes: content.byteLength,
      ok: response.ok,
    };
  } catch (error) {
    const cause = error instanceof Error ? error.cause : null;
    const causeCode = cause && typeof cause === 'object' && 'code' in cause ? String(cause.code) : '';
    const causeMessage = cause instanceof Error ? cause.message : '';
    return {
      durationMs: performance.now() - startedAt,
      status: 0,
      bytes: 0,
      ok: false,
      error: [error instanceof Error ? error.message : String(error), causeCode, causeMessage]
        .filter(Boolean)
        .join(' | '),
    };
  }
}

async function runConcurrent(path, requestCount, maximumConcurrency) {
  const results = [];
  let nextIndex = 0;
  const workers = Array.from({ length: Math.min(maximumConcurrency, requestCount) }, async () => {
    while (nextIndex < requestCount) {
      nextIndex += 1;
      results.push(await timedGet(path));
    }
  });
  await Promise.all(workers);
  return results;
}

function solvePow(challenge) {
  const prefix = '0'.repeat(Number(challenge.powDifficulty) || 0);
  for (let nonce = 0; nonce < Number.MAX_SAFE_INTEGER; nonce += 1) {
    const digest = createHash('sha256')
      .update(`${challenge.challengeId}:${challenge.powPrefix}:${nonce}`)
      .digest('hex');
    if (digest.startsWith(prefix)) return String(nonce);
  }
  throw new Error('无法完成登录 PoW。');
}

function solveCaptcha(challenge) {
  if (!challenge.captchaRequired) return null;
  const match = /^(\d+)\s*([+-])\s*(\d+)/.exec(challenge.captchaQuestion || '');
  if (!match) throw new Error(`不支持的验证码题目：${challenge.captchaQuestion}`);
  const left = Number(match[1]);
  const right = Number(match[3]);
  return String(match[2] === '+' ? left + right : left - right);
}

function extractSessionCookie(headers) {
  const setCookies = typeof headers.getSetCookie === 'function'
    ? headers.getSetCookie()
    : [headers.get('set-cookie')].filter(Boolean);
  const sessionCookie = setCookies.find((value) => value.startsWith('EREPORT_SESSION='));
  if (!sessionCookie) throw new Error('登录响应没有返回 WEB 会话 Cookie。');
  return sessionCookie.split(';', 1)[0];
}

function summarize(samples) {
  const durations = samples.map((sample) => sample.durationMs).sort((left, right) => left - right);
  const successful = samples.filter((sample) => sample.ok);
  const errorMessages = Object.fromEntries(Object.entries(Object.groupBy(
    samples.filter((sample) => sample.error),
    (sample) => sample.error,
  )).map(([message, values]) => [message, values.length]));
  return {
    count: samples.length,
    success: successful.length,
    errors: samples.length - successful.length,
    errorRate: round((samples.length - successful.length) / samples.length),
    p50Ms: percentile(durations, 0.50),
    p95Ms: percentile(durations, 0.95),
    p99Ms: percentile(durations, 0.99),
    averageMs: round(durations.reduce((sum, duration) => sum + duration, 0) / durations.length),
    minimumMs: round(durations[0]),
    maximumMs: round(durations.at(-1)),
    responseBytes: successful[0]?.bytes || 0,
    statuses: Object.fromEntries(Object.entries(Object.groupBy(samples, (sample) => String(sample.status)))
      .map(([status, values]) => [status, values.length])),
    errorMessages,
  };
}

function percentile(sortedValues, quantile) {
  if (sortedValues.length === 0) return 0;
  const index = Math.max(0, Math.ceil(sortedValues.length * quantile) - 1);
  return round(sortedValues[index]);
}

function positiveInteger(value, fallback) {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function round(value) {
  return Math.round(value * 100) / 100;
}

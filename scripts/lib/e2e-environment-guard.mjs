const PRODUCTION_HOSTS = new Set([
  'e-report.fantong.eu.org',
  '36.212.129.231',
]);

const ISOLATION_DISABLED_FLAGS = Object.freeze([
  'notificationDeliveryEnabled',
  'notificationCenterEnabled',
  'emailEnabled',
  'weComEnabled',
  'dingTalkEnabled',
  'dailyOutstandingReportEnabled',
  'outboxEnqueueEnabled',
  'outboxDeliveryEnabled',
  'dailyOutstandingReportSchedulerEnabled',
]);

function parseExplicitUrl(rawValue, environmentName) {
  if (!rawValue || !rawValue.trim()) {
    throw new Error(`${environmentName} URL 必须通过环境变量显式提供。`);
  }
  let url;
  try {
    url = new URL(rawValue.trim());
  } catch (error) {
    throw new Error(`${environmentName} URL 无效。`, { cause: error });
  }
  if (!['http:', 'https:'].includes(url.protocol)) {
    throw new Error(`${environmentName} URL 只允许使用 HTTP 或 HTTPS。`);
  }
  if (url.username || url.password) {
    throw new Error(`${environmentName} URL 不允许携带用户名或密码。`);
  }
  return url;
}

function normalizedApiBaseUrl(url) {
  return url.href.replace(/\/$/, '');
}

export function assertIsolatedE2eTarget(rawValue) {
  const url = parseExplicitUrl(rawValue, '隔离 E2E');
  if (PRODUCTION_HOSTS.has(url.hostname.toLowerCase())) {
    throw new Error(`写入型 E2E 禁止连接生产主机 ${url.hostname}。`);
  }
  return normalizedApiBaseUrl(url);
}

export function assertProductionReadOnlyTarget(rawValue) {
  const url = parseExplicitUrl(rawValue, '生产只读巡检');
  if (!PRODUCTION_HOSTS.has(url.hostname.toLowerCase())) {
    throw new Error(`生产只读巡检只允许连接已登记生产主机，当前为 ${url.hostname}。`);
  }
  return normalizedApiBaseUrl(url);
}

export function assertIsolationReady(status) {
  if (!status || typeof status !== 'object' || Array.isArray(status)) {
    throw new Error('隔离能力响应格式无效。');
  }
  const unsafeFlags = ISOLATION_DISABLED_FLAGS.filter((flag) => status[flag] !== false);
  if (status.ready !== true || unsafeFlags.length > 0) {
    throw new Error(`隔离环境未关闭全部外部副作用：${unsafeFlags.join(', ') || 'ready=false'}`);
  }
}

export function assertReadOnlyBusinessMethod(method) {
  const normalizedMethod = String(method || 'GET').toUpperCase();
  if (!['GET', 'HEAD'].includes(normalizedMethod)) {
    throw new Error(`生产业务巡检禁止使用 ${normalizedMethod} 请求。`);
  }
  return normalizedMethod;
}

const SENSITIVE_KEY_PARTS = [
  'password', 'passwd', 'token', 'authorization', 'cookie', 'captcha', 'phone', 'email', 'address',
    'latitude', 'longitude', 'androidid', 'imei', 'serial', 'macaddress', 'advertisingid', 'gaid', 'idfa',
    'bssid', 'ssid', 'ipaddress', 'gateway', 'subnet',
];

export function sanitizeTelemetryText(value: unknown, maximumLength = 2000) {
  if (value === undefined || value === null || value === '') return '';
  return String(value)
    .replace(/bearer\s+[a-z0-9._~+/=-]+/gi, 'Bearer [已脱敏]')
    .replace(/(?<![a-z0-9_-])[a-z0-9_-]{10,}\.[a-z0-9_-]{10,}\.[a-z0-9_-]{10,}(?![a-z0-9_-])/gi, '[令牌已脱敏]')
    .replace(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/gi, '[邮箱已脱敏]')
    .replace(/(?<!\d)1[3-9]\d{9}(?!\d)/g, '[手机号已脱敏]')
    .replace(/\b(imei|android[_ -]?id|serial(?: number)?|mac(?: address)?)\s*[:=]\s*[^,;\s]+/gi, '$1=[已移除]')
    .replace(/(?<![0-9a-f])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f])/gi, '[MAC已移除]')
    .replace(/\b(latitude|longitude|lat|lng)\s*[:=]\s*-?\d{1,3}(?:\.\d+)?/gi, '$1=[坐标已脱敏]')
    .trim()
    .slice(0, maximumLength);
}

export function sanitizeTelemetryValue(value: unknown, depth = 0): unknown {
  if (value === undefined || value === null) return value;
  if (depth >= 6) return '[层级已截断]';
  if (Array.isArray(value)) return value.slice(0, 50).map((item) => sanitizeTelemetryValue(item, depth + 1));
  if (typeof value === 'object') {
    const sanitized: Record<string, unknown> = {};
    Object.entries(value as Record<string, unknown>).slice(0, 50).forEach(([key, item]) => {
      sanitized[key] = isSensitiveKey(key) ? '[已脱敏]' : sanitizeTelemetryValue(item, depth + 1);
    });
    return sanitized;
  }
  if (typeof value === 'number' || typeof value === 'boolean') return value;
  return sanitizeTelemetryText(value, 1000);
}

export function sanitizeTelemetryAttributes(value: Record<string, unknown> | undefined) {
  return (sanitizeTelemetryValue(value || {}) || {}) as Record<string, unknown>;
}

export function sanitizeTelemetryRoute(value: unknown) {
  return sanitizeTelemetryText(value, 255).split(/[?#]/)[0];
}

export function normalizeTelemetryCode(value: unknown, fallback: string, maximumLength: number) {
  const normalized = String(value || fallback).toUpperCase().replace(/[^A-Z0-9_.:-]/g, '_');
  return normalized.slice(0, maximumLength) || fallback;
}

function isSensitiveKey(key: string) {
  const normalized = key.toLowerCase().replace(/[-_]/g, '');
  return SENSITIVE_KEY_PARTS.some((part) => normalized.includes(part.replace(/[-_]/g, '')))
    || normalized === 'lat'
    || normalized === 'lng'
    || normalized === 'mac';
}

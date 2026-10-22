export function buildMobileUserAgent(appVersion: unknown, osVersion: unknown): string {
  const normalizedAppVersion = normalizeToken(appVersion, 'unknown');
  const normalizedOsVersion = normalizeToken(osVersion, 'unknown');
  return `e-report-app/${normalizedAppVersion} Android/${normalizedOsVersion}`;
}

function normalizeToken(value: unknown, fallback: string): string {
  const normalized = String(value ?? '').trim().replace(/\s+/g, '_');
  return normalized || fallback;
}

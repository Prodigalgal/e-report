const NON_REFRESHABLE_AUTH_PATHS = new Set([
  '/auth/challenge',
  '/auth/login',
  '/auth/refresh',
]);

export function shouldRefreshAfterUnauthorized(path: string, hasCredentials: boolean): boolean {
  if (!hasCredentials) return false;
  const normalizedPath = `/${path.split('?')[0].split('#')[0].replace(/^\/+/, '')}`;
  return !NON_REFRESHABLE_AUTH_PATHS.has(normalizedPath);
}

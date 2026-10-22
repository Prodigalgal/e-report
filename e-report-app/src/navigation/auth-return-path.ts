import type { Href } from 'expo-router';

export function resolveAuthReturnPath(value: string | string[] | undefined): Href {
  const candidate = Array.isArray(value) ? value[0] : value;
  if (!candidate || !candidate.startsWith('/') || candidate.startsWith('/login')) {
    return '/';
  }
  return candidate as Href;
}

export function resolveUnauthenticatedReturnPath(pathname: string, explicitlyLoggedOut: boolean): string {
  if (explicitlyLoggedOut || pathname === '/') {
    return '/';
  }
  return pathname;
}

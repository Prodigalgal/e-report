import { apiFetch } from '@/api/http';
import type { ApiTokenCreated, ApiTokenPermissionOption, ApiTokenView } from '@/types/api';

export function fetchApiTokens() {
  return apiFetch<ApiTokenView[]>('/api-tokens');
}

export function fetchApiTokenPermissionOptions() {
  return apiFetch<ApiTokenPermissionOption[]>('/api-tokens/options');
}

export function createApiToken(payload: {
  name: string;
  expiresAt: string | null;
  permissionNames: string[];
}) {
  return apiFetch<ApiTokenCreated>('/api-tokens', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function revokeApiToken(tokenId: string) {
  return apiFetch<void>(`/api-tokens/${tokenId}`, { method: 'DELETE' });
}

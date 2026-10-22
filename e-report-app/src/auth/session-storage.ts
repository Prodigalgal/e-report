import * as SecureStore from 'expo-secure-store';

import type { LoginResponse } from '@/types/api';

export const AUTH_TOKEN_KEY = 'e_report_auth_token_v3';
export const AUTH_REFRESH_TOKEN_KEY = 'e_report_refresh_token_v1';
export const AUTH_SESSION_ID_KEY = 'e_report_auth_session_id_v1';
const AUTH_USER_KEY = 'e_report_auth_user_v4';
const AUTH_KEYS_TO_CLEAR = ['e_report_auth_token', 'e_report_auth_user', 'e_report_auth_user_v3'];

export type StoredCredentials = {
  token: string;
  refreshToken: string;
  sessionId: string;
};

export type StoredAuthState = {
  user: LoginResponse | null;
  credentials: StoredCredentials | null;
};

export async function loadStoredAuthState(): Promise<StoredAuthState> {
  const [token, refreshToken, sessionId, storedUser] = await Promise.all([
    SecureStore.getItemAsync(AUTH_TOKEN_KEY),
    SecureStore.getItemAsync(AUTH_REFRESH_TOKEN_KEY),
    SecureStore.getItemAsync(AUTH_SESSION_ID_KEY),
    SecureStore.getItemAsync(AUTH_USER_KEY),
  ]);
  const credentials = token && refreshToken && sessionId ? { token, refreshToken, sessionId } : null;
  return {
    credentials,
    user: parseStoredUser(storedUser, credentials),
  };
}

export async function loadStoredCredentials(): Promise<StoredCredentials | null> {
  const [token, refreshToken, sessionId] = await Promise.all([
    SecureStore.getItemAsync(AUTH_TOKEN_KEY),
    SecureStore.getItemAsync(AUTH_REFRESH_TOKEN_KEY),
    SecureStore.getItemAsync(AUTH_SESSION_ID_KEY),
  ]);
  return token && refreshToken && sessionId ? { token, refreshToken, sessionId } : null;
}

export async function loadStoredSession(): Promise<LoginResponse | null> {
  const [storedUser, credentials] = await Promise.all([
    SecureStore.getItemAsync(AUTH_USER_KEY),
    loadStoredCredentials(),
  ]);
  return parseStoredUser(storedUser, credentials);
}

function parseStoredUser(
  storedUser: string | null,
  credentials: StoredCredentials | null,
): LoginResponse | null {
  if (!storedUser || !credentials) return null;
  try {
    const parsed = JSON.parse(storedUser) as Partial<LoginResponse>;
    if (typeof parsed.username !== 'string' || !Array.isArray(parsed.permissions)) {
      return null;
    }
    return {
      ...parsed,
      ...credentials,
      username: parsed.username,
      name: typeof parsed.name === 'string' ? parsed.name : parsed.username,
      permissions: parsed.permissions.filter((permission): permission is string => typeof permission === 'string'),
    };
  } catch {
    return null;
  }
}

export async function storeSession(user: LoginResponse) {
  if (!user.token || !user.refreshToken || !user.sessionId) {
    throw new Error('登录会话凭据不完整。');
  }
  const { token, refreshToken, ...safeUser } = user;
  await Promise.all([
    SecureStore.setItemAsync(AUTH_TOKEN_KEY, token),
    SecureStore.setItemAsync(AUTH_REFRESH_TOKEN_KEY, refreshToken),
    SecureStore.setItemAsync(AUTH_SESSION_ID_KEY, user.sessionId),
    SecureStore.setItemAsync(AUTH_USER_KEY, JSON.stringify(safeUser)),
    ...AUTH_KEYS_TO_CLEAR.map(key => SecureStore.deleteItemAsync(key)),
  ]);
}

export async function storeSessionUser(user: LoginResponse) {
  const { token: _token, refreshToken: _refreshToken, ...safeUser } = user;
  await SecureStore.setItemAsync(AUTH_USER_KEY, JSON.stringify(safeUser));
}

export async function updateStoredCredentials(credentials: StoredCredentials) {
  await Promise.all([
    SecureStore.setItemAsync(AUTH_TOKEN_KEY, credentials.token),
    SecureStore.setItemAsync(AUTH_REFRESH_TOKEN_KEY, credentials.refreshToken),
    SecureStore.setItemAsync(AUTH_SESSION_ID_KEY, credentials.sessionId),
  ]);
}

export async function clearStoredSession() {
  await Promise.all([
    SecureStore.deleteItemAsync(AUTH_TOKEN_KEY),
    SecureStore.deleteItemAsync(AUTH_REFRESH_TOKEN_KEY),
    SecureStore.deleteItemAsync(AUTH_SESSION_ID_KEY),
    SecureStore.deleteItemAsync(AUTH_USER_KEY),
    ...AUTH_KEYS_TO_CLEAR.map(key => SecureStore.deleteItemAsync(key)),
  ]);
}

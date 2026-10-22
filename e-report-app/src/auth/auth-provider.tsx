import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { ApiError, setApiAuthCredentials } from '@/api/http';
import {
  fetchCurrentUser,
  fetchPasswordPolicy,
  loginWithChallenge,
  type LoginProgressStage,
  logoutCurrentSession,
  updateCurrentUserProfile,
} from '@/auth/auth-api';
import { userIsAdmin } from '@/auth/admin-access';
import {
  clearStoredSession,
  loadStoredAuthState,
  loadStoredCredentials,
  storeSession,
  storeSessionUser,
} from '@/auth/session-storage';
import { userHasAnyPermission } from '@/constants/work-orders';
import type { AuthChallenge, LoginResponse } from '@/types/api';
import { DEFAULT_PASSWORD_POLICY, normalizePasswordPolicy } from '@/auth/password-policy';
import type { PasswordPolicy } from '@/types/api';

type AuthContextValue = {
  currentUser: LoginResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  explicitlyLoggedOut: boolean;
  token: string | null;
  authHeader: string | null;
  login: (
    username: string,
    password: string,
    captchaAnswer: string,
    challenge: AuthChallenge,
    onProgress?: (stage: LoginProgressStage | 'SAVING_SESSION') => void,
  ) => Promise<LoginResponse>;
  logout: () => Promise<void>;
  refreshCurrentUser: () => Promise<void>;
  updateProfile: (name: string, phone: string, email?: string | null) => Promise<void>;
  hasPermission: (permission: string) => boolean;
  hasAnyPermission: (permissions: readonly string[]) => boolean;
  isAdmin: boolean;
  passwordPolicy: PasswordPolicy;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [currentUser, setCurrentUser] = useState<LoginResponse | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [explicitlyLoggedOut, setExplicitlyLoggedOut] = useState(false);
  const [passwordPolicy, setPasswordPolicy] = useState<PasswordPolicy>(DEFAULT_PASSWORD_POLICY);

  const loadPasswordPolicy = useCallback(async () => {
    try {
      const policy = await fetchPasswordPolicy();
      setPasswordPolicy(normalizePasswordPolicy(policy));
    } catch {
      setPasswordPolicy(DEFAULT_PASSWORD_POLICY);
    }
  }, []);

  const applySession = useCallback(async (user: LoginResponse) => {
    if (!user.token || !user.refreshToken || !user.sessionId) {
      throw new Error('登录会话凭据不完整。');
    }
    await storeSession(user);
    setApiAuthCredentials({ token: user.token, refreshToken: user.refreshToken, sessionId: user.sessionId });
    setExplicitlyLoggedOut(false);
    setCurrentUser(user);
    setToken(user.token);
    void loadPasswordPolicy();
  }, [loadPasswordPolicy]);

  const refreshCurrentUser = useCallback(async () => {
    const { user: storedUser, credentials: storedCredentials } = await loadStoredAuthState();
    setApiAuthCredentials(storedCredentials);
    setToken(storedCredentials?.token || null);
    if (!storedCredentials) {
      setCurrentUser(null);
      setPasswordPolicy(DEFAULT_PASSWORD_POLICY);
      return;
    }
    if (storedUser) setCurrentUser(storedUser);
    try {
      const user = await fetchCurrentUser();
      const latestCredentials = await loadStoredCredentials();
      if (!latestCredentials) throw new Error('刷新后的登录凭据不存在。');
      const refreshedUser = { ...user, ...latestCredentials };
      await storeSessionUser(refreshedUser);
      setApiAuthCredentials(latestCredentials);
      setExplicitlyLoggedOut(false);
      setCurrentUser(refreshedUser);
      setToken(latestCredentials.token);
      void loadPasswordPolicy();
    } catch (error) {
      if (isAuthenticationExpired(error)) {
        await clearStoredSession();
        setApiAuthCredentials(null);
        setToken(null);
        setCurrentUser(null);
        setPasswordPolicy(DEFAULT_PASSWORD_POLICY);
        return;
      }
      if (!storedUser) setCurrentUser(null);
    }
  }, [loadPasswordPolicy]);

  useEffect(() => {
    refreshCurrentUser().finally(() => setIsLoading(false));
  }, [refreshCurrentUser]);

  const login = useCallback(async (
    username: string,
    password: string,
    captchaAnswer: string,
    challenge: AuthChallenge,
    onProgress?: (stage: LoginProgressStage | 'SAVING_SESSION') => void,
  ) => {
    const user = await loginWithChallenge(username, password, captchaAnswer, challenge, onProgress);
    onProgress?.('SAVING_SESSION');
    await applySession(user);
    return user;
  }, [applySession]);

  const logout = useCallback(async () => {
    try {
      await logoutCurrentSession();
    } catch (error) {
      if (!isAuthenticationExpired(error)) throw error;
    } finally {
      await clearStoredSession();
      setApiAuthCredentials(null);
      setExplicitlyLoggedOut(true);
      setCurrentUser(null);
      setToken(null);
      setPasswordPolicy(DEFAULT_PASSWORD_POLICY);
    }
  }, []);

  const updateProfile = useCallback(async (name: string, phone: string, email?: string | null) => {
    const user = await updateCurrentUserProfile(name, phone, email);
    const credentials = await loadStoredCredentials();
    if (!credentials) throw new Error('登录状态已失效，请重新登录。');
    await applySession({ ...user, ...credentials });
  }, [applySession]);

  const hasPermission = useCallback((permission: string) => (
    currentUser?.permissions?.includes(permission) ?? false
  ), [currentUser]);

  const hasAnyPermission = useCallback((permissions: readonly string[]) => (
    userHasAnyPermission(currentUser, permissions)
  ), [currentUser]);

  const isAdmin = useMemo(() => userIsAdmin(currentUser), [currentUser]);

  const value = useMemo<AuthContextValue>(() => ({
    currentUser,
    isAuthenticated: !!currentUser && !!token,
    isLoading,
    explicitlyLoggedOut,
    token,
    authHeader: token ? `Bearer ${token}` : null,
    login,
    logout,
    refreshCurrentUser,
    updateProfile,
    hasPermission,
    hasAnyPermission,
    isAdmin,
    passwordPolicy,
  }), [currentUser, explicitlyLoggedOut, hasAnyPermission, hasPermission, isAdmin, isLoading, login, logout, passwordPolicy, refreshCurrentUser, token, updateProfile]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider.');
  return context;
}

function isAuthenticationExpired(error: unknown) {
  return error instanceof ApiError && error.status === 401;
}

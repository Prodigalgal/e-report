import apiClient from '../services/apiClient.js';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AuthContext } from './AuthContext.jsx';
import { clearAuthStorage, storeRememberLoginPreference, webClientInstanceId } from '../constants/authStorage.js';
import { fetchCurrentUserAPI, fetchPasswordPolicyAPI, initializeCsrfAPI, logoutAPI, updateCurrentUserProfileAPI } from '../services/authApiService.js';
import { formatApiErrorMessage } from '../utils/formatApiErrorMessage.js';
import { DEFAULT_PASSWORD_POLICY, normalizePasswordPolicy } from '../utils/passwordPolicy.js';

const toUserData = responseData => ({
    username: responseData.username,
    name: responseData.name,
    permissions: Array.isArray(responseData.permissions) ? responseData.permissions : [],
    membershipId: responseData.membershipId,
    tenantId: responseData.tenantId,
    tenantCode: responseData.tenantCode,
    tenantName: responseData.tenantName,
    orgUnitId: responseData.orgUnitId,
    authorizationVersion: responseData.authorizationVersion,
    phone: responseData.phone || '',
    email: responseData.email || '',
    sessionId: responseData.sessionId,
    clientType: responseData.clientType,
    passwordChangeRequired: responseData.passwordChangeRequired === true,
});

const hasCsrfCookie = () => document.cookie
    .split(';')
    .some(cookie => cookie.trim().startsWith('XSRF-TOKEN='));

const ensureCsrfCookie = () => (hasCsrfCookie() ? Promise.resolve() : initializeCsrfAPI());

export const AuthProvider = ({ children }) => {
    const [currentUser, setCurrentUser] = useState(null);
    const [loadingAuth, setLoadingAuth] = useState(true);
    const [passwordPolicy, setPasswordPolicy] = useState(DEFAULT_PASSWORD_POLICY);

    const loadPasswordPolicy = useCallback(async () => {
        try {
            const response = await fetchPasswordPolicyAPI();
            setPasswordPolicy(normalizePasswordPolicy(response.data));
        } catch (error) {
            if (error.response?.status !== 401 && error.response?.status !== 403) {
                console.warn('密码策略加载失败，将使用安全默认值。', error);
            }
            setPasswordPolicy(DEFAULT_PASSWORD_POLICY);
        }
    }, []);

    const refreshCurrentUser = useCallback(async () => {
        setLoadingAuth(true);
        try {
            const response = await fetchCurrentUserAPI();
            await ensureCsrfCookie();
            const freshUser = toUserData(response.data);
            setCurrentUser(freshUser);
            void loadPasswordPolicy();
            return freshUser;
        } catch (error) {
            if (error.response?.status === 401 || error.response?.status === 403) {
                setCurrentUser(null);
                return null;
            }
            setCurrentUser(null);
            throw error;
        } finally {
            setLoadingAuth(false);
        }
    }, [loadPasswordPolicy]);

    useEffect(() => {
        clearAuthStorage();
        refreshCurrentUser().catch(() => {});
    }, [refreshCurrentUser]);

    useEffect(() => {
        const expire = () => setCurrentUser(null);
        window.addEventListener('ereport-auth-expired', expire);
        return () => window.removeEventListener('ereport-auth-expired', expire);
    }, []);

    const login = useCallback(async (username, password, challengeProof, rememberMe) => {
        try {
            const response = await apiClient.post('/auth/login', {
                username,
                password,
                clientType: 'WEB',
                clientInstanceId: webClientInstanceId(),
                rememberMe,
                ...challengeProof,
            });
            if (!response.data?.username) {
                throw new Error('登录响应不完整，请稍后重试。');
            }
            await ensureCsrfCookie();
            const userData = toUserData(response.data);
            storeRememberLoginPreference(rememberMe);
            setCurrentUser(userData);
            void loadPasswordPolicy();
            return { success: true, user: userData };
        } catch (error) {
            setCurrentUser(null);
            throw normalizeLoginError(error);
        }
    }, [loadPasswordPolicy]);

    const logout = useCallback(async () => {
        try {
            await logoutAPI();
        } catch (error) {
            if (error.response?.status !== 401 && error.response?.status !== 403) throw error;
        } finally {
            setCurrentUser(null);
            setPasswordPolicy(DEFAULT_PASSWORD_POLICY);
            clearAuthStorage();
        }
    }, []);

    const updateProfile = useCallback(async profile => {
        const response = await updateCurrentUserProfileAPI(profile);
        const userData = toUserData(response.data);
        setCurrentUser(userData);
        return userData;
    }, []);

    const isAuthenticated = useCallback(() => currentUser !== null, [currentUser]);

    const hasPermission = useCallback(permissionName => (
        Array.isArray(currentUser?.permissions) && currentUser.permissions.includes(permissionName)
    ), [currentUser]);

    const value = useMemo(() => ({
        currentUser,
        passwordPolicy,
        isAuthenticated,
        login,
        logout,
        loadingAuth,
        hasPermission,
        updateProfile,
        refreshAuthUser: refreshCurrentUser,
    }), [currentUser, hasPermission, isAuthenticated, loadingAuth, login, logout, passwordPolicy, refreshCurrentUser, updateProfile]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

const normalizeLoginError = error => new Error(
    formatApiErrorMessage(error, '登录失败，请检查账号、密码或安全验证后重试。'),
);

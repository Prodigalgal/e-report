const AUTH_STORAGE_KEYS_TO_CLEAR = ['sessionToken', 'authUser', 'authStorageVersion'];
const CLIENT_INSTANCE_KEY = 'e-report-web-client-instance-v1';
const REMEMBER_LOGIN_KEY = 'e-report-web-remember-login-v1';

export const clearAuthStorage = () => {
    AUTH_STORAGE_KEYS_TO_CLEAR.forEach(key => localStorage.removeItem(key));
};

export const webClientInstanceId = () => {
    const existing = localStorage.getItem(CLIENT_INSTANCE_KEY);
    if (existing) return existing;
    const created = createUuid();
    localStorage.setItem(CLIENT_INSTANCE_KEY, created);
    return created;
};

export const loadRememberLoginPreference = () => localStorage.getItem(REMEMBER_LOGIN_KEY) !== 'false';

export const storeRememberLoginPreference = rememberLogin => {
    localStorage.setItem(REMEMBER_LOGIN_KEY, rememberLogin ? 'true' : 'false');
};

const createUuid = () => {
    if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
    const bytes = new Uint8Array(16);
    globalThis.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = [...bytes].map(value => value.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};

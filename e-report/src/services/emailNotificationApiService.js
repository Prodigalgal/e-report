import apiClient from './apiClient';

export const fetchEmailNotificationPreferencesAPI = signal => apiClient.get('/auth/me/email-notifications', { signal });

export const updateEmailNotificationPreferencesAPI = preferences => apiClient.put('/auth/me/email-notifications', preferences);

function adminPreferencesPath(userId) {
    if (!userId) throw new Error('请选择需要管理的用户。');
    return `/admin/users/${encodeURIComponent(userId)}/email-notifications`;
}

export const fetchAdminEmailNotificationPreferencesAPI = (userId, signal) => (
    apiClient.get(adminPreferencesPath(userId), { signal })
);

export const updateAdminEmailNotificationPreferencesAPI = (userId, preferences) => (
    apiClient.put(adminPreferencesPath(userId), preferences)
);

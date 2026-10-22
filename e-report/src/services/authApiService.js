import apiClient from './apiClient';

export const fetchCurrentUserAPI = () => {
    return apiClient.get('/auth/me', { telemetryExpectedStatuses: [401] });
};

export const fetchPasswordPolicyAPI = () => (
    apiClient.get('/auth/password-policy', { telemetryExpectedStatuses: [401, 403] })
);

export const initializeCsrfAPI = () => apiClient.get('/auth/csrf');

export const updateCurrentUserProfileAPI = (profile) => {
    return apiClient.put('/auth/me', profile);
};

export const changeCurrentPasswordAPI = (currentPassword, newPassword) => (
    apiClient.post('/auth/password', { currentPassword, newPassword })
);

export const fetchAuthSessionsAPI = (page = 0, size = 10) => apiClient.get('/auth/sessions/page', {
    params: { page, size, sort: 'createdAt,desc' },
});

export const revokeAuthSessionAPI = sessionId => apiClient.delete(`/auth/sessions/${sessionId}`);

export const revokeOtherAuthSessionsAPI = () => apiClient.delete('/auth/sessions/others');

export const logoutAPI = () => apiClient.post('/auth/logout', undefined, {
    telemetryExpectedStatuses: [401, 403],
});

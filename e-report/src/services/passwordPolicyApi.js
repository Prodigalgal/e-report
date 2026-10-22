import apiClient from './apiClient';

export const fetchAdminPasswordPolicyAPI = (orgUnitId) => apiClient.get('/admin/password-policy', {
    params: { orgUnitId },
});

export const updateAdminPasswordPolicyAPI = (payload, orgUnitId) => apiClient.put('/admin/password-policy', payload, {
    params: { orgUnitId },
});

export const deleteAdminPasswordPolicyAPI = (orgUnitId) => apiClient.delete('/admin/password-policy', {
    params: { orgUnitId },
});

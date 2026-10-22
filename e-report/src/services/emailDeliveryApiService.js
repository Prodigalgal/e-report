import apiClient from './apiClient';

export const searchEmailDeliveries = (params, signal) => apiClient.get('/admin/notifications/emails', { params, signal });
export const fetchEmailDeliveryScopes = signal => apiClient.get('/admin/notifications/emails/scopes', { signal });
export const fetchEmailDelivery = (id, signal) => apiClient.get(`/admin/notifications/emails/${encodeURIComponent(id)}`, { signal });

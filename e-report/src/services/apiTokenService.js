import apiClient from './apiClient.js';

export const fetchApiTokensAPI = () => apiClient.get('/api-tokens');

export const fetchApiTokenOptionsAPI = () => apiClient.get('/api-tokens/options');

export const createApiTokenAPI = payload => apiClient.post('/api-tokens', payload);

export const revokeApiTokenAPI = tokenId => apiClient.delete(`/api-tokens/${tokenId}`);

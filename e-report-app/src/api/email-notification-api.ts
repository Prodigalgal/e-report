import options from '../../../shared/email-notification-preferences.json';

import { apiFetch } from '@/api/http';

export const emailNotificationOptions = options;
export type EmailNotificationPreferenceKey = keyof typeof options;
export type EmailNotificationPreferences = Record<EmailNotificationPreferenceKey, boolean>;

export function fetchEmailNotificationPreferences() {
  return apiFetch<EmailNotificationPreferences>('/auth/me/email-notifications');
}

export function updateEmailNotificationPreferences(preferences: EmailNotificationPreferences) {
  return apiFetch<EmailNotificationPreferences>('/auth/me/email-notifications', {
    method: 'PUT',
    body: JSON.stringify(preferences),
  });
}

function adminPreferencesPath(userId: string) {
  if (!userId) throw new Error('请选择需要管理的用户。');
  return `/admin/users/${encodeURIComponent(userId)}/email-notifications`;
}

export function fetchAdminEmailNotificationPreferences(userId: string) {
  return apiFetch<EmailNotificationPreferences>(adminPreferencesPath(userId));
}

export function updateAdminEmailNotificationPreferences(userId: string, preferences: EmailNotificationPreferences) {
  return apiFetch<EmailNotificationPreferences>(adminPreferencesPath(userId), {
    method: 'PUT',
    body: JSON.stringify(preferences),
  });
}

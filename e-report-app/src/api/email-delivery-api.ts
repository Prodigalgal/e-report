import options from '../../../shared/email-delivery.json';
import { apiFetch } from '@/api/http';
import type { PageResponse } from '@/types/api';

export type EmailDeliveryCategory = keyof typeof options.categories;
export type EmailDeliveryStatus = keyof typeof options.statuses;
export type EmailDelivery = {
  id: string;
  orgUnitId: string | null;
  orgUnitName: string;
  category: EmailDeliveryCategory;
  status: EmailDeliveryStatus;
  subject: string;
  recipientAddress: string;
  senderAddress: string | null;
  relatedId: string | null;
  recipientUserId: string | null;
  startedAt: string;
  completedAt: string | null;
  durationMs: number | null;
  failureCode: string | null;
  failureMessage: string | null;
};
export type EmailDeliveryFilters = {
  category: string; status: string; keyword: string; orgUnitId: string; from: string; to: string;
};

export function searchEmailDeliveries(filters: EmailDeliveryFilters, page: number) {
  const params = new URLSearchParams({ page: String(page), size: '20' });
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
  return apiFetch<PageResponse<EmailDelivery>>(`/admin/notifications/emails?${params}`);
}

export function fetchEmailDeliveryScopes() {
  return apiFetch<{ id: string; name: string }[]>('/admin/notifications/emails/scopes');
}

export function fetchEmailDelivery(id: string) {
  return apiFetch<EmailDelivery>(`/admin/notifications/emails/${encodeURIComponent(id)}`);
}

import { apiFetch } from '@/api/http';
import type {
  AdminUser,
  AppConfig,
  ConfigScopeResponse,
  PasswordPolicy,
  NotificationCenterSettings,
  DailyOutstandingReportSettings,
  NotificationSwitch,
  AppRelease,
  PageResponse,
  Permission,
  UserStatus,
  ObservationEventDetail,
  ObservationEventSummary,
  ObservationPlatform,
  ObservationSeverity,
  ObservationUserOption,
  ObservationViewMode,
  RedisRuntimeStatus,
  OrgUnit,
  AdminUserDetail,
  AuthorizationOperationPayload,
  AuthorizationOptions,
  AuthorizationPlan,
  UserAuthorizationView,
  AuthorizationProfile,
  AuthorizationProfileSummary,
  UserOrganizationMovePreview,
  UserOrganizationMoveRequest,
  UserDepartmentMoveRequest,
  Department,
} from '@/types/api';

type PageQuery = {
  page?: number;
  size?: number;
  sort?: string;
  searchTerm?: string;
};

export type UserFormPayload = {
  username?: string;
  password?: string;
  name: string;
  phone: string;
  email?: string | null;
  status: UserStatus;
  orgUnitId?: string;
  departmentId?: string | null;
};

export type UserAdministrationUpdatePayload = {
  details?: {
    name: string;
    phone: string;
    email?: string | null;
    orgUnitId: string;
    organizationMoveStrategy?: 'KEEP' | 'REVOKE_ALL';
  };
  status?: UserStatus;
};

export type AppReleasePolicyUpdatePayload = {
  forceUpdate: boolean;
  minSupportedVersionCode: number;
  releaseNotes?: string;
};

export type AuthorizationProfileFormPayload = {
  code: string;
  displayName: string;
  description?: string | null;
  rules: { permissionName: string }[];
  changeSummary?: string | null;
};

export type ConfigFormPayload = {
  configKey: string;
  configValue: string;
  description?: string;
};

export type ConfigUpdatePayload = {
  configValue: string | null;
  description?: string;
  retainSensitiveValue: boolean;
};

export type PasswordPolicyUpdatePayload = Pick<PasswordPolicy, 'minLength' | 'requireUppercase' | 'requireLowercase' | 'requireDigit' | 'requireSpecial'>;

export function fetchAdminUsers(params: PageQuery & {
  searchTerm?: string;
  orgUnitId?: string;
  departmentId?: string;
  status?: UserStatus;
  includeDeleted?: boolean;
}) {
  return apiFetch<PageResponse<AdminUser>>('/admin/users', {
    query: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      sort: params.sort ?? 'username,asc',
      searchTerm: params.searchTerm,
      orgUnitId: params.orgUnitId,
      departmentId: params.departmentId,
      status: params.status,
      includeDeleted: params.includeDeleted ?? false,
    },
  });
}

export function createAdminUser(payload: UserFormPayload) {
  return apiFetch<AdminUser>('/admin/users', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchAdminUserDetail(userId: string) {
  return apiFetch<AdminUserDetail>(`/admin/users/${userId}/detail`);
}

export function fetchAdminUserAuthorization(userId: string) {
  return apiFetch<UserAuthorizationView>(`/admin/users/${userId}/authorization`);
}

export function fetchAdminUserAuthorizationOptions(userId: string) {
  return apiFetch<AuthorizationOptions>(`/admin/users/${userId}/authorization/options`);
}

export function createAdminUserAuthorizationPlan(
  userId: string,
  expectedAuthorizationVersion: number,
  operations: AuthorizationOperationPayload[],
) {
  return apiFetch<AuthorizationPlan>(`/admin/users/${userId}/authorization/plans`, {
    method: 'POST',
    body: JSON.stringify({ expectedAuthorizationVersion, operations }),
  });
}

export function applyAdminUserAuthorizationPlan(userId: string, planToken: string) {
  return apiFetch<UserAuthorizationView>(
    `/admin/users/${userId}/authorization/plans/${encodeURIComponent(planToken)}/apply`,
    { method: 'POST' },
  );
}

export function fetchAdminAuthorizationProfiles() {
  return apiFetch<AuthorizationProfile[]>('/admin/authorization-profiles');
}

export function fetchAdminAuthorizationProfileSummaries() {
  return apiFetch<AuthorizationProfileSummary[]>('/admin/authorization-profiles/summaries');
}

export function createAdminAuthorizationProfile(payload: AuthorizationProfileFormPayload) {
  return apiFetch<AuthorizationProfile>('/admin/authorization-profiles', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateAdminAuthorizationProfileDraft(profileId: string, payload: AuthorizationProfileFormPayload) {
  return apiFetch<AuthorizationProfile>(`/admin/authorization-profiles/${profileId}/draft`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function copyAdminAuthorizationProfile(profileId: string, code: string, displayName: string) {
  return apiFetch<AuthorizationProfile>(`/admin/authorization-profiles/${profileId}/copy`, {
    method: 'POST',
    body: JSON.stringify({ code, displayName }),
  });
}

export function publishAdminAuthorizationProfile(profileId: string) {
  return apiFetch<AuthorizationProfile>(`/admin/authorization-profiles/${profileId}/publish`, { method: 'POST' });
}

export function retireAdminAuthorizationProfile(profileId: string) {
  return apiFetch<AuthorizationProfile>(`/admin/authorization-profiles/${profileId}/retire`, { method: 'POST' });
}

export function updateAdminUserAdministration(userId: string, payload: UserAdministrationUpdatePayload) {
  return apiFetch<AdminUserDetail>(`/admin/users/${userId}/detail`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function previewAdminUserOrganizationMove(userId: string, payload: UserOrganizationMoveRequest) {
  return apiFetch<UserOrganizationMovePreview>(`/admin/users/${userId}/organization-move/preview`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function executeAdminUserOrganizationMove(userId: string, payload: UserOrganizationMoveRequest) {
  return apiFetch<AdminUserDetail>(`/admin/users/${userId}/organization-move`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function moveAdminUserDepartment(userId: string, payload: UserDepartmentMoveRequest) {
  return apiFetch<AdminUserDetail>(`/admin/users/${userId}/department-move`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateAdminUserDetails(userId: string, details: { name: string; phone: string; email?: string | null; orgUnitId?: string }) {
  return apiFetch<AdminUser>(`/admin/users/${userId}`, {
    method: 'PUT',
    body: JSON.stringify(details),
  });
}

export function updateAdminUserStatus(userId: string, status: UserStatus) {
  return apiFetch<AdminUser>(`/admin/users/${userId}/status`, {
    method: 'PUT',
    query: { status },
  });
}

export function fetchAdminOrgUnits() {
  return apiFetch<OrgUnit[]>('/admin/org-units');
}

export function fetchAdminDepartments() {
  return apiFetch<Department[]>('/admin/departments');
}

export function resetAdminUserPassword(userId: string, newPassword: string, confirmPassword: string) {
  return apiFetch<void>(`/admin/users/${userId}/reset-password`, {
    method: 'POST',
    body: JSON.stringify({ newPassword, confirmPassword }),
  });
}

export function deleteAdminUser(userId: string) {
  return apiFetch<void>(`/admin/users/${userId}`, { method: 'DELETE' });
}

export function restoreAdminUser(userId: string) {
  return apiFetch<AdminUser>(`/admin/users/${userId}/restore`, { method: 'PATCH' });
}

export function fetchAdminPermissions(params: PageQuery = {}) {
  return apiFetch<PageResponse<Permission>>('/admin/permissions', {
    query: {
      page: params.page ?? 0,
      size: params.size ?? 200,
      sort: params.sort ?? 'name,asc',
    },
  });
}

export function fetchAdminConfigScopes() {
  return apiFetch<ConfigScopeResponse>('/admin/configs/scopes');
}

export function fetchAdminPasswordPolicy(orgUnitId?: string) {
  return apiFetch<PasswordPolicy>('/admin/password-policy', {
    query: { orgUnitId },
  });
}

export function updateAdminPasswordPolicy(payload: PasswordPolicyUpdatePayload, orgUnitId?: string) {
  return apiFetch<PasswordPolicy>('/admin/password-policy', {
    method: 'PUT',
    query: { orgUnitId },
    body: JSON.stringify(payload),
  });
}

export function deleteAdminPasswordPolicy(orgUnitId?: string) {
  return apiFetch<void>('/admin/password-policy', {
    method: 'DELETE',
    query: { orgUnitId },
  });
}

export function fetchAdminConfigs(params: PageQuery & { orgUnitId?: string } = {}) {
  return apiFetch<PageResponse<AppConfig>>('/admin/configs', {
    query: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      sort: params.sort ?? 'configKey,asc',
      searchTerm: params.searchTerm,
      orgUnitId: params.orgUnitId,
      includeNotification: false,
    },
  });
}

export function fetchAdminNotificationSettings(orgUnitId?: string) {
  return apiFetch<NotificationCenterSettings>('/admin/notifications/settings', {
    query: { orgUnitId },
  });
}

export function updateAdminNotificationSwitch(name: string, enabled: boolean, orgUnitId?: string) {
  return apiFetch<NotificationSwitch>(`/admin/notifications/settings/${encodeURIComponent(name)}`, {
    method: 'PUT',
    query: { orgUnitId },
    body: JSON.stringify({ enabled }),
  });
}

export function updateDailyOutstandingReportSchedule(sendTime: string, orgUnitId?: string) {
  return apiFetch<DailyOutstandingReportSettings>('/admin/notifications/settings/daily-outstanding-report/schedule', {
    method: 'PUT',
    query: { orgUnitId },
    body: JSON.stringify({ sendTime }),
  });
}

export function createAdminConfig(payload: ConfigFormPayload, orgUnitId?: string) {
  return apiFetch<AppConfig>('/admin/configs', {
    method: 'POST',
    query: { orgUnitId },
    body: JSON.stringify(payload),
  });
}

export function updateAdminConfig(configKey: string, payload: ConfigUpdatePayload, orgUnitId?: string) {
  return apiFetch<AppConfig>(`/admin/configs/${encodeURIComponent(configKey)}`, {
    method: 'PUT',
    query: { orgUnitId },
    body: JSON.stringify(payload),
  });
}

export function deleteAdminConfig(configKey: string, orgUnitId?: string) {
  return apiFetch<void>(`/admin/configs/${encodeURIComponent(configKey)}`, {
    method: 'DELETE',
    query: { orgUnitId },
  });
}

export function fetchAdminAppReleases(params: PageQuery & { edition?: 'NATIVE' | 'LITE' } = {}) {
  return apiFetch<PageResponse<AppRelease>>('/admin/app-updates', {
    query: {
      platform: 'ANDROID',
      edition: params.edition ?? 'NATIVE',
      channel: 'stable',
      page: params.page ?? 0,
      size: params.size ?? 20,
      sort: params.sort ?? 'publishedAt,desc',
      searchTerm: params.searchTerm,
    },
  });
}

export function deleteAdminAppRelease(releaseId: string) {
  return apiFetch<void>(`/admin/app-updates/${releaseId}`, { method: 'DELETE' });
}

export function updateAdminAppReleasePolicy(releaseId: string, payload: AppReleasePolicyUpdatePayload) {
  return apiFetch<AppRelease>(`/admin/app-updates/${releaseId}/policy`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function fetchAdminObservationEvents(params: {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
  platform?: ObservationPlatform;
  severity?: ObservationSeverity;
  view?: ObservationViewMode;
  category?: string;
  keyword?: string;
  userId?: string;
}, signal?: AbortSignal) {
  return apiFetch<PageResponse<ObservationEventSummary>>('/admin/observability/events', {
    signal,
    query: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      sort: 'receivedAt,desc',
      from: params.from,
      to: params.to,
      platform: params.platform,
      severity: params.severity,
      view: params.view,
      category: params.category,
      keyword: params.keyword,
      userId: params.userId,
    },
  });
}

export function searchAdminObservationUsers(keyword: string, signal?: AbortSignal) {
  return apiFetch<ObservationUserOption[]>('/admin/observability/events/users', {
    signal,
    query: { keyword, limit: 20 },
  });
}

export function fetchAdminObservationEvent(eventId: string, signal?: AbortSignal) {
  return apiFetch<ObservationEventDetail>(`/admin/observability/events/${eventId}`, { signal });
}

export function fetchRedisRuntimeStatus(signal?: AbortSignal) {
  return apiFetch<RedisRuntimeStatus>('/admin/observability/redis', { signal });
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';

import {
  fetchAdminOrgUnits,
  fetchAdminDepartments,
  fetchAdminUserAuthorization,
  fetchAdminUserDetail,
  executeAdminUserOrganizationMove,
  moveAdminUserDepartment,
  previewAdminUserOrganizationMove,
  resetAdminUserPassword,
  updateAdminUserAdministration,
  type UserAdministrationUpdatePayload,
} from '@/api/admin-api';
import { formatApiErrorMessage } from '@/api/http';
import { evaluatePassword, passwordsMatch } from '@/auth/password-policy';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import { AuthorizationEditor } from '@/features/admin/authorization-editor';
import { filterAuthorizationGrants } from '@/features/admin/authorization-editor-workflow';
import { EmailNotificationPreferencesPanel } from '@/features/profile/email-notification-preferences-panel';
import {
  ORGANIZATION_MOVE_STRATEGIES,
  ORGANIZATION_MOVE_STRATEGY_BY_VALUE,
  authorizationChangeCounts,
} from '@/features/admin/organization-move-workflow';
import {
  USER_STATUSES,
  USER_STATUS_LABELS,
  validateUserDetails,
} from '@/features/admin/user-administration';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { EditModal } from '@/ui/edit-modal';
import { InfoRow } from '@/ui/info-row';
import { PageHeader } from '@/ui/page-header';
import { PasswordPolicyHint } from '@/ui/password-policy-hint';
import { Screen } from '@/ui/screen';
import { SelectField } from '@/ui/select-field';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';
import type {
  AuthorizationGrantView,
  UserOrganizationMovePreview,
  UserOrganizationMoveRequest,
  UserOrganizationMoveStrategy,
  UserStatus,
} from '@/types/api';

type DialogName = 'details' | 'organization' | 'department' | 'status' | 'authorization' | 'password' | 'email-notifications' | null;

type UserDetailsDraft = {
  name: string;
  phone: string;
  email: string;
  orgUnitId: string;
};

type UpdateVariables = {
  payload: UserAdministrationUpdatePayload;
  successMessage: string;
};

const AUTHORIZATION_GRANT_PREVIEW_LIMIT = 8;
function authorizationIssuerLabel(grant: AuthorizationGrantView) {
  const labels: Record<string, string> = {
    USER: '人工授权',
    IMPORT: '批量导入',
    MIGRATION: '历史迁移',
    SYSTEM_ROOT: '系统根授权',
    SYSTEM_BASELINE: '组织基线',
    SYSTEM_SELF_BASELINE: '个人基础权限',
  };
  return labels[grant.issuerType] || grant.issuerType;
}

function authorizationGrantExpiryLabel(grant: AuthorizationGrantView) {
  return grant.expiresAt ? `有效期至 ${formatDateTime(grant.expiresAt)}` : '长期有效';
}

export default function AdminUserDetailScreen() {
  const { userId: rawUserId } = useLocalSearchParams<{ userId: string | string[] }>();
  const userId = Array.isArray(rawUserId) ? rawUserId[0] : rawUserId;
  const queryClient = useQueryClient();
  const { currentUser, hasPermission, passwordPolicy } = useAuth();
  const canView = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_VIEW);
  const canUpdateDetails = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_UPDATE_DETAILS);
  const canUpdateContact = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_CONTACT_UPDATE);
  const canMoveOrganization = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_ORGANIZATION_MOVE);
  const canMoveDepartment = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_DEPARTMENT_MOVE);
  const canUpdateStatus = [
    ADMIN_PERMISSIONS.USER_MANAGE_STATUS_ACTIVATE,
    ADMIN_PERMISSIONS.USER_MANAGE_STATUS_DEACTIVATE,
    ADMIN_PERMISSIONS.USER_MANAGE_STATUS_LOCK,
    ADMIN_PERMISSIONS.USER_MANAGE_STATUS_UNLOCK,
  ].some(hasPermission);
  const canManageAuthorization = [
    ADMIN_PERMISSIONS.USER_AUTHORIZATION_GRANT,
    ADMIN_PERMISSIONS.USER_AUTHORIZATION_ADJUST,
    ADMIN_PERMISSIONS.USER_AUTHORIZATION_REVOKE,
    ADMIN_PERMISSIONS.USER_AUTHORIZATION_REATTACH,
  ].some(hasPermission);
  const canViewAuthorization = hasPermission(ADMIN_PERMISSIONS.USER_AUTHORIZATION_VIEW);
  const canResetPassword = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_RESET_PASSWORD);
  const [activeDialog, setActiveDialog] = useState<DialogName>(null);
  const [detailsDraft, setDetailsDraft] = useState<UserDetailsDraft>({ name: '', phone: '', email: '', orgUnitId: '' });
  const [statusDraft, setStatusDraft] = useState<UserStatus>('ACTIVE');
  const [targetOrgUnitId, setTargetOrgUnitId] = useState('');
  const [targetDepartmentId, setTargetDepartmentId] = useState('');
  const [organizationMoveStrategy, setOrganizationMoveStrategy] = useState<UserOrganizationMoveStrategy>('REVOKE_ALL');
  const [organizationMovePreview, setOrganizationMovePreview] = useState<UserOrganizationMovePreview | null>(null);
  const [passwordDraft, setPasswordDraft] = useState('');
  const [passwordConfirmationDraft, setPasswordConfirmationDraft] = useState('');
  const [dialogError, setDialogError] = useState('');
  const [message, setMessage] = useState('');
  const [showAllAuthorizationGrants, setShowAllAuthorizationGrants] = useState(false);

  const detailQuery = useQuery({
    queryKey: ['admin-user-detail', userId],
    queryFn: () => fetchAdminUserDetail(userId!),
    enabled: canView && !!userId,
  });
  const authorizationQuery = useQuery({
    queryKey: ['admin-user-authorization', userId],
    queryFn: () => fetchAdminUserAuthorization(userId!),
    enabled: canViewAuthorization && !!userId,
  });
  const orgUnitsQuery = useQuery({
    queryKey: ['admin-org-units-for-users'],
    queryFn: fetchAdminOrgUnits,
    enabled: canMoveOrganization && activeDialog === 'organization',
    staleTime: 1000 * 60 * 5,
  });
  const departmentsQuery = useQuery({
    queryKey: ['admin-departments-for-users'],
    queryFn: fetchAdminDepartments,
    enabled: canMoveDepartment && activeDialog === 'department',
    staleTime: 1000 * 60 * 5,
  });

  const updateMutation = useMutation({
    mutationFn: async ({ payload }: UpdateVariables) => {
      if (!userId) throw new Error('用户标识无效。');
      return updateAdminUserAdministration(userId, payload);
    },
    onSuccess: async (saved, variables) => {
      queryClient.setQueryData(['admin-user-detail', userId], saved);
      setActiveDialog(null);
      setMessage(variables.successMessage);
      await queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
  });

  const organizationMovePreviewMutation = useMutation({
    mutationFn: async (payload: UserOrganizationMoveRequest) => {
      if (!userId) throw new Error('用户标识无效。');
      return previewAdminUserOrganizationMove(userId, payload);
    },
    onSuccess: setOrganizationMovePreview,
  });

  const organizationMoveMutation = useMutation({
    mutationFn: async (payload: UserOrganizationMoveRequest) => {
      if (!userId) throw new Error('用户标识无效。');
      return executeAdminUserOrganizationMove(userId, payload);
    },
    onSuccess: async (saved) => {
      queryClient.setQueryData(['admin-user-detail', userId], saved);
      setOrganizationMovePreview(null);
      setActiveDialog(null);
      setMessage('所属组织及相关授权已更新。');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-users'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-user-authorization', userId] }),
      ]);
    },
  });

  const departmentMoveMutation = useMutation({
    mutationFn: async (targetDepartment: string | null) => {
      if (!userId || !detailQuery.data) throw new Error('用户标识无效。');
      return moveAdminUserDepartment(userId, {
        targetDepartmentId: targetDepartment,
        expectedAuthorizationVersion: detailQuery.data.user.authorizationVersion,
      });
    },
    onSuccess: async (saved) => {
      queryClient.setQueryData(['admin-user-detail', userId], saved);
      setActiveDialog(null);
      setMessage('所属部门已更新。');
      await queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
  });

  const resetPasswordMutation = useMutation({
    mutationFn: async ({ newPassword, confirmPassword }: { newPassword: string; confirmPassword: string }) => {
      if (!userId) throw new Error('用户标识无效。');
      if (!evaluatePassword(newPassword, detailQuery.data?.user, passwordPolicy).valid) {
        throw new Error('新密码不符合安全策略，请按提示调整。');
      }
      if (!passwordsMatch(newPassword, confirmPassword)) {
        throw new Error('两次输入的新密码不一致。');
      }
      await resetAdminUserPassword(userId, newPassword, confirmPassword);
    },
    onSuccess: () => {
      setPasswordDraft('');
      setPasswordConfirmationDraft('');
      setActiveDialog(null);
      setMessage('用户密码已重置。');
    },
  });

  const openDialog = (dialogName: Exclude<DialogName, null>) => {
    const detail = detailQuery.data;
    if (!detail) return;
    setDialogError('');
    setMessage('');
    updateMutation.reset();
    organizationMovePreviewMutation.reset();
    organizationMoveMutation.reset();
    departmentMoveMutation.reset();
    resetPasswordMutation.reset();
    if (dialogName === 'details') {
      setDetailsDraft({
        name: detail.user.name || '',
        phone: detail.user.phone || '',
        email: detail.user.email || '',
        orgUnitId: detail.user.orgUnitId || '',
      });
    } else if (dialogName === 'status') {
      setStatusDraft(detail.user.status);
    } else if (dialogName === 'organization') {
      setTargetOrgUnitId(detail.user.orgUnitId || '');
      setOrganizationMoveStrategy('REVOKE_ALL');
      setOrganizationMovePreview(null);
    } else if (dialogName === 'department') {
      setTargetDepartmentId(detail.user.departmentId || '');
    } else {
      setPasswordDraft('');
      setPasswordConfirmationDraft('');
    }
    setActiveDialog(dialogName);
  };

  const closeDialog = () => {
    if (updateMutation.isPending || organizationMovePreviewMutation.isPending
      || organizationMoveMutation.isPending || departmentMoveMutation.isPending
      || resetPasswordMutation.isPending) return;
    setActiveDialog(null);
    setDialogError('');
  };

  const saveDetails = () => {
    try {
      validateUserDetails(detailsDraft);
      setDialogError('');
      updateMutation.mutate({
        payload: {
          details: {
            name: detailsDraft.name.trim(),
            phone: detailsDraft.phone.trim(),
            email: detailsDraft.email.trim() || null,
            orgUnitId: detailsDraft.orgUnitId,
          },
        },
        successMessage: '个人信息已保存。',
      });
    } catch (error) {
      setDialogError(formatApiErrorMessage(error, '个人信息填写不正确。'));
    }
  };

  const saveStatus = () => {
    setDialogError('');
    updateMutation.mutate({ payload: { status: statusDraft }, successMessage: '账号状态已保存。' });
  };

  const previewOrganization = () => {
    if (!targetOrgUnitId || targetOrgUnitId === detail.user.orgUnitId) {
      return setDialogError('请选择不同于当前归属的目标组织。');
    }
    if (!organizationMoveStrategy) return setDialogError('请选择既有授权处理策略。');
    setDialogError('');
    organizationMovePreviewMutation.mutate({
      targetOrgUnitId,
      strategy: organizationMoveStrategy,
      expectedAuthorizationVersion: detail.user.authorizationVersion,
    });
  };

  const saveOrganization = () => {
    if (!organizationMovePreview) return previewOrganization();
    setDialogError('');
    organizationMoveMutation.mutate({
      targetOrgUnitId,
      strategy: organizationMoveStrategy,
      expectedAuthorizationVersion: organizationMovePreview.expectedAuthorizationVersion,
    });
  };

  if (!canView) {
    return <Screen><Card><Text style={{ color: colors.muted }}>当前账号没有查看用户详情的权限。</Text></Card></Screen>;
  }
  if (!userId) {
    return <Screen><Card><Text style={{ color: colors.danger }}>用户标识无效。</Text></Card></Screen>;
  }
  if (detailQuery.isLoading && !detailQuery.data) {
    return <Screen><ActivityIndicator color={colors.primary} /></Screen>;
  }
  if (detailQuery.error || !detailQuery.data) {
    return (
      <Screen>
        <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(detailQuery.error, '用户详情加载失败，请稍后重试。')}</Text></Card>
        <AppButton title="重新加载" onPress={() => detailQuery.refetch()} />
      </Screen>
    );
  }

  const detail = detailQuery.data;
  const editable = detail.user.status !== 'DELETED';
  const capability = (action: string) => detail.capabilities.actions[action] ?? {
    allowed: false,
    reasonCode: 'CAPABILITY_UNAVAILABLE',
    message: '服务端未返回该管理动作的判定结果。',
  };
  const updateError = updateMutation.error
    ? formatApiErrorMessage(updateMutation.error, '保存用户失败，请稍后重试。')
    : '';
  const organizationMoveError = organizationMovePreviewMutation.error || organizationMoveMutation.error;
  const organizationMoveErrorMessage = organizationMoveError
    ? formatApiErrorMessage(organizationMoveError, '组织迁移失败，请重新预览后再试。')
    : '';
  const passwordError = resetPasswordMutation.error
    ? formatApiErrorMessage(resetPasswordMutation.error, '重置密码失败，请稍后重试。')
    : '';
  const optionError = orgUnitsQuery.error || departmentsQuery.error;
  const optionErrorMessage = optionError ? formatApiErrorMessage(optionError, '加载编辑选项失败，请稍后重试。') : '';
  const targetEditable = editable && [
    'UPDATE_DETAILS', 'UPDATE_IDENTITY', 'MOVE_ORGANIZATION', 'MOVE_DEPARTMENT', 'UPDATE_STATUS',
    'RESET_PASSWORD', 'MANAGE_ACCESS', 'UPDATE_CONTACT',
  ].some(action => capability(action).allowed);
  const authorizationGrants = filterAuthorizationGrants(authorizationQuery.data?.grants);
  const visibleAuthorizationGrants = showAllAuthorizationGrants
    ? authorizationGrants
    : authorizationGrants.slice(0, AUTHORIZATION_GRANT_PREVIEW_LIMIT);
  const hiddenAuthorizationGrantCount = Math.max(0, authorizationGrants.length - AUTHORIZATION_GRANT_PREVIEW_LIMIT);
  const selectedOrganizationMoveStrategy = ORGANIZATION_MOVE_STRATEGY_BY_VALUE[organizationMoveStrategy];
  const organizationMoveChanges = organizationMovePreview?.authorization.changes ?? [];
  const organizationMoveChangeCounts = authorizationChangeCounts(organizationMoveChanges);
  const organizationMoveWorking = organizationMovePreviewMutation.isPending || organizationMoveMutation.isPending;

  return (
    <>
      <Screen>
        <PageHeader
          title={detail.user.name || detail.user.username}
          subtitle={`${detail.user.username} · ${detail.user.orgUnitName || '未分配组织'} · ${USER_STATUS_LABELS[detail.user.status]}`}
          accentColor={detail.user.systemProtected ? colors.management : colors.primary}
        />

        {targetEditable && (
          <Card style={{ gap: 10 }}>
            <Text style={{ color: colors.text, fontSize: 18, fontWeight: '900' }}>用户操作</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {(canUpdateDetails || canUpdateContact) && (
                <View style={{ width: '48%', flexGrow: 1 }}><AppButton title="编辑个人信息"
                  disabled={!(canUpdateDetails && capability('UPDATE_DETAILS').allowed)
                    && !(canUpdateContact && capability('UPDATE_CONTACT').allowed)}
                  onPress={() => openDialog('details')} variant="secondary" /></View>
              )}
              {canUpdateContact && (
                <View style={{ width: '48%', flexGrow: 1 }}><AppButton title="邮件通知偏好"
                  disabled={!capability('UPDATE_CONTACT').allowed}
                  onPress={() => openDialog('email-notifications')} variant="secondary" /></View>
              )}
              {canMoveOrganization && (
                <View style={{ width: '48%', flexGrow: 1 }}><AppButton title="调整所属组织" disabled={!capability('MOVE_ORGANIZATION').allowed} onPress={() => openDialog('organization')} variant="secondary" /></View>
              )}
              {canMoveDepartment && (
                <View style={{ width: '48%', flexGrow: 1 }}><AppButton title="调整所属部门" disabled={!capability('MOVE_DEPARTMENT').allowed} onPress={() => openDialog('department')} variant="secondary" /></View>
              )}
              {canManageAuthorization && canViewAuthorization && (
                <View style={{ width: '48%', flexGrow: 1 }}><AppButton title="调整授权" disabled={!capability('MANAGE_ACCESS').allowed} onPress={() => openDialog('authorization')} variant="secondary" /></View>
              )}
              {canUpdateStatus && (
                <View style={{ width: '48%', flexGrow: 1 }}><AppButton title="调整账号状态" disabled={!capability('UPDATE_STATUS').allowed} onPress={() => openDialog('status')} variant="secondary" /></View>
              )}
              {canResetPassword && (
                <View style={{ width: '48%', flexGrow: 1 }}><AppButton title="重置登录密码" disabled={!capability('RESET_PASSWORD').allowed} onPress={() => openDialog('password')} variant="secondary" /></View>
              )}
            </View>
          </Card>
        )}

        <Card style={{ gap: 10 }}>
          <Text style={{ color: colors.text, fontSize: 18, fontWeight: '900' }}>当前权限</Text>
          <Text style={{ color: colors.muted }}>
            {authorizationQuery.data
              ? `${authorizationQuery.data.effectivePermissionCount} 项有效权限，共 ${authorizationGrants.length} 条可见授权`
              : '无权查看授权详情'}
          </Text>
          {authorizationQuery.isLoading && <ActivityIndicator color={colors.primary} />}
          {visibleAuthorizationGrants.map((grant) => (
            <View key={grant.id} style={{ gap: 5, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 11 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <Text style={{ color: colors.text, fontWeight: '900', flex: 1 }}>{grant.permissionDisplayName}</Text>
                <View style={{ borderRadius: 999, backgroundColor: grant.manageable ? `${colors.primary}14` : colors.surfaceAlt, paddingHorizontal: 8, paddingVertical: 4 }}>
                  <Text style={{ color: grant.manageable ? colors.primary : colors.muted, fontSize: 11, fontWeight: '800' }}>
                    {grant.manageable ? '可管理' : '只读'}
                  </Text>
                </View>
              </View>
              <Text selectable style={{ color: colors.muted, fontSize: 11 }}>{grant.permissionName}</Text>
              <Text style={{ color: colors.muted, fontSize: 12 }}>
                {grant.effective ? '有效' : grant.status} · {authorizationIssuerLabel(grant)} · {authorizationGrantExpiryLabel(grant)}
                {grant.grantOption ? ' · 可转授' : ''}
              </Text>
            </View>
          ))}
          {authorizationQuery.data && authorizationGrants.length === 0 && !authorizationQuery.isLoading ? (
            <Text style={{ color: colors.muted }}>没有当前账号可见的权限。</Text>
          ) : null}
          {hiddenAuthorizationGrantCount > 0 ? (
            <AppButton
              title={showAllAuthorizationGrants ? '收起权限清单' : `展开其余 ${hiddenAuthorizationGrantCount} 条权限`}
              onPress={() => setShowAllAuthorizationGrants(current => !current)}
              variant="secondary"
            />
          ) : null}
          {!!authorizationQuery.data?.grants.some(grant => !grant.manageable) && (
            <View style={{ borderWidth: 1, borderColor: '#99f6e4', borderRadius: 8, backgroundColor: '#f0fdfa', padding: 10 }}>
              <Text style={{ color: colors.management, fontSize: 12, lineHeight: 18 }}>系统基线和非当前授权链来源可查看但不能在此入口调整。</Text>
            </View>
          )}
        </Card>

        {!targetEditable && (
          <Card><Text selectable style={{ color: colors.warning }}>
            {capability('MANAGE_ACCESS').message || '该账号由上级管理域维护，当前仅可查看。'}
          </Text></Card>
        )}

        <Card style={{ gap: 10 }}>
          <Text style={{ color: colors.text, fontSize: 18, fontWeight: '900' }}>账号与联系方式</Text>
          <InfoRow label="用户标识" value={detail.user.id} />
          <InfoRow label="用户名" value={detail.user.username} />
          <InfoRow label="手机号" value={detail.user.phone} />
          <InfoRow label="邮箱" value={detail.user.email} />
          <InfoRow label="所属组织" value={detail.user.orgUnitName} />
          <InfoRow label="所属部门" value={detail.user.departmentName || '组织直属'} />
          <InfoRow label="账号状态" value={`${USER_STATUS_LABELS[detail.user.status]}${detail.user.systemProtected ? ' · 系统保护' : ''}`} />
          <InfoRow label="授权版本" value={detail.user.authorizationVersion} />
          <InfoRow label="创建时间" value={formatDateTime(detail.user.createdAt)} />
          <InfoRow label="最后更新" value={formatDateTime(detail.user.updatedAt)} />
        </Card>

        <Card style={{ gap: 12 }}>
          <Text style={{ color: colors.text, fontSize: 18, fontWeight: '900' }}>个人工单分析</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}>
            <Metric label="累计提单" value={formatMetric(detail.performance.reportedTotal)} secondary={`近30天 ${formatMetric(detail.performance.reportedLast30Days)}`} />
            <Metric label="未闭环提单" value={formatMetric(detail.performance.reportedOpen)} secondary={`已闭环 ${formatMetric(detail.performance.reportedClosed)}`} />
            <Metric label="参与处理工单" value={formatMetric(detail.performance.assignedTotal)} secondary={`当前在办 ${formatMetric(detail.performance.activeAssignments)}`} />
            <Metric label="累计回单" value={formatMetric(detail.performance.responsesSubmitted)} secondary={`近30天 ${formatMetric(detail.performance.responsesLast30Days)}`} />
            <Metric label="24小时内回单率" value={formatMetric(detail.performance.responseWithin24HourRate, '%')} secondary={`${formatMetric(detail.performance.responsesWithin24Hours)} 次`} />
            <Metric label="平均回单历时" value={formatMetric(detail.performance.averageResponseMinutes, ' 分钟')} />
            <Metric label="验收通过率" value={formatMetric(detail.performance.verificationApprovalRate, '%')} secondary={`${formatMetric(detail.performance.verificationsCompleted)} 次验收`} />
            <Metric label="提单平均闭环" value={formatMetric(detail.performance.averageReportedClosureHours, ' 小时')} />
          </View>
        </Card>

        {!!message && <Card><Text style={{ color: colors.primary, fontWeight: '800' }}>{message}</Text></Card>}
      </Screen>

      {activeDialog === 'email-notifications' && canUpdateContact && editable && capability('UPDATE_CONTACT').allowed && currentUser && (
        <EmailNotificationPreferencesPanel
          key={userId} ownerKey={currentUser.membershipId || currentUser.username}
          targetUserId={userId} targetName={`${detail.user.name || detail.user.username}（${detail.user.username}）`}
          onClose={closeDialog}
        />
      )}
      <EditModal
        visible={activeDialog === 'details'}
        title="编辑个人信息"
        primaryActionLabel="保存个人信息"
        onPrimaryAction={saveDetails}
        onClose={closeDialog}
        working={updateMutation.isPending}
        primaryDisabled={orgUnitsQuery.isLoading || !!orgUnitsQuery.error}
      >
        {!!(dialogError || updateError || optionErrorMessage) && <Text selectable style={{ color: colors.danger }}>{dialogError || updateError || optionErrorMessage}</Text>}
        {orgUnitsQuery.isLoading ? <ActivityIndicator color={colors.primary} /> : (
          <>
            <TextField label="姓名" value={detailsDraft.name} onChangeText={(name) => setDetailsDraft({ ...detailsDraft, name })} editable={!updateMutation.isPending && canUpdateDetails && capability('UPDATE_DETAILS').allowed} />
            <TextField label="手机号" value={detailsDraft.phone} onChangeText={(phone) => setDetailsDraft({ ...detailsDraft, phone })} keyboardType="phone-pad" maxLength={11} required editable={!updateMutation.isPending && canUpdateContact && capability('UPDATE_CONTACT').allowed} />
            <TextField label="邮箱" value={detailsDraft.email} onChangeText={(email) => setDetailsDraft({ ...detailsDraft, email })} keyboardType="email-address" autoCapitalize="none" maxLength={254} editable={!updateMutation.isPending && canUpdateContact && capability('UPDATE_CONTACT').allowed} />
          </>
        )}
      </EditModal>

      <EditModal
        visible={activeDialog === 'department'}
        title="调整所属部门"
        primaryActionLabel="确认调整"
        onPrimaryAction={() => departmentMoveMutation.mutate(targetDepartmentId || null)}
        onClose={closeDialog}
        working={departmentMoveMutation.isPending}
        primaryDisabled={departmentsQuery.isLoading || targetDepartmentId === (detail.user.departmentId || '')}
      >
        {!!departmentMoveMutation.error && <Text selectable style={{ color: colors.danger }}>
          {formatApiErrorMessage(departmentMoveMutation.error, '调整所属部门失败，请刷新后重试。')}
        </Text>}
        <Text style={{ color: colors.muted }}>部门仅用于组织内部协作、派单和统计，不改变用户的组织数据范围。</Text>
        {departmentsQuery.isLoading ? <ActivityIndicator color={colors.primary} /> : (
          <SelectField
            label="所属部门"
            value={targetDepartmentId}
            options={[
              { value: '', label: '组织直属' },
              ...(departmentsQuery.data ?? [])
                .filter((department) => department.orgUnitId === detail.user.orgUnitId && department.status === 'ACTIVE')
                .map((department) => ({ value: department.id, label: `${department.name}（${department.code}）` })),
            ]}
            onChange={setTargetDepartmentId}
          />
        )}
      </EditModal>

      <EditModal
        visible={activeDialog === 'organization'}
        title="调整所属组织"
        primaryActionLabel={organizationMovePreview ? '确认迁移' : '预览变化'}
        onPrimaryAction={saveOrganization}
        onClose={closeDialog}
        working={organizationMoveWorking}
        primaryDisabled={orgUnitsQuery.isLoading || !!orgUnitsQuery.error
          || !targetOrgUnitId || targetOrgUnitId === detail.user.orgUnitId || !organizationMoveStrategy}
      >
        {!!(dialogError || organizationMoveErrorMessage || optionErrorMessage) && (
          <Text selectable style={{ color: colors.danger }}>
            {dialogError || organizationMoveErrorMessage || optionErrorMessage}
          </Text>
        )}
        {organizationMovePreviewMutation.isPending ? (
          <View style={{ minHeight: 180, justifyContent: 'center', alignItems: 'center', gap: 10 }}>
            <ActivityIndicator color={colors.primary} />
            <Text style={{ color: colors.muted }}>正在计算组织、授权和工单影响…</Text>
          </View>
        ) : orgUnitsQuery.isLoading ? <ActivityIndicator color={colors.primary} /> : !organizationMovePreview ? (
          <>
            <Text style={{ color: colors.muted }}>
              先选择目标组织和授权处理方式，再由服务端计算实际变化；预览不会写入任何数据。
            </Text>
            <SelectField
              label="目标组织"
              value={targetOrgUnitId}
              options={(orgUnitsQuery.data ?? []).map((organization) => ({ label: organization.name, value: organization.id }))}
              onChange={(value) => {
                setTargetOrgUnitId(value);
                setOrganizationMovePreview(null);
              }}
            />
            <SelectField
              label="授权处理策略"
              value={organizationMoveStrategy}
              options={ORGANIZATION_MOVE_STRATEGIES.map((strategy) => ({
                label: strategy.label,
                value: strategy.value,
              }))}
              onChange={(value) => {
                setOrganizationMoveStrategy(value as UserOrganizationMoveStrategy);
                setOrganizationMovePreview(null);
              }}
            />
            <Card style={{ gap: 6, backgroundColor: colors.surfaceAlt }}>
              <Text style={{ color: colors.text, fontWeight: '900' }}>{selectedOrganizationMoveStrategy.label}</Text>
              <Text style={{ color: colors.muted, lineHeight: 21 }}>{selectedOrganizationMoveStrategy.description}</Text>
            </Card>
          </>
        ) : (
          <View style={{ gap: 12 }}>
            <Text style={{ color: colors.success, fontWeight: '900' }}>预检完成，请核对以下实际变化。</Text>
            <Card style={{ gap: 6, backgroundColor: colors.surfaceAlt }}>
              <Text style={{ color: colors.text, fontSize: 17, fontWeight: '900' }}>
                授权处理结果：{selectedOrganizationMoveStrategy.label}
              </Text>
              <Text style={{ color: colors.muted, lineHeight: 21 }}>
                {selectedOrganizationMoveStrategy.description}
              </Text>
            </Card>
            <Card style={{ gap: 8 }}>
              <Text style={{ color: colors.text, fontSize: 17, fontWeight: '900' }}>所属组织</Text>
              <Text style={{ color: colors.text }}>
                {organizationMovePreview.organization.beforeOrgUnitName} → {organizationMovePreview.organization.afterOrgUnitName}
              </Text>
            </Card>
            <Card style={{ gap: 8 }}>
              <Text style={{ color: colors.text, fontSize: 17, fontWeight: '900' }}>授权变化</Text>
              <Text style={{ color: colors.muted }}>
                调整 {organizationMoveChangeCounts.UPDATE} · 新增 {organizationMoveChangeCounts.ADD} · 撤销 {organizationMoveChangeCounts.REMOVE}
              </Text>
              <Text style={{ color: colors.muted }}>
                生效授权数量：{organizationMovePreview.authorization.beforeActiveGrantCount} → {organizationMovePreview.authorization.afterActiveGrantCount}
              </Text>
              {organizationMoveChanges.length === 0 ? (
                <Text style={{ color: organizationMoveStrategy === 'KEEP' ? colors.warning : colors.muted }}>
                  {organizationMoveStrategy === 'KEEP'
                    ? '原子权限保持不变，数据视野将按新的所属组织重新计算。'
                    : '当前没有需要调整的授权。'}
                </Text>
              ) : organizationMoveChanges.map((change, index) => {
                const snapshot = change.after || change.before;
                const kindLabel = { ADD: '新增', UPDATE: '调整', REMOVE: '撤销' }[change.kind];
                return (
                  <View key={`${snapshot?.permissionName || 'permission'}-${index}`}
                    style={{ gap: 5, padding: 10, borderWidth: 1, borderColor: colors.border, borderRadius: 8 }}>
                    <Text style={{ color: change.kind === 'REMOVE' ? colors.danger : colors.primary, fontWeight: '900' }}>
                      {kindLabel} · {snapshot?.permissionDisplayName || snapshot?.permissionName}
                    </Text>
                    <Text style={{ color: colors.muted }}>{change.kind === 'REMOVE' ? '该 Grant 将被撤销' : '该 Grant 的授权事实将更新'}</Text>
                  </View>
                );
              })}
            </Card>
            <Card style={{ gap: 5 }}>
              <Text style={{ color: colors.text, fontSize: 17, fontWeight: '900' }}>工单影响</Text>
              <Text style={{ color: colors.text }}>未闭环提单：{organizationMovePreview.workOrders.openReportedWorkOrders}</Text>
              <Text style={{ color: colors.text }}>待接责任：{organizationMovePreview.workOrders.pendingAcceptanceAssignments}</Text>
              <Text style={{ color: colors.text }}>处理中责任：{organizationMovePreview.workOrders.acceptedAssignments}</Text>
              <Text style={{ color: colors.muted }}>历史归属和当前处理责任不会自动迁移或改派。</Text>
            </Card>
            <Card style={{ gap: 5 }}>
              <Text style={{ color: colors.text, fontSize: 17, fontWeight: '900' }}>授权影响</Text>
              <Text style={{ color: colors.text }}>有效权限：{organizationMovePreview.management.activeGrantCount}</Text>
              <Text style={{ color: colors.muted }}>
                {organizationMovePreview.management.reauthorizationRecommended ? '迁移后建议按新职责重新核对授权。' : '当前迁移方向无需强制重授权。'}
              </Text>
            </Card>
            {organizationMovePreview.notices.map((notice) => (
              <View key={notice.code} style={{ gap: 4, padding: 12, borderRadius: 8, backgroundColor: colors.surfaceAlt }}>
                <Text style={{ color: notice.severity === 'WARNING' ? colors.warning : colors.primary, fontWeight: '900' }}>
                  {notice.title}
                </Text>
                <Text style={{ color: colors.muted, lineHeight: 20 }}>{notice.detail}</Text>
              </View>
            ))}
            <AppButton title="返回修改" variant="secondary" disabled={organizationMoveWorking}
              onPress={() => setOrganizationMovePreview(null)} />
          </View>
        )}
      </EditModal>

      <EditModal
        visible={activeDialog === 'status'}
        title="调整账号状态"
        primaryActionLabel="保存账号状态"
        onPrimaryAction={saveStatus}
        onClose={closeDialog}
        working={updateMutation.isPending}
        primaryDisabled={statusDraft === detail.user.status}
      >
        {!!(dialogError || updateError) && <Text selectable style={{ color: colors.danger }}>{dialogError || updateError}</Text>}
        <SelectField
          label="账号状态"
          value={statusDraft}
          options={USER_STATUSES.map((status) => ({ label: USER_STATUS_LABELS[status], value: status }))}
          onChange={(status) => setStatusDraft(status as UserStatus)}
        />
      </EditModal>

      <AuthorizationEditor
        visible={activeDialog === 'authorization'}
        userId={userId}
        onClose={closeDialog}
        onApplied={async (authorization) => {
          queryClient.setQueryData(['admin-user-authorization', userId], authorization);
          setActiveDialog(null);
          setMessage('用户授权已按预览计划更新。');
          await detailQuery.refetch();
          await queryClient.invalidateQueries({ queryKey: ['admin-users'] });
        }}
      />

      <EditModal
        visible={activeDialog === 'password'}
        title="重置登录密码"
        primaryActionLabel="确认重置"
        onPrimaryAction={() => resetPasswordMutation.mutate({ newPassword: passwordDraft, confirmPassword: passwordConfirmationDraft })}
        onClose={closeDialog}
        working={resetPasswordMutation.isPending}
        primaryDisabled={!evaluatePassword(passwordDraft, detail.user, passwordPolicy).valid || !passwordsMatch(passwordDraft, passwordConfirmationDraft)}
      >
        {!!(dialogError || passwordError) && <Text selectable style={{ color: colors.danger }}>{dialogError || passwordError}</Text>}
        <TextField label="新密码" value={passwordDraft} onChangeText={setPasswordDraft} secureTextEntry maxLength={passwordPolicy.maxLength} editable={!resetPasswordMutation.isPending} />
        <PasswordPolicyHint password={passwordDraft} context={detail.user} policy={passwordPolicy} />
        <TextField label="确认新密码" value={passwordConfirmationDraft} onChangeText={setPasswordConfirmationDraft} secureTextEntry maxLength={passwordPolicy.maxLength} editable={!resetPasswordMutation.isPending} />
        {!!passwordConfirmationDraft && !passwordsMatch(passwordDraft, passwordConfirmationDraft) && (
          <Text style={{ color: colors.danger }}>两次输入的新密码不一致。</Text>
        )}
      </EditModal>
    </>
  );
}

function Metric({ label, value, secondary }: { label: string; value: string; secondary?: string }) {
  return (
    <View style={{ width: '47%', minWidth: 136, gap: 3 }}>
      <Text style={{ color: colors.muted, fontSize: 13 }}>{label}</Text>
      <Text style={{ color: colors.text, fontSize: 20, fontWeight: '900' }}>{value}</Text>
      {!!secondary && <Text style={{ color: colors.muted, fontSize: 12 }}>{secondary}</Text>}
    </View>
  );
}

function formatMetric(value: number, suffix = '') {
  const numericValue = Number(value || 0);
  const formatted = Number.isFinite(numericValue)
    ? numericValue.toLocaleString('zh-CN', { maximumFractionDigits: 1 })
    : '0';
  return `${formatted}${suffix}`;
}

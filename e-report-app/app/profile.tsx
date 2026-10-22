import { useMutation, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Text, View } from 'react-native';

import { formatApiErrorMessage } from '@/api/http';
import {
  changeCurrentPassword,
  fetchAuthSessions,
  revokeAuthSession,
  revokeOtherAuthSessions,
} from '@/auth/auth-api';
import { useAuth } from '@/auth/auth-provider';
import { evaluatePassword } from '@/auth/password-policy';
import { ApiTokenPanel } from '@/features/profile/api-token-panel';
import { EmailNotificationPreferencesPanel } from '@/features/profile/email-notification-preferences-panel';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import type { AuthSessionView } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { BrandLoading } from '@/ui/brand';
import { Card } from '@/ui/card';
import { PasswordPolicyHint } from '@/ui/password-policy-hint';
import { Screen } from '@/ui/screen';
import { PageHeader } from '@/ui/page-header';
import { SectionTabs, SectionContent } from '@/ui/section-tabs';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';

export default function ProfileScreen() {
  const router = useRouter();
  const {
    currentUser,
      isAuthenticated,
      isLoading,
      logout,
      updateProfile,
      hasPermission,
      passwordPolicy,
  } = useAuth();
  const [name, setName] = useState(currentUser?.name || '');
  const [phone, setPhone] = useState(currentUser?.phone || '');
  const [email, setEmail] = useState(currentUser?.email || '');
  const [profileMessage, setProfileMessage] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [sessionMessage, setSessionMessage] = useState('');
  const [sessionPageNumber, setSessionPageNumber] = useState(0);
  const [activeSection, setActiveSection] = useState<string | null>(null);
  const selectedSection = activeSection ?? (currentUser?.passwordChangeRequired ? 'security' : 'profile');
  const sessionPageSize = 10;

  useEffect(() => {
    if (!isLoading && !isAuthenticated) router.replace('/login');
  }, [isAuthenticated, isLoading, router]);

  useEffect(() => {
    setName(currentUser?.name || '');
    setPhone(currentUser?.phone || '');
    setEmail(currentUser?.email || '');
  }, [currentUser?.email, currentUser?.name, currentUser?.phone]);

  const sessionsQuery = useQuery({
    queryKey: ['auth-sessions', sessionPageNumber, sessionPageSize],
    queryFn: () => fetchAuthSessions(sessionPageNumber, sessionPageSize),
    enabled: isAuthenticated,
    staleTime: 15000,
  });

  const normalizedName = name.trim();
  const normalizedPhone = phone.trim();
  const normalizedEmail = email.trim();
  const phoneValid = /^1[3-9][0-9]{9}$/.test(normalizedPhone);
  const emailValid = !normalizedEmail || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail);
  const hasChanges = normalizedName !== (currentUser?.name || '')
    || normalizedPhone !== (currentUser?.phone || '')
    || normalizedEmail !== (currentUser?.email || '');
  const passwordContext = useMemo(() => ({
    username: currentUser?.username,
    name: currentUser?.name,
    phone: currentUser?.phone,
    email: currentUser?.email,
  }), [currentUser]);
  const passwordEvaluation = useMemo(
    () => evaluatePassword(newPassword, passwordContext, passwordPolicy),
    [newPassword, passwordContext, passwordPolicy],
  );
  const passwordsMatch = newPassword.length > 0 && newPassword === confirmPassword;
  const canViewApiTokens = hasPermission(ADMIN_PERMISSIONS.API_TOKEN_VIEW);
  const canCreateApiTokens = hasPermission(ADMIN_PERMISSIONS.API_TOKEN_CREATE);
  const canRevokeApiTokens = hasPermission(ADMIN_PERMISSIONS.API_TOKEN_REVOKE);
  const canManageApiTokens = canViewApiTokens || canCreateApiTokens || canRevokeApiTokens;

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!normalizedName) throw new Error('姓名不能为空。');
      if (!phoneValid) throw new Error('请输入11位中国大陆手机号。');
      if (!emailValid) throw new Error('请输入正确的邮箱地址。');
      return updateProfile(normalizedName, normalizedPhone, normalizedEmail || null);
    },
    onSuccess: () => setProfileMessage('个人信息已保存。'),
  });

  const passwordMutation = useMutation({
    mutationFn: () => changeCurrentPassword(currentPassword, newPassword),
    onSuccess: async () => {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      await logout();
      router.replace({ pathname: '/login', params: { returnTo: '/' } });
    },
  });

  const revokeMutation = useMutation({
    mutationFn: revokeAuthSession,
    onSuccess: async () => {
      await sessionsQuery.refetch();
      setSessionMessage('登录会话已撤销。');
    },
  });

  const revokeOthersMutation = useMutation({
    mutationFn: revokeOtherAuthSessions,
    onSuccess: async () => {
      await sessionsQuery.refetch();
      setSessionMessage('其他登录会话已全部撤销。');
    },
  });

  if (isLoading || !isAuthenticated || !currentUser) {
    return <BrandLoading message="正在加载个人信息" />;
  }

  return (
    <Screen>
      {currentUser.passwordChangeRequired && (
        <View style={{ padding: 14, borderRadius: 8, backgroundColor: '#fff7ed', borderWidth: 1, borderColor: '#fdba74' }}>
          <Text style={{ color: colors.warning, fontWeight: '800', lineHeight: 21 }}>
            首次登录或密码已由管理员重置，请先设置新的个人密码。
          </Text>
        </View>
      )}

      <PageHeader title="个人信息" subtitle="联系方式、通知偏好与账号安全" />
      <SectionTabs label="个人设置分类" value={selectedSection} onChange={setActiveSection} options={[
        { value: 'profile', label: '基本资料' },
        ...(!currentUser.passwordChangeRequired ? [{ value: 'notifications', label: '邮件通知' }] : []),
        { value: 'security', label: '登录密码' },
        ...(!currentUser.passwordChangeRequired && canManageApiTokens ? [{ value: 'tokens', label: 'API Token' }] : []),
        { value: 'sessions', label: '登录设备' },
      ]} />
      <SectionContent value="profile" selected={selectedSection}>
        <Card>
          <Text style={{ color: colors.text, fontSize: 19, fontWeight: '900' }}>基本资料</Text>
          <ReadOnlyRow label="用户名" value={currentUser.username} />
          <TextField label="姓名" value={name} onChangeText={(value) => { setProfileMessage(''); setName(value); }}
                     maxLength={100} placeholder="请输入姓名" />
          <TextField label="手机号" value={phone} onChangeText={(value) => { setProfileMessage(''); setPhone(value); }}
                     keyboardType="phone-pad" maxLength={11} required
                     errorMessage={phone.length > 0 && !phoneValid ? '请输入11位中国大陆手机号' : undefined}
                     placeholder="请输入手机号" />
          <TextField label="邮箱" value={email} onChangeText={(value) => { setProfileMessage(''); setEmail(value); }}
                     keyboardType="email-address" autoCapitalize="none" maxLength={254}
                     errorMessage={email.length > 0 && !emailValid ? '请输入正确的邮箱地址' : undefined}
                     helperText="可选；邮件通知优先使用此地址" placeholder="请输入邮箱" />
          <ReadOnlyRow label="权限数量" value={`${currentUser.permissions?.length ?? 0} 项`} />
          {!!profileMessage && <Text style={{ color: colors.success, fontWeight: '800' }}>{profileMessage}</Text>}
          {!!saveMutation.error && (
            <Text selectable style={{ color: colors.danger }}>
              {formatApiErrorMessage(saveMutation.error, '个人信息保存失败，请稍后重试。')}
            </Text>
          )}
          <AppButton title={saveMutation.isPending ? '保存中' : '保存个人信息'} onPress={() => saveMutation.mutate()}
                     disabled={!hasChanges || !normalizedName || !phoneValid || !emailValid || saveMutation.isPending}
                     loading={saveMutation.isPending} />
        </Card>

      </SectionContent>
      <SectionContent value="notifications" selected={selectedSection}>
        {!currentUser.passwordChangeRequired && (
          <EmailNotificationPreferencesPanel key={currentUser.membershipId || currentUser.username} ownerKey={currentUser.membershipId || currentUser.username} />
        )}
      </SectionContent>
      <SectionContent value="security" selected={selectedSection}>
        <Card>
          <Text style={{ color: colors.text, fontSize: 19, fontWeight: '900' }}>登录密码</Text>
          <TextField label="当前密码" value={currentPassword} onChangeText={setCurrentPassword}
                     secureTextEntry autoCapitalize="none" maxLength={100} required />
          <TextField label="新密码" value={newPassword} onChangeText={setNewPassword}
                     secureTextEntry autoCapitalize="none" maxLength={passwordPolicy.maxLength} required />
          <PasswordPolicyHint password={newPassword} context={passwordContext} policy={passwordPolicy} />
          <TextField label="确认新密码" value={confirmPassword} onChangeText={setConfirmPassword}
                     secureTextEntry autoCapitalize="none" maxLength={passwordPolicy.maxLength} required
                     errorMessage={confirmPassword.length > 0 && !passwordsMatch ? '两次输入的新密码不一致' : undefined} />
          {!!passwordMutation.error && (
            <Text selectable style={{ color: colors.danger }}>
              {formatApiErrorMessage(passwordMutation.error, '密码修改失败，请检查后重试。')}
            </Text>
          )}
          <AppButton title={passwordMutation.isPending ? '修改中' : '修改密码'}
                     onPress={() => passwordMutation.mutate()}
                     disabled={!currentPassword || !passwordEvaluation.valid || !passwordsMatch || passwordMutation.isPending}
                     loading={passwordMutation.isPending} />
        </Card>

      </SectionContent>
      <SectionContent value="tokens" selected={selectedSection}>
        {!currentUser.passwordChangeRequired && canManageApiTokens && (
          <ApiTokenPanel
            ownerKey={currentUser.membershipId || currentUser.username}
            canView={canViewApiTokens}
            canCreate={canCreateApiTokens}
            canRevoke={canRevokeApiTokens}
          />
        )}

      </SectionContent>
      <SectionContent value="sessions" selected={selectedSection}>
        <Card style={{ gap: 12 }}>
          {!!sessionMessage && <Text style={{ color: colors.success, fontWeight: '800' }}>{sessionMessage}</Text>}
          {!!(revokeMutation.error || revokeOthersMutation.error) && (
            <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(revokeMutation.error || revokeOthersMutation.error, '会话撤销失败，请稍后重试。')}</Text>
          )}
          <View style={{ gap: 8 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <View style={{ flex: 1 }}>
                <Text style={{ color: colors.text, fontSize: 19, fontWeight: '900' }}>登录设备</Text>
                <Text style={{ color: colors.muted, fontSize: 13, marginTop: 3 }}>
                  共 {sessionsQuery.data?.page.totalElements ?? 0} 个登录会话
                </Text>
              </View>
              <AppButton title="刷新" onPress={() => sessionsQuery.refetch()} variant="ghost" disabled={sessionsQuery.isFetching} />
            </View>
            {(sessionsQuery.data?.activeOtherSessionCount ?? 0) > 0 && (
              <AppButton
                title={revokeOthersMutation.isPending ? '撤销中' : `撤销其他全部会话（${sessionsQuery.data?.activeOtherSessionCount}）`}
                onPress={() => revokeOthersMutation.mutate()}
                variant="danger"
                disabled={revokeOthersMutation.isPending}
                loading={revokeOthersMutation.isPending}
              />
            )}
            <AppButton
              title="退出当前账号"
              onPress={async () => {
                await logout();
                router.replace({ pathname: '/login', params: { returnTo: '/' } });
              }}
              variant="ghost"
            />
          </View>
          {!!sessionsQuery.error && (
            <Text selectable style={{ color: colors.danger }}>
              {formatApiErrorMessage(sessionsQuery.error, '登录设备加载失败，请稍后重试。')}
            </Text>
          )}
          {(sessionsQuery.data?.page.content || []).map(session => (
            <SessionRow key={session.id} session={session}
                        revoking={revokeMutation.isPending && revokeMutation.variables === session.id}
                        onRevoke={() => revokeMutation.mutate(session.id)} />
          ))}
          {!sessionsQuery.isLoading && !sessionsQuery.data?.page.content.length && (
            <Text style={{ color: colors.muted }}>暂无登录会话。</Text>
          )}
          {(sessionsQuery.data?.page.totalPages ?? 0) > 1 && (
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10, marginTop: 4 }}>
              <AppButton
                title="上一页"
                onPress={() => setSessionPageNumber(page => Math.max(0, page - 1))}
                variant="ghost"
                disabled={sessionPageNumber === 0 || sessionsQuery.isFetching}
              />
              <Text style={{ color: colors.muted, fontWeight: '700' }}>
                第 {sessionPageNumber + 1} / {sessionsQuery.data?.page.totalPages} 页
              </Text>
              <AppButton
                title="下一页"
                onPress={() => setSessionPageNumber(page => Math.min((sessionsQuery.data?.page.totalPages ?? 1) - 1, page + 1))}
                variant="ghost"
                disabled={sessionPageNumber >= (sessionsQuery.data?.page.totalPages ?? 1) - 1 || sessionsQuery.isFetching}
              />
            </View>
          )}
        </Card>

      </SectionContent>
    </Screen>
  );
}

function SessionRow({ session, revoking, onRevoke }: {
  session: AuthSessionView;
  revoking: boolean;
  onRevoke: () => void;
}) {
  return (
    <View style={{ borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 12, gap: 6 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
        <Text style={{ color: colors.text, fontWeight: '800', flex: 1 }}>{session.deviceName || '未知设备'}</Text>
        <Text style={{ color: session.status === 'ACTIVE' ? colors.success : colors.muted, fontWeight: '800' }}>
          {session.current ? '当前会话' : sessionStatusLabel(session.status)}
        </Text>
      </View>
      <Text style={{ color: colors.muted, fontSize: 13 }}>
        {[session.osVersion, session.appVersion && `应用 ${session.appVersion}`].filter(Boolean).join(' · ') || '网页会话'}
      </Text>
      <Text style={{ color: colors.muted, fontSize: 13 }}>最近活动：{formatDateTime(session.lastSeenAt)}</Text>
      {!session.current && session.status === 'ACTIVE' && (
        <AppButton title={revoking ? '撤销中' : '撤销会话'} onPress={onRevoke} variant="secondary" disabled={revoking} loading={revoking} />
      )}
    </View>
  );
}

function ReadOnlyRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={{ gap: 6 }}>
      <Text style={{ color: colors.text, fontWeight: '700', fontSize: 14 }}>{label}</Text>
      <View style={{ minHeight: 46, borderWidth: 1, borderColor: colors.border, borderRadius: 8,
        backgroundColor: colors.surfaceAlt, justifyContent: 'center', paddingHorizontal: 12, paddingVertical: 10 }}>
        <Text selectable style={{ color: colors.text, fontSize: 16 }}>{value}</Text>
      </View>
    </View>
  );
}

function sessionStatusLabel(status: AuthSessionView['status']) {
  return { ACTIVE: '有效', REVOKED: '已撤销', EXPIRED: '已过期', COMPROMISED: '存在风险' }[status];
}

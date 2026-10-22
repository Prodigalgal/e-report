import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Switch, Text, View } from 'react-native';

import {
  createAdminConfig,
  deleteAdminConfig,
  fetchAdminConfigScopes,
  fetchAdminConfigs,
  fetchAdminPasswordPolicy,
  fetchAdminNotificationSettings,
  updateAdminNotificationSwitch,
  updateDailyOutstandingReportSchedule,
  updateAdminConfig,
  updateAdminPasswordPolicy,
  deleteAdminPasswordPolicy,
  type ConfigFormPayload,
  type PasswordPolicyUpdatePayload,
} from '@/api/admin-api';
import { formatApiErrorMessage } from '@/api/http';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { PageHeader } from '@/ui/page-header';
import { SearchField } from '@/ui/search-field';
import { Screen } from '@/ui/screen';
import { SectionTabs } from '@/ui/section-tabs';
import { SelectField } from '@/ui/select-field';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';
import type { AppConfig, DailyOutstandingReportSettings, NotificationSwitch, PasswordPolicy } from '@/types/api';

type ConfigFormState = ConfigFormPayload & {
  editingKey?: string;
  sensitive?: boolean;
};

type ScopeSelection = 'tenant' | string;
type ConfigTab = 'system' | 'password' | 'notification';

const emptyConfigForm: ConfigFormState = {
  configKey: '',
  configValue: '',
  description: '',
};
const IMAGE_REDACTION_SUBMISSION_CONFIG_KEY = 'media.image-redaction.submission-enabled';

export default function AdminConfigsScreen() {
  const queryClient = useQueryClient();
  const { currentUser, hasPermission } = useAuth();
  const tenantScopeLabel = `${currentUser?.tenantName || '当前市公司'}默认`;
  const [page, setPage] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const debouncedSearchTerm = useDebouncedValue(searchTerm.trim());
  const [form, setForm] = useState<ConfigFormState | null>(null);
  const [message, setMessage] = useState('');
  const [activeTab, setActiveTab] = useState<ConfigTab>('system');
  const [dailySendTime, setDailySendTime] = useState('18:00');
  const [passwordPolicyDraft, setPasswordPolicyDraft] = useState<PasswordPolicy | null>(null);
  const canView = hasPermission(ADMIN_PERMISSIONS.SYSTEM_CONFIG_VIEW);
  const canCreate = hasPermission(ADMIN_PERMISSIONS.SYSTEM_CONFIG_CREATE);
  const canUpdate = hasPermission(ADMIN_PERMISSIONS.SYSTEM_CONFIG_UPDATE);
  const canDelete = hasPermission(ADMIN_PERMISSIONS.SYSTEM_CONFIG_DELETE);

  const scopesQuery = useQuery({
    queryKey: ['admin-config-scopes'],
    queryFn: fetchAdminConfigScopes,
    enabled: canView,
    staleTime: 1000 * 60,
  });
  const [selectedScope, setSelectedScope] = useState<ScopeSelection | null>(null);

  useEffect(() => {
    if (selectedScope !== null || !scopesQuery.data) return;
    if (scopesQuery.data.tenantWide) {
      setSelectedScope('tenant');
      return;
    }
    const currentOrganization = scopesQuery.data.organizations.find((org) => org.id === currentUser?.orgUnitId);
    setSelectedScope(currentOrganization?.id ?? scopesQuery.data.organizations[0]?.id ?? null);
  }, [currentUser?.orgUnitId, scopesQuery.data, selectedScope]);

  const orgUnitId = selectedScope && selectedScope !== 'tenant' ? selectedScope : undefined;

  const configsQuery = useQuery({
    queryKey: ['admin-configs', selectedScope, page, debouncedSearchTerm],
    queryFn: () => fetchAdminConfigs({ page, size: 10, searchTerm: debouncedSearchTerm || undefined, orgUnitId }),
    enabled: canView && selectedScope !== null && activeTab === 'system',
    placeholderData: keepPreviousData,
    staleTime: 1000 * 30,
  });

  const passwordPolicyQuery = useQuery({
    queryKey: ['admin-password-policy', selectedScope],
    queryFn: () => fetchAdminPasswordPolicy(orgUnitId),
    enabled: canView && selectedScope !== null && activeTab === 'password',
    staleTime: 1000 * 30,
  });

  useEffect(() => {
    setPasswordPolicyDraft(passwordPolicyQuery.data ?? null);
  }, [passwordPolicyQuery.data]);

  const passwordPolicyMutation = useMutation({
    mutationFn: (payload: PasswordPolicyUpdatePayload) => updateAdminPasswordPolicy(payload, orgUnitId),
    onSuccess: async (policy) => {
      setPasswordPolicyDraft(policy);
      setMessage('密码策略已保存。');
      await queryClient.invalidateQueries({ queryKey: ['admin-password-policy', selectedScope] });
    },
  });

  const passwordPolicyResetMutation = useMutation({
    mutationFn: () => deleteAdminPasswordPolicy(orgUnitId),
    onSuccess: async () => {
      setMessage('密码策略覆盖已删除，已恢复继承。');
      await queryClient.invalidateQueries({ queryKey: ['admin-password-policy', selectedScope] });
    },
  });

  const notificationSettingsQuery = useQuery({
    queryKey: ['admin-notification-settings', selectedScope],
    queryFn: () => fetchAdminNotificationSettings(orgUnitId),
    enabled: canView && selectedScope !== null && activeTab === 'notification',
    staleTime: 1000 * 30,
  });

  const notificationMutation = useMutation({
    mutationFn: ({ name, enabled }: { name: string; enabled: boolean }) => (
      updateAdminNotificationSwitch(name, enabled, orgUnitId)
    ),
    onSuccess: async (setting) => {
      setMessage(`${setting.displayName}已${setting.enabled ? '开启' : '关闭'}。`);
      await queryClient.invalidateQueries({ queryKey: ['admin-notification-settings', selectedScope] });
    },
  });

  const dailyScheduleMutation = useMutation({
    mutationFn: (sendTime: string) => updateDailyOutstandingReportSchedule(sendTime, orgUnitId),
    onSuccess: async (settings) => {
      setDailySendTime(settings.sendTime);
      setMessage(`日报发送时间已更新为${settings.sendTime}。`);
      await queryClient.invalidateQueries({ queryKey: ['admin-notification-settings', selectedScope] });
    },
  });

  useEffect(() => {
    const sendTime = notificationSettingsQuery.data?.dailyOutstandingReport.sendTime;
    if (sendTime) setDailySendTime(sendTime);
  }, [notificationSettingsQuery.data?.dailyOutstandingReport.sendTime]);

  const saveMutation = useMutation({
    mutationFn: (payload: ConfigFormState) => {
      if (payload.editingKey) {
        const retainSensitiveValue = !!payload.sensitive && payload.configValue === '';
        return updateAdminConfig(payload.editingKey, {
          configValue: retainSensitiveValue ? null : payload.configValue,
          description: payload.description,
          retainSensitiveValue,
        }, orgUnitId);
      }
      return createAdminConfig(payload, orgUnitId);
    },
    onSuccess: async () => {
      setMessage('配置已保存。');
      setForm(null);
      await queryClient.invalidateQueries({ queryKey: ['admin-configs'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (configKey: string) => deleteAdminConfig(configKey, orgUnitId),
    onSuccess: async () => {
      setMessage('配置已删除。');
      await queryClient.invalidateQueries({ queryKey: ['admin-configs'] });
    },
  });

  if (!canView) {
    return (
      <Screen>
        <Card><Text style={{ color: colors.muted }}>当前账号没有系统配置查看权限。</Text></Card>
      </Screen>
    );
  }

  return (
    <Screen>
      <PageHeader title="系统配置" subtitle={activeTab === 'system' ? '运行参数' : activeTab === 'password' ? '账号密码规则' : '通知渠道与日报'} />

      <SectionTabs label="配置分类" value={activeTab} onChange={changeTab} options={[
        { value: 'system', label: '系统参数' },
        { value: 'password', label: '密码策略' },
        { value: 'notification', label: '通知与汇总' },
      ]} />

      <Card style={{ gap: 10 }}>
        {scopesQuery.isLoading && <ActivityIndicator color={colors.primary} />}
        {!!scopesQuery.error && (
          <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(scopesQuery.error, '配置作用域加载失败，请稍后重试。')}</Text>
        )}
        {scopesQuery.data && <SelectField label="配置作用域" value={selectedScope ?? ''} onChange={changeScope}
          options={[
            ...(scopesQuery.data.tenantWide ? [{ value: 'tenant', label: tenantScopeLabel }] : []),
            ...scopesQuery.data.organizations.map(organization => ({ value: organization.id, label: organization.name })),
          ]} />}
        {scopesQuery.data && !scopesQuery.data.tenantWide && scopesQuery.data.organizations.length === 0 && (
          <Text style={{ color: colors.danger }}>当前账号没有可访问的配置作用域。</Text>
        )}
      </Card>

      {activeTab === 'password' && <Card style={{ gap: 10 }}>
        {passwordPolicyQuery.isLoading && <ActivityIndicator color={colors.primary} />}
        {!!passwordPolicyQuery.error && <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(passwordPolicyQuery.error, '密码策略加载失败，请稍后重试。')}</Text>}
        {!!passwordPolicyMutation.error && <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(passwordPolicyMutation.error, '密码策略保存失败，请稍后重试。')}</Text>}
        {!!passwordPolicyResetMutation.error && <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(passwordPolicyResetMutation.error, '恢复密码策略失败，请稍后重试。')}</Text>}
        {!!passwordPolicyQuery.data && !!passwordPolicyDraft && (
          <PasswordPolicyPanel
            policy={passwordPolicyDraft}
            scopeLabel={selectedScope === 'tenant' ? tenantScopeLabel : scopesQuery.data?.organizations.find((organization) => organization.id === selectedScope)?.name || '当前组织'}
            canUpdate={canUpdate}
            canDelete={canDelete}
            saving={passwordPolicyMutation.isPending || passwordPolicyResetMutation.isPending}
            onChange={setPasswordPolicyDraft}
            onSave={() => passwordPolicyMutation.mutate({
              minLength: Number(passwordPolicyDraft.minLength),
              requireUppercase: passwordPolicyDraft.requireUppercase,
              requireLowercase: passwordPolicyDraft.requireLowercase,
              requireDigit: passwordPolicyDraft.requireDigit,
              requireSpecial: passwordPolicyDraft.requireSpecial,
            })}
            onReset={() => confirmResetPolicy(selectedScope === 'tenant' ? tenantScopeLabel : '当前组织', () => passwordPolicyResetMutation.mutate())}
          />
        )}
      </Card>}
      {activeTab === 'system' && <Card style={{ gap: 10 }}>
        <SearchField
          label="搜索配置"
          value={searchTerm}
          onChangeText={(value) => {
            setPage(0);
            setSearchTerm(value);
          }}
          placeholder="配置键、非敏感值、说明"
          isSearching={configsQuery.isFetching}
          resultCount={configsQuery.data?.totalElements}
        />
        {canCreate && <AppButton title="添加配置" onPress={() => setForm(emptyConfigForm)} />}
      </Card>}

      {activeTab === 'system' && form && (
        <ConfigForm
          form={form}
          isSaving={saveMutation.isPending}
          onChange={setForm}
          onCancel={() => setForm(null)}
          onSubmit={() => saveMutation.mutate(form)}
        />
      )}

      {!!message && <Card><Text style={{ color: colors.primary, fontWeight: '800' }}>{message}</Text></Card>}
      {activeTab === 'system' && !!saveMutation.error && (
        <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(saveMutation.error, '保存配置失败，请稍后重试。')}</Text></Card>
      )}
      {activeTab === 'system' && !!deleteMutation.error && (
        <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(deleteMutation.error, '删除配置失败，请稍后重试。')}</Text></Card>
      )}
      {activeTab === 'system' && !!configsQuery.error && (
        <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(configsQuery.error, '配置列表加载失败，请稍后重试。')}</Text></Card>
      )}
      {activeTab === 'system' && configsQuery.isLoading && <ActivityIndicator color={colors.primary} />}

      {activeTab === 'system' && configsQuery.data?.content.map((config) => (
        <ConfigCard
          key={config.configKey}
          config={config}
          canUpdate={canUpdate}
          canDelete={canDelete}
          onEdit={() => setForm({
            editingKey: config.configKey,
            configKey: config.configKey,
            configValue: config.sensitive ? '' : (config.configValue ?? ''),
            description: config.description ?? '',
            sensitive: config.sensitive,
          })}
          onDelete={() => confirmDelete(config, () => deleteMutation.mutate(config.configKey))}
        />
      ))}

      {activeTab === 'system' && configsQuery.data && configsQuery.data.content.length === 0 && (
        <Card><Text style={{ color: colors.muted }}>没有找到配置项。</Text></Card>
      )}

      {activeTab === 'system' && configsQuery.data && configsQuery.data.totalPages > 1 && (
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <AppButton title="上一页" onPress={() => setPage((value) => Math.max(0, value - 1))} disabled={page <= 0} variant="secondary" />
          <Text accessibilityRole="text" style={{ flex: 1, textAlign: 'center', alignSelf: 'center', color: colors.muted }}>第 {page + 1} / {configsQuery.data.totalPages} 页</Text>
          <AppButton title="下一页" onPress={() => setPage((value) => value + 1)} disabled={configsQuery.data.last} variant="secondary" />
        </View>
      )}

      {activeTab === 'notification' && notificationSettingsQuery.isLoading && <ActivityIndicator color={colors.primary} />}
      {activeTab === 'notification' && !!notificationSettingsQuery.error && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(notificationSettingsQuery.error, '通知中心加载失败，请稍后重试。')}
          </Text>
        </Card>
      )}
      {activeTab === 'notification' && !!notificationMutation.error && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(notificationMutation.error, '通知渠道更新失败，请稍后重试。')}
          </Text>
        </Card>
      )}
      {activeTab === 'notification' && !!dailyScheduleMutation.error && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(dailyScheduleMutation.error, '日报发送时间更新失败，请稍后重试。')}
          </Text>
        </Card>
      )}
      {activeTab === 'notification' && notificationSettingsQuery.data && (
        <NotificationSettingsPanel
          center={notificationSettingsQuery.data.center}
          channels={notificationSettingsQuery.data.channels}
          dailyOutstandingReport={notificationSettingsQuery.data.dailyOutstandingReport}
          dailySendTime={dailySendTime}
          canUpdate={canUpdate}
          savingName={notificationMutation.isPending ? notificationMutation.variables?.name : undefined}
          savingSchedule={dailyScheduleMutation.isPending}
          onChange={(name, enabled) => notificationMutation.mutate({ name, enabled })}
          onDailySendTimeChange={setDailySendTime}
          onSaveDailySchedule={() => dailyScheduleMutation.mutate(dailySendTime)}
        />
      )}
    </Screen>
  );

  function changeScope(scope: ScopeSelection) {
    setSelectedScope(scope);
    setPage(0);
    setForm(null);
    setMessage('');
  }

  function changeTab(tab: ConfigTab) {
    setActiveTab(tab);
    setPage(0);
    setForm(null);
    setMessage('');
  }
}

function NotificationSettingsPanel({
  center,
  channels,
  dailyOutstandingReport,
  dailySendTime,
  canUpdate,
  savingName,
  savingSchedule,
  onChange,
  onDailySendTimeChange,
  onSaveDailySchedule,
}: {
  center: NotificationSwitch;
  channels: NotificationSwitch[];
  dailyOutstandingReport: DailyOutstandingReportSettings;
  dailySendTime: string;
  canUpdate: boolean;
  savingName?: string;
  savingSchedule: boolean;
  onChange: (name: string, enabled: boolean) => void;
  onDailySendTimeChange: (sendTime: string) => void;
  onSaveDailySchedule: () => void;
}) {
  const sendTimeValid = /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(dailySendTime);
  return (
    <Card style={{ gap: 0 }}>
      {[center, ...channels, dailyOutstandingReport.switchSetting].map((setting, index) => (
        <View
          key={setting.name}
          style={{
            minHeight: 72,
            flexDirection: 'row',
            alignItems: 'center',
            gap: 12,
            paddingVertical: 12,
            borderTopWidth: index === 0 ? 0 : 1,
            borderTopColor: colors.border,
          }}
        >
          <View style={{ flex: 1, gap: 4 }}>
            <Text style={{ color: colors.text, fontWeight: '900', fontSize: 16 }}>{setting.displayName}</Text>
            <Text style={{ color: colors.muted, lineHeight: 19 }}>{setting.description}</Text>
            <Text style={{ color: setting.inherited ? colors.muted : colors.primary, fontSize: 12, fontWeight: '700' }}>
              {notificationSourceLabel(setting)}
            </Text>
          </View>
          {savingName === setting.name && <ActivityIndicator color={colors.primary} />}
          <Switch
            accessibilityLabel={setting.displayName}
            value={setting.enabled}
            onValueChange={(enabled) => onChange(setting.name, enabled)}
            disabled={!canUpdate || !!savingName}
            trackColor={{ false: colors.border, true: colors.primary }}
          />
        </View>
      ))}
      <View style={{ gap: 10, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 14, paddingBottom: 8 }}>
        <Text style={{ color: colors.text, fontWeight: '900', fontSize: 16 }}>日报发送时间</Text>
        <TextField
          label="发送时间"
          value={dailySendTime}
          onChangeText={onDailySendTimeChange}
          placeholder="18:00"
          maxLength={5}
          keyboardType="numbers-and-punctuation"
          errorMessage={dailySendTime.length > 0 && !sendTimeValid ? '请使用HH:mm格式' : undefined}
        />
        <Text style={{ color: dailyOutstandingReport.inherited ? colors.muted : colors.primary, fontSize: 12, fontWeight: '700' }}>
          {notificationSourceLabel(dailyOutstandingReport)} · Asia/Shanghai
        </Text>
        <AppButton
          title={savingSchedule ? '保存中' : '保存时间'}
          onPress={onSaveDailySchedule}
          loading={savingSchedule}
          disabled={!canUpdate
            || !!savingName
            || savingSchedule
            || !sendTimeValid
            || dailySendTime === dailyOutstandingReport.sendTime}
          variant="secondary"
        />
      </View>
      {!canUpdate && <Text style={{ color: colors.muted, paddingTop: 10 }}>当前账号只有查看权限。</Text>}
    </Card>
  );
}

function notificationSourceLabel(setting: Pick<NotificationSwitch, 'sourceScope' | 'sourceOrgUnitName'>) {
  if (setting.sourceScope === 'ORGANIZATION_OVERRIDE') {
    return `${setting.sourceOrgUnitName || '当前组织'}配置`;
  }
  return setting.sourceScope === 'TENANT_OVERRIDE' ? '全市默认' : '平台默认';
}

function PasswordPolicyPanel({
  policy,
  scopeLabel,
  canUpdate,
  canDelete,
  saving,
  onChange,
  onSave,
  onReset,
}: {
  policy: PasswordPolicy;
  scopeLabel: string;
  canUpdate: boolean;
  canDelete: boolean;
  saving: boolean;
  onChange: (policy: PasswordPolicy) => void;
  onSave: () => void;
  onReset: () => void;
}) {
  const source = policy.sourceScope === 'ORGANIZATION_OVERRIDE'
    ? `${policy.sourceOrgUnitName || '当前组织'}配置`
    : policy.sourceScope === 'TENANT_OVERRIDE' ? '全市默认配置' : '平台默认配置';
  const minLength = Number(policy.minLength);
  const validLength = Number.isInteger(minLength) && minLength >= 8 && minLength <= 100;
  const fields = [
    ['requireUppercase', '必须包含大写字母'],
    ['requireLowercase', '必须包含小写字母'],
    ['requireDigit', '必须包含数字'],
    ['requireSpecial', '必须包含特殊字符'],
  ] as const;
  return (
    <View style={{ gap: 10, borderBottomWidth: 1, borderBottomColor: colors.border, paddingBottom: 14, marginBottom: 4 }}>
      <View style={{ gap: 4 }}>
        <Text style={{ color: colors.text, fontSize: 18, fontWeight: '900' }}>密码策略</Text>
        <Text style={{ color: colors.muted }}>当前生效：{source}{policy.inherited ? '（继承）' : ''}</Text>
      </View>
      <TextField
        label="最小密码长度"
        value={String(policy.minLength)}
        onChangeText={(value) => onChange({ ...policy, minLength: Number(value) })}
        keyboardType="number-pad"
        helperText="允许范围：8—100位；默认8位"
        errorMessage={!validLength ? '请输入8到100之间的整数。' : undefined}
        editable={canUpdate && !saving}
      />
      <View style={{ gap: 2 }}>
        <Text style={{ color: colors.text, fontWeight: '800' }}>字符组成要求</Text>
        {fields.map(([key, label]) => (
          <View key={key} style={{ minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text style={{ color: colors.text }}>{label}</Text>
            <Switch
              value={policy[key] === true}
              onValueChange={(value) => onChange({ ...policy, [key]: value })}
              disabled={!canUpdate || saving}
              trackColor={{ false: colors.border, true: colors.primary }}
            />
          </View>
        ))}
      </View>
      <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
        {canUpdate && <AppButton title={saving ? '保存中' : '保存密码策略'} onPress={onSave} loading={saving} disabled={!validLength || saving} />}
        {canDelete && policy.localOverride && <AppButton title="恢复继承默认值" onPress={onReset} variant="secondary" disabled={saving} />}
        {!canUpdate && <Text style={{ color: colors.muted, alignSelf: 'center' }}>当前账号只有查看权限。</Text>}
      </View>
    </View>
  );
}

function confirmResetPolicy(scopeLabel: string, onConfirm: () => void) {
  Alert.alert('请确认', `确认删除${scopeLabel}的密码策略覆盖并恢复继承吗？`, [
    { text: '取消', style: 'cancel' },
    { text: '确认', style: 'destructive', onPress: onConfirm },
  ]);
}

function ConfigForm({
  form,
  isSaving,
  onChange,
  onCancel,
  onSubmit,
}: {
  form: ConfigFormState;
  isSaving: boolean;
  onChange: (form: ConfigFormState) => void;
  onCancel: () => void;
  onSubmit: () => void;
}) {
  return (
    <Card style={{ gap: 12, borderLeftWidth: 5, borderLeftColor: colors.primary }}>
      <Text style={{ fontSize: 20, fontWeight: '900', color: colors.text }}>{form.editingKey ? '编辑配置' : '添加配置'}</Text>
      <TextField
        label="配置键"
        value={form.configKey}
        editable={!form.editingKey}
        onChangeText={(configKey) => onChange({ ...form, configKey })}
        autoCapitalize="none"
      />
      {form.configKey === IMAGE_REDACTION_SUBMISSION_CONFIG_KEY ? (
        <View style={{ gap: 6 }}>
          <View style={{ minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text style={{ color: colors.text, fontWeight: '800' }}>允许主体服务提交新的图片脱敏任务</Text>
            <Switch
              accessibilityLabel="允许主体服务提交新的图片脱敏任务"
              value={['true', '1', 'yes'].includes(String(form.configValue || '').toLowerCase())}
              onValueChange={(enabled) => onChange({ ...form, configValue: enabled ? 'true' : 'false' })}
              disabled={isSaving}
              trackColor={{ false: colors.border, true: colors.primary }}
            />
          </View>
          <Text style={{ color: colors.muted, lineHeight: 19 }}>
            关闭后，已提交给图像服务的任务继续处理；新图片先记录为待提交，不会暴露原图。
          </Text>
        </View>
      ) : (
        <TextField
          label={form.editingKey && form.sensitive ? '新配置值' : '配置值'}
          value={form.configValue}
          onChangeText={(configValue) => onChange({ ...form, configValue })}
          multiline
          helperText={form.editingKey && form.sensitive
            ? '敏感值不会从服务端回显；留空将保持现有值，输入内容将替换现有值。'
            : undefined}
        />
      )}
      <TextField
        label="描述"
        value={form.description}
        onChangeText={(description) => onChange({ ...form, description })}
        multiline
      />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <AppButton title={isSaving ? '保存中' : '保存'} onPress={onSubmit} disabled={isSaving} loading={isSaving} />
        <AppButton title="取消" onPress={onCancel} variant="secondary" disabled={isSaving} />
      </View>
    </Card>
  );
}

function ConfigCard({
  config,
  canUpdate,
  canDelete,
  onEdit,
  onDelete,
}: {
  config: AppConfig;
  canUpdate: boolean;
  canDelete: boolean;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const source = config.sourceScope === 'ORGANIZATION_OVERRIDE'
    ? `${config.sourceOrgUnitName || '组织'}配置`
    : config.sourceScope === 'TENANT_OVERRIDE'
      ? '全市默认'
      : '平台默认';
  return (
    <Card style={{ gap: 10 }}>
      <Text selectable style={{ color: colors.text, fontSize: 17, fontWeight: '900' }}>{config.configKey}</Text>
      <Text selectable={!config.sensitive} style={{ color: colors.muted, lineHeight: 20 }}>
        {config.sensitive
          ? (config.configured ? '敏感配置 · 已配置（内容不可回显）' : '敏感配置 · 未配置')
          : config.configKey === IMAGE_REDACTION_SUBMISSION_CONFIG_KEY
            ? (['true', '1', 'yes'].includes(String(config.configValue || '').toLowerCase())
              ? '允许提交新任务'
              : '已停止提交新任务')
            : (config.configValue || '空值')}
      </Text>
      {!!config.description && <Text style={{ color: colors.muted }}>说明：{config.description}</Text>}
      <Text style={{ color: config.inherited ? colors.muted : colors.primary, fontWeight: '800' }}>
        来源：{source}{config.inherited ? '（继承）' : ''}
      </Text>
      {!!config.lastUpdatedTime && <Text style={{ color: colors.muted, fontSize: 12 }}>更新：{formatDateTime(config.lastUpdatedTime)}</Text>}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {canUpdate && <AppButton title={config.inherited ? '创建覆盖' : '编辑'} onPress={onEdit} variant="secondary" />}
        {canDelete && !config.inherited && <AppButton title="删除覆盖" onPress={onDelete} variant="ghost" />}
      </View>
    </Card>
  );
}

function confirmDelete(config: AppConfig, onConfirm: () => void) {
  Alert.alert('请确认', `确认删除配置 ${config.configKey}？`, [
    { text: '取消', style: 'cancel' },
    { text: '确认', style: 'destructive', onPress: onConfirm },
  ]);
}

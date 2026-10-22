import DateTimePicker, { type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, Switch, Text, View } from 'react-native';

import {
  applyAdminUserAuthorizationPlan,
  createAdminUserAuthorizationPlan,
  fetchAdminUserAuthorization,
  fetchAdminUserAuthorizationOptions,
} from '@/api/admin-api';
import { formatApiErrorMessage } from '@/api/http';
import type {
  AuthorizationGrantView,
  AuthorizationOperationPayload,
  AuthorizationPlan,
  AuthorizationPermissionOption,
  UserAuthorizationView,
} from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { EditModal } from '@/ui/edit-modal';
import { SearchField } from '@/ui/search-field';
import { SelectField } from '@/ui/select-field';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';
import {
  dateForUtc8Picker,
  formatDateTime,
  pickerDateToApiDateTime,
} from '@/utils/format';
import {
  buildAuthorizationDiffRows,
  filterAuthorizationGrants,
  filterPermissionOptions,
  type AuthorizationDiffFieldKey,
  type AuthorizationDiffFieldValue,
  type AuthorizationDiffRow,
} from './authorization-editor-workflow';

const STEP_LABELS = ['编辑权限清单', '确认变更'];
const OPERATION_LABELS: Record<string, string> = {
  APPLY_TEMPLATE: '追加岗位模板',
  GRANT_PERMISSION: '追加单条权限',
  ADJUST_PERMISSION: '调整权限',
  REVOKE_PERMISSION: '撤销权限',
  REATTACH_PERMISSION: '恢复授权链',
};
const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '有效',
  REVOKED: '已撤销',
  SUSPENDED_UPSTREAM: '上游失效已暂停',
};
const ISSUER_LABELS: Record<string, string> = {
  USER: '人工授权',
  IMPORT: '批量导入',
  MIGRATION: '历史迁移',
  SYSTEM_ROOT: '系统根授权',
  SYSTEM_BASELINE: '组织基线',
  SYSTEM_SELF_BASELINE: '个人基础权限',
};
const DIFF_KIND_META = {
  ADD: { label: '新增', color: colors.success },
  UPDATE: { label: '调整', color: colors.primary },
  REMOVE: { label: '撤销', color: colors.danger },
} as const;
const DIFF_FIELD_LABELS: Record<AuthorizationDiffFieldKey, string> = {
  permission: '原子权限',
  parentGrantId: '授权链上游',
  grantOption: '可转授权',
  status: '状态',
  validFrom: '开始时间',
  expiresAt: '有效期',
};

type StagedOperation = AuthorizationOperationPayload & {
  label: string;
  permissionNames?: string[];
};

type PermissionDraft = {
  permissionName: string;
  validFrom: string | null;
  expiresAt: string | null;
  grantOption: boolean;
  reason: string;
};

type TemplateDraft = {
  templateId: string;
  validFrom: string | null;
  expiresAt: string | null;
  grantOption: boolean;
  reason: string;
};

type ReasonOperation = {
  type: 'REVOKE_PERMISSION' | 'REATTACH_PERMISSION';
  grantId: string;
  parentGrantId?: string | null;
  label: string;
  parentCandidates?: { grantId: string; permissionDisplayName: string; expiresAt?: string | null }[];
};

const emptyPermissionDraft = (): PermissionDraft => ({
  permissionName: '', validFrom: null, expiresAt: null, grantOption: false, reason: '',
});
const emptyTemplateDraft = (): TemplateDraft => ({
  templateId: '', validFrom: null, expiresAt: null, grantOption: false, reason: '',
});

export function AuthorizationEditor({
  visible,
  userId,
  onClose,
  onApplied,
}: {
  visible: boolean;
  userId: string;
  userOrgId?: string;
  orgUnits?: unknown[];
  onClose: () => void;
  onApplied: (authorization: UserAuthorizationView) => void;
}) {
  const [step, setStep] = useState(0);
  const [operations, setOperations] = useState<StagedOperation[]>([]);
  const [templateDraft, setTemplateDraft] = useState<TemplateDraft>(emptyTemplateDraft);
  const [templateEditorOpen, setTemplateEditorOpen] = useState(false);
  const [permissionDraft, setPermissionDraft] = useState<PermissionDraft>(emptyPermissionDraft);
  const [permissionEditorOpen, setPermissionEditorOpen] = useState(false);
  const [editingGrant, setEditingGrant] = useState<AuthorizationGrantView | null>(null);
  const [permissionSearch, setPermissionSearch] = useState('');
  const [grantSearch, setGrantSearch] = useState('');
  const [reasonOperation, setReasonOperation] = useState<ReasonOperation | null>(null);
  const [reason, setReason] = useState('');
  const [plan, setPlan] = useState<AuthorizationPlan | null>(null);
  const [error, setError] = useState('');

  const authorizationQuery = useQuery({
    queryKey: ['admin-user-authorization', userId],
    queryFn: () => fetchAdminUserAuthorization(userId),
    enabled: visible,
  });
  const optionsQuery = useQuery({
    queryKey: ['admin-user-authorization-options', userId],
    queryFn: () => fetchAdminUserAuthorizationOptions(userId),
    enabled: visible,
  });

  useEffect(() => {
    if (!visible) return;
    setStep(0);
    setOperations([]);
    setTemplateDraft(emptyTemplateDraft());
    setTemplateEditorOpen(false);
    setPermissionDraft(emptyPermissionDraft());
    setPermissionEditorOpen(false);
    setEditingGrant(null);
    setPermissionSearch('');
    setGrantSearch('');
    setReasonOperation(null);
    setReason('');
    setPlan(null);
    setError('');
  }, [visible]);

  const planMutation = useMutation({
    mutationFn: async () => {
      const authorization = authorizationQuery.data;
      if (!authorization) throw new Error('用户授权信息尚未加载。');
      return createAdminUserAuthorizationPlan(
        userId,
        authorization.authorizationVersion,
        operations.map(({ label: _label, permissionNames: _permissionNames, ...operation }) => operation),
      );
    },
    onSuccess: nextPlan => {
      setPlan(nextPlan);
      setStep(1);
    },
  });
  const applyMutation = useMutation({
    mutationFn: async () => {
      if (!plan) throw new Error('授权变更计划不存在。');
      return applyAdminUserAuthorizationPlan(userId, plan.planToken);
    },
    onSuccess: onApplied,
  });

  const selectedTemplate = useMemo(
    () => optionsQuery.data?.templates.find(template => template.id === templateDraft.templateId),
    [optionsQuery.data?.templates, templateDraft.templateId],
  );
  const selectedPermission = useMemo(
    () => optionsQuery.data?.permissions.find(permission => permission.name === permissionDraft.permissionName),
    [optionsQuery.data?.permissions, permissionDraft.permissionName],
  );
  const filteredPermissions = useMemo(
    () => filterPermissionOptions(optionsQuery.data?.permissions, permissionSearch),
    [optionsQuery.data?.permissions, permissionSearch],
  );
  const visibleGrants = useMemo(
    () => filterAuthorizationGrants(authorizationQuery.data?.grants, grantSearch),
    [authorizationQuery.data?.grants, grantSearch],
  );
  const permissionByName = useMemo(
    () => new Map((optionsQuery.data?.permissions || []).map(permission => [permission.name, permission])),
    [optionsQuery.data?.permissions],
  );

  const stageTemplate = () => {
    if (!selectedTemplate) return setError('请选择岗位模板。');
    setOperations(current => [...current, {
      type: 'APPLY_TEMPLATE',
      templateId: selectedTemplate.id,
      validFrom: templateDraft.validFrom,
      expiresAt: templateDraft.expiresAt,
      grantOption: templateDraft.grantOption,
      reason: templateDraft.reason.trim() || `应用岗位模板：${selectedTemplate.displayName}`,
      label: selectedTemplate.displayName,
      permissionNames: selectedTemplate.permissionNames,
    }]);
    setTemplateDraft(emptyTemplateDraft());
    setTemplateEditorOpen(false);
    setError('');
  };

  const stagePermission = () => {
    if (!selectedPermission) return setError('请选择原子权限。');
    if (!permissionDraft.reason.trim()) return setError('单独授予或调整权限必须填写原因。');
    setOperations(current => [...current, {
      type: editingGrant ? 'ADJUST_PERMISSION' : 'GRANT_PERMISSION',
      grantId: editingGrant?.id,
      permissionName: selectedPermission.name,
      validFrom: permissionDraft.validFrom,
      expiresAt: permissionDraft.expiresAt,
      grantOption: permissionDraft.grantOption,
      reason: permissionDraft.reason.trim(),
      label: selectedPermission.displayName,
    }]);
    setPermissionDraft(emptyPermissionDraft());
    setEditingGrant(null);
    setPermissionEditorOpen(false);
    setPermissionSearch('');
    setError('');
  };

  const startAddPermission = () => {
    setEditingGrant(null);
    setPermissionDraft(emptyPermissionDraft());
    setPermissionSearch('');
    setPermissionEditorOpen(true);
    setError('');
  };

  const startAdjust = (grant: AuthorizationGrantView) => {
    setEditingGrant(grant);
    setPermissionDraft({
      permissionName: grant.permissionName,
      validFrom: grant.validFrom || null,
      expiresAt: grant.expiresAt || null,
      grantOption: grant.grantOption,
      reason: '',
    });
    setPermissionSearch('');
    setPermissionEditorOpen(true);
    setError('');
  };

  const startRevoke = (grant: AuthorizationGrantView) => {
    setReasonOperation({ type: 'REVOKE_PERMISSION', grantId: grant.id, label: grant.permissionDisplayName });
    setReason('');
  };

  const startReattach = (grant: AuthorizationGrantView) => {
    const candidates = (optionsQuery.data?.parentGrants || [])
      .filter(parent => parent.permissionName === grant.permissionName);
    setReasonOperation({
      type: 'REATTACH_PERMISSION',
      grantId: grant.id,
      parentGrantId: candidates.length === 1 ? candidates[0].grantId : null,
      parentCandidates: candidates,
      label: grant.permissionDisplayName,
    });
    setReason('');
  };

  const stageReasonOperation = () => {
    if (!reasonOperation || !reason.trim()) return;
    if (reasonOperation.type === 'REATTACH_PERMISSION' && !reasonOperation.parentGrantId) return;
    setOperations(current => [...current, {
      type: reasonOperation.type,
      grantId: reasonOperation.grantId,
      parentGrantId: reasonOperation.parentGrantId,
      reason: reason.trim(),
      label: reasonOperation.label,
    }]);
    setReasonOperation(null);
    setReason('');
  };

  const loading = authorizationQuery.isLoading || optionsQuery.isLoading;
  const working = planMutation.isPending || applyMutation.isPending;
  const hasDelegablePermissions = (optionsQuery.data?.permissions.length || 0) > 0;
  const hasDelegableTemplates = (optionsQuery.data?.templates.length || 0) > 0;
  const visibleError = error
    || (authorizationQuery.error || optionsQuery.error
      ? formatApiErrorMessage(authorizationQuery.error || optionsQuery.error, '授权信息加载失败，请稍后重试。')
      : '')
    || (planMutation.error || applyMutation.error
      ? formatApiErrorMessage(planMutation.error || applyMutation.error, '授权变更失败，请刷新后重试。')
      : '');

  const primaryAction = () => {
    if (step === 0) {
      if (operations.length === 0) return setError('请至少添加一项授权变更。');
      planMutation.mutate();
    } else {
      applyMutation.mutate();
    }
  };

  return (
    <>
      <EditModal
        visible={visible}
        title={`调整用户授权 · ${STEP_LABELS[step]}`}
        primaryActionLabel={step === 1 ? '应用变更' : '预览变更'}
        onPrimaryAction={primaryAction}
        onClose={onClose}
        working={working}
        primaryDisabled={loading || (step === 0 && operations.length === 0) || (step === 1 && !plan)}
      >
        <StepIndicator step={step} />
        {!!visibleError && <Text selectable style={{ color: colors.danger }}>{visibleError}</Text>}
        {loading ? <ActivityIndicator color={colors.primary} /> : null}
        {!loading && step === 0 ? (
          <View style={{ gap: 12 }}>
            {!hasDelegablePermissions && !hasDelegableTemplates ? (
              <Text style={{ color: colors.warning, lineHeight: 20 }}>
                当前账号可以查看授权，但没有 grantOption=true 的可转授权原子权限，不能继续追加权限或岗位模板。
                请由上级先补充对应转授权能力。
              </Text>
            ) : null}
            <SearchField
              label="搜索当前权限"
              placeholder="输入权限名称、编码或状态"
              value={grantSearch}
              onChangeText={setGrantSearch}
              resultCount={visibleGrants.length}
            />
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <View style={{ flex: 1 }}><AppButton title="追加岗位模板" variant="secondary" disabled={!hasDelegableTemplates} onPress={() => setTemplateEditorOpen(true)} /></View>
              <View style={{ flex: 1 }}><AppButton title="追加单条权限" variant="secondary" disabled={!hasDelegablePermissions} onPress={startAddPermission} /></View>
            </View>
            <OperationList operations={operations} permissionByName={permissionByName} onRemove={index => (
              setOperations(current => current.filter((_, currentIndex) => currentIndex !== index))
            )} />
            <AuthorizationGrantList
              grants={visibleGrants}
              onAdjust={startAdjust}
              onRevoke={startRevoke}
              onReattach={startReattach}
            />
          </View>
        ) : null}
        {!loading && step === 1 && plan ? (
          <>
            <AuthorizationDiffPreview plan={plan} />
            <AppButton title="返回修改" variant="ghost" onPress={() => { setPlan(null); setStep(0); }} />
          </>
        ) : null}
      </EditModal>

      <EditModal
        visible={templateEditorOpen}
        title="追加岗位模板"
        primaryActionLabel="加入待变更清单"
        onPrimaryAction={stageTemplate}
        onClose={() => setTemplateEditorOpen(false)}
        primaryDisabled={!selectedTemplate}
      >
        <Text style={{ color: colors.primary, lineHeight: 20 }}>
          岗位模板只用于一次性批量追加原子权限，保存后不再与用户授权绑定。
        </Text>
        {!hasDelegableTemplates ? (
          <Text style={{ color: colors.warning }}>当前账号没有能够完整委派的岗位模板。</Text>
        ) : null}
        <SelectField
          label="岗位模板"
          value={templateDraft.templateId}
          options={[
            { label: '请选择', value: '' },
            ...(optionsQuery.data?.templates || []).map(template => ({
              label: `${template.displayName} · ${template.permissionNames.length} 项权限`,
              value: template.id,
            })),
          ]}
          onChange={templateId => setTemplateDraft(current => ({ ...current, templateId }))}
        />
        {selectedTemplate ? (
          <View style={{ gap: 7, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 10 }}>
            <Text style={{ color: colors.text, fontWeight: '900' }}>将追加的权限（{selectedTemplate.permissionNames.length}）</Text>
            {selectedTemplate.permissionNames.map(permissionName => (
              <Text key={permissionName} selectable style={{ color: colors.muted, fontSize: 12 }}>
                {permissionByName.get(permissionName)?.displayName || permissionName} · {permissionName}
              </Text>
            ))}
          </View>
        ) : null}
        <ValidityEditor
          validFrom={templateDraft.validFrom}
          expiresAt={templateDraft.expiresAt}
          grantOption={templateDraft.grantOption}
          onChange={patch => setTemplateDraft(current => ({ ...current, ...patch }))}
        />
        <TextField
          label="变更原因（选填）"
          value={templateDraft.reason}
          onChangeText={reasonValue => setTemplateDraft(current => ({ ...current, reason: reasonValue }))}
          multiline
        />
      </EditModal>

      <EditModal
        visible={permissionEditorOpen}
        title={editingGrant ? `调整权限 · ${editingGrant.permissionDisplayName}` : '追加单条权限'}
        primaryActionLabel={editingGrant ? '加入权限调整' : '加入待变更清单'}
        onPrimaryAction={stagePermission}
        onClose={() => {
          setPermissionEditorOpen(false);
          setEditingGrant(null);
          setPermissionDraft(emptyPermissionDraft());
        }}
        primaryDisabled={!selectedPermission || !permissionDraft.reason.trim()}
      >
        {editingGrant ? (
          <Text style={{ color: colors.primary, lineHeight: 20 }}>
            可以直接更换为另一项原子权限；预览会明确展示实际变化。
          </Text>
        ) : null}
        {!hasDelegablePermissions ? (
          <Text style={{ color: colors.warning }}>当前账号没有 grantOption=true 的可转授权原子权限。</Text>
        ) : null}
        <PermissionPicker
          permissions={filteredPermissions}
          selectedPermission={selectedPermission}
          search={permissionSearch}
          onSearchChange={setPermissionSearch}
          onSelect={permission => setPermissionDraft(current => ({ ...current, permissionName: permission.name }))}
        />
        <ValidityEditor
          validFrom={permissionDraft.validFrom}
          expiresAt={permissionDraft.expiresAt}
          grantOption={permissionDraft.grantOption}
          onChange={patch => setPermissionDraft(current => ({ ...current, ...patch }))}
        />
        <TextField
          label="变更原因"
          value={permissionDraft.reason}
          onChangeText={reasonValue => setPermissionDraft(current => ({ ...current, reason: reasonValue }))}
          multiline
        />
      </EditModal>

      <EditModal
        visible={Boolean(reasonOperation)}
        title={reasonOperation?.type === 'REATTACH_PERMISSION' ? '恢复授权链' : '确认撤销权限'}
        primaryActionLabel="加入待变更清单"
        onPrimaryAction={stageReasonOperation}
        onClose={() => { setReasonOperation(null); setReason(''); }}
        primaryDisabled={!reason.trim() || (reasonOperation?.type === 'REATTACH_PERMISSION' && !reasonOperation.parentGrantId)}
      >
        <Text style={{ color: reasonOperation?.type === 'REVOKE_PERMISSION' ? colors.danger : colors.primary, fontWeight: '800' }}>
          {reasonOperation?.label}
        </Text>
        {reasonOperation?.type === 'REATTACH_PERMISSION' ? (
          <SelectField
            label="新的上游授权"
            value={reasonOperation.parentGrantId || ''}
            options={[
              { label: '请选择', value: '' },
              ...(reasonOperation.parentCandidates || []).map(parent => ({
                label: `${parent.permissionDisplayName} · ${parent.expiresAt ? formatDateTime(parent.expiresAt) : '长期有效'}`,
                value: parent.grantId,
              })),
            ]}
            onChange={parentGrantId => setReasonOperation(current => current ? ({ ...current, parentGrantId }) : current)}
          />
        ) : null}
        <TextField label="变更原因" value={reason} onChangeText={setReason} multiline />
      </EditModal>
    </>
  );
}

function PermissionPicker({
  permissions, selectedPermission, search, onSearchChange, onSelect,
}: {
  permissions: AuthorizationPermissionOption[];
  selectedPermission?: AuthorizationPermissionOption;
  search: string;
  onSearchChange: (value: string) => void;
  onSelect: (permission: AuthorizationPermissionOption) => void;
}) {
  return (
    <View style={{ gap: 8 }}>
      <SearchField
        label="搜索原子权限"
        placeholder="输入权限名称或编码"
        value={search}
        onChangeText={onSearchChange}
        resultCount={permissions.length}
      />
      {selectedPermission ? (
        <View style={{ gap: 3, borderWidth: 1, borderColor: colors.primary, borderRadius: 8, padding: 10 }}>
          <Text style={{ color: colors.text, fontWeight: '900' }}>{selectedPermission.displayName}</Text>
          <Text selectable style={{ color: colors.muted, fontSize: 11 }}>{selectedPermission.name}</Text>
        </View>
      ) : null}
      <View style={{ gap: 5, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 5 }}>
        {permissions.map(permission => (
          <Pressable
            key={permission.name}
            accessibilityRole="button"
            accessibilityLabel={`选择权限${permission.displayName}`}
            onPress={() => onSelect(permission)}
            style={({ pressed }) => ({
              gap: 3,
              borderRadius: 6,
              padding: 10,
              backgroundColor: selectedPermission?.name === permission.name ? colors.controlSelectedSurface : colors.controlSurface,
              opacity: pressed ? 0.72 : 1,
            })}
          >
            <Text style={{ color: colors.text, fontWeight: '800' }}>{permission.displayName}</Text>
            <Text selectable style={{ color: colors.muted, fontSize: 11 }}>{permission.name}</Text>
          </Pressable>
        ))}
        {permissions.length === 0 ? (
          <Text style={{ color: colors.muted, padding: 8 }}>
            {search.trim() ? '没有匹配的原子权限。' : '当前没有可转授权的原子权限。'}
          </Text>
        ) : null}
      </View>
    </View>
  );
}

function ValidityEditor({
  validFrom, expiresAt, grantOption, onChange,
}: {
  validFrom: string | null;
  expiresAt: string | null;
  grantOption: boolean;
  onChange: (patch: Partial<Pick<PermissionDraft, 'validFrom' | 'expiresAt' | 'grantOption'>>) => void;
}) {
  return (
    <View style={{ gap: 10 }}>
      <DateTimeField
        label="开始时间"
        emptyLabel="立即生效"
        value={validFrom}
        onChange={value => onChange({ validFrom: value })}
      />
      <DateTimeField
        label="结束时间"
        emptyLabel="长期有效"
        value={expiresAt}
        onChange={value => onChange({ expiresAt: value })}
      />
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ color: colors.text, fontWeight: '800', flex: 1 }}>允许该用户继续转授此权限</Text>
        <Switch value={grantOption} onValueChange={value => onChange({ grantOption: value })} />
      </View>
    </View>
  );
}

function DateTimeField({
  label, emptyLabel, value, onChange,
}: {
  label: string;
  emptyLabel: string;
  value: string | null;
  onChange: (value: string | null) => void;
}) {
  const [pickerMode, setPickerMode] = useState<'date' | 'time' | null>(null);
  const selectedDate = dateForUtc8Picker(value);
  const onPickerChange = (event: DateTimePickerEvent, nextDate?: Date) => {
    if (event.type === 'dismissed' || !nextDate) return setPickerMode(null);
    const apiDateTime = pickerDateToApiDateTime(nextDate);
    if (apiDateTime) onChange(apiDateTime);
    if (Platform.OS === 'android' && pickerMode === 'date') setPickerMode('time');
    else setPickerMode(null);
  };
  return (
    <View style={{ gap: 6 }}>
      <Text style={{ color: colors.text, fontWeight: '800' }}>{label}</Text>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Pressable
          onPress={() => setPickerMode('date')}
          accessibilityRole="button"
          accessibilityLabel={`选择${label}`}
          style={{ flex: 1, minHeight: 48, borderWidth: 1, borderColor: colors.controlBorder, borderRadius: 8, padding: 12, backgroundColor: colors.controlSurface }}
        >
          <Text style={{ color: colors.text, fontWeight: '700' }}>{value ? formatDateTime(value) : emptyLabel}</Text>
        </Pressable>
        {value ? <AppButton title="清除" variant="ghost" onPress={() => onChange(null)} /> : null}
      </View>
      {!!pickerMode && <DateTimePicker value={selectedDate} mode={pickerMode} onChange={onPickerChange} />}
    </View>
  );
}

function AuthorizationGrantList({
  grants, onAdjust, onRevoke, onReattach,
}: {
  grants: AuthorizationGrantView[];
  onAdjust: (grant: AuthorizationGrantView) => void;
  onRevoke: (grant: AuthorizationGrantView) => void;
  onReattach: (grant: AuthorizationGrantView) => void;
}) {
  if (grants.length === 0) {
    return <Text style={{ color: colors.muted, paddingVertical: 16, textAlign: 'center' }}>没有匹配的当前权限。</Text>;
  }
  return (
    <View style={{ borderWidth: 1, borderColor: colors.border, borderRadius: 8, overflow: 'hidden' }}>
      <View style={{ backgroundColor: colors.surfaceAlt, padding: 11 }}>
        <Text style={{ color: colors.text, fontWeight: '900' }}>当前 Grant 清单（{grants.length}）</Text>
      </View>
      {grants.map(grant => (
        <View key={grant.id} style={{ gap: 5, borderTopWidth: 1, borderTopColor: colors.border, padding: 11 }}>
          <Text selectable style={{ color: colors.text, fontWeight: '800' }}>{grant.permissionDisplayName}</Text>
          <Text selectable style={{ color: colors.muted, fontSize: 11 }}>{grant.permissionName}</Text>
          <Text style={{ color: colors.muted, fontSize: 12 }}>
            {STATUS_LABELS[grant.status] || grant.status} · {grant.expiresAt ? `至 ${formatDateTime(grant.expiresAt)}` : '长期有效'}
            {grant.grantOption ? ' · 可转授' : ''}
          </Text>
          <Text selectable style={{ color: colors.muted, fontSize: 11 }}>
            来源：{ISSUER_LABELS[grant.issuerType] || grant.issuerType}
          </Text>
          {grant.manageable && grant.status === 'ACTIVE' ? (
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <View style={{ flex: 1 }}><AppButton title="调整" variant="secondary" onPress={() => onAdjust(grant)} /></View>
              <View style={{ flex: 1 }}><AppButton title="撤销" variant="danger" onPress={() => onRevoke(grant)} /></View>
            </View>
          ) : null}
          {grant.manageable && grant.status === 'SUSPENDED_UPSTREAM' ? (
            <AppButton title="恢复授权链" variant="secondary" onPress={() => onReattach(grant)} />
          ) : null}
          {!grant.manageable ? <Text style={{ color: colors.muted, fontSize: 11 }}>{grant.manageabilityReason || '只读'}</Text> : null}
        </View>
      ))}
    </View>
  );
}

function AuthorizationDiffPreview({ plan }: { plan: AuthorizationPlan }) {
  const rows = buildAuthorizationDiffRows(plan);
  const counts = rows.reduce((result, row) => {
    result[row.kind] += 1;
    return result;
  }, { ADD: 0, UPDATE: 0, REMOVE: 0 });
  return (
    <View style={{ gap: 10 }}>
      <Text style={{ color: colors.text, fontWeight: '900' }}>
        共 {rows.length} 项变更：新增 {counts.ADD}、调整 {counts.UPDATE}、撤销 {counts.REMOVE}
      </Text>
      <Text style={{ color: colors.muted, fontSize: 12 }}>应用后 {plan.resultingActiveGrantCount} 条有效权限</Text>
      {rows.map(row => {
        const kind = DIFF_KIND_META[row.kind];
        return (
          <View key={row.key} style={{ borderWidth: 1, borderLeftWidth: 4, borderColor: colors.border, borderLeftColor: kind.color, borderRadius: 8, overflow: 'hidden' }}>
            <View style={{ padding: 10, backgroundColor: colors.surfaceAlt }}>
              <Text style={{ color: kind.color, fontWeight: '900' }}>{kind.label} · {row.permissionDisplayName}</Text>
              <Text selectable style={{ color: colors.muted, fontSize: 11 }}>{row.permissionName}</Text>
            </View>
            {row.fields.map(field => (
              <View key={field.field} style={{ gap: 3, padding: 10, borderTopWidth: 1, borderTopColor: colors.border }}>
                <Text style={{ color: colors.muted, fontSize: 12 }}>{DIFF_FIELD_LABELS[field.field]}</Text>
                {row.kind !== 'ADD' ? <Text style={{ color: colors.danger }}>- {diffValueLabel(row, field.field, field.before)}</Text> : null}
                {row.kind !== 'REMOVE' ? <Text style={{ color: colors.success }}>+ {diffValueLabel(row, field.field, field.after)}</Text> : null}
              </View>
            ))}
          </View>
        );
      })}
    </View>
  );
}

function diffValueLabel(
  _row: AuthorizationDiffRow,
  field: AuthorizationDiffFieldKey,
  value: AuthorizationDiffFieldValue,
) {
  if (field === 'grantOption') return value ? '允许继续转授' : '不允许继续转授';
  if (field === 'status') return STATUS_LABELS[String(value)] || String(value || '未设置');
  if (field === 'parentGrantId') return value ? String(value) : '无（系统根授权）';
  if (field === 'validFrom') return value ? formatDateTime(String(value)) : '立即生效';
  if (field === 'expiresAt') return value ? formatDateTime(String(value)) : '长期有效';
  return value === null ? '未设置' : String(value);
}

function StepIndicator({ step }: { step: number }) {
  return (
    <View style={{ flexDirection: 'row', gap: 6 }}>
      {STEP_LABELS.map((label, index) => (
        <View key={label} style={{ flex: 1, gap: 4 }}>
          <View style={{ height: 4, borderRadius: 2, backgroundColor: index <= step ? colors.primary : colors.border }} />
          <Text style={{ color: index === step ? colors.text : colors.muted, fontSize: 12, fontWeight: index === step ? '800' : '500' }}>{label}</Text>
        </View>
      ))}
    </View>
  );
}

function OperationList({
  operations, permissionByName, onRemove,
}: {
  operations: StagedOperation[];
  permissionByName: Map<string, AuthorizationPermissionOption>;
  onRemove: (index: number) => void;
}) {
  if (operations.length === 0) return null;
  return (
    <View style={{ gap: 8 }}>
      <Text style={{ color: colors.text, fontWeight: '900' }}>待预检变更（{operations.length}）</Text>
      {operations.map((operation, index) => (
        <View key={`${operation.type}-${operation.grantId || operation.templateId || operation.permissionName}-${index}`} style={{ gap: 6, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 10 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Text style={{ color: colors.primary, fontSize: 12, fontWeight: '800' }}>{OPERATION_LABELS[operation.type]}</Text>
            <Text style={{ flex: 1, color: colors.text, fontWeight: '800' }}>{operation.label}</Text>
            <AppButton title="移除" variant="ghost" onPress={() => onRemove(index)} />
          </View>
          {(operation.permissionNames || []).length > 0 ? (
            <Text style={{ color: colors.muted, fontSize: 12 }}>
              {operation.permissionNames!.map(name => permissionByName.get(name)?.displayName || name).join('、')}
            </Text>
          ) : null}
        </View>
      ))}
    </View>
  );
}

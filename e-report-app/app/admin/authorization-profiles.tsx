import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';

import {
  copyAdminAuthorizationProfile,
  createAdminAuthorizationProfile,
  fetchAdminAuthorizationProfiles,
  fetchAdminPermissions,
  publishAdminAuthorizationProfile,
  retireAdminAuthorizationProfile,
  updateAdminAuthorizationProfileDraft,
  type AuthorizationProfileFormPayload,
} from '@/api/admin-api';
import { formatApiErrorMessage } from '@/api/http';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import type { AuthorizationProfile, Permission } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { EditModal } from '@/ui/edit-modal';
import { PageHeader } from '@/ui/page-header';
import { Screen } from '@/ui/screen';
import { SelectField } from '@/ui/select-field';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';

type EditorMode = 'create' | 'edit' | 'copy';
type EditorState = { mode: EditorMode; profileId?: string; form: AuthorizationProfileFormPayload } | null;

function emptyForm(): AuthorizationProfileFormPayload {
  return {
    code: '',
    displayName: '',
    description: '',
    rules: [],
    changeSummary: '',
  };
}

function profileForm(profile: AuthorizationProfile): AuthorizationProfileFormPayload {
  const version = profile.draftVersion || profile.currentVersion;
  return {
    code: profile.code,
    displayName: profile.displayName,
    description: profile.description || '',
    rules: (version?.rules || []).map(rule => ({ permissionName: rule.permissionName })),
    changeSummary: version?.changeSummary || '',
  };
}

export default function AuthorizationProfilesScreen() {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const canView = hasPermission(ADMIN_PERMISSIONS.AUTHORIZATION_PROFILE_VIEW);
  const canCreate = hasPermission(ADMIN_PERMISSIONS.AUTHORIZATION_PROFILE_CREATE);
  const canEdit = hasPermission(ADMIN_PERMISSIONS.AUTHORIZATION_PROFILE_DRAFT_UPDATE);
  const canCopy = hasPermission(ADMIN_PERMISSIONS.AUTHORIZATION_PROFILE_COPY);
  const canPublish = hasPermission(ADMIN_PERMISSIONS.AUTHORIZATION_PROFILE_PUBLISH);
  const canRetire = hasPermission(ADMIN_PERMISSIONS.AUTHORIZATION_PROFILE_RETIRE);
  const [selectedId, setSelectedId] = useState('');
  const [editor, setEditor] = useState<EditorState>(null);
  const [message, setMessage] = useState('');

  const profilesQuery = useQuery({
    queryKey: ['authorization-profiles'],
    queryFn: fetchAdminAuthorizationProfiles,
    enabled: canView,
  });
  const permissionsQuery = useQuery({
    queryKey: ['admin-permissions-for-profiles'],
    queryFn: () => fetchAdminPermissions({ page: 0, size: 500 }),
    enabled: canView,
  });
  const selected = profilesQuery.data?.find(profile => profile.id === selectedId)
    || profilesQuery.data?.[0]
    || null;

  const refresh = async () => queryClient.invalidateQueries({ queryKey: ['authorization-profiles'] });
  const saveMutation = useMutation({
    mutationFn: async (state: NonNullable<EditorState>) => {
      const payload = {
        ...state.form,
        code: state.form.code.trim().toUpperCase(),
        displayName: state.form.displayName.trim(),
        description: state.form.description?.trim() || null,
        changeSummary: state.form.changeSummary?.trim() || null,
      };
      if (state.mode === 'create') return createAdminAuthorizationProfile(payload);
      if (state.mode === 'copy') return copyAdminAuthorizationProfile(
        state.profileId!, payload.code, payload.displayName);
      return updateAdminAuthorizationProfileDraft(state.profileId!, payload);
    },
    onSuccess: async profile => {
      setSelectedId(profile.id);
      setEditor(null);
      setMessage('岗位模板草稿已保存。');
      await refresh();
    },
  });
  const actionMutation = useMutation({
    mutationFn: async ({ action, profileId }: { action: 'publish' | 'retire'; profileId: string }) =>
      action === 'publish'
        ? publishAdminAuthorizationProfile(profileId)
        : retireAdminAuthorizationProfile(profileId),
    onSuccess: async (_, variables) => {
      setMessage(variables.action === 'publish'
        ? '岗位模板新版本已发布，既有用户授权未自动变化。'
        : '岗位模板已退役，既有用户授权保持有效。');
      await refresh();
    },
  });

  if (!canView) return <Screen><Card><Text style={{ color: colors.muted }}>当前账号没有查看岗位权限模板的权限。</Text></Card></Screen>;
  if (profilesQuery.isLoading || permissionsQuery.isLoading) return <Screen><ActivityIndicator color={colors.primary} /></Screen>;
  if (profilesQuery.error || permissionsQuery.error) {
    return <Screen><Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(profilesQuery.error || permissionsQuery.error, '岗位权限模板加载失败。')}</Text></Card></Screen>;
  }

  return (
    <>
      <Screen>
        <PageHeader title="岗位权限模板" subtitle="模板只用于批量添加原子权限，应用后不与用户授权绑定" />
        {canCreate && <AppButton title="新建模板草稿" onPress={() => setEditor({ mode: 'create', form: emptyForm() })} />}
        {!!message && <Card><Text style={{ color: colors.primary, fontWeight: '800' }}>{message}</Text></Card>}
        {(profilesQuery.data || []).length === 0 ? (
          <Card>
            <Text style={{ color: colors.warning, lineHeight: 20 }}>
              当前账号没有可查看或可应用的岗位模板。模板只展示全部权限都在当前账号可转授权范围内的项目；
              请由上级补充对应 grantOption 后重新登录。
            </Text>
          </Card>
        ) : null}
        {!!profilesQuery.data?.length && (
          <Card>
            <SelectField label="岗位模板" value={selected?.id || ''} onChange={setSelectedId}
              options={profilesQuery.data.map(profile => ({ value: profile.id,
                label: `${profile.displayName} · ${profile.builtIn ? '内置' : '自定义'} · ${profile.active ? '启用' : '已退役'}` }))} />
          </Card>
        )}
        {!!selected && (
          <Card style={{ gap: 10 }}>
            <Text style={{ color: colors.text, fontSize: 18, fontWeight: '900' }}>{selected.displayName}</Text>
            <Text selectable style={{ color: colors.muted, fontSize: 12 }}>{selected.code}</Text>
            <Text style={{ color: colors.muted }}>{selected.description || '无职责说明'}</Text>
            <Text style={{ color: colors.muted }}>
              {selected.draftVersion ? `草稿 v${selected.draftVersion.versionNo}` : `已发布 v${selected.currentVersion?.versionNo || '-'}`}
            </Text>
            {canCopy && <AppButton title="复制为自定义模板" variant="secondary" onPress={() => setEditor({ mode: 'copy', profileId: selected.id, form: { ...emptyForm(), code: `${selected.code}_COPY`, displayName: `${selected.displayName}副本` } })} />}
            {!selected.builtIn && canEdit && <AppButton title="编辑草稿" variant="secondary" onPress={() => setEditor({ mode: 'edit', profileId: selected.id, form: profileForm(selected) })} />}
            {!selected.builtIn && !!selected.draftVersion && canPublish && <AppButton title="发布新版本" onPress={() => actionMutation.mutate({ action: 'publish', profileId: selected.id })} />}
            {!selected.builtIn && selected.active && canRetire && <AppButton title="退役模板" variant="ghost" onPress={() => actionMutation.mutate({ action: 'retire', profileId: selected.id })} />}
            {(selected.draftVersion || selected.currentVersion)?.rules.map(rule => (
              <View key={rule.id} style={{ gap: 2, borderBottomWidth: 1, borderBottomColor: colors.border, paddingBottom: 8 }}>
                <Text style={{ color: colors.text, fontWeight: '700' }}>{rule.permissionDisplayName}</Text>
                <Text selectable style={{ color: colors.muted, fontSize: 12 }}>{rule.permissionName}</Text>
              </View>
            ))}

          </Card>
        )}
      </Screen>
      <ProfileEditor
        editor={editor}
        permissions={permissionsQuery.data?.content || []}
        working={saveMutation.isPending}
        error={saveMutation.error}
        onChange={setEditor}
        onClose={() => setEditor(null)}
        onSave={() => { if (editor) saveMutation.mutate(editor); }}
      />
    </>
  );
}

function ProfileEditor({
  editor,
  permissions,
  working,
  error,
  onChange,
  onClose,
  onSave,
}: {
  editor: EditorState;
  permissions: Permission[];
  working: boolean;
  error: unknown;
  onChange: (editor: EditorState) => void;
  onClose: () => void;
  onSave: () => void;
}) {
  const [permissionSearch, setPermissionSearch] = useState('');
  const [permissionName, setPermissionName] = useState('');
  const filteredPermissions = useMemo(() => {
    const keyword = permissionSearch.trim().toLowerCase();
    if (!keyword) return [];
    return permissions.filter(permission => [permission.name, permission.displayName]
      .some(value => value.toLowerCase().includes(keyword))).slice(0, 30);
  }, [permissionSearch, permissions]);
  if (!editor) return null;
  const form = editor.form;
  const selectedPermission = permissions.find(permission => permission.name === permissionName);
  const updateForm = (next: Partial<AuthorizationProfileFormPayload>) => onChange({ ...editor, form: { ...form, ...next } });
  const addRule = () => {
    if (!selectedPermission || form.rules.some(rule => rule.permissionName === permissionName)) return;
    updateForm({ rules: [...form.rules, { permissionName }] });
    setPermissionName('');
    setPermissionSearch('');
  };
  return (
    <EditModal
      visible
      title={editor.mode === 'create' ? '新建岗位模板草稿' : editor.mode === 'copy' ? '复制岗位模板' : '编辑岗位模板草稿'}
      primaryActionLabel={editor.mode === 'copy' ? '创建副本' : '保存草稿'}
      onPrimaryAction={onSave}
      onClose={onClose}
      working={working}
      primaryDisabled={!form.code.trim() || !form.displayName.trim()}
    >
      {!!error && <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(error, '岗位模板保存失败。')}</Text>}
      <TextField label="模板编码" value={form.code} onChangeText={code => updateForm({ code: code.toUpperCase() })} autoCapitalize="characters" />
      <TextField label="模板名称" value={form.displayName} onChangeText={displayName => updateForm({ displayName })} />
      {editor.mode !== 'copy' && (
        <>
          <TextField label="职责说明" value={form.description || ''} onChangeText={description => updateForm({ description })} multiline />
          <TextField label="版本变更说明" value={form.changeSummary || ''} onChangeText={changeSummary => updateForm({ changeSummary })} />
          <TextField label="搜索原子权限" value={permissionSearch} onChangeText={setPermissionSearch} />
          <SelectField
            label="原子权限"
            value={permissionName}
            options={[{ label: '请输入关键字后选择', value: '' }, ...filteredPermissions.map(permission => ({ label: permission.displayName, value: permission.name }))]}
            onChange={setPermissionName}
          />
          <AppButton title="加入原子权限" variant="secondary" onPress={addRule} disabled={!selectedPermission} />
          {form.rules.map(rule => (
            <View key={rule.permissionName} style={{ gap: 4, borderBottomWidth: 1, borderBottomColor: colors.border, paddingBottom: 8 }}>
              <Text style={{ color: colors.text, fontWeight: '700' }}>{permissions.find(permission => permission.name === rule.permissionName)?.displayName || rule.permissionName}</Text>
              <Text selectable style={{ color: colors.muted, fontSize: 12 }}>{rule.permissionName}</Text>
              <AppButton title="移除" variant="ghost" onPress={() => updateForm({ rules: form.rules.filter(item => item !== rule) })} />
            </View>
          ))}
        </>
      )}
    </EditModal>
  );
}

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Alert, Text, View } from 'react-native';

import {
  createAdminUser,
  deleteAdminUser,
  fetchAdminOrgUnits,
  fetchAdminDepartments,
  fetchAdminUsers,
  restoreAdminUser,
  type UserFormPayload,
} from '@/api/admin-api';
import { formatApiErrorMessage } from '@/api/http';
import { evaluatePassword } from '@/auth/password-policy';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import {
  defaultOrganizationId,
  USER_STATUS_LABELS,
  validateUserDetails,
} from '@/features/admin/user-administration';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { PageHeader } from '@/ui/page-header';
import { PasswordPolicyHint } from '@/ui/password-policy-hint';
import { SearchField } from '@/ui/search-field';
import { Screen } from '@/ui/screen';
import { SelectField, type SelectOption } from '@/ui/select-field';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';
import type { AdminUser, Department, OrgUnit, UserStatus } from '@/types/api';

type EditingState = {
  username: string;
  password: string;
  name: string;
  phone: string;
  email: string;
  status: UserStatus;
  orgUnitId: string;
  departmentId: string;
};

const emptyForm: EditingState = {
  username: '',
  password: '',
  name: '',
  phone: '',
  email: '',
  status: 'ACTIVE',
  orgUnitId: '',
  departmentId: '',
};

const USER_PAGE_SIZE = 10;

export default function AdminUsersScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { hasPermission, passwordPolicy } = useAuth();
  const [page, setPage] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const debouncedSearchTerm = useDebouncedValue(searchTerm.trim());
  const [orgUnitFilter, setOrgUnitFilter] = useState('');
  const [departmentFilter, setDepartmentFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState<'' | UserStatus>('');
  const [form, setForm] = useState<EditingState | null>(null);
  const [message, setMessage] = useState('');

  const canView = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_VIEW);
  const canCreate = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_CREATE);
  const canDelete = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_DELETE);
  const canRestore = hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_RESTORE);
  const usersQuery = useQuery({
    queryKey: ['admin-users', page, debouncedSearchTerm, orgUnitFilter, departmentFilter, statusFilter],
    queryFn: () => fetchAdminUsers({
      page,
      size: USER_PAGE_SIZE,
      searchTerm: debouncedSearchTerm || undefined,
      orgUnitId: orgUnitFilter || undefined,
      departmentId: departmentFilter || undefined,
      status: statusFilter || undefined,
      includeDeleted: statusFilter === 'DELETED',
    }),
    enabled: canView,
    placeholderData: keepPreviousData,
    staleTime: 1000 * 30,
  });
  const orgUnitsQuery = useQuery({
    queryKey: ['admin-org-units-for-users'],
    queryFn: fetchAdminOrgUnits,
    enabled: canView,
    staleTime: 1000 * 60 * 5,
  });
  const departmentsQuery = useQuery({
    queryKey: ['admin-departments-for-users'],
    queryFn: fetchAdminDepartments,
    enabled: canView,
    staleTime: 1000 * 60 * 5,
  });
  const organizationFilterOptions = useMemo<SelectOption[]>(() => [
    { value: '', label: '全部组织' },
    ...(orgUnitsQuery.data ?? []).map((organization) => ({ value: organization.id, label: organization.name })),
  ], [orgUnitsQuery.data]);
  const departmentFilterOptions = useMemo<SelectOption[]>(() => [
    { value: '', label: '全部部门' },
    ...(departmentsQuery.data ?? [])
      .filter((department) => department.status === 'ACTIVE' && (!orgUnitFilter || department.orgUnitId === orgUnitFilter))
      .map((department) => ({ value: department.id, label: department.name })),
  ], [departmentsQuery.data, orgUnitFilter]);
  const statusFilterOptions: SelectOption[] = [
    { value: '', label: '全部状态' },
    ...Object.entries(USER_STATUS_LABELS).map(([value, label]) => ({ value, label })),
  ];
  const hasActiveFilters = Boolean(orgUnitFilter || departmentFilter || statusFilter);

  const saveMutation = useMutation({
    mutationFn: async (payload: EditingState) => {
      if (!canCreate) throw new Error('当前账号没有创建用户权限。');
      validateCreateUserForm(payload, passwordPolicy);
      const createPayload: UserFormPayload = {
        username: payload.username.trim(),
        password: payload.password,
        name: payload.name.trim(),
        phone: payload.phone.trim(),
        email: payload.email.trim() || null,
        status: payload.status,
        orgUnitId: payload.orgUnitId,
        departmentId: payload.departmentId || null,
      };
      return createAdminUser(createPayload);
    },
    onSuccess: async (createdUser) => {
      setForm(null);
      await queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      router.push({ pathname: '/admin/users/[userId]', params: { userId: createdUser.id } });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteAdminUser,
    onSuccess: async () => {
      setMessage('用户已删除。');
      await queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
  });

  const restoreMutation = useMutation({
    mutationFn: restoreAdminUser,
    onSuccess: async () => {
      setMessage('用户已恢复。');
      await queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
  });

  const openCreateForm = () => {
    const defaultOrgUnitId = defaultOrganizationId(orgUnitsQuery.data ?? []);
    setForm({
      ...emptyForm,
      orgUnitId: defaultOrgUnitId,
    });
  };

  if (!canView) {
    return (
      <Screen>
        <Card><Text style={{ color: colors.muted }}>当前账号没有用户管理权限。</Text></Card>
      </Screen>
    );
  }

  const operationError = saveMutation.error || deleteMutation.error || restoreMutation.error;

  return (
    <Screen>
      <PageHeader title="用户管理" subtitle={usersQuery.data ? `共 ${usersQuery.data.totalElements} 个账号` : '账号与原子权限授权'} />

      <Card style={{ gap: 10 }}>
        <SearchField
          label="搜索用户（姓名、拼音或首字母）"
          value={searchTerm}
          onChangeText={(value) => {
            setPage(0);
            setSearchTerm(value);
          }}
          placeholder="账号、姓名、手机号或邮箱"
          isSearching={usersQuery.isFetching}
          resultCount={usersQuery.data?.totalElements}
        />
        <SelectField
          label="所属组织"
          value={orgUnitFilter}
          options={organizationFilterOptions}
          onChange={(value) => {
            setPage(0);
            setOrgUnitFilter(value);
            setDepartmentFilter('');
          }}
        />
        <SelectField
          label="所属部门"
          value={departmentFilter}
          options={departmentFilterOptions}
          onChange={(value) => {
            setPage(0);
            setDepartmentFilter(value);
          }}
        />
        <SelectField
          label="账号状态"
          value={statusFilter}
          options={statusFilterOptions}
          onChange={(value) => {
            setPage(0);
            setStatusFilter(value as '' | UserStatus);
          }}
        />
        <View style={{ flexDirection: 'row', gap: 8 }}>
          {canCreate && <AppButton title="添加用户" onPress={openCreateForm} disabled={!orgUnitsQuery.data?.length} />}
          {hasActiveFilters && <AppButton title="清空筛选" onPress={() => {
            setPage(0);
            setOrgUnitFilter('');
            setDepartmentFilter('');
            setStatusFilter('');
          }} variant="secondary" />}
        </View>
      </Card>

      {form && (
        <UserForm
          form={form}
          orgUnits={orgUnitsQuery.data ?? []}
          departments={departmentsQuery.data ?? []}
          passwordPolicy={passwordPolicy}
          isSaving={saveMutation.isPending}
          onChange={setForm}
          onCancel={() => setForm(null)}
          onSubmit={() => saveMutation.mutate(form)}
        />
      )}

      {!!message && (
        <Card><Text style={{ color: colors.primary, fontWeight: '800' }}>{message}</Text></Card>
      )}
      {!!operationError && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(operationError, '用户操作失败，请稍后重试。')}
          </Text>
        </Card>
      )}
      {!!orgUnitsQuery.error && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(orgUnitsQuery.error, '组织目录加载失败，暂时无法维护用户归属。')}
          </Text>
        </Card>
      )}
      {!!departmentsQuery.error && (
        <Card><Text selectable style={{ color: colors.danger }}>
          {formatApiErrorMessage(departmentsQuery.error, '部门目录加载失败，暂时无法筛选部门。')}
        </Text></Card>
      )}
      {!!usersQuery.error && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(usersQuery.error, '用户列表加载失败，请稍后重试。')}
          </Text>
        </Card>
      )}
      {usersQuery.isLoading && <ActivityIndicator color={colors.primary} />}

      {usersQuery.data?.content.map((user) => (
        <UserCard
          key={user.id}
          user={user}
          canDelete={canDelete}
          canRestore={canRestore}
          onOpen={() => router.push({ pathname: '/admin/users/[userId]', params: { userId: user.id } })}
          onDelete={() => confirmDanger(`确认删除用户 ${user.username}？`, () => deleteMutation.mutate(user.id))}
          onRestore={() => restoreMutation.mutate(user.id)}
        />
      ))}

      {usersQuery.data && usersQuery.data.content.length === 0 && (
        <Card><Text style={{ color: colors.muted }}>没有找到用户。</Text></Card>
      )}
      {usersQuery.data && usersQuery.data.totalPages > 1 && (
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <AppButton title="上一页" onPress={() => setPage((value) => Math.max(0, value - 1))} disabled={page <= 0} variant="secondary" />
          <Text style={{ alignSelf: 'center', color: colors.muted }}>第 {page + 1} / {usersQuery.data.totalPages} 页</Text>
          <AppButton title="下一页" onPress={() => setPage((value) => value + 1)} disabled={usersQuery.data.last} variant="secondary" />
        </View>
      )}
    </Screen>
  );
}

function UserForm({
  form,
  orgUnits,
  departments,
  passwordPolicy,
  isSaving,
  onChange,
  onCancel,
  onSubmit,
}: {
  form: EditingState;
  orgUnits: OrgUnit[];
  departments: Department[];
  passwordPolicy: import('@/types/api').PasswordPolicy;
  isSaving: boolean;
  onChange: (form: EditingState) => void;
  onCancel: () => void;
  onSubmit: () => void;
}) {
  return (
    <Card style={{ gap: 12, borderLeftWidth: 5, borderLeftColor: colors.primary }}>
      <Text style={{ fontSize: 20, fontWeight: '900', color: colors.text }}>添加用户</Text>
      <TextField label="用户名" value={form.username} onChangeText={(username) => onChange({ ...form, username })} autoCapitalize="none" />
      <TextField label="初始密码" value={form.password} onChangeText={(password) => onChange({ ...form, password })} secureTextEntry />
      <PasswordPolicyHint password={form.password} context={form} policy={passwordPolicy} />
      <TextField
        label="姓名"
        value={form.name}
        onChangeText={(name) => onChange({ ...form, name })}
      />
      <SelectField
        label="所属组织"
        value={form.orgUnitId}
        options={orgUnits.map(organization => ({ value: organization.id, label: organization.name }))}
        onChange={(orgUnitId) => onChange({ ...form, orgUnitId, departmentId: '' })}
      />
      <SelectField
        label="所属部门（可选）"
        value={form.departmentId}
        options={[
          { value: '', label: '组织直属' },
          ...departments.filter(department => department.orgUnitId === form.orgUnitId && department.status === 'ACTIVE')
            .map(department => ({ value: department.id, label: department.name })),
        ]}
        onChange={(departmentId) => onChange({ ...form, departmentId })}
      />
      <TextField
        label="手机号"
        value={form.phone}
        onChangeText={(phone) => onChange({ ...form, phone })}
        keyboardType="phone-pad"
        maxLength={11}
        required
      />
      <TextField
        label="邮箱"
        value={form.email}
        onChangeText={(email) => onChange({ ...form, email })}
        keyboardType="email-address"
        autoCapitalize="none"
        maxLength={254}
        helperText="可选；邮件通知优先使用此地址"
      />
      <Text style={{ color: colors.muted }}>新用户创建后默认启用。</Text>
      <Text style={{ color: colors.muted }}>保存后进入用户详情，可通过岗位模板或单条方式追加原子权限。</Text>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <AppButton title={isSaving ? '保存中' : '保存'} onPress={onSubmit} disabled={isSaving} loading={isSaving} />
        <AppButton title="取消" onPress={onCancel} variant="secondary" disabled={isSaving} />
      </View>
    </Card>
  );
}

function UserCard({
  user,
  canDelete,
  canRestore,
  onOpen,
  onDelete,
  onRestore,
}: {
  user: AdminUser;
  canDelete: boolean;
  canRestore: boolean;
  onOpen: () => void;
  onDelete: () => void;
  onRestore: () => void;
}) {
  const deleted = user.status === 'DELETED';
  return (
    <Card style={{ gap: 10, opacity: deleted ? 0.68 : 1 }}>
      <View style={{ flexDirection: 'row', gap: 10 }}>
        <View style={{ flex: 1, gap: 4 }}>
          <Text style={{ color: colors.text, fontSize: 18, fontWeight: '900' }}>{user.name || user.username}</Text>
          <Text selectable style={{ color: colors.muted }}>{user.username}</Text>
        </View>
        <StatusPill status={user.status} />
      </View>
      <Text style={{ color: colors.muted }}>授权版本：v{user.authorizationVersion ?? 0}</Text>
      <Text selectable style={{ color: colors.muted }}>
        联系方式：{user.phone || '未补录'}{user.email ? ` · ${user.email}` : ''}
      </Text>
      <Text style={{ color: colors.muted }}>所属组织：{user.orgUnitName || '未分配'}</Text>
      <Text style={{ color: colors.muted }}>所属部门：{user.departmentName || '组织直属'}</Text>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <AppButton title="查看详情" onPress={onOpen} variant="secondary" />
        {!deleted && !user.systemProtected && canDelete && <AppButton title="删除" onPress={onDelete} variant="danger" />}
        {deleted && canRestore && <AppButton title="恢复" onPress={onRestore} />}
      </View>
    </Card>
  );
}

function StatusPill({ status }: { status: UserStatus }) {
  const color = status === 'ACTIVE' ? '#16a34a' : status === 'DELETED' ? colors.danger : '#e07800';
  return (
    <View style={{ alignSelf: 'flex-start', borderRadius: 999, backgroundColor: `${color}18`, paddingHorizontal: 9, paddingVertical: 5 }}>
      <Text style={{ color, fontWeight: '900', fontSize: 12 }}>{USER_STATUS_LABELS[status]}</Text>
    </View>
  );
}

function validateCreateUserForm(payload: EditingState, passwordPolicy: import('@/types/api').PasswordPolicy) {
  if (payload.username.trim().length < 3) {
    throw new Error('用户名至少需要3个字符。');
  }
  if (!evaluatePassword(payload.password, payload, passwordPolicy).valid) {
    throw new Error('初始密码不符合安全策略，请按提示调整。');
  }
  validateUserDetails(payload);
}

function confirmDanger(message: string, onConfirm: () => void) {
  Alert.alert('请确认', message, [
    { text: '取消', style: 'cancel' },
    { text: '确认', style: 'destructive', onPress: onConfirm },
  ]);
}

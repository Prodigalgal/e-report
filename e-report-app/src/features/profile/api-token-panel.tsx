import * as Clipboard from 'expo-clipboard';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Alert, Pressable, Switch, Text, View } from 'react-native';

import { formatApiErrorMessage } from '@/api/http';
import {
  createApiToken,
  fetchApiTokenPermissionOptions,
  fetchApiTokens,
  revokeApiToken,
} from '@/auth/api-token-api';
import {
  buildApiTokenCreatePayload,
  filterApiTokenPermissionOptions,
  MAX_API_TOKEN_EXPIRY_DAYS,
} from '@/features/profile/api-token-workflow';
import type { ApiTokenCreated, ApiTokenPermissionOption, ApiTokenView } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { EditModal } from '@/ui/edit-modal';
import { SearchField } from '@/ui/search-field';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';

type ApiTokenPanelProps = {
  ownerKey: string;
  canView: boolean;
  canCreate: boolean;
  canRevoke: boolean;
};

export function ApiTokenPanel({ ownerKey, canView, canCreate, canRevoke }: ApiTokenPanelProps) {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [longLived, setLongLived] = useState(false);
  const [expiryDays, setExpiryDays] = useState('30');
  const [permissionSearch, setPermissionSearch] = useState('');
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);
  const [createdToken, setCreatedToken] = useState<ApiTokenCreated | null>(null);
  const [copied, setCopied] = useState(false);
  const [message, setMessage] = useState('');

  const tokensQuery = useQuery({
    queryKey: ['api-tokens', ownerKey],
    queryFn: fetchApiTokens,
    enabled: canView,
    staleTime: 15000,
  });
  const optionsQuery = useQuery({
    queryKey: ['api-token-permission-options', ownerKey],
    queryFn: fetchApiTokenPermissionOptions,
    enabled: canCreate,
    staleTime: 30000,
  });
  const filteredOptions = useMemo(
    () => filterApiTokenPermissionOptions(optionsQuery.data || [], permissionSearch),
    [optionsQuery.data, permissionSearch],
  );
  const optionByName = useMemo(
    () => new Map((optionsQuery.data || []).map(option => [option.name, option])),
    [optionsQuery.data],
  );
  const selectedHighRisk = selectedPermissions.filter(permissionName =>
    ['HIGH', 'CRITICAL'].includes(optionByName.get(permissionName)?.riskLevel || '')).length;
  const parsedExpiryDays = Number(expiryDays);
  const expiryValid = longLived || (Number.isInteger(parsedExpiryDays)
    && parsedExpiryDays >= 1 && parsedExpiryDays <= MAX_API_TOKEN_EXPIRY_DAYS);

  const createMutation = useMutation({
    mutationFn: () => createApiToken(buildApiTokenCreatePayload(
      name,
      longLived,
      parsedExpiryDays,
      selectedPermissions,
    )),
    onSuccess: async result => {
      setCreatedToken(result);
      setCreateOpen(false);
      setCopied(false);
      await queryClient.invalidateQueries({ queryKey: ['api-tokens', ownerKey] });
    },
  });
  const revokeMutation = useMutation({
    mutationFn: revokeApiToken,
    onSuccess: async () => {
      setMessage('API Token 已撤销。');
      await queryClient.invalidateQueries({ queryKey: ['api-tokens', ownerKey] });
    },
  });

  const openCreate = () => {
    setName('');
    setLongLived(false);
    setExpiryDays('30');
    setPermissionSearch('');
    setSelectedPermissions([]);
    setMessage('');
    setCreateOpen(true);
  };

  const togglePermission = (permissionName: string) => {
    setSelectedPermissions(current => current.includes(permissionName)
      ? current.filter(value => value !== permissionName)
      : [...current, permissionName]);
  };

  const confirmRevoke = (token: ApiTokenView) => {
    Alert.alert(
      '撤销 API Token',
      `撤销“${token.name}”后，使用该 Token 的大模型或脚本会立即失去访问能力。`,
      [
        { text: '取消', style: 'cancel' },
        { text: '确认撤销', style: 'destructive', onPress: () => revokeMutation.mutate(token.id) },
      ],
    );
  };

  return (
    <Card style={{ gap: 12 }}>
      <View style={{ gap: 5 }}>
        <Text selectable style={{ color: colors.text, fontSize: 19, fontWeight: '900' }}>API Token</Text>
        <Text selectable style={{ color: colors.muted, lineHeight: 20 }}>
          用于大模型或自动化接口调用，权限不会超过当前账号。
        </Text>
      </View>
      {!!message && <Text selectable style={{ color: colors.success, fontWeight: '800' }}>{message}</Text>}
      {!!tokensQuery.error && (
        <Text selectable style={{ color: colors.danger }}>
          {formatApiErrorMessage(tokensQuery.error, 'API Token 加载失败，请稍后重试。')}
        </Text>
      )}
      {!canView && (
        <Text selectable style={{ color: colors.muted }}>
          当前仅可执行已授权的 Token 操作，未授予 Token 查看权限。
        </Text>
      )}
      {canView && (tokensQuery.data || []).map(token => (
        <TokenRow
          key={token.id}
          token={token}
          revoking={revokeMutation.isPending && revokeMutation.variables === token.id}
          onRevoke={() => confirmRevoke(token)}
          canRevoke={canRevoke}
        />
      ))}
      {canView && !tokensQuery.isLoading && !tokensQuery.data?.length && (
        <Text selectable style={{ color: colors.muted }}>暂无 API Token。</Text>
      )}
      {canCreate && (
        <AppButton
          title="创建 Token"
          onPress={openCreate}
          disabled={optionsQuery.isLoading || !optionsQuery.data?.length}
        />
      )}

      <EditModal
        visible={createOpen}
        title="创建 API Token"
        primaryActionLabel="创建"
        onPrimaryAction={() => createMutation.mutate()}
        onClose={() => setCreateOpen(false)}
        working={createMutation.isPending}
        primaryDisabled={!name.trim() || selectedPermissions.length === 0 || !expiryValid}
      >
        <TextField label="Token 名称" value={name} onChangeText={setName} maxLength={80} required />
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <Text selectable style={{ color: colors.text, fontWeight: '800' }}>长期有效</Text>
          <Switch value={longLived} onValueChange={setLongLived} />
        </View>
        {!longLived && (
          <TextField
            label="有效天数"
            value={expiryDays}
            onChangeText={setExpiryDays}
            keyboardType="number-pad"
            errorMessage={!expiryValid ? `请输入 1-${MAX_API_TOKEN_EXPIRY_DAYS} 天` : undefined}
          />
        )}
        {longLived && (
          <Text selectable style={{ color: colors.warning, lineHeight: 20 }}>
            长期 Token 不会自动到期，只应在受控环境使用。
          </Text>
        )}
        <SearchField
          label="搜索权限"
          value={permissionSearch}
          onChangeText={setPermissionSearch}
          resultCount={filteredOptions.length}
          placeholder="权限名称或编码"
        />
        <Text selectable style={{ color: colors.text, fontWeight: '900' }}>
          访问权限 · 已选 {selectedPermissions.length} 项
        </Text>
        {selectedHighRisk > 0 && (
          <Text selectable style={{ color: colors.warning, lineHeight: 20 }}>
            已选择 {selectedHighRisk} 项高风险权限，请确认大模型确实需要执行写操作。
          </Text>
        )}
        {filteredOptions.map(option => (
          <PermissionRow
            key={option.name}
            option={option}
            selected={selectedPermissions.includes(option.name)}
            onPress={() => togglePermission(option.name)}
          />
        ))}
        {filteredOptions.length === 0 && <Text selectable style={{ color: colors.muted }}>没有匹配的权限。</Text>}
        {!!createMutation.error && (
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(createMutation.error, 'API Token 创建失败，请检查后重试。')}
          </Text>
        )}
      </EditModal>

      <EditModal
        visible={!!createdToken}
        title="API Token 已创建"
        primaryActionLabel={copied ? '已复制' : '复制 Token'}
        onPrimaryAction={async () => {
          if (!createdToken?.token) return;
          await Clipboard.setStringAsync(createdToken.token);
          setCopied(true);
        }}
        onClose={() => setCreatedToken(null)}
        secondaryActionLabel="我已保存"
      >
        <Text selectable style={{ color: colors.warning, fontWeight: '800', lineHeight: 21 }}>
          Token 明文只显示这一次，关闭后无法再次查看。
        </Text>
        <View style={{ borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, backgroundColor: colors.surfaceAlt }}>
          <Text selectable style={{ color: colors.text, fontFamily: 'monospace', lineHeight: 21 }}>
            {createdToken?.token || ''}
          </Text>
        </View>
        <Text selectable style={{ color: colors.muted, lineHeight: 20 }}>
          MCP 服务地址：/api/mcp。请在 MCP 客户端使用个人 API Token 作为 Bearer Token；工具范围随当前授权动态更新。
        </Text>
      </EditModal>
    </Card>
  );
}

function TokenRow({ token, revoking, onRevoke, canRevoke }: {
  token: ApiTokenView;
  revoking: boolean;
  onRevoke: () => void;
  canRevoke: boolean;
}) {
  return (
    <View style={{ borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 12, gap: 6 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
        <Text selectable style={{ color: colors.text, fontWeight: '800', flex: 1 }}>{token.name}</Text>
        <Text selectable style={{ color: token.status === 'ACTIVE' ? colors.success : colors.muted, fontWeight: '800' }}>
          {tokenStatusLabel(token.status)}
        </Text>
      </View>
      <Text selectable style={{ color: colors.muted, fontFamily: 'monospace' }}>{token.tokenPrefix}</Text>
      <Text selectable style={{ color: colors.muted, fontSize: 13 }}>
        {token.permissionNames.length} 项权限 · {token.expiresAt ? `到期 ${formatDateTime(token.expiresAt)}` : '长期有效'}
      </Text>
      <Text selectable style={{ color: colors.muted, fontSize: 13 }}>
        最近使用：{token.lastUsedAt ? formatDateTime(token.lastUsedAt) : '尚未使用'}
      </Text>
      {canRevoke && token.status === 'ACTIVE' && (
        <AppButton title={revoking ? '撤销中' : '撤销 Token'} onPress={onRevoke} variant="danger" disabled={revoking} />
      )}
    </View>
  );
}

function PermissionRow({ option, selected, onPress }: {
  option: ApiTokenPermissionOption;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected }}
      onPress={onPress}
      style={({ pressed }) => ({
        minHeight: 58,
        borderWidth: 1,
        borderColor: selected ? colors.primary : colors.controlBorder,
        borderRadius: 8,
        backgroundColor: selected ? colors.controlSelectedSurface : colors.controlSurface,
        padding: 11,
        flexDirection: 'row',
        gap: 10,
        opacity: pressed ? 0.72 : 1,
      })}
    >
      <View style={{ width: 22, height: 22, borderRadius: 4, borderWidth: 1,
        borderColor: selected ? colors.primary : colors.border,
        backgroundColor: selected ? colors.primary : colors.surface,
        alignItems: 'center', justifyContent: 'center' }}>
        {selected && <Text style={{ color: '#fff', fontWeight: '900' }}>✓</Text>}
      </View>
      <View style={{ flex: 1, gap: 3 }}>
        <Text selectable style={{ color: colors.text, fontWeight: '800' }}>{option.displayName}</Text>
        <Text selectable style={{ color: colors.muted, fontSize: 12 }}>{option.name}</Text>
        <Text selectable style={{ color: riskColor(option.riskLevel), fontSize: 12 }}>
          {riskLabel(option.riskLevel)} · {option.description}
        </Text>
      </View>
    </Pressable>
  );
}

function tokenStatusLabel(status: ApiTokenView['status']) {
  return { ACTIVE: '有效', EXPIRED: '已过期', REVOKED: '已撤销' }[status];
}

function riskLabel(risk: ApiTokenPermissionOption['riskLevel']) {
  return { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '关键风险' }[risk];
}

function riskColor(risk: ApiTokenPermissionOption['riskLevel']) {
  return risk === 'HIGH' || risk === 'CRITICAL' ? colors.warning : colors.muted;
}

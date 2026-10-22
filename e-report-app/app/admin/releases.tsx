import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, Alert, Linking, Pressable, Switch, Text, View } from 'react-native';

import {
  deleteAdminAppRelease,
  fetchAdminAppReleases,
  updateAdminAppReleasePolicy,
  type AppReleasePolicyUpdatePayload,
} from '@/api/admin-api';
import { formatApiErrorMessage } from '@/api/http';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import { normalizeAppReleasePolicy } from '@/features/admin/app-release-policy';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { EditModal } from '@/ui/edit-modal';
import { PageHeader } from '@/ui/page-header';
import { SearchField } from '@/ui/search-field';
import { Screen } from '@/ui/screen';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';
import type { AppRelease } from '@/types/api';

export default function AdminReleasesScreen() {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const [page, setPage] = useState(0);
  const [edition, setEdition] = useState<'NATIVE' | 'LITE'>('NATIVE');
  const [searchTerm, setSearchTerm] = useState('');
  const debouncedSearchTerm = useDebouncedValue(searchTerm.trim());
  const [message, setMessage] = useState('');
  const [policyRelease, setPolicyRelease] = useState<AppRelease | null>(null);
  const [policyDraft, setPolicyDraft] = useState({ forceUpdate: false, minSupportedVersionCode: '0', releaseNotes: '' });
  const [policyError, setPolicyError] = useState('');
  const isSuperAdministrator = hasPermission(ADMIN_PERMISSIONS.SYSTEM_SUPER_ADMINISTRATOR);
  const canView = isSuperAdministrator || hasPermission(ADMIN_PERMISSIONS.APP_UPDATE_VIEW);
  const canDelete = isSuperAdministrator || hasPermission(ADMIN_PERMISSIONS.APP_UPDATE_DELETE);
  const canUpdatePolicy = isSuperAdministrator || hasPermission(ADMIN_PERMISSIONS.APP_UPDATE_POLICY_UPDATE);

  const releasesQuery = useQuery({
    queryKey: ['admin-app-releases', edition, page, debouncedSearchTerm],
    queryFn: () => fetchAdminAppReleases({ edition, page, size: 10, searchTerm: debouncedSearchTerm || undefined }),
    enabled: canView,
    placeholderData: keepPreviousData,
    staleTime: 1000 * 30,
  });

  const deleteMutation = useMutation({
    mutationFn: deleteAdminAppRelease,
    onSuccess: async () => {
      setMessage('发布版本已撤销。');
      await queryClient.invalidateQueries({ queryKey: ['admin-app-releases'] });
    },
  });

  const policyMutation = useMutation({
    mutationFn: ({ releaseId, payload }: { releaseId: string; payload: AppReleasePolicyUpdatePayload }) => (
      updateAdminAppReleasePolicy(releaseId, payload)
    ),
    onSuccess: async (release) => {
      setMessage(`构建号 ${release.versionCode} 的升级策略已保存。`);
      setPolicyRelease(null);
      await queryClient.invalidateQueries({ queryKey: ['admin-app-releases'] });
    },
  });

  const openPolicy = (release: AppRelease) => {
    policyMutation.reset();
    setPolicyError('');
    setPolicyRelease(release);
    setPolicyDraft({
      forceUpdate: release.forceUpdate,
      minSupportedVersionCode: String(release.minSupportedVersionCode ?? 0),
      releaseNotes: release.releaseNotes || '',
    });
  };

  const savePolicy = () => {
    if (!policyRelease) return;
    let payload: AppReleasePolicyUpdatePayload;
    try {
      payload = normalizeAppReleasePolicy(policyDraft, policyRelease.versionCode);
    } catch (error) {
      setPolicyError(formatApiErrorMessage(error, '升级策略填写不正确。'));
      return;
    }
    setPolicyError('');
    policyMutation.mutate({
      releaseId: policyRelease.id,
      payload,
    });
  };

  if (!canView) {
    return (
      <Screen>
        <Card><Text style={{ color: colors.muted }}>当前账号没有发布中心管理权限。</Text></Card>
      </Screen>
    );
  }

  return (
    <>
      <Screen>
      <PageHeader title="发布中心" subtitle={releasesQuery.data ? `共 ${releasesQuery.data.totalElements} 个版本` : '安卓发布记录'} />

      <View style={{ flexDirection: 'row', gap: 12 }}>
        <AppButton title="原生版 APK" onPress={() => { setEdition('NATIVE'); setPage(0); }} variant={edition === 'NATIVE' ? 'primary' : 'secondary'} />
        <AppButton title="Lite APK" onPress={() => { setEdition('LITE'); setPage(0); }} variant={edition === 'LITE' ? 'primary' : 'secondary'} />
      </View>

      <Card>
        <SearchField
          label="搜索发布版本"
          value={searchTerm}
          onChangeText={(value) => {
            setPage(0);
            setSearchTerm(value);
          }}
          placeholder="版本、构建号、文件名、校验摘要或说明"
          isSearching={releasesQuery.isFetching}
          resultCount={releasesQuery.data?.totalElements}
        />
      </Card>

      {!!message && <Card><Text style={{ color: colors.primary, fontWeight: '800' }}>{message}</Text></Card>}
      {!!deleteMutation.error && (
        <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(deleteMutation.error, '撤销发布失败，请稍后重试。')}</Text></Card>
      )}
      {!!policyMutation.error && !policyRelease && (
        <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(policyMutation.error, '保存升级策略失败，请稍后重试。')}</Text></Card>
      )}
      {!!releasesQuery.error && (
        <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(releasesQuery.error, '发布记录加载失败，请稍后重试。')}</Text></Card>
      )}
      {releasesQuery.isLoading && <ActivityIndicator color={colors.primary} />}

      {releasesQuery.data && releasesQuery.data.totalPages > 1 && (
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 8 }}>
          <AppButton title="上一页" onPress={() => setPage((value) => Math.max(0, value - 1))} disabled={page <= 0} variant="secondary" />
          <Text style={{ color: colors.muted, alignSelf: 'center', fontVariant: ['tabular-nums'] }}>{page + 1} / {releasesQuery.data.totalPages}</Text>
          <AppButton title="下一页" onPress={() => setPage((value) => value + 1)} disabled={releasesQuery.data.last} variant="secondary" />
        </View>
      )}
      {releasesQuery.data?.content.map((release) => (
        <ReleaseCard
          key={release.id}
          release={release}
          onDownload={release.published && release.apkUrl ? () => Linking.openURL(release.apkUrl) : undefined}
          onDelete={canDelete ? () => confirmDelete(release, () => deleteMutation.mutate(release.id)) : undefined}
          onEditPolicy={canUpdatePolicy ? () => openPolicy(release) : undefined}
        />
      ))}

      {releasesQuery.data && releasesQuery.data.content.length === 0 && (
        <Card><Text style={{ color: colors.muted }}>暂无发布记录。</Text></Card>
      )}

      </Screen>

      <EditModal
        visible={!!policyRelease}
        title="编辑升级策略"
        primaryActionLabel="保存升级策略"
        onPrimaryAction={savePolicy}
        onClose={() => {
          if (!policyMutation.isPending) setPolicyRelease(null);
        }}
        working={policyMutation.isPending}
      >
        {policyRelease && (
          <>
            <Info label="版本" value={`${policyRelease.versionName} · 构建号 ${policyRelease.versionCode}`} />
            <Info label="运行时版本" value={policyRelease.runtimeVersion} />
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
              <View style={{ flex: 1, gap: 3 }}>
                <Text style={{ color: colors.text, fontWeight: '800' }}>强制更新</Text>
                <Text style={{ color: colors.muted, lineHeight: 19 }}>启用后，目标版本以下的客户端不能跳过升级。</Text>
              </View>
              <Switch
                accessibilityLabel="强制更新"
                value={policyDraft.forceUpdate}
                onValueChange={(forceUpdate) => setPolicyDraft({ ...policyDraft, forceUpdate })}
                disabled={policyMutation.isPending}
                trackColor={{ false: colors.border, true: `${colors.primary}88` }}
                thumbColor={policyDraft.forceUpdate ? colors.primary : '#f8fafc'}
              />
            </View>
            <TextField
              label="最低支持构建号"
              value={policyDraft.minSupportedVersionCode}
              onChangeText={(minSupportedVersionCode) => setPolicyDraft({ ...policyDraft, minSupportedVersionCode })}
              keyboardType="number-pad"
              editable={!policyMutation.isPending}
            />
            {!policyDraft.forceUpdate && Number(policyDraft.minSupportedVersionCode) >= policyRelease.versionCode && (
              <Text style={{ color: colors.primary, lineHeight: 20 }}>低于最低支持构建号的客户端仍会被强制升级。</Text>
            )}
            <TextField
              label="更新说明"
              value={policyDraft.releaseNotes}
              onChangeText={(releaseNotes) => setPolicyDraft({ ...policyDraft, releaseNotes })}
              multiline
              maxLength={2000}
              helperText={`${policyDraft.releaseNotes.length} / 2000`}
              editable={!policyMutation.isPending}
            />
            {!!(policyError || policyMutation.error) && (
              <Text selectable style={{ color: colors.danger }}>
                {policyError || formatApiErrorMessage(policyMutation.error, '保存升级策略失败，请稍后重试。')}
              </Text>
            )}
          </>
        )}
      </EditModal>
    </>
  );
}

function ReleaseCard({ release, onDelete, onDownload, onEditPolicy }: {
  release: AppRelease;
  onDelete?: () => void;
  onDownload?: () => void;
  onEditPolicy?: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  return (
    <Card style={{ gap: 16 }}>
      <Pressable accessibilityRole="button" accessibilityLabel={`${release.versionName}，查看版本详情`} accessibilityState={{ expanded }}
        onPress={() => setExpanded(value => !value)} style={{ flexDirection: 'row', alignItems: 'center', gap: 10, minHeight: 48 }}>
        <View style={{ flex: 1, gap: 4 }}>
          <Text style={{ color: colors.text, fontSize: 20, fontWeight: '600' }}>{release.versionName}</Text>
          <Text style={{ color: colors.muted }}>构建号 {release.versionCode} · {formatReleaseChannel(release.channel)}</Text>
        </View>
        <View style={{ borderRadius: 6, backgroundColor: release.forceUpdate ? '#FFF1F1' : '#EFF4FF', paddingHorizontal: 9, paddingVertical: 5 }}>
          <Text style={{ color: release.forceUpdate ? colors.danger : colors.primary, fontWeight: '500', fontSize: 12 }}>
            {release.forceUpdate ? '强制' : '普通'}
          </Text>
        </View>
        <Text style={{ color: colors.muted, fontSize: 20 }}>{expanded ? '⌃' : '⌄'}</Text>
      </Pressable>
      <View style={{ display: expanded ? 'flex' : 'none', gap: 12, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 16 }}>
      <Info label="运行时版本" value={release.runtimeVersion} />
      <Info label="文件" value={`${release.originalFileName} · ${formatBytes(release.fileSize)}`} />
      <Info label="SHA-256" value={release.sha256} selectable />
      {!!release.publishedAt && <Info label="发布时间" value={formatDateTime(release.publishedAt)} />}
      {!!release.releaseNotes && <Text style={{ color: colors.muted, lineHeight: 20 }}>{release.releaseNotes}</Text>}
      {onDownload && <AppButton title="下载此版本" onPress={onDownload} />}
      {onEditPolicy && <AppButton title="编辑升级策略" onPress={onEditPolicy} variant="secondary" />}
      {onDelete && <AppButton title="撤销发布" onPress={onDelete} variant="danger" />}
      </View>
    </Card>
  );
}

function Info({ label, value, selectable }: { label: string; value: string; selectable?: boolean }) {
  return (
    <View style={{ gap: 2 }}>
      <Text style={{ color: colors.muted, fontSize: 12 }}>{label}</Text>
      <Text selectable={selectable} style={{ color: colors.text }}>{value}</Text>
    </View>
  );
}

function formatBytes(bytes?: number) {
  if (!bytes || bytes <= 0) {
    return '0 字节';
  }
  const mb = bytes / 1024 / 1024;
  return `${mb.toFixed(1)} 兆字节`;
}

function confirmDelete(release: AppRelease, onConfirm: () => void) {
  Alert.alert('请确认', `确认撤销 ${release.versionName}（构建号 ${release.versionCode}）？撤销后旧版本不会再提示升级到该版本。`, [
    { text: '取消', style: 'cancel' },
    { text: '确认', style: 'destructive', onPress: onConfirm },
  ]);
}

function formatReleaseChannel(channel?: string) {
  if (!channel || channel === 'stable') return '稳定版';
  if (channel === 'beta') return '测试版';
  return channel;
}

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { fetchAdminObservationEvents, fetchRedisRuntimeStatus, searchAdminObservationUsers } from '@/api/admin-api';
import { formatApiErrorMessage } from '@/api/http';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { PageHeader } from '@/ui/page-header';
import { Screen } from '@/ui/screen';
import { SelectField } from '@/ui/select-field';
import { SectionTabs } from '@/ui/section-tabs';
import { TextField } from '@/ui/text-field';
import { colors, uiLayout } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';
import type {
  ObservationEventSummary,
  ObservationPlatform,
  ObservationSeverity,
  ObservationUserOption,
  ObservationViewMode,
  RedisRuntimeStatus,
} from '@/types/api';

const PAGE_SIZE = 10;
const PERIODS = {
  '24h': 24 * 60 * 60 * 1000,
  '7d': 7 * 24 * 60 * 60 * 1000,
  '30d': 30 * 24 * 60 * 60 * 1000,
} as const;
const PLATFORM_LABELS: Record<string, string> = { WEB: '网页端', ANDROID: '安卓端', SERVER: '服务端' };
const SEVERITY_LABELS: Record<string, string> = { FATAL: '严重错误', ERROR: '错误', WARN: '警告', INFO: '主动诊断' };
const CATEGORY_LABELS: Record<string, string> = {
  SYSTEM: '系统', UI: '界面', NETWORK: '网络', AUTH: '登录认证', LOCATION: '定位',
  WORK_ORDER: '工单', ATTACHMENT: '附件', UPDATE: '应用升级', ADMIN: '后台管理',
};

export default function AdminObservabilityScreen() {
  const router = useRouter();
  const { hasPermission } = useAuth();
  const canView = hasPermission(ADMIN_PERMISSIONS.OBSERVABILITY_VIEW);
  const [view, setView] = useState<ObservationViewMode>('DIAGNOSTIC');
  const [page, setPage] = useState(0);
  const [period, setPeriod] = useState<keyof typeof PERIODS>('24h');
  const [platform, setPlatform] = useState('');
  const [severity, setSeverity] = useState('');
  const [category, setCategory] = useState('');
  const [keyword, setKeyword] = useState('');
  const [userKeyword, setUserKeyword] = useState('');
  const [selectedUser, setSelectedUser] = useState<ObservationUserOption | null>(null);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const debouncedKeyword = useDebouncedValue(keyword.trim());
  const debouncedUserKeyword = useDebouncedValue(userKeyword.trim());
  const usersQuery = useQuery({
    queryKey: ['admin-observation-users', debouncedUserKeyword],
    queryFn: ({ signal }) => searchAdminObservationUsers(debouncedUserKeyword, signal),
    enabled: canView && !selectedUser && debouncedUserKeyword.length > 0,
    staleTime: 30_000,
  });
  const eventsQuery = useQuery({
    queryKey: ['admin-observation-events', view, page, period, platform, severity, category, debouncedKeyword, selectedUser?.id],
    queryFn: ({ signal }) => {
      const to = new Date();
      return fetchAdminObservationEvents({
        page,
        size: PAGE_SIZE,
        from: new Date(to.getTime() - PERIODS[period]).toISOString(),
        to: to.toISOString(),
        platform: (platform || undefined) as ObservationPlatform | undefined,
        severity: (severity || undefined) as ObservationSeverity | undefined,
        view,
        category: category || undefined,
        keyword: debouncedKeyword || undefined,
        userId: selectedUser?.id,
      }, signal);
    },
    enabled: canView,
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
  const redisQuery = useQuery({
    queryKey: ['redis-runtime-status'],
    queryFn: ({ signal }) => fetchRedisRuntimeStatus(signal),
    enabled: canView,
    staleTime: 30_000,
  });

  if (!canView) {
    return <Screen><Card><Text style={{ color: colors.muted }}>当前账号没有运行诊断查看权限。</Text></Card></Screen>;
  }

  const updateFilter = (setter: (value: string) => void) => (value: string) => {
    setPage(0);
    setter(value);
  };
  const refreshing = eventsQuery.isFetching || redisQuery.isFetching;

  return (
    <Screen>
      <PageHeader title="运行诊断" subtitle={eventsQuery.data ? `当前条件共 ${eventsQuery.data.totalElements} 条` : '客户端与服务运行记录'}
        trailing={<AppButton title={refreshing ? '正在刷新' : '刷新'} disabled={refreshing} loading={refreshing} variant="secondary"
          onPress={() => { void Promise.all([eventsQuery.refetch(), redisQuery.refetch()]); }} />} />
      <RedisStatusCard status={redisQuery.data} loading={redisQuery.isLoading} error={redisQuery.error} />

      <View style={{ gap: 10 }}>
        <SectionTabs label="观测数据视图" value={view} onChange={nextView => { setPage(0); setSeverity(''); setView(nextView); }}
          options={[{ value: 'DIAGNOSTIC', label: '故障诊断' }, { value: 'BEHAVIOR', label: '操作轨迹' }]} />
        <Text style={{ color: colors.muted, fontSize: 13 }}>
          {view === 'DIAGNOSTIC' ? '错误、警告、失败操作和主动诊断' : '按用户查看页面访问和关键操作过程'}
        </Text>
      </View>

      <Card style={{ gap: 10 }}>
        <TextField
          label="关键词或错误编号"
          value={keyword}
          onChangeText={updateFilter(setKeyword)}
          placeholder="事件、消息、页面或请求编号"
          autoCapitalize="none"
          autoCorrect={false}
        />
        <SelectField label="时间范围" value={period} options={[
          { label: '最近24小时', value: '24h' }, { label: '最近7天', value: '7d' }, { label: '最近30天', value: '30d' },
        ]} onChange={value => { setPage(0); setPeriod(value as keyof typeof PERIODS); }} />
        <AppButton title={filtersExpanded ? '收起筛选' : `更多筛选${[platform, severity, category, selectedUser?.id].filter(Boolean).length ? ` (${[platform, severity, category, selectedUser?.id].filter(Boolean).length})` : ''}`}
          variant="ghost" onPress={() => setFiltersExpanded(value => !value)} />
        <View style={{ display: filtersExpanded ? 'flex' : 'none', gap: 12 }}>
        <TextField
          label="用户筛选"
          value={userKeyword}
          onChangeText={(value) => {
            setUserKeyword(value);
            if (!value.trim()) {
              setSelectedUser(null);
              setPage(0);
            }
          }}
          placeholder="输入姓名、账号或拼音"
          autoCapitalize="none"
          autoCorrect={false}
          helperText={selectedUser ? `已选择：${userOptionLabel(selectedUser)}` : '输入后显示可选用户，不会一次加载全部人员'}
        />
        {usersQuery.isFetching && <ActivityIndicator color={colors.primary} />}
        {!selectedUser && debouncedUserKeyword && usersQuery.data?.slice(0, 6).map((user) => (
          <Pressable
            key={user.id}
            accessibilityRole="button"
            onPress={() => { setSelectedUser(user); setUserKeyword(userOptionLabel(user)); setPage(0); }}
            style={({ pressed }) => ({
              minHeight: uiLayout.controlHeight,
              justifyContent: 'center',
              paddingHorizontal: 12,
              borderWidth: 1,
              borderColor: colors.controlBorder,
              backgroundColor: pressed ? colors.controlPressed : colors.controlSurface,
              borderRadius: 8,
              opacity: pressed ? 0.72 : 1,
            })}
          >
            <Text style={{ color: colors.text, fontWeight: '700' }}>{userOptionLabel(user)}</Text>
          </Pressable>
        ))}
        {selectedUser && (
          <AppButton
            title="清除用户筛选"
            variant="ghost"
            onPress={() => { setSelectedUser(null); setUserKeyword(''); setPage(0); }}
          />
        )}
        <View style={{ flexDirection: 'row', gap: 10 }}>
          <View style={{ flex: 1 }}>
            <SelectField label="平台" value={platform} options={[
              { label: '全部', value: '' },
              { label: '网页端', value: 'WEB' },
              { label: '安卓端', value: 'ANDROID' },
              { label: '服务端', value: 'SERVER' },
            ]} onChange={updateFilter(setPlatform)} />
          </View>
        </View>
        <View style={{ flexDirection: 'row', gap: 10 }}>
          <View style={{ flex: 1 }}>
            <SelectField label="级别" value={severity} options={[
              { label: '全部', value: '' },
              { label: '严重错误', value: 'FATAL' },
              { label: '错误', value: 'ERROR' },
              { label: '警告', value: 'WARN' },
              { label: '信息', value: 'INFO' },
            ]} onChange={updateFilter(setSeverity)} />
          </View>
          <View style={{ flex: 1 }}>
            <SelectField label="功能" value={category} options={[
              { label: '全部', value: '' },
              ...Object.entries(CATEGORY_LABELS).map(([value, label]) => ({ label, value })),
            ]} onChange={updateFilter(setCategory)} />
          </View>
        </View>
        </View>
      </Card>

      {eventsQuery.isLoading && <ActivityIndicator color={colors.primary} />}
      {!!eventsQuery.error && (
        <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(eventsQuery.error, '运行诊断加载失败，请稍后重试。')}</Text></Card>
      )}
      {eventsQuery.data && eventsQuery.data.totalPages > 1 && (
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <View style={{ flex: 1 }}><AppButton title="上一页" onPress={() => setPage((value) => Math.max(0, value - 1))} disabled={page <= 0} variant="secondary" /></View>
          <View style={{ justifyContent: 'center', paddingHorizontal: 12 }}><Text style={{ color: colors.muted, fontVariant: ['tabular-nums'] }}>{page + 1} / {eventsQuery.data.totalPages}</Text></View>
          <View style={{ flex: 1 }}><AppButton title="下一页" onPress={() => setPage((value) => value + 1)} disabled={eventsQuery.data.last} variant="secondary" /></View>
        </View>
      )}
      {eventsQuery.data?.content.map((event) => (
        <ObservationEventCard key={event.id} event={event} onPress={() => router.push(`/admin/observability/${event.id}`)} />
      ))}
      {eventsQuery.data && eventsQuery.data.content.length === 0 && (
        <Card><Text style={{ color: colors.muted }}>当前条件下没有{view === 'DIAGNOSTIC' ? '故障诊断' : '操作轨迹'}记录。</Text></Card>
      )}

    </Screen>
  );
}

function RedisStatusCard({ status, loading, error }: { status?: RedisRuntimeStatus; loading: boolean; error: unknown }) {
  const [expanded, setExpanded] = useState(false);
  if (loading) return <ActivityIndicator color={colors.primary} />;
  if (error || !status?.available) {
    return <Card><Text selectable style={{ color: colors.danger }}>{status?.error || formatApiErrorMessage(error, 'Redis 运行状态暂时无法读取。')}</Text></Card>;
  }
  return (
    <Card style={{ gap: 12 }}>
      <Pressable accessibilityRole="button" accessibilityLabel="查看 Redis 运行指标" accessibilityState={{ expanded }} onPress={() => setExpanded(value => !value)}
        style={{ flexDirection: 'row', alignItems: 'center', gap: 8, minHeight: 32 }}>
        <View style={{ width: 7, height: 7, borderRadius: 4, backgroundColor: colors.success }} />
        <Text style={{ flex: 1, color: colors.text, fontSize: 15, fontWeight: '600' }}>Redis 运行正常</Text>
        <Text style={{ color: colors.muted }}>v{status.version || '-'}</Text>
        <Text style={{ color: colors.muted, fontSize: 20 }}>{expanded ? '⌃' : '⌄'}</Text>
      </Pressable>
      <View style={{ display: expanded ? 'flex' : 'none', flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}>
        <StatusMetric label="内存" value={`${formatBytes(status.usedMemoryBytes)} / ${formatBytes(status.maxMemoryBytes)}`} />
        <StatusMetric label="命中率" value={`${formatNumber(status.hitRate, 2)}%`} />
        <StatusMetric label="连接 / 键" value={`${formatNumber(status.connectedClients)} / ${formatNumber(status.totalKeys)}`} />
        <StatusMetric label="诊断队列" value={`${formatNumber(status.observationReadyBatches)} / ${formatNumber(status.observationProcessingBatches)} / ${formatNumber(status.observationDeadLetterBatches)}`} />
      </View>
    </Card>
  );
}

function StatusMetric({ label, value }: { label: string; value: string }) {
  return <View style={{ flexBasis: '47%', flexGrow: 1, gap: 3 }}><Text style={{ color: colors.muted, fontSize: 12 }}>{label}</Text><Text selectable style={{ color: colors.text, fontWeight: '800', fontVariant: ['tabular-nums'] }}>{value}</Text></View>;
}

function ObservationEventCard({ event, onPress }: { event: ObservationEventSummary; onPress: () => void }) {
  const color = event.severity === 'FATAL' || event.severity === 'ERROR' ? colors.danger : event.severity === 'WARN' ? colors.warning : colors.primary;
  const severityLabel = event.eventType === 'OPERATION'
    ? (event.severity === 'INFO' ? '成功' : '失败')
    : (SEVERITY_LABELS[event.severity] || event.severity);
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`查看${event.eventName}诊断详情`} onPress={onPress} style={({ pressed }) => ({ opacity: pressed ? 0.76 : 1 })}>
      <Card style={{ gap: 10 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <Text style={{ color, fontWeight: '600', backgroundColor: `${color}0D`, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 5 }}>{severityLabel}</Text>
          <Text style={{ color: colors.muted }}>{PLATFORM_LABELS[event.platform] || event.platform}</Text>
          <Text style={{ marginLeft: 'auto', color: colors.muted, fontSize: 12 }}>{formatTime(event.clientOccurredAt || event.receivedAt)}</Text>
        </View>
        <Text style={{ color: colors.text, fontSize: 15, fontWeight: '600' }}>{CATEGORY_LABELS[event.category] || event.category} · {event.eventName}</Text>
        <Text numberOfLines={3} style={{ color: colors.muted, lineHeight: 22 }}>{event.message || '没有附加消息'}</Text>
        <Text style={{ color: colors.muted, fontSize: 12 }}>{environmentLabel(event)} · {event.orgUnitName || '租户级'} / {event.userName || '未登录'}</Text>
        <Text style={{ color: colors.primary, fontSize: 13 }}>查看详情 →</Text>
      </Card>
    </Pressable>
  );
}

function userOptionLabel(user: ObservationUserOption) {
  return [user.name || user.username, user.username, user.orgUnitName].filter(Boolean).join(' · ');
}

function environmentLabel(event: ObservationEventSummary) {
  if (event.platform === 'WEB') {
    return [
      event.osVersion || event.osName || '',
      event.deviceModel ? `浏览器 ${event.deviceModel}` : '',
      event.appVersion && event.appVersion !== 'web' ? `应用 ${event.appVersion}` : '',
    ].filter(Boolean).join(' · ') || '-';
  }
  return [
    event.appVersion && event.appVersion !== 'web' ? `应用 ${event.appVersion}` : '',
    event.manufacturer ? `厂商 ${event.manufacturer}` : '',
    event.deviceModel ? `型号 ${event.deviceModel}` : '',
    event.osVersion ? `系统 ${event.osVersion}` : '',
  ].filter(Boolean).join(' · ') || '-';
}

function formatTime(value?: string) {
  return formatDateTime(value);
}

function formatBytes(value?: number) {
  if (!value || value <= 0) return '-';
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function formatNumber(value?: number, maximumFractionDigits = 0) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue.toLocaleString('zh-CN', { maximumFractionDigits }) : '-';
}

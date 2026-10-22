import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Text, View } from 'react-native';

import { fetchAdminObservationEvent } from '@/api/admin-api';
import { formatApiErrorMessage } from '@/api/http';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import { Card } from '@/ui/card';
import { Screen } from '@/ui/screen';
import { colors } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';

export default function AdminObservationDetailScreen() {
  const { id } = useLocalSearchParams<{ id?: string | string[] }>();
  const eventId = Array.isArray(id) ? id[0] : id;
  const { hasPermission } = useAuth();
  const canView = hasPermission(ADMIN_PERMISSIONS.OBSERVABILITY_VIEW);
  const query = useQuery({
    queryKey: ['admin-observation-event', eventId],
    queryFn: ({ signal }) => fetchAdminObservationEvent(eventId || '', signal),
    enabled: canView && Boolean(eventId),
    staleTime: 60_000,
  });

  if (!canView) {
    return <Screen><Card><Text style={{ color: colors.muted }}>当前账号没有运行诊断查看权限。</Text></Card></Screen>;
  }
  if (!eventId) {
    return <Screen><Card><Text selectable style={{ color: colors.danger }}>诊断记录标识无效。</Text></Card></Screen>;
  }

  return (
    <Screen>
      {query.isLoading && <ActivityIndicator color={colors.primary} />}
      {!!query.error && <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(query.error, '诊断详情加载失败，请稍后重试。')}</Text></Card>}
      {query.data && (
        <>
          <Card style={{ gap: 10 }}>
            <Info label="发生时间" value={formatTime(query.data.clientOccurredAt || query.data.receivedAt)} />
            <Info label="级别 / 类型" value={`${query.data.severity} / ${query.data.eventType}`} />
            <Info label="功能 / 事件" value={`${query.data.category} / ${query.data.eventName}`} />
            <Info label="页面或接口" value={query.data.route || '-'} />
            <Info label="请求编号" value={query.data.traceId || '-'} selectable />
            <Info label="组织 / 用户" value={`${query.data.orgUnitName || '租户级'} / ${query.data.userName || '未登录'}`} />
            <Info label="应用环境" value={[
              query.data.appVersion ? `版本 ${query.data.appVersion}` : '',
              query.data.appBuild ? `构建 ${query.data.appBuild}` : '',
              query.data.manufacturer ? `厂商 ${query.data.manufacturer}` : '',
              query.data.deviceModel ? `型号 ${query.data.deviceModel}` : '',
              query.data.osName,
              query.data.osVersion,
            ].filter(Boolean).join(' · ') || '-'} />
            <Info label="网络" value={query.data.networkType || '-'} />
          </Card>
          <DetailBlock label="消息" value={query.data.message} />
          <DetailBlock label="错误堆栈" value={query.data.stackTrace} monospace />
          <DetailBlock label="诊断上下文" value={formatJson(query.data.context)} monospace />
          <DetailBlock label="最近操作" value={formatJson(query.data.breadcrumbs)} monospace />
        </>
      )}
    </Screen>
  );
}

function Info({ label, value, selectable = false }: { label: string; value: string; selectable?: boolean }) {
  return <View style={{ gap: 3 }}><Text style={{ color: colors.muted, fontSize: 12 }}>{label}</Text><Text selectable={selectable} style={{ color: colors.text, fontWeight: '700' }}>{value}</Text></View>;
}

function DetailBlock({ label, value, monospace = false }: { label: string; value?: string; monospace?: boolean }) {
  if (!value) return null;
  return <Card style={{ gap: 6 }}><Text style={{ color: colors.muted, fontSize: 12 }}>{label}</Text><Text selectable style={{ color: colors.text, lineHeight: 20, fontFamily: monospace ? 'monospace' : undefined }}>{value}</Text></Card>;
}

function formatJson(value: unknown) {
  if (value == null) return '';
  try { return JSON.stringify(value, null, 2); } catch { return String(value); }
}

function formatTime(value?: string) {
  return formatDateTime(value);
}

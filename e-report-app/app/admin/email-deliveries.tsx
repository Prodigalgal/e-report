import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, Modal, ScrollView, Text, View } from 'react-native';
import options from '../../../shared/email-delivery.json';
import { defaultEmailDeliveryRange, validateEmailDeliveryRange } from '../../../shared/email-delivery';
import { fetchEmailDelivery, fetchEmailDeliveryScopes, searchEmailDeliveries, type EmailDelivery, type EmailDeliveryFilters } from '@/api/email-delivery-api';
import { formatApiErrorMessage } from '@/api/http';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { InfoRow } from '@/ui/info-row';
import { PageHeader } from '@/ui/page-header';
import { Screen } from '@/ui/screen';
import { SearchField } from '@/ui/search-field';
import { SelectField } from '@/ui/select-field';
import { TextField } from '@/ui/text-field';
import { colors, uiLayout } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';

const initialFilters = (): EmailDeliveryFilters => ({ category: '', status: '', keyword: '', orgUnitId: '', ...defaultEmailDeliveryRange() });

export default function EmailDeliveryScreen() {
  const { hasPermission } = useAuth();
  const allowed = hasPermission(ADMIN_PERMISSIONS.EMAIL_DELIVERY_VIEW);
  const [draft, setDraft] = useState(initialFilters);
  const [filters, setFilters] = useState(initialFilters);
  const [page, setPage] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const [validation, setValidation] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const query = useQuery({ queryKey: ['email-deliveries', filters, page], queryFn: () => searchEmailDeliveries(filters, page), enabled: allowed });
  const scopes = useQuery({ queryKey: ['email-delivery-scopes'], queryFn: fetchEmailDeliveryScopes, enabled: allowed });
  const detail = useQuery({ queryKey: ['email-delivery', selectedId], queryFn: () => fetchEmailDelivery(selectedId!), enabled: allowed && selectedId !== null });
  const update = (key: keyof EmailDeliveryFilters, value: string) => setDraft(previous => ({ ...previous, [key]: value }));
  const search = () => {
    const error = validateEmailDeliveryRange(draft.from, draft.to);
    setValidation(error);
    if (!error) { setPage(0); setFilters({ ...draft, keyword: draft.keyword.trim() }); }
  };

  if (!allowed) return <Screen><Card><Text style={{ color: colors.muted }}>当前账号没有邮件台账查看权限。</Text></Card></Screen>;

  return <Screen>
    <PageHeader title="邮件发送台账" subtitle="按收件人追踪实际发送尝试" trailing={<AppButton title="刷新" variant="secondary" onPress={() => { void query.refetch(); }} disabled={query.isFetching} />} />
    <Card style={{ gap: 8 }}>
      <SearchField label="关键词" value={draft.keyword} onChangeText={value => update('keyword', value)} placeholder="主题、脱敏地址、记录或关联编号" maxLength={160} onSubmitEditing={search} />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <View style={{ flex: 1 }}><SelectField label="通知类型" value={draft.category} onChange={value => update('category', value)} options={[{ value: '', label: '全部类型' }, ...Object.entries(options.categories).map(([value, label]) => ({ value, label }))]} /></View>
        <View style={{ flex: 1 }}><SelectField label="发送状态" value={draft.status} onChange={value => update('status', value)} options={[{ value: '', label: '全部状态' }, ...Object.entries(options.statuses).map(([value, presentation]) => ({ value, label: presentation.label }))]} /></View>
      </View>
      {expanded && <>
        <SelectField label="归属组织" value={draft.orgUnitId} onChange={value => update('orgUnitId', value)} options={[{ value: '', label: '全部职责范围' }, ...(scopes.data ?? []).map(scope => ({ value: scope.id, label: scope.name }))]} />
        {!!scopes.error && <Text style={{ color: colors.danger }}>{formatApiErrorMessage(scopes.error, '组织范围加载失败。')}</Text>}
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <View style={{ flex: 1 }}><TextField label="发送日期从" value={draft.from} onChangeText={value => update('from', value)} placeholder="YYYY-MM-DD" maxLength={10} /></View>
          <View style={{ flex: 1 }}><TextField label="发送日期至" value={draft.to} onChangeText={value => update('to', value)} placeholder="YYYY-MM-DD" maxLength={10} /></View>
        </View>
        <AppButton title="重置条件" variant="ghost" onPress={() => { const reset = initialFilters(); setDraft(reset); setFilters(reset); setPage(0); setValidation(null); }} />
      </>}
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <View style={{ flex: 1 }}><AppButton title="查询" onPress={search} disabled={query.isFetching} /></View>
        <View style={{ flex: 1 }}><AppButton title={expanded ? '收起条件' : '更多条件'} variant="secondary" onPress={() => setExpanded(value => !value)} /></View>
      </View>
    </Card>
    {!!validation && <Text style={{ color: colors.danger }}>{validation}</Text>}
    {!!query.error && <Card><Text style={{ color: colors.danger }}>{formatApiErrorMessage(query.error, '邮件发送记录加载失败。')}</Text></Card>}
    <Text style={{ color: colors.muted, fontSize: 13 }}>共 {query.data?.totalElements ?? 0} 次 · {filters.from} 至 {filters.to}</Text>
    {query.isFetching && <ActivityIndicator color={colors.primary} />}
    {!query.error && query.data?.content.map(delivery => <Card key={delivery.id} style={{ gap: 6 }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: 8 }}><Text style={{ color: colors.muted, fontSize: 13 }}>{options.categories[delivery.category]}</Text><DeliveryStatus delivery={delivery} /></View>
      <Text style={{ color: colors.text, fontWeight: '600' }}>{delivery.subject}</Text>
      <Text style={{ color: colors.muted }}>收件：{delivery.recipientAddress}</Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}><Text style={{ color: colors.muted, fontSize: 13, flex: 1 }}>{formatDateTime(delivery.startedAt)}</Text><AppButton title="详情" variant="ghost" onPress={() => setSelectedId(delivery.id)} /></View>
    </Card>)}
    {!query.isLoading && !query.error && query.data?.content.length === 0 && <Card><Text style={{ color: colors.muted }}>当前条件下没有发送记录。台账从功能启用后开始记录。</Text></Card>}
    {query.data && query.data.totalPages > 1 && <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      <AppButton title="上一页" variant="secondary" disabled={page === 0 || query.isFetching} onPress={() => setPage(value => value - 1)} />
      <Text style={{ flex: 1, color: colors.muted, textAlign: 'center' }}>{page + 1} / {query.data.totalPages}</Text>
      <AppButton title="下一页" variant="secondary" disabled={page + 1 >= query.data.totalPages || query.isFetching} onPress={() => setPage(value => value + 1)} />
    </View>}
    <Text style={{ color: colors.muted, fontSize: 12 }}>SMTP 已接受不能证明收件箱送达或已读。时间为 UTC+8。</Text>
    <Modal visible={selectedId !== null} transparent animationType="fade" onRequestClose={() => setSelectedId(null)}>
      <View style={{ flex: 1, justifyContent: 'center', backgroundColor: 'rgba(15,23,42,0.42)', padding: uiLayout.pageGutter }}>
        <Card style={{ width: '100%', maxWidth: uiLayout.dialogMaxWidth, maxHeight: '88%', alignSelf: 'center' }}>
          <PageHeader title="邮件发送详情" />
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ gap: 12 }}>
            {detail.isLoading && <ActivityIndicator color={colors.primary} />}
            {!!detail.error && <Text style={{ color: colors.danger }}>{formatApiErrorMessage(detail.error, '邮件详情加载失败。')}</Text>}
            {detail.data && <>
              <DeliveryStatus delivery={detail.data} />
              {!!detail.data.failureMessage && <Text style={{ color: colors.warning }}>{detail.data.failureMessage}</Text>}
              <InfoRow label="通知类型" value={options.categories[detail.data.category]} />
              <InfoRow label="主题" value={detail.data.subject} /><InfoRow label="归属组织" value={detail.data.orgUnitName} />
              <InfoRow label="收件地址" value={detail.data.recipientAddress} /><InfoRow label="发件地址" value={detail.data.senderAddress || '未选定发件方'} />
              <InfoRow label="开始时间" value={formatDateTime(detail.data.startedAt)} /><InfoRow label="结束时间" value={detail.data.completedAt ? formatDateTime(detail.data.completedAt) : '尚未确认'} />
              <InfoRow label="耗时" value={detail.data.durationMs == null ? '-' : `${detail.data.durationMs} ms`} />
              <InfoRow label="失败分类" value={detail.data.failureCode} /><InfoRow label="关联编号" value={detail.data.relatedId} /><InfoRow label="记录编号" value={detail.data.id} />
            </>}
          </ScrollView>
          <AppButton title="关闭" variant="secondary" onPress={() => setSelectedId(null)} />
        </Card>
      </View>
    </Modal>
  </Screen>;
}

function DeliveryStatus({ delivery }: { delivery: EmailDelivery }) {
  const presentation = options.statuses[delivery.status] || options.statuses.UNKNOWN;
  const tones: Record<string, string> = { info: colors.primary, success: colors.success, error: colors.danger, warning: colors.warning };
  const tone = tones[presentation.color];
  return <Text accessibilityLabel={`发送状态：${presentation.label}`} style={{ color: tone || colors.text, fontSize: 13, fontWeight: '600' }}>{presentation.label}</Text>;
}

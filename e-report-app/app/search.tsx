import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, Text, View } from 'react-native';

import { formatApiErrorMessage } from '@/api/http';
import { searchWorkOrders } from '@/api/work-order-api';
import {
  WORK_ORDER_TYPES,
} from '@/constants/work-orders';
import { WorkOrderCard } from '@/features/work-orders/work-order-card';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { PageHeader } from '@/ui/page-header';
import { SearchField } from '@/ui/search-field';
import { Screen } from '@/ui/screen';
import { MultiSelectField } from '@/ui/multi-select-field';
import { colors, uiLayout } from '@/ui/theme';
import { normalizeSelectedValues } from '@/utils/multi-select';

const PAGE_SIZE = 20;
const STATUS_FILTERS = [
  { label: '全部状态', value: '' },
  { label: '已提交', value: 'SUBMITTED' },
  { label: '处理中', value: 'IN_PROGRESS' },
  { label: '待验收', value: 'PENDING_VERIFICATION' },
  { label: '已挂起', value: 'ON_HOLD' },
  { label: '已闭环', value: 'CLOSED' },
  { label: '已取消', value: 'CANCELLED' },
] as const;
const QUEUE_FILTERS = [
  { label: '全部工单', value: '' },
  { label: '可接单', value: 'CLAIMABLE' },
  { label: '待我接收', value: 'ASSIGNED_TO_ME' },
  { label: '我的处理中', value: 'PROCESSING_BY_ME' },
  { label: '待我验收', value: 'PENDING_VERIFICATION' },
  { label: '我提交的', value: 'REPORTED_BY_ME' },
] as const;
const STATUS_FILTER_VALUES = STATUS_FILTERS.flatMap((item) => item.value ? [item.value] : []);
const QUEUE_FILTER_VALUES = QUEUE_FILTERS.flatMap((item) => item.value ? [item.value] : []);
const REPORT_TYPE_FILTERS = [
  { label: '全部类型', value: '' },
  ...WORK_ORDER_TYPES.map((item) => ({ label: item.navLabel, value: item.reportType })),
];

export default function WorkOrderSearchScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{ type?: string | string[]; status?: string | string[]; queue?: string | string[] }>();
  const initialReportTypes = resolveInitialReportTypes(params.type, WORK_ORDER_TYPES);
  const initialStatuses = normalizeSelectedValues(params.status, STATUS_FILTER_VALUES);
  const initialQueues = normalizeSelectedValues(params.queue, QUEUE_FILTER_VALUES);
  const [searchTerm, setSearchTerm] = useState('');
  const [reportTypes, setReportTypes] = useState(initialReportTypes);
  const [statuses, setStatuses] = useState(initialStatuses);
  const [queues, setQueues] = useState(initialQueues);
  const [page, setPage] = useState(0);
  const [filterPanelOpen, setFilterPanelOpen] = useState(false);
  const debouncedSearchTerm = useDebouncedValue(searchTerm.trim());

  useEffect(() => {
    setPage(0);
  }, [debouncedSearchTerm, queues, reportTypes, statuses]);

  const query = useQuery({
    queryKey: ['work-order-search', queues, reportTypes, statuses, debouncedSearchTerm, page],
    queryFn: () => searchWorkOrders({
      page,
      size: PAGE_SIZE,
      reportType: reportTypes.length > 0 ? reportTypes : undefined,
      status: statuses.length > 0 ? statuses : undefined,
      searchTerm: debouncedSearchTerm || undefined,
      queue: queues.length > 0 ? queues : undefined,
    }),
    placeholderData: keepPreviousData,
    staleTime: 1000 * 60,
  });

  const openWorkOrder = (item: NonNullable<typeof query.data>['content'][number]) => {
    router.push(`/work-orders/${item.id}`);
  };

  const activeFilterCount = queues.length + reportTypes.length + statuses.length;
  const filterSummary = activeFilterCount > 0 ? `已选 ${activeFilterCount} 项` : '未设置筛选条件';

  return (
    <Screen>
      <PageHeader
        title="工单查询"
        subtitle="集中查看本人上报、待接收、处理中和待验收工单"
      />

      <Card style={{ padding: 12, gap: 8 }}>
        <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 8 }}>
          <View style={{ flex: 1, minWidth: 0 }}>
            <SearchField
              label="关键词"
              value={searchTerm}
              onChangeText={setSearchTerm}
              placeholder="#工单号、地点、联系人、原因、报单人"
              isSearching={query.isFetching}
            />
          </View>
          <AppButton
            title={`筛选${activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}`}
            variant="secondary"
            accessibilityLabel={`打开筛选条件，${filterSummary}`}
            onPress={() => setFilterPanelOpen(true)}
          />
        </View>
      </Card>

      <Modal transparent visible={filterPanelOpen} animationType="slide" onRequestClose={() => setFilterPanelOpen(false)}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="关闭筛选条件"
          onPress={() => setFilterPanelOpen(false)}
          style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(15, 23, 42, 0.42)' }}
        >
          <Pressable
            accessibilityRole="none"
            onPress={(event) => event.stopPropagation()}
            style={{ width: '100%', maxWidth: uiLayout.dialogMaxWidth, alignSelf: 'center', maxHeight: '86%', borderTopLeftRadius: 16, borderTopRightRadius: 16, backgroundColor: colors.surface }}
          >
            <View style={{ padding: uiLayout.cardPadding, borderBottomWidth: 1, borderBottomColor: colors.border, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <View>
                <Text style={{ color: colors.text, fontSize: 18, fontWeight: '900' }}>筛选条件</Text>
                <Text style={{ color: colors.muted, fontSize: 13, marginTop: 3 }}>{filterSummary}</Text>
              </View>
              <AppButton title="关闭" onPress={() => setFilterPanelOpen(false)} variant="ghost" />
            </View>
            <ScrollView contentContainerStyle={{ gap: uiLayout.fieldGap, padding: uiLayout.cardPadding }} keyboardShouldPersistTaps="handled">
              <MultiSelectField label="工作队列" values={queues} options={QUEUE_FILTERS} allLabel="全部工单" onChange={setQueues} />
              <MultiSelectField label="工单类型" values={reportTypes} options={REPORT_TYPE_FILTERS} allLabel="全部类型" onChange={setReportTypes} />
              <MultiSelectField label="处理状态" values={statuses} options={STATUS_FILTERS} allLabel="全部状态" onChange={setStatuses} />
            </ScrollView>
            <View style={{ flexDirection: 'row', gap: uiLayout.fieldGap, padding: uiLayout.cardPadding, borderTopWidth: 1, borderTopColor: colors.border }}>
              <View style={{ flex: 1 }}>
                <AppButton title="关闭" variant="secondary" onPress={() => setFilterPanelOpen(false)} />
              </View>
              <View style={{ flex: 1 }}>
                <AppButton title="应用筛选" onPress={() => setFilterPanelOpen(false)} />
              </View>
            </View>
          </Pressable>
        </Pressable>
      </Modal>

      {query.isLoading && <ActivityIndicator color={colors.primary} />}
      {!!query.error && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(query.error, '工单搜索失败，请检查网络后重试。')}
          </Text>
        </Card>
      )}

      {query.data && query.data.content.length > 0 && (
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'baseline', justifyContent: 'space-between', columnGap: 12, rowGap: 4 }}>
          <Text style={{ color: colors.muted }}>查询结果 · 共 {query.data.totalElements} 条</Text>
          {query.data.totalPages > 1 && <Text style={{ color: colors.muted }}>第 {query.data.number + 1} / {query.data.totalPages} 页</Text>}
        </View>
      )}

      {query.data?.content.map((item) => (
        <Pressable
          key={item.id}
          accessibilityRole="button"
          onPress={() => openWorkOrder(item)}
          style={({ pressed }) => ({ opacity: pressed ? 0.78 : 1 })}
        >
          <WorkOrderCard workOrder={item} />
        </Pressable>
      ))}

      {query.data && query.data.content.length === 0 && !query.error && (
        <Card>
          <Text style={{ color: colors.muted }}>
            {debouncedSearchTerm ? '没有找到匹配工单，请尝试缩短关键词。' : '当前筛选条件下没有工单。'}
          </Text>
        </Card>
      )}

      {query.data && query.data.totalPages > 1 && (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <AppButton title="上一页" variant="secondary" disabled={query.data.first || query.isFetching} onPress={() => setPage((value) => Math.max(0, value - 1))} />
              <Text style={{ color: colors.muted }}>第 {query.data.number + 1} / {query.data.totalPages} 页</Text>
              <AppButton title="下一页" variant="secondary" disabled={query.data.last || query.isFetching} onPress={() => setPage((value) => value + 1)} />
          </View>
      )}
    </Screen>
  );
}

function resolveInitialReportTypes(
  value: string | string[] | undefined,
  availableTypes: typeof WORK_ORDER_TYPES,
): string[] {
  const candidates = Array.isArray(value) ? value : value ? [value] : [];
  const reportTypes: string[] = [];
  for (const candidate of candidates.flatMap((item) => item.split(','))) {
    const match = availableTypes.find((item) => item.reportType === candidate || item.slug === candidate);
    if (match) {
      reportTypes.push(match.reportType);
    }
  }
  return [...new Set(reportTypes)];
}

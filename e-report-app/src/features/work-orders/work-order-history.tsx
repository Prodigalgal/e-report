import { useQuery } from '@tanstack/react-query';
import { Text, View } from 'react-native';

import { formatApiErrorMessage } from '@/api/http';
import { fetchWorkOrderEvents, fetchWorkOrderResponses } from '@/api/work-order-api';
import { Card } from '@/ui/card';
import { SectionHeader } from '@/ui/page-header';
import { colors } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';
import { buildWorkOrderTimeline, type WorkOrderTimelineTone } from './work-order-timeline';

const toneColors: Record<WorkOrderTimelineTone, string> = {
  lifecycle: colors.primary,
  response: colors.warning,
  approved: colors.success,
  rejected: colors.danger,
};

export function WorkOrderHistory({ workOrderId, version }: { workOrderId: string; version: number }) {
  const eventsQuery = useQuery({
    queryKey: ['work-order-events', workOrderId, version],
    queryFn: () => fetchWorkOrderEvents(workOrderId, { page: 0, size: 200 }),
  });
  const responsesQuery = useQuery({
    queryKey: ['work-order-responses', workOrderId, version],
    queryFn: () => fetchWorkOrderResponses(workOrderId),
  });
  const error = eventsQuery.error || responsesQuery.error;
  const loading = eventsQuery.isLoading || responsesQuery.isLoading;
  const timeline = buildWorkOrderTimeline(eventsQuery.data?.content || [], responsesQuery.data || []);

  return (
    <Card style={{ gap: 12 }}>
      <SectionHeader
        title="处理与审计历史"
        subtitle={timeline.length ? `按发生时间从早到晚 · 共 ${timeline.length} 条` : undefined}
      />
      {!!error && <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(error, '加载工单历史失败。')}</Text>}
      {!loading && !error && timeline.length === 0 && <Text style={{ color: colors.muted }}>暂无处理或审计记录。</Text>}
      <View>
        {timeline.map((item, index) => {
          const last = index === timeline.length - 1;
          const toneColor = toneColors[item.tone];
          return (
            <View key={item.id} style={{ flexDirection: 'row', gap: 10 }}>
              <View style={{ width: 18, alignItems: 'center' }}>
                <View style={{ width: 11, height: 11, marginTop: 8, borderRadius: 6, backgroundColor: toneColor, zIndex: 1 }} />
                {!last && <View style={{ width: 2, flex: 1, minHeight: 58, backgroundColor: colors.border }} />}
              </View>
              <View
                style={{
                  flex: 1,
                  gap: 4,
                  marginBottom: last ? 0 : 12,
                  padding: 10,
                  borderWidth: 1,
                  borderLeftWidth: 3,
                  borderColor: colors.border,
                  borderLeftColor: toneColor,
                  borderRadius: 8,
                  backgroundColor: colors.surface,
                }}
              >
                <Text selectable style={{ color: colors.text, fontWeight: '800' }}>{item.title}</Text>
                <Text selectable style={{ color: colors.muted }}>{formatDateTime(item.occurredAt)} · 操作人：{item.actor}</Text>
                {!!item.meta && <Text selectable style={{ color: colors.text }}>{item.meta}</Text>}
                {!!item.detail && <Text selectable style={{ color: colors.text }}>{item.detail}</Text>}
              </View>
            </View>
          );
        })}
      </View>
    </Card>
  );
}

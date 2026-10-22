import { Text, View } from 'react-native';

import {
  priorityPresentation,
  reportTypePresentation,
  slaRiskPresentation,
} from '@/constants/work-order-presentation';
import type { WorkOrderListItem } from '@/types/api';
import { Card } from '@/ui/card';
import { PresentationBadge } from '@/ui/presentation-badge';
import { StatusBadge } from '@/ui/status-badge';
import { colors, uiLayout } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';
import { formatSlaSummary } from '@/utils/work-order-sla';

export function WorkOrderCard({ workOrder }: { workOrder: WorkOrderListItem }) {
  const typeTone = reportTypePresentation(workOrder.reportType);
  const priorityTone = priorityPresentation(workOrder.priority);
  const slaTone = slaRiskPresentation(workOrder.slaRiskLevel);
  const slaSummary = formatSlaSummary(workOrder);
  const handlerName = workOrder.handler?.name || workOrder.handler?.username || '暂未分配';
  return (
    <Card style={{ gap: 8 }}>
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
        <View style={{ flex: 1, minWidth: 0, gap: 2 }}>
          <Text style={{ color: typeTone.foreground, fontSize: 12, lineHeight: uiLayout.captionLineHeight, includeFontPadding: false, fontWeight: '500' }}>
            {workOrder.workOrderNo || `#${workOrder.id}`} · {workOrder.reportTypeName}
          </Text>
          <Text numberOfLines={2} style={{ color: colors.text, fontSize: 17, lineHeight: 24, includeFontPadding: false, fontWeight: '600' }}>
            {workOrder.location || '未填写地点'}
          </Text>
        </View>
        <StatusBadge status={workOrder.statusCode || workOrder.statusLabel || workOrder.status} phaseCode={workOrder.phaseCode} />
      </View>
      <Text numberOfLines={2} style={{ color: colors.muted, lineHeight: uiLayout.bodyLineHeight }}>{workOrder.reason || '-'}</Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6, minHeight: 28 }}>
        <PresentationBadge label={workOrder.priorityName || priorityTone.label} tone={priorityTone} />
        {slaSummary && <PresentationBadge label={slaSummary} tone={slaTone} />}
        <Text numberOfLines={1} style={{ flexShrink: 1, color: colors.muted, fontSize: 13, lineHeight: uiLayout.captionLineHeight, includeFontPadding: false }}>
          {workOrder.handlerStateName || '处理人员'}：{handlerName}
        </Text>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <Text style={{ flex: 1, minWidth: 0, flexShrink: 1, color: colors.muted, fontSize: 13, lineHeight: uiLayout.captionLineHeight, includeFontPadding: false }}>{formatDateTime(workOrder.reportTime)}</Text>
        <Text style={{ color: colors.muted, fontSize: 13, lineHeight: uiLayout.captionLineHeight, includeFontPadding: false }}>附件 {workOrder.attachmentCount || 0}</Text>
        <Text accessibilityLabel="查看详情" style={{ color: colors.primary, width: 24, height: 24, textAlign: 'center', fontSize: 22, fontWeight: '700', lineHeight: 24 }}>›</Text>
      </View>
    </Card>
  );
}

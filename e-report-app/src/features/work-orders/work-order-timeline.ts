import type { WorkOrderEvent, WorkOrderResponseHistory } from '@/types/api';
import { parseApiDateTime } from '../../utils/format.ts';

const RICH_RESPONSE_EVENT_TYPES = new Set([
  'RESPONSE_SUBMITTED',
  'VERIFICATION_APPROVED',
  'VERIFICATION_REJECTED',
]);

export type WorkOrderTimelineTone = 'lifecycle' | 'response' | 'approved' | 'rejected';

export type WorkOrderTimelineItem = {
  id: string;
  occurredAt: string;
  title: string;
  actor: string;
  detail: string;
  meta?: string;
  tone: WorkOrderTimelineTone;
  sortOrder: number;
};

function timestamp(value?: string): number {
  const parsed = parseApiDateTime(value);
  return parsed ? parsed.getTime() : Number.MAX_SAFE_INTEGER;
}

function personName(person: { name?: string; username?: string } | undefined, fallback: string): string {
  return person?.name || person?.username || fallback;
}

export function buildWorkOrderTimeline(
  events: WorkOrderEvent[] = [],
  responses: WorkOrderResponseHistory[] = [],
): WorkOrderTimelineItem[] {
  const lifecycleEntries: WorkOrderTimelineItem[] = events
    .filter((event) => !RICH_RESPONSE_EVENT_TYPES.has(event.eventType))
    .map((event) => ({
      id: `event-${event.id}`,
      occurredAt: event.occurredAt,
      title: event.eventTypeLabel || '未知事件',
      actor: event.operatorName || '系统',
      detail: event.reason || '',
      tone: 'lifecycle',
      sortOrder: 0,
    }));

  const responseEntries = responses.flatMap<WorkOrderTimelineItem>((response) => {
    const entries: WorkOrderTimelineItem[] = [{
      id: `response-${response.id}`,
      occurredAt: response.submittedAt,
      title: `第 ${response.attemptNo} 次回单`,
      actor: personName(response.submittedBy, '处理人员'),
      detail: response.resolutionDetails || '',
      meta: response.resolutionMethod ? `处理方式：${response.resolutionMethod}` : undefined,
      tone: 'response',
      sortOrder: 1,
    }];
    if (response.verifiedAt) {
      const rejected = response.verificationDecision === 'REJECTED';
      entries.push({
        id: `verification-${response.id}`,
        occurredAt: response.verifiedAt,
        title: `第 ${response.attemptNo} 次验收 · ${response.verificationDecisionLabel || (rejected ? '不通过' : '通过')}`,
        actor: personName(response.verifiedBy, '验收人员'),
        detail: response.verificationComment || '',
        tone: rejected ? 'rejected' : 'approved',
        sortOrder: 2,
      });
    }
    return entries;
  });

  return [...lifecycleEntries, ...responseEntries].sort((left, right) => (
    timestamp(left.occurredAt) - timestamp(right.occurredAt)
    || left.sortOrder - right.sortOrder
    || left.id.localeCompare(right.id)
  ));
}

import { parseApiDateTime } from '../../utils/formatDate.js';

const RICH_RESPONSE_EVENT_TYPES = new Set([
    'RESPONSE_SUBMITTED',
    'VERIFICATION_APPROVED',
    'VERIFICATION_REJECTED',
]);

const timestamp = value => {
    const parsed = parseApiDateTime(value);
    return parsed ? parsed.getTime() : Number.MAX_SAFE_INTEGER;
};

const personName = (person, fallback) => person?.name || person?.username || fallback;

export function buildWorkOrderTimeline(events = [], responses = []) {
    const lifecycleEntries = events
        .filter(event => !RICH_RESPONSE_EVENT_TYPES.has(event.eventType))
        .map(event => ({
            id: `event-${event.id}`,
            occurredAt: event.occurredAt,
            title: event.eventTypeLabel || '未知事件',
            actor: event.operatorName || '系统',
            detail: event.reason || '',
            tone: 'lifecycle',
            sortOrder: 0,
        }));

    const responseEntries = responses.flatMap(response => {
        const entries = [{
            id: `response-${response.id}`,
            occurredAt: response.submittedAt,
            title: `第 ${response.attemptNo} 次回单`,
            actor: personName(response.submittedBy, '处理人员'),
            detail: response.resolutionDetails || '',
            meta: response.resolutionMethod ? `处理方式：${response.resolutionMethod}` : '',
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

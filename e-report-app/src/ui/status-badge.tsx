import { statusDisplayLabel, statusPresentation } from '@/constants/work-order-presentation';
import { PresentationBadge } from '@/ui/presentation-badge';

const statusLabels: Record<string, string> = {
  SUBMITTED: '已提交',
  ASSIGNED: '已派单',
  IN_PROGRESS: '处理中',
  PENDING_VERIFICATION: '待验收',
  ON_HOLD: '已挂起',
  CLOSED: '已闭环',
  CANCELLED: '已取消',
  PENDING: '待处理',
  RESOLVED: '已解决',
};

export function StatusBadge({ status, phaseCode }: { status: string; phaseCode?: string }) {
  const statusInput = { statusCode: status, statusLabel: statusLabels[status] || status, phaseCode };
  const tone = statusPresentation(statusInput);
  return <PresentationBadge label={statusDisplayLabel(statusInput)} tone={tone} />;
}

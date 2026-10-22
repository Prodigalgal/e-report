import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';

import {
  decideWorkOrderTransfer,
  fetchWorkOrderTransfers,
  fetchWorkOrderTransferTargets,
  requestWorkOrderTransfer,
} from '@/api/work-order-api';
import { formatApiErrorMessage } from '@/api/http';
import type { WorkOrder, WorkOrderTransfer } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { EditModal } from '@/ui/edit-modal';
import { SectionHeader } from '@/ui/page-header';
import { SelectField } from '@/ui/select-field';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';
import { formatDateTime } from '@/utils/format';

type DialogState = { type: 'create' } | { type: 'accept' | 'reject' | 'cancel'; transfer: WorkOrderTransfer } | null;

const STATUS_LABELS: Record<WorkOrderTransfer['status'], string> = {
  PENDING: '待目标组织确认', ACCEPTED: '已接受', REJECTED: '已拒绝', CANCELLED: '已撤销',
};

export function WorkOrderOrganizationTransfer({ workOrder, onChanged }: { workOrder: WorkOrder; onChanged: () => Promise<unknown> }) {
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState<DialogState>(null);
  const [targetOrgUnitId, setTargetOrgUnitId] = useState('');
  const [comment, setComment] = useState('');
  const transfersQuery = useQuery({
    queryKey: ['work-order-transfers', workOrder.id],
    queryFn: () => fetchWorkOrderTransfers(workOrder.id),
  });
  const targetsQuery = useQuery({
    queryKey: ['work-order-transfer-targets'],
    queryFn: fetchWorkOrderTransferTargets,
    enabled: dialog?.type === 'create',
    staleTime: 5 * 60 * 1000,
  });
  const pending = useMemo(() => transfersQuery.data?.find((transfer) => transfer.status === 'PENDING'), [transfersQuery.data]);
  const canCreate = workOrder.allowedActions.includes('TRANSFER_CREATE') && !pending;

  const mutation = useMutation({
    mutationFn: async () => {
      if (!dialog || !comment.trim()) throw new Error('必须填写原因或处理意见。');
      if (dialog.type === 'create') {
        if (!targetOrgUnitId) throw new Error('请选择目标组织。');
        return requestWorkOrderTransfer(workOrder.id, targetOrgUnitId, workOrder.version, comment.trim());
      }
      return decideWorkOrderTransfer(workOrder.id, dialog.transfer.id, dialog.type, workOrder.version, comment.trim());
    },
    onSuccess: async () => {
      setDialog(null);
      setComment('');
      await Promise.all([
        onChanged(),
        queryClient.invalidateQueries({ queryKey: ['work-order-transfers', workOrder.id] }),
        queryClient.invalidateQueries({ queryKey: ['work-orders'] }),
      ]);
    },
  });

  const openDialog = (nextDialog: Exclude<DialogState, null>) => {
    mutation.reset();
    setComment('');
    setTargetOrgUnitId('');
    setDialog(nextDialog);
  };

  return (
    <>
      <Card style={{ gap: 10 }}>
        <SectionHeader title="组织移交" subtitle="完整移交后原组织保留查看权" />
        {canCreate && <AppButton title="发起完整移交" variant="secondary" onPress={() => openDialog({ type: 'create' })} />}
        {transfersQuery.isLoading && <ActivityIndicator color={colors.primary} />}
        {!!transfersQuery.error && <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(transfersQuery.error, '加载移交记录失败。')}</Text>}
        {transfersQuery.data?.length === 0 && <Text style={{ color: colors.muted }}>暂无组织移交记录。</Text>}
        {transfersQuery.data?.map((transfer) => (
          <View key={transfer.id} style={{ gap: 7, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 10 }}>
            <Text style={{ color: colors.text, fontWeight: '900' }}>{transfer.sourceOrgUnitName} → {transfer.targetOrgUnitName}</Text>
            <Text style={{ color: transfer.status === 'REJECTED' ? colors.danger : colors.muted }}>{STATUS_LABELS[transfer.status]}</Text>
            <Text style={{ color: colors.text }}>申请原因：{transfer.requestReason}</Text>
            <Text style={{ color: colors.muted, fontSize: 12 }}>申请时间：{formatDateTime(transfer.requestedAt)}</Text>
            {!!transfer.decisionComment && <Text style={{ color: colors.text }}>处理意见：{transfer.decisionComment}</Text>}
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {transfer.allowedActions.includes('ACCEPT') && <AppButton title="接受移交" onPress={() => openDialog({ type: 'accept', transfer })} />}
              {transfer.allowedActions.includes('REJECT') && <AppButton title="拒绝移交" variant="secondary" onPress={() => openDialog({ type: 'reject', transfer })} />}
              {transfer.allowedActions.includes('CANCEL') && <AppButton title="撤销申请" variant="secondary" onPress={() => openDialog({ type: 'cancel', transfer })} />}
            </View>
          </View>
        ))}
      </Card>

      <EditModal
        visible={Boolean(dialog)}
        title={dialog?.type === 'create' ? '发起工单完整移交' : dialog?.type === 'accept' ? '接受完整移交' : dialog?.type === 'reject' ? '拒绝完整移交' : '撤销移交申请'}
        primaryActionLabel="确认提交"
        onPrimaryAction={() => mutation.mutate()}
        onClose={() => setDialog(null)}
        working={mutation.isPending}
        primaryDisabled={!comment.trim() || (dialog?.type === 'create' && !targetOrgUnitId)}
      >
        {!!mutation.error && <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(mutation.error, '组织移交操作失败，请刷新工单后重试。')}</Text>}
        {dialog?.type === 'create' ? (
          <>
            <Text style={{ color: colors.warning }}>目标组织接受后，当前处理责任会撤销，工单回到目标组织待领取状态。</Text>
            {targetsQuery.isLoading ? <ActivityIndicator color={colors.primary} /> : (
              <SelectField
                label="目标组织"
                value={targetOrgUnitId}
                options={(targetsQuery.data ?? []).filter((target) => target.id !== workOrder.ownerOrgId)
                  .map((target) => ({ value: target.id, label: `${target.name}（${target.code}）` }))}
                onChange={setTargetOrgUnitId}
              />
            )}
          </>
        ) : <Text style={{ color: colors.muted }}>{dialog?.transfer.sourceOrgUnitName} → {dialog?.transfer.targetOrgUnitName}</Text>}
        <TextField
          label={dialog?.type === 'create' ? '移交原因' : '处理意见'}
          value={comment}
          onChangeText={setComment}
          multiline
          maxLength={2000}
          required
        />
      </EditModal>
    </>
  );
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';

import { formatApiErrorMessage } from '@/api/http';
import {
  acceptAssignment,
  assignWorkOrder,
  cancelWorkOrder,
  claimWorkOrder,
  fetchAssignmentCandidates,
  holdWorkOrder,
  reassignWorkOrder,
  rejectAssignment,
  resumeWorkOrder,
  verifyWorkOrder,
} from '@/api/work-order-api';
import { hasAllowedAction } from '@/constants/work-orders';
import type { AssignmentCandidate, WorkOrder } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { SectionHeader } from '@/ui/page-header';
import { SearchField } from '@/ui/search-field';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';

type ReasonMode = 'REJECT_ASSIGNMENT' | 'HOLD' | 'CANCEL' | 'WITHDRAW' | null;

export function WorkOrderLifecycleActions({ workOrder, onRespond }: { workOrder: WorkOrder; onRespond: () => void }) {
  const queryClient = useQueryClient();
  const [reasonMode, setReasonMode] = useState<ReasonMode>(null);
  const [reason, setReason] = useState('');
  const [dispatchMode, setDispatchMode] = useState<'ASSIGN' | 'REASSIGN' | null>(null);
  const [candidate, setCandidate] = useState<AssignmentCandidate | null>(null);
  const [candidateSearchTerm, setCandidateSearchTerm] = useState('');
  const [candidateQuery, setCandidateQuery] = useState('');
  const [dispatchReason, setDispatchReason] = useState('');
  const [verificationComment, setVerificationComment] = useState('');

  const candidatesQuery = useQuery({
    queryKey: ['assignment-candidates', workOrder.id, workOrder.version, candidateQuery],
    queryFn: () => fetchAssignmentCandidates(workOrder.id, candidateQuery),
    enabled: dispatchMode != null && candidateQuery.length > 0,
  });

  const mutation = useMutation({
    mutationFn: async ({ action }: { action: string }) => {
      if (action === 'CLAIM') return claimWorkOrder(workOrder.id, workOrder.version);
      if (action === 'ACCEPT_ASSIGNMENT') return acceptAssignment(workOrder.id, workOrder.pendingAssignment!.id, workOrder.version);
      if (action === 'RESUME') return resumeWorkOrder(workOrder.id, workOrder.version);
      if (action === 'REJECT_ASSIGNMENT') return rejectAssignment(workOrder.id, workOrder.pendingAssignment!.id, workOrder.version, reason.trim());
      if (action === 'HOLD') return holdWorkOrder(workOrder.id, workOrder.version, reason.trim());
      if (action === 'CANCEL' || action === 'WITHDRAW') return cancelWorkOrder(workOrder.id, workOrder.version, reason.trim());
      if (action === 'ASSIGN' || action === 'REASSIGN') {
        if (!candidate) throw new Error('请选择目标处理人员。');
        const command = {
          assigneeMembershipId: candidate.membershipId,
          responsibleOrgId: candidate.orgUnitId,
          reason: dispatchReason.trim(),
          expectedVersion: workOrder.version,
        };
        return action === 'ASSIGN' ? assignWorkOrder(workOrder.id, command) : reassignWorkOrder(workOrder.id, command);
      }
      if (action === 'VERIFY_APPROVE' || action === 'VERIFY_REJECT') {
        if (!workOrder.currentResponseId) throw new Error('当前回单不存在，请刷新后重试。');
        const normalizedComment = verificationComment.trim();
        return verifyWorkOrder(workOrder.id, {
          responseId: workOrder.currentResponseId,
          decision: action === 'VERIFY_APPROVE' ? 'APPROVED' : 'REJECTED',
          comment: normalizedComment,
          expectedVersion: workOrder.version,
        });
      }
      throw new Error('不支持的工单操作。');
    },
    onSuccess: async (updatedWorkOrder, variables) => {
      queryClient.setQueryData(['work-order', workOrder.id], updatedWorkOrder);
      setReasonMode(null);
      setReason('');
      setDispatchMode(null);
      setCandidate(null);
      setCandidateSearchTerm('');
      setCandidateQuery('');
      setDispatchReason('');
      setVerificationComment('');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['work-order-search'] }),
        queryClient.invalidateQueries({ queryKey: ['work-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-work-order-statistics'] }),
      ]);
      Alert.alert('操作成功', actionSuccessMessage(variables.action));
    },
  });

  const runReasonAction = () => {
    if (reasonMode && reason.trim()) mutation.mutate({ action: reasonMode });
  };

  const hasAnyAction = (workOrder.allowedActions || []).length > 0;
  return (
    <Card style={{ gap: 10 }}>
      <SectionHeader
        title="工单操作"
        subtitle={workOrder.currentAssignment
          ? `当前处理人：${workOrder.currentAssignment.name || workOrder.currentAssignment.username}`
          : '当前无责任处理人'}
      />

      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {hasAllowedAction(workOrder, 'CLAIM') && <View style={{ width: '100%' }}><AppButton title="主动接单" onPress={() => mutation.mutate({ action: 'CLAIM' })} disabled={mutation.isPending} /></View>}
        {hasAllowedAction(workOrder, 'ACCEPT_ASSIGNMENT') && <View style={{ width: '100%' }}><AppButton title="接受派单" onPress={() => mutation.mutate({ action: 'ACCEPT_ASSIGNMENT' })} disabled={mutation.isPending} /></View>}
        {hasAllowedAction(workOrder, 'REJECT_ASSIGNMENT') && <View style={{ flexBasis: 144, flexGrow: 1 }}><AppButton title="拒绝派单" variant="secondary" onPress={() => { setReason(''); setReasonMode('REJECT_ASSIGNMENT'); }} /></View>}
        {hasAllowedAction(workOrder, 'ASSIGN') && <View style={{ flexBasis: 144, flexGrow: 1 }}><AppButton title="派单" variant="secondary" onPress={() => { setDispatchMode('ASSIGN'); setCandidate(null); setCandidateSearchTerm(''); setCandidateQuery(''); setDispatchReason(''); }} /></View>}
        {hasAllowedAction(workOrder, 'REASSIGN') && <View style={{ flexBasis: 144, flexGrow: 1 }}><AppButton title="调单" variant="secondary" onPress={() => { setDispatchMode('REASSIGN'); setCandidate(null); setCandidateSearchTerm(''); setCandidateQuery(''); setDispatchReason(''); }} /></View>}
        {hasAllowedAction(workOrder, 'RESPOND') && <View style={{ width: '100%' }}><AppButton title="提交回单" onPress={onRespond} /></View>}
        {hasAllowedAction(workOrder, 'HOLD') && <View style={{ flexBasis: 144, flexGrow: 1 }}><AppButton title="挂起工单" variant="secondary" onPress={() => { setReason(''); setReasonMode('HOLD'); }} /></View>}
        {hasAllowedAction(workOrder, 'RESUME') && <View style={{ width: '100%' }}><AppButton title="恢复处理" variant="primary" onPress={() => mutation.mutate({ action: 'RESUME' })} disabled={mutation.isPending} /></View>}
        {hasAllowedAction(workOrder, 'CANCEL') && <View style={{ flexBasis: 144, flexGrow: 1 }}><AppButton title="取消工单" variant="danger" onPress={() => { setReason(''); setReasonMode('CANCEL'); }} /></View>}
        {hasAllowedAction(workOrder, 'WITHDRAW') && <View style={{ flexBasis: 144, flexGrow: 1 }}><AppButton title="撤回工单" variant="danger" onPress={() => { setReason(''); setReasonMode('WITHDRAW'); }} /></View>}
      </View>

      {reasonMode && (
        <View style={{ gap: 8, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 10 }}>
          <TextField label={reasonLabel(reasonMode)} value={reason} onChangeText={setReason} multiline required />
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <View style={{ flex: 1 }}><AppButton title="取消" variant="ghost" onPress={() => setReasonMode(null)} /></View>
            <View style={{ flex: 1 }}><AppButton title="确认" onPress={runReasonAction} disabled={!reason.trim() || mutation.isPending} /></View>
          </View>
        </View>
      )}

      {dispatchMode && (
        <View style={{ gap: 10, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 10 }}>
          <SectionHeader title={dispatchMode === 'ASSIGN' ? '选择处理人员' : '选择新处理人员'} />
          <SearchField
            label="搜索处理人员"
            value={candidateSearchTerm}
            onChangeText={(value) => {
              setCandidateSearchTerm(value);
              setCandidate(null);
            }}
            placeholder="输入姓名、拼音、首字母或账号"
            isSearching={candidatesQuery.isFetching}
            resultCount={candidateSearchTerm.trim() === candidateQuery ? candidatesQuery.data?.length : undefined}
          />
          <AppButton
            title="查询处理人员"
            variant="secondary"
            onPress={() => setCandidateQuery(candidateSearchTerm.trim())}
            disabled={!candidateSearchTerm.trim() || candidatesQuery.isFetching}
          />
          {candidatesQuery.isLoading && <Text style={{ color: colors.muted }}>正在加载合格处理人员...</Text>}
          {!!candidatesQuery.error && <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(candidatesQuery.error, '加载处理人员失败。')}</Text>}
          {!candidateQuery && <Text style={{ color: colors.muted }}>请输入姓名、拼音、首字母或账号，查询后再选择处理人员。</Text>}
          {candidateQuery && !candidatesQuery.isFetching && candidatesQuery.data?.length === 0 && (
            <Text style={{ color: colors.muted }}>没有找到符合条件的处理人员。</Text>
          )}
          {(candidateSearchTerm.trim() === candidateQuery ? candidatesQuery.data || [] : []).map((item) => {
            const selected = candidate?.membershipId === item.membershipId;
            return (
              <Pressable
                key={item.membershipId}
                onPress={() => setCandidate(item)}
                style={{ borderWidth: 1, borderColor: selected ? colors.primary : colors.controlBorder, borderRadius: 8, padding: 10, backgroundColor: selected ? colors.controlSelectedSurface : colors.controlSurface }}
              >
                <Text style={{ color: colors.text, fontWeight: '800' }}>{item.name || item.username}</Text>
                <Text style={{ color: colors.muted }}>{item.orgName} · 在办 {item.activeWorkOrderCount}</Text>
              </Pressable>
            );
          })}
          <TextField label="派调单原因" value={dispatchReason} onChangeText={setDispatchReason} multiline required />
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <View style={{ flex: 1 }}><AppButton title="取消" variant="ghost" onPress={() => { setDispatchMode(null); setCandidateSearchTerm(''); setCandidateQuery(''); }} /></View>
            <View style={{ flex: 1 }}><AppButton title="确认" onPress={() => mutation.mutate({ action: dispatchMode })} disabled={!candidate || !dispatchReason.trim() || mutation.isPending} /></View>
          </View>
        </View>
      )}

      {(hasAllowedAction(workOrder, 'VERIFY_APPROVE') || hasAllowedAction(workOrder, 'VERIFY_REJECT')) && (
        <View style={{ gap: 8, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 10 }}>
          <SectionHeader title="验收回单" />
          <TextField
            label="验收/整改意见"
            value={verificationComment}
            onChangeText={setVerificationComment}
            multiline
            required
          />
          {hasAllowedAction(workOrder, 'VERIFY_APPROVE') && <AppButton title="验收通过" onPress={() => mutation.mutate({ action: 'VERIFY_APPROVE' })} disabled={!verificationComment.trim() || mutation.isPending} />}
          {hasAllowedAction(workOrder, 'VERIFY_REJECT') && <AppButton title="驳回返工" variant="secondary" onPress={() => mutation.mutate({ action: 'VERIFY_REJECT' })} disabled={!verificationComment.trim() || mutation.isPending} />}
        </View>
      )}

      {!hasAnyAction && <Text style={{ color: colors.muted }}>当前账号在此状态下没有可执行操作。</Text>}
      {!!mutation.error && <Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(mutation.error, '工单状态更新失败，请刷新后重试。')}</Text>}
    </Card>
  );
}

function reasonLabel(mode: Exclude<ReasonMode, null>) {
  if (mode === 'REJECT_ASSIGNMENT') return '拒绝原因';
  if (mode === 'HOLD') return '挂起原因';
  if (mode === 'WITHDRAW') return '撤回原因';
  return '取消原因';
}

function actionSuccessMessage(action: string) {
  const messages: Record<string, string> = {
    CLAIM: '接单成功，当前工单由你负责。',
    ACCEPT_ASSIGNMENT: '派单已接受。',
    REJECT_ASSIGNMENT: '派单已拒绝。',
    ASSIGN: '工单已派发，等待目标人员接收。',
    REASSIGN: '调单成功，新处理人员已立即接管工单。',
    HOLD: '工单已挂起。',
    RESUME: '工单已恢复处理。',
    CANCEL: '工单已取消。',
    WITHDRAW: '工单已撤回。',
    VERIFY_APPROVE: '验收通过，工单已闭环。',
    VERIFY_REJECT: '回单已驳回，工单进入返工处理。',
  };
  return messages[action] || '工单已更新。';
}

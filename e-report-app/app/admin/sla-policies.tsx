import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Switch, Text, View } from 'react-native';
import { useState } from 'react';

import { formatApiErrorMessage } from '@/api/http';
import { fetchWorkOrderSlaPolicies, updateWorkOrderSlaPolicy } from '@/api/work-order-api';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS } from '@/constants/work-orders';
import type { WorkOrderSlaPolicy, WorkOrderSlaPolicyUpdate } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { PageHeader } from '@/ui/page-header';
import { SectionTabs, SectionContent } from '@/ui/section-tabs';
import { SelectField } from '@/ui/select-field';
import { Screen } from '@/ui/screen';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';

export default function AdminSlaPoliciesScreen() {
  const { hasPermission } = useAuth();
  const canUpdate = hasPermission(ADMIN_PERMISSIONS.SLA_POLICY_UPDATE);
  const [selectedType, setSelectedType] = useState('');
  const [selectedPriority, setSelectedPriority] = useState('ROUTINE');
  const query = useQuery({
    queryKey: ['work-order-sla-policies'],
    queryFn: fetchWorkOrderSlaPolicies,
  });

  const groups = new Map<string, WorkOrderSlaPolicy[]>();
  (query.data || []).forEach((policy) => {
    groups.set(policy.reportType, [...(groups.get(policy.reportType) || []), policy]);
  });

  const activeType = groups.has(selectedType) ? selectedType : [...groups.keys()][0] || '';

  return (
    <Screen>
      <PageHeader title="故障时限策略" subtitle="维护接单、派单接收、处理、验收、整改和挂起时限" />
      {!!query.error && (
        <Card><Text selectable style={{ color: colors.danger }}>{formatApiErrorMessage(query.error, '加载故障时限策略失败。')}</Text></Card>
      )}
      {!!groups.size && <SectionTabs label="工单类型" value={activeType} onChange={setSelectedType}
        options={[...groups.entries()].map(([value, policies]) => ({ value, label: policies[0]?.reportTypeName || value }))} />}
      {[...groups.entries()].map(([reportType, policies]) => (
        <SectionContent key={reportType} value={reportType} selected={activeType}>
          <SelectField label="优先级"
            value={policies.some(policy => policy.priority === selectedPriority) ? selectedPriority : policies[0]?.priority || ''}
            options={policies.map(policy => ({ value: policy.priority, label: policy.priorityName }))} onChange={setSelectedPriority} />
          {policies.map(policy => (
            <SectionContent key={policy.id} value={policy.priority}
              selected={policies.some(row => row.priority === selectedPriority) ? selectedPriority : policies[0]?.priority || ''}>
              <PolicyEditor policy={policy} canUpdate={canUpdate} />
            </SectionContent>
          ))}
        </SectionContent>
      ))}
      {query.isLoading && <Text style={{ color: colors.muted }}>正在加载故障时限策略...</Text>}
    </Screen>
  );
}

function PolicyEditor({ policy, canUpdate }: { policy: WorkOrderSlaPolicy; canUpdate: boolean }) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(() => toDraft(policy));
  const mutation = useMutation({
    mutationFn: (payload: WorkOrderSlaPolicyUpdate) => updateWorkOrderSlaPolicy(policy.id, payload),
    onSuccess: async (saved) => {
      setDraft(toDraft(saved));
      await queryClient.invalidateQueries({ queryKey: ['work-order-sla-policies'] });
      Alert.alert('保存成功', `${saved.reportTypeName} / ${saved.priorityName} 已更新。`);
    },
    onError: (error) => Alert.alert('保存失败', formatApiErrorMessage(error, '保存故障时限策略失败。')),
  });

  const save = () => {
    const payload = parseDraft(draft);
    if (!payload) {
      Alert.alert('输入有误', '阶段时限必须是正整数，暂停上限必须是非负整数，且单次上限不能超过累计上限。');
      return;
    }
    mutation.mutate(payload);
  };

  return (
    <Card style={{ gap: 10 }}>
      <Text style={{ color: colors.text, fontSize: 18, fontWeight: '900' }}>{policy.priorityName}</Text>
      <TextField label="接单时限（分钟）" value={draft.claimMinutes} onChangeText={(value) => setDraft((current) => ({ ...current, claimMinutes: value }))} keyboardType="number-pad" editable={canUpdate} />
      <TextField label="派单接收时限（分钟）" value={draft.assignmentAcceptanceMinutes} onChangeText={(value) => setDraft((current) => ({ ...current, assignmentAcceptanceMinutes: value }))} keyboardType="number-pad" editable={canUpdate} />
      <TextField label="处理时限（分钟）" value={draft.responseMinutes} onChangeText={(value) => setDraft((current) => ({ ...current, responseMinutes: value }))} keyboardType="number-pad" editable={canUpdate} />
      <TextField label="验收时限（分钟）" value={draft.verificationMinutes} onChangeText={(value) => setDraft((current) => ({ ...current, verificationMinutes: value }))} keyboardType="number-pad" editable={canUpdate} />
      <TextField label="整改时限（分钟）" value={draft.reworkMinutes} onChangeText={(value) => setDraft((current) => ({ ...current, reworkMinutes: value }))} keyboardType="number-pad" editable={canUpdate} />
      <TextField label="单次挂起时限（分钟）" value={draft.maxSingleHoldMinutes} onChangeText={(value) => setDraft((current) => ({ ...current, maxSingleHoldMinutes: value }))} keyboardType="number-pad" editable={canUpdate} />
      <TextField label="累计挂起时限（分钟）" value={draft.maxTotalHoldMinutes} onChangeText={(value) => setDraft((current) => ({ ...current, maxTotalHoldMinutes: value }))} keyboardType="number-pad" editable={canUpdate} />
      <View style={{ minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
        <Text style={{ flex: 1, color: colors.text, fontWeight: '700' }}>挂起时暂停计时</Text>
        <Switch
          accessibilityLabel="挂起时暂停计时"
          value={draft.pauseOnHold}
          onValueChange={(value) => setDraft((current) => ({ ...current, pauseOnHold: value }))}
          disabled={!canUpdate}
        />
      </View>
      {canUpdate && <AppButton title={mutation.isPending ? '正在保存' : '保存策略'} onPress={save} disabled={mutation.isPending} loading={mutation.isPending} />}
    </Card>
  );
}

type PolicyDraft = Record<'claimMinutes' | 'assignmentAcceptanceMinutes' | 'responseMinutes' | 'verificationMinutes' | 'reworkMinutes' | 'maxSingleHoldMinutes' | 'maxTotalHoldMinutes', string> & {
  pauseOnHold: boolean;
};

function toDraft(policy: WorkOrderSlaPolicy): PolicyDraft {
  return {
    claimMinutes: String(policy.claimMinutes),
    assignmentAcceptanceMinutes: String(policy.assignmentAcceptanceMinutes),
    responseMinutes: String(policy.responseMinutes),
    verificationMinutes: String(policy.verificationMinutes),
    reworkMinutes: String(policy.reworkMinutes),
    maxSingleHoldMinutes: String(policy.maxSingleHoldMinutes),
    maxTotalHoldMinutes: String(policy.maxTotalHoldMinutes),
    pauseOnHold: policy.pauseOnHold,
  };
}

function parseDraft(draft: PolicyDraft): WorkOrderSlaPolicyUpdate | null {
  const claimMinutes = Number(draft.claimMinutes);
  const assignmentAcceptanceMinutes = Number(draft.assignmentAcceptanceMinutes);
  const responseMinutes = Number(draft.responseMinutes);
  const verificationMinutes = Number(draft.verificationMinutes);
  const reworkMinutes = Number(draft.reworkMinutes);
  const maxSingleHoldMinutes = Number(draft.maxSingleHoldMinutes);
  const maxTotalHoldMinutes = Number(draft.maxTotalHoldMinutes);
  const positive = [claimMinutes, assignmentAcceptanceMinutes, responseMinutes, verificationMinutes, reworkMinutes]
    .every((value) => Number.isInteger(value) && value > 0);
  const holdLimitsValid = [maxSingleHoldMinutes, maxTotalHoldMinutes].every((value) => Number.isInteger(value) && value >= 0)
    && maxSingleHoldMinutes <= maxTotalHoldMinutes;
  if (!positive || !holdLimitsValid) return null;
  return { claimMinutes, assignmentAcceptanceMinutes, responseMinutes, verificationMinutes, reworkMinutes, pauseOnHold: draft.pauseOnHold, maxSingleHoldMinutes, maxTotalHoldMinutes };
}

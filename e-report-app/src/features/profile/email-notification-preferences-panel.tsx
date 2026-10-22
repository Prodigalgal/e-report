import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, Switch, Text, View } from 'react-native';

import {
  emailNotificationOptions,
  fetchAdminEmailNotificationPreferences,
  fetchEmailNotificationPreferences,
  updateAdminEmailNotificationPreferences,
  updateEmailNotificationPreferences,
  type EmailNotificationPreferenceKey,
  type EmailNotificationPreferences,
} from '@/api/email-notification-api';
import { formatApiErrorMessage } from '@/api/http';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { EditModal } from '@/ui/edit-modal';
import { FormErrorMessage } from '@/ui/form-error-message';
import { SectionHeader } from '@/ui/page-header';
import { colors, uiLayout } from '@/ui/theme';

export function EmailNotificationPreferencesPanel({ ownerKey, targetUserId, targetName, onClose }: {
  ownerKey: string;
  targetUserId?: string;
  targetName?: string;
  onClose?: () => void;
}) {
  const queryClient = useQueryClient();
  const queryKey = targetUserId === undefined
    ? ['email-notification-preferences', ownerKey]
    : ['email-notification-preferences', ownerKey, 'admin', targetUserId];
  const query = useQuery({
    queryKey,
    queryFn: () => targetUserId === undefined
      ? fetchEmailNotificationPreferences()
      : fetchAdminEmailNotificationPreferences(targetUserId),
  });
  const [draft, setDraft] = useState<EmailNotificationPreferences | null>(null);
  const [saved, setSaved] = useState(false);
  const mutation = useMutation({
    mutationFn: (preferences: EmailNotificationPreferences) => targetUserId === undefined
      ? updateEmailNotificationPreferences(preferences)
      : updateAdminEmailNotificationPreferences(targetUserId, preferences),
    onSuccess: preferences => {
      queryClient.setQueryData(queryKey, preferences);
      setDraft(null);
      setSaved(true);
      if (targetUserId !== undefined) {
        void queryClient.invalidateQueries({ queryKey: ['email-notification-preferences', targetUserId], exact: true });
      }
    },
  });
  const preferences = draft ?? query.data;
  const keys = Object.keys(emailNotificationOptions) as EmailNotificationPreferenceKey[];
  const changed = preferences && query.data && keys.some(key => preferences[key] !== query.data[key]);

  const subtitle = targetUserId === undefined
    ? '仅影响本人收信，实际通知仍受组织设置、收件权限及可用邮箱限制。'
    : `正在编辑 ${targetName || '该用户'} 的邮件订阅，本人仍可自行修改。实际通知仍受组织设置、收件权限及可用邮箱限制。`;
  const save = () => {
    if (preferences && changed && !mutation.isPending) mutation.mutate(preferences);
  };
  const content = (
    <>
      {query.isLoading && <ActivityIndicator color={colors.primary} accessibilityLabel="加载邮件订阅" />}
      {!!query.error && <FormErrorMessage>{formatApiErrorMessage(query.error, '邮件订阅加载失败。')}</FormErrorMessage>}
      {!!mutation.error && <FormErrorMessage>{formatApiErrorMessage(mutation.error, '邮件订阅保存失败，请重试。')}</FormErrorMessage>}
      {saved && <Text accessibilityLiveRegion="polite" style={{ color: colors.success }}>邮件订阅已保存。</Text>}
      {!query.isLoading && !query.data && <AppButton title="重新加载" onPress={() => { void query.refetch(); }} variant="secondary" />}
      {preferences && keys.map(key => (
        <View key={key} style={{ flexDirection: 'row', alignItems: 'center', gap: uiLayout.fieldGap, minHeight: uiLayout.controlHeight }}>
          <View style={{ flex: 1, minWidth: 0, gap: 4 }}>
            <Text style={{ color: colors.text, fontWeight: '700', lineHeight: uiLayout.bodyLineHeight, includeFontPadding: false }}>
              {emailNotificationOptions[key].title}
            </Text>
            <Text style={{ color: colors.muted, fontSize: 13, lineHeight: uiLayout.captionLineHeight, includeFontPadding: false }}>
              {emailNotificationOptions[key].description}
            </Text>
          </View>
          <Switch
            accessibilityLabel={emailNotificationOptions[key].title}
            value={preferences[key]}
            disabled={mutation.isPending}
            trackColor={{ false: colors.border, true: colors.primary }}
            thumbColor={colors.surface}
            onValueChange={value => {
              setDraft({ ...preferences, [key]: value });
              setSaved(false);
              mutation.reset();
            }}
          />
        </View>
      ))}
    </>
  );

  if (onClose) {
    return <EditModal
      visible title="邮件通知偏好" primaryActionLabel="保存通知偏好"
      onPrimaryAction={save} onClose={onClose} secondaryActionLabel="关闭"
      working={mutation.isPending} primaryDisabled={!changed || query.isLoading}
    >
      <Text style={{ color: colors.muted, lineHeight: uiLayout.bodyLineHeight }}>{subtitle}</Text>
      {content}
    </EditModal>;
  }

  return (
    <Card>
      <SectionHeader title="邮件通知偏好" subtitle={subtitle} />
      {content}
      {preferences && <AppButton
        title={mutation.isPending ? '保存中' : '保存通知偏好'}
        disabled={!changed || mutation.isPending}
        loading={mutation.isPending}
        onPress={save}
      />}
    </Card>
  );
}

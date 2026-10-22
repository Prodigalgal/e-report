import { KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, Text, View } from 'react-native';

import { AppButton } from '@/ui/app-button';
import { colors, uiLayout } from '@/ui/theme';

export function EditModal({
  visible,
  title,
  children,
  primaryActionLabel,
  onPrimaryAction,
  onClose,
  working = false,
  primaryDisabled = false,
  secondaryActionLabel = '取消',
}: {
  visible: boolean;
  title: string;
  children: React.ReactNode;
  primaryActionLabel: string;
  onPrimaryAction: () => void;
  onClose: () => void;
  working?: boolean;
  primaryDisabled?: boolean;
  secondaryActionLabel?: string;
}) {
  const close = () => {
    if (!working) onClose();
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={close} statusBarTranslucent>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={{ flex: 1 }}
      >
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="关闭编辑窗口"
          onPress={close}
          style={{
            flex: 1,
            justifyContent: 'center',
            padding: uiLayout.pageGutter,
            backgroundColor: 'rgba(15, 23, 42, 0.48)',
          }}
        >
          <Pressable
            accessibilityRole="none"
            onPress={(event) => event.stopPropagation()}
            style={{
              width: '100%',
              maxWidth: uiLayout.dialogMaxWidth,
              maxHeight: '90%',
              alignSelf: 'center',
              borderRadius: uiLayout.cornerRadius,
              borderWidth: 1,
              borderColor: colors.border,
              backgroundColor: colors.surface,
              overflow: 'hidden',
            }}
          >
            <View style={{ padding: uiLayout.cardPadding, borderBottomWidth: 1, borderBottomColor: colors.border }}>
              <Text style={{ color: colors.text, fontSize: 20, lineHeight: 28, includeFontPadding: false, fontWeight: '900' }}>{title}</Text>
            </View>
            <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: uiLayout.cardPadding, gap: uiLayout.fieldGap }}>
              {children}
            </ScrollView>
            <View style={{ padding: uiLayout.cardPadding, gap: uiLayout.fieldGap, borderTopWidth: 1, borderTopColor: colors.border }}>
              <AppButton
                title={working ? '处理中' : primaryActionLabel}
                onPress={onPrimaryAction}
                disabled={working || primaryDisabled}
                loading={working}
              />
              <AppButton title={secondaryActionLabel} onPress={close} disabled={working} variant="secondary" />
            </View>
          </Pressable>
        </Pressable>
      </KeyboardAvoidingView>
    </Modal>
  );
}

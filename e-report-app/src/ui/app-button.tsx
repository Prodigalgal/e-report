import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { actionColors, uiLayout } from '@/ui/theme';

type AppButtonProps = {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
};

export function AppButton({ title, onPress, disabled, loading = false, variant = 'primary' }: AppButtonProps) {
  const blocked = Boolean(disabled || loading);
  const tone = variant === 'danger' ? actionColors.danger : variant === 'ghost' ? actionColors.neutral : actionColors.primary;
  const backgroundColor = variant === 'ghost' ? 'transparent' : blocked ? actionColors.disabled.surface : variant === 'primary' ? tone.solid : tone.surface;
  const textColor = blocked ? actionColors.disabled.foreground : variant === 'primary' ? '#ffffff' : tone.foreground;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ busy: loading, disabled: Boolean(disabled || loading) }}
      disabled={disabled || loading}
      onPress={onPress}
      hitSlop={4}
      android_ripple={{ color: variant === 'primary' ? 'rgba(255,255,255,0.18)' : 'rgba(37,99,235,0.10)' }}
      style={({ pressed }) => ({
        minHeight: uiLayout.controlHeight,
        borderRadius: 10,
        backgroundColor: pressed && !blocked ? (variant === 'primary' ? tone.foreground : tone.hover) : backgroundColor,
        alignItems: 'center',
        justifyContent: 'center',
        paddingHorizontal: uiLayout.cardPadding,
        paddingVertical: uiLayout.fieldGap,
        opacity: 1,
      })}
    >
      <View style={{ minHeight: 22, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, maxWidth: '100%' }}>
        {loading ? <ActivityIndicator size="small" color={textColor} /> : null}
        <Text style={{ color: textColor, fontWeight: '600', fontSize: 15, lineHeight: uiLayout.bodyLineHeight,
          includeFontPadding: false, flexShrink: 1, textAlign: 'center' }}>{title}</Text>
      </View>
    </Pressable>
  );
}

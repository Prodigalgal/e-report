import { Text, View } from 'react-native';

import { colors, uiLayout } from '@/ui/theme';

export function InfoRow({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <View style={{ gap: 4, minWidth: 0 }}>
      <Text style={{ color: colors.muted, fontSize: 13, lineHeight: uiLayout.captionLineHeight, includeFontPadding: false }}>{label}</Text>
      <Text selectable style={{ color: colors.text, fontSize: 15, lineHeight: uiLayout.bodyLineHeight, includeFontPadding: false, fontWeight: '600' }}>
        {value === undefined || value === null || value === '' ? '-' : String(value)}
      </Text>
    </View>
  );
}

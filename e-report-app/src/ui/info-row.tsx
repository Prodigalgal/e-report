import { Text, View } from 'react-native';

import { colors, uiLayout } from '@/ui/theme';

export function InfoRow({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 12, minWidth: 0 }}>
      <Text style={{ width: 88, flexShrink: 0, color: colors.muted, fontSize: 13, lineHeight: uiLayout.bodyLineHeight, includeFontPadding: false }}>{label}</Text>
      <Text selectable style={{ flex: 1, minWidth: 0, color: colors.text, fontSize: 15, lineHeight: uiLayout.bodyLineHeight, includeFontPadding: false, fontWeight: '600' }}>
        {value === undefined || value === null || value === '' ? '-' : String(value)}
      </Text>
    </View>
  );
}

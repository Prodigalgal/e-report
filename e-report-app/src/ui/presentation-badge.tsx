import { Text, View } from 'react-native';

import type { PresentationTone } from '@/constants/work-order-presentation';

export function PresentationBadge({ label, tone }: { label: string; tone: PresentationTone }) {
  return (
    <View
      style={{
        alignSelf: 'flex-start',
        minHeight: 28,
        maxWidth: '100%',
        flexShrink: 1,
        minWidth: 0,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: 6,
        backgroundColor: tone.surface,
        paddingHorizontal: 9,
        paddingVertical: 4,
      }}
    >
      <Text style={{ color: tone.foreground, fontWeight: '500', fontSize: 12, lineHeight: 18, includeFontPadding: false }}>{label}</Text>
    </View>
  );
}

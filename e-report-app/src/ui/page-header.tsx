import { Text, View, useWindowDimensions } from 'react-native';

import { colors, uiLayout } from '@/ui/theme';

export function PageHeader({
  title,
  subtitle,
  accentColor = colors.text,
  trailing,
}: {
  title: string;
  subtitle?: string;
  accentColor?: string;
  trailing?: React.ReactNode;
}) {
  const { width } = useWindowDimensions();
  const narrow = width < 480;
  return (
    <View style={{ flexDirection: narrow ? 'column' : 'row', alignItems: narrow ? 'stretch' : 'center', gap: 8, minWidth: 0 }}>
      <View style={{ flex: narrow ? undefined : 1, minWidth: 0, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'baseline', columnGap: 12, rowGap: 4 }}>
        <Text style={{ color: accentColor, fontSize: uiLayout.pageTitleSize, lineHeight: uiLayout.pageTitleLineHeight,
          fontWeight: '700', letterSpacing: -0.5, includeFontPadding: false }}>{title}</Text>
        {!!subtitle && <Text style={{ color: colors.muted, lineHeight: uiLayout.bodyLineHeight }}>{subtitle}</Text>}
      </View>
      {!!trailing && <View style={{ flexShrink: 1, alignItems: narrow ? 'flex-start' : 'flex-end' }}>{trailing}</View>}
    </View>
  );
}

export function SectionHeader({
  title,
  subtitle,
  step,
}: {
  title: string;
  subtitle?: string;
  step?: number;
}) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      {step !== undefined && (
        <View
          style={{
            width: 26,
            height: 26,
            borderRadius: 13,
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: colors.controlSelectedSurface,
          }}
        >
          <Text style={{ color: colors.primary, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] }}>{step}</Text>
        </View>
      )}
      <View style={{ flex: 1, minWidth: 0, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'baseline', columnGap: 8, rowGap: 2 }}>
        <Text style={{ color: colors.text, fontSize: 18, lineHeight: 26, fontWeight: '600', includeFontPadding: false }}>{title}</Text>
        {!!subtitle && <Text style={{ color: colors.muted, fontSize: 13, lineHeight: 18 }}>{subtitle}</Text>}
      </View>
    </View>
  );
}

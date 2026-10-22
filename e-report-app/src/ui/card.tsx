import { View, type ViewStyle } from 'react-native';

import { colors, uiLayout } from '@/ui/theme';

export function Card({ children, style }: { children: React.ReactNode; style?: ViewStyle }) {
  return (
    <View
      style={[
        {
          borderRadius: 14,
          borderWidth: 1,
          borderColor: colors.border,
          backgroundColor: colors.surface,
          padding: uiLayout.cardPadding,
          gap: uiLayout.fieldGap,
          minWidth: 0,
        },
        style,
      ]}
    >
      {children}
    </View>
  );
}

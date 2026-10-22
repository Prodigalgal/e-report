import { Text, View } from 'react-native';

import { colors } from '@/ui/theme';

export function FormErrorMessage({ children }: { children: string }) {
  return (
    <View
      accessibilityRole="alert"
      style={{
        borderWidth: 1,
        borderColor: '#FCA5A5',
        borderRadius: 8,
        backgroundColor: colors.dangerSurface,
        padding: 12,
      }}
    >
      <Text selectable style={{ color: colors.danger, fontSize: 14, lineHeight: 21 }}>{children}</Text>
    </View>
  );
}

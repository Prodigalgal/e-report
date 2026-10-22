import { ScrollView, View, type ViewStyle, useWindowDimensions } from 'react-native';

import { colors, uiLayout } from '@/ui/theme';

export function Screen({
  children,
  contentStyle,
  maxWidth = uiLayout.appContentMaxWidth,
}: {
  children: React.ReactNode;
  contentStyle?: ViewStyle;
  maxWidth?: number;
}) {
  const { width } = useWindowDimensions();
  const horizontalPadding = width >= 768 ? uiLayout.widePageGutter : uiLayout.pageGutter;

  return (
    <ScrollView
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{ flexGrow: 1 }}
    >
      <View
        style={[
          {
            width: '100%',
            maxWidth,
            alignSelf: 'center',
            flexGrow: 1,
            paddingHorizontal: horizontalPadding,
            paddingTop: uiLayout.pageGutter,
            paddingBottom: 24,
            gap: uiLayout.pageGap,
          },
          contentStyle,
        ]}
      >
        {children}
      </View>
    </ScrollView>
  );
}

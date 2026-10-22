import { Pressable, ScrollView, Text, View } from 'react-native';

import { colors, uiLayout } from '@/ui/theme';

export function SectionTabs<T extends string>({ value, onChange, options, label }: {
  value: T;
  onChange: (value: T) => void;
  options: { value: T; label: string }[];
  label: string;
}) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexGrow: 0, borderBottomWidth: 1, borderBottomColor: colors.border }}
                accessibilityLabel={label} contentContainerStyle={{ gap: 12 }}>
      {options.map(option => (
        <Pressable key={option.value} accessibilityRole="tab" accessibilityState={{ selected: value === option.value }}
                   onPress={() => onChange(option.value)}
                   style={({ pressed }) => ({
                     minHeight: uiLayout.controlHeight, paddingHorizontal: 8, justifyContent: 'center',
                     borderBottomWidth: 2, borderBottomColor: value === option.value ? colors.primary : 'transparent',
                     backgroundColor: pressed ? colors.controlPressed : 'transparent',
                   })}>
          <Text style={{ color: value === option.value ? colors.primary : colors.muted, fontWeight: '600', fontSize: 14 }}>
            {option.label}
          </Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}

export function SectionContent({ value, selected, children }: {
  value: string;
  selected: string;
  children: React.ReactNode;
}) {
  const visible = value === selected;
  return (
    <View accessibilityElementsHidden={!visible} importantForAccessibility={visible ? 'auto' : 'no-hide-descendants'}
          style={{ display: visible ? 'flex' : 'none', gap: uiLayout.pageGap }}>
      {children}
    </View>
  );
}

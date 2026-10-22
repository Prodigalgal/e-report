import { Pressable, Text, TextInput, type TextInputProps, View } from 'react-native';

import { colors, uiLayout } from '@/ui/theme';

type SearchFieldProps = Omit<TextInputProps, 'value' | 'onChangeText'> & {
  value: string;
  onChangeText: (value: string) => void;
  label?: string;
  isSearching?: boolean;
  resultCount?: number;
  helperText?: string;
};

export function SearchField({
  value,
  onChangeText,
  label = '搜索',
  isSearching = false,
  resultCount,
  helperText,
  style,
  ...inputProps
}: SearchFieldProps) {
  const hasSearchTerm = value.trim().length > 0;
  const feedbackText = hasSearchTerm
    ? isSearching
      ? '正在搜索...'
      : typeof resultCount === 'number' ? `找到 ${resultCount} 条结果` : helperText
    : helperText;

  return (
    <View style={{ gap: 6 }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'baseline', justifyContent: 'space-between', columnGap: 8, rowGap: 2 }}>
        <Text style={{ color: colors.text, fontWeight: '700', fontSize: 14, lineHeight: uiLayout.labelLineHeight, includeFontPadding: false }}>{label}</Text>
        {!!feedbackText && <Text accessibilityLiveRegion="polite" style={{ color: colors.muted, fontSize: 13, lineHeight: uiLayout.captionLineHeight, includeFontPadding: false }}>{feedbackText}</Text>}
      </View>
      <View
        style={{
          minHeight: uiLayout.controlHeight,
          flexDirection: 'row',
          alignItems: 'center',
          borderWidth: 1,
          borderColor: colors.border,
          borderRadius: uiLayout.cornerRadius,
          backgroundColor: colors.surface,
          paddingLeft: 12,
        }}
      >
        <TextInput
          {...inputProps}
          accessibilityLabel={inputProps.accessibilityLabel || label}
          value={value}
          onChangeText={onChangeText}
          returnKeyType="search"
          autoCapitalize="none"
          autoCorrect={false}
          placeholderTextColor={colors.placeholder}
          style={[
            {
              minHeight: uiLayout.controlHeight - 2,
              flex: 1,
              color: colors.text,
              fontSize: 16,
              lineHeight: uiLayout.bodyLineHeight,
              includeFontPadding: false,
              paddingVertical: 10,
            },
            style,
          ]}
        />
        {hasSearchTerm && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="清除搜索内容"
            hitSlop={4}
            onPress={() => onChangeText('')}
            style={({ pressed }) => ({
              width: 46,
              minHeight: uiLayout.controlHeight - 2,
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: colors.controlSurface,
              borderRadius: uiLayout.cornerRadius,
              marginRight: 2,
              opacity: pressed ? 0.55 : 1,
            })}
          >
            <Text style={{ color: colors.muted, fontSize: 24, lineHeight: 28 }}>×</Text>
          </Pressable>
        )}
      </View>
    </View>
  );
}

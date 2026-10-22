import { useState } from 'react';
import { Text, TextInput, type TextInputProps, View } from 'react-native';

import { colors, uiLayout } from '@/ui/theme';

type TextFieldProps = TextInputProps & {
  label: string;
  helperText?: string;
  errorMessage?: string;
  required?: boolean;
};

export function TextField({ label, helperText, errorMessage, required, multiline, style, onBlur, onFocus, ...props }: TextFieldProps) {
  const [focused, setFocused] = useState(false);

  return (
    <View style={{ gap: 6 }}>
      <Text style={{ color: colors.text, fontWeight: '700', fontSize: 14, lineHeight: uiLayout.labelLineHeight, includeFontPadding: false }}>
        {label}{required ? ' *' : ''}
      </Text>
      <TextInput
        {...props}
        onFocus={event => {
          setFocused(true);
          onFocus?.(event);
        }}
        onBlur={event => {
          setFocused(false);
          onBlur?.(event);
        }}
        accessibilityLabel={props.accessibilityLabel || label}
        multiline={multiline}
        placeholderTextColor={colors.placeholder}
        style={[
          {
            minHeight: multiline ? uiLayout.controlHeight * 2 : uiLayout.controlHeight,
            borderWidth: 1,
            borderColor: errorMessage ? colors.danger : focused ? colors.primary : colors.border,
            borderRadius: uiLayout.cornerRadius,
            backgroundColor: '#fff',
            paddingHorizontal: 12,
            paddingVertical: 10,
            color: colors.text,
            fontSize: 16,
            lineHeight: uiLayout.bodyLineHeight,
            includeFontPadding: false,
            textAlignVertical: multiline ? 'top' : 'center',
            boxShadow: focused ? '0 0 0 3px rgba(37, 99, 235, 0.12)' : undefined,
          },
          style,
        ]}
      />
      {!!errorMessage && <Text selectable style={{ color: colors.danger, fontSize: 13, lineHeight: uiLayout.captionLineHeight }}>{errorMessage}</Text>}
      {!errorMessage && !!helperText && <Text style={{ color: colors.muted, fontSize: 13, lineHeight: uiLayout.captionLineHeight }}>{helperText}</Text>}
    </View>
  );
}

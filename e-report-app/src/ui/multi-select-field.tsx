import { useState } from 'react';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';

import { AppButton } from '@/ui/app-button';
import type { SelectOption } from '@/ui/select-field';
import { colors, uiLayout } from '@/ui/theme';
import { toggleSelectedValue } from '@/utils/multi-select';

type MultiSelectFieldProps = {
  label: string;
  values: readonly string[];
  options: readonly SelectOption[];
  allLabel: string;
  onChange: (values: string[]) => void;
};

export function MultiSelectField({ label, values, options, allLabel, onChange }: MultiSelectFieldProps) {
  const [open, setOpen] = useState(false);
  const [draftValues, setDraftValues] = useState<string[]>([]);
  const selectableOptions = options.filter((option) => option.value);
  const selectedLabels = selectableOptions
    .filter((option) => values.includes(option.value))
    .map((option) => option.label);
  const selectedSummary = selectedLabels.join('、');
  const displayValue = selectedLabels.length === 0
    ? allLabel
    : selectedLabels.length === 1
      ? selectedLabels[0]
      : `${selectedLabels[0]}等`;

  const openOptions = () => {
    setDraftValues([...values]);
    setOpen(true);
  };

  return (
    <View style={{ gap: 6 }}>
      <Text style={{ color: colors.text, fontSize: 14, lineHeight: uiLayout.labelLineHeight, includeFontPadding: false, fontWeight: '700' }}>{label}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}，当前选择${selectedSummary || allLabel}`}
        onPress={openOptions}
        style={({ pressed }) => ({
          minHeight: uiLayout.controlHeight,
          borderWidth: 1,
          borderColor: colors.controlBorder,
          borderRadius: uiLayout.cornerRadius,
          backgroundColor: colors.controlSurface,
          paddingHorizontal: 12,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          opacity: pressed ? 0.72 : 1,
        })}
      >
        <Text numberOfLines={1} style={{ flex: 1, minWidth: 0, color: colors.text, fontSize: 16, lineHeight: uiLayout.bodyLineHeight, includeFontPadding: false, fontWeight: '700' }}>
          {displayValue}
        </Text>
        <Text style={{ color: colors.muted, fontSize: 14, fontWeight: '800' }}>
          {values.length > 0 ? `${values.length}项` : '全部'}
        </Text>
      </Pressable>

      <Modal transparent visible={open} animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="关闭选项"
          onPress={() => setOpen(false)}
          style={{
            flex: 1,
            backgroundColor: 'rgba(15, 23, 42, 0.42)',
            justifyContent: 'center',
            padding: uiLayout.pageGutter,
            alignItems: 'center',
          }}
        >
          <Pressable
            accessibilityRole="none"
            onPress={(event) => event.stopPropagation()}
            style={{
              width: '100%',
              maxWidth: uiLayout.pickerMaxWidth,
              maxHeight: '80%',
              borderRadius: uiLayout.cornerRadius,
              backgroundColor: colors.surface,
              overflow: 'hidden',
            }}
          >
            <View style={{ padding: uiLayout.cardPadding, borderBottomWidth: 1, borderBottomColor: colors.border }}>
              <Text style={{ color: colors.text, fontSize: 18, lineHeight: 26, includeFontPadding: false, fontWeight: '900' }}>{label}</Text>
            </View>
            <ScrollView>
              <OptionRow
                label={allLabel}
                selected={draftValues.length === 0}
                onPress={() => setDraftValues([])}
              />
              {selectableOptions.map((option) => (
                <OptionRow
                  key={option.value}
                  label={option.label}
                  selected={draftValues.includes(option.value)}
                  onPress={() => setDraftValues((current) => toggleSelectedValue(current, option.value))}
                />
              ))}
            </ScrollView>
            <View style={{ flexDirection: 'row', gap: uiLayout.fieldGap, padding: uiLayout.cardPadding, borderTopWidth: 1, borderTopColor: colors.border }}>
              <View style={{ flex: 1 }}>
                <AppButton title="取消" onPress={() => setOpen(false)} variant="secondary" />
              </View>
              <View style={{ flex: 1 }}>
                <AppButton
                  title="确定"
                  onPress={() => {
                    onChange(draftValues);
                    setOpen(false);
                  }}
                />
              </View>
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}

function OptionRow({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected }}
      onPress={onPress}
      style={({ pressed }) => ({
        minHeight: uiLayout.controlHeight,
        paddingHorizontal: 16,
        paddingVertical: 12,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
        borderBottomWidth: 1,
        borderBottomColor: colors.border,
        backgroundColor: selected ? colors.controlSelectedSurface : colors.controlSurface,
        opacity: pressed ? 0.72 : 1,
      })}
    >
      <View style={{ width: 22, height: 22, borderRadius: 4, borderWidth: 1, borderColor: selected ? colors.primary : colors.border, backgroundColor: selected ? colors.primary : colors.surface, alignItems: 'center', justifyContent: 'center' }}>
        {selected && <Text style={{ color: '#ffffff', fontSize: 15, fontWeight: '900' }}>✓</Text>}
      </View>
      <Text style={{ flex: 1, color: selected ? colors.controlSelectedText : colors.text, fontSize: 16, lineHeight: uiLayout.bodyLineHeight, includeFontPadding: false, fontWeight: selected ? '800' : '600' }}>
        {label}
      </Text>
    </Pressable>
  );
}

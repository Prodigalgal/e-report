import { useState } from 'react';
import { FlatList, Modal, Pressable, Text, View } from 'react-native';

import { AppButton } from '@/ui/app-button';
import { SearchField } from '@/ui/search-field';
import { colors, uiLayout } from '@/ui/theme';

export type SelectOption = {
  label: string;
  value: string;
};

type SelectFieldProps = {
  label: string;
  value: string;
  options: readonly SelectOption[];
  onChange: (value: string) => void;
};

export function SelectField({ label, value, options, onChange }: SelectFieldProps) {
  const [open, setOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const selected = options.find((option) => option.value === value) || options[0];
  const searchable = options.length > 8;
  const normalizedSearch = searchTerm.trim().toLocaleLowerCase();
  const visibleOptions = searchable && normalizedSearch
    ? options.filter(option => option.label.toLocaleLowerCase().includes(normalizedSearch))
    : options;

  return (
    <View style={{ gap: 6 }}>
      <Text style={{ color: colors.text, fontSize: 14, lineHeight: uiLayout.labelLineHeight, includeFontPadding: false, fontWeight: '700' }}>{label}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}，当前选择${selected?.label || '未选择'}`}
        onPress={() => { setSearchTerm(''); setOpen(true); }}
        style={({ pressed }) => ({
          minHeight: uiLayout.controlHeight,
          borderWidth: 1,
          borderColor: colors.controlBorder,
          borderRadius: 10,
          backgroundColor: colors.controlSurface,
          paddingHorizontal: 12,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          opacity: pressed ? 0.72 : 1,
        })}
      >
        <Text numberOfLines={1} style={{ flex: 1, minWidth: 0, color: colors.text, fontSize: 16, lineHeight: uiLayout.bodyLineHeight, includeFontPadding: false }}>
          {selected?.label || '请选择'}
        </Text>
        <Text style={{ color: colors.muted, fontSize: 18 }}>⌄</Text>
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
              height: searchable ? '75%' : undefined,
              borderRadius: 18,
              backgroundColor: colors.surface,
              overflow: 'hidden',
            }}
          >
            <View style={{ padding: uiLayout.cardPadding, borderBottomWidth: 1, borderBottomColor: colors.border, gap: uiLayout.fieldGap }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                <Text style={{ flex: 1, color: colors.text, fontSize: 18, lineHeight: 26, includeFontPadding: false, fontWeight: '900' }}>{label}</Text>
                <AppButton title="关闭" variant="ghost" onPress={() => setOpen(false)} />
              </View>
              {searchable && <SearchField label="搜索选项" placeholder="输入名称" value={searchTerm} onChangeText={setSearchTerm} resultCount={visibleOptions.length} />}
            </View>
            <FlatList
              data={visibleOptions}
              style={{ flexGrow: 0, flexShrink: 1 }}
              keyboardShouldPersistTaps="handled"
              initialNumToRender={12}
              maxToRenderPerBatch={12}
              windowSize={5}
              extraData={value}
              keyExtractor={option => option.value || '__all__'}
              ListEmptyComponent={<Text style={{ padding: uiLayout.cardPadding, color: colors.muted }}>没有匹配的选项，请修改搜索内容。</Text>}
              renderItem={({ item: option }) => {
                const isSelected = option.value === value;
                return (
                  <Pressable
                    key={option.value || '__all__'}
                    accessibilityRole="button"
                    accessibilityState={{ selected: isSelected }}
                    onPress={() => {
                      onChange(option.value);
                      setOpen(false);
                    }}
                    style={({ pressed }) => ({
                      minHeight: uiLayout.controlHeight,
                      paddingHorizontal: 16,
                      paddingVertical: 12,
                      flexDirection: 'row',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      borderBottomWidth: 1,
                      borderBottomColor: colors.border,
                      backgroundColor: isSelected ? colors.controlSelectedSurface : colors.controlSurface,
                      opacity: pressed ? 0.72 : 1,
                    })}
                  >
                    <Text style={{ flex: 1, color: isSelected ? colors.controlSelectedText : colors.text, fontSize: 16, lineHeight: uiLayout.bodyLineHeight, includeFontPadding: false, fontWeight: isSelected ? '600' : '400' }}>
                      {option.label}
                    </Text>
                    {isSelected && <Text style={{ color: colors.primary, fontWeight: '900' }}>已选</Text>}
                  </Pressable>
                );
              }}
            />
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}

import { Pressable, Text, View } from 'react-native';

import { useAppMenu } from '@/navigation/app-menu-state';
import { BrandMark } from '@/ui/brand';
import { colors } from '@/ui/theme';

export function AppHeaderTitle() {
  const { sidebarExpanded, toggleSidebar } = useAppMenu();

  return (
    <Pressable
      accessibilityLabel={sidebarExpanded ? '收起侧边栏' : '展开侧边栏'}
      onPress={toggleSidebar}
      style={({ pressed }) => ({
        minHeight: 44,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
        opacity: pressed ? 0.78 : 1,
      })}
    >
      <View
        style={{
          width: 28,
          height: 28,
          borderRadius: 14,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Text style={{ color: colors.muted, fontSize: 20 }}>
          {sidebarExpanded ? '‹' : '☰'}
        </Text>
      </View>
      <BrandMark size={26} />
      <Text style={{ color: colors.text, fontSize: 17, fontWeight: '600' }}>网络运维管理</Text>
    </Pressable>
  );
}

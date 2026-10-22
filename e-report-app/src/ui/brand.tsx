import { Image } from 'expo-image';
import { Text, View } from 'react-native';

import { colors } from '@/ui/theme';

const brandMark = require('../../assets/splash-icon.png');

export function BrandMark({ size = 72 }: { size?: number }) {
  return (
    <Image
      source={brandMark}
      contentFit="contain"
      style={{
        width: size,
        height: size,
      }}
    />
  );
}

export function BrandLoading({ message = '正在加载应用' }: { message?: string }) {
  return (
    <View
      style={{
        flex: 1,
        minHeight: 360,
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        padding: 24,
        backgroundColor: colors.background,
      }}
    >
      <BrandMark size={86} />
      <Text style={{ color: colors.text, fontSize: 20, lineHeight: 28, includeFontPadding: false, fontWeight: '800' }}>网络运维管理</Text>
      <Text style={{ color: colors.muted }}>{message}</Text>
      <View
        style={{
          width: 148,
          height: 4,
          borderRadius: 999,
          backgroundColor: 'rgba(37, 99, 235, 0.14)',
          overflow: 'hidden',
        }}
      >
        <View
          style={{
            width: 72,
            height: 4,
            borderRadius: 999,
            backgroundColor: colors.primary,
          }}
        />
      </View>
    </View>
  );
}

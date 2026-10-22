import { useRouter } from 'expo-router';
import { useEffect } from 'react';
import { Pressable, Text, View } from 'react-native';

import { useAuth } from '@/auth/auth-provider';
import { WORK_ORDER_TYPES, reportTypeToSlug } from '@/constants/work-orders';
import { reportTypePresentation, type PresentationTone } from '@/constants/work-order-presentation';
import type { WorkOrderType } from '@/types/api';
import { BrandLoading } from '@/ui/brand';
import { Card } from '@/ui/card';
import { PageHeader } from '@/ui/page-header';
import { Screen } from '@/ui/screen';
import { colors } from '@/ui/theme';

export default function ReportIndexScreen() {
  const router = useRouter();
  const { isAuthenticated, isLoading, hasPermission } = useAuth();
  const reportEntries = WORK_ORDER_TYPES.filter((item) => hasPermission(item.permissions.report));

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace('/login');
    }
  }, [isAuthenticated, isLoading, router]);

  if (isLoading || !isAuthenticated) {
    return <BrandLoading message="正在加载报单入口" />;
  }

  return (
    <Screen>
      <PageHeader title="现场报单" subtitle="选择工单类型" />

      {reportEntries.length > 0 ? (
        <View style={{ gap: 10 }}>
          {reportEntries.map((item) => (
            <ReportTypeCard
              key={item.reportType}
              title={item.reportLabel}
              description={item.description}
              tone={reportTypePresentation(item.reportType)}
              onPress={() => router.push(`/report/${reportTypeToSlug(item.reportType as WorkOrderType)}`)}
            />
          ))}
        </View>
      ) : (
        <Card>
          <Text style={{ color: colors.muted }}>当前账号没有报单权限。</Text>
        </Card>
      )}
    </Screen>
  );
}

function ReportTypeCard({
  title,
  description,
  tone,
  onPress,
}: {
  title: string;
  description: string;
  tone: PresentationTone;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => ({ opacity: pressed ? 0.78 : 1 })}>
      <View
        style={{
          borderRadius: 8,
          borderWidth: 1,
          borderColor: tone.border,
          backgroundColor: tone.surface,
          minHeight: 78,
          padding: 16,
          gap: 6,
          borderLeftWidth: 5,
          borderLeftColor: tone.foreground,
        }}
      >
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
          <Text style={{ flex: 1, color: colors.text, fontSize: 18, fontWeight: '900' }}>{title}</Text>
          <Text accessibilityLabel="进入" style={{ color: tone.foreground, fontSize: 24, fontWeight: '900' }}>›</Text>
        </View>
        <Text style={{ color: colors.muted, lineHeight: 20 }}>{description}</Text>
      </View>
    </Pressable>
  );
}

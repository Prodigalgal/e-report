import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, Text, View, useWindowDimensions } from 'react-native';

import { formatApiErrorMessage } from '@/api/http';
import { searchWorkOrders, fetchWorkOrderStatistics } from '@/api/work-order-api';
import { useAuth } from '@/auth/auth-provider';
import { resolveUserAccessSummary } from '@/auth/admin-access';
import {
  DASHBOARD_STATISTICS_PERMISSIONS,
  WORK_ORDER_TASKS,
  WORK_ORDER_TYPES,
} from '@/constants/work-orders';
import { WorkOrderCard } from '@/features/work-orders/work-order-card';
import { useAppMenu } from '@/navigation/app-menu-state';
import type { WorkOrderType, WorkOrderListItem, WorkOrderStatistics } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { BrandMark } from '@/ui/brand';
import { Card } from '@/ui/card';
import { PageHeader, SectionHeader } from '@/ui/page-header';
import { colors, uiLayout } from '@/ui/theme';

type SidebarItem = {
  title: string;
  shortTitle: string;
  subtitle: string;
  tone: string;
  active?: boolean;
  onPress: () => void;
};

export function HomeDashboard() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const { sidebarExpanded, closeSidebar } = useAppMenu();
  const { currentUser, hasPermission, hasAnyPermission, isAdmin } = useAuth();

  const reportEntries = WORK_ORDER_TYPES.filter((item) => hasPermission(item.permissions.report));
  const respondEntries = WORK_ORDER_TYPES.filter((item) => hasPermission(item.permissions.responseSubmit));
  const canProcess = respondEntries.length > 0;
  const canViewDashboard = hasAnyPermission(DASHBOARD_STATISTICS_PERMISSIONS);
  const canOpenAdmin = isAdmin;

  const myWorkOrdersQuery = useQuery({
    queryKey: ['my-work-orders', 'home'],
    queryFn: () => searchWorkOrders({ page: 0, size: 3 }),
    placeholderData: keepPreviousData,
    staleTime: 1000 * 60 * 2,
  });
  const statisticsQuery = useQuery({
    queryKey: ['admin-work-order-statistics'],
    queryFn: fetchWorkOrderStatistics,
    enabled: isAdmin && canViewDashboard,
    placeholderData: keepPreviousData,
    staleTime: 1000 * 60 * 2,
  });

  const profileLabel = resolveUserAccessSummary(currentUser);
  const processingTotal = countPendingByTypes(statisticsQuery.data, respondEntries.map((item) => item.reportType));
  const sidebarWidth = Math.min(Math.max(width * 0.72, 240), 292);

  const navigate = (href: Parameters<typeof router.push>[0]) => {
    closeSidebar();
    router.push(href);
  };

  const sidebarItems: SidebarItem[] = [
    {
      title: '首页概览',
      shortTitle: '首',
      subtitle: '数据与入口',
      tone: colors.primary,
      active: true,
      onPress: () => closeSidebar(),
    },
    ...(reportEntries.length > 0 ? [{
      title: WORK_ORDER_TASKS.REPORT.label,
      shortTitle: '报',
      subtitle: '三类工单',
      tone: '#2563eb',
      onPress: () => navigate('/report'),
    }] : []),
    {
      title: WORK_ORDER_TASKS.SEARCH.label,
      shortTitle: '搜',
      subtitle: canProcess ? '查询与处理' : '进度查询',
      tone: '#0369a1',
      onPress: () => navigate(WORK_ORDER_TASKS.SEARCH.path),
    },
    ...(canOpenAdmin ? [{
      title: WORK_ORDER_TASKS.ADMIN.label,
      shortTitle: '概',
      subtitle: canViewDashboard ? '统计与配置' : '系统配置',
      tone: colors.management,
      onPress: () => navigate('/admin'),
    }] : []),
    {
      title: '个人信息',
      shortTitle: '人',
      subtitle: '资料与退出',
      tone: '#475569',
      onPress: () => navigate('/profile'),
    },
    {
      title: '关于应用',
      shortTitle: '!',
      subtitle: '版本线路',
      tone: '#64748b',
      onPress: () => navigate('/about'),
    },
  ];

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        keyboardShouldPersistTaps="handled"
        style={{ flex: 1, backgroundColor: colors.background }}
        contentContainerStyle={{
          width: '100%',
          maxWidth: uiLayout.appContentMaxWidth,
          alignSelf: 'center',
          paddingHorizontal: width >= 768 ? uiLayout.widePageGutter : uiLayout.pageGutter,
          paddingTop: uiLayout.pageGutter,
          gap: uiLayout.pageGap,
          paddingBottom: 32,
        }}
      >
        <PageHeader
          title={currentUser?.name ? `你好，${currentUser.name}` : '首页概览'}
          subtitle={profileLabel}
        />
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          <StatusMetric label="可见工单" value={formatNullableNumber(myWorkOrdersQuery.data?.totalElements)} />
          {canProcess && (
            <StatusMetric
              label="待处理"
              value={statisticsQuery.isLoading ? '...' : formatNullableNumber(processingTotal)}
              tone="warning"
            />
          )}
          {isAdmin && <StatusMetric label="全局工单" value={formatNullableNumber(statisticsQuery.data?.total)} tone="primary" />}
        </View>

        <View style={{ gap: 10 }}>
          <SectionHeader title="工作入口" />
          {reportEntries.length > 0 && (
            <TaskTile
              title={WORK_ORDER_TASKS.REPORT.label}
              description={WORK_ORDER_TASKS.REPORT.description}
              tone={colors.primary}
              onPress={() => navigate('/report')}
            />
          )}
          <TaskTile
            title={WORK_ORDER_TASKS.SEARCH.label}
            description={WORK_ORDER_TASKS.SEARCH.description}
            tone="#0369a1"
            badge={canProcess && !statisticsQuery.isLoading ? formatNullableNumber(processingTotal) : undefined}
            onPress={() => navigate(WORK_ORDER_TASKS.SEARCH.path)}
          />
          {canOpenAdmin && (
            <TaskTile
              title={WORK_ORDER_TASKS.ADMIN.label}
              description={WORK_ORDER_TASKS.ADMIN.description}
              tone={colors.management}
              onPress={() => navigate('/admin')}
            />
          )}
        </View>

        <RecentWorkOrdersPreview
          isLoading={myWorkOrdersQuery.isLoading}
          error={myWorkOrdersQuery.error}
          workOrders={myWorkOrdersQuery.data?.content ?? []}
          totalElements={myWorkOrdersQuery.data?.totalElements ?? 0}
          onOpen={(id) => navigate(`/work-orders/${id}`)}
          onOpenAll={() => navigate('/search')}
        />

        {!!statisticsQuery.error && canViewDashboard && (
          <Card>
            <Text selectable style={{ color: colors.danger }}>
              {formatApiErrorMessage(statisticsQuery.error, '统计数据加载失败，不影响报单和处理工单。')}
            </Text>
          </Card>
        )}
      </ScrollView>

      {sidebarExpanded && (
        <View
          pointerEvents="box-none"
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 0,
            bottom: 0,
            zIndex: 20,
          }}
        >
          <Pressable
            accessibilityLabel="收起侧边栏"
            onPress={closeSidebar}
            style={{
              position: 'absolute',
              left: 0,
              right: 0,
              top: 0,
              bottom: 0,
              backgroundColor: 'rgba(15, 23, 42, 0.16)',
            }}
          />
          <View
            pointerEvents="box-none"
            style={{
              position: 'absolute',
              left: 12,
              top: 12,
              bottom: 12,
              width: sidebarWidth,
              zIndex: 21,
            }}
          >
            <HomeSidebar
              width={sidebarWidth}
              displayName={currentUser?.name || currentUser?.username || '未登录'}
              profileLabel={profileLabel}
              myWorkOrderTotal={myWorkOrdersQuery.data?.totalElements}
              processingTotal={processingTotal}
              globalTotal={statisticsQuery.data?.total}
              canProcess={canProcess}
              isAdmin={isAdmin}
              statisticsLoading={statisticsQuery.isLoading}
              items={sidebarItems}
            />
          </View>
        </View>
      )}
    </View>
  );
}

function HomeSidebar({
  width,
  displayName,
  profileLabel,
  myWorkOrderTotal,
  processingTotal,
  globalTotal,
  canProcess,
  isAdmin,
  statisticsLoading,
  items,
}: {
  width: number;
  displayName: string;
  profileLabel: string;
  myWorkOrderTotal?: number;
  processingTotal?: number;
  globalTotal?: number;
  canProcess: boolean;
  isAdmin: boolean;
  statisticsLoading: boolean;
  items: SidebarItem[];
}) {
  return (
    <Card
      style={{
        width,
        maxHeight: '100%',
        padding: uiLayout.cardPadding,
        gap: uiLayout.fieldGap,
        boxShadow: '0 12px 28px rgba(15, 23, 42, 0.24)',
      }}
    >
      <View style={{ alignItems: 'flex-start', gap: 8 }}>
        <BrandMark size={48} />
        <View style={{ gap: 3 }}>
          <Text selectable numberOfLines={1} style={{ color: colors.text, fontSize: 16, fontWeight: '900' }}>{displayName}</Text>
          <Text style={{ color: colors.muted, fontSize: 12 }}>{profileLabel}</Text>
        </View>
      </View>

      <View style={{ gap: 6 }}>
        <CompactMetric label="我的" value={formatNullableNumber(myWorkOrderTotal)} />
        {canProcess && <CompactMetric label="待处" value={statisticsLoading ? '...' : formatNullableNumber(processingTotal)} tone="#e07800" />}
        {isAdmin && <CompactMetric label="全局" value={formatNullableNumber(globalTotal)} tone={colors.primary} />}
      </View>

      <View style={{ gap: 8 }}>
        {items.map((item) => (
          <SidebarButton key={item.title} item={item} />
        ))}
      </View>
    </Card>
  );
}

function SidebarButton({ item }: { item: SidebarItem }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: item.active }}
      onPress={item.onPress}
      style={({ pressed }) => ({
        minHeight: 56,
        borderRadius: 8,
        backgroundColor: pressed ? colors.controlPressed : item.active ? colors.controlSelectedSurface : 'transparent',
        justifyContent: 'center',
        paddingHorizontal: 10,
        alignItems: 'flex-start',
        opacity: pressed ? 0.78 : 1,
      })}
    >
      <Text style={{ color: item.active ? colors.primary : colors.text, fontSize: 15, fontWeight: '600' }}>
        {item.title}
      </Text>
      <Text style={{ color: colors.muted, fontSize: 12 }}>{item.subtitle}</Text>
    </Pressable>
  );
}

function CompactMetric({ label, value, tone = colors.text }: { label: string; value: string | number; tone?: string }) {
  return (
    <View
      style={{
        borderRadius: 14,
        borderWidth: 1,
        borderColor: colors.border,
        backgroundColor: colors.surfaceAlt,
        paddingHorizontal: 9,
        paddingVertical: 7,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 8,
      }}
    >
      <Text style={{ color: colors.muted, fontSize: 12 }}>{label}</Text>
      <Text style={{ color: tone, fontSize: 16, fontWeight: '900', fontVariant: ['tabular-nums'] }}>{value}</Text>
    </View>
  );
}

function RecentWorkOrdersPreview({
  isLoading,
  error,
  workOrders,
  totalElements,
  onOpen,
  onOpenAll,
}: {
  isLoading: boolean;
  error: unknown;
  workOrders: WorkOrderListItem[];
  totalElements: number;
  onOpen: (id: string) => void;
  onOpenAll: () => void;
}) {
  return (
    <View style={{ gap: 10 }}>
      <SectionHeader title="最近工单" subtitle={totalElements > 0 ? `共 ${totalElements} 条` : undefined} />
      {isLoading && <ActivityIndicator color={colors.primary} />}
      {!!error && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
              {formatApiErrorMessage(error, '工单记录加载失败，请稍后重试。')}
          </Text>
        </Card>
      )}
      {workOrders.map((workOrder) => (
        <Pressable key={workOrder.id} onPress={() => onOpen(workOrder.id)} style={({ pressed }) => ({ opacity: pressed ? 0.78 : 1 })}>
          <WorkOrderCard workOrder={workOrder} />
        </Pressable>
      ))}
      {!isLoading && workOrders.length === 0 && !error && (
        <Card>
          <Text style={{ color: colors.muted }}>还没有提交过工单。</Text>
        </Card>
      )}
      {totalElements > 3 && (
        <AppButton title="进入工单查询" onPress={onOpenAll} variant="secondary" />
      )}
    </View>
  );
}

function StatusMetric({ label, value, tone = 'default' }: { label: string; value: number | string; tone?: 'default' | 'warning' | 'primary' }) {
  const toneColor = tone === 'warning' ? '#e07800' : tone === 'primary' ? colors.primary : colors.text;
  return (
    <View
      style={{
        flexGrow: 1,
        flexBasis: '30%',
        minWidth: 86,
        borderRadius: 8,
        borderWidth: 1,
        borderColor: colors.border,
        backgroundColor: colors.surfaceAlt,
        padding: 10,
        gap: 2,
      }}
    >
      <Text style={{ color: colors.muted, fontSize: 12 }}>{label}</Text>
      <Text style={{ color: toneColor, fontSize: 24, fontWeight: '900', fontVariant: ['tabular-nums'] }}>{value}</Text>
    </View>
  );
}

function TaskTile({
  title,
  description,
  tone,
  badge,
  onPress,
}: {
  title: string;
  description: string;
  tone: string;
  badge?: string | number;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={({ pressed }) => ({
        minHeight: 84,
        borderRadius: 14,
        borderWidth: 1,
        borderColor: colors.border,
        backgroundColor: pressed ? colors.controlPressed : colors.surface,
        padding: 18,
        gap: 8,
        opacity: pressed ? 0.78 : 1,
      })}
    >
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
        <View style={{ flex: 1, gap: 4 }}>
          <Text style={{ color: colors.text, fontSize: 18, fontWeight: '600' }}>{title}</Text>
          <Text style={{ color: colors.muted, lineHeight: 19 }}>{description}</Text>
        </View>
        {badge !== undefined && (
          <Text style={{ color: tone, fontSize: 22, fontWeight: '900', fontVariant: ['tabular-nums'] }}>{badge}</Text>
        )}
        <Text accessibilityLabel="进入" style={{ color: colors.muted, fontSize: 26 }}>›</Text>
      </View>
    </Pressable>
  );
}

function countPendingByTypes(statistics: WorkOrderStatistics | undefined, reportTypes: WorkOrderType[]) {
  if (!statistics || reportTypes.length === 0) {
    return undefined;
  }
  const allowed = new Set(reportTypes);
  return statistics.byType
    .filter((item) => allowed.has(item.reportType))
    .reduce((total, item) => total + item.pending, 0);
}

function formatNullableNumber(value: number | undefined) {
  return typeof value === 'number' ? value : '-';
}

import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { formatApiErrorMessage } from '@/api/http';
import { fetchWorkOrderStatisticsByCounty, fetchWorkOrderStatisticsScopes } from '@/api/work-order-api';
import { useAuth } from '@/auth/auth-provider';
import { ADMIN_PERMISSIONS, DASHBOARD_STATISTICS_PERMISSIONS, getVisibleAdminWorkOrderTypes, reportTypeToSlug } from '@/constants/work-orders';
import {
  durationBucketPresentation,
  reportTypePresentation,
  slaRiskPresentation,
  type PresentationTone,
} from '@/constants/work-order-presentation';
import { Card } from '@/ui/card';
import { AppButton } from '@/ui/app-button';
import { PageHeader, SectionHeader } from '@/ui/page-header';
import { Screen } from '@/ui/screen';
import { SectionContent, SectionTabs } from '@/ui/section-tabs';
import { SelectField } from '@/ui/select-field';
import { colors } from '@/ui/theme';
import type { WorkOrderCountyComparison, WorkOrderTypeStatistics } from '@/types/api';
import { formatDuration } from '@/utils/work-order-sla';

export default function AdminDashboardScreen() {
  const router = useRouter();
  const { hasPermission, hasAnyPermission, isAdmin } = useAuth();
  const visibleTypes = getVisibleAdminWorkOrderTypes(hasPermission);
  const canViewDashboard = hasAnyPermission(DASHBOARD_STATISTICS_PERMISSIONS);
  const [activeSection, setActiveSection] = useState<'overview' | 'counties' | 'types' | 'settings'>('overview');
  const selectedSection = canViewDashboard ? activeSection : 'settings';
  const [selectedCountyOrgUnitId, setSelectedCountyOrgUnitId] = useState('');
  const scopesQuery = useQuery({
    queryKey: ['admin-work-order-statistics-scopes'],
    queryFn: fetchWorkOrderStatisticsScopes,
    enabled: isAdmin && canViewDashboard,
    staleTime: 1000 * 60 * 5,
  });
  const query = useQuery({
    queryKey: ['admin-work-order-statistics', selectedCountyOrgUnitId],
    queryFn: () => fetchWorkOrderStatisticsByCounty(selectedCountyOrgUnitId || undefined),
    enabled: isAdmin && canViewDashboard,
    staleTime: 1000 * 60 * 2,
  });

  if (!isAdmin) {
    return (
      <Screen>
        <Card>
          <Text style={{ color: colors.muted }}>当前账号没有管理员后台权限。</Text>
        </Card>
      </Screen>
    );
  }

  const statistics = query.data;
  const statisticsScopes = scopesQuery.data ?? [];
  const selectedCountyName = statisticsScopes.find(
    (scope) => scope.countyOrgUnitId === selectedCountyOrgUnitId,
  )?.countyName;
  const dashboardSubtitle = selectedCountyName
    ? `${selectedCountyName}工单概览`
    : !canViewDashboard || scopesQuery.isLoading || scopesQuery.isError
      ? '工单概览与管理入口'
      : statisticsScopes.length > 0
        ? '全市工单概览与管理入口'
        : '本区县工单概览与管理入口';

  return (
    <Screen>
      <PageHeader title="管理后台" subtitle={dashboardSubtitle}
        trailing={visibleTypes.length > 0 ? <AppButton title="工单查询" onPress={() => router.push('/search')} /> : undefined} />

      {canViewDashboard && (
        <SectionTabs label="后台任务" value={selectedSection} onChange={setActiveSection} options={[
          { value: 'overview', label: '统计概览' },
          { value: 'counties', label: '区县对比' },
          { value: 'types', label: '分类工单' },
          { value: 'settings', label: '管理配置' },
        ]} />
      )}

      {statisticsScopes.length > 0 && selectedSection !== 'settings' && (
        <Card>
          <SelectField
            label="统计范围"
            value={selectedCountyOrgUnitId}
            options={[
              { label: '全市汇总', value: '' },
              ...statisticsScopes.map((scope) => ({ label: scope.countyName, value: scope.countyOrgUnitId })),
            ]}
            onChange={setSelectedCountyOrgUnitId}
          />
        </Card>
      )}

      {!!scopesQuery.error && selectedSection !== 'settings' && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(scopesQuery.error, '区县统计范围加载失败，请稍后重试。')}
          </Text>
        </Card>
      )}

      {query.isLoading && selectedSection !== 'settings' && <ActivityIndicator color={colors.primary} />}
      {!!query.error && selectedSection !== 'settings' && (
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(query.error, '后台统计加载失败，请检查网络后重试。')}
          </Text>
        </Card>
      )}

      {statistics && (
        <>
          <SectionContent value="overview" selected={selectedSection}>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 10 }}>
              <MetricCard label="工单总数" value={statistics.total} />
              <MetricCard label="待处理" value={statistics.pending} />
              <MetricCard label="已处理" value={statistics.resolved + statistics.closed} />
              <MetricCard label="处理率" value={`${formatRate(statistics.resolutionRate)}%`} />
            </View>

            <View style={{ gap: 10 }}>
              <SectionHeader title="故障历时与处理时限" />
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 10 }}>
                <MetricCard label="活动工单" value={statistics.active} tone={durationBucketPresentation('WITHIN_24_HOURS')} />
                <MetricCard label="24小时内" value={statistics.within24Hours} tone={durationBucketPresentation('WITHIN_24_HOURS')} />
                <MetricCard label="24-48小时" value={statistics.from24To48Hours} tone={durationBucketPresentation('HOURS_24_TO_48')} />
                <MetricCard label="48-72小时" value={statistics.from48To72Hours} tone={durationBucketPresentation('HOURS_48_TO_72')} />
                <MetricCard label="超过72小时" value={statistics.over72Hours} tone={durationBucketPresentation('OVER_72_HOURS')} />
                <MetricCard label="当前时限超时" value={statistics.currentSlaBreached} tone={slaRiskPresentation('BREACHED')} />
                <MetricCard label="曾发生超时" value={statistics.everSlaBreached} tone={slaRiskPresentation('BREACHED')} />
                <MetricCard label="最长未闭环" value={formatDuration(statistics.longestActiveElapsedSeconds)} tone={durationBucketPresentation('WITHIN_24_HOURS')} />
              </View>
            </View>
          </SectionContent>

          <SectionContent value="counties" selected={selectedSection}>
            {statistics.countyComparison.length > 0 ? (
              <CountyComparison
                data={statistics.countyComparison}
                onSelect={setSelectedCountyOrgUnitId}
              />
            ) : (
              <Card><Text style={{ color: colors.muted }}>当前统计范围没有区县对比数据。</Text></Card>
            )}
          </SectionContent>

          <SectionContent value="types" selected={selectedSection}>
            <View style={{ gap: 10 }}>
              <SectionHeader title="分类工单" />
              {visibleTypes.map((type) => {
                const itemStatistics = statistics.byType.find((item) => item.reportType === type.reportType);
                return (
                  <Pressable
                    key={type.reportType}
                  accessibilityRole="button"
                  accessibilityLabel={`查看${type.navLabel}工单`}
                    onPress={() => router.push(`/search?type=${reportTypeToSlug(type.reportType)}`)}
                    style={({ pressed }) => ({ opacity: pressed ? 0.78 : 1 })}
                  >
                    <TypeStatisticsCard
                      statistics={itemStatistics}
                      fallbackName={type.navLabel}
                      tone={reportTypePresentation(type.reportType)}
                    />
                  </Pressable>
                );
              })}
            </View>
          </SectionContent>

          <SectionContent value="overview" selected={selectedSection}>
            <Card style={{ gap: 10 }}>
              <Text style={{ fontSize: 18, fontWeight: '800', color: colors.text }}>近 7 天新增</Text>
              <View style={{ gap: 8 }}>
                {statistics.dailyNewReports.map((item) => (
                  <View key={item.date} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                    <Text style={{ width: 86, color: colors.muted }}>{item.date.slice(5)}</Text>
                    <View style={{ flex: 1, height: 8, borderRadius: 999, backgroundColor: colors.surfaceAlt }}>
                      <View
                        style={{
                          width: `${dailyBarPercent(item.total, statistics.dailyNewReports)}%`,
                          minWidth: item.total > 0 ? 8 : 0,
                          height: 8,
                          borderRadius: 999,
                          backgroundColor: reportTypePresentation('NETWORK_FAULT').foreground,
                        }}
                      />
                    </View>
                    <Text style={{ width: 46, textAlign: 'right', color: colors.text, fontWeight: '800' }}>{item.total}</Text>
                  </View>
                ))}
              </View>
            </Card>
          </SectionContent>
        </>
      )}

      <SectionContent value="settings" selected={selectedSection}>
        <View style={{ gap: 10 }}>
          <SectionHeader title="后台配置" />
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 10 }}>
            {hasPermission(ADMIN_PERMISSIONS.USER_MANAGE_VIEW) && (
              <AdminNavButton title="用户管理" onPress={() => router.push('/admin/users')} />
            )}
            {hasPermission(ADMIN_PERMISSIONS.AUTHORIZATION_PROFILE_VIEW) && (
              <AdminNavButton title="岗位权限模板" onPress={() => router.push('/admin/authorization-profiles')} />
            )}
            {hasPermission(ADMIN_PERMISSIONS.SYSTEM_CONFIG_VIEW) && (
              <AdminNavButton title="系统配置" onPress={() => router.push('/admin/configs')} />
            )}
            {hasPermission(ADMIN_PERMISSIONS.SLA_POLICY_VIEW) && (
              <AdminNavButton title="时限策略" onPress={() => router.push('/admin/sla-policies')} />
            )}
            {(hasPermission(ADMIN_PERMISSIONS.SYSTEM_SUPER_ADMINISTRATOR)
              || hasPermission(ADMIN_PERMISSIONS.APP_UPDATE_VIEW)) && (
              <AdminNavButton title="发布中心" onPress={() => router.push('/admin/releases')} />
            )}
            {hasPermission(ADMIN_PERMISSIONS.OBSERVABILITY_VIEW) && (
              <AdminNavButton title="运行诊断" onPress={() => router.push('/admin/observability')} />
            )}
          </View>
        </View>
      </SectionContent>
    </Screen>
  );
}

function CountyComparison({
  data,
  onSelect,
}: {
  data: WorkOrderCountyComparison[];
  onSelect: (countyOrgUnitId: string) => void;
}) {
  return (
    <Card style={{ gap: 0 }}>
      <Text style={{ fontSize: 18, fontWeight: '900', color: colors.text, paddingBottom: 10 }}>区县数据对比</Text>
      {data.map((item, index) => (
        <Pressable
          key={item.countyOrgUnitId}
          accessibilityRole="button"
          accessibilityLabel={`查看${item.countyName}统计`}
          onPress={() => onSelect(item.countyOrgUnitId)}
          style={({ pressed }) => ({
            minHeight: 72,
            paddingVertical: 11,
            borderTopWidth: index === 0 ? 0 : 1,
            borderTopColor: colors.border,
            backgroundColor: pressed ? colors.controlPressed : colors.controlSurface,
            paddingHorizontal: 12,
            borderRadius: 8,
            opacity: pressed ? 0.72 : 1,
          })}
        >
          <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 8 }}>
            <Text style={{ flex: 1, color: colors.text, fontSize: 16, fontWeight: '900' }}>{item.countyName}</Text>
            <Text style={{ color: colors.text, fontSize: 20, fontWeight: '900' }}>{item.total}</Text>
            <Text style={{ color: colors.muted }}>单</Text>
          </View>
          <Text style={{ color: colors.muted, marginTop: 5 }}>
            在途 {item.active} · 已闭环 {item.closed} · 闭环率 {formatRate(item.resolutionRate)}%
          </Text>
          <Text style={{ color: item.currentSlaBreached > 0 || item.over72Hours > 0 ? slaRiskPresentation('BREACHED').foreground : colors.muted, marginTop: 3 }}>
            当前超时 {item.currentSlaBreached} · 超过72小时 {item.over72Hours}
          </Text>
        </Pressable>
      ))}
    </Card>
  );
}

function AdminNavButton({ title, onPress }: { title: string; onPress: () => void }) {
  return (
    <View style={{ flexGrow: 1, flexBasis: '47%' }}>
      <AppButton title={title} onPress={onPress} variant="secondary" />
    </View>
  );
}

function MetricCard({ label, value, tone }: { label: string; value: number | string; tone?: PresentationTone }) {
  return (
    <Card style={{ flexGrow: 1, flexBasis: '47%', gap: 8, padding: 16 }}>
      <Text style={{ color: colors.muted, fontSize: 14 }}>{label}</Text>
      <Text style={{ color: value === 0 || value === '0' ? colors.text : tone?.foreground || colors.text, fontSize: 28, fontWeight: '600', fontVariant: ['tabular-nums'] }}>{value}</Text>
    </Card>
  );
}

function TypeStatisticsCard({
  statistics,
  fallbackName,
  tone,
}: {
  statistics?: WorkOrderTypeStatistics;
  fallbackName: string;
  tone: PresentationTone;
}) {
  const total = statistics?.total ?? 0;
  const handled = (statistics?.resolved ?? 0) + (statistics?.closed ?? 0);
  const rate = total > 0 ? (handled / total) * 100 : 0;

  return (
    <Card style={{ gap: 14 }}>
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 10 }}>
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: 20, color: colors.text, fontWeight: '900' }}>
            {statistics?.reportTypeName || fallbackName}
          </Text>
          <Text style={{ color: colors.muted }}>点击进入统一查询并筛选此类型</Text>
        </View>
        <Text style={{ color: colors.text, fontSize: 34, fontWeight: '900' }}>{total}</Text>
      </View>
      <View style={{ height: 8, borderRadius: 999, backgroundColor: colors.surfaceAlt }}>
        <View
          style={{
            width: `${Math.min(100, Math.max(0, rate))}%`,
            height: 8,
            borderRadius: 999,
            backgroundColor: tone.foreground,
          }}
        />
      </View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <InlineMetric label="待处理" value={statistics?.pending ?? 0} />
        <InlineMetric label="待验收" value={statistics?.resolved ?? 0} />
        <InlineMetric label="已闭环" value={statistics?.closed ?? 0} />
        <InlineMetric label="处理率" value={`${formatRate(rate)}%`} />
      </View>
    </Card>
  );
}

function InlineMetric({ label, value }: { label: string; value: number | string }) {
  return (
    <View style={{ minWidth: '47%', borderRadius: 8, backgroundColor: colors.surfaceAlt, padding: 10, gap: 4 }}>
      <Text style={{ color: colors.muted }}>{label}</Text>
      <Text style={{ color: colors.text, fontSize: 20, fontWeight: '900' }}>{value}</Text>
    </View>
  );
}

function formatRate(value: number) {
  return Number.isFinite(value) ? value.toFixed(1) : '0.0';
}

function dailyBarPercent(total: number, items: { total: number }[]) {
  const maxTotal = Math.max(1, ...items.map((item) => item.total));
  return Math.round((total / maxTotal) * 100);
}

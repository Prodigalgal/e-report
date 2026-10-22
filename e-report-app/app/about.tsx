import Constants from 'expo-constants';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';

import { currentApiBaseUrl, currentApiEndpoints } from '@/api/http';
import { APP_VERSION_NAME, APP_VERSION_CODE, APP_BUSINESS_VERSION, APP_RUNTIME_VERSION, APP_PACKAGE_ID } from '@/api/app-update-api';
import { OtaUpdateCard } from '@/features/app-updates/ota-update-card';
import { resolveUserAccessSummary } from '@/auth/admin-access';
import { useAuth } from '@/auth/auth-provider';
import { submitManualAppDiagnostic } from '@/observability/telemetry';
import { AppButton } from '@/ui/app-button';
import { BrandMark } from '@/ui/brand';
import { Card } from '@/ui/card';
import { Screen } from '@/ui/screen';
import { colors } from '@/ui/theme';

export default function AboutScreen() {
  const router = useRouter();
  const { currentUser } = useAuth();
  const expoConfig = Constants.expoConfig;
  const endpoints = currentApiEndpoints();
  const activeLine = currentApiBaseUrl() === endpoints.fallbackApiBaseUrl ? '备用线路' : '主线路';
  const [diagnosticSubmitting, setDiagnosticSubmitting] = useState(false);
  const [diagnosticResult, setDiagnosticResult] = useState('');

  const submitDiagnostic = async () => {
    setDiagnosticSubmitting(true);
    setDiagnosticResult('');
    try {
      const result = await submitManualAppDiagnostic();
      setDiagnosticResult(result.uploaded ? '诊断信息已上传。' : `网络暂不可用，诊断信息已保存在本机队列（${result.queued}条）。`);
    } catch {
      setDiagnosticResult('诊断信息已保存在本机，将在网络恢复后自动上传。');
    } finally {
      setDiagnosticSubmitting(false);
    }
  };

  return (
    <Screen>
      <Card style={{ gap: 12, alignItems: 'center' }}>
        <BrandMark size={86} />
        <View style={{ alignItems: 'center', gap: 4 }}>
          <Text style={{ color: colors.text, fontSize: 24, fontWeight: '900' }}>
            {expoConfig?.name || '网络运维管理'}
          </Text>
          <Text style={{ color: colors.muted }}>网络现场工单应用</Text>
        </View>
      </Card>

      <Card style={{ gap: 12 }}>
        <Text style={{ color: colors.text, fontSize: 20, fontWeight: '900' }}>版本信息</Text>
        <InfoRow label="业务版本" value={APP_BUSINESS_VERSION} />
        <InfoRow label="原生基础包" value={`${APP_VERSION_NAME} (${APP_VERSION_CODE})`} />
        <InfoRow label="运行时版本" value={APP_RUNTIME_VERSION} />
        <InfoRow label="应用包名" value={APP_PACKAGE_ID} />
        <InfoRow label="更新通道" value={formatUpdateChannel(process.env.EXPO_PUBLIC_APP_UPDATE_CHANNEL)} />
        <Text style={{ color: colors.muted, lineHeight: 20 }}>当前为原生版，仅接收原生版升级。切换 Lite 请先卸载本应用，再下载安装 Lite。</Text>
      </Card>

      <OtaUpdateCard />

      <Card style={{ gap: 12 }}>
        <Text style={{ color: colors.text, fontSize: 20, fontWeight: '900' }}>服务线路</Text>
        <InfoRow label="当前环境" value={endpoints.label} />
        <InfoRow label="当前线路" value={activeLine} />
        <InfoRow label="主线路" value={endpoints.apiBaseUrl} />
        <InfoRow label="备用线路" value={endpoints.fallbackApiBaseUrl || '未配置'} />
      </Card>

      <Card style={{ gap: 12 }}>
        <Text style={{ color: colors.text, fontSize: 20, fontWeight: '900' }}>当前账号</Text>
        <InfoRow label="用户名" value={currentUser?.username || '未登录'} />
        <InfoRow label="姓名" value={currentUser?.name || '未登录'} />
        <InfoRow label="权限摘要" value={resolveUserAccessSummary(currentUser)} />
      </Card>

      <Card style={{ gap: 12 }}>
        <Text style={{ color: colors.text, fontSize: 20, fontWeight: '900' }}>运行诊断</Text>
        <AppButton title={diagnosticSubmitting ? '正在上传' : '上传诊断信息'} onPress={submitDiagnostic} disabled={diagnosticSubmitting} />
        {diagnosticSubmitting && <ActivityIndicator color={colors.primary} />}
        {!!diagnosticResult && <Text selectable style={{ color: colors.muted, lineHeight: 20 }}>{diagnosticResult}</Text>}
      </Card>

      <AppButton title="返回首页" onPress={() => router.replace('/')} variant="secondary" />
    </Screen>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={{ gap: 4 }}>
      <Text style={{ color: colors.muted, fontSize: 13, fontWeight: '700' }}>{label}</Text>
      <Text selectable style={{ color: colors.text, fontSize: 15, lineHeight: 21 }}>{value}</Text>
    </View>
  );
}

function formatValue(value: string | number | null | undefined) {
  return value === undefined || value === null || value === '' ? '未知' : String(value);
}

function formatUpdateChannel(channel: string | undefined) {
  if (!channel || channel === 'stable') return '稳定版';
  if (channel === 'beta') return '测试版';
  return channel;
}

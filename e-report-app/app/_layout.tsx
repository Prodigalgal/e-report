import 'react-native-gesture-handler';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';

import { AuthProvider } from '@/auth/auth-provider';
import { initializeApiEnvironment } from '@/config/api-environment';
import { AppUpdateGate } from '@/features/app-updates/app-update-gate';
import { AuthenticatedAppStack } from '@/navigation/authenticated-app-stack';
import { AppMenuProvider } from '@/navigation/app-menu-state';
import { NetworkStatusProvider } from '@/network/network-status-provider';
import { AppTelemetryErrorBoundary, AppTelemetryLifecycle } from '@/observability/app-telemetry-boundary';
import { AppButton } from '@/ui/app-button';
import { BrandLoading } from '@/ui/brand';
import { Screen } from '@/ui/screen';
import { colors } from '@/ui/theme';

export default function RootLayout() {
  const [environmentReady, setEnvironmentReady] = useState(false);
  const [environmentError, setEnvironmentError] = useState('');
  const [environmentRetry, setEnvironmentRetry] = useState(0);
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 1000 * 60 * 2,
        gcTime: 1000 * 60 * 10,
        refetchOnMount: false,
        refetchOnWindowFocus: false,
        retry: 1,
      },
      mutations: {
        retry: 0,
      },
    },
  }));

  useEffect(() => {
    let active = true;
    void initializeApiEnvironment()
      .then(() => {
        if (active) setEnvironmentReady(true);
      })
      .catch((error: unknown) => {
        if (active) {
          setEnvironmentError(error instanceof Error ? error.message : '读取服务环境失败。');
        }
      });
    return () => { active = false; };
  }, [environmentRetry]);

  if (environmentError) {
    return (
      <Screen contentStyle={{ flex: 1, justifyContent: 'center', gap: 16 }}>
        <View style={{ gap: 8 }}>
          <Text style={{ color: colors.text, fontSize: 20, fontWeight: '800' }}>无法读取服务环境</Text>
          <Text selectable style={{ color: colors.muted }}>{environmentError}</Text>
        </View>
        <AppButton
          title="重试"
          onPress={() => {
            setEnvironmentReady(false);
            setEnvironmentError('');
            setEnvironmentRetry((attempt) => attempt + 1);
          }}
        />
      </Screen>
    );
  }
  if (!environmentReady) {
    return <BrandLoading message="正在恢复服务环境配置" />;
  }

  return (
    <AppTelemetryErrorBoundary>
      <AppTelemetryLifecycle />
      <QueryClientProvider client={queryClient}>
        <NetworkStatusProvider>
          <AuthProvider>
            <AppMenuProvider>
              <AppUpdateGate />
              <AuthenticatedAppStack />
            </AppMenuProvider>
          </AuthProvider>
        </NetworkStatusProvider>
      </QueryClientProvider>
    </AppTelemetryErrorBoundary>
  );
}

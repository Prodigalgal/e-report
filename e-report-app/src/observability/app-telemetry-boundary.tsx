import { usePathname } from 'expo-router';
import React, { useEffect } from 'react';
import { Text, View } from 'react-native';

import { AppButton } from '@/ui/app-button';
import { colors } from '@/ui/theme';
import { captureAppTelemetryError, initializeAppTelemetry, setAppTelemetryRoute } from './telemetry';

type BoundaryProps = { children: React.ReactNode };
type BoundaryState = { hasError: boolean };

export class AppTelemetryErrorBoundary extends React.Component<BoundaryProps, BoundaryState> {
  state: BoundaryState = { hasError: false };

  static getDerivedStateFromError(): BoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    captureAppTelemetryError(error, {
      category: 'UI',
      eventName: 'REACT_RENDER_ERROR',
      fatal: true,
      attributes: { componentStack: errorInfo.componentStack },
    });
  }

  render() {
    if (!this.state.hasError) return this.props.children;
    return (
      <View style={{ flex: 1, justifyContent: 'center', padding: 20, gap: 14, backgroundColor: colors.background }}>
        <Text style={{ color: colors.danger, fontSize: 20, fontWeight: '900' }}>当前页面运行异常</Text>
        <Text style={{ color: colors.muted, lineHeight: 21 }}>诊断信息已保存，请重新打开应用。</Text>
        <AppButton title="重新加载" onPress={() => this.setState({ hasError: false })} />
      </View>
    );
  }
}

export function AppTelemetryLifecycle() {
  const pathname = usePathname();

  useEffect(() => {
    void initializeAppTelemetry();
  }, []);

  useEffect(() => {
    setAppTelemetryRoute(pathname);
  }, [pathname]);

  return null;
}

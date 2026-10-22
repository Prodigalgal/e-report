import { Stack, useGlobalSearchParams, usePathname, useRouter, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';

import { useAuth } from '@/auth/auth-provider';
import { resolveAuthReturnPath, resolveUnauthenticatedReturnPath } from '@/navigation/auth-return-path';
import { AppHeaderTitle } from '@/navigation/app-header-title';
import { BrandLoading } from '@/ui/brand';
import { colors } from '@/ui/theme';

export function AuthenticatedAppStack() {
  const router = useRouter();
  const pathname = usePathname();
  const segments = useSegments();
  const { returnTo } = useGlobalSearchParams<{ returnTo?: string | string[] }>();
  const { currentUser, explicitlyLoggedOut, isAuthenticated, isAdmin, isLoading } = useAuth();
  const isLoginRoute = segments[0] === 'login';
  const isAdminRoute = segments[0] === 'admin';
  const isProfileRoute = segments[0] === 'profile';

  useEffect(() => {
    if (isLoading) return;

    if (!isAuthenticated && !isLoginRoute) {
      router.replace({
        pathname: '/login',
        params: { returnTo: resolveUnauthenticatedReturnPath(pathname, explicitlyLoggedOut) },
      });
      return;
    }

    if (isAuthenticated && isLoginRoute) {
      router.replace(currentUser?.passwordChangeRequired ? '/profile' : resolveAuthReturnPath(returnTo));
      return;
    }

    if (isAuthenticated && currentUser?.passwordChangeRequired && !isProfileRoute) {
      router.replace('/profile');
      return;
    }

    if (isAuthenticated && isAdminRoute && !isAdmin) {
      router.replace('/');
    }
  }, [currentUser?.passwordChangeRequired, explicitlyLoggedOut, isAdmin, isAdminRoute, isAuthenticated, isLoading, isLoginRoute, isProfileRoute, pathname, returnTo, router]);

  if (isLoading) {
    return <BrandLoading message="正在检查登录状态" />;
  }

  if ((!isAuthenticated && !isLoginRoute) || (isAuthenticated && isLoginRoute)) {
    return <BrandLoading message={isAuthenticated ? '正在进入工作台' : '正在前往登录页'} />;
  }

  if (isAuthenticated && isAdminRoute && !isAdmin) {
    return <BrandLoading message="当前账号不可进入管理后台" />;
  }

  if (isAuthenticated && currentUser?.passwordChangeRequired && !isProfileRoute) {
    return <BrandLoading message="正在前往密码设置" />;
  }

  return (
    <>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.surface },
          headerTintColor: colors.text,
          headerTitleStyle: { fontWeight: '600' },
          headerShadowVisible: false,
          contentStyle: { backgroundColor: colors.background },
        }}
      >
        <Stack.Screen name="index" options={{ headerTitle: () => <AppHeaderTitle /> }} />
        <Stack.Screen name="login" options={{ headerShown: false }} />
        <Stack.Screen name="report/index" options={{ title: '现场报单' }} />
        <Stack.Screen name="report/[type]" options={{ title: '工单报单' }} />
        <Stack.Screen name="work-orders/index" options={{ title: '工单查询' }} />
        <Stack.Screen name="work-orders/[id]" options={{ title: '工单详情' }} />
        <Stack.Screen name="search" options={{ title: '工单查询' }} />
        <Stack.Screen name="processing/index" options={{ title: '工单查询' }} />
        <Stack.Screen name="processing/[id]" options={{ title: '处理回单' }} />
        <Stack.Screen name="profile" options={{ title: '个人信息' }} />
        <Stack.Screen name="about" options={{ title: '关于应用' }} />
        <Stack.Screen name="admin/index" options={{ title: '管理后台' }} />
        <Stack.Screen name="admin/work-orders/index" options={{ title: '工单查询' }} />
        <Stack.Screen name="admin/work-orders/[type]" options={{ title: '工单查询' }} />
        <Stack.Screen name="admin/users/index" options={{ title: '用户管理' }} />
        <Stack.Screen name="admin/users/[userId]" options={{ title: '用户详情' }} />
        <Stack.Screen name="admin/authorization-profiles" options={{ title: '岗位权限模板' }} />
        <Stack.Screen name="admin/configs" options={{ title: '系统配置' }} />
        <Stack.Screen name="admin/releases" options={{ title: '发布中心' }} />
        <Stack.Screen name="admin/sla-policies" options={{ title: '时限策略' }} />
        <Stack.Screen name="admin/email-deliveries" options={{ title: '邮件发送台账' }} />
        <Stack.Screen name="admin/observability/index" options={{ title: '运行诊断' }} />
        <Stack.Screen name="admin/observability/[id]" options={{ title: '诊断详情' }} />
      </Stack>
    </>
  );
}

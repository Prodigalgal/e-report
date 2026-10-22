import { useMutation, useQuery } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ActivityIndicator, Pressable, Text, View, useWindowDimensions } from 'react-native';

import { formatApiErrorMessage } from '@/api/http';
import { getCurrentApiEnvironment } from '@/config/api-environment';
import { apiEndpointsFor, type ApiEnvironment } from '@/config/public-endpoints';
import { fetchAuthChallenge } from '@/auth/auth-api';
import { useAuth } from '@/auth/auth-provider';
import { LoginEnvironmentSettings } from '@/features/auth/login-environment-settings';
import { resolveAuthReturnPath } from '@/navigation/auth-return-path';
import { AppButton } from '@/ui/app-button';
import { BrandLoading, BrandMark } from '@/ui/brand';
import { FormErrorMessage } from '@/ui/form-error-message';
import { Screen } from '@/ui/screen';
import { TextField } from '@/ui/text-field';
import { colors, uiLayout } from '@/ui/theme';

export default function LoginScreen() {
  const router = useRouter();
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const { returnTo } = useLocalSearchParams<{ returnTo?: string | string[] }>();
  const { login, isAuthenticated, isLoading, currentUser } = useAuth();
  const [apiEnvironment, setApiEnvironment] = useState<ApiEnvironment>(getCurrentApiEnvironment());
  const [environmentSettingsVisible, setEnvironmentSettingsVisible] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [captchaAnswer, setCaptchaAnswer] = useState('');
  const [challengeUsername, setChallengeUsername] = useState('');
  const [loginStage, setLoginStage] = useState<'IDLE' | 'SECURITY_CHECK' | 'AUTHENTICATING' | 'SAVING_SESSION' | 'ENTERING'>('IDLE');

  useEffect(() => {
    const normalizedUsername = username.trim();
    const timeout = setTimeout(() => setChallengeUsername(normalizedUsername), 350);
    return () => clearTimeout(timeout);
  }, [username]);

  const challengeQuery = useQuery({
    queryKey: ['auth-challenge', apiEnvironment, challengeUsername],
    queryFn: () => fetchAuthChallenge(challengeUsername),
    enabled: !!challengeUsername,
    retry: 2,
    retryDelay: attemptIndex => Math.min(1500 * (attemptIndex + 1), 5000),
    staleTime: 0,
    refetchOnMount: true,
  });

  const loginMutation = useMutation({
    mutationFn: () => {
      if (!challengeQuery.data) {
        throw new Error('安全验证还没有加载完成。');
      }
      setLoginStage('SECURITY_CHECK');
      return login(username.trim(), password, captchaAnswer.trim(), challengeQuery.data, setLoginStage);
    },
    onSuccess: user => {
      setLoginStage('ENTERING');
      router.replace(user.passwordChangeRequired ? '/profile' : resolveAuthReturnPath(returnTo));
    },
    onError: () => {
      setLoginStage('IDLE');
      setCaptchaAnswer('');
      challengeQuery.refetch();
    },
  });

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace(currentUser?.passwordChangeRequired ? '/profile' : resolveAuthReturnPath(returnTo));
    }
  }, [currentUser?.passwordChangeRequired, isAuthenticated, isLoading, returnTo, router]);

  if (isLoading) {
    return <BrandLoading message="正在检查登录状态" />;
  }

  const compact = width < 420;
  const challengeLoading = challengeQuery.isLoading || (!challengeQuery.data && challengeQuery.isFetching);

  return (
    <Screen
      maxWidth={520}
      contentStyle={{
        gap: 24,
        justifyContent: 'center',
        minHeight: '100%',
        paddingTop: insets.top + uiLayout.controlHeight + uiLayout.pageGutter,
        paddingBottom: Math.max(insets.bottom, 24),
        position: 'relative',
      }}
    >
      <Pressable
        testID="login-environment-settings-button"
        accessibilityRole="button"
        accessibilityLabel="登录环境设置"
        accessibilityHint="切换生产或测试环境，并修改服务地址"
        accessibilityState={{ disabled: isAuthenticated }}
        onPress={() => setEnvironmentSettingsVisible(true)}
        disabled={isAuthenticated}
        style={({ pressed }) => ({
          position: 'absolute',
          top: insets.top + 8,
          right: uiLayout.pageGutter,
          zIndex: 2,
          width: uiLayout.controlHeight,
          height: uiLayout.controlHeight,
          alignItems: 'center',
          justifyContent: 'center',
          borderWidth: 1,
          borderColor: colors.controlBorder,
          borderRadius: 8,
          backgroundColor: colors.controlSurface,
          opacity: isAuthenticated ? 0.5 : pressed ? 0.75 : 1,
        })}
      >
        <Text style={{ color: colors.primary, fontSize: 23, fontWeight: '700' }}>⚙</Text>
      </Pressable>

      <View style={{ alignItems: 'center', gap: 12 }}>
        <BrandMark size={88} />
        <Text selectable style={{ color: colors.ink, fontSize: 20, lineHeight: 28, includeFontPadding: false, fontWeight: '800' }}>网络运维管理</Text>
      </View>

      <View
        testID="login-panel"
        style={{
          gap: uiLayout.pageGap,
          borderRadius: uiLayout.cornerRadius,
          borderWidth: 1,
          borderColor: colors.border,
          backgroundColor: colors.surface,
          padding: compact ? uiLayout.cardPadding : uiLayout.widePageGutter,
          boxShadow: '0 18px 45px rgba(15, 23, 42, 0.08)',
        }}
      >
        <View style={{ gap: 6 }}>
          <Text selectable style={{ fontSize: compact ? 28 : 30, fontWeight: '800', color: colors.ink }}>欢迎回来</Text>
          <Text selectable style={{ color: colors.muted, fontSize: 15 }}>使用系统账号继续</Text>
          <Text selectable style={{ color: apiEnvironment === 'test' ? colors.primary : colors.muted, fontSize: 12 }}>
            {`当前连接：${apiEndpointsFor(apiEnvironment).label}`}
          </Text>
        </View>

        <TextField
          label="用户名"
          value={username}
          onChangeText={(value) => {
            setUsername(value);
            setCaptchaAnswer('');
          }}
          autoCapitalize="none"
          autoCorrect={false}
          autoComplete="username"
          textContentType="username"
          returnKeyType="next"
          required
        />
        <TextField
          label="密码"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          autoComplete="current-password"
          textContentType="password"
          returnKeyType="next"
          required
        />

        {challengeQuery.data?.captchaRequired && <View style={{ gap: 9 }}>
          <Text selectable style={{ fontSize: 14, fontWeight: '700', color: colors.text }}>数学验证码</Text>
          <View
            style={{
              minHeight: 58,
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
              paddingHorizontal: 14,
              borderRadius: 8,
              borderWidth: 1,
              borderColor: colors.border,
              backgroundColor: colors.surfaceAlt,
            }}
          >
            {challengeLoading ? (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                <ActivityIndicator color={colors.primary} />
                <Text selectable style={{ color: colors.muted }}>正在获取验证码</Text>
              </View>
            ) : (
              <Text selectable style={{ color: colors.ink, fontSize: 22, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
                {challengeQuery.data?.captchaQuestion || '加载失败'}
              </Text>
            )}
            <View style={{ minWidth: 112 }}>
              <AppButton
                title={challengeQuery.isFetching ? '正在刷新' : '刷新验证码'}
                onPress={() => challengeQuery.refetch()}
                variant="ghost"
                disabled={challengeQuery.isFetching}
              />
            </View>
          </View>
        </View>}

        {challengeQuery.data?.captchaRequired && <TextField
          label="验证码答案"
          value={captchaAnswer}
          onChangeText={setCaptchaAnswer}
          keyboardType="number-pad"
          returnKeyType="done"
          onSubmitEditing={() => {
            if (username.trim() && password && captchaAnswer.trim() && challengeQuery.data && !loginMutation.isPending) {
              loginMutation.mutate();
            }
          }}
          required
        />}

        {challengeQuery.data?.mode === 'COOLDOWN' && (
          <FormErrorMessage>
            {`登录保护已启用，请在${challengeQuery.data.retryAfterSeconds || 1}秒后重试。`}
          </FormErrorMessage>
        )}

        {!!challengeQuery.error && !challengeQuery.isFetching && (
          <FormErrorMessage>{formatApiErrorMessage(challengeQuery.error, '安全验证加载失败，请检查网络后重试。')}</FormErrorMessage>
        )}
        {!!loginMutation.error && (
          <FormErrorMessage>{formatApiErrorMessage(loginMutation.error, '登录失败，请检查账号、密码或安全验证后重试。')}</FormErrorMessage>
        )}

        <AppButton
          title={loginStageLabel(loginStage, challengeLoading)}
          onPress={() => loginMutation.mutate()}
          disabled={!username.trim()
            || !password
            || !challengeQuery.data
            || challengeQuery.data.mode === 'COOLDOWN'
            || (challengeQuery.data.captchaRequired && !captchaAnswer.trim())
            || loginMutation.isPending}
          loading={loginMutation.isPending || challengeLoading}
        />
      </View>

      <Text selectable style={{ textAlign: 'center', color: colors.placeholder, fontSize: 13 }}>
        仅限已授权账号访问
      </Text>

      <LoginEnvironmentSettings
        visible={environmentSettingsVisible}
        currentEnvironment={apiEnvironment}
        onClose={() => setEnvironmentSettingsVisible(false)}
        onSaved={(environment) => {
          setApiEnvironment(environment);
          setUsername('');
          setPassword('');
          setCaptchaAnswer('');
          setChallengeUsername('');
        }}
      />
    </Screen>
  );
}

function loginStageLabel(
  stage: 'IDLE' | 'SECURITY_CHECK' | 'AUTHENTICATING' | 'SAVING_SESSION' | 'ENTERING',
  challengeLoading: boolean,
) {
  if (challengeLoading) return '正在准备安全验证';
  if (stage === 'SECURITY_CHECK') return '正在进行安全校验';
  if (stage === 'AUTHENTICATING') return '正在验证账号';
  if (stage === 'SAVING_SESSION') return '正在保存登录状态';
  if (stage === 'ENTERING') return '正在进入系统';
  return '登录';
}

import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';

import { formatApiErrorMessage, saveApiEnvironmentSettings } from '@/api/http';
import {
  API_ENDPOINTS,
  apiEndpointsFor,
  normalizeApiBaseUrl,
  type ApiEnvironment,
} from '@/config/public-endpoints';
import { clearTelemetryQueue } from '@/observability/telemetry-queue';
import { AppButton } from '@/ui/app-button';
import { EditModal } from '@/ui/edit-modal';
import { FormErrorMessage } from '@/ui/form-error-message';
import { TextField } from '@/ui/text-field';
import { colors, uiLayout } from '@/ui/theme';

const API_ENVIRONMENTS: readonly ApiEnvironment[] = ['production', 'test'];

type LoginEnvironmentSettingsProps = {
  visible: boolean;
  currentEnvironment: ApiEnvironment;
  onClose: () => void;
  onSaved: (environment: ApiEnvironment) => void;
};

export function LoginEnvironmentSettings({
  visible,
  currentEnvironment,
  onClose,
  onSaved,
}: LoginEnvironmentSettingsProps) {
  const queryClient = useQueryClient();
  const [targetEnvironment, setTargetEnvironment] = useState(currentEnvironment);
  const [apiBaseUrlDrafts, setApiBaseUrlDrafts] = useState<Record<ApiEnvironment, string>>(() => ({
    production: apiEndpointsFor('production').apiBaseUrl,
    test: apiEndpointsFor('test').apiBaseUrl,
  }));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!visible) return;
    setTargetEnvironment(currentEnvironment);
    setApiBaseUrlDrafts({
      production: apiEndpointsFor('production').apiBaseUrl,
      test: apiEndpointsFor('test').apiBaseUrl,
    });
    setError('');
  }, [currentEnvironment, visible]);

  const submit = () => {
    setError('');

    let apiBaseUrlOverrides: Partial<Record<ApiEnvironment, string>>;
    try {
      apiBaseUrlOverrides = {
        production: normalizeApiBaseUrl(apiBaseUrlDrafts.production),
        test: normalizeApiBaseUrl(apiBaseUrlDrafts.test),
      };
    } catch (validationError) {
      setError(formatApiErrorMessage(validationError, '请检查生产和测试环境的服务地址。'));
      return;
    }

    const effectiveApiBaseUrls = {
      production: apiBaseUrlOverrides.production || API_ENDPOINTS.production.apiBaseUrl,
      test: apiBaseUrlOverrides.test || API_ENDPOINTS.test.apiBaseUrl,
    };
    const productionEndpointChanged = effectiveApiBaseUrls.production !== apiEndpointsFor('production').apiBaseUrl;
    const environmentChanged = targetEnvironment !== currentEnvironment;
    const mustConfirmProduction = targetEnvironment === 'production'
      && (environmentChanged || productionEndpointChanged);

    if (environmentChanged || mustConfirmProduction) {
      const label = apiEndpointsFor(targetEnvironment).label;
      const message = targetEnvironment === 'production'
        ? '即将连接生产环境，后续操作会影响真实业务数据。'
        : '即将连接测试环境，测试数据与生产环境隔离。';
      Alert.alert(`确认连接${label}`, message, [
        { text: '取消', style: 'cancel' },
        {
          text: '确认并连接',
          style: targetEnvironment === 'production' ? 'destructive' : 'default',
          onPress: () => { void applySettings(apiBaseUrlOverrides); },
        },
      ]);
      return;
    }

    void applySettings(apiBaseUrlOverrides);
  };

  const applySettings = async (apiBaseUrlOverrides: Partial<Record<ApiEnvironment, string>>) => {
    setSaving(true);
    setError('');
    try {
      const changedEndpointEnvironments = API_ENVIRONMENTS.filter((environment) => {
        const nextApiBaseUrl = apiBaseUrlOverrides[environment]
          || API_ENDPOINTS[environment].apiBaseUrl;
        return nextApiBaseUrl !== apiEndpointsFor(environment).apiBaseUrl;
      });
      await Promise.all(changedEndpointEnvironments.map((environment) => clearTelemetryQueue(environment)));

      const changed = await saveApiEnvironmentSettings(targetEnvironment, apiBaseUrlOverrides);
      if (changed) queryClient.clear();
      onSaved(targetEnvironment);
      onClose();
    } catch (saveError) {
      setError(formatApiErrorMessage(saveError, '无法保存环境设置，请检查网络后重试。'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <EditModal
      visible={visible}
      title="登录环境设置"
      primaryActionLabel={`保存并连接${apiEndpointsFor(targetEnvironment).label}`}
      onPrimaryAction={submit}
      onClose={onClose}
      working={saving}
      primaryDisabled={saving}
    >
      <View style={{ gap: 16 }}>
        <View style={{ gap: 6 }}>
          <Text style={{ color: colors.text, fontSize: 14, fontWeight: '700' }}>连接环境</Text>
          <View style={{ flexDirection: 'row', gap: 10 }}>
            {API_ENVIRONMENTS.map((environment) => (
              <EnvironmentChoice
                key={environment}
                environment={environment}
                selected={targetEnvironment === environment}
                disabled={saving}
                onPress={() => setTargetEnvironment(environment)}
              />
            ))}
          </View>
        </View>

        {API_ENVIRONMENTS.map((environment) => (
          <ApiBaseUrlField
            key={environment}
            environment={environment}
            value={apiBaseUrlDrafts[environment]}
            disabled={saving}
            onChange={(value) => setApiBaseUrlDrafts((previous) => ({ ...previous, [environment]: value }))}
            onRestoreDefault={() => setApiBaseUrlDrafts((previous) => ({
              ...previous,
              [environment]: API_ENDPOINTS[environment].apiBaseUrl,
            }))}
          />
        ))}

        <Text selectable style={{ color: targetEnvironment === 'production' ? colors.danger : colors.muted, fontSize: 13, lineHeight: 19 }}>
          {targetEnvironment === 'production'
            ? '生产环境会操作真实业务数据，请确认连接地址无误。'
            : '测试环境用于验收，数据与生产环境隔离。'}
        </Text>
        {!!error && <FormErrorMessage>{error}</FormErrorMessage>}
      </View>
    </EditModal>
  );
}

function ApiBaseUrlField({
  environment,
  value,
  disabled,
  onChange,
  onRestoreDefault,
}: {
  environment: ApiEnvironment;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
  onRestoreDefault: () => void;
}) {
  const label = apiEndpointsFor(environment).label;
  return (
    <View style={{ gap: 6 }}>
      <TextField
        label={`${label} API 地址`}
        value={value}
        onChangeText={onChange}
        placeholder="https://example.com/api"
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="url"
        autoComplete="off"
        maxLength={512}
        editable={!disabled}
        helperText="仅支持 HTTPS；清空后使用应用内置地址，自定义地址不启用内置备用线路。"
      />
      <AppButton title={`恢复${label}默认地址`} onPress={onRestoreDefault} disabled={disabled} variant="secondary" />
    </View>
  );
}

function EnvironmentChoice({
  environment,
  selected,
  disabled,
  onPress,
}: {
  environment: ApiEnvironment;
  selected: boolean;
  disabled: boolean;
  onPress: () => void;
}) {
  const label = apiEndpointsFor(environment).label;
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ checked: selected, disabled }}
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => ({
        flex: 1,
        minHeight: uiLayout.controlHeight,
        alignItems: 'center',
        justifyContent: 'center',
        borderWidth: 1,
        borderColor: selected ? colors.primary : colors.controlBorder,
        borderRadius: 8,
        backgroundColor: selected ? colors.primary : colors.controlSurface,
        opacity: disabled && !selected ? 0.55 : pressed ? 0.82 : 1,
      })}
    >
      <Text style={{ color: selected ? '#fff' : colors.text, fontWeight: '700' }}>{label}</Text>
    </Pressable>
  );
}

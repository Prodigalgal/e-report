import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Modal, Platform, ScrollView, Text, View } from 'react-native';

import { APP_VERSION_CODE, APP_VERSION_NAME, checkAppUpdate } from '@/api/app-update-api';
import {
  downloadVerifyAndInstallAppUpdate,
  friendlyAppUpdateError,
  openUnknownSourceSettings,
  type AppUpdateProgress,
} from '@/features/app-updates/app-update-installer';
import type { AppUpdateCheckResponse } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { colors, uiLayout } from '@/ui/theme';

export function AppUpdateGate() {
  const [visibleUpdate, setVisibleUpdate] = useState<AppUpdateCheckResponse | null>(null);
  const [dismissedVersionCode, setDismissedVersionCode] = useState<number | null>(null);
  const [progress, setProgress] = useState<AppUpdateProgress | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [isWorking, setIsWorking] = useState(false);
  const updateQuery = useQuery({
    queryKey: ['app-update-check', APP_VERSION_CODE],
    queryFn: checkAppUpdate,
    enabled: Platform.OS === 'android',
    staleTime: 1000 * 60,
    gcTime: 1000 * 60 * 60,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(2000 * (attemptIndex + 1), 8000),
    refetchOnMount: 'always',
  });
  const refetchUpdate = updateQuery.refetch;

  useEffect(() => {
    if (Platform.OS !== 'android') {
      return undefined;
    }
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        refetchUpdate();
      }
    });
    return () => subscription.remove();
  }, [refetchUpdate]);

  useEffect(() => {
    const update = updateQuery.data;
    if (!update?.updateAvailable || !update.versionCode) {
      return;
    }
    if (!update.forceUpdate && dismissedVersionCode === update.versionCode) {
      return;
    }
    setVisibleUpdate(update);
    setErrorMessage('');
  }, [dismissedVersionCode, updateQuery.data]);

  const releaseNotes = useMemo(() => {
    const rawNotes = visibleUpdate?.releaseNotes?.trim();
    return rawNotes || '管理员发布了新的应用版本，建议尽快升级。';
  }, [visibleUpdate?.releaseNotes]);

  if (!visibleUpdate) {
    return null;
  }

  const downloadPercent = progress ? Math.round(progress.progress * 100) : 0;
  const canDismiss = !visibleUpdate.forceUpdate && !isWorking;

  const dismiss = () => {
    if (!canDismiss) {
      return;
    }
    setDismissedVersionCode(visibleUpdate.versionCode || null);
    setVisibleUpdate(null);
    setProgress(null);
    setErrorMessage('');
  };

  const startUpgrade = async () => {
    setIsWorking(true);
    setErrorMessage('');
    try {
      await downloadVerifyAndInstallAppUpdate(visibleUpdate, setProgress);
      setProgress({ phase: 'install', progress: 1, label: '系统安装器已打开，请按提示完成安装' });
    } catch (error) {
      setErrorMessage(friendlyAppUpdateError(error));
    } finally {
      setIsWorking(false);
    }
  };

  const openInstallSettings = async () => {
    try {
      await openUnknownSourceSettings();
    } catch {
      setErrorMessage('无法打开安装权限设置，请到系统设置中允许本应用安装未知来源应用。');
    }
  };

  return (
    <Modal visible transparent animationType="fade" onRequestClose={dismiss}>
      <View
        style={{
          flex: 1,
          justifyContent: 'center',
          padding: uiLayout.pageGutter,
          backgroundColor: 'rgba(15, 23, 42, 0.48)',
        }}
      >
        <ScrollView
          contentContainerStyle={{ padding: uiLayout.cardPadding, gap: uiLayout.pageGap }}
          style={{
            width: '100%',
            maxWidth: uiLayout.dialogMaxWidth,
            maxHeight: '90%',
            alignSelf: 'center',
            flexGrow: 0,
            borderRadius: uiLayout.cornerRadius,
            borderWidth: 1,
            borderColor: colors.border,
            backgroundColor: colors.surface,
          }}
        >
          <View style={{ gap: 6 }}>
            <Text style={{ fontSize: 22, fontWeight: '900', color: colors.text }}>发现新版本</Text>
            <Text style={{ color: colors.muted, lineHeight: 21 }}>
              当前版本 {APP_VERSION_NAME} ({APP_VERSION_CODE})，最新版本 {visibleUpdate.versionName || visibleUpdate.versionCode} ({visibleUpdate.versionCode})。
            </Text>
            {visibleUpdate.forceUpdate && (
              <Text style={{ color: colors.danger, fontWeight: '800' }}>该版本为强制升级，需要安装后继续使用。</Text>
            )}
          </View>

          <View style={{ gap: 6 }}>
            <Text style={{ color: colors.text, fontWeight: '800' }}>更新内容</Text>
            <Text style={{ color: colors.muted, lineHeight: 21 }}>{releaseNotes}</Text>
          </View>

          {progress && (
            <View style={{ gap: 8 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12 }}>
                <Text style={{ color: colors.text, fontWeight: '800' }}>{progress.label}</Text>
                <Text style={{ color: colors.primary, fontWeight: '900' }}>{downloadPercent}%</Text>
              </View>
              <View style={{ height: 8, borderRadius: 8, backgroundColor: colors.surfaceAlt, overflow: 'hidden' }}>
                <View style={{ width: `${downloadPercent}%`, height: '100%', backgroundColor: colors.primary }} />
              </View>
            </View>
          )}

          {errorMessage && (
            <View style={{ gap: 10 }}>
              <Text style={{ color: colors.danger, lineHeight: 21 }}>{errorMessage}</Text>
              <AppButton title="打开安装权限设置" onPress={openInstallSettings} variant="secondary" />
            </View>
          )}

          <View style={{ gap: 10 }}>
            <AppButton
              title={isWorking ? '正在升级' : '下载并安装'}
              onPress={startUpgrade}
              disabled={isWorking}
            />
            {canDismiss && <AppButton title="稍后再说" onPress={dismiss} variant="secondary" />}
          </View>
        </ScrollView>
      </View>
    </Modal>
  );
}

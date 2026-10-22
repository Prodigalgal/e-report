import * as Updates from 'expo-updates';
import { useState } from 'react';
import { Alert, Text, View } from 'react-native';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { colors } from '@/ui/theme';

export function OtaUpdateCard() {
  const [working, setWorking] = useState(false);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('功能更新在启动时自动下载，下次启动生效。');
  const [failed, setFailed] = useState(false);
  const check = async () => {
    setWorking(true); setFailed(false); setMessage('正在检查并校验功能更新…');
    try {
      const available = await Updates.checkForUpdateAsync();
      if (available.isAvailable || available.isRollBackToEmbedded) {
        const downloaded = await Updates.fetchUpdateAsync();
        const ready = downloaded.isNew || downloaded.isRollBackToEmbedded;
        setPending(ready);
        setMessage(ready ? '更新已下载并通过校验。下次启动生效，也可以现在重启。' : '当前功能已是最新版本。');
      } else { setMessage('当前功能已是最新版本。'); }
    } catch {
      setFailed(true); setMessage('暂时无法获取功能更新，当前版本仍可使用。请检查网络后重试。');
    } finally { setWorking(false); }
  };
  const restart = () => Alert.alert('重启应用以完成更新', '请先保存正在填写的内容，重启将关闭当前页面。', [
    { text: '取消', style: 'cancel' },
    { text: '立即重启', onPress: () => { void Updates.reloadAsync().catch(() => {
      setFailed(true); setMessage('自动重启失败，请完全关闭应用后重新打开。');
    }); } },
  ]);
  return (
    <Card style={{ gap: 12 }}>
      <Text style={{ color: colors.text, fontSize: 20, fontWeight: '900' }}>功能更新</Text>
      <Text style={{ color: colors.muted, lineHeight: 21 }}>
        {Updates.isEnabled
          ? (Updates.isEmbeddedLaunch || Updates.isEmergencyLaunch || !Updates.updateId ? '当前使用 APK 内置功能包' : '当前使用已签名的 OTA 功能包')
          : (__DEV__ ? '开发模式未启用 OTA，正式安装包支持自动更新。' : '当前使用 APK 内置功能包，功能更新暂不可用。')}
      </Text>
      {!!Updates.updateId && <Text selectable style={{ color: colors.muted, fontSize: 12 }}>更新标识：{Updates.updateId}</Text>}
      <Text accessibilityLiveRegion="polite" style={{ color: failed ? colors.danger : colors.muted, lineHeight: 21 }}>{message}</Text>
      {Updates.isEmergencyLaunch && <Text style={{ color: colors.danger }}>更新未能正常启动，系统已恢复到内置功能包。</Text>}
      {Updates.isEnabled && <View style={{ gap: 10 }}>
        <AppButton title={working ? '正在检查更新' : '检查功能更新'} onPress={() => { void check(); }} disabled={working} />
        {pending && <AppButton title="重启并应用更新" onPress={restart} variant="secondary" />}
      </View>}
    </Card>
  );
}

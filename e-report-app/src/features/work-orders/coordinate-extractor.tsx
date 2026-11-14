import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { geocodeAddress, recognizeCoordinate } from '@/api/location-coordinate-api';
import { formatApiErrorMessage } from '@/api/http';
import { startAppTelemetryOperation } from '@/observability/telemetry';
import { AppButton } from '@/ui/app-button';
import { SelectField } from '@/ui/select-field';
import { TextField } from '@/ui/text-field';
import { colors } from '@/ui/theme';

/**
 * 坐标提取器（APP 端）：让无法到达现场的用户也能准确报单。
 *
 * APP 不内嵌网页地图，因此提供两条最切实可用的路径：
 * 1. 粘贴坐标 —— 从其他地图应用复制的坐标，自动识别坐标系并归一化；
 * 2. 地址搜索 —— 输入已知地点名称或地址，由服务端解析为坐标。
 *
 * 关键约束：WGS84 与 GCJ-02 在中国大陆相差数百米，无法自动判定时列出候选由用户确认，
 * 绝不静默选取一个坐标直接提交。
 */

const STANDARD_OPTIONS = [
  { label: '自动识别', value: '' },
  { label: '国际通用坐标（GPS 原始）', value: 'wgs84' },
  { label: '国测局坐标（高德、腾讯）', value: 'gcj02' },
  { label: '百度坐标', value: 'bd09' },
];

type CoordinateExtractorProps = {
  onApply: (payload: { gpsCoordinates: string; location: string }) => void;
};

export function CoordinateExtractor({ onApply }: CoordinateExtractorProps) {
  const operation = startAppTelemetryOperation({
    category: 'LOCATION',
    eventName: 'COORDINATE_EXTRACT',
    operationName: '坐标提取',
    forceCapture: true,
  });

  const [pasteText, setPasteText] = useState('');
  const [standard, setStandard] = useState('');
  const [keyword, setKeyword] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [candidates, setCandidates] = useState<CandidateOption[]>([]);

  const applyCandidate = (candidate: CandidateOption) => {
    onApply({ gpsCoordinates: candidate.gpsCoordinates, location: candidate.address });
    setCandidates([]);
    setMessage(`已填入坐标与地点：${candidate.address || candidate.gpsCoordinates}`);
  };

  const handleRecognize = async () => {
    if (!pasteText.trim()) {
      setMessage('请先粘贴从其他地图复制的坐标。');
      return;
    }
    setBusy(true);
    setMessage('');
    setCandidates([]);
    try {
      const response = await recognizeCoordinate(pasteText.trim(), standard || undefined);
      const data = response;
      if (!data.recognized) {
        operation.failure(new Error(data.message || '坐标识别失败'), { failureStage: 'RECOGNIZE' });
        setMessage(data.message || '未能识别坐标，请检查格式。');
        return;
      }

      const options = (data.candidates || []).map(candidate => ({
        key: candidate.standard,
        label: candidate.standardDisplayName,
        address: candidate.address || '',
        gpsCoordinates: candidate.gpsCoordinates,
        preferred: candidate.preferred,
        note: typeof candidate.offsetFromPreferredMeters === 'number' && candidate.offsetFromPreferredMeters > 1
          ? `与首选相差约 ${Math.round(candidate.offsetFromPreferredMeters)} 米`
          : '',
      }));

      // 只有一个候选或已明确坐标系时直接采用，避免让用户做无意义的选择。
      if (options.length <= 1) {
        const only = options[0];
        if (only) {
          applyCandidate(only);
        }
        return;
      }

      if (data.confident && data.gpsCoordinates && data.address) {
        applyCandidate({
          key: data.standard || 'auto',
          label: data.standardDisplayName || '自动识别',
          address: data.address,
          gpsCoordinates: data.gpsCoordinates,
          preferred: true,
          note: '',
        });
        return;
      }

      setCandidates(options);
      setMessage(data.message || '不同坐标系相差可达数百米，请选择与目标地点相符的一项。');
      operation.success({ candidateCount: options.length, confident: false });
    } catch (error) {
      operation.failure(error, { failureStage: 'RECOGNIZE' });
      setMessage(formatApiErrorMessage(error, '坐标识别失败，请稍后重试。'));
    } finally {
      setBusy(false);
    }
  };

  const handleGeocode = async () => {
    if (!keyword.trim()) {
      setMessage('请先输入地址或地点名称。');
      return;
    }
    setBusy(true);
    setMessage('');
    setCandidates([]);
    try {
      const data = await geocodeAddress(keyword.trim());
      if (!data.found) {
        operation.failure(new Error(data.message || '地址解析失败'), { failureStage: 'GEOCODE' });
        setMessage(data.message || '没有找到该地址，请补充更完整的信息后重试。');
        return;
      }

      const options = (data.candidates || []).map(candidate => ({
        key: candidate.gpsCoordinates,
        label: candidate.name || candidate.address,
        address: candidate.address || candidate.name,
        gpsCoordinates: candidate.gpsCoordinates,
        preferred: candidate.gpsCoordinates === data.gpsCoordinates,
        note: [candidate.city, candidate.district].filter(Boolean).join(' '),
      }));

      if (options.length <= 1) {
        const only = options[0];
        if (only) {
          applyCandidate(only);
        }
        return;
      }

      setCandidates(options);
      setMessage('找到多个匹配地点，请选择正确的目标。');
      operation.success({ candidateCount: options.length, source: 'GEOCODE' });
    } catch (error) {
      operation.failure(error, { failureStage: 'GEOCODE' });
      setMessage(formatApiErrorMessage(error, '地址解析失败，请稍后重试。'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <View style={{ gap: 12 }}>
      <Text style={{ color: colors.muted, fontSize: 13, lineHeight: 19 }}>
        无法前往现场时，可以粘贴其他地图复制的坐标，或直接搜索地址来定位。
      </Text>

      <TextField
        label="按地址搜索"
        value={keyword}
        onChangeText={setKeyword}
        placeholder="例如：漳州市医院"
      />
      <AppButton
        title={busy ? '正在处理' : '搜索地址并取点'}
        onPress={handleGeocode}
        variant="secondary"
        loading={busy}
        disabled={busy || !keyword.trim()}
      />

      <TextField
        label="粘贴坐标"
        value={pasteText}
        onChangeText={setPasteText}
        placeholder="例如：117.647,24.513"
        multiline
      />
      <SelectField
        label="坐标系"
        value={standard}
        options={STANDARD_OPTIONS}
        onChange={setStandard}
      />
      <AppButton
        title={busy ? '正在识别' : '识别并填入坐标'}
        onPress={handleRecognize}
        variant="secondary"
        loading={busy}
        disabled={busy || !pasteText.trim()}
      />

      {candidates.length > 0 && (
        <View style={{ gap: 8 }}>
          <Text style={{ color: colors.text, fontWeight: '700', fontSize: 14 }}>请选择匹配的地点</Text>
          {candidates.map(candidate => (
            <Pressable
              key={candidate.key}
              accessibilityRole="button"
              accessibilityLabel={`采用 ${candidate.label}`}
              onPress={() => applyCandidate(candidate)}
              style={({ pressed }) => ({
                borderWidth: 1,
                borderColor: candidate.preferred ? colors.primary : colors.border,
                borderRadius: 8,
                padding: 10,
                backgroundColor: pressed ? colors.surface : '#fff',
                gap: 3,
              })}
            >
              <Text style={{ color: colors.text, fontWeight: '700' }}>{candidate.label}</Text>
              {!!candidate.address && (
                <Text style={{ color: colors.muted, fontSize: 13 }}>{candidate.address}</Text>
              )}
              {!!candidate.note && (
                <Text style={{ color: colors.muted, fontSize: 12 }}>{candidate.note}</Text>
              )}
            </Pressable>
          ))}
        </View>
      )}

      {!!message && (
        <Text style={{ color: colors.muted, fontSize: 13, lineHeight: 19 }}>{message}</Text>
      )}
    </View>
  );
}

type CandidateOption = {
  key: string;
  label: string;
  address: string;
  gpsCoordinates: string;
  preferred: boolean;
  note: string;
};

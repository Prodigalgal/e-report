import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { Alert, Image, Pressable, Text, View } from 'react-native';

import { formatApiErrorMessage } from '@/api/http';
import { fetchWorkOrderById, respondWorkOrder } from '@/api/work-order-api';
import type { FileTransferProgress } from '@/api/file-transfer-api';
import { findMaterialOption, MATERIAL_OPTIONS } from '@/constants/materials';
import { hasAllowedAction } from '@/constants/work-orders';
import { openExternalNavigation, shareWorkOrderLocation } from '@/location/map-navigation';
import type { LocalAttachment, MaterialCategory, MaterialUsage } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { InfoRow } from '@/ui/info-row';
import { PageHeader, SectionHeader } from '@/ui/page-header';
import { Screen } from '@/ui/screen';
import { TextField } from '@/ui/text-field';
import { colors, uiLayout } from '@/ui/theme';
import {
  buildAttachmentLimitMessage,
  formatAttachmentSize,
  getAttachmentTotalSize,
  MAX_ATTACHMENT_COUNT,
  MAX_ATTACHMENT_TOTAL_MB,
} from '@/utils/attachment-limits';

export default function ProcessingDetailScreen() {
  const params = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const id = params.id?.trim() || '';
  const [resolutionDetails, setResolutionDetails] = useState('');
  const [resolutionMethod, setResolutionMethod] = useState('');
  const [replacementCompleted, setReplacementCompleted] = useState(false);
  const [replacementResult, setReplacementResult] = useState('');
  const [responseAttachments, setResponseAttachments] = useState<LocalAttachment[]>([]);
  const [materialCategory, setMaterialCategory] = useState<MaterialCategory>('OPTICAL_CABLE');
  const selectedMaterialOption = useMemo(() => findMaterialOption(materialCategory), [materialCategory]);
  const [materialSpecification, setMaterialSpecification] = useState('12芯');
  const [materialQuantity, setMaterialQuantity] = useState('1');
  const [materialUsages, setMaterialUsages] = useState<MaterialUsage[]>([]);
  const [submitProgress, setSubmitProgress] = useState<FileTransferProgress | null>(null);

  const query = useQuery({
    queryKey: ['work-order', id],
    queryFn: () => fetchWorkOrderById(id),
    enabled: Boolean(id),
  });

  const responseMutation = useMutation({
    mutationFn: () => respondWorkOrder(id, {
      expectedVersion: query.data?.version ?? 0,
      resolutionDetails: resolutionDetails.trim(),
      resolutionMethod: resolutionMethod.trim(),
      replacementCompleted: query.data?.reportType === 'DAMAGED_BOX_REPLACEMENT' ? replacementCompleted : undefined,
      replacementResult: query.data?.reportType === 'DAMAGED_BOX_REPLACEMENT' ? replacementResult.trim() : undefined,
      materialUsages,
      attachments: responseAttachments,
    }, setSubmitProgress),
    onSuccess: async () => {
      setSubmitProgress(null);
      await queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      await queryClient.invalidateQueries({ queryKey: ['processing-work-orders'] });
      await queryClient.invalidateQueries({ queryKey: ['admin-work-orders'] });
      await queryClient.invalidateQueries({ queryKey: ['admin-work-order-statistics'] });
      Alert.alert(
        '回单成功',
        '工单处理结果已提交。',
        [{ text: '确定', onPress: () => router.replace('/search') }],
        { cancelable: false },
      );
    },
  });

  if (!query.data) {
    return (
      <Screen>
        <Card>
          <Text selectable style={{ color: query.error ? colors.danger : colors.muted }}>
            {query.error ? formatApiErrorMessage(query.error, '工单加载失败，请检查网络后重试。') : '正在加载工单'}
          </Text>
        </Card>
      </Screen>
    );
  }

  const workOrder = query.data;
  if (!hasAllowedAction(workOrder, 'RESPOND')) {
    return (
      <Screen>
        <Card>
          <Text style={{ color: colors.danger }}>当前账号没有处理此类工单的权限。</Text>
        </Card>
      </Screen>
    );
  }

  const chooseMaterialCategory = (category: MaterialCategory) => {
    const nextOption = findMaterialOption(category);
    setMaterialCategory(category);
    setMaterialSpecification(nextOption.specifications[0]);
  };

  const addMaterialUsage = () => {
    const quantity = Number(materialQuantity);
    if (!Number.isInteger(quantity) || quantity < 1) {
      Alert.alert('物料数量不正确', '物料数量必须是大于0的整数。');
      return;
    }
    setMaterialUsages((current) => [
      ...current,
      {
        category: materialCategory,
        categoryName: selectedMaterialOption.label,
        specification: materialSpecification,
        quantity,
      },
    ]);
    setMaterialQuantity('1');
  };

  const addFromCamera = async () => {
    if (responseAttachments.length >= MAX_ATTACHMENT_COUNT) {
      Alert.alert('无法添加附件', `附件最多上传${MAX_ATTACHMENT_COUNT}个，请先删除一个附件再继续。`);
      return;
    }
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (permission.status !== 'granted') {
      Alert.alert('无法拍摄', '请先授予相机权限。');
      return;
    }
    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ['images', 'videos'],
      quality: 0.48,
    });
    addPickerAssets(result);
  };

  const addFromLibrary = async () => {
    if (responseAttachments.length >= MAX_ATTACHMENT_COUNT) {
      Alert.alert('无法添加附件', `附件最多上传${MAX_ATTACHMENT_COUNT}个，请先删除一个附件再继续。`);
      return;
    }
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (permission.status !== 'granted') {
      Alert.alert('无法选择附件', '请先授予相册权限。');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images', 'videos'],
      allowsMultipleSelection: true,
      selectionLimit: MAX_ATTACHMENT_COUNT - responseAttachments.length,
      quality: 0.48,
    });
    addPickerAssets(result);
  };

  const addPickerAssets = (result: ImagePicker.ImagePickerResult) => {
    if (result.canceled) {
      return;
    }
    const pickedAttachments: LocalAttachment[] = result.assets.map((asset, index) => {
      const mediaType: LocalAttachment['mediaType'] = asset.type === 'video' ? 'video' : 'image';
      return {
        uri: asset.uri,
        name: asset.fileName || `response-attachment-${Date.now()}-${index}${mediaType === 'video' ? '.mp4' : '.jpg'}`,
        type: asset.mimeType || (mediaType === 'video' ? 'video/mp4' : 'image/jpeg'),
        mediaType,
        size: asset.fileSize,
      };
    });
    const limitMessage = buildAttachmentLimitMessage(responseAttachments, pickedAttachments);
    if (limitMessage) {
      Alert.alert('无法添加附件', limitMessage);
      return;
    }
    setResponseAttachments((current) => [...current, ...pickedAttachments]);
  };

  const replacementReady = workOrder.reportType !== 'DAMAGED_BOX_REPLACEMENT'
    || (!replacementCompleted || replacementResult.trim());
  const responseAttachmentLimitMessage = buildAttachmentLimitMessage([], responseAttachments);
  const canSubmitResponse = !!resolutionDetails.trim()
    && !!resolutionMethod.trim()
    && responseAttachments.length > 0
    && !responseAttachmentLimitMessage
    && !!replacementReady
    && !responseMutation.isPending;

  return (
    <Screen>
      <PageHeader title={workOrder.reportTypeName} subtitle={`工单 #${workOrder.id}`} />
      <Card style={{ gap: 8 }}>
        <SectionHeader step={1} title="确认工单" />
        <InfoRow label="工单编号" value={`#${workOrder.id}`} />
        <InfoRow label="地点" value={workOrder.location} />
        <InfoRow label="定位坐标" value={workOrder.gpsCoordinates} />
        <InfoRow label="原因" value={workOrder.reason} />
        <AppButton
          title="导航到现场"
          variant="secondary"
          onPress={() => openExternalNavigation(workOrder.gpsCoordinates, workOrder.location)
            .catch((error) => Alert.alert('无法打开导航', formatApiErrorMessage(error, '未能打开地图应用，请稍后重试。')))}
        />
        <AppButton
          title="分享位置链接"
          variant="secondary"
          onPress={() => shareWorkOrderLocation(workOrder.id, workOrder.gpsCoordinates, workOrder.location)
            .catch((error) => Alert.alert('无法分享位置', formatApiErrorMessage(error, '位置分享失败，请稍后重试。')))}
        />
      </Card>

      <Card style={{ gap: 12 }}>
        <SectionHeader step={2} title="回单处理" />
        <TextField label="处理情况" value={resolutionDetails} onChangeText={setResolutionDetails} multiline required />
        <TextField label="处理方式" value={resolutionMethod} onChangeText={setResolutionMethod} multiline required />
        {workOrder.reportType === 'DAMAGED_BOX_REPLACEMENT' && (
          <>
            <AppButton
              title={replacementCompleted ? '已完成换箱' : '未完成换箱'}
              onPress={() => setReplacementCompleted((value) => !value)}
              variant={replacementCompleted ? 'secondary' : 'ghost'}
            />
            <TextField label="换箱结果" value={replacementResult} onChangeText={setReplacementResult} multiline />
          </>
        )}
      </Card>

      <Card style={{ gap: 12 }}>
        <SectionHeader
          step={3}
          title="回单材料"
          subtitle={`至少上传1张处理后照片或视频，最多${MAX_ATTACHMENT_COUNT}个，合计不超过${MAX_ATTACHMENT_TOTAL_MB}兆字节`}
        />
        <Text style={{ color: colors.muted }}>
          已选 {responseAttachments.length} / {MAX_ATTACHMENT_COUNT} 个 · 合计 {formatAttachmentSize(getAttachmentTotalSize(responseAttachments))} / {MAX_ATTACHMENT_TOTAL_MB}兆字节
        </Text>
        {!!responseAttachmentLimitMessage && <Text style={{ color: colors.danger }}>{responseAttachmentLimitMessage}</Text>}
        <View style={{ flexDirection: 'row', gap: 10 }}>
          <View style={{ flex: 1 }}>
            <AppButton title="拍照" onPress={addFromCamera} variant="secondary" disabled={responseAttachments.length >= MAX_ATTACHMENT_COUNT} />
          </View>
          <View style={{ flex: 1 }}>
            <AppButton title="相册" onPress={addFromLibrary} variant="secondary" disabled={responseAttachments.length >= MAX_ATTACHMENT_COUNT} />
          </View>
        </View>
        {responseAttachments.map((attachment, index) => (
          <View key={`${attachment.uri}-${index}`} style={{ flexDirection: 'row', gap: 10, alignItems: 'center' }}>
            {attachment.mediaType === 'image' ? (
              <Image source={{ uri: attachment.uri }} style={{ width: 58, height: 58, borderRadius: 8, backgroundColor: colors.surfaceAlt }} />
            ) : (
              <View style={{ width: 58, height: 58, borderRadius: 8, backgroundColor: colors.surfaceAlt, alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ color: colors.muted }}>视频</Text>
              </View>
            )}
            <View style={{ flex: 1 }}>
              <Text numberOfLines={1} style={{ color: colors.text, fontWeight: '700' }}>{attachment.name}</Text>
              <Text style={{ color: colors.muted }}>{attachment.type}</Text>
            </View>
            <AppButton title="移除" variant="danger" onPress={() => setResponseAttachments((current) => current.filter((_, itemIndex) => itemIndex !== index))} />
          </View>
        ))}
      </Card>

      <Card style={{ gap: 12 }}>
        <SectionHeader step={4} title="消耗物料" subtitle="无物料消耗可直接跳过" />
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {MATERIAL_OPTIONS.map((item) => {
            const selected = item.category === materialCategory;
            return (
              <Pressable
                key={item.category}
                onPress={() => chooseMaterialCategory(item.category)}
                style={{
                  paddingHorizontal: 12,
                  minHeight: uiLayout.controlHeight,
                  justifyContent: 'center',
                  paddingVertical: 8,
                  borderRadius: 8,
                  borderWidth: 1,
                  borderColor: selected ? colors.primary : colors.controlBorder,
                  backgroundColor: selected ? colors.primary : colors.controlSurface,
                }}
              >
                <Text style={{ color: selected ? '#fff' : colors.text, fontWeight: '800' }}>{item.label}</Text>
              </Pressable>
            );
          })}
        </View>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {selectedMaterialOption.specifications.map((specification) => {
            const selected = specification === materialSpecification;
            return (
              <Pressable
                key={specification}
                onPress={() => setMaterialSpecification(specification)}
                style={{
                  paddingHorizontal: 10,
                  minHeight: uiLayout.controlHeight,
                  justifyContent: 'center',
                  paddingVertical: 7,
                  borderRadius: 8,
                  borderWidth: 1,
                  borderColor: selected ? colors.primary : colors.controlBorder,
                  backgroundColor: selected ? colors.controlSelectedSurface : colors.controlSurface,
                }}
              >
                <Text style={{ color: selected ? colors.controlSelectedText : colors.text, fontWeight: '700' }}>{specification}</Text>
              </Pressable>
            );
          })}
        </View>
        <TextField label="数量" value={materialQuantity} onChangeText={setMaterialQuantity} keyboardType="number-pad" />
        <AppButton title="添加物料" onPress={addMaterialUsage} variant="secondary" />
        {materialUsages.map((item, index) => (
          <View key={`${item.category}-${item.specification}-${index}`} style={{ flexDirection: 'row', gap: 10, alignItems: 'center' }}>
            <View style={{ flex: 1 }}>
              <Text style={{ color: colors.text, fontWeight: '800' }}>{item.categoryName || findMaterialOption(item.category).label} · {item.specification}</Text>
              <Text style={{ color: colors.muted }}>数量 {item.quantity}</Text>
            </View>
            <AppButton title="移除" variant="danger" onPress={() => setMaterialUsages((current) => current.filter((_, itemIndex) => itemIndex !== index))} />
          </View>
        ))}
      </Card>

      {!!responseMutation.error && (
        <Text selectable style={{ color: colors.danger }}>
          {formatApiErrorMessage(responseMutation.error, '回单失败，请稍后重试。')}
        </Text>
      )}
      {responseMutation.isPending && submitProgress && (
        <Card>
          <Text style={{ color: colors.text, fontWeight: '700' }}>正在上传回单材料</Text>
          <Text style={{ color: colors.muted }}>
            {submitProgress.message
              || `${submitProgress.fileName} · ${submitProgress.fileIndex + 1}/${submitProgress.fileCount}`}
          </Text>
        </Card>
      )}
      <AppButton
        title={responseMutation.isPending ? '正在提交' : '提交回单'}
        onPress={() => responseMutation.mutate()}
        disabled={!canSubmitResponse}
        loading={responseMutation.isPending}
      />
    </Screen>
  );
}

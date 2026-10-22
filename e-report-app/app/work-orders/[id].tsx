import { useQuery } from '@tanstack/react-query';
import { File, FileMode, Paths } from 'expo-file-system';
import { Image } from 'expo-image';
import * as MediaLibrary from 'expo-media-library';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useVideoPlayer, VideoView } from 'expo-video';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { formatApiErrorMessage, rawApiFetch } from '@/api/http';
import { fetchWorkOrderById } from '@/api/work-order-api';
import { AttachmentImagePreview } from '@/features/work-orders/attachment-image-preview';
import {
  attachmentMediaKind,
  buildAttachmentApiPath,
  buildLocalMediaFileName,
  supportsAttachmentPreview,
} from '@/features/work-orders/attachment-media';
import { WorkOrderLifecycleActions } from '@/features/work-orders/lifecycle-actions';
import { WorkOrderHistory } from '@/features/work-orders/work-order-history';
import { WorkOrderOrganizationTransfer } from '@/features/work-orders/organization-transfer';
import { priorityPresentation, slaRiskPresentation } from '@/constants/work-order-presentation';
import { openExternalNavigation, shareWorkOrderLocation } from '@/location/map-navigation';
import type { Attachment } from '@/types/api';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { InfoRow } from '@/ui/info-row';
import { PageHeader, SectionHeader } from '@/ui/page-header';
import { SectionTabs, SectionContent } from '@/ui/section-tabs';
import { PresentationBadge } from '@/ui/presentation-badge';
import { Screen } from '@/ui/screen';
import { StatusBadge } from '@/ui/status-badge';
import { colors, uiLayout } from '@/ui/theme';
import { formatDateTime, formatDateTimeWithSeconds, formatFileSize } from '@/utils/format';
import { formatDuration, formatSlaCountdown, formatSlaSummary } from '@/utils/work-order-sla';

const DOWNLOAD_CHUNK_BYTES = 2 * 1024 * 1024;
const MAX_DOWNLOAD_CHUNK_RETRIES = 5;
const DOWNLOAD_CHUNK_TIMEOUT_MS = 30_000;
const attachmentDownloadPromises = new Map<string, Promise<File>>();

export default function WorkOrderDetailScreen() {
  const params = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const id = params.id?.trim() || '';
  const [savingAttachmentId, setSavingAttachmentId] = useState<string | null>(null);
  const [activeSection, setActiveSection] = useState('details');
  const [previewAttachment, setPreviewAttachment] = useState<Attachment | null>(null);
  const [previewFileUri, setPreviewFileUri] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const previewRequestId = useRef(0);
  const query = useQuery({
    queryKey: ['work-order', id],
    queryFn: () => fetchWorkOrderById(id),
    enabled: Boolean(id),
  });

  if (query.isLoading) {
    return (
      <Screen>
        <ActivityIndicator color={colors.primary} />
      </Screen>
    );
  }

  if (!query.data) {
    return (
      <Screen>
        <Card>
          <Text selectable style={{ color: colors.danger }}>
            {formatApiErrorMessage(query.error, '工单不存在、无权限查看或加载失败。')}
          </Text>
        </Card>
      </Screen>
    );
  }

  const workOrder = query.data;
  const priorityTone = priorityPresentation(workOrder.priority);
  const slaTone = slaRiskPresentation(workOrder.slaRiskLevel);
  const slaSummary = formatSlaSummary(workOrder);
  const canViewHistory = workOrder.allowedViews.includes('HISTORY');
  const canViewAttachments = workOrder.allowedViews.includes('ATTACHMENTS');
  const reportAttachments = workOrder.attachments.filter((attachment) => attachment.attachmentStage !== 'RESPONSE');
  const responseAttachments = workOrder.attachments.filter((attachment) => attachment.attachmentStage === 'RESPONSE');
  const activeOrganizations = (workOrder.organizationParticipations ?? []).filter((participation) => participation.active);
  const currentOwnerName = activeOrganizations.find((participation) => participation.relationType === 'OWNER')?.organizationName;
  const activeDepartments = (workOrder.departmentParticipations ?? []).filter((participation) => participation.active);
  const reportingDepartmentName = activeDepartments.find((participation) => participation.relationType === 'REPORTING')?.departmentName;
  const responsibleDepartmentName = activeDepartments.find((participation) => participation.relationType === 'RESPONSIBLE')?.departmentName;
  const processingDepartmentName = activeDepartments.find((participation) => participation.relationType === 'PROCESSING')?.departmentName;
  const saveAttachmentToAlbum = async (attachment: Attachment) => {
    const mediaKind = attachmentMediaKind(attachment.fileType);
    if (mediaKind === 'unsupported') {
      Alert.alert('无法保存', '当前附件不是图片或视频，无法写入系统相册。');
      return;
    }

    setSavingAttachmentId(attachment.id);
    try {
      const permission = await MediaLibrary.requestPermissionsAsync(true, [mediaKind]);
      if (!permission.granted) {
        throw new Error('未获得系统相册保存权限，请在系统设置中允许后重试。');
      }
      const downloadedFile = await downloadAttachmentToCache(workOrder.id, attachment);
      await MediaLibrary.Asset.create(downloadedFile.uri);
      Alert.alert('保存完成', '附件已保存到系统相册。');
    } catch (error) {
      Alert.alert('保存失败', formatApiErrorMessage(error, '附件保存失败，请稍后重试。'));
    } finally {
      setSavingAttachmentId(null);
    }
  };
  const openAttachmentPreview = async (attachment: Attachment) => {
    const requestId = ++previewRequestId.current;
    setPreviewAttachment(attachment);
    setPreviewFileUri(null);
    setPreviewError(null);
    try {
      const downloadedFile = await downloadAttachmentToCache(workOrder.id, attachment);
      if (requestId === previewRequestId.current) setPreviewFileUri(downloadedFile.uri);
    } catch (error) {
      if (requestId === previewRequestId.current) {
        setPreviewError(formatApiErrorMessage(error, '附件预览加载失败，请稍后重试。'));
      }
    }
  };
  const closeAttachmentPreview = () => {
    previewRequestId.current += 1;
    setPreviewAttachment(null);
    setPreviewFileUri(null);
    setPreviewError(null);
  };

  return (
    <Screen>
      <PageHeader
        title={workOrder.reportTypeName}
        subtitle={`${workOrder.workOrderNo || `工单 #${workOrder.id}`} · ${formatDateTimeWithSeconds(workOrder.reportTime)}`}
        trailing={<StatusBadge status={workOrder.statusCode || workOrder.statusLabel || workOrder.status} phaseCode={workOrder.phaseCode} />}
      />
      <WorkOrderLifecycleActions workOrder={workOrder} onRespond={() => router.push(`/processing/${workOrder.id}`)} />

      <SectionTabs label="工单详情分类" value={activeSection} onChange={setActiveSection} options={[
        { value: 'details', label: '工单信息' },
        ...(canViewAttachments ? [{ value: 'attachments', label: `附件 (${workOrder.attachments.length})` }] : []),
        ...(canViewHistory ? [{ value: 'history', label: '处理记录' }] : []),
        { value: 'transfer', label: '组织移交' },
      ]} />
      <SectionContent value="details" selected={activeSection}>
        <Card style={{ gap: 8, borderColor: slaTone.border, backgroundColor: slaTone.surface }}>
          <SectionHeader title="处理时限" />
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}>
            <PresentationBadge label={workOrder.priorityName || priorityTone.label} tone={priorityTone} />
            {slaSummary && <PresentationBadge label={slaSummary} tone={slaTone} />}
          </View>
          {workOrder.slaRiskLevel === 'BREACHED' && (
            <Text selectable style={{ color: slaTone.foreground, fontWeight: '800' }}>
              当前{workOrder.slaStageName}阶段已超时 {formatDuration(workOrder.slaBreachSeconds)}
            </Text>
          )}
          <InfoRow label="当前阶段" value={workOrder.slaStageName} />
          <InfoRow label="阶段进度" value={formatSlaCountdown(workOrder)} />
          <InfoRow label="阶段截止" value={workOrder.slaDueAt ? formatDateTime(workOrder.slaDueAt) : '已结束'} />
          <InfoRow label="故障历时" value={formatDuration(workOrder.elapsedSeconds)} />
        </Card>

        <Card style={{ gap: 10 }}>
          <SectionHeader title="工单概览" />
          <InfoRow label="工单编号" value={workOrder.workOrderNo || `#${workOrder.id}`} />
          <InfoRow label="报单时间" value={formatDateTimeWithSeconds(workOrder.reportTime)} />
          <InfoRow label="更新时间" value={formatDateTimeWithSeconds(workOrder.lastUpdatedTime)} />
          <InfoRow label="当前归属组织" value={currentOwnerName} />
          <InfoRow label="提单部门" value={reportingDepartmentName || '组织直属'} />
          <InfoRow label="当前责任部门" value={responsibleDepartmentName || '组织公共工单池'} />
          <InfoRow label="当前处理部门" value={processingDepartmentName || '尚未进入处理'} />
          <InfoRow
            label={workOrder.handlerStateName || '处理人员'}
            value={workOrder.handler?.name || workOrder.handler?.username || '暂未分配'}
          />
        </Card>

        <Card style={{ gap: 8 }}>
          <SectionHeader title="工单内容" />
          <InfoRow label="地点" value={workOrder.location} />
          <InfoRow label="定位坐标" value={workOrder.gpsCoordinates} />
          <InfoRow label="原因" value={workOrder.reason} />
          <InfoRow label="联系人" value={workOrder.contactPerson} />
          <InfoRow label="电话" value={workOrder.phone} />
          {workOrder.boxQuantity != null && <InfoRow label="箱体数量" value={String(workOrder.boxQuantity)} />}
          {workOrder.replacementRequired != null && <InfoRow label="是否需换箱" value={workOrder.replacementRequired ? '是' : '否'} />}
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

        {(workOrder.resolutionDetails || workOrder.resolutionMethod || workOrder.replacementResult || workOrder.closureReason) && (
          <Card style={{ gap: 8 }}>
            <SectionHeader title="处理结果" />
            <InfoRow label="处理情况" value={workOrder.resolutionDetails} />
            <InfoRow label="处理方式" value={workOrder.resolutionMethod} />
            <InfoRow label="处理时间" value={formatDateTime(workOrder.resolutionTime)} />
            <InfoRow label="换箱结果" value={workOrder.replacementResult} />
            <InfoRow label="关闭原因" value={workOrder.closureReason} />
            <InfoRow label="关闭时间" value={formatDateTime(workOrder.closureTime)} />
            {workOrder.materialUsages?.length ? (
              <View style={{ gap: 6 }}>
                <Text style={{ color: colors.text, fontWeight: '800' }}>消耗物料</Text>
                {workOrder.materialUsages.map((item, index) => (
                  <Text key={`${item.category}-${item.specification}-${index}`} style={{ color: colors.muted }}>
                    {item.categoryName || item.category} · {item.specification} · {item.quantity}
                  </Text>
                ))}
              </View>
            ) : null}
          </Card>
        )}

      </SectionContent>
      <SectionContent value="transfer" selected={activeSection}>
        <WorkOrderOrganizationTransfer workOrder={workOrder} onChanged={() => query.refetch()} />
      </SectionContent>
      <SectionContent value="history" selected={activeSection}>
        {canViewHistory && <WorkOrderHistory workOrderId={workOrder.id} version={workOrder.version} />}
      </SectionContent>
      <SectionContent value="attachments" selected={activeSection}>

        {canViewAttachments && (
          <Card style={{ gap: 10 }}>
            <SectionHeader title="附件预览" subtitle={workOrder.attachments.length ? `共 ${workOrder.attachments.length} 个附件` : undefined} />
            {workOrder.attachments.length ? (
              <View style={{ gap: 12 }}>
                <AttachmentGroup
                  title="报单材料"
                  description="提单时上传的现场情况与故障依据"
                  attachments={reportAttachments}
                  tone="report"
                  workOrderId={workOrder.id}
                  savingAttachmentId={savingAttachmentId}
                  onPreview={openAttachmentPreview}
                  onSave={saveAttachmentToAlbum}
                />
                <AttachmentGroup
                  title="回单材料"
                  description="处理人员回单时上传的结果与佐证"
                  attachments={responseAttachments}
                  tone="response"
                  workOrderId={workOrder.id}
                  savingAttachmentId={savingAttachmentId}
                  onPreview={openAttachmentPreview}
                  onSave={saveAttachmentToAlbum}
                />
              </View>
            ) : (
              <Text style={{ color: colors.muted }}>暂无附件。</Text>
            )}
          </Card>
        )}

      </SectionContent>
      {previewAttachment && (
        <AttachmentPreviewModal
          attachment={previewAttachment}
          fileUri={previewFileUri}
          errorMessage={previewError}
          onRetry={() => void openAttachmentPreview(previewAttachment)}
          onClose={closeAttachmentPreview}
        />
      )}

    </Screen>
  );
}

function AttachmentGroup({
  title,
  description,
  attachments,
  tone,
  workOrderId,
  savingAttachmentId,
  onPreview,
  onSave,
}: {
  title: string;
  description: string;
  attachments: Attachment[];
  tone: 'report' | 'response';
  workOrderId: string;
  savingAttachmentId: string | null;
  onPreview: (attachment: Attachment) => Promise<void>;
  onSave: (attachment: Attachment) => Promise<void>;
}) {
  if (!attachments.length) return null;
  const toneColor = tone === 'response' ? colors.warning : colors.primary;
  return (
    <View style={[styles.attachmentGroup, { borderTopColor: toneColor }]}>
      <View style={styles.attachmentGroupHeader}>
        <View style={{ flex: 1, gap: 2 }}>
          <Text style={{ color: colors.text, fontWeight: '900' }}>{title}</Text>
          <Text style={{ color: colors.muted, fontSize: 13 }}>{description}</Text>
        </View>
        <View style={[styles.attachmentCountBadge, { borderColor: toneColor }]}>
          <Text style={{ color: toneColor, fontWeight: '800' }}>{attachments.length} 份</Text>
        </View>
      </View>
      {attachments.map((attachment, index) => {
        const mediaKind = attachmentMediaKind(attachment.fileType);
        const redactionPending = !!attachment.imageRedactionStatus && attachment.imageRedactionStatus !== 'READY';
        const canPreview = !redactionPending && supportsAttachmentPreview(attachment.fileType);
        return (
          <View key={attachment.id} style={[styles.attachmentItem, index > 0 && styles.attachmentItemDivider]}>
            {canPreview && (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`预览${title} ${attachment.fileName}`}
                onPress={() => void onPreview(attachment)}
                style={({ pressed }) => [styles.attachmentPreviewButton, pressed && styles.pressed]}
              >
                {mediaKind === 'photo' ? (
                  <AttachmentPhotoThumbnail attachment={attachment} workOrderId={workOrderId} />
                ) : (
                  <View style={styles.videoThumbnail}>
                    <Text style={styles.videoThumbnailTitle}>视频附件</Text>
                    <Text style={styles.videoThumbnailHint}>点击预览</Text>
                  </View>
                )}
              </Pressable>
            )}
            <View style={[styles.attachmentStageBadge, { borderColor: toneColor }]}>
              <Text style={{ color: toneColor, fontSize: 12, fontWeight: '800' }}>{title}</Text>
            </View>
            <Text selectable style={{ color: colors.text, fontWeight: '700' }}>{attachment.fileName}</Text>
            <Text style={{ color: colors.muted }}>{attachment.fileType || '未知类型'} · {formatFileSize(attachment.fileSize)}</Text>
            {redactionPending && (
              <Text style={{ color: attachment.imageRedactionStatus === 'FAILED' ? colors.danger : colors.warning, fontWeight: '700' }}>
                {attachment.imageRedactionMessage || '附件正在后台进行安全处理'}
              </Text>
            )}
            <AppButton
              title={savingAttachmentId === attachment.id ? '正在保存' : '保存到相册'}
              onPress={() => void onSave(attachment)}
              disabled={savingAttachmentId !== null || mediaKind === 'unsupported' || redactionPending}
              variant="secondary"
            />
          </View>
        );
      })}
    </View>
  );
}

function AttachmentPreviewModal({
  attachment,
  fileUri,
  errorMessage,
  onRetry,
  onClose,
}: {
  attachment: Attachment;
  fileUri: string | null;
  errorMessage: string | null;
  onRetry: () => void;
  onClose: () => void;
}) {
  const mediaKind = attachmentMediaKind(attachment.fileType);

  return (
    <Modal
      visible
      animationType="fade"
      presentationStyle="fullScreen"
      statusBarTranslucent
      onRequestClose={onClose}
    >
      <SafeAreaView style={styles.previewScreen}>
        <View style={styles.previewHeader}>
          <Text numberOfLines={1} style={styles.previewTitle}>{attachment.fileName}</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="关闭附件预览"
            onPress={onClose}
            style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}
          >
            <Text style={styles.closeButtonText}>关闭</Text>
          </Pressable>
        </View>
        <View style={styles.previewContent}>
          {!fileUri && !errorMessage ? (
            <ActivityIndicator color="#FFFFFF" size="large" />
          ) : errorMessage ? (
            <View style={styles.previewErrorState}>
              <Text style={styles.previewErrorText}>{errorMessage}</Text>
              <AppButton title="重新加载" variant="secondary" onPress={onRetry} />
            </View>
          ) : mediaKind === 'photo' ? (
            <AttachmentImagePreview key={`${attachment.id}:${fileUri}`} uri={fileUri!} label={attachment.fileName} onRetry={onRetry} />
          ) : (
            <VideoAttachmentPreview uri={fileUri!} />
          )}
        </View>
      </SafeAreaView>
    </Modal>
  );
}

function VideoAttachmentPreview({ uri }: { uri: string }) {
  const player = useVideoPlayer(uri);

  return (
    <VideoView
      player={player}
      style={styles.previewMedia}
      nativeControls
      contentFit="contain"
      fullscreenOptions={{ enable: true }}
    />
  );
}

function AttachmentPhotoThumbnail({ attachment, workOrderId }: { attachment: Attachment; workOrderId: string }) {
  const [fileUri, setFileUri] = useState<string | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    let active = true;
    setFileUri(null);
    setLoadFailed(false);
    downloadAttachmentToCache(workOrderId, attachment)
      .then((file) => {
        if (active) setFileUri(file.uri);
      })
      .catch(() => {
        if (active) setLoadFailed(true);
      });
    return () => {
      active = false;
    };
  }, [attachment.id, attachment.fileName, attachment.fileSize, attachment.storedFileName, workOrderId]);

  if (loadFailed) {
    return (
      <View style={styles.attachmentLoadState}>
        <Text style={styles.attachmentLoadText}>缩略图加载失败，点击后重试</Text>
      </View>
    );
  }
  if (!fileUri) {
    return (
      <View style={styles.attachmentLoadState}>
        <ActivityIndicator color={colors.primary} />
        <Text style={styles.attachmentLoadText}>正在加载附件</Text>
      </View>
    );
  }
  return <Image source={{ uri: fileUri }} style={styles.attachmentThumbnail} contentFit="cover" />;
}

async function downloadAttachmentToCache(workOrderId: string, attachment: Attachment): Promise<File> {
  const safeName = buildLocalMediaFileName(attachment);
  const targetFile = new File(Paths.cache, `work-order-${workOrderId}-${attachment.id}-${safeName}`);
  if (targetFile.exists && targetFile.size === attachment.fileSize) {
    return targetFile;
  }
  const existingDownload = attachmentDownloadPromises.get(targetFile.uri);
  if (existingDownload) {
    return existingDownload;
  }
  const attachmentPath = buildAttachmentApiPath(workOrderId, attachment.id);
  const downloadPromise = downloadFileInChunks(attachmentPath, targetFile, attachment.fileSize)
    .then(() => targetFile)
    .finally(() => attachmentDownloadPromises.delete(targetFile.uri));
  attachmentDownloadPromises.set(targetFile.uri, downloadPromise);
  return downloadPromise;
}

async function downloadFileInChunks(attachmentPath: string, targetFile: File, totalSize: number) {
  if (!totalSize || totalSize <= 0) {
    throw new Error('附件大小信息缺失，无法安全下载。');
  }
  if (targetFile.exists) {
    targetFile.delete();
  }
  targetFile.create({ overwrite: true, intermediates: true });
  const handle = targetFile.open(FileMode.ReadWrite);
  let downloadedBytes = 0;
  try {
    while (downloadedBytes < totalSize) {
      const start = downloadedBytes;
      const end = Math.min(start + DOWNLOAD_CHUNK_BYTES - 1, totalSize - 1);
      let retryCount = 0;
      while (true) {
        try {
          const chunk = await fetchDownloadChunk(attachmentPath, start, end, totalSize);
          handle.offset = start;
          handle.writeBytes(chunk);
          downloadedBytes = end + 1;
          break;
        } catch (error) {
          retryCount += 1;
          if (retryCount > MAX_DOWNLOAD_CHUNK_RETRIES) {
            throw new Error(`附件分片下载失败：${error instanceof Error ? error.message : String(error || '')}`);
          }
          await delay(Math.min(retryCount * 1200, 5000));
        }
      }
    }
  } finally {
    handle.close();
  }
  if (targetFile.size !== totalSize) {
    throw new Error('附件下载不完整，请重新下载。');
  }
}

async function fetchDownloadChunk(attachmentPath: string, start: number, end: number, totalSize: number) {
  const headers: Record<string, string> = {
    Accept: '*/*',
    Range: `bytes=${start}-${end}`,
    'X-Request-Id': `ereport-app-attachment-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  };
  const response = await rawApiFetch(attachmentPath, {
    method: 'GET',
    headers,
    timeoutMs: DOWNLOAD_CHUNK_TIMEOUT_MS,
    telemetryExpectedStatuses: [206],
  });
  if (response.status !== 206) {
    throw new Error(`HTTP ${response.status}`);
  }
  const contentRange = response.headers.get('Content-Range') || '';
  if (!contentRange.endsWith(`/${totalSize}`)) {
    throw new Error('服务器返回的附件分片范围不正确。');
  }
  const chunk = new Uint8Array(await response.arrayBuffer());
  if (chunk.length !== end - start + 1) {
    throw new Error('服务器返回的附件分片不完整。');
  }
  return chunk;
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const styles = StyleSheet.create({
  attachmentGroup: {
    gap: 10,
    padding: 10,
    borderWidth: 1,
    borderTopWidth: 3,
    borderColor: colors.border,
    borderRadius: 10,
    backgroundColor: colors.surface,
  },
  attachmentGroupHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  attachmentCountBadge: {
    minWidth: 50,
    paddingHorizontal: 9,
    paddingVertical: 4,
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 999,
  },
  attachmentItem: {
    gap: 6,
  },
  attachmentItemDivider: {
    paddingTop: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
  attachmentStageBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderWidth: 1,
    borderRadius: 999,
  },
  attachmentPreviewButton: {
    width: '100%',
    height: 190,
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: colors.surfaceAlt,
  },
  attachmentThumbnail: {
    width: '100%',
    height: '100%',
    backgroundColor: colors.surfaceAlt,
  },
  attachmentLoadState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  attachmentLoadText: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: '700',
  },
  videoThumbnail: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    backgroundColor: colors.surfaceAlt,
  },
  videoThumbnailTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: '800',
  },
  videoThumbnailHint: {
    color: colors.muted,
    fontSize: 13,
  },
  pressed: {
    opacity: 0.72,
  },
  previewScreen: {
    flex: 1,
    backgroundColor: '#050505',
  },
  previewHeader: {
    minHeight: 56,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#303030',
  },
  previewTitle: {
    flex: 1,
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  closeButton: {
    minWidth: 56,
    minHeight: uiLayout.controlHeight,
    borderRadius: uiLayout.cornerRadius,
    borderWidth: 1,
    borderColor: colors.controlBorder,
    backgroundColor: colors.controlSurface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeButtonText: {
    color: colors.text,
    fontSize: 15,
    fontWeight: '700',
  },
  previewContent: {
    flex: 1,
    alignItems: 'stretch',
    justifyContent: 'center',
  },
  previewErrorState: {
    alignSelf: 'center',
    width: '80%',
    gap: 16,
  },
  previewErrorText: {
    color: '#FFFFFF',
    fontSize: 15,
    lineHeight: 22,
    textAlign: 'center',
  },
  previewMedia: {
    width: '100%',
    height: '100%',
  },
});

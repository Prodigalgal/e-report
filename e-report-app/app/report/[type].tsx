import * as ImagePicker from 'expo-image-picker';
import * as Location from 'expo-location';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Alert, Image, Linking, PermissionsAndroid, Platform, Pressable, Text, View } from 'react-native';

import { ApiError, formatApiErrorMessage } from '@/api/http';
import { reverseGeocode } from '@/api/location-api';
import { submitWorkOrder } from '@/api/work-order-api';
import type { FileTransferProgress } from '@/api/file-transfer-api';
import { useAuth } from '@/auth/auth-provider';
import { findWorkOrderTypeBySlug } from '@/constants/work-orders';
import { priorityPresentation, reportTypePresentation } from '@/constants/work-order-presentation';
import {
  formatDeviceLocationSource,
  formatLocationDiagnosticsSummary,
  getDeviceLocationDiagnostics,
  getDualRouteLocation,
  type DeviceLocationDiagnostics,
  type DeviceLocationFix,
} from '@/location/device-location';
import { recordAppTelemetryOperation, startAppTelemetryOperation } from '@/observability/telemetry';
import { AppButton } from '@/ui/app-button';
import { Card } from '@/ui/card';
import { PageHeader, SectionHeader } from '@/ui/page-header';
import { Screen } from '@/ui/screen';
import { TextField } from '@/ui/text-field';
import { colors, uiLayout } from '@/ui/theme';
import type { LocalAttachment, WorkOrderPriority } from '@/types/api';
import {
  buildAttachmentLimitMessage,
  formatAttachmentSize,
  getAttachmentTotalSize,
  MAX_ATTACHMENT_COUNT,
  MAX_ATTACHMENT_TOTAL_MB,
} from '@/utils/attachment-limits';
import { WORK_ORDER_PRIORITIES } from '@/utils/work-order-sla';

const LOCATION_PERMISSION_TIMEOUT_MS = 60000;
const LOCATION_SERVICE_CHECK_TIMEOUT_MS = 10000;
const GPS_PROVIDER_TIMEOUT_MS = 60000;
const NATIVE_GPS_TIMEOUT_MS = 45000;
const AMAP_GPS_TIMEOUT_MS = 75000;
const LOW_ACCURACY_WARNING_METERS = 100;

type ReportFormErrors = Partial<Record<'location' | 'gpsCoordinates' | 'reason' | 'contactPerson' | 'phone' | 'boxQuantity' | 'attachments', string>>;

export default function ReportWorkOrderScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const params = useLocalSearchParams<{ type: string }>();
  const { currentUser, hasPermission } = useAuth();
  const workOrderType = useMemo(() => findWorkOrderTypeBySlug(params.type), [params.type]);
  const [location, setLocation] = useState('');
  const [priority, setPriority] = useState<WorkOrderPriority>('ROUTINE');
  const [gpsCoordinates, setGpsCoordinates] = useState('');
  const [reason, setReason] = useState('');
  const [contactPerson, setContactPerson] = useState('');
  const [phone, setPhone] = useState('');
  const [contactPersonCustomized, setContactPersonCustomized] = useState(false);
  const [phoneCustomized, setPhoneCustomized] = useState(false);
  const [boxQuantity, setBoxQuantity] = useState(workOrderType?.reportType === 'BOX_DOOR_OPEN' ? '1' : '');
  const [attachments, setAttachments] = useState<LocalAttachment[]>([]);
  const [gpsLoading, setGpsLoading] = useState(false);
  const [locationHint, setLocationHint] = useState('');
  const [formErrors, setFormErrors] = useState<ReportFormErrors>({});
  const [submitProgress, setSubmitProgress] = useState<FileTransferProgress | null>(null);

  useEffect(() => {
    if (!contactPersonCustomized && !contactPerson.trim() && currentUser?.name?.trim()) {
      setContactPerson(currentUser.name.trim());
    }
    if (!phoneCustomized && !phone.trim() && currentUser?.phone?.trim()) {
      setPhone(currentUser.phone.trim());
    }
  }, [contactPerson, contactPersonCustomized, currentUser?.name, currentUser?.phone, phone, phoneCustomized]);

  const validateReport = () => {
    const errors: ReportFormErrors = {};
    if (!location.trim()) errors.location = '工单地点不能为空';
    if (!parseGpsCoordinates(gpsCoordinates)) errors.gpsCoordinates = '请定位或按“经度,纬度”填写有效定位坐标';
    if (!reason.trim()) errors.reason = '工单原因不能为空';
    if (!contactPerson.trim()) errors.contactPerson = '联系人不能为空';
    if (!/^\d{7,15}$/.test(phone.trim())) errors.phone = '请输入7至15位联系电话';
    if (workOrderType?.reportType === 'BOX_DOOR_OPEN') {
      const quantity = Number(boxQuantity);
      if (!Number.isInteger(quantity) || quantity < 1 || quantity > 999) {
        errors.boxQuantity = '箱体数量必须是1至999之间的整数';
      }
    }
    if (attachments.length === 0) {
      errors.attachments = '请至少上传一份现场附件';
    } else {
      const attachmentLimitMessage = buildAttachmentLimitMessage([], attachments);
      if (attachmentLimitMessage) errors.attachments = attachmentLimitMessage;
    }
    return errors;
  };

  const clearFormError = (field: keyof ReportFormErrors) => {
    setFormErrors((current) => current[field] ? { ...current, [field]: undefined } : current);
  };

  const mutation = useMutation({
    mutationFn: async () => {
      const operation = startAppTelemetryOperation({
        category: 'WORK_ORDER',
        eventName: 'WORK_ORDER_REPORT_SUBMIT',
        operationName: '提交工单',
        forceCapture: true,
        attributes: { reportType: workOrderType?.reportType },
      });
      if (!workOrderType) {
        operation.failure(new Error('未知工单类型。'), { failureStage: 'TYPE_VALIDATION' });
        throw new Error('未知工单类型。');
      }
      operation.stage('FORM_VALIDATION_STARTED');
      const errors = validateReport();
      if (Object.keys(errors).length > 0) {
        setFormErrors(errors);
        operation.failure(new Error(Object.values(errors)[0]), {
          failureStage: 'FORM_VALIDATION',
          invalidFields: Object.keys(errors),
        });
        throw new Error(Object.values(errors)[0]);
      }
      operation.stage('ATTACHMENTS_READY', {
        count: attachments.length,
        totalBytes: getAttachmentTotalSize(attachments),
        mediaTypes: [...new Set(attachments.map((attachment) => attachment.mediaType))],
      });
      try {
        const created = await submitWorkOrder({
          data: {
            reportType: workOrderType.reportType,
            priority,
            location: location.trim(),
            gpsCoordinates: gpsCoordinates.trim(),
            reason: reason.trim(),
            contactPerson: contactPerson.trim(),
            phone: phone.trim(),
            boxQuantity: workOrderType.reportType === 'BOX_DOOR_OPEN' ? Number(boxQuantity || 1) : undefined,
          },
          attachments,
        }, setSubmitProgress);
        operation.success({ attachmentCount: attachments.length });
        return created;
      } catch (error) {
        operation.failure(error, { failureStage: 'SUBMIT_OR_UPLOAD', attachmentCount: attachments.length });
        throw error;
      }
    },
    onSuccess: async (created) => {
      setSubmitProgress(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['my-work-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['processing-work-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-work-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-work-order-statistics'] }),
      ]);
      setFormErrors({});
      Alert.alert('提交成功', `工单 #${created.id} 已上报。`, [
        { text: '查看详情', onPress: () => router.replace(`/work-orders/${created.id}`) },
        { text: '返回首页', onPress: () => router.replace('/') },
      ]);
    },
  });

  if (!workOrderType) {
    return (
      <Screen>
        <Card>
          <Text style={{ color: colors.danger }}>未知工单类型。</Text>
        </Card>
      </Screen>
    );
  }

  if (!hasPermission(workOrderType.permissions.report)) {
    return (
      <Screen>
        <Card>
          <Text style={{ color: colors.danger }}>当前账号没有提交此类工单的权限。</Text>
        </Card>
      </Screen>
    );
  }

  const collectLocation = async () => {
    const operation = startAppTelemetryOperation({
      category: 'LOCATION',
      eventName: 'REPORT_LOCATION_COLLECTION',
      operationName: '获取定位并解析地址',
      forceCapture: true,
      attributes: { reportType: workOrderType.reportType },
    });
    setGpsLoading(true);
    setLocationHint('正在请求精确定位权限。');
    operation.stage('LOCATION_BUTTON_PRESSED');
    let failureStage = 'PRECISE_PERMISSION_REQUEST';
    let deviceDiagnostics: DeviceLocationDiagnostics | null = null;
    try {
      await requestPreciseLocationPermission();
      operation.stage('PRECISE_PERMISSION_GRANTED');
      failureStage = 'LOCATION_PROVIDER_PREFLIGHT';
      await ensureLocationProvidersReady();
      operation.stage('LOCATION_PROVIDERS_READY');

      failureStage = 'LOCATION_FIX';
      const fix = await getDualRouteLocation({
        gpsProviderTimeoutMs: GPS_PROVIDER_TIMEOUT_MS,
        nativeTimeoutMs: NATIVE_GPS_TIMEOUT_MS,
        amapTimeoutMs: AMAP_GPS_TIMEOUT_MS,
        requiredAccuracyMeters: LOW_ACCURACY_WARNING_METERS,
        onStageChange: setLocationHint,
        onTelemetryStage: operation.stage,
        traceId: operation.traceId,
      });
      failureStage = 'ADDRESS_RESOLUTION';
      const reverseGeocodeSucceeded = await applyGpsPosition(fix, operation);
      operation.success({
        provider: fix.provider,
        source: fix.source,
        coordinateSystem: fix.coordinateSystem,
        accuracy: fix.accuracy,
        fallbackUsed: Boolean(fix.fallbackReason),
        reverseGeocodeSucceeded,
      });
    } catch (error) {
      deviceDiagnostics = await getDeviceLocationDiagnostics();
      operation.failure(error, { failureStage, diagnostics: deviceDiagnostics });
      const message = await formatGpsErrorMessageWithDiagnostics(error, deviceDiagnostics);
      setLocationHint(message);
      Alert.alert('定位失败', message);
    } finally {
      setGpsLoading(false);
    }
  };

  const requestPreciseLocationPermission = async () => {
    if (Platform.OS !== 'android') {
      const permission = await withTimeout(
        Location.requestForegroundPermissionsAsync(),
        LOCATION_PERMISSION_TIMEOUT_MS,
        '定位授权没有返回结果，请确认系统权限弹窗是否被拦截，然后重试。',
      );
      if (permission.status !== 'granted') {
        showLocationSettingsAlert('定位权限未授权', '请在系统设置中允许网络运维管理使用位置，并开启“精确位置”。');
        throw new Error('定位权限未授权，请在系统设置中允许网络运维管理使用位置，并开启“精确位置”。');
      }
      if (permission.android?.accuracy === 'coarse') {
        showLocationSettingsAlert('需要精确定位', '当前仅授予了模糊定位，无法满足现场维修定位精度。请在系统权限中开启“精确位置”。');
        throw new Error('当前仅授予了模糊定位，无法满足现场维修定位精度。请在系统权限中开启“精确位置”。');
      }
      return;
    }

    const permissionResult = await withTimeout(
      PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION, {
        title: '需要精确定位权限',
        message: '网络运维管理需要获取现场定位，用于后续维修人员准确到达故障位置。',
        buttonPositive: '允许',
        buttonNegative: '拒绝',
      }),
      LOCATION_PERMISSION_TIMEOUT_MS,
      '定位授权没有返回结果，请确认系统权限弹窗是否被拦截，然后重试。',
    );
    if (permissionResult !== PermissionsAndroid.RESULTS.GRANTED) {
      showLocationSettingsAlert('定位权限未授权', '请在系统设置中允许网络运维管理使用位置，并开启“精确位置”。');
      throw new Error('定位权限未授权，请在系统设置中允许网络运维管理使用位置，并开启“精确位置”。');
    }

    const diagnostics = await getDeviceLocationDiagnostics();
    if (diagnostics && !diagnostics.finePermissionGranted) {
      showLocationSettingsAlert('需要精确定位', '当前仅授予了模糊定位，无法满足现场维修定位精度。请在系统权限中开启“精确位置”。');
      throw new Error(`当前未检测到精确定位权限。${formatLocationDiagnosticsSummary(diagnostics)}`);
    }
  };

  const ensureLocationProvidersReady = async () => {
    setLocationHint('正在读取手机定位诊断。');
    if (Platform.OS !== 'android') {
      const servicesEnabled = await withTimeout(
        Location.hasServicesEnabledAsync(),
        LOCATION_SERVICE_CHECK_TIMEOUT_MS,
        '系统定位服务检查超时，请确认手机定位总开关已开启。',
      );
      if (!servicesEnabled) {
        throw new Error('手机系统定位服务未开启，请先打开系统定位总开关。');
      }
      return;
    }

    const diagnostics = await withTimeout(
      getDeviceLocationDiagnostics(),
      LOCATION_SERVICE_CHECK_TIMEOUT_MS,
      '手机定位诊断读取超时，请确认系统定位总开关已开启。',
    );
    const summary = formatLocationDiagnosticsSummary(diagnostics);
    if (summary) {
      setLocationHint(summary);
    }
    if (!diagnostics) {
      return;
    }
    if (!diagnostics.locationManagerAvailable) {
      throw new Error('手机系统定位服务不可用，请检查系统定位设置或重启手机后再试。');
    }
    if (!diagnostics.finePermissionGranted) {
      throw new Error(`当前未检测到精确定位权限，请在系统权限中开启“精确位置”。${summary}`);
    }
    if (!diagnostics.locationServicesEnabled) {
      throw new Error(`手机系统定位总开关未开启，请先打开系统定位。${summary}`);
    }
    if (!diagnostics.gpsProviderEnabled && !diagnostics.networkProviderEnabled) {
      throw new Error(`卫星定位和网络定位都未开启，请打开高精度定位后重试。${summary}`);
    }
    if (!diagnostics.gpsProviderEnabled) {
      setLocationHint(`${summary}；卫星定位未开启，将直接尝试系统综合定位和高德定位。`);
      return;
    }
    setLocationHint(`${summary}；即将优先使用系统卫星定位采集。`);
  };

  const applyGpsPosition = async (
    position: DeviceLocationFix,
    operation: ReturnType<typeof startAppTelemetryOperation>,
  ) => {
    const longitude = position.longitude;
    const latitude = position.latitude;
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
      throw new Error('系统定位返回了无效坐标，请重新定位或手动填写经纬度。');
    }
    const nextGps = formatGpsCoordinates(longitude, latitude);
    setGpsCoordinates(nextGps);
    clearFormError('gpsCoordinates');
    const source = formatDeviceLocationSource(position);
    const accuracy = position.accuracy;
    const accuracyText = typeof accuracy === 'number'
      ? `，精度约 ${Math.round(accuracy)} 米`
      : '';
    const fallbackText = position.fallbackReason ? `；已自动从原生定位切换，原生原因：${position.fallbackReason}` : '';
    if (typeof accuracy === 'number' && accuracy > LOW_ACCURACY_WARNING_METERS) {
      const hint = `${source}已返回坐标${accuracyText}${fallbackText}，但精度偏低。系统仍会尝试解析地址，请务必核对。`;
      setLocationHint(hint);
      Alert.alert('定位精度偏低', hint);
    }
    if (!isLikelyMainlandChina(longitude, latitude)) {
      const hint = `${source}已返回坐标${accuracyText}${fallbackText}。当前坐标不在中国大陆常用范围内，请确认现场定位是否准确，或手动填写定位坐标和地址。`;
      setLocationHint(hint);
      Alert.alert('定位已获取', hint);
      operation.stage('ADDRESS_RESOLUTION_SKIPPED', { reason: 'OUTSIDE_MAINLAND_RANGE' });
      return false;
    }
    return resolveAddress(nextGps, `${source}已获取${accuracyText}${fallbackText}`, operation);
  };

  const resolveAddress = async (
    coordinates: string,
    sourceSummary: string,
    operation: ReturnType<typeof startAppTelemetryOperation>,
  ) => {
    const parsedGps = parseGpsCoordinates(coordinates);
    if (!parsedGps) {
      const message = '定位结果格式不正确，请重新定位或手动填写地点。';
      setLocationHint(message);
      Alert.alert('无法解析地址', message);
      operation.stage('ADDRESS_RESOLUTION_SKIPPED', { reason: 'INVALID_COORDINATE_FORMAT' });
      return false;
    }

    if (!isLikelyMainlandChina(parsedGps.longitude, parsedGps.latitude)) {
      const message = '当前定位坐标不在中国大陆常用范围内，地图服务可能无法解析地址。请确认现场定位是否准确，或手动填写地址。';
      setLocationHint(message);
      Alert.alert('无法解析地址', message);
      operation.stage('ADDRESS_RESOLUTION_SKIPPED', { reason: 'OUTSIDE_MAINLAND_RANGE' });
      return false;
    }

    setLocationHint(`${sourceSummary}；正在连接服务器解析详细地址。`);
    operation.stage('REVERSE_GEOCODE_STARTED');
    try {
      const area = await reverseGeocode(formatGpsCoordinates(parsedGps.longitude, parsedGps.latitude));
      if (area.formattedAddress) {
        setLocation(area.formattedAddress);
        clearFormError('location');
      }
      if (!area.formattedAddress) {
        operation.stage('REVERSE_GEOCODE_EMPTY');
        setLocationHint(`${sourceSummary}；定位坐标已保留，但没有解析到详细地址，请手动填写工单地点。`);
        return false;
      }
      operation.stage('REVERSE_GEOCODE_SUCCEEDED');
      setLocationHint(`${sourceSummary}；地址已填写，请结合现场核对后提交。`);
      return true;
    } catch (error) {
      operation.stage('REVERSE_GEOCODE_FAILED', {
        reason: formatLocationRequestError(error, '地址解析失败'),
      });
      const message = formatLocationRequestError(error, '定位坐标已保留，地址解析失败，请手动填写工单地点。');
      setLocationHint(message);
      Alert.alert('地址解析失败', `${message}\n定位坐标：${formatGpsCoordinates(parsedGps.longitude, parsedGps.latitude)}`);
      return false;
    }
  };

  const addFromCamera = async () => {
    if (attachments.length >= MAX_ATTACHMENT_COUNT) {
      Alert.alert('无法添加附件', `附件最多上传${MAX_ATTACHMENT_COUNT}个，请先删除一个附件再继续。`);
      return;
    }
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (permission.status !== 'granted') {
      recordAppTelemetryOperation({ category: 'ATTACHMENT', eventName: 'CAMERA_PERMISSION_DENIED', operationName: '拍摄现场材料', outcome: 'FAILURE', forceCapture: true });
      Alert.alert('无法拍摄', '请先授予相机权限。');
      return;
    }
    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ['images', 'videos'],
      quality: 0.48,
    });
    addPickerAssets(result, 'CAMERA');
  };

  const addFromLibrary = async () => {
    if (attachments.length >= MAX_ATTACHMENT_COUNT) {
      Alert.alert('无法添加附件', `附件最多上传${MAX_ATTACHMENT_COUNT}个，请先删除一个附件再继续。`);
      return;
    }
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (permission.status !== 'granted') {
      recordAppTelemetryOperation({ category: 'ATTACHMENT', eventName: 'MEDIA_PERMISSION_DENIED', operationName: '选择现场材料', outcome: 'FAILURE', forceCapture: true });
      Alert.alert('无法选择附件', '请先授予相册权限。');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images', 'videos'],
      allowsMultipleSelection: true,
      selectionLimit: MAX_ATTACHMENT_COUNT - attachments.length,
      quality: 0.48,
    });
    addPickerAssets(result, 'LIBRARY');
  };

  const addPickerAssets = (result: ImagePicker.ImagePickerResult, source: 'CAMERA' | 'LIBRARY') => {
    if (result.canceled) {
      recordAppTelemetryOperation({ category: 'ATTACHMENT', eventName: 'ATTACHMENT_PICKER_CANCELLED', operationName: '选择现场材料', outcome: 'SUCCESS', attributes: { source } });
      return;
    }
    const pickedAttachments: LocalAttachment[] = result.assets.map((asset, index) => {
      const mediaType: LocalAttachment['mediaType'] = asset.type === 'video' ? 'video' : 'image';
      return {
        uri: asset.uri,
        name: asset.fileName || `work-order-attachment-${Date.now()}-${index}`,
        type: asset.mimeType || (mediaType === 'video' ? 'video/mp4' : 'image/jpeg'),
        mediaType,
        size: asset.fileSize,
      };
    });
    const limitMessage = buildAttachmentLimitMessage(attachments, pickedAttachments);
    if (limitMessage) {
      recordAppTelemetryOperation({ category: 'ATTACHMENT', eventName: 'ATTACHMENT_LIMIT_REJECTED', operationName: '选择现场材料', outcome: 'FAILURE', forceCapture: true, attributes: { source, selectedCount: pickedAttachments.length } });
      Alert.alert('无法添加附件', limitMessage);
      return;
    }
    setAttachments((current) => [...current, ...pickedAttachments]);
    recordAppTelemetryOperation({
      category: 'ATTACHMENT',
      eventName: 'ATTACHMENTS_SELECTED',
      operationName: '选择现场材料',
      outcome: 'SUCCESS',
      attributes: {
        source,
        selectedCount: pickedAttachments.length,
        mediaTypes: [...new Set(pickedAttachments.map((attachment) => attachment.mediaType))],
      },
    });
    clearFormError('attachments');
  };

  const submitReport = () => {
    const errors = validateReport();
    setFormErrors(errors);
    if (Object.keys(errors).length > 0) {
      Alert.alert('请完善报单信息', Object.values(errors)[0]);
      return;
    }
    mutation.mutate();
  };

  return (
    <Screen>
      <PageHeader
        title={workOrderType.reportLabel}
        subtitle={workOrderType.description}
        accentColor={reportTypePresentation(workOrderType.reportType).foreground}
      />

      <Card style={{ gap: 12 }}>
        <SectionHeader step={1} title="位置与工单内容" />
        <View style={{ gap: 6 }}>
          <Text style={{ color: colors.text, fontWeight: '700' }}>工单优先级</Text>
          <View style={{ flexDirection: 'row', gap: 6 }}>
            {WORK_ORDER_PRIORITIES.map((item) => {
              const selected = priority === item.value;
              const tone = priorityPresentation(item.value);
              return (
                <Pressable
                  key={item.value}
                  accessibilityRole="button"
                  accessibilityState={{ selected }}
                  onPress={() => setPriority(item.value)}
                  style={({ pressed }) => ({
                    flex: 1,
                    minHeight: uiLayout.controlHeight,
                    borderWidth: 1,
                    borderColor: selected ? tone.foreground : tone.border,
                    borderRadius: 8,
                    backgroundColor: selected ? tone.surface : colors.controlSurface,
                    alignItems: 'center',
                    justifyContent: 'center',
                    opacity: pressed ? 0.78 : 1,
                  })}
                >
                  <Text style={{ color: selected ? tone.foreground : colors.text, fontWeight: '800' }}>{item.label}</Text>
                </Pressable>
              );
            })}
          </View>
        </View>
        <TextField
          label={workOrderType.locationLabel}
          value={location}
          onChangeText={(value) => {
            setLocation(value);
            clearFormError('location');
          }}
          multiline
          required
          errorMessage={formErrors.location}
        />
        <TextField
          label="定位坐标（经度,纬度）"
          value={gpsCoordinates}
          onChangeText={(value) => {
            setGpsCoordinates(value);
            clearFormError('gpsCoordinates');
          }}
          required
          errorMessage={formErrors.gpsCoordinates}
        />
        <AppButton
          title={gpsLoading ? '正在定位并解析地址' : '定位并填写地址'}
          onPress={collectLocation}
          variant="secondary"
          loading={gpsLoading}
          disabled={gpsLoading}
        />
        {!!locationHint && (
          <Text style={{ color: colors.muted, lineHeight: 20 }}>
            {locationHint}
          </Text>
        )}
        <TextField
          label={workOrderType.reasonLabel}
          value={reason}
          onChangeText={(value) => {
            setReason(value);
            clearFormError('reason');
          }}
          multiline
          required
          errorMessage={formErrors.reason}
        />
        {workOrderType.reportType === 'BOX_DOOR_OPEN' && (
          <TextField
            label="箱体数量"
            value={boxQuantity}
            onChangeText={(value) => {
              setBoxQuantity(value);
              clearFormError('boxQuantity');
            }}
            keyboardType="number-pad"
            required
            errorMessage={formErrors.boxQuantity}
          />
        )}
        <View style={{ flexDirection: 'row', gap: uiLayout.fieldGap }}>
          <View style={{ flex: 1, minWidth: 0 }}>
        <TextField
          label="联系人"
          value={contactPerson}
          placeholder={currentUser?.name ? `默认：${currentUser.name}` : undefined}
          onChangeText={(value) => {
            setContactPersonCustomized(true);
            setContactPerson(value);
            clearFormError('contactPerson');
          }}
          required
          errorMessage={formErrors.contactPerson}
        />
          </View>
          <View style={{ flex: 1, minWidth: 0 }}>
        <TextField
          label="联系电话"
          value={phone}
          placeholder={currentUser?.phone ? `默认：${currentUser.phone}` : undefined}
          onChangeText={(value) => {
            setPhoneCustomized(true);
            setPhone(value);
            clearFormError('phone');
          }}
          keyboardType="phone-pad"
          required
          errorMessage={formErrors.phone || (phone && !/^\d{7,15}$/.test(phone.trim()) ? '请输入7至15位联系电话' : undefined)}
        />
          </View>
        </View>
      </Card>

      <Card style={{ gap: 12 }}>
        <SectionHeader
          step={2}
          title="现场材料 *"
          subtitle={`至少上传一份图片或视频，最多${MAX_ATTACHMENT_COUNT}个，合计不超过${MAX_ATTACHMENT_TOTAL_MB}兆字节`}
        />
        <Text style={{ color: colors.muted }}>
          已选 {attachments.length} / {MAX_ATTACHMENT_COUNT} 个 · 合计 {formatAttachmentSize(getAttachmentTotalSize(attachments))} / {MAX_ATTACHMENT_TOTAL_MB}兆字节
        </Text>
        {!!formErrors.attachments && <Text style={{ color: colors.danger }}>{formErrors.attachments}</Text>}
        <View style={{ flexDirection: 'row', gap: 10 }}>
          <View style={{ flex: 1 }}>
            <AppButton title="拍摄" onPress={addFromCamera} variant="secondary" disabled={attachments.length >= MAX_ATTACHMENT_COUNT} />
          </View>
          <View style={{ flex: 1 }}>
            <AppButton title="相册" onPress={addFromLibrary} variant="secondary" disabled={attachments.length >= MAX_ATTACHMENT_COUNT} />
          </View>
        </View>
        {attachments.map((attachment, index) => (
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
            <AppButton title="移除" variant="danger" onPress={() => setAttachments((current) => current.filter((_, itemIndex) => itemIndex !== index))} />
          </View>
        ))}
      </Card>

      {!!mutation.error && (
        <Text selectable style={{ color: colors.danger }}>
          {formatApiErrorMessage(mutation.error, '提交失败，请稍后重试。')}
        </Text>
      )}

      {mutation.isPending && submitProgress && (
        <Card>
          <Text style={{ color: colors.text, fontWeight: '700' }}>正在上传现场材料</Text>
          <Text style={{ color: colors.muted }}>
            {submitProgress.message
              || `${submitProgress.fileName} · ${submitProgress.fileIndex + 1}/${submitProgress.fileCount}`}
          </Text>
        </Card>
      )}

      <AppButton
        title={mutation.isPending ? '正在提交' : '提交报单'}
        onPress={submitReport}
        disabled={mutation.isPending}
        loading={mutation.isPending}
      />
    </Screen>
  );
}

function formatGpsCoordinates(longitude: number, latitude: number) {
  return `${longitude.toFixed(6)},${latitude.toFixed(6)}`;
}

function parseGpsCoordinates(gpsCoordinates: string) {
  const parts = gpsCoordinates.split(',').map((part) => Number(part.trim()));
  const [longitude, latitude] = parts;
  if (
    parts.length !== 2
    || !Number.isFinite(longitude)
    || !Number.isFinite(latitude)
    || Math.abs(longitude) > 180
    || Math.abs(latitude) > 90
  ) {
    return null;
  }
  return { longitude, latitude };
}

function isLikelyMainlandChina(longitude: number, latitude: number) {
  return longitude >= 73 && longitude <= 136 && latitude >= 3 && latitude <= 54;
}

function formatLocationRequestError(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 0) {
      return `地址解析请求没有连接到服务器，可能是当前网络不稳定。定位坐标已保留，请稍后重试或手动填写地址。\n错误编号：${error.requestId}`;
    }
    if (error.status === 401 || error.status === 403) {
      return '登录状态已失效或当前账号无权解析地址，请重新登录后再试。';
    }
    if (error.status === 503) {
      return '定位服务暂时不可用，可能是地图密钥额度、服务器网络或地图服务异常。请稍后重试或手动填写地址。';
    }
  }
  if (error instanceof Error && (error.name === 'AbortError' || error.message.includes('aborted'))) {
      return '地址解析超时，请检查网络后重试，或直接手动填写工单地点。';
  }
  return formatApiErrorMessage(error, fallback);
}

async function formatGpsErrorMessageWithDiagnostics(
  error: unknown,
  resolvedDiagnostics?: DeviceLocationDiagnostics | null,
) {
  const baseMessage = formatGpsErrorMessage(error);
  const diagnostics = resolvedDiagnostics === undefined
    ? await getDeviceLocationDiagnostics()
    : resolvedDiagnostics;
  const diagnosticsSummary = formatLocationDiagnosticsSummary(diagnostics);
  if (!diagnosticsSummary || baseMessage.includes('诊断：')) {
    return baseMessage;
  }
  return `${baseMessage}\n${diagnosticsSummary}`;
}

function formatGpsErrorMessage(error: unknown) {
  if (!(error instanceof Error)) {
    return '无法获取定位坐标，请手动填写经纬度和地址。';
  }
  const message = error.message.toLowerCase();
  if (message.includes('permission') || message.includes('权限') || message.includes('authorized')) {
    return '定位权限未开启或只授予了模糊定位。请在系统设置中允许网络运维管理使用精确位置。';
  }
  if (message.includes('location provider') || message.includes('定位服务') || message.includes('services')) {
    return '手机系统定位服务不可用，请打开系统定位总开关，并确认卫星或网络定位可用。';
  }
  if (message.includes('gps_provider')) {
    return error.message;
  }
  if (message.includes('timeout') || message.includes('超时')) {
    return error.message;
  }
  if (message.includes('高德') || message.includes('amap') || message.includes('sdk key')) {
    return error.message;
  }
  if (message.includes('unavailable') || message.includes('无法') || message.includes('failed')) {
    return '系统暂时没有返回定位坐标。请到室外或窗边重试；如果仍失败，请手动填写经纬度和地址。';
  }
  return formatApiErrorMessage(error, '无法获取定位坐标，请手动填写经纬度和地址。');
}

function showLocationSettingsAlert(title: string, message: string) {
  Alert.alert(title, message, [
    { text: '稍后处理', style: 'cancel' },
    { text: '打开设置', onPress: () => Linking.openSettings().catch(() => undefined) },
  ]);
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, timeoutMessage: string) {
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  const timeoutPromise = new Promise<T>((_, reject) => {
    timeoutId = setTimeout(() => reject(new Error(timeoutMessage)), timeoutMs);
  });
  return Promise.race([promise, timeoutPromise]).finally(() => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
  });
}

import { Image, useImage } from 'expo-image';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View, type AccessibilityActionEvent, type LayoutChangeEvent } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { cancelAnimation, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  clampImageScale,
  constrainImageTransform,
  DOUBLE_TAP_IMAGE_SCALE,
  fitImageToViewport,
  MAX_IMAGE_SCALE,
  MIN_IMAGE_SCALE,
  zoomImageAtPoint,
  type ImageSize,
  type ImageTransform,
} from '../../../../shared/attachment-image-transform';
import { colors } from '@/ui/theme';

type AttachmentImagePreviewProps = {
  uri: string;
  label: string;
  onRetry: () => void;
};

const MAX_PREVIEW_IMAGE_DIMENSION = 3200;

export function AttachmentImagePreview({ uri, label, onRetry }: AttachmentImagePreviewProps) {
  const [viewport, setViewport] = useState<ImageSize>({ width: 0, height: 0 });
  const [loadFailed, setLoadFailed] = useState(false);
  const image = useImage(uri, {
    maxWidth: MAX_PREVIEW_IMAGE_DIMENSION,
    maxHeight: MAX_PREVIEW_IMAGE_DIMENSION,
    onError: () => setLoadFailed(true),
  });
  const imageSize = { width: image?.width ?? 0, height: image?.height ?? 0 };
  const imageFailed = loadFailed || Boolean(image && (imageSize.width <= 0 || imageSize.height <= 0));
  const [zoom, setZoom] = useState(MIN_IMAGE_SCALE);
  const fitted = fitImageToViewport(imageSize, viewport);
  const ready = !imageFailed && fitted.width > 0 && fitted.height > 0;
  const scale = useSharedValue(MIN_IMAGE_SCALE);
  const offsetX = useSharedValue(0);
  const offsetY = useSharedValue(0);
  const pinchStart = useSharedValue<ImageTransform>({ scale: MIN_IMAGE_SCALE, x: 0, y: 0 });
  const pinchOrigin = useSharedValue({ x: 0, y: 0 });
  const pinching = useSharedValue(false);
  const panning = useSharedValue(false);

  useEffect(() => {
    cancelAnimation(scale);
    cancelAnimation(offsetX);
    cancelAnimation(offsetY);
    scale.value = MIN_IMAGE_SCALE;
    offsetX.value = 0;
    offsetY.value = 0;
    pinching.value = false;
    panning.value = false;
    setZoom(MIN_IMAGE_SCALE);
  }, [uri, viewport.width, viewport.height, fitted.width, fitted.height, scale, offsetX, offsetY, pinching, panning]);

  const animateTransform = useCallback((next: ImageTransform) => {
    'worklet';
    const timing = { duration: 180, reduceMotion: ReduceMotion.System };
    scale.value = withTiming(next.scale, timing);
    offsetX.value = withTiming(next.x, timing);
    offsetY.value = withTiming(next.y, timing);
    scheduleOnRN(setZoom, next.scale);
  }, [scale, offsetX, offsetY]);

  const gesture = useMemo(() => {
    const image = { width: fitted.width, height: fitted.height };
    const bounds = { width: viewport.width, height: viewport.height };
    const pinch = Gesture.Pinch()
      .enabled(ready)
      .onStart((event) => {
        cancelAnimation(scale);
        cancelAnimation(offsetX);
        cancelAnimation(offsetY);
        pinching.value = true;
        pinchStart.value = { scale: scale.value, x: offsetX.value, y: offsetY.value };
        pinchOrigin.value = { x: event.focalX, y: event.focalY };
      })
      .onUpdate((event) => {
        const next = zoomImageAtPoint(pinchStart.value, pinchStart.value.scale * event.scale,
          pinchOrigin.value, { x: event.focalX, y: event.focalY }, image, bounds);
        scale.value = next.scale;
        offsetX.value = next.x;
        offsetY.value = next.y;
      })
      .onFinalize(() => {
        if (pinching.value) scheduleOnRN(setZoom, scale.value);
        pinching.value = false;
      });

    const pan = Gesture.Pan()
      .enabled(ready)
      .averageTouches(true)
      .minDistance(6)
      .onStart(() => {
        panning.value = true;
        cancelAnimation(scale);
        cancelAnimation(offsetX);
        cancelAnimation(offsetY);
      })
      .onChange((event) => {
        if (pinching.value || event.numberOfPointers !== 1) return;
        const next = constrainImageTransform({ scale: scale.value,
          x: offsetX.value + event.changeX, y: offsetY.value + event.changeY }, image, bounds);
        offsetX.value = next.x;
        offsetY.value = next.y;
      })
      .onFinalize(() => {
        if (panning.value && !pinching.value) scheduleOnRN(setZoom, scale.value);
        panning.value = false;
      });

    const doubleTap = Gesture.Tap()
      .enabled(ready)
      .numberOfTaps(2)
      .maxDistance(10)
      .maxDelay(250)
      .onEnd((event, success) => {
        if (!success) return;
        const focal = { x: event.x, y: event.y };
        const nextScale = scale.value > MIN_IMAGE_SCALE + 0.01 ? MIN_IMAGE_SCALE : DOUBLE_TAP_IMAGE_SCALE;
        animateTransform(zoomImageAtPoint({ scale: scale.value, x: offsetX.value, y: offsetY.value },
          nextScale, focal, focal, image, bounds));
      });

    return Gesture.Simultaneous(pinch, Gesture.Race(doubleTap, pan));
  }, [ready, fitted.width, fitted.height, viewport.width, viewport.height,
    scale, offsetX, offsetY, pinchStart, pinchOrigin, pinching, panning, animateTransform]);

  const imageTransform = useAnimatedStyle(() => ({
    transform: [{ translateX: offsetX.value }, { translateY: offsetY.value }, { scale: scale.value }],
  }));

  const changeZoom = (nextScale: number) => {
    if (!ready) return;
    const center = { x: viewport.width / 2, y: viewport.height / 2 };
    animateTransform(zoomImageAtPoint({ scale: scale.value, x: offsetX.value, y: offsetY.value },
      nextScale, center, center, fitted, viewport));
  };
  const measureViewport = ({ nativeEvent: { layout } }: LayoutChangeEvent) => {
    setViewport((previous) => previous.width === layout.width && previous.height === layout.height
      ? previous : { width: layout.width, height: layout.height });
  };
  const performAccessibilityAction = ({ nativeEvent: { actionName } }: AccessibilityActionEvent) => {
    if (!ready) return;
    if (actionName === 'increment') return changeZoom(zoom + 1);
    if (actionName === 'decrement') return changeZoom(zoom - 1);
    if (actionName === 'activate') return changeZoom(MIN_IMAGE_SCALE);
    const horizontal = actionName === 'moveLeft' ? -1 : actionName === 'moveRight' ? 1 : 0;
    const vertical = actionName === 'moveUp' ? -1 : actionName === 'moveDown' ? 1 : 0;
    animateTransform(constrainImageTransform({ scale: scale.value,
      x: offsetX.value + horizontal * viewport.width / 4,
      y: offsetY.value + vertical * viewport.height / 4 }, fitted, viewport));
  };
  const percentage = Math.round(zoom * 100);

  return (
    <GestureHandlerRootView style={styles.root}>
      <GestureDetector gesture={gesture}>
        <View
          collapsable={false}
          style={styles.viewport}
          onLayout={measureViewport}
          accessible={!imageFailed}
          accessibilityRole="image"
          accessibilityLabel={label}
          accessibilityHint="可双指缩放、双击放大或还原，放大后可拖动；也可使用下方缩放按钮。"
          accessibilityValue={{ min: 100, max: 500, now: percentage, text: `${percentage}%` }}
          accessibilityActions={[
            { name: 'increment', label: '放大图片' }, { name: 'decrement', label: '缩小图片' },
            { name: 'activate', label: '还原为适应屏幕' },
            { name: 'moveLeft', label: '向左移动图片' }, { name: 'moveRight', label: '向右移动图片' },
            { name: 'moveUp', label: '向上移动图片' }, { name: 'moveDown', label: '向下移动图片' },
          ]}
          onAccessibilityAction={performAccessibilityAction}
        >
          {image && !imageFailed && (
            <Animated.View style={[{ width: fitted.width || viewport.width || 1, height: fitted.height || viewport.height || 1 }, imageTransform]}>
              <Image
                source={image}
                style={StyleSheet.absoluteFill}
                contentFit="contain"
                accessible={false}
                onError={() => setLoadFailed(true)}
              />
            </Animated.View>
          )}
          {!ready && !imageFailed && <ActivityIndicator style={StyleSheet.absoluteFill} color={colors.surface} size="large" />}
          {imageFailed && (
            <View style={styles.errorState}>
              <Text accessibilityLiveRegion="polite" style={styles.hint}>图片无法显示，请重新加载。</Text>
              <Pressable accessibilityRole="button" onPress={onRetry} style={({ pressed }) => [styles.control, pressed && styles.pressed]}>
                <Text style={styles.controlText}>重新加载</Text>
              </Pressable>
            </View>
          )}
        </View>
      </GestureDetector>
      <View style={styles.toolbar}>
        <View style={styles.controls}>
          <Pressable accessibilityRole="button" accessibilityLabel="缩小图片" disabled={!ready || zoom <= MIN_IMAGE_SCALE}
            onPress={() => changeZoom(clampImageScale(zoom - 1))}
            style={({ pressed }) => [styles.control, (!ready || zoom <= MIN_IMAGE_SCALE) && styles.disabled, pressed && styles.pressed]}>
            <Text allowFontScaling={false} style={styles.symbol}>−</Text>
          </Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel={`当前缩放 ${percentage}%，还原为适应屏幕`}
            disabled={!ready} onPress={() => changeZoom(MIN_IMAGE_SCALE)}
            style={({ pressed }) => [styles.control, styles.resetControl, !ready && styles.disabled, pressed && styles.pressed]}>
            <Text style={styles.controlText}>{percentage}% · 还原</Text>
          </Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel="放大图片" disabled={!ready || zoom >= MAX_IMAGE_SCALE}
            onPress={() => changeZoom(clampImageScale(zoom + 1))}
            style={({ pressed }) => [styles.control, (!ready || zoom >= MAX_IMAGE_SCALE) && styles.disabled, pressed && styles.pressed]}>
            <Text allowFontScaling={false} style={styles.symbol}>+</Text>
          </Pressable>
        </View>
        <Text style={styles.hint}>双指缩放 · 双击放大或还原 · 放大后拖动</Text>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  viewport: { flex: 1, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  toolbar: { padding: 12, gap: 10, backgroundColor: colors.ink },
  controls: { width: '100%', maxWidth: 344, alignSelf: 'center', flexDirection: 'row', justifyContent: 'center', alignItems: 'stretch', gap: 12 },
  control: { minWidth: 48, minHeight: 48, paddingHorizontal: 12, borderRadius: 12,
    backgroundColor: colors.surfaceAlt, alignItems: 'center', justifyContent: 'center' },
  resetControl: { flex: 1, minWidth: 0, maxWidth: 224 },
  controlText: { color: colors.text, fontSize: 15, fontWeight: '700', textAlign: 'center' },
  symbol: { color: colors.text, fontSize: 24, fontWeight: '600' },
  hint: { color: colors.surface, fontSize: 13, lineHeight: 20, textAlign: 'center' },
  errorState: { maxWidth: '90%', alignItems: 'center', gap: 16 },
  pressed: { opacity: 0.72 },
  disabled: { opacity: 0.4 },
});

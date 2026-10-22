export type ImageSize = { width: number; height: number };
export type ImagePoint = { x: number; y: number };
export type ImageTransform = { scale: number; x: number; y: number };

export const MIN_IMAGE_SCALE = 1;
export const MAX_IMAGE_SCALE = 5;
export const DOUBLE_TAP_IMAGE_SCALE = 2.5;

export function clampImageScale(scale: number): number {
  'worklet';
  return Number.isFinite(scale) ? Math.min(MAX_IMAGE_SCALE, Math.max(MIN_IMAGE_SCALE, scale)) : MIN_IMAGE_SCALE;
}

export function fitImageToViewport(image: ImageSize, viewport: ImageSize): ImageSize {
  'worklet';
  if (![image.width, image.height, viewport.width, viewport.height].every((value) => Number.isFinite(value) && value > 0)) {
    return { width: 0, height: 0 };
  }
  const ratio = Math.min(viewport.width / image.width, viewport.height / image.height);
  return { width: image.width * ratio, height: image.height * ratio };
}

export function constrainImageTransform(transform: ImageTransform, image: ImageSize, viewport: ImageSize): ImageTransform {
  'worklet';
  const scale = clampImageScale(transform.scale);
  const maxX = Math.max(0, (image.width * scale - viewport.width) / 2);
  const maxY = Math.max(0, (image.height * scale - viewport.height) / 2);
  return {
    scale,
    x: maxX > 0 && Number.isFinite(transform.x) ? Math.min(maxX, Math.max(-maxX, transform.x)) : 0,
    y: maxY > 0 && Number.isFinite(transform.y) ? Math.min(maxY, Math.max(-maxY, transform.y)) : 0,
  };
}

export function zoomImageAtPoint(
  transform: ImageTransform,
  nextScale: number,
  origin: ImagePoint,
  focal: ImagePoint,
  image: ImageSize,
  viewport: ImageSize,
): ImageTransform {
  'worklet';
  const scale = clampImageScale(nextScale);
  const ratio = scale / clampImageScale(transform.scale);
  // Coordinates are relative to the stable viewport, not the transformed image.
  return constrainImageTransform({
    scale,
    x: focal.x - viewport.width / 2 - (origin.x - viewport.width / 2 - transform.x) * ratio,
    y: focal.y - viewport.height / 2 - (origin.y - viewport.height / 2 - transform.y) * ratio,
  }, image, viewport);
}

import assert from 'node:assert/strict';
import test from 'node:test';

import {
  clampImageScale,
  constrainImageTransform,
  fitImageToViewport,
  zoomImageAtPoint,
} from '../../shared/attachment-image-transform.ts';

const viewport = { width: 400, height: 600 };
const portrait = { width: 400, height: 600 };
const centered = { scale: 1, x: 0, y: 0 };

test('fits portrait, landscape and small images without changing their aspect ratio', () => {
  assert.deepEqual(fitImageToViewport({ width: 800, height: 1200 }, viewport), portrait);
  assert.deepEqual(fitImageToViewport({ width: 1600, height: 800 }, viewport), { width: 400, height: 200 });
  assert.deepEqual(fitImageToViewport({ width: 100, height: 100 }, viewport), { width: 400, height: 400 });
  const rotated = fitImageToViewport(portrait, { width: 600, height: 400 });
  assert.equal(rotated.height, 400);
  assert.ok(Math.abs(rotated.width - 800 / 3) < 1e-9);
});

test('rejects invalid sizes and keeps scale between fit-to-screen and five times', () => {
  for (const width of [0, -10, Number.NaN, Number.POSITIVE_INFINITY]) {
    assert.deepEqual(fitImageToViewport({ width, height: 600 }, viewport), { width: 0, height: 0 });
  }
  assert.equal(clampImageScale(0.2), 1);
  assert.equal(clampImageScale(1.7), 1.7);
  assert.equal(clampImageScale(30), 5);
  assert.equal(clampImageScale(Number.NaN), 1);
});

test('preserves the pixel under the pinch focal point when zooming', () => {
  const focal = { x: 300, y: 400 };
  const result = zoomImageAtPoint(centered, 2, focal, focal, portrait, viewport);
  assert.deepEqual(result, { scale: 2, x: -100, y: -100 });
  assert.equal((focal.x - viewport.width / 2 - result.x) / result.scale, focal.x - viewport.width / 2);
  assert.equal((focal.y - viewport.height / 2 - result.y) / result.scale, focal.y - viewport.height / 2);
});

test('tracks a moving two-finger focus and continues from an already panned image', () => {
  assert.deepEqual(zoomImageAtPoint({ scale: 2, x: 30, y: -20 }, 3,
    { x: 200, y: 300 }, { x: 240, y: 330 }, portrait, viewport), { scale: 3, x: 85, y: 0 });
});

test('constrains each axis to the actual fitted image and keeps letterboxed axes centered', () => {
  const landscape = { width: 400, height: 200 };
  assert.deepEqual(constrainImageTransform({ scale: 2, x: 900, y: 900 }, landscape, viewport), { scale: 2, x: 200, y: 0 });
  assert.deepEqual(constrainImageTransform({ scale: 5, x: -9000, y: -9000 }, landscape, viewport), { scale: 5, x: -800, y: -200 });
  assert.deepEqual(constrainImageTransform({ scale: 1, x: 900, y: -900 }, portrait, viewport), centered);
});

test('clamps excessive pinch scale before computing translation and recenters when reset', () => {
  const middle = { x: 200, y: 300 };
  assert.deepEqual(zoomImageAtPoint({ scale: 2, x: 40, y: 60 }, 50, middle, middle, portrait, viewport),
    { scale: 5, x: 100, y: 150 });
  assert.deepEqual(zoomImageAtPoint({ scale: 4, x: 300, y: -200 }, 1, middle, middle, portrait, viewport), centered);
  assert.deepEqual(constrainImageTransform({ scale: 2, x: Number.NaN, y: Number.NaN }, portrait, viewport),
    { scale: 2, x: 0, y: 0 });
});

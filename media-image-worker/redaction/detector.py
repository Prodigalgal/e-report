from __future__ import annotations

from dataclasses import dataclass
import hashlib
from pathlib import Path

import cv2
import numpy as np

from redaction.ocr import (
    BrandTextDetection,
    CmccBrandTextDetector,
    expand_brand_text_detection,
)


INPUT_SIZE = 384
PRECISION_WINDOW_SIZE = 768
PRECISION_WINDOW_OVERLAP = 0.25
DENSE_WINDOW_SIZE = 1536
DENSE_WINDOW_OVERLAP = 0.25
DENSE_SCAN_MAX_WINDOWS = 16
LARGE_DETECTION_AREA_RATIO = 0.04
LARGE_DETECTION_SIDE_RATIO = 0.45


@dataclass(frozen=True)
class Detection:
    left: int
    top: int
    right: int
    bottom: int
    confidence: float


class CmccBrandDetector:
    def __init__(
        self,
        model_path: Path,
        expected_sha256: str,
        confidence_threshold: float,
        nms_threshold: float,
        grayscale_inference: bool = False,
        dense_scan: bool = False,
        ocr_enabled: bool = False,
        ocr_max_side: int = 1600,
        ocr_min_confidence: float = 0.65,
        standalone_model_confidence: float = 0.90,
    ) -> None:
        actual_sha256 = sha256_file(model_path)
        if actual_sha256 != expected_sha256:
            raise RuntimeError(
                f"Model checksum mismatch: expected={expected_sha256}, actual={actual_sha256}"
            )
        self._network = cv2.dnn.readNetFromONNX(str(model_path))
        self._confidence_threshold = confidence_threshold
        self._nms_threshold = nms_threshold
        self._grayscale_inference = grayscale_inference
        self._dense_scan = dense_scan
        self._standalone_model_confidence = standalone_model_confidence
        self._text_detector = (
            CmccBrandTextDetector(ocr_max_side, ocr_min_confidence)
            if ocr_enabled
            else None
        )

    def detect(self, image_bgr: np.ndarray) -> list[Detection]:
        model_detections = self._detect_model(image_bgr)
        if self._text_detector is None:
            return model_detections

        text_detections = self._text_detector.detect(image_bgr)
        associated_text = {
            id(text): any(is_near_text(detection, text) for detection in model_detections)
            for text in text_detections
        }
        accepted_model_detections = [
            detection
            for detection in model_detections
            if detection.confidence >= self._standalone_model_confidence
            or any(is_near_text(detection, text) for text in text_detections)
        ]
        height, width = image_bgr.shape[:2]
        for text in text_detections:
            candidate = (
                text
                if associated_text[id(text)]
                else expand_brand_text_detection(text, width, height)
            )
            accepted_model_detections.append(
                Detection(
                    left=candidate.left,
                    top=candidate.top,
                    right=candidate.right,
                    bottom=candidate.bottom,
                    confidence=candidate.confidence,
                )
            )
        return suppress_overlaps(
            accepted_model_detections,
            self._confidence_threshold,
            self._nms_threshold,
        )

    def _detect_model(self, image_bgr: np.ndarray) -> list[Detection]:
        height, width = image_bgr.shape[:2]
        detections: list[Detection] = []
        for left, top, right, bottom in inference_windows(width, height):
            window = image_bgr[top:bottom, left:right]
            detections.extend(
                Detection(
                    left=item.left + left,
                    top=item.top + top,
                    right=item.right + left,
                    bottom=item.bottom + top,
                    confidence=item.confidence,
                )
                for item in self._detect_window(window)
            )
        coarse_detections = suppress_overlaps(detections, self._confidence_threshold, self._nms_threshold)
        if not coarse_detections and not self._dense_scan:
            return []

        precision_detections: list[Detection] = []
        for detection in coarse_detections:
            if is_large_detection(detection, width, height):
                precision_detections.extend(
                    self._detect_precision_region(image_bgr, detection)
                )
            else:
                precision_detections.append(detection)

        precision_detections = suppress_overlaps(
            precision_detections,
            self._confidence_threshold,
            self._nms_threshold,
        )
        if self._dense_scan and (not precision_detections or len(precision_detections) < len(coarse_detections)):
            precision_detections.extend(self._detect_dense_regions(image_bgr))
        verified_detections = [
            detection
            for detection in precision_detections
            if not is_qr_like_crop(image_bgr, detection)
        ]
        return suppress_overlaps(
            verified_detections,
            self._confidence_threshold,
            self._nms_threshold,
        )

    def _detect_dense_regions(self, image_bgr: np.ndarray) -> list[Detection]:
        height, width = image_bgr.shape[:2]
        if max(width, height) <= 1024:
            return []
        detections: list[Detection] = []
        for left, top, right, bottom in dense_inference_windows(width, height):
            window = image_bgr[top:bottom, left:right]
            detections.extend(
                Detection(
                    left=item.left + left,
                    top=item.top + top,
                    right=item.right + left,
                    bottom=item.bottom + top,
                    confidence=item.confidence,
                )
                for item in self._detect_window(window)
            )
        return detections

    def _detect_precision_region(
        self,
        image_bgr: np.ndarray,
        coarse_detection: Detection,
    ) -> list[Detection]:
        detections: list[Detection] = []
        for left, top, right, bottom in precision_windows(
                coarse_detection.left,
                coarse_detection.top,
                coarse_detection.right,
                coarse_detection.bottom,
        ):
            window = image_bgr[top:bottom, left:right]
            detections.extend(
                Detection(
                    left=item.left + left,
                    top=item.top + top,
                    right=item.right + left,
                    bottom=item.bottom + top,
                    confidence=item.confidence,
                )
                for item in self._detect_window(window)
            )
        return detections

    def _detect_window(self, image_bgr: np.ndarray) -> list[Detection]:
        height, width = image_bgr.shape[:2]
        if self._grayscale_inference:
            grayscale = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
            image_bgr = cv2.cvtColor(grayscale, cv2.COLOR_GRAY2BGR)
        resized, scale, offset_x, offset_y = letterbox(image_bgr, INPUT_SIZE)
        blob = cv2.dnn.blobFromImage(
            resized,
            scalefactor=1.0 / 255.0,
            size=(INPUT_SIZE, INPUT_SIZE),
            mean=(0.0, 0.0, 0.0),
            swapRB=True,
            crop=False,
        )
        self._network.setInput(blob)
        output = np.asarray(self._network.forward())
        if output.ndim != 4 or output.shape[0] != 1 or output.shape[1] != 5:
            raise RuntimeError(f"Unexpected detector output shape: {output.shape}")

        grid_height, grid_width = output.shape[2:]
        objectness = sigmoid(output[0, 0])
        boxes: list[list[int]] = []
        scores: list[float] = []
        for grid_y, grid_x in zip(*np.where(objectness >= self._confidence_threshold)):
            score = float(objectness[grid_y, grid_x])
            center_x = (grid_x + float(sigmoid(output[0, 1, grid_y, grid_x]))) / grid_width
            center_y = (grid_y + float(sigmoid(output[0, 2, grid_y, grid_x]))) / grid_height
            box_width = float(sigmoid(output[0, 3, grid_y, grid_x]))
            box_height = float(sigmoid(output[0, 4, grid_y, grid_x]))
            left = (center_x - box_width / 2.0) * INPUT_SIZE
            top = (center_y - box_height / 2.0) * INPUT_SIZE
            box_w = box_width * INPUT_SIZE
            box_h = box_height * INPUT_SIZE
            source_left = int(round((left - offset_x) / scale))
            source_top = int(round((top - offset_y) / scale))
            source_width = int(round(box_w / scale))
            source_height = int(round(box_h / scale))
            clipped = clip_box(source_left, source_top, source_width, source_height, width, height)
            if clipped[2] <= 1 or clipped[3] <= 1:
                continue
            boxes.append(clipped)
            scores.append(score)

        detections: list[Detection] = []
        for selected_index in np.asarray(
            cv2.dnn.NMSBoxes(boxes, scores, self._confidence_threshold, self._nms_threshold)
        ).reshape(-1):
            left, top, box_width, box_height = boxes[int(selected_index)]
            detections.append(
                Detection(
                    left=left,
                    top=top,
                    right=left + box_width,
                    bottom=top + box_height,
                    confidence=scores[int(selected_index)],
                )
            )
        return detections


def inference_windows(width: int, height: int) -> list[tuple[int, int, int, int]]:
    windows = [(0, 0, width, height)]
    if max(width, height) <= 1024:
        return windows
    tile_width = max(640, int(round(width * 0.62)))
    tile_height = max(640, int(round(height * 0.62)))
    tile_width = min(width, tile_width)
    tile_height = min(height, tile_height)
    for left in sorted({0, width - tile_width}):
        for top in sorted({0, height - tile_height}):
            window = (left, top, left + tile_width, top + tile_height)
            if window not in windows:
                windows.append(window)
    return windows


def dense_inference_windows(
    width: int,
    height: int,
    window_size: int = DENSE_WINDOW_SIZE,
    overlap: float = DENSE_WINDOW_OVERLAP,
) -> list[tuple[int, int, int, int]]:
    """Return a bounded overlapping square grid for small or edge-positioned marks."""
    if max(width, height) <= 1024:
        return []
    size = min(window_size, width, height)
    x_starts = sliding_window_starts(0, width, size, overlap)
    y_starts = sliding_window_starts(0, height, size, overlap)
    windows = [
        (x, y, min(width, x + size), min(height, y + size))
        for y in y_starts
        for x in x_starts
    ]
    return windows[:DENSE_SCAN_MAX_WINDOWS]


def precision_windows(
    left: int,
    top: int,
    right: int,
    bottom: int,
    window_size: int = PRECISION_WINDOW_SIZE,
    overlap: float = PRECISION_WINDOW_OVERLAP,
) -> list[tuple[int, int, int, int]]:
    """Return a small overlapping grid used only for oversized coarse boxes."""
    if right <= left or bottom <= top:
        return []
    width = right - left
    height = bottom - top
    size = min(window_size, max(width, height))
    x_starts = sliding_window_starts(left, right, size, overlap)
    y_starts = sliding_window_starts(top, bottom, size, overlap)
    return [
        (x, y, min(right, x + size), min(bottom, y + size))
        for y in y_starts
        for x in x_starts
    ]


def sliding_window_starts(start: int, end: int, size: int, overlap: float) -> list[int]:
    span = end - start
    if span <= size:
        return [start]
    step = max(1, int(round(size * (1.0 - overlap))))
    starts = list(range(start, end - size + 1, step))
    final_start = end - size
    if not starts or starts[-1] != final_start:
        starts.append(final_start)
    return list(dict.fromkeys(starts))


def is_large_detection(detection: Detection, image_width: int, image_height: int) -> bool:
    detection_width = max(0, detection.right - detection.left)
    detection_height = max(0, detection.bottom - detection.top)
    area_ratio = detection_width * detection_height / max(1, image_width * image_height)
    return (
        area_ratio >= LARGE_DETECTION_AREA_RATIO
        or detection_width / max(1, image_width) >= LARGE_DETECTION_SIDE_RATIO
        or detection_height / max(1, image_height) >= LARGE_DETECTION_SIDE_RATIO
    )


def is_qr_like_crop(image_bgr: np.ndarray, detection: Detection) -> bool:
    """Reject a candidate when a QR finder pattern occupies most of its box.

    This is a negative-only guard. It never accepts a candidate and it never
    looks at colour, so a logo next to a QR code can still be detected as a
    separate box by the trained detector.
    """
    crop = image_bgr[
        max(0, detection.top):max(0, detection.bottom),
        max(0, detection.left):max(0, detection.right),
    ]
    if crop.size == 0 or min(crop.shape[:2]) < 32:
        return False
    try:
        detected, points = cv2.QRCodeDetector().detect(crop)
    except cv2.error:
        return False
    if not detected or points is None:
        return False
    polygon = np.asarray(points, dtype=np.float32).reshape(-1, 2)
    polygon_area = abs(float(cv2.contourArea(polygon)))
    crop_area = float(crop.shape[0] * crop.shape[1])
    return polygon_area / max(1.0, crop_area) >= 0.30


def is_near_text(detection: Detection, text: BrandTextDetection) -> bool:
    if (
        detection.left < text.right
        and text.left < detection.right
        and detection.top < text.bottom
        and text.top < detection.bottom
    ):
        return True
    horizontal_gap = max(text.left - detection.right, detection.left - text.right, 0)
    vertical_gap = max(text.top - detection.bottom, detection.top - text.bottom, 0)
    text_height = max(1, text.bottom - text.top)
    detection_height = max(1, detection.bottom - detection.top)
    return max(horizontal_gap, vertical_gap) <= max(24, 2 * max(text_height, detection_height))


def suppress_overlaps(
    detections: list[Detection],
    confidence_threshold: float,
    nms_threshold: float,
) -> list[Detection]:
    if not detections:
        return []
    boxes = [
        [item.left, item.top, item.right - item.left, item.bottom - item.top]
        for item in detections
    ]
    scores = [item.confidence for item in detections]
    selected = cv2.dnn.NMSBoxes(boxes, scores, confidence_threshold, nms_threshold)
    return [detections[int(index)] for index in np.asarray(selected).reshape(-1)]


def letterbox(image: np.ndarray, size: int) -> tuple[np.ndarray, float, int, int]:
    height, width = image.shape[:2]
    scale = min(size / width, size / height)
    resized_width = max(1, int(round(width * scale)))
    resized_height = max(1, int(round(height * scale)))
    resized = cv2.resize(image, (resized_width, resized_height), interpolation=cv2.INTER_AREA)
    canvas = np.full((size, size, 3), 114, dtype=np.uint8)
    offset_x = (size - resized_width) // 2
    offset_y = (size - resized_height) // 2
    canvas[offset_y:offset_y + resized_height, offset_x:offset_x + resized_width] = resized
    return canvas, scale, offset_x, offset_y


def clip_box(left: int, top: int, width: int, height: int, image_width: int, image_height: int) -> list[int]:
    clipped_left = max(0, min(left, image_width - 1))
    clipped_top = max(0, min(top, image_height - 1))
    clipped_right = max(clipped_left + 1, min(left + width, image_width))
    clipped_bottom = max(clipped_top + 1, min(top + height, image_height))
    return [clipped_left, clipped_top, clipped_right - clipped_left, clipped_bottom - clipped_top]


def sigmoid(value: np.ndarray | np.float32) -> np.ndarray | np.float32:
    return 1.0 / (1.0 + np.exp(-np.clip(value, -30.0, 30.0)))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as model_file:
        for chunk in iter(lambda: model_file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

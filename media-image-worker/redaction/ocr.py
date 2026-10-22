from __future__ import annotations

from dataclasses import dataclass
from difflib import SequenceMatcher
import re

import cv2
import numpy as np


OCR_MAX_SIDE = 1600
OCR_MIN_CONFIDENCE = 0.65


@dataclass(frozen=True)
class BrandTextDetection:
    left: int
    top: int
    right: int
    bottom: int
    confidence: float
    text: str


class CmccBrandTextDetector:
    """Optional second-stage OCR that accepts only China Mobile brand text."""

    def __init__(
        self,
        max_side: int = OCR_MAX_SIDE,
        minimum_confidence: float = OCR_MIN_CONFIDENCE,
    ) -> None:
        if max_side < 256:
            raise ValueError("OCR max_side must be at least 256")
        if not 0.0 < minimum_confidence <= 1.0:
            raise ValueError("OCR minimum_confidence must be between 0 and 1")
        try:
            from rapidocr_onnxruntime import RapidOCR
        except ImportError as exception:
            raise RuntimeError(
                "OCR is enabled but rapidocr-onnxruntime is not installed"
            ) from exception
        self._ocr = RapidOCR()
        self._max_side = max_side
        self._minimum_confidence = minimum_confidence

    def detect(self, image_bgr: np.ndarray) -> list[BrandTextDetection]:
        height, width = image_bgr.shape[:2]
        scale = min(1.0, self._max_side / max(height, width))
        if scale < 1.0:
            resized = cv2.resize(
                image_bgr,
                (max(1, round(width * scale)), max(1, round(height * scale))),
                interpolation=cv2.INTER_AREA,
            )
        else:
            resized = image_bgr
        results, _ = self._ocr(resized)
        detections: list[BrandTextDetection] = []
        for result in results or []:
            if len(result) < 3:
                continue
            points, text, confidence = result[0], str(result[1]), float(result[2])
            if confidence < self._minimum_confidence:
                continue
            if not is_target_text(text):
                continue
            polygon = np.asarray(points, dtype=np.float32).reshape(-1, 2)
            if polygon.shape[0] < 4:
                continue
            left = max(0, int(np.floor(float(polygon[:, 0].min()) / scale)))
            top = max(0, int(np.floor(float(polygon[:, 1].min()) / scale)))
            right = min(width, int(np.ceil(float(polygon[:, 0].max()) / scale)))
            bottom = min(height, int(np.ceil(float(polygon[:, 1].max()) / scale)))
            if right - left < 4 or bottom - top < 4:
                continue
            detections.append(
                BrandTextDetection(left, top, right, bottom, confidence, text)
            )
        return deduplicate(detections)


def is_target_text(text: str) -> bool:
    compact = re.sub(r"[^a-z0-9\u4e00-\u9fff]", "", text.lower())
    if "中国移动" in compact:
        return True
    # Perspective/occlusion can drop the first Chinese character.  Keep this
    # narrow fallback for a company-name context, not for generic text.
    if "移动通信" in compact and any(marker in compact for marker in ("集团", "有限公司", "china")):
        return True
    letters = re.sub(r"[^a-z]", "", text.lower())
    if len(letters) < 8:
        return False
    if "chinamobile" in letters:
        return True
    return SequenceMatcher(None, letters, "chinamobile").ratio() >= 0.82


def deduplicate(
    detections: list[BrandTextDetection],
) -> list[BrandTextDetection]:
    ordered = sorted(detections, key=lambda item: item.confidence, reverse=True)
    result: list[BrandTextDetection] = []
    for candidate in ordered:
        if any(intersection_over_union(candidate, existing) >= 0.75 for existing in result):
            continue
        result.append(candidate)
    return result


def expand_brand_text_detection(
    detection: BrandTextDetection,
    image_width: int,
    image_height: int,
) -> BrandTextDetection:
    """Expand one semantic text hit toward a neighbouring lock-up symbol."""
    text_height = max(1, detection.bottom - detection.top)
    horizontal_expansion = min(120, max(8, round(text_height * 0.65)))
    top_expansion = min(160, max(8, round(text_height * 1.55)))
    bottom_expansion = min(64, max(4, round(text_height * 0.45)))
    return BrandTextDetection(
        left=max(0, detection.left - horizontal_expansion),
        top=max(0, detection.top - top_expansion),
        right=min(image_width, detection.right + min(80, max(8, round(text_height * 0.35)))),
        bottom=min(image_height, detection.bottom + bottom_expansion),
        confidence=detection.confidence,
        text=detection.text,
    )

def intersection_over_union(
    left: BrandTextDetection,
    right: BrandTextDetection,
) -> float:
    intersection_left = max(left.left, right.left)
    intersection_top = max(left.top, right.top)
    intersection_right = min(left.right, right.right)
    intersection_bottom = min(left.bottom, right.bottom)
    intersection = max(0, intersection_right - intersection_left) * max(
        0, intersection_bottom - intersection_top
    )
    left_area = max(0, left.right - left.left) * max(0, left.bottom - left.top)
    right_area = max(0, right.right - right.left) * max(0, right.bottom - right.top)
    return intersection / max(1, left_area + right_area - intersection)

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import tempfile
import time

import cv2
import numpy as np
from PIL import Image, ImageFile, UnidentifiedImageError
from pillow_heif import register_heif_opener

from redaction.detector import CmccBrandDetector, Detection


register_heif_opener()
ImageFile.LOAD_TRUNCATED_IMAGES = False


class ImageRejectedError(RuntimeError):
    def __init__(self, error_code: str, public_message: str) -> None:
        super().__init__(public_message)
        self.error_code = error_code
        self.public_message = public_message


@dataclass(frozen=True)
class ProcessResult:
    output_stored_file_name: str
    output_original_file_name: str
    output_content_type: str
    output_file_size: int
    output_sha256: str
    detected_regions: int
    duration_ms: int


class ImageRedactionProcessor:
    def __init__(
        self,
        detector: CmccBrandDetector,
        storage_root: Path,
        max_file_bytes: int,
        max_pixels: int,
        max_side: int,
        padding_ratio: float,
        max_padding_pixels: int,
    ) -> None:
        self._detector = detector
        self._storage_root = storage_root.resolve()
        self._max_file_bytes = max_file_bytes
        self._max_pixels = max_pixels
        self._max_side = max_side
        self._padding_ratio = padding_ratio
        self._max_padding_pixels = max_padding_pixels
        (self._storage_root / "_sanitized").mkdir(parents=True, exist_ok=True)

    def process(self, job: dict[str, object]) -> ProcessResult:
        started = time.perf_counter()
        source_relative = str(job["source_stored_file_name"])
        source_path = source_path_for_attachment(self._storage_root, source_relative)
        if not source_path.is_file():
            raise ImageRejectedError("SOURCE_MISSING", "图片文件不存在，请重新上传。")
        if source_path.stat().st_size > self._max_file_bytes:
            raise ImageRejectedError("FILE_TOO_LARGE", "图片超过安全处理上限，请压缩后重新上传。")

        try:
            with Image.open(source_path) as source_image:
                source_image.load()
                width, height = source_image.size
                if width <= 0 or height <= 0 or width > self._max_side or height > self._max_side:
                    raise ImageRejectedError("IMAGE_DIMENSION_LIMIT", "图片尺寸过大，请缩小后重新上传。")
                if width * height > self._max_pixels:
                    raise ImageRejectedError("IMAGE_PIXEL_LIMIT", "图片像素总量过大，请缩小后重新上传。")
                rgb_image = source_image.convert("RGB")
        except ImageRejectedError:
            raise
        except (UnidentifiedImageError, OSError, ValueError) as exception:
            raise ImageRejectedError("IMAGE_DECODE_FAILED", "图片内容损坏或格式不受支持，请转换为 JPG 后重新上传。") from exception

        image_bgr = cv2.cvtColor(np.asarray(rgb_image), cv2.COLOR_RGB2BGR)
        detections = self._detector.detect(image_bgr)
        merged_detections = merge_overlapping(detections)
        blur_detections = expand_detections(
            merged_detections,
            width,
            height,
            self._padding_ratio,
            self._max_padding_pixels,
        )
        for detection in blur_detections:
            blur_region(image_bgr, detection)

        job_id = str(job["id"])
        output_relative = f"_sanitized/{job_id}.jpg"
        output_path = safe_path(self._storage_root, output_relative, required_prefix="_sanitized")
        output_original_name = sanitized_name(str(job.get("original_file_name") or "现场材料"))
        output_bytes = encode_jpeg(image_bgr)
        write_atomic(output_path, output_bytes)
        duration_ms = int((time.perf_counter() - started) * 1000)
        return ProcessResult(
            output_stored_file_name=output_relative,
            output_original_file_name=output_original_name,
            output_content_type="image/jpeg",
            output_file_size=len(output_bytes),
            output_sha256=hashlib.sha256(output_bytes).hexdigest(),
            detected_regions=len(blur_detections),
            duration_ms=duration_ms,
        )

def expand_detections(
    detections: list[Detection],
    width: int,
    height: int,
    padding_ratio: float,
    max_padding_pixels: int,
) -> list[Detection]:
    expanded: list[Detection] = []
    bounded_max_padding = max(2, max_padding_pixels)
    for detection in detections:
        padding_x = min(
            bounded_max_padding,
            max(2, round((detection.right - detection.left) * padding_ratio)),
        )
        padding_y = min(
            bounded_max_padding,
            max(2, round((detection.bottom - detection.top) * padding_ratio)),
        )
        expanded.append(
            Detection(
                left=max(0, detection.left - padding_x),
                top=max(0, detection.top - padding_y),
                right=min(width, detection.right + padding_x),
                bottom=min(height, detection.bottom + padding_y),
                confidence=detection.confidence,
            )
        )
    return expanded


def merge_overlapping(detections: list[Detection]) -> list[Detection]:
    pending = sorted(detections, key=lambda item: item.confidence, reverse=True)
    merged: list[Detection] = []
    while pending:
        current = pending.pop(0)
        changed = True
        while changed:
            changed = False
            remaining: list[Detection] = []
            for candidate in pending:
                if intersects(current, candidate):
                    current = Detection(
                        min(current.left, candidate.left),
                        min(current.top, candidate.top),
                        max(current.right, candidate.right),
                        max(current.bottom, candidate.bottom),
                        max(current.confidence, candidate.confidence),
                    )
                    changed = True
                else:
                    remaining.append(candidate)
            pending = remaining
        merged.append(current)
    return merged


def intersects(left: Detection, right: Detection) -> bool:
    return left.left < right.right and right.left < left.right and left.top < right.bottom and right.top < left.bottom


def blur_region(image_bgr: np.ndarray, detection: Detection) -> None:
    region = image_bgr[detection.top:detection.bottom, detection.left:detection.right]
    if region.size == 0:
        return
    minimum_side = min(region.shape[0], region.shape[1])
    kernel = max(9, (minimum_side // 4) | 1)
    kernel = min(kernel, 99)
    image_bgr[detection.top:detection.bottom, detection.left:detection.right] = cv2.GaussianBlur(
        region,
        (kernel, kernel),
        sigmaX=max(8.0, minimum_side / 7.0),
        sigmaY=max(8.0, minimum_side / 7.0),
    )


def encode_jpeg(image_bgr: np.ndarray) -> bytes:
    success, encoded = cv2.imencode(".jpg", image_bgr, [cv2.IMWRITE_JPEG_QUALITY, 92])
    if not success:
        raise ImageRejectedError("IMAGE_ENCODE_FAILED", "图片安全处理失败，请重新上传。")
    return encoded.tobytes()


def sanitized_name(original_name: str) -> str:
    safe_name = Path(original_name).name
    stem = Path(safe_name).stem.strip() or "现场材料"
    return f"{stem}-已脱敏.jpg"


def safe_path(root: Path, relative_name: str, required_prefix: str) -> Path:
    normalized = Path(relative_name.replace("\\", "/"))
    if normalized.is_absolute() or ".." in normalized.parts or not normalized.parts:
        raise ImageRejectedError("UNSAFE_STORAGE_PATH", "图片存储路径不安全，请重新上传。")
    if normalized.parts[0] != required_prefix:
        raise ImageRejectedError("UNEXPECTED_STORAGE_AREA", "图片不在安全处理隔离区，请重新上传。")
    resolved = (root / normalized).resolve()
    if root not in resolved.parents:
        raise ImageRejectedError("UNSAFE_STORAGE_PATH", "图片存储路径不安全，请重新上传。")
    return resolved


def source_path_for_attachment(root: Path, relative_name: str) -> Path:
    normalized = relative_name.replace("\\", "/")
    if normalized.lower().startswith("_quarantine/"):
        return safe_path(root, normalized, required_prefix="_quarantine")
    return safe_legacy_attachment_path(root, normalized)


def safe_legacy_attachment_path(root: Path, relative_name: str) -> Path:
    normalized = Path(relative_name.replace("\\", "/"))
    if normalized.is_absolute() or ".." in normalized.parts or len(normalized.parts) != 1:
        raise ImageRejectedError("UNSAFE_STORAGE_PATH", "图片存储路径不安全，请重新上传。")
    if normalized.name.lower() in {"_quarantine", "_sanitized", "_zip-cache"}:
        raise ImageRejectedError("UNEXPECTED_STORAGE_AREA", "图片不在安全处理隔离区，请重新上传。")
    resolved = (root / normalized).resolve()
    if root not in resolved.parents:
        raise ImageRejectedError("UNSAFE_STORAGE_PATH", "图片存储路径不安全，请重新上传。")
    return resolved


def write_atomic(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    file_descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(file_descriptor, "wb") as output_file:
            output_file.write(content)
            output_file.flush()
            os.fsync(output_file.fileno())
        os.replace(temporary_name, path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)

from __future__ import annotations

import hashlib
from pathlib import Path
import tempfile
import unittest

import cv2
import numpy as np
from PIL import Image

from redaction.detector import Detection, inference_windows
from redaction.processor import (
    ImageRedactionProcessor,
    ImageRejectedError,
    expand_detections,
    merge_overlapping,
    safe_legacy_attachment_path,
    safe_path,
)


class FakeDetector:
    def __init__(self, detections: list[Detection]) -> None:
        self._detections = detections

    def detect(self, image_bgr: np.ndarray) -> list[Detection]:
        return self._detections


class ImageRedactionProcessorTests(unittest.TestCase):
    def test_large_images_use_overlapping_inference_windows(self) -> None:
        windows = inference_windows(2592, 1936)

        self.assertEqual((0, 0, 2592, 1936), windows[0])
        self.assertEqual(5, len(windows))
        self.assertTrue(any(left > 0 and top > 0 for left, top, _, _ in windows))

    def test_blurs_only_detected_region_and_reencodes_without_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            quarantine = root / "_quarantine"
            quarantine.mkdir()
            source = quarantine / "source.jpg"
            image = np.full((200, 300, 3), 230, dtype=np.uint8)
            for x in range(60, 160, 10):
                image[60:140, x:x + 5] = (0, 0, 255)
            cv2.imwrite(str(source), image)
            processor = ImageRedactionProcessor(
                detector=FakeDetector([Detection(60, 60, 160, 140, 0.95)]),
                storage_root=root,
                max_file_bytes=10_000_000,
                max_pixels=1_000_000,
                max_side=2_000,
                padding_ratio=0.1,
                max_padding_pixels=16,
            )

            result = processor.process(
                {
                    "id": "019d0000-0000-7000-8000-000000000001",
                    "source_stored_file_name": "_quarantine/source.jpg",
                    "original_file_name": "现场材料.jpg",
                }
            )

            output = root / result.output_stored_file_name
            self.assertTrue(output.is_file())
            self.assertTrue(source.is_file(), "原始上传文件必须保留在 _quarantine 供审计和人工取证")
            self.assertEqual(hashlib.sha256(output.read_bytes()).hexdigest(), result.output_sha256)
            with Image.open(output) as sanitized:
                self.assertFalse(sanitized.getexif())
                sanitized_array = np.asarray(sanitized)
            source_array = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            self.assertLess(np.abs(sanitized_array[100, 100].astype(int) - source_array[100, 100].astype(int)).max(), 220)
            self.assertLess(np.abs(sanitized_array[20, 20].astype(int) - source_array[20, 20].astype(int)).max(), 20)

    def test_rejects_excessive_pixel_count(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            quarantine = root / "_quarantine"
            quarantine.mkdir()
            Image.new("RGB", (120, 120), "white").save(quarantine / "large.png")
            processor = ImageRedactionProcessor(
                detector=FakeDetector([]),
                storage_root=root,
                max_file_bytes=10_000_000,
                max_pixels=10_000,
                max_side=2_000,
                padding_ratio=0.1,
                max_padding_pixels=16,
            )

            with self.assertRaises(ImageRejectedError) as context:
                processor.process(
                    {
                        "id": "019d0000-0000-7000-8000-000000000002",
                        "source_stored_file_name": "_quarantine/large.png",
                        "original_file_name": "large.png",
                    }
                )
            self.assertEqual("IMAGE_PIXEL_LIMIT", context.exception.error_code)

    def test_rejects_path_outside_quarantine(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            with self.assertRaises(ImageRejectedError):
                safe_path(root, "../secret.jpg", "_quarantine")

    def test_historical_root_level_attachment_is_allowed_but_reserved_paths_are_not(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            self.assertEqual(root / "legacy.jpg", safe_legacy_attachment_path(root, "legacy.jpg"))
            with self.assertRaises(ImageRejectedError):
                safe_legacy_attachment_path(root, "../secret.jpg")
            with self.assertRaises(ImageRejectedError):
                safe_legacy_attachment_path(root, "_sanitized/output.jpg")
            with self.assertRaises(ImageRejectedError):
                safe_legacy_attachment_path(root, "nested/legacy.jpg")

    def test_padding_is_bounded_and_applied_after_merge(self) -> None:
        detections = [
            Detection(20, 20, 40, 40, 0.95),
            Detection(42, 20, 62, 40, 0.9),
        ]

        merged = merge_overlapping(detections)
        expanded = expand_detections(merged, 100, 100, 0.5, 3)

        self.assertEqual(2, len(merged), "仅因 padding 相邻的检测框不能被链式合并")
        self.assertEqual(2, len(expanded))
        self.assertEqual((17, 17, 43, 43), (
            expanded[0].left,
            expanded[0].top,
            expanded[0].right,
            expanded[0].bottom,
        ))
        self.assertEqual((39, 17, 65, 43), (
            expanded[1].left,
            expanded[1].top,
            expanded[1].right,
            expanded[1].bottom,
        ))


if __name__ == "__main__":
    unittest.main()

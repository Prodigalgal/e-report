from __future__ import annotations

import unittest

import cv2
import numpy as np

from redaction.detector import (
    Detection,
    is_qr_like_crop,
    precision_windows,
)


class DetectorPrecisionTests(unittest.TestCase):
    def test_precision_windows_cover_an_oversized_region_without_duplicates(self) -> None:
        windows = precision_windows(100, 200, 2_100, 2_300)

        self.assertEqual(len(windows), 16)
        self.assertEqual(len(windows), len(set(windows)))
        self.assertIn((100, 200, 868, 968), windows)
        self.assertIn((1_332, 1_532, 2_100, 2_300), windows)

    def test_saturated_colour_block_is_not_brand_evidence(self) -> None:
        image = np.full((240, 240, 3), (45, 45, 45), dtype=np.uint8)
        cv2.rectangle(image, (20, 20), (220, 220), (40, 190, 40), thickness=-1)
        detection = Detection(0, 0, 240, 240, 0.8)

        # 颜色本身不参与验收；纯色块也不会被当作二维码。
        self.assertFalse(is_qr_like_crop(image, detection))

    def test_qr_like_crop_is_rejected_as_negative_only_guard(self) -> None:
        encoded = cv2.QRCodeEncoder_create().encode("https://example.com/e-report")
        encoded = cv2.resize(encoded, (240, 240), interpolation=cv2.INTER_NEAREST)
        image = np.full((300, 300, 3), 128, dtype=np.uint8)
        image[30:270, 30:270] = cv2.cvtColor(encoded, cv2.COLOR_GRAY2BGR)

        self.assertTrue(is_qr_like_crop(image, Detection(20, 20, 280, 280, 0.9)))


if __name__ == "__main__":
    unittest.main()

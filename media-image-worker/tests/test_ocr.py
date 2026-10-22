from __future__ import annotations

import unittest

from redaction.ocr import BrandTextDetection, expand_brand_text_detection, is_target_text


class CmccBrandTextTests(unittest.TestCase):
    def test_accepts_only_the_declared_china_mobile_names(self) -> None:
        for text in ("中国移动", "中国移动通信集团有限公司", "China Mobile", "China-Mobile"):
            with self.subTest(text=text):
                self.assertTrue(is_target_text(text))

    def test_accepts_ocr_drop_first_character_only_in_company_context(self) -> None:
        self.assertTrue(is_target_text("移动通信集团"))
        self.assertTrue(is_target_text("移动通信有限公司"))

    def test_rejects_generic_text_other_brands_and_colours(self) -> None:
        for text in (
            "G3",
            "3G",
            "Centre Militaire",
            "中国联通",
            "中国电信",
            "红色标牌",
            "二维码",
        ):
            with self.subTest(text=text):
                self.assertFalse(is_target_text(text))

    def test_expansion_is_bounded_by_image_edges(self) -> None:
        detection = BrandTextDetection(5, 5, 45, 25, 0.9, "中国移动")
        expanded = expand_brand_text_detection(detection, 50, 30)

        self.assertGreaterEqual(expanded.left, 0)
        self.assertGreaterEqual(expanded.top, 0)
        self.assertLessEqual(expanded.right, 50)
        self.assertLessEqual(expanded.bottom, 30)
        self.assertGreaterEqual(expanded.right - expanded.left, detection.right - detection.left)
        self.assertGreaterEqual(expanded.bottom - expanded.top, detection.bottom - detection.top)


if __name__ == "__main__":
    unittest.main()

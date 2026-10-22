from __future__ import annotations

import json
from pathlib import Path

import cv2


ROOT = Path(__file__).resolve().parent


def main() -> None:
    payload = json.loads((ROOT / "real_annotations.json").read_text(encoding="utf-8"))
    output_directory = ROOT / "generated" / "annotations"
    output_directory.mkdir(parents=True, exist_ok=True)
    for item in payload["images"]:
        image = cv2.imread(str(ROOT / "sources" / item["fileName"]), cv2.IMREAD_COLOR)
        if image is None:
            raise RuntimeError(f"Cannot read annotated source: {item['fileName']}")
        for left, top, right, bottom in item["boxes"]:
            cv2.rectangle(image, (left, top), (right, bottom), (0, 0, 255), max(3, min(image.shape[:2]) // 300))
        target = output_directory / item["fileName"]
        cv2.imwrite(str(target), image, [cv2.IMWRITE_JPEG_QUALITY, 88])
        print(target)


if __name__ == "__main__":
    main()

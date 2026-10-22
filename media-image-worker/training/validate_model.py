from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import statistics
import sys
import time

import cv2
import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parent
WORKER_ROOT = ROOT.parent
sys.path.insert(0, str(WORKER_ROOT))

from redaction.detector import CmccBrandDetector, Detection  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-version", default="cmcc-brand-v1")
    parser.add_argument("--model-path", type=Path)
    parser.add_argument("--source-directory", type=Path, default=ROOT / "sources")
    parser.add_argument("--annotations", type=Path, default=ROOT / "real_annotations.json")
    parser.add_argument("--include-annotations", action="store_true")
    parser.add_argument("--grayscale-inference", action="store_true")
    parser.add_argument("--dense-scan", action="store_true")
    parser.add_argument("--ocr-enabled", action="store_true")
    parser.add_argument("--ocr-max-side", type=int, default=2000)
    parser.add_argument("--ocr-min-confidence", type=float, default=0.65)
    parser.add_argument("--standalone-model-confidence", type=float, default=0.90)
    parser.add_argument("--threshold", type=float, default=0.65)
    parser.add_argument("--maximum-inference-ms", type=float, default=500.0)
    arguments = parser.parse_args()

    model_path = (arguments.model_path or (WORKER_ROOT / "models" / f"{arguments.model_version}.onnx")).resolve()
    model_sha256 = hashlib.sha256(model_path.read_bytes()).hexdigest()
    detector = CmccBrandDetector(
        model_path,
        model_sha256,
        arguments.threshold,
        0.35,
        grayscale_inference=arguments.grayscale_inference,
        dense_scan=arguments.dense_scan,
        ocr_enabled=arguments.ocr_enabled,
        ocr_max_side=arguments.ocr_max_side,
        ocr_min_confidence=arguments.ocr_min_confidence,
        standalone_model_confidence=arguments.standalone_model_confidence,
    )

    output_directory = ROOT / "generated" / "validation" / arguments.model_version
    output_directory.mkdir(parents=True, exist_ok=True)
    positive_current_path, positive_current_box = create_current_logo_scene(output_directory)
    samples = [
        evaluate_sample(
            detector,
            "current-logo-composite",
            positive_current_path,
            [positive_current_box],
            output_directory,
        ),
        evaluate_sample(
            detector,
            "legacy-logo-real-photo",
            ROOT / "sources" / "cmcc-lte-sim.jpg",
            [(1975, 1190, 2440, 1455)],
            output_directory,
        ),
        evaluate_sample(
            detector,
            "legacy-storefront-held-out",
            ROOT / "sources" / "cmcc-communications-panoramio.jpg",
            [(900, 540, 1135, 760)],
            output_directory,
        ),
        evaluate_sample(
            detector,
            "negative-network-centre",
            ROOT / "sources" / "cmcc-network-centre.jpg",
            [],
            output_directory,
        ),
    ]
    if arguments.include_annotations:
        annotations = json.loads(arguments.annotations.read_text(encoding="utf-8"))
        for item in annotations["images"]:
            samples.append(
                evaluate_sample(
                    detector,
                    f"annotated-{Path(item['fileName']).stem}",
                    arguments.source_directory / item["fileName"],
                    [tuple(box) for box in item["boxes"]],
                    output_directory,
                )
            )

    benchmark_image = cv2.imread(str(positive_current_path), cv2.IMREAD_COLOR)
    timings: list[float] = []
    for _ in range(15):
        started = time.perf_counter()
        detector.detect(benchmark_image)
        timings.append((time.perf_counter() - started) * 1000.0)
    sorted_timings = sorted(timings)
    p95_index = max(0, min(len(sorted_timings) - 1, int(len(sorted_timings) * 0.95) - 1))
    benchmark = {
        "medianMs": round(statistics.median(timings), 2),
        "p95Ms": round(sorted_timings[p95_index], 2),
        "maximumAllowedMs": arguments.maximum_inference_ms,
    }

    failures: list[str] = []
    for sample in samples:
        if sample["expectedPositive"] and sample["matchedCount"] < sample["expectedCount"]:
            failures.append(f"{sample['name']}: one or more annotated logo/text targets were not located")
        if not sample["expectedPositive"] and sample["detections"]:
            failures.append(f"{sample['name']}: false positive detected")
        if sample["maxAreaInflation"] > 6.0:
            failures.append(f"{sample['name']}: detection box is too broad (area inflation > 6x)")
    if benchmark["p95Ms"] > arguments.maximum_inference_ms:
        failures.append("CPU inference p95 exceeded the configured budget")

    report = {
        "schemaVersion": 1,
        "modelSha256": model_sha256,
        "confidenceThreshold": arguments.threshold,
        "detectorConfig": {
            "grayscaleInference": arguments.grayscale_inference,
            "denseScan": arguments.dense_scan,
            "ocrEnabled": arguments.ocr_enabled,
            "ocrMaxSide": arguments.ocr_max_side,
            "ocrMinConfidence": arguments.ocr_min_confidence,
            "standaloneModelConfidence": arguments.standalone_model_confidence,
        },
        "samples": samples,
        "cpuBenchmark": benchmark,
        "passed": not failures,
        "failures": failures,
    }
    report_path = output_directory / "validation-report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if not failures:
        update_model_manifest(report, arguments.model_version)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if failures:
        raise SystemExit(1)


def update_model_manifest(report: dict[str, object], model_version: str) -> None:
    manifest_path = WORKER_ROOT / "models" / f"{model_version}.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["acceptance"] = {
        "confidenceThreshold": report["confidenceThreshold"],
        "detectorConfig": report["detectorConfig"],
        "cpuBenchmark": report["cpuBenchmark"],
        "samples": [
            {
                "name": sample["name"],
                "expectedPositive": sample["expectedPositive"],
                "bestIou": sample["bestIou"],
                "matchedCount": sample["matchedCount"],
                "expectedCount": sample["expectedCount"],
                "detectionCount": len(sample["detections"]),
            }
            for sample in report["samples"]
        ],
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def create_current_logo_scene(output_directory: Path) -> tuple[Path, tuple[int, int, int, int]]:
    background = Image.open(ROOT / "sources" / "cmcc-network-centre.jpg").convert("RGB")
    background.thumbnail((1600, 1200), Image.Resampling.LANCZOS)
    logo = Image.open(ROOT / "generated" / "templates" / "cmcc-current-combined.png").convert("RGBA")
    logo.thumbnail((420, 180), Image.Resampling.LANCZOS)
    left = 70
    top = max(40, background.height - logo.height - 70)
    background.paste(logo, (left, top), logo)
    output_path = output_directory / "current-logo-composite.jpg"
    background.save(output_path, quality=92, optimize=True)
    return output_path, (left, top, left + logo.width, top + logo.height)


def evaluate_sample(
    detector: CmccBrandDetector,
    name: str,
    image_path: Path,
    expected_boxes: list[tuple[int, int, int, int]],
    output_directory: Path,
) -> dict[str, object]:
    image = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if image is None:
        raise RuntimeError(f"Cannot decode validation image: {image_path}")
    detections = detector.detect(image)
    best_ious = [
        max((intersection_over_union(detection, expected_box) for detection in detections), default=0.0)
        for expected_box in expected_boxes
    ]
    matched_count = sum(1 for value in best_ious if value >= 0.25)
    area_inflations = []
    for expected_box, best_iou in zip(expected_boxes, best_ious):
        if best_iou < 0.25:
            continue
        best_detection = max(detections, key=lambda detection: intersection_over_union(detection, expected_box))
        expected_area = max(1, (expected_box[2] - expected_box[0]) * (expected_box[3] - expected_box[1]))
        detected_area = max(1, (best_detection.right - best_detection.left) * (best_detection.bottom - best_detection.top))
        area_inflations.append(detected_area / expected_area)
    annotated = image.copy()
    for detection in detections:
        cv2.rectangle(
            annotated,
            (detection.left, detection.top),
            (detection.right, detection.bottom),
            (0, 0, 255),
            max(2, min(image.shape[:2]) // 300),
        )
    for expected_box in expected_boxes:
        cv2.rectangle(annotated, expected_box[:2], expected_box[2:], (0, 255, 0), 2)
    cv2.imwrite(str(output_directory / f"{name}-detections.jpg"), annotated)
    return {
        "name": name,
        "expectedPositive": bool(expected_boxes),
        "expectedCount": len(expected_boxes),
        "matchedCount": matched_count,
        "bestIou": round(max(best_ious, default=0.0), 4),
        "maxAreaInflation": round(max(area_inflations, default=0.0), 4),
        "detections": [
            {
                "left": item.left,
                "top": item.top,
                "right": item.right,
                "bottom": item.bottom,
                "confidence": round(item.confidence, 4),
            }
            for item in detections
        ],
    }


def intersection_over_union(detection: Detection, expected: tuple[int, int, int, int]) -> float:
    left = max(detection.left, expected[0])
    top = max(detection.top, expected[1])
    right = min(detection.right, expected[2])
    bottom = min(detection.bottom, expected[3])
    intersection = max(0, right - left) * max(0, bottom - top)
    detected_area = max(0, detection.right - detection.left) * max(0, detection.bottom - detection.top)
    expected_area = max(0, expected[2] - expected[0]) * max(0, expected[3] - expected[1])
    return intersection / max(1, detected_area + expected_area - intersection)


if __name__ == "__main__":
    main()

from __future__ import annotations

import json
from pathlib import Path
import shutil
import sys


ROOT = Path(__file__).resolve().parent
WORKER_ROOT = ROOT.parent
sys.path.insert(0, str(WORKER_ROOT))

from redaction.detector import CmccBrandDetector  # noqa: E402
from redaction.processor import ImageRedactionProcessor  # noqa: E402


def main() -> None:
    manifest = json.loads((WORKER_ROOT / "models" / "cmcc-brand-v1.json").read_text(encoding="utf-8"))
    detector = CmccBrandDetector(
        WORKER_ROOT / "models" / "cmcc-brand-v1.onnx",
        manifest["sha256"],
        manifest["acceptance"]["confidenceThreshold"],
        0.35,
        grayscale_inference=manifest["input"].get("color") == "grayscale-replicated",
    )
    output_root = ROOT / "generated" / "redaction-preview"
    quarantine = output_root / "_quarantine"
    quarantine.mkdir(parents=True, exist_ok=True)
    processor = ImageRedactionProcessor(
        detector=detector,
        storage_root=output_root,
        max_file_bytes=100 * 1024 * 1024,
        max_pixels=40_000_000,
        max_side=12_000,
        padding_ratio=0.05,
        max_padding_pixels=16,
    )
    samples = [
        "cmcc-lte-sim.jpg",
        "cmcc-communications-panoramio.jpg",
        "cmcc-network-centre.jpg",
    ]
    for index, file_name in enumerate(samples, start=1):
        source = ROOT / "sources" / file_name
        quarantine_name = f"sample-{index}{source.suffix.lower()}"
        shutil.copyfile(source, quarantine / quarantine_name)
        result = processor.process({
            "id": f"019d0000-0000-7000-8000-{index:012d}",
            "source_stored_file_name": f"_quarantine/{quarantine_name}",
            "original_file_name": file_name,
        })
        final_name = output_root / f"preview-{file_name}"
        shutil.copyfile(output_root / result.output_stored_file_name, final_name)
        print(f"{file_name}: regions={result.detected_regions} duration_ms={result.duration_ms} output={final_name}")


if __name__ == "__main__":
    main()

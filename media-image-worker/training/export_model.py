from __future__ import annotations

import hashlib
import json
import argparse
from pathlib import Path

import onnx
import torch

from model import TinyBrandDetector


ROOT = Path(__file__).resolve().parent
MODEL_DIRECTORY = ROOT.parent / "models"


def main() -> None:
    parser = argparse.ArgumentParser(description="Export a trained detector checkpoint to an immutable ONNX model.")
    parser.add_argument("--model-version", default="cmcc-brand-v2")
    parser.add_argument("--checkpoint", type=Path)
    parser.add_argument("--grayscale-inference", action="store_true")
    arguments = parser.parse_args()

    checkpoint_path = (arguments.checkpoint or (ROOT / "checkpoints" / f"{arguments.model_version}.pt")).resolve()
    checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=True)
    model = TinyBrandDetector(pretrained=False)
    model.load_state_dict(checkpoint["model"])
    model.eval()
    MODEL_DIRECTORY.mkdir(parents=True, exist_ok=True)
    output_path = MODEL_DIRECTORY / f"{arguments.model_version}.onnx"
    torch.onnx.export(
        model,
        torch.zeros(1, 3, 384, 384),
        output_path,
        input_names=["image"],
        output_names=["detections"],
        opset_version=18,
        do_constant_folding=True,
        dynamo=False,
    )
    onnx_model = onnx.load(output_path)
    onnx.checker.check_model(onnx_model)
    digest = hashlib.sha256(output_path.read_bytes()).hexdigest()
    manifest = {
        "schemaVersion": 1,
        "modelVersion": arguments.model_version,
        "architecture": "MobileNetV3-Small ImageNet transfer learning with an anchor-free detection head",
        "input": {
            "name": "image",
            "shape": [1, 3, 384, 384],
            "color": "grayscale-replicated" if arguments.grayscale_inference else "RGB",
            "range": [0.0, 1.0],
        },
        "output": {"name": "detections", "shape": [1, 5, 24, 24]},
        "sha256": digest,
        "bytes": output_path.stat().st_size,
        "validation": checkpoint.get("metrics", {}),
        "trainingSources": "training/source-lock.json",
    }
    (MODEL_DIRECTORY / f"{arguments.model_version}.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

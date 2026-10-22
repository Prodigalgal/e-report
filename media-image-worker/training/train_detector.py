from __future__ import annotations

import argparse
import json
from pathlib import Path
import random
import time

import numpy as np
import torch
from torch import nn
from torch.utils.data import ConcatDataset, DataLoader

from model import TinyBrandDetector
from synthetic_dataset import RealSceneBrandDataset, SyntheticBrandDataset


ROOT = Path(__file__).resolve().parent


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-version", default="cmcc-brand-v2")
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--train-samples", type=int, default=2400)
    parser.add_argument("--validation-samples", type=int, default=400)
    parser.add_argument("--real-train-samples", type=int, default=1600)
    parser.add_argument("--real-validation-samples", type=int, default=400)
    parser.add_argument("--hard-negative-real-train-samples", type=int, default=1200)
    parser.add_argument("--hard-negative-real-validation-samples", type=int, default=400)
    parser.add_argument("--positive-real-train-samples", type=int, default=1600)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--learning-rate", type=float, default=0.002)
    parser.add_argument("--objectness-positive-weight", type=float, default=12.0)
    parser.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    parser.add_argument("--template-directory", type=Path, default=ROOT / "generated" / "templates")
    parser.add_argument("--source-directory", type=Path, default=ROOT / "sources")
    parser.add_argument("--annotations", type=Path, default=ROOT / "real_annotations.json")
    parser.add_argument("--positive-probability", type=float, default=0.72)
    parser.add_argument("--hard-negative-probability", type=float, default=0.85)
    parser.add_argument("--grayscale-probability", type=float, default=0.70)
    parser.add_argument("--max-real-scene-side", type=int, default=2048)
    parser.add_argument("--focused-box-side-min", type=int, default=180)
    parser.add_argument("--focused-box-side-max", type=int, default=340)
    parser.add_argument("--cache-in-memory", action="store_true")
    arguments = parser.parse_args()

    seed_everything(5400)
    templates = arguments.template_directory.resolve()
    source_directory = arguments.source_directory.resolve()
    background_paths = [source_directory / "cmcc-network-centre.jpg"]
    annotations_path = arguments.annotations.resolve()
    train_dataset = ConcatDataset([
        SyntheticBrandDataset(
            templates,
            background_paths,
            arguments.train_samples,
            5400,
            positive_probability=arguments.positive_probability,
            hard_negative_probability=arguments.hard_negative_probability,
            grayscale_probability=arguments.grayscale_probability,
            cache_in_memory=arguments.cache_in_memory,
        ),
        RealSceneBrandDataset(
            source_directory,
            annotations_path,
            "train",
            arguments.real_train_samples,
            6400,
            grayscale_probability=arguments.grayscale_probability,
            max_scene_side=arguments.max_real_scene_side,
            cache_in_memory=arguments.cache_in_memory,
        ),
        RealSceneBrandDataset(
            source_directory,
            annotations_path,
            "train",
            arguments.positive_real_train_samples,
            6900,
            grayscale_probability=arguments.grayscale_probability,
            max_scene_side=arguments.max_real_scene_side,
            only_non_empty_scenes=True,
            desired_box_side_min=arguments.focused_box_side_min,
            desired_box_side_max=arguments.focused_box_side_max,
            cache_in_memory=arguments.cache_in_memory,
        ),
        RealSceneBrandDataset(
            source_directory,
            annotations_path,
            "train",
            arguments.hard_negative_real_train_samples,
            7400,
            grayscale_probability=arguments.grayscale_probability,
            max_scene_side=arguments.max_real_scene_side,
            only_empty_scenes=True,
            cache_in_memory=arguments.cache_in_memory,
        ),
    ])
    validation_dataset = ConcatDataset([
        SyntheticBrandDataset(
            templates,
            background_paths,
            arguments.validation_samples,
            9540,
            positive_probability=arguments.positive_probability,
            hard_negative_probability=arguments.hard_negative_probability,
            grayscale_probability=arguments.grayscale_probability,
            cache_in_memory=arguments.cache_in_memory,
        ),
        RealSceneBrandDataset(
            source_directory,
            annotations_path,
            "validation",
            arguments.real_validation_samples,
            10540,
            grayscale_probability=arguments.grayscale_probability,
            max_scene_side=arguments.max_real_scene_side,
            cache_in_memory=arguments.cache_in_memory,
        ),
        RealSceneBrandDataset(
            source_directory,
            annotations_path,
            "validation",
            arguments.hard_negative_real_validation_samples,
            11400,
            grayscale_probability=arguments.grayscale_probability,
            max_scene_side=arguments.max_real_scene_side,
            only_empty_scenes=True,
            cache_in_memory=arguments.cache_in_memory,
        ),
    ])
    train_loader = DataLoader(
        train_dataset,
        batch_size=arguments.batch_size,
        shuffle=True,
        num_workers=0,
        pin_memory=arguments.device.startswith("cuda"),
    )
    validation_loader = DataLoader(validation_dataset, batch_size=arguments.batch_size, num_workers=0)

    device = torch.device(arguments.device)
    model = TinyBrandDetector().to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=arguments.learning_rate, weight_decay=0.0005)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=arguments.epochs)
    objectness_loss = nn.BCEWithLogitsLoss(
        pos_weight=torch.tensor([arguments.objectness_positive_weight], device=device)
    )
    best_score = -1.0
    best_path = ROOT / "checkpoints" / f"{arguments.model_version}.pt"
    best_path.parent.mkdir(parents=True, exist_ok=True)
    history: list[dict[str, float | int]] = []

    for epoch in range(1, arguments.epochs + 1):
        started = time.perf_counter()
        model.train()
        total_loss = 0.0
        for images, targets in train_loader:
            images = images.to(device, non_blocking=True)
            targets = targets.to(device, non_blocking=True)
            predictions = model(images)
            loss = detector_loss(predictions, targets, objectness_loss)
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            optimizer.step()
            total_loss += float(loss.detach()) * images.shape[0]
        scheduler.step()
        metrics = evaluate(model, validation_loader, device)
        average_loss = total_loss / len(train_dataset)
        score = metrics["f1"]
        history.append({"epoch": epoch, "trainLoss": average_loss, **metrics})
        if score > best_score:
            best_score = score
            torch.save({"model": model.state_dict(), "metrics": metrics, "epoch": epoch}, best_path)
        print(
            f"epoch={epoch:02d} loss={average_loss:.4f} precision={metrics['precision']:.4f} "
            f"recall={metrics['recall']:.4f} f1={metrics['f1']:.4f} seconds={time.perf_counter() - started:.1f}"
        )

    (ROOT / "checkpoints" / f"{arguments.model_version}-training-history.json").write_text(
        json.dumps(history, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"best checkpoint: {best_path} f1={best_score:.4f}")


def detector_loss(
    predictions: torch.Tensor,
    targets: torch.Tensor,
    objectness_loss: nn.BCEWithLogitsLoss,
) -> torch.Tensor:
    object_targets = targets[:, 0:1]
    object_loss_value = objectness_loss(predictions[:, 0:1], object_targets)
    positive_mask = object_targets.expand(-1, 4, -1, -1) > 0.5
    if positive_mask.any():
        box_predictions = torch.sigmoid(predictions[:, 1:5])
        box_loss_value = nn.functional.smooth_l1_loss(
            box_predictions[positive_mask],
            targets[:, 1:5][positive_mask],
            beta=0.05,
        )
    else:
        box_loss_value = predictions.sum() * 0.0
    return object_loss_value + box_loss_value * 5.0


@torch.no_grad()
def evaluate(model: nn.Module, data_loader: DataLoader, device: torch.device) -> dict[str, float]:
    model.eval()
    true_positive = 0
    false_positive = 0
    false_negative = 0
    for images, targets in data_loader:
        predictions = torch.sigmoid(model(images.to(device))).cpu()
        predicted = predictions[:, 0] >= 0.55
        expected = targets[:, 0] >= 0.5
        true_positive += int((predicted & expected).sum())
        false_positive += int((predicted & ~expected).sum())
        false_negative += int((~predicted & expected).sum())
    precision = true_positive / max(1, true_positive + false_positive)
    recall = true_positive / max(1, true_positive + false_negative)
    f1 = 2.0 * precision * recall / max(1e-9, precision + recall)
    return {"precision": precision, "recall": recall, "f1": f1}


def seed_everything(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


if __name__ == "__main__":
    main()

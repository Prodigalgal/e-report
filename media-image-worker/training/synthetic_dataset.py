from __future__ import annotations

import math
import json
from pathlib import Path
import random

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps
import torch
from torch.utils.data import Dataset


INPUT_SIZE = 384
GRID_SIZE = 24
MAX_REAL_SCENE_SIDE = 2048


class SyntheticBrandDataset(Dataset[tuple[torch.Tensor, torch.Tensor]]):
    def __init__(
        self,
        template_directory: Path,
        background_paths: list[Path],
        sample_count: int,
        seed: int,
        positive_probability: float = 0.82,
        hard_negative_probability: float = 0.75,
        grayscale_probability: float = 0.0,
        cache_in_memory: bool = True,
    ) -> None:
        self._templates = [Image.open(path).convert("RGBA") for path in sorted(template_directory.glob("*.png"))]
        if not self._templates:
            raise RuntimeError(f"No templates found in {template_directory}")
        self._backgrounds = [Image.open(path).convert("RGB") for path in background_paths]
        self._sample_count = sample_count
        self._seed = seed
        self._positive_probability = positive_probability
        self._hard_negative_probability = hard_negative_probability
        self._grayscale_probability = grayscale_probability
        self._cache = [self._render(index) for index in range(sample_count)] if cache_in_memory else None

    def __len__(self) -> int:
        return self._sample_count

    def __getitem__(self, index: int) -> tuple[torch.Tensor, torch.Tensor]:
        image_array, target = self._cache[index] if self._cache is not None else self._render(index)
        image_tensor = torch.from_numpy(np.transpose(image_array.astype(np.float32) / 255.0, (2, 0, 1))).contiguous()
        return image_tensor, torch.from_numpy(target.copy())

    def _render(self, index: int) -> tuple[np.ndarray, np.ndarray]:
        randomizer = random.Random(self._seed + index * 104729)
        canvas = make_background(randomizer, self._backgrounds)
        boxes: list[tuple[int, int, int, int]] = []
        if randomizer.random() < self._positive_probability:
            target_count = randomizer.randint(1, 3)
            for _ in range(target_count):
                template = randomizer.choice(self._templates).copy()
                transformed = transform_template(template, randomizer)
                box = paste_random(canvas, transformed, randomizer, boxes)
                if box is not None:
                    boxes.append(box)
        elif randomizer.random() < self._hard_negative_probability:
            canvas = render_hard_negative(canvas, randomizer)

        canvas = degrade_scene(canvas, randomizer)
        if randomizer.random() < self._grayscale_probability:
            canvas = ImageOps.grayscale(canvas).convert("RGB")
        target = encode_targets(boxes)
        return np.asarray(canvas, dtype=np.uint8).copy(), target


class RealSceneBrandDataset(Dataset[tuple[torch.Tensor, torch.Tensor]]):
    def __init__(
        self,
        source_directory: Path,
        annotations_path: Path,
        split: str,
        sample_count: int,
        seed: int,
        grayscale_probability: float = 0.0,
        max_scene_side: int = MAX_REAL_SCENE_SIDE,
        only_empty_scenes: bool = False,
        only_non_empty_scenes: bool = False,
        desired_box_side_min: int = 64,
        desired_box_side_max: int = 300,
        cache_in_memory: bool = True,
    ) -> None:
        payload = json.loads(annotations_path.read_text(encoding="utf-8"))
        self._scenes = [
            load_real_scene(source_directory / item["fileName"], item["boxes"], max_scene_side)
            for item in payload["images"]
            if item["split"] == split
            and (not only_empty_scenes or not item["boxes"])
            and (not only_non_empty_scenes or bool(item["boxes"]))
        ]
        if not self._scenes:
            raise RuntimeError(f"No real-scene annotations found for split={split}")
        self._sample_count = sample_count
        self._seed = seed
        self._grayscale_probability = grayscale_probability
        if desired_box_side_min < 16 or desired_box_side_min > desired_box_side_max:
            raise ValueError("desired_box_side_min must be positive and no greater than desired_box_side_max")
        self._desired_box_side_min = desired_box_side_min
        self._desired_box_side_max = desired_box_side_max
        self._cache = [self._render(index) for index in range(sample_count)] if cache_in_memory else None

    def __len__(self) -> int:
        return self._sample_count

    def __getitem__(self, index: int) -> tuple[torch.Tensor, torch.Tensor]:
        image_array, target = self._cache[index] if self._cache is not None else self._render(index)
        image_tensor = torch.from_numpy(np.transpose(image_array.astype(np.float32) / 255.0, (2, 0, 1))).contiguous()
        return image_tensor, torch.from_numpy(target.copy())

    def _render(self, index: int) -> tuple[np.ndarray, np.ndarray]:
        randomizer = random.Random(self._seed + index * 130363)
        scene = randomizer.choice(self._scenes)
        image = scene["image"]
        boxes = scene["boxes"]
        if boxes:
            focus_box = randomizer.choice(boxes)
            crop = choose_focus_crop(
                image.size,
                focus_box,
                randomizer,
                self._desired_box_side_min,
                self._desired_box_side_max,
            )
        else:
            crop = choose_negative_crop(image.size, randomizer)
        cropped = image.crop(crop).resize((INPUT_SIZE, INPUT_SIZE), Image.Resampling.LANCZOS)
        transformed_boxes = transform_boxes_to_crop(boxes, crop) if boxes else []
        if randomizer.random() < 0.5:
            cropped = ImageOps.mirror(cropped)
            transformed_boxes = [
                (INPUT_SIZE - right, top, INPUT_SIZE - left, bottom)
                for left, top, right, bottom in transformed_boxes
            ]
        if randomizer.random() < 0.65:
            cropped = ImageEnhance.Brightness(cropped).enhance(randomizer.uniform(0.7, 1.25))
        if randomizer.random() < 0.5:
            cropped = ImageEnhance.Contrast(cropped).enhance(randomizer.uniform(0.75, 1.3))
        cropped = degrade_scene(cropped, randomizer)
        if randomizer.random() < self._grayscale_probability:
            cropped = ImageOps.grayscale(cropped).convert("RGB")
        return np.asarray(cropped, dtype=np.uint8).copy(), encode_targets(transformed_boxes)


def load_real_scene(
    image_path: Path,
    boxes: list[list[int]],
    max_scene_side: int,
) -> dict[str, object]:
    with Image.open(image_path) as source:
        image = source.convert("RGB")
    scale = min(1.0, max_scene_side / max(image.size))
    if scale < 1.0:
        image = image.resize(
            (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
            Image.Resampling.LANCZOS,
        )
    scaled_boxes = [
        tuple(round(coordinate * scale) for coordinate in box)
        for box in boxes
    ]
    return {"image": image, "boxes": scaled_boxes}


def choose_focus_crop(
    image_size: tuple[int, int],
    focus_box: tuple[int, int, int, int],
    randomizer: random.Random,
    desired_box_side_min: int = 64,
    desired_box_side_max: int = 300,
) -> tuple[int, int, int, int]:
    image_width, image_height = image_size
    box_width = focus_box[2] - focus_box[0]
    box_height = focus_box[3] - focus_box[1]
    # Keep small logo/text targets readable while retaining a few contextual
    # pixels around the sign; this is deliberately independent of hue.
    desired_box_side = randomizer.randint(desired_box_side_min, desired_box_side_max)
    crop_side = int(max(box_width, box_height) * INPUT_SIZE / desired_box_side)
    crop_side = max(max(box_width, box_height) + 8, min(crop_side, min(image_width, image_height)))
    center_x = (focus_box[0] + focus_box[2]) / 2.0
    center_y = (focus_box[1] + focus_box[3]) / 2.0
    jitter_x = randomizer.uniform(-0.18, 0.18) * max(0, crop_side - box_width)
    jitter_y = randomizer.uniform(-0.18, 0.18) * max(0, crop_side - box_height)
    left = int(round(center_x - crop_side / 2.0 + jitter_x))
    top = int(round(center_y - crop_side / 2.0 + jitter_y))
    left = max(0, min(left, image_width - crop_side))
    top = max(0, min(top, image_height - crop_side))
    return left, top, left + crop_side, top + crop_side


def choose_negative_crop(
    image_size: tuple[int, int],
    randomizer: random.Random,
) -> tuple[int, int, int, int]:
    """Choose a varied square crop for a real hard-negative scene."""
    image_width, image_height = image_size
    minimum_side = min(image_width, image_height)
    lower_bound = max(64, int(minimum_side * 0.35))
    side = randomizer.randint(lower_bound, minimum_side)
    left = randomizer.randint(0, max(0, image_width - side))
    top = randomizer.randint(0, max(0, image_height - side))
    return left, top, left + side, top + side


def transform_boxes_to_crop(
    boxes: list[tuple[int, int, int, int]],
    crop: tuple[int, int, int, int],
) -> list[tuple[int, int, int, int]]:
    scale = INPUT_SIZE / (crop[2] - crop[0])
    transformed: list[tuple[int, int, int, int]] = []
    for left, top, right, bottom in boxes:
        clipped_left = max(left, crop[0])
        clipped_top = max(top, crop[1])
        clipped_right = min(right, crop[2])
        clipped_bottom = min(bottom, crop[3])
        if clipped_right - clipped_left < 8 or clipped_bottom - clipped_top < 8:
            continue
        transformed.append((
            int(round((clipped_left - crop[0]) * scale)),
            int(round((clipped_top - crop[1]) * scale)),
            int(round((clipped_right - crop[0]) * scale)),
            int(round((clipped_bottom - crop[1]) * scale)),
        ))
    return transformed


def make_background(randomizer: random.Random, backgrounds: list[Image.Image]) -> Image.Image:
    if backgrounds and randomizer.random() < 0.3:
        background = randomizer.choice(backgrounds)
        scale = max(INPUT_SIZE / background.width, INPUT_SIZE / background.height)
        resized = background.resize(
            (max(INPUT_SIZE, int(background.width * scale)), max(INPUT_SIZE, int(background.height * scale))),
            Image.Resampling.LANCZOS,
        )
        left = randomizer.randint(0, resized.width - INPUT_SIZE)
        top = randomizer.randint(0, resized.height - INPUT_SIZE)
        return resized.crop((left, top, left + INPUT_SIZE, top + INPUT_SIZE))

    start = np.array([randomizer.randint(20, 235) for _ in range(3)], dtype=np.float32)
    end = np.array([randomizer.randint(20, 235) for _ in range(3)], dtype=np.float32)
    vertical = np.linspace(0.0, 1.0, INPUT_SIZE, dtype=np.float32)[:, None, None]
    image = start[None, None, :] * (1.0 - vertical) + end[None, None, :] * vertical
    image = np.repeat(image, INPUT_SIZE, axis=1)
    noise = np.random.default_rng(randomizer.randint(0, 2**32 - 1)).normal(0.0, 12.0, image.shape)
    image = np.clip(image + noise, 0, 255).astype(np.uint8)
    canvas = Image.fromarray(image, mode="RGB")
    draw = cv2.cvtColor(np.asarray(canvas), cv2.COLOR_RGB2BGR).copy()
    for _ in range(randomizer.randint(4, 18)):
        color = tuple(randomizer.randint(0, 255) for _ in range(3))
        x1, y1 = randomizer.randint(0, INPUT_SIZE - 1), randomizer.randint(0, INPUT_SIZE - 1)
        x2, y2 = randomizer.randint(x1, INPUT_SIZE), randomizer.randint(y1, INPUT_SIZE)
        cv2.rectangle(draw, (x1, y1), (x2, y2), color, randomizer.choice([1, 2, -1]))
    return Image.fromarray(cv2.cvtColor(draw, cv2.COLOR_BGR2RGB))


def transform_template(template: Image.Image, randomizer: random.Random) -> Image.Image:
    target_width = randomizer.randint(32, 240)
    scale = target_width / template.width
    target_height = max(14, int(template.height * scale))
    transformed = template.resize((target_width, target_height), Image.Resampling.LANCZOS)
    if randomizer.random() < 0.55:
        transformed = ImageEnhance.Brightness(transformed).enhance(randomizer.uniform(0.55, 1.35))
    if randomizer.random() < 0.35:
        transformed = ImageEnhance.Contrast(transformed).enhance(randomizer.uniform(0.65, 1.45))
    if randomizer.random() < 0.7:
        transformed = color_invariant_template(transformed, randomizer)
    if randomizer.random() < 0.65:
        transformed = transformed.rotate(
            randomizer.uniform(-22.0, 22.0),
            resample=Image.Resampling.BICUBIC,
            expand=True,
        )
    if randomizer.random() < 0.2:
        transformed = transformed.filter(ImageFilter.GaussianBlur(randomizer.uniform(0.4, 1.8)))
    return transformed


def color_invariant_template(template: Image.Image, randomizer: random.Random) -> Image.Image:
    """Randomise chroma while preserving logo/text geometry and transparency."""
    alpha = template.getchannel("A")
    grayscale = ImageOps.grayscale(template.convert("RGB"))
    if randomizer.random() < 0.55:
        rgb = grayscale.convert("RGB")
    else:
        dark = tuple(randomizer.randint(0, 90) for _ in range(3))
        light = tuple(randomizer.randint(160, 255) for _ in range(3))
        rgb = ImageOps.colorize(grayscale, black=dark, white=light).convert("RGB")
    return Image.merge("RGBA", (*rgb.split(), alpha))


def paste_random(
    canvas: Image.Image,
    overlay: Image.Image,
    randomizer: random.Random,
    existing_boxes: list[tuple[int, int, int, int]],
) -> tuple[int, int, int, int] | None:
    if overlay.width >= INPUT_SIZE or overlay.height >= INPUT_SIZE:
        return None
    for _ in range(15):
        left = randomizer.randint(0, INPUT_SIZE - overlay.width)
        top = randomizer.randint(0, INPUT_SIZE - overlay.height)
        box = (left, top, left + overlay.width, top + overlay.height)
        center_cell = ((left + overlay.width // 2) // 16, (top + overlay.height // 2) // 16)
        if any(((old[0] + old[2]) // 2 // 16, (old[1] + old[3]) // 2 // 16) == center_cell for old in existing_boxes):
            continue
        canvas.alpha_composite(overlay, (left, top)) if canvas.mode == "RGBA" else canvas.paste(overlay, (left, top), overlay)
        return box
    return None


def render_hard_negative(canvas: Image.Image, randomizer: random.Random) -> Image.Image:
    """Render non-brand patterns that must not be mistaken for the logo/text."""
    draw = ImageDraw.Draw(canvas)
    kind = randomizer.choice(("color_panel", "qr", "text_lines", "led_panel", "generic_mark"))
    if kind == "color_panel":
        for _ in range(randomizer.randint(2, 7)):
            left = randomizer.randint(0, INPUT_SIZE - 32)
            top = randomizer.randint(0, INPUT_SIZE - 32)
            right = randomizer.randint(left + 16, INPUT_SIZE)
            bottom = randomizer.randint(top + 16, INPUT_SIZE)
            color = tuple(randomizer.randint(0, 255) for _ in range(3))
            draw.rectangle((left, top, right, bottom), fill=color)
    elif kind == "qr":
        render_qr_like_pattern(draw, randomizer)
    elif kind == "text_lines":
        for _ in range(randomizer.randint(3, 8)):
            top = randomizer.randint(12, INPUT_SIZE - 24)
            left = randomizer.randint(8, 70)
            for _ in range(randomizer.randint(4, 14)):
                width = randomizer.randint(3, 14)
                height = randomizer.randint(8, 30)
                draw.rectangle((left, top, min(INPUT_SIZE, left + width), min(INPUT_SIZE, top + height)), fill=(randomizer.randint(0, 120),) * 3)
                left += width + randomizer.randint(3, 10)
                if left >= INPUT_SIZE - 8:
                    break
    elif kind == "led_panel":
        left = randomizer.randint(15, 90)
        top = randomizer.randint(15, 90)
        width = randomizer.randint(150, 280)
        height = randomizer.randint(50, 130)
        draw.rectangle((left, top, min(INPUT_SIZE - 1, left + width), min(INPUT_SIZE - 1, top + height)), fill=(20, 20, 20))
        for x in range(left + 8, min(INPUT_SIZE, left + width - 4), 8):
            color = randomizer.choice(((235, 235, 235), (180, 180, 180), (80, 80, 80)))
            draw.rectangle((x, top + 8, min(INPUT_SIZE - 1, x + 4), min(INPUT_SIZE - 1, top + height - 8)), fill=color)
    else:
        center = (randomizer.randint(70, INPUT_SIZE - 70), randomizer.randint(70, INPUT_SIZE - 70))
        radius = randomizer.randint(24, 80)
        draw.ellipse((center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius), outline=(randomizer.randint(0, 255),) * 3, width=randomizer.randint(2, 8))
        points = []
        for index in range(16):
            angle = index * math.pi / 4.0
            distance = radius * (0.2 + index / 20.0)
            points.append((int(center[0] + math.cos(angle) * distance), int(center[1] + math.sin(angle) * distance)))
        draw.line(points, fill=(randomizer.randint(0, 255),) * 3, width=randomizer.randint(2, 6))
    return canvas


def render_qr_like_pattern(draw: ImageDraw.ImageDraw, randomizer: random.Random) -> None:
    side = randomizer.randint(112, 260)
    left = randomizer.randint(8, INPUT_SIZE - side - 8)
    top = randomizer.randint(8, INPUT_SIZE - side - 8)
    cell = max(4, side // randomizer.randint(18, 29))
    side = cell * max(18, side // cell)
    draw.rectangle((left, top, left + side, top + side), fill=(250, 250, 250))
    for row in range(1, side // cell - 1):
        for column in range(1, side // cell - 1):
            if randomizer.random() < 0.38:
                x = left + column * cell
                y = top + row * cell
                draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(10, 10, 10))
    for finder_left, finder_top in ((left + cell, top + cell), (left + side - 8 * cell, top + cell), (left + cell, top + side - 8 * cell)):
        draw.rectangle((finder_left, finder_top, finder_left + 6 * cell, finder_top + 6 * cell), fill=(10, 10, 10))
        draw.rectangle((finder_left + cell, finder_top + cell, finder_left + 5 * cell, finder_top + 5 * cell), fill=(250, 250, 250))
        draw.rectangle((finder_left + 2 * cell, finder_top + 2 * cell, finder_left + 4 * cell, finder_top + 4 * cell), fill=(10, 10, 10))


def degrade_scene(image: Image.Image, randomizer: random.Random) -> Image.Image:
    result = image
    if randomizer.random() < 0.35:
        result = result.filter(ImageFilter.GaussianBlur(randomizer.uniform(0.2, 1.2)))
    if randomizer.random() < 0.4:
        quality = randomizer.randint(40, 90)
        encoded = cv2.imencode(
            ".jpg",
            cv2.cvtColor(np.asarray(result), cv2.COLOR_RGB2BGR),
            [cv2.IMWRITE_JPEG_QUALITY, quality],
        )[1]
        result = Image.fromarray(cv2.cvtColor(cv2.imdecode(encoded, cv2.IMREAD_COLOR), cv2.COLOR_BGR2RGB))
    return result


def encode_targets(boxes: list[tuple[int, int, int, int]]) -> np.ndarray:
    target = np.zeros((5, GRID_SIZE, GRID_SIZE), dtype=np.float32)
    for left, top, right, bottom in boxes:
        center_x = ((left + right) / 2.0) / INPUT_SIZE
        center_y = ((top + bottom) / 2.0) / INPUT_SIZE
        width = (right - left) / INPUT_SIZE
        height = (bottom - top) / INPUT_SIZE
        grid_x = min(GRID_SIZE - 1, max(0, int(center_x * GRID_SIZE)))
        grid_y = min(GRID_SIZE - 1, max(0, int(center_y * GRID_SIZE)))
        target[0, grid_y, grid_x] = 1.0
        target[1, grid_y, grid_x] = center_x * GRID_SIZE - grid_x
        target[2, grid_y, grid_x] = center_y * GRID_SIZE - grid_y
        target[3, grid_y, grid_x] = width
        target[4, grid_y, grid_x] = height
    return target

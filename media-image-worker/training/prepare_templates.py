from __future__ import annotations

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parent
SOURCES = ROOT / "sources"
TEMPLATES = ROOT / "generated" / "templates"


def main() -> None:
    TEMPLATES.mkdir(parents=True, exist_ok=True)
    logo = Image.open(SOURCES / "cmcc-logo-2019.png").convert("RGBA")
    alpha_bounds = logo.getchannel("A").getbbox()
    if alpha_bounds is None:
        raise RuntimeError("Current logo source has no visible pixels")
    logo = logo.crop(alpha_bounds)
    save_trimmed(logo, "cmcc-current-combined.png")

    width, height = logo.size
    save_trimmed(logo.crop((0, 0, int(width * 0.36), height)), "cmcc-current-symbol.png")
    save_trimmed(logo.crop((int(width * 0.37), 0, width, height)), "cmcc-current-wordmark.png")

    sim = Image.open(SOURCES / "cmcc-lte-sim.jpg").convert("RGB")
    # Public-domain source, 2592x1936. The bottom-right white legacy lockup is intentionally isolated.
    legacy = sim.crop((1990, 1220, 2410, 1425)).convert("RGBA")
    legacy = isolate_light_foreground(legacy)
    save_trimmed(legacy, "cmcc-legacy-sim-lockup.png", remove_bright_background=False)
    print(f"prepared {len(list(TEMPLATES.glob('*.png')))} templates in {TEMPLATES}")


def save_trimmed(image: Image.Image, file_name: str, remove_bright_background: bool = False) -> None:
    if remove_bright_background:
        pixels = image.load()
        for y in range(image.height):
            for x in range(image.width):
                red, green, blue, alpha = pixels[x, y]
                if red > 245 and green > 245 and blue > 245:
                    pixels[x, y] = (red, green, blue, 0)
    target = TEMPLATES / file_name
    image.save(target, optimize=True)
    print(f"template {target.name}: {image.width}x{image.height}")


def isolate_light_foreground(image: Image.Image) -> Image.Image:
    isolated = image.copy()
    pixels = isolated.load()
    for y in range(isolated.height):
        for x in range(isolated.width):
            red, green, blue, _ = pixels[x, y]
            lightness = min(red, green, blue)
            alpha = max(0, min(255, (lightness - 105) * 4))
            pixels[x, y] = (red, green, blue, alpha)
    bounds = isolated.getchannel("A").getbbox()
    if bounds is None:
        raise RuntimeError("Legacy logo foreground extraction produced an empty template")
    return isolated.crop(bounds)


if __name__ == "__main__":
    main()

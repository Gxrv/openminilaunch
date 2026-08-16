#!/usr/bin/env python3
"""Generate Android launcher icon assets from the checked-in Mink source artwork."""

from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "artwork" / "mink-launcher-logo-source.png"
RES = ROOT / "app" / "src" / "main" / "res"

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}


def isolated_mark(source: Image.Image) -> Image.Image:
    """Remove only the connected outer background; preserve the enclosed face fill."""
    image = source.convert("RGBA")
    flood = image.copy()
    width, height = flood.size
    for seed in ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)):
        ImageDraw.floodfill(flood, seed, (0, 0, 0, 0), thresh=32)

    mask = flood.getchannel("A").filter(ImageFilter.GaussianBlur(0.8))
    bbox = mask.getbbox()
    if bbox is None:
        raise RuntimeError("Could not isolate logo from its background")
    padding = 4
    bbox = (
        max(0, bbox[0] - padding),
        max(0, bbox[1] - padding),
        min(width, bbox[2] + padding),
        min(height, bbox[3] + padding),
    )
    mark = image.crop(bbox)
    mark.putalpha(mask.crop(bbox))
    return mark


def fit_mark(mark: Image.Image, size: int, coverage: float) -> Image.Image:
    max_dimension = max(mark.size)
    scale = size * coverage / max_dimension
    resized = mark.resize(
        (max(1, round(mark.width * scale)), max(1, round(mark.height * scale))),
        Image.Resampling.LANCZOS,
    )
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(
        resized,
        ((size - resized.width) // 2, (size - resized.height) // 2),
    )
    return canvas


def monochrome_mark(mark: Image.Image, size: int) -> Image.Image:
    fitted = fit_mark(mark, size, 0.61)
    luminance = fitted.convert("L")
    darkness = luminance.point(lambda value: max(0, min(255, round((205 - value) * 255 / 145))))
    alpha = ImageChops.multiply(fitted.getchannel("A"), darkness)
    result = Image.new("RGBA", fitted.size, (255, 255, 255, 0))
    result.putalpha(alpha)
    return result


def legacy_icon(mark: Image.Image, size: int, background: tuple[int, int, int], round_icon: bool) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (*background, 0 if round_icon else 255))
    if round_icon:
        ImageDraw.Draw(canvas).ellipse((0, 0, size - 1, size - 1), fill=(*background, 255))
    fitted = fit_mark(mark, size, 0.72 if round_icon else 0.84)
    canvas.alpha_composite(fitted)
    return canvas


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    corners = [source.getpixel(point)[:3] for point in ((0, 0), (source.width - 1, 0), (0, source.height - 1), (source.width - 1, source.height - 1))]
    background = tuple(round(sum(pixel[channel] for pixel in corners) / len(corners)) for channel in range(3))
    mark = isolated_mark(source)

    save(legacy_icon(mark, 512, background, False), ROOT / "artwork" / "mink-launcher-play-store-512.png")

    for density, scale in DENSITIES.items():
        legacy_size = round(48 * scale)
        adaptive_size = round(108 * scale)
        save(legacy_icon(mark, legacy_size, background, False), RES / f"mipmap-{density}" / "ic_launcher.png")
        save(legacy_icon(mark, legacy_size, background, True), RES / f"mipmap-{density}" / "ic_launcher_round.png")
        save(fit_mark(mark, adaptive_size, 0.61), RES / f"drawable-{density}" / "ic_launcher_foreground.png")
        save(monochrome_mark(mark, adaptive_size), RES / f"drawable-{density}" / "ic_launcher_monochrome.png")

    print(f"Generated launcher assets from {SOURCE.name}; sampled background RGB {background}")


if __name__ == "__main__":
    main()

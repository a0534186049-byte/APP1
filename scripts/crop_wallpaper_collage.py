#!/usr/bin/env python3
"""Split the approved six-panel wallpaper concept into app-ready images."""
from __future__ import annotations
import argparse
from pathlib import Path
from PIL import Image

PANELS = {
    "navy-gold.webp": (24, 26, 748, 322),
    "violet-gold.webp": (788, 26, 1511, 322),
    "burgundy-gold.webp": (24, 365, 748, 656),
    "emerald-gold.webp": (788, 365, 1511, 656),
    "black-gold.webp": (24, 704, 748, 997),
    "ivory-gold.webp": (788, 704, 1511, 997),
}

def save_lossless(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "WEBP", lossless=True, method=6)

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/backgrounds"))
    args = parser.parse_args()
    if not args.source.is_file():
        raise SystemExit(f"Wallpaper collage not found: {args.source}")
    with Image.open(args.source) as original:
        image = original.convert("RGB")
    if image.size != (1536, 1024):
        raise SystemExit(f"Unexpected collage dimensions {image.size}; refusing incorrect crops.")
    for filename, bounds in PANELS.items():
        portrait = image.crop(bounds).transpose(Image.Transpose.ROTATE_270)
        save_lossless(portrait, args.output / filename)
    # Separate landscape image for widget; never derived from the current app theme.
    save_lossless(image.crop(PANELS["navy-gold.webp"]), args.output / "widget-navy-gold.webp")
    print("Generated six portrait wallpapers and the independent widget background.")
    for filename in [*PANELS, "widget-navy-gold.webp"]:
        path=args.output/filename
        print(f"{path}: {path.stat().st_size} bytes")

if __name__ == "__main__":
    main()

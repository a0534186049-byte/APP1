#!/usr/bin/env python3
"""Create exact lossless WebP background crops from the supplied triptych.

The script is intentionally deterministic and validates every output before it
stages/commits the generated assets to the current PR branch.
"""
from __future__ import annotations

import argparse
import hashlib
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "backgrounds"

CROPS = {
    "gold.webp": ((0, 0, 497, 1025), (497, 1025)),
    "gradient.webp": ((508, 0, 1026, 1025), (518, 1025)),
    "premium.webp": ((1037, 0, 1534, 1025), (497, 1025)),
}
WHITE_THRESHOLD = 235
MAX_WHITE_EDGE_RATIO = 0.05


def run_git(*args: str, capture: bool = False) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )
    return result.stdout.strip() if capture else ""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def near_white_ratio(image: Image.Image, x: int) -> float:
    rgb = image.convert("RGB")
    white_count = 0
    for y in range(rgb.height):
        red, green, blue = rgb.getpixel((x, y))
        if red > WHITE_THRESHOLD and green > WHITE_THRESHOLD and blue > WHITE_THRESHOLD:
            white_count += 1
    return white_count / rgb.height


def validate_output(path: Path, expected_size: tuple[int, int], source_crop: Image.Image | None) -> dict:
    with Image.open(path) as opened:
        if opened.format != "WEBP":
            raise RuntimeError(f"{path.name}: expected WebP, got {opened.format}")
        if opened.size != expected_size:
            raise RuntimeError(f"{path.name}: expected {expected_size}, got {opened.size}")
        output = opened.convert("RGB")
        output.load()

    if source_crop is not None:
        expected = source_crop.convert("RGB")
        difference = ImageChops.difference(expected, output)
        if difference.getbbox() is not None:
            raise RuntimeError(f"{path.name}: decoded WebP pixels differ from the exact source crop (not lossless)")

    left_ratio = near_white_ratio(output, 0)
    right_ratio = near_white_ratio(output, output.width - 1)
    if left_ratio >= MAX_WHITE_EDGE_RATIO or right_ratio >= MAX_WHITE_EDGE_RATIO:
        raise RuntimeError(
            f"{path.name}: near-white edge ratio too high "
            f"(first={left_ratio:.3%}, last={right_ratio:.3%}); likely separator remains"
        )

    return {
        "name": path.name,
        "width": output.width,
        "height": output.height,
        "size": path.stat().st_size,
        "sha256": sha256(path),
        "left_white_percent": round(left_ratio * 100, 4),
        "right_white_percent": round(right_ratio * 100, 4),
    }


def verify_no_old_backgrounds() -> None:
    if not ASSET_DIR.is_dir():
        raise RuntimeError(f"Background directory not found: {ASSET_DIR}")
    present = {path.name for path in ASSET_DIR.iterdir() if path.is_file()}
    # The widget keeps its own landscape image; it is not an app theme.
    expected = set(CROPS) | {"widget-navy-gold.webp"}
    if present != expected:
        raise RuntimeError(
            "Unexpected or missing files in backgrounds directory. "
            f"Expected {sorted(expected)}, found {sorted(present)}"
        )
    for name in sorted(expected):
        path = ASSET_DIR / name
        with Image.open(path) as image:
            if image.width == 240:
                raise RuntimeError(f"Old 240px background remains: {name}")


def create_crops(source_path: Path) -> list[dict]:
    if not source_path.is_file():
        raise FileNotFoundError(f"Triptych source not found: {source_path}")
    with Image.open(source_path) as opened:
        if opened.format != "PNG":
            raise RuntimeError(f"Source must be PNG, got {opened.format}")
        if opened.size != (1534, 1025):
            raise RuntimeError(f"Source dimensions must be 1534x1025, got {opened.size}")
        source = opened.convert("RGB")
        source.load()

    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    results = []
    for filename, (box, expected_size) in CROPS.items():
        crop = source.crop(box)
        if crop.size != expected_size:
            raise RuntimeError(f"{filename}: crop size mismatch: {crop.size}")

        destination = ASSET_DIR / filename
        temporary = destination.with_suffix(".tmp.webp")
        crop.save(temporary, format="WEBP", lossless=True, quality=100, method=6)
        temporary.replace(destination)
        results.append(validate_output(destination, expected_size, crop))

    verify_no_old_backgrounds()
    return results


def verify_existing() -> list[dict]:
    results = []
    for filename, (_, expected_size) in CROPS.items():
        path = ASSET_DIR / filename
        results.append(validate_output(path, expected_size, None))
    verify_no_old_backgrounds()
    return results


def print_report(results: list[dict]) -> None:
    for item in results:
        print(
            f"BACKGROUND {item['name']} "
            f"resolution={item['width']}x{item['height']} "
            f"size={item['size']} bytes "
            f"sha256={item['sha256']} "
            f"near_white_first={item['left_white_percent']:.4f}% "
            f"near_white_last={item['right_white_percent']:.4f}%"
        )
    print("BACKGROUND_CHECKS: exact dimensions, WebP format, lossless pixels (when source present), and edge thresholds passed.")
    print("BACKGROUND_CHECKS: no old 240px assets or unexpected files remain in the backgrounds directory.")


def commit_and_push(branch: str) -> None:
    if not branch or branch.startswith("-") or "/" not in branch and " " in branch:
        raise RuntimeError("Invalid target branch name")
    files = [
        "triptych.png",
        "scripts/crop_triptych.py",
        *[str(ASSET_DIR.relative_to(ROOT) / filename) for filename in CROPS],
    ]
    run_git("add", "--", *files)
    staged = subprocess.run(
        ["git", "diff", "--cached", "--quiet"],
        cwd=ROOT,
        check=False,
    )
    if staged.returncode == 0:
        print("No generated changes to commit; the assets already match.")
        return
    if staged.returncode != 1:
        raise RuntimeError("Could not inspect staged image changes")
    run_git("config", "user.name", "github-actions[bot]")
    run_git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    run_git("commit", "-m", "Section 3: crop source triptych into lossless WebP backgrounds")
    commit_sha = run_git("rev-parse", "HEAD", capture=True)
    run_git("push", "origin", f"HEAD:refs/heads/{branch}")
    print(f"BACKGROUND_COMMIT: {commit_sha}")
    print(f"BACKGROUND_COMMIT_PUSHED_TO: {branch}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default="triptych.png")
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--commit", action="store_true")
    parser.add_argument("--branch", default="")
    args = parser.parse_args()

    if args.verify_only:
        results = verify_existing()
    else:
        source = (ROOT / args.source).resolve()
        results = create_crops(source)

    print_report(results)
    if args.commit:
        if args.verify_only:
            raise RuntimeError("--commit cannot be combined with --verify-only")
        commit_and_push(args.branch)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise

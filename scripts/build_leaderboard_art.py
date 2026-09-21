#!/usr/bin/env python3
"""Builds the three Game Center leaderboard images from art we already have.

App Store Connect wants one image per leaderboard, 512x512 or 1024x1024, RGB,
flattened, no alpha. There is no designed source for these, so rather than
invent a fourth visual language they are assembled out of the app icon's
background recipe (diagonal amber gradient, tilted grid, two white paws) with a
dog still or the streak flame dropped on top. Nothing here is new art; it is the
icon's treatment applied to assets in `art/source` and `:libraries:resources`.

Three boards, three reads, distinguished by subject and by colour so they are
still tellable apart at the size Game Center actually draws them:

    lifetime_score   dog with the trophy, on the icon's amber-to-coral
    longest_streak   the streak flame, on a hotter amber-to-red
    weekly_score     dog with the pencil, on the app's blue

The paw geometry is copied from `art/source/icons/paw.svg` rather than eyeballed,
so these paws and the ones in the app are the same shape.

Usage:
    ./scripts/build_leaderboard_art.py            # rebuild all three
    ./scripts/build_leaderboard_art.py --check    # exit 1 if any is out of date
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
STILLS = ROOT / "art" / "source" / "stills"
DRAWABLE = ROOT / "libraries" / "resources" / "src" / "commonMain" / "composeResources" / "drawable"
OUT = ROOT / "docs" / "store" / "gamecenter"

SIZE = 1024

# Sampled off pages/app-icon.png rather than guessed: the icon runs from an
# amber top-left to a coral bottom-right, and the two amber boards sit on that
# same ramp so they read as siblings of the icon.
AMBER = (245, 168, 47)
CORAL = (229, 101, 82)
EMBER = (238, 116, 40)
RED = (193, 36, 40)
BLUE_LIGHT = (79, 163, 247)
BLUE_DEEP = (47, 111, 208)

BOARDS = {
    "lifetime_score": {"subject": STILLS / "dog-solved.png", "from": AMBER, "to": CORAL},
    # Starts hotter and ends deeper than the lifetime board. On the icon's own
    # amber-to-coral ramp the two were near enough to be mistaken for each other
    # in a list, which is the one thing these images have to avoid.
    "longest_streak": {"subject": DRAWABLE / "flame.webp", "from": EMBER, "to": RED},
    "weekly_score": {"subject": STILLS / "dog-thinking.png", "from": BLUE_LIGHT, "to": BLUE_DEEP},
}

GRID_SPACING = 165
GRID_WIDTH = 11
GRID_ALPHA = 42
GRID_ANGLE = 20

SUBJECT_FRACTION = 0.62


def gradient(start: tuple[int, int, int], end: tuple[int, int, int]) -> Image.Image:
    """A corner-to-corner ramp, drawn a row at a time down the diagonal."""
    base = Image.new("RGB", (SIZE, SIZE))
    pixels = base.load()
    for y in range(SIZE):
        for x in range(SIZE):
            t = (x + y) / (2 * (SIZE - 1))
            pixels[x, y] = tuple(round(s + (e - s) * t) for s, e in zip(start, end))
    return base


def grid_overlay() -> Image.Image:
    """Tilted white lines, drawn oversized then rotated so no end is visible."""
    big = SIZE * 2
    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    white = (255, 255, 255, GRID_ALPHA)
    for offset in range(0, big, GRID_SPACING):
        draw.line([(offset, 0), (offset, big)], fill=white, width=GRID_WIDTH)
        draw.line([(0, offset), (big, offset)], fill=white, width=GRID_WIDTH)
    layer = layer.rotate(GRID_ANGLE, resample=Image.BICUBIC)
    inset = (big - SIZE) // 2
    return layer.crop((inset, inset, inset + SIZE, inset + SIZE))


def paw(size: int, angle: float) -> Image.Image:
    """One paw print, at the proportions in art/source/icons/paw.svg (120 box)."""
    scale = size / 120
    supersample = 4
    box = int(size * supersample)
    layer = Image.new("RGBA", (box, box), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    white = (255, 255, 255, 235)

    def ellipse(cx: float, cy: float, rx: float, ry: float) -> None:
        f = scale * supersample
        draw.ellipse(
            [(cx - rx) * f, (cy - ry) * f, (cx + rx) * f, (cy + ry) * f],
            fill=white,
        )

    ellipse(60, 79, 30, 24)
    for cx, cy in ((28, 47), (49, 31), (71, 31), (92, 47)):
        ellipse(cx, cy, 12, 12)

    layer = layer.resize((size, size), Image.LANCZOS)
    return layer.rotate(angle, resample=Image.BICUBIC, expand=True)


def subject(path: Path) -> Image.Image:
    """The dog or the flame, fitted into the middle of the canvas with a shadow."""
    art = Image.open(path).convert("RGBA")
    # Trim the transparent margin first. The dog stills carry a lot of it and
    # the flame carries almost none, so fitting the raw files to one box makes
    # the flame look twice the size of the dogs.
    art = art.crop(art.getbbox())
    target = int(SIZE * SUBJECT_FRACTION)
    art.thumbnail((target, target), Image.LANCZOS)

    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    x = (SIZE - art.width) // 2
    y = (SIZE - art.height) // 2

    shadow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 70), (x, y + 16), art)
    layer = Image.alpha_composite(layer, shadow.filter(ImageFilter.GaussianBlur(18)))
    layer.paste(art, (x, y), art)
    return layer


def build(name: str) -> Image.Image:
    spec = BOARDS[name]
    canvas = gradient(spec["from"], spec["to"]).convert("RGBA")
    canvas = Image.alpha_composite(canvas, grid_overlay())

    top_left = paw(150, -18)
    canvas.alpha_composite(top_left, (78, 86))
    bottom_right = paw(190, 14)
    canvas.alpha_composite(bottom_right, (SIZE - bottom_right.width - 72, SIZE - bottom_right.height - 80))

    canvas = Image.alpha_composite(canvas, subject(spec["subject"]))
    # Flattened on purpose: App Store Connect rejects an alpha channel here the
    # same way it rejects one on a store icon.
    return canvas.convert("RGB")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="exit 1 if any output is out of date")
    args = parser.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    stale = []
    for name in BOARDS:
        built = build(name)
        path = OUT / f"{name}.png"
        if args.check:
            if not path.exists() or Image.open(path).convert("RGB").tobytes() != built.tobytes():
                stale.append(path.relative_to(ROOT))
            continue
        built.save(path)
        print(f"wrote {path.relative_to(ROOT)} {built.size[0]}x{built.size[1]} {built.mode}")

    if stale:
        for path in stale:
            print(f"out of date: {path}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

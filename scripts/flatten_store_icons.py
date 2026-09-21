#!/usr/bin/env python3
"""Removes transparency from the two icons the stores refuse to take with it.

Apple rejects an app icon carrying an alpha channel at upload time, ITMS-90717,
and the rejection costs a whole round trip because it arrives after the build
has been processed. Play wants a 32-bit PNG with no transparency for the listing
icon. Both source files were exported with alpha, so both were going to fail.

The two need different treatment, which is the only interesting thing here:

- **The iOS icon** is 1024x1024 with *rounded corners baked in*, so the corners
  are genuinely transparent rather than merely carrying an unused channel.
  Dropping the channel would paint them black. They are filled instead with a
  diagonal gradient sampled from the icon's own opaque diagonal, so the seam
  falls exactly where the artwork's own background already was. iOS masks the
  icon with a superellipse that sits inside those corners anyway, so on a device
  none of this is visible; it exists to satisfy the validator.
- **The Play icon** is 512x512 and already opaque edge to edge. It only carries
  an unused alpha channel, so the channel is simply dropped.

Neither file changes visually. Re-run after any icon re-export.

Usage:
    ./scripts/flatten_store_icons.py            # rewrite both in place
    ./scripts/flatten_store_icons.py --check    # exit 1 if either still has alpha
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
APPLE = ROOT / "apps/ios/iosApp/Assets.xcassets/AppIcon.appiconset/Sodogku Icon-selection (8).png"
PLAY = ROOT / "apps/compose/src/androidMain/ic_launcher-playstore.png"

# Sampled off the artwork's own opaque diagonal rather than chosen. Keep these
# in step with the file if the icon is ever re-exported; the check below will
# not catch a colour drift, only a missing flatten.
SAMPLE_NEAR = (120, 120)
SAMPLE_FAR = (904, 904)


def flatten_apple(image: Image.Image) -> Image.Image:
    """Fill the rounded corners with the icon's own background ramp."""
    rgb = image.convert("RGB")
    start = rgb.getpixel(SAMPLE_NEAR)
    end = rgb.getpixel(SAMPLE_FAR)

    width, height = image.size
    background = Image.new("RGB", image.size)
    pixels = background.load()
    for y in range(height):
        for x in range(width):
            t = (x + y) / (width + height - 2)
            pixels[x, y] = tuple(round(s + (e - s) * t) for s, e in zip(start, end))

    background.paste(image, (0, 0), image)
    return background


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="exit 1 if either icon still has alpha")
    args = parser.parse_args()

    failures = []
    for path, flatten in ((APPLE, True), (PLAY, False)):
        image = Image.open(path)
        has_alpha = image.mode in ("RGBA", "LA") or "transparency" in image.info
        if args.check:
            if has_alpha:
                failures.append(path.relative_to(ROOT))
            continue
        if not has_alpha:
            print(f"already flat  {path.relative_to(ROOT)}")
            continue
        flat = flatten_apple(image.convert("RGBA")) if flatten else image.convert("RGB")
        flat.save(path)
        print(f"flattened     {path.relative_to(ROOT)} {flat.size[0]}x{flat.size[1]} {flat.mode}")

    for path in failures:
        print(f"still has an alpha channel: {path}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())

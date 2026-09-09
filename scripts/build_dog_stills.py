#!/usr/bin/env python3
"""Derives the shipped dog stills from the 1024px masters in art/source/stills.

These used to be downscaled by hand, and it showed. Every still is drawn by the
same `Dog()` composable, whose *default* size is `DogHeroSize` (160dp) — so a
still cut for the 48dp booster circle gets used at 160dp the moment somebody
writes `Dog(pose = DogPose.Focused)` without a size, which is exactly what
BoosterPrompt and GameDialogs do. dog-still and dog-focused shipped at 192px and
were being blown up 2.5x on a 3x screen.

So the size is not per-asset judgement any more. Every still ships at
STILL_SIZE, which is the largest place any of them renders (160dp) times the
densest screen we care about (4x, xxxhdpi). One number, no per-file decisions,
and no way for a new call site to quietly outgrow its asset.

The cost is APK size, and it is worth naming: measured, the six stills go from
587KB to 1054KB, so +467KB. That is against sprite sheets that already cost
five megabytes, for the six images a player looks at directly and at rest — a
dialog dog holds still on screen for as long as the dialog is open, which is
the worst possible place to hide a soft upscale.

Usage:
    ./scripts/build_dog_stills.py            # rebuild every shipped still
    ./scripts/build_dog_stills.py --check    # exit 1 if any is out of date
"""
import argparse
import os
import sys
from PIL import Image

SOURCE_DIR = "art/source/stills"
OUT_DIR = "libraries/resources/src/commonMain/composeResources/drawable"

# 160dp (DogHeroSize, the largest render of any still) at 4x density.
STILL_SIZE = 640

# Masters that become shipped drawables. dog-appmark is deliberately absent: it
# is the launcher icon's source and goes through the platform icon pipelines,
# not through composeResources.
STILLS = ["still", "focused", "solved", "thinking", "paused", "hardmode"]


def source_path(name: str) -> str:
    return os.path.join(SOURCE_DIR, f"dog-{name}.png")


def out_path(name: str) -> str:
    return os.path.join(OUT_DIR, f"dog_{name}.png")


def build(name: str) -> None:
    master = Image.open(source_path(name)).convert("RGBA")
    if master.size != (1024, 1024):
        raise SystemExit(f"dog-{name}.png is {master.size}, expected (1024, 1024)")
    master.resize((STILL_SIZE, STILL_SIZE), Image.LANCZOS).save(out_path(name), optimize=True)
    size = os.path.getsize(out_path(name))
    print(f"dog_{name}.png: {STILL_SIZE}px, {size // 1024}KB")


def check(name: str) -> bool:
    path = out_path(name)
    if not os.path.exists(path):
        print(f"dog_{name}.png: missing")
        return False
    width, height = Image.open(path).size
    if (width, height) != (STILL_SIZE, STILL_SIZE):
        print(f"dog_{name}.png: {width}x{height}, expected {STILL_SIZE}x{STILL_SIZE}")
        return False
    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify sizes without rewriting")
    args = parser.parse_args()

    # Run from the repo root whatever the caller's cwd is, so the paths above
    # can stay readable.
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

    if args.check:
        if not all([check(name) for name in STILLS]):
            sys.exit(1)
        print(f"all {len(STILLS)} stills are {STILL_SIZE}px")
    else:
        for name in STILLS:
            build(name)

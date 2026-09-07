#!/usr/bin/env python3
"""Packs an animated WebP clip into a sprite sheet the app can step through.

Animated WebP does not play on Compose Multiplatform iOS: Coil's animated path
goes through Android's ImageDecoder, and Skia hands back a single frame. A sheet
of frames renders identically on both platforms, costs one bitmap however many
dogs are on the board, and puts the frame rate under our control.

Usage:
    ./scripts/build_dog_sprites.py <clip.webp> <out.png> [--size 128] [--frames 20]
"""
import argparse
import math
from PIL import Image


def build(source: str, out: str, size: int, frames: int) -> None:
    clip = Image.open(source)
    total = getattr(clip, "n_frames", 1)
    # Even sampling across the loop rather than the first N frames, so the
    # packed animation still reads as the whole gesture.
    picks = [round(i * total / frames) for i in range(frames)]

    columns = math.ceil(math.sqrt(frames))
    rows = math.ceil(frames / columns)
    sheet = Image.new("RGBA", (columns * size, rows * size), (0, 0, 0, 0))

    for index, frame in enumerate(picks):
        clip.seek(frame)
        cell = clip.convert("RGBA").resize((size, size), Image.LANCZOS)
        sheet.paste(cell, ((index % columns) * size, (index // columns) * size))

    sheet.save(out, optimize=True)
    print(f"{out}: {frames} frames of {total}, {columns}x{rows} grid at {size}px")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("out")
    parser.add_argument("--size", type=int, default=128)
    parser.add_argument("--frames", type=int, default=20)
    args = parser.parse_args()
    build(args.source, args.out, args.size, args.frames)

#!/usr/bin/env python3
"""Drives the app on a connected emulator by *text*, not by pixel guesses.

Screenshot-and-tap-by-coordinate breaks every time a layout shifts or the app
boots a second slower. This dumps the accessibility tree and taps the node whose
text matches, retrying until it appears.

    ./scripts/dev/drive.py launch
    ./scripts/dev/drive.py tap "I know how to play"
    ./scripts/dev/drive.py shot /tmp/board.png

`launch` uses `am start`, deliberately, and NOT `monkey -p <pkg> -c LAUNCHER 1`.
Monkey is the obvious way to start an app by package name, and its trailing
number is not a repeat count — it is how many pseudo-random input events monkey
fires *after* the app comes up. So every launch injected one stray event into the
first frame.

Measured, on this emulator at the onboarding screen: **1 launch in 16** ended up
somewhere other than where it should have been, once leaving the device on the
launcher entirely. Low enough to look like flakiness, high enough that a session
with dozens of launches hits it repeatedly — which is what happened during
launch-gate verification on 2026-09-07, where it closed a banner twice and wrote
a persisted dismissal into AppData, making the feature look broken.

That rate is the reason this matters. Tooling that fails outright gets fixed;
tooling that acts on the app 6% of the time makes every screenshot after it one
interaction ahead of where you think you are, and you blame the app.
"""
import re
import subprocess
import sys
import time

PKG = "com.sodogku.debug"
LAUNCH_ACTIVITY = "com.sodogku.MainActivity"
SERIAL = ["-s", "emulator-5554"]


def adb(*args: str, binary: bool = False):
    result = subprocess.run(["adb", *SERIAL, *args], capture_output=True)
    return result.stdout if binary else result.stdout.decode(errors="replace")


def nodes():
    adb("shell", "rm", "-f", "/sdcard/ui.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    found = []
    for match in re.finditer(r'(text|content-desc)="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        label = match.group(2)
        if not label:
            continue
        x1, y1, x2, y2 = (int(match.group(i)) for i in range(3, 7))
        found.append((label, (x1 + x2) // 2, (y1 + y2) // 2))
    return found


def tap(label: str, timeout: float = 30.0) -> bool:
    target = label.casefold()
    deadline = time.time() + timeout
    while time.time() < deadline:
        for text, x, y in nodes():
            if target in text.casefold():
                adb("shell", "input", "tap", str(x), str(y))
                print(f"tapped '{text}' at ({x},{y})")
                return True
        time.sleep(1)
    print(f"never found '{label}'. on screen: {[t for t, _, _ in nodes()]}", file=sys.stderr)
    return False


def main() -> int:
    command = sys.argv[1]
    if command == "launch":
        adb("shell", "am", "force-stop", PKG)
        if "--fresh" in sys.argv:
            adb("shell", "pm", "clear", PKG)
        # Explicit component, no injected events. See the module docstring for
        # why `monkey` is not used here.
        adb("shell", "am", "start", "-W",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER",
            "-n", f"{PKG}/{LAUNCH_ACTIVITY}")
        # Wait for the app to actually be resumed. A fixed sleep races the boot
        # gate, which holds for up to eight seconds waiting on remote config.
        deadline = time.time() + 60
        while time.time() < deadline:
            if PKG in adb("shell", "dumpsys", "activity", "activities" ) and \
                    PKG in adb("shell", "dumpsys", "window", "windows"):
                break
            time.sleep(1)
        # Then wait for the first real (non-splash) frame.
        for _ in range(40):
            if any(t for t, _, _ in nodes()):
                return 0
            time.sleep(1)
        return 0
    if command == "tap":
        return 0 if tap(sys.argv[2]) else 1
    if command == "shot":
        with open(sys.argv[2], "wb") as handle:
            handle.write(adb("exec-out", "screencap", "-p", binary=True))
        return 0
    if command == "text":
        print("\n".join(t for t, _, _ in nodes()))
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())

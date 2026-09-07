#!/bin/bash

# Script to rename "goodtimes" to "kmptemplate" throughout the codebase

set -e
cd "$(dirname "$0")/.."

echo "🔄 Starting rename from 'goodtimes' to 'kmptemplate'..."
echo ""

python3 << 'EOF'
import os
import shutil

ROOT = os.getcwd()
SKIP = {'.git', '.gradle', '.kotlin', 'build', 'node_modules'}
EXTS = {'.kt', '.kts', '.java', '.xml', '.json', '.yaml', '.yml', '.md', '.txt',
        '.properties', '.swift', '.h', '.m', '.plist', '.entitlements', '.xcconfig',
        '.pbxproj', '.xcscheme', '.storyboard', '.xib', '.gradle', '.iml'}

REPLACEMENTS = [
    ('com.dangerfield.goodtimes', 'com.kmptemplate'),
    ('com.dangerfield.libraries', 'com.kmptemplate.libraries'),
    ('com.dangerfield.virtu', 'com.kmptemplate'),
    ('com.dangerfield.virtue', 'com.kmptemplate'),
    ('com.dangerfield.merizo', 'com.kmptemplate'),
    ('group.com.dangerfield.goodtimes', 'group.com.kmptemplate'),
    ('goodtimes.kotlin.multiplatform', 'kmptemplate.kotlin.multiplatform'),
    ('goodtimes.compose.multiplatform', 'kmptemplate.compose.multiplatform'),
    ('goodtimes.feature', 'kmptemplate.feature'),
    ('goodtimes.application', 'kmptemplate.application'),
    ('GoodTimesApplication', 'KMPTemplateApplication'),
    ('TheAppOfGoodTimes', 'KMPTemplate'),
    ('GoodTimes', 'KMPTemplate'),
    ('Goodtimes', 'KMPTemplate'),
    ('goodTimes', 'kmpTemplate'),
    ('projects.libraries.goodtimes', 'projects.libraries.kmptemplate'),
    (':libraries:goodtimes', ':libraries:kmptemplate'),
    ('libraries.goodtimes', 'libraries.kmptemplate'),
    ('rootProject.name = "Goodtimes"', 'rootProject.name = "KMPTemplate"'),
    ('goodtimes', 'kmptemplate'),
]

DIR_REPLACEMENTS = [
    ('com/dangerfield/goodtimes', 'com/kmptemplate'),
    ('com/dangerfield/libraries', 'com/kmptemplate/libraries'),
    ('com/dangerfield/virtu', 'com/kmptemplate'),
    ('com/dangerfield/virtue', 'com/kmptemplate'),
    ('com/dangerfield/merizo', 'com/kmptemplate'),
    ('com/kmptemplate/libraries/goodtimes', 'com/kmptemplate/libraries/kmptemplate'),  # Handle nested
]

def skip(p):
    return any(s in p.split(os.sep) for s in SKIP)

def rename_module():
    print("📁 Step 1: Renaming libraries/goodtimes -> libraries/kmptemplate...")
    src = os.path.join(ROOT, 'libraries', 'goodtimes')
    dst = os.path.join(ROOT, 'libraries', 'kmptemplate')
    if os.path.exists(src) and not os.path.exists(dst):
        os.rename(src, dst)
        print("   Done")
    else:
        print("   Skipped (already done or doesn't exist)")

def process_files():
    print("\n📝 Step 2: Replacing content in files...")
    count = 0
    for root, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP]
        if skip(root): continue
        for f in files:
            fp = os.path.join(root, f)
            ext = os.path.splitext(f)[1].lower()
            if ext not in EXTS: continue
            try:
                with open(fp, 'r', encoding='utf-8', errors='ignore') as file:
                    c = file.read()
                orig = c
                for old, new in REPLACEMENTS:
                    c = c.replace(old, new)
                if c != orig:
                    with open(fp, 'w', encoding='utf-8') as file:
                        file.write(c)
                    count += 1
            except: pass
    print(f"   Modified {count} files")

def rename_dirs():
    print("\n📁 Step 3: Renaming package directories...")
    count = 0
    dirs = []
    for root, dirnames, _ in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP]
        if skip(root): continue
        for d in dirnames:
            dirs.append(os.path.join(root, d))

    dirs.sort(key=lambda x: x.count(os.sep), reverse=True)

    for d in dirs:
        newp = d
        for old, new in DIR_REPLACEMENTS:
            newp = newp.replace(old, new)
        if d != newp and os.path.exists(d):
            os.makedirs(os.path.dirname(newp), exist_ok=True)
            if os.path.exists(newp):
                for item in os.listdir(d):
                    shutil.move(os.path.join(d, item), os.path.join(newp, item))
                try: os.rmdir(d)
                except: pass
            else:
                shutil.move(d, newp)
            count += 1
    print(f"   Renamed {count} directories")

def cleanup():
    print("\n🧹 Step 4: Cleaning up empty directories...")
    count = 0
    for root, dirs, _ in os.walk(ROOT, topdown=False):
        if skip(root): continue
        if 'dangerfield' in root:
            try:
                if not os.listdir(root):
                    os.rmdir(root)
                    count += 1
            except: pass
    print(f"   Removed {count} empty directories")

def rename_bl():
    print("\n📁 Step 5: Renaming build-logic package...")
    src = os.path.join(ROOT, 'build-logic/src/main/java/com/dangerfield/goodtimes')
    dst = os.path.join(ROOT, 'build-logic/src/main/java/com/kmptemplate')
    if os.path.exists(src):
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copytree(src, dst, dirs_exist_ok=True)
        shutil.rmtree(os.path.join(ROOT, 'build-logic/src/main/java/com/dangerfield'))
        print("   Done")
    else:
        print("   Skipped (already done)")

def rename_files():
    print("\n📄 Step 6: Renaming files...")
    count = 0
    for root, _, files in os.walk(ROOT):
        if skip(root): continue
        for f in files:
            if 'GoodTimes' in f or 'TheAppOfGoodTimes' in f:
                old = os.path.join(root, f)
                new = os.path.join(root, f.replace('GoodTimes', 'KMPTemplate').replace('TheAppOfGoodTimes', 'KMPTemplate'))
                if os.path.exists(old):
                    os.rename(old, new)
                    print(f"   {f} -> {os.path.basename(new)}")
                    count += 1
    print(f"   Renamed {count} files")

# Run in correct order
rename_module()
process_files()
rename_dirs()
cleanup()
rename_bl()
rename_files()
print("\n✅ Rename complete!")
EOF

echo ""
echo "📝 Next steps:"
echo "   1. git diff --stat"
echo "   2. ./gradlew :apps:compose:assembleDebug"

#!/usr/bin/env bash
# Patch prebuilt arm64 .so files in jniLibs to use 16KB page alignment.
#
# Required tool: patchelf 0.18 or later
#   macOS:  brew install patchelf
#   Ubuntu: sudo apt install patchelf   (Ubuntu 24.04+ ships 0.18)
#
# Run from the repo root:
#   bash tools/fix-so-16kb-alignment.sh
#
# After patching, rebuild in Android Studio and verify with APK Analyzer:
#   Build → Analyze APK → lib/arm64-v8a/<name>.so → check PHDR alignment = 0x4000

set -e

JNIDIR="app/src/main/jniLibs/arm64-v8a"

if ! command -v patchelf &>/dev/null; then
    echo "ERROR: patchelf not found."
    echo "  macOS:  brew install patchelf"
    echo "  Ubuntu: sudo apt install patchelf"
    exit 1
fi

PATCHELF_VERSION=$(patchelf --version 2>&1 | awk '{print $2}')
echo "patchelf $PATCHELF_VERSION"

for SO in "$JNIDIR"/*.so; do
    echo "Patching $(basename "$SO") ..."
    patchelf --page-size 16384 "$SO"
done

echo ""
echo "Done. Rebuild the project in Android Studio and re-test on the Fold 7."

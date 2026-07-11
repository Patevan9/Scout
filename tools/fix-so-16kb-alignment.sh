#!/usr/bin/env bash
# Patch prebuilt arm64 .so files in jniLibs to use 16KB page alignment.
#
# Required tool: patchelf 0.18 or later
#
# WINDOWS (WSL2) — open Ubuntu in WSL2, then:
#   sudo apt update && sudo apt install patchelf
#   Run this script from inside WSL2 using the Windows repo path, e.g.:
#   cd /mnt/c/Users/YourName/AndroidStudioProjects/Scout
#   bash tools/fix-so-16kb-alignment.sh
#
# If you don't have WSL2 yet:
#   Open PowerShell as Administrator and run:  wsl --install
#   Then restart, open the Ubuntu app, and follow the steps above.
#
# After patching, rebuild in Android Studio and verify with APK Analyzer:
#   Build → Analyze APK → lib/arm64-v8a/<name>.so → check PHDR alignment = 0x4000

set -e

JNIDIR="app/src/main/jniLibs/arm64-v8a"

if ! command -v patchelf &>/dev/null; then
    echo "ERROR: patchelf not found."
    echo ""
    echo "  Windows (WSL2): sudo apt update && sudo apt install patchelf"
    echo "  Ubuntu/Debian:  sudo apt update && sudo apt install patchelf"
    echo ""
    echo "  If you don't have WSL2: open PowerShell as Administrator and run:"
    echo "    wsl --install"
    echo "  Then restart and open the Ubuntu app."
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

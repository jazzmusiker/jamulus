#!/usr/bin/env bash
# Copyright (c) 2026 Thomas Mueller
# Author(s): Thomas Mueller
# Licensed under AGPL 3.0 or any later version. See COPYING for details.

set -euo pipefail

log() {
    printf '[%s] %s\n' "$(date +'%H:%M:%S')" "$*"
}

fail() {
    log "ERROR: $*"
    exit 1
}

usage() {
    cat <<'EOF'
Usage: ./android_build.sh [--build-only]

Builds the Compose Multiplatform Android debug APK with the native Jamulus
engine. By default, installs it on a connected Android device. Set
ANDROID_SERIAL to choose a specific device. Use --build-only to build without
installing it.

Optional environment variables:
  ANDROID_SDK_ROOT  Android SDK path (ANDROID_HOME is also recognized)
  ANDROID_NDK_ROOT  Android NDK path (NDK 27 or newer required)
  QT_ANDROID_DIR    Qt 6.8 or newer Android kit directory
  ANDROID_ABI       Android ABI to build (default: arm64-v8a)
  ADB               adb executable or path
  ANDROID_SERIAL    Device serial to install on
EOF
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_APK=true
ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --build-only)
            INSTALL_APK=false
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            fail "Unknown argument: $1"
            ;;
    esac
    shift
done

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        ANDROID_SDK_ROOT="$ANDROID_HOME"
    elif [[ "$(uname -s)" == "Darwin" ]]; then
        ANDROID_SDK_ROOT="${HOME}/Library/Android/sdk"
    else
        ANDROID_SDK_ROOT="${HOME}/Android/Sdk"
    fi
fi

QT_ANDROID_DIR="${QT_ANDROID_DIR:-${HOME}/Qt/6.10.3/android_arm64_v8a}"
if [[ -z "${ANDROID_NDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}/ndk/27.2.12479018" ]]; then
    ANDROID_NDK_ROOT="${ANDROID_SDK_ROOT}/ndk/27.2.12479018"
fi
if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
    ANDROID_NDK_ROOT="$(find "${ANDROID_SDK_ROOT}/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort | tail -n 1)"
fi
NDK_VERSION="$(sed -n 's/^Pkg.Revision[[:space:]]*=[[:space:]]*//p' "${ANDROID_NDK_ROOT}/source.properties" 2>/dev/null | head -n 1)"
NDK_MAJOR="${NDK_VERSION%%.*}"
if [[ ! "${NDK_MAJOR}" =~ ^[0-9]+$ ]] || (( NDK_MAJOR < 27 )); then
    fail "Android NDK 27 or newer is required for 16 KB page-size support (found: ${NDK_VERSION:-unknown})."
fi

GRADLEW="${ROOT_DIR}/gradlew"
APK_PATH="${ROOT_DIR}/composeApp/build/outputs/apk/debug/composeApp-debug.apk"

if [[ ! -x "${GRADLEW}" ]]; then
    fail "Gradle wrapper not found or not executable: ${GRADLEW}"
fi

if [[ ! -d "${ANDROID_SDK_ROOT}" ]]; then
    fail "Android SDK root not found: ${ANDROID_SDK_ROOT}"
fi

if [[ -z "${QT_ANDROID_DIR:-}" || ! -d "${QT_ANDROID_DIR}" ]]; then
    fail "Install Qt 6.10.3 or another 16 KB-compatible Android kit, then set QT_ANDROID_DIR to its directory."
fi

if ! command -v java >/dev/null 2>&1; then
    fail "Java was not found on PATH. Install JDK 17 or newer."
fi

export ANDROID_SDK_ROOT
export ANDROID_ABI QT_ANDROID_DIR
if [[ -n "${ANDROID_NDK_ROOT:-}" ]]; then
    export ANDROID_NDK_ROOT
fi
log "Using Android SDK: ${ANDROID_SDK_ROOT}"
log "Using Qt Android kit: ${QT_ANDROID_DIR}"
log "Building Compose Android debug APK with native Jamulus engine (${ANDROID_ABI})"

cd "${ROOT_DIR}"
"${GRADLEW}" :composeApp:assembleDebug

if [[ ! -f "${APK_PATH}" ]]; then
    APK_PATH="$(find "${ROOT_DIR}/composeApp/build/outputs/apk" -type f -name '*debug.apk' -print -quit)"
fi

if [[ -z "${APK_PATH}" || ! -f "${APK_PATH}" ]]; then
    fail "Could not find the built debug APK under ${ROOT_DIR}/composeApp/build/outputs/apk"
fi

log "Built APK: ${APK_PATH}"

case "$(uname -s)" in
    Darwin) NDK_HOST_TAG="darwin-x86_64" ;;
    Linux) NDK_HOST_TAG="linux-x86_64" ;;
    *) fail "Unsupported build host: $(uname -s)" ;;
esac
LLVM_READELF="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${NDK_HOST_TAG}/bin/llvm-readelf"
if [[ ! -x "${LLVM_READELF}" ]]; then
    fail "NDK llvm-readelf not found: ${LLVM_READELF}"
fi
ZIPALIGN="$(find "${ANDROID_SDK_ROOT}/build-tools" -mindepth 2 -maxdepth 2 -type f -name zipalign 2>/dev/null | sort | tail -n 1)"
if [[ -z "${ZIPALIGN}" ]]; then
    fail "Android zipalign was not found in ${ANDROID_SDK_ROOT}/build-tools."
fi
"${ZIPALIGN}" -c -P 16 -v 4 "${APK_PATH}" >/dev/null
APK_NATIVE_TMP="$(mktemp -d)"
trap 'rm -rf "${APK_NATIVE_TMP}"' EXIT
unzip -q "${APK_PATH}" 'lib/*/*.so' -d "${APK_NATIVE_TMP}"
while IFS= read -r -d '' LIBRARY; do
    while IFS= read -r SEGMENT_ALIGNMENT; do
        if (( SEGMENT_ALIGNMENT < 0x4000 )); then
            fail "APK library is not compatible with 16 KB pages: ${LIBRARY} (LOAD alignment ${SEGMENT_ALIGNMENT})."
        fi
    done < <("${LLVM_READELF}" --program-headers --wide "${LIBRARY}" | awk '$1 == "LOAD" { print $NF }')
done < <(find "${APK_NATIVE_TMP}/lib" -type f -name '*.so' -print0)
log "Verified APK ZIP alignment and native 16 KB LOAD alignment"
rm -rf "${APK_NATIVE_TMP}"
trap - EXIT

if [[ "${INSTALL_APK}" == false ]]; then
    exit 0
fi

if [[ -n "${ADB:-}" ]]; then
    ADB_COMMAND="${ADB}"
elif [[ -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]]; then
    ADB_COMMAND="${ANDROID_SDK_ROOT}/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
    ADB_COMMAND="$(command -v adb)"
else
    fail "adb was not found. Install Android platform-tools or use --build-only."
fi

DEVICE_LIST="$("${ADB_COMMAND}" devices | awk 'NR > 1 && $2 == "device" {print $1}')"
if [[ -z "${DEVICE_LIST}" ]]; then
    fail "No authorized Android device found. Connect a device or use --build-only."
fi

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    if ! printf '%s\n' "${DEVICE_LIST}" | awk -v serial="${ANDROID_SERIAL}" '$0 == serial {found = 1} END {exit !found}'; then
        fail "ANDROID_SERIAL is not an authorized connected device: ${ANDROID_SERIAL}"
    fi
    TARGET_SERIAL="${ANDROID_SERIAL}"
else
    TARGET_SERIAL="$(printf '%s\n' "${DEVICE_LIST}" | awk 'NR == 1 {print $1}')"
fi

log "Installing on device: ${TARGET_SERIAL}"
"${ADB_COMMAND}" -s "${TARGET_SERIAL}" install -r "${APK_PATH}"
log "Installation finished successfully"

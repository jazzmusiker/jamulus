#!/usr/bin/env bash
# Copyright (c) 2026 Thomas Mueller
# Author(s): Thomas Mueller
# Licensed under AGPL 3.0 or any later version. See COPYING for details.

set -euo pipefail

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MULTIPLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPOSITORY_DIR="$(cd "${MULTIPLATFORM_DIR}/.." && pwd)"
QT_ANDROID_DIR="${QT_ANDROID_DIR:-}"
ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
ANDROID_COMPOSE_NATIVE_OUTPUT="${ANDROID_COMPOSE_NATIVE_OUTPUT:-${MULTIPLATFORM_DIR}/composeApp/build/generated/androidJniLibs}"

if [[ -z "${ANDROID_SDK_ROOT}" ]]; then
    if [[ "$(uname -s)" == "Darwin" ]]; then
        ANDROID_SDK_ROOT="${HOME}/Library/Android/sdk"
    else
        ANDROID_SDK_ROOT="${HOME}/Android/Sdk"
    fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ "$(uname -s)" == "Darwin" ]]; then
        JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
    elif command -v java >/dev/null 2>&1; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    fi
fi
if [[ -z "${JAVA_HOME:-}" || ! -d "${JAVA_HOME}" ]]; then
    fail "JAVA_HOME is not set to a valid JDK."
fi

if [[ -z "${QT_ANDROID_DIR}" || ! -d "${QT_ANDROID_DIR}" ]]; then
    fail "Set QT_ANDROID_DIR to an installed Qt Android kit directory."
fi

QMAKE="${QMAKE:-${QT_ANDROID_DIR}/bin/qmake}"
if [[ ! -x "${QMAKE}" && -x "${QT_ANDROID_DIR}/bin/qmake6" ]]; then
    QMAKE="${QT_ANDROID_DIR}/bin/qmake6"
fi
if [[ ! -x "${QMAKE}" ]]; then
    fail "Android qmake not found or not executable: ${QMAKE}"
fi
QT_VERSION="$("${QMAKE}" -query QT_VERSION)"
if [[ -z "${QT_VERSION}" ]]; then
    fail "Could not determine the Qt Android kit version from ${QMAKE}."
fi
NATIVE_BUILD_DIR="${MULTIPLATFORM_DIR}/native/android-build/${ANDROID_ABI}-qt${QT_VERSION}"

if [[ ! -d "${ANDROID_SDK_ROOT}" ]]; then
    fail "Android SDK root not found: ${ANDROID_SDK_ROOT}"
fi

if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
    NDK_CANDIDATE="${ANDROID_SDK_ROOT}/ndk/latest"
    if [[ ! -d "${NDK_CANDIDATE}" ]]; then
        NDK_CANDIDATE="$(find "${ANDROID_SDK_ROOT}/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort | tail -n 1)"
    fi
    ANDROID_NDK_ROOT="${NDK_CANDIDATE}"
fi
if [[ -z "${ANDROID_NDK_ROOT}" || ! -d "${ANDROID_NDK_ROOT}" ]]; then
    fail "Android NDK not found. Set ANDROID_NDK_ROOT to an installed NDK."
fi
NDK_VERSION="$(sed -n 's/^Pkg.Revision[[:space:]]*=[[:space:]]*//p' "${ANDROID_NDK_ROOT}/source.properties" 2>/dev/null | head -n 1)"
NDK_MAJOR="${NDK_VERSION%%.*}"
if [[ ! "${NDK_MAJOR}" =~ ^[0-9]+$ ]] || (( NDK_MAJOR < 27 )); then
    fail "Android NDK 27 or newer is required for 16 KB page-size support (found: ${NDK_VERSION:-unknown})."
fi

case "$(uname -s)" in
    Darwin) NDK_HOST_TAG="darwin-x86_64" ;;
    Linux) NDK_HOST_TAG="linux-x86_64" ;;
    *) fail "Unsupported build host: $(uname -s)" ;;
esac

case "${ANDROID_ABI}" in
    arm64-v8a) NDK_TARGET="aarch64-linux-android" ;;
    armeabi-v7a) NDK_TARGET="arm-linux-androideabi" ;;
    x86) NDK_TARGET="i686-linux-android" ;;
    x86_64) NDK_TARGET="x86_64-linux-android" ;;
    *) fail "Unsupported Android ABI: ${ANDROID_ABI}" ;;
esac

ANDROID_NDK_HOST="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${NDK_HOST_TAG}"
if [[ ! -d "${ANDROID_NDK_HOST}" ]]; then
    fail "NDK toolchain not found: ${ANDROID_NDK_HOST}"
fi

MAKE="${MAKE:-make}"
if ! command -v "${MAKE}" >/dev/null 2>&1; then
    fail "Build tool not found: ${MAKE}"
fi

mkdir -p "${NATIVE_BUILD_DIR}"
cd "${REPOSITORY_DIR}"
export ANDROID_SDK_ROOT ANDROID_NDK_ROOT JAVA_HOME

QMAKE_ARGS=("-spec" "android-clang" "ANDROID_TARGET_ARCH=${ANDROID_ABI}" "ANDROID_ABIS=${ANDROID_ABI}")
QT_CONF="${QT_ANDROID_DIR}/bin/qt.conf"
if [[ -f "${QT_CONF}" ]]; then
    QMAKE_ARGS=("-qtconf" "${QT_CONF}" "${QMAKE_ARGS[@]}")
fi

"${QMAKE}" "${QMAKE_ARGS[@]}" "${REPOSITORY_DIR}/multiplatform/native/compose_engine.pro" -o "${NATIVE_BUILD_DIR}/Makefile"
"${MAKE}" -C "${NATIVE_BUILD_DIR}" -j"$(sysctl -n hw.ncpu 2>/dev/null || getconf _NPROCESSORS_ONLN)"

QT_LIB_DIR="$("${QMAKE}" -query QT_INSTALL_LIBS)"
ANDROID_LIB_DIR="${ANDROID_COMPOSE_NATIVE_OUTPUT}/${ANDROID_ABI}"
NATIVE_LIBRARY="${NATIVE_BUILD_DIR}/lib/libjamuluscompose_${ANDROID_ABI}.so"
LLVM_READELF="${ANDROID_NDK_HOST}/bin/llvm-readelf"

if [[ ! -f "${NATIVE_LIBRARY}" ]]; then
    fail "Could not find built Android JNI library: ${NATIVE_LIBRARY}"
fi
if [[ ! -x "${LLVM_READELF}" ]]; then
    fail "NDK llvm-readelf not found or not executable: ${LLVM_READELF}"
fi
mkdir -p "${ANDROID_LIB_DIR}"
rm -f "${ANDROID_LIB_DIR}"/*.so
JNI_LIBRARY="${ANDROID_LIB_DIR}/libjamuluscompose.so"
cp "${NATIVE_LIBRARY}" "${JNI_LIBRARY}"

NDK_TARGET_LIB_DIR="${ANDROID_NDK_HOST}/sysroot/usr/lib/${NDK_TARGET}"
PENDING_LIBRARIES=("${JNI_LIBRARY}")
PROCESSED_LIBRARIES=""
LIBRARY_INDEX=0
while [[ "${LIBRARY_INDEX}" -lt "${#PENDING_LIBRARIES[@]}" ]]; do
    CURRENT_LIBRARY="${PENDING_LIBRARIES[${LIBRARY_INDEX}]}"
    LIBRARY_INDEX=$((LIBRARY_INDEX + 1))
    while IFS= read -r NEEDED_LIBRARY; do
        [[ -z "${NEEDED_LIBRARY}" ]] && continue
        case " ${PROCESSED_LIBRARIES} " in
            *" ${NEEDED_LIBRARY} "*) continue ;;
        esac
        PROCESSED_LIBRARIES="${PROCESSED_LIBRARIES} ${NEEDED_LIBRARY}"

        case "${NEEDED_LIBRARY}" in
            libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libOpenSLES.so|libz.so|libjnigraphics.so|libGLESv2.so|libEGL.so|libmediandk.so|libaaudio.so|libnativewindow.so|libsync.so|libvulkan.so|libcamera2ndk.so|libatomic.so)
                continue
                ;;
        esac

        if [[ -f "${ANDROID_LIB_DIR}/${NEEDED_LIBRARY}" ]]; then
            DEPENDENCY_LIBRARY="${ANDROID_LIB_DIR}/${NEEDED_LIBRARY}"
        elif [[ -f "${QT_LIB_DIR}/${NEEDED_LIBRARY}" ]]; then
            DEPENDENCY_LIBRARY="${ANDROID_LIB_DIR}/${NEEDED_LIBRARY}"
            cp "${QT_LIB_DIR}/${NEEDED_LIBRARY}" "${DEPENDENCY_LIBRARY}"
        elif [[ -f "${NDK_TARGET_LIB_DIR}/${NEEDED_LIBRARY}" ]]; then
            DEPENDENCY_LIBRARY="${ANDROID_LIB_DIR}/${NEEDED_LIBRARY}"
            cp "${NDK_TARGET_LIB_DIR}/${NEEDED_LIBRARY}" "${DEPENDENCY_LIBRARY}"
        else
            fail "Android runtime dependency not found: ${NEEDED_LIBRARY} (required by ${CURRENT_LIBRARY})"
        fi
        PENDING_LIBRARIES+=("${DEPENDENCY_LIBRARY}")
    done < <("${LLVM_READELF}" --dynamic "${CURRENT_LIBRARY}" | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p')
done

for LIBRARY in "${ANDROID_LIB_DIR}"/*.so; do
    [[ -f "${LIBRARY}" ]] || continue
    while IFS= read -r SEGMENT_ALIGNMENT; do
        if (( SEGMENT_ALIGNMENT < 0x4000 )); then
            fail "Android library is not compatible with 16 KB pages: ${LIBRARY} (LOAD alignment ${SEGMENT_ALIGNMENT}). Install a Qt Android kit with 16 KB-aligned runtime libraries and use an NDK 27 or newer."
        fi
    done < <("${LLVM_READELF}" --program-headers --wide "${LIBRARY}" | awk '$1 == "LOAD" { print $NF }')
done

printf 'Built and staged Qt %s JNI libraries for %s in %s\n' "${QT_VERSION}" "${ANDROID_ABI}" "${ANDROID_LIB_DIR}"

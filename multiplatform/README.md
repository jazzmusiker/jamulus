<!--
Copyright (c) 2026 Thomas Mueller
Author(s): Thomas Mueller
Licensed under AGPL 3.0 or any later version. See COPYING for details.
-->

# Jamulus Compose Multiplatform

This directory contains the Compose Multiplatform application described in
[`compose_multiplatform.md`](compose_multiplatform.md). Client preferences and
the last ten manually used server addresses are stored locally on desktop and
Android.

Targets are Windows, macOS, Linux, and Android. Android is client-only. The desktop
app has a JNI bridge to the existing headless Jamulus C++ client and exposes
automatic server-list loading from all standard and configured custom
directories, direct connections, send mute, mixer controls, and chat. The macOS
distributable bundles the Qt frameworks used by the client and their dependent
libraries. Windows/Linux packages still need their platform-specific Qt runtime
integration. Android uses the same headless C++ client through JNI and the
Jamulus Oboe audio backend. Its native build requires a Qt Android kit.

The C++ engine must remain on its native real-time threads. Do not call JNI,
Compose, or Kotlin from an audio callback. Android native libraries must use
16 KB ELF load alignment; use Qt 6.10.3 or another Qt Android kit whose runtime
libraries pass the alignment checks, and NDK 27 or newer.

## Build

Use JDK 17 or newer and Gradle. The Android target requires the Android SDK,
NDK 27 or newer and a 16 KB-compatible Qt Android kit; set `QT_ANDROID_DIR` to
the kit directory. The configured default is Qt 6.10.3.
A desktop run or package task builds the JNI library with qmake and
the host make tool. Qt 5 and a supported native compiler must be installed;
Windows builds use `nmake` by default and can select another tool with
`-Pjamulus.make=...`. qmake can be selected with `-Pjamulus.qmake=...`.

```shell
./gradlew :composeApp:run
./gradlew :composeApp:assembleDebug
```

From the `multiplatform` directory, run `./android_build.sh` to build the Android
debug APK and install it on the first connected device. The script defaults to
Qt 6.10.3 at `~/Qt/6.10.3/android_arm64_v8a` and NDK 27.2 when installed in the
Android SDK. Override these paths with `QT_ANDROID_DIR` and `ANDROID_NDK_ROOT`.
It verifies 16 KB ELF load alignment and APK ZIP alignment before installing.
Set `ANDROID_SERIAL` to choose a device, or pass `--build-only` to build without
installing. The default ABI is `arm64-v8a`; set `ANDROID_ABI` to target a
different installed Qt kit and device architecture.

When IntelliJ opens this directory as the Gradle project, the shared
`Jamulus Desktop` run configuration starts `:composeApp:run`. Its qmake path
uses the default Homebrew Qt 5 location on Apple Silicon; change that run
configuration argument if Qt is installed elsewhere. IntelliJ's **Available
Devices** selector is for Android devices managed through ADB. macOS Desktop
is a run target and appears in the run-configuration selector instead.

## Desktop client prototype

The desktop source has a JNI bridge to Jamulus' headless C++ client. The
Gradle resource task compiles and embeds the host library for local runs and
development packages. On macOS, install Qt 5 and Xcode command line tools; on
Windows, install Qt 5 with a compiler matching the Qt kit and initialize its
compiler environment before invoking Gradle.

```shell
cd multiplatform
./gradlew :composeApp:run
```

The desktop engine stores its Jamulus settings in
`~/.jamulus-compose/Jamulus.ini` on macOS/Linux and the equivalent home
directory on Windows. Android stores its Jamulus settings in its private app
files and loads the same native client through JNI. The microphone permission
is requested before engine startup. Background audio service support is still
not implemented. Android builds stop if native libraries fail 16 KB page-size
alignment checks.

> 🤖 Used AI: GPT-6, Codex

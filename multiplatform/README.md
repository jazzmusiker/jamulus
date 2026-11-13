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
the Jamulus directory selector, automatic server-list loading, direct
connections, send mute, mixer controls, and chat. The default directory is
loaded from the Compose client's Jamulus settings; selecting another directory
refreshes the list and stores the choice. The macOS distributable bundles the
Qt frameworks used by the client and their dependent libraries. Windows/Linux
packages still need their platform-specific Qt runtime integration. Android
currently builds the Compose UI but uses an unavailable-engine placeholder and
cannot connect yet.

The C++ engine must remain on its native real-time threads. Do not call JNI,
Compose, or Kotlin from an audio callback.

## Build

Use JDK 17 or newer and Gradle. The Android target requires the Android SDK
and NDK. A desktop run or package task builds the JNI library with qmake and
the host make tool. Qt 5 and a supported native compiler must be installed;
Windows builds use `nmake` by default and can select another tool with
`-Pjamulus.make=...`. qmake can be selected with `-Pjamulus.qmake=...`.

```shell
./gradlew :composeApp:run
./gradlew :composeApp:assembleDebug
```

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
directory on Windows. The Android build currently uses the fail-closed
placeholder; its native Qt/Oboe integration, runtime microphone permission,
and foreground audio service remain to be implemented.

> 🤖 Used AI: GPT-6, Codex

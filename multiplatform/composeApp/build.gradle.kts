// Copyright (c) 2026 Thomas Mueller
// Author(s): Thomas Mueller
// Licensed under AGPL 3.0 or any later version. See COPYING for details.

import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val repositoryDirectory = rootProject.projectDir.parentFile
val nativeBuildDirectory = rootProject.layout.projectDirectory.dir("native/build")
val nativeLibraryName = when {
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "libjamuluscompose.dylib"
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "jamuluscompose.dll"
    else -> "libjamuluscompose.so"
}
val nativeLibraryFile = nativeBuildDirectory.file(nativeLibraryName)
val generatedNativeResources = layout.buildDirectory.dir("generated/jamulusNativeResources/desktopMain")
val generatedAndroidJniLibs = layout.buildDirectory.dir("generated/androidJniLibs")
val defaultQmake = if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
    "/opt/homebrew/opt/qt@5/bin/qmake"
} else {
    "qmake"
}
val qmakeExecutable = providers.gradleProperty("jamulus.qmake").orElse(defaultQmake)
val nativeMakeExecutable = providers.gradleProperty("jamulus.make").orElse(
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "nmake" else "make"
)
val isMacOS = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
val qtInstallPrefix = providers.gradleProperty("jamulus.qt.prefix").orElse("/opt/homebrew/opt/qt@5")
val androidQtDirectory = providers.gradleProperty("jamulus.android.qt")
    .orElse(providers.environmentVariable("QT_ANDROID_DIR"))
    .orElse(File(System.getProperty("user.home"), "Qt/6.10.3/android_arm64_v8a").absolutePath)
val androidQtJavaRuntime = androidQtDirectory.map { File(it, "jar/Qt6Android.jar") }
val androidQmake = providers.gradleProperty("jamulus.android.qmake")
    .orElse(providers.environmentVariable("ANDROID_QMAKE"))
val androidNdkRoot = providers.gradleProperty("jamulus.android.ndk")
    .orElse(providers.environmentVariable("ANDROID_NDK_ROOT"))
val androidSdkRoot = providers.gradleProperty("jamulus.android.sdk")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
val androidAbi = providers.gradleProperty("jamulus.android.abi")
    .orElse(providers.environmentVariable("ANDROID_ABI"))
    .orElse("arm64-v8a")

val buildJamulusNativeAndroid by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds and stages the Jamulus Qt/Oboe JNI engine for Android."

    inputs.files(fileTree(repositoryDirectory) {
        include("Jamulus.pro", "src/**", "libs/**", "multiplatform/native/**")
        exclude("multiplatform/native/build/**", "multiplatform/native/android-build/**")
    })
    inputs.property("androidQtDirectory", androidQtDirectory.orNull.orEmpty())
    inputs.property("androidQmake", androidQmake.orNull.orEmpty())
    inputs.property("androidNdkRoot", androidNdkRoot.orNull.orEmpty())
    inputs.property("androidSdkRoot", androidSdkRoot.orNull.orEmpty())
    inputs.property("androidAbi", androidAbi)
    outputs.dir(generatedAndroidJniLibs)
    workingDir(repositoryDirectory)
    commandLine("bash", repositoryDirectory.resolve("multiplatform/native/build_android_native.sh").absolutePath)
    doFirst {
        val qtDirectory = androidQtDirectory.orNull
            ?: throw GradleException("Set QT_ANDROID_DIR or -Pjamulus.android.qt to a Qt Android kit directory.")
        environment("QT_ANDROID_DIR", qtDirectory)
        environment("ANDROID_ABI", androidAbi.get())
        environment("ANDROID_COMPOSE_NATIVE_OUTPUT", generatedAndroidJniLibs.get().asFile.absolutePath)
        androidQmake.orNull?.let { environment("QMAKE", it) }
        androidNdkRoot.orNull?.let { environment("ANDROID_NDK_ROOT", it) }
        androidSdkRoot.orNull?.let { environment("ANDROID_SDK_ROOT", it) }
    }
}

val configureJamulusNativeDesktop by tasks.registering(Exec::class) {
    group = "build"
    description = "Configures the desktop JNI library with the repository qmake project."

    inputs.files(fileTree(repositoryDirectory) {
        include("Jamulus.pro", "src/**", "libs/**", "multiplatform/native/**")
        exclude("multiplatform/native/build/**")
    })
    inputs.property("qmakeExecutable", qmakeExecutable)
    if (file(qmakeExecutable.get()).isFile) {
        inputs.file(qmakeExecutable.get())
    }
    outputs.file(repositoryDirectory.resolve("JamulusCompose.mk"))
    workingDir(repositoryDirectory)
    environment("JAVA_HOME", System.getenv("JAVA_HOME") ?: System.getProperty("java.home"))
    doFirst { nativeBuildDirectory.asFile.mkdirs() }
    commandLine(
        qmakeExecutable.get(),
        "-o",
        "JamulusCompose.mk",
        "multiplatform/native/compose_engine.pro"
    )
}

val buildJamulusNativeDesktop by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the desktop JNI library from the existing Jamulus C++ client."
    dependsOn(configureJamulusNativeDesktop)

    inputs.files(fileTree(repositoryDirectory) {
        include("Jamulus.pro", "src/**", "libs/**", "multiplatform/native/**")
        exclude("multiplatform/native/build/**")
    })
    inputs.property("nativeMakeExecutable", nativeMakeExecutable)
    inputs.file(repositoryDirectory.resolve("JamulusCompose.mk"))
    outputs.file(nativeLibraryFile)
    workingDir(repositoryDirectory)
    val makeName = nativeMakeExecutable.get().substringAfterLast('/').substringAfterLast('\\')
    val parallelArgument = if (makeName.equals("nmake", ignoreCase = true)) emptyList() else listOf("-j1")
    commandLine(listOf(nativeMakeExecutable.get(), "-f", "JamulusCompose.mk") + parallelArgument)
}

val bundleJamulusNativeDesktop by tasks.registering(Copy::class) {
    group = "build"
    description = "Adds the host desktop JNI library to the Compose application resources."
    dependsOn(buildJamulusNativeDesktop)
    from(nativeLibraryFile)
    into(generatedNativeResources.map { it.dir("native") })
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(files(androidQtJavaRuntime.get()))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
            resources.srcDir(generatedNativeResources)
            resources.srcDir(repositoryDirectory.resolve("src/res"))
        }
    }
}

tasks.named("desktopProcessResources") {
    dependsOn(bundleJamulusNativeDesktop)
}

val deployMacQtRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Bundles Qt frameworks and the JNI library into the generated macOS app bundle."
    onlyIf { isMacOS }
    dependsOn(buildJamulusNativeDesktop)

    val appBundle = layout.buildDirectory.file("compose/binaries/main/app/Jamulus.app").get().asFile
    val bundledNativeLibrary = appBundle.resolve("Contents/MacOS/$nativeLibraryName")
    doFirst {
        bundledNativeLibrary.parentFile.mkdirs()
        nativeLibraryFile.asFile.copyTo(bundledNativeLibrary, overwrite = true)
    }
    commandLine(
        providers.gradleProperty("jamulus.macdeployqt").orElse(qtInstallPrefix.map { "$it/bin/macdeployqt" }).get(),
        appBundle.absolutePath,
        "-executable=${bundledNativeLibrary.absolutePath}",
        "-no-plugins",
        "-libpath=${qtInstallPrefix.get()}/lib"
    )
}

val signMacQtRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Re-signs the locally bundled macOS app after macdeployqt modifies it."
    onlyIf { isMacOS }
    dependsOn(deployMacQtRuntime)

    val appBundle = layout.buildDirectory.file("compose/binaries/main/app/Jamulus.app").get().asFile
    commandLine("codesign", "--force", "--deep", "--sign", "-", appBundle.absolutePath)
}

tasks.configureEach {
    if (name == "createDistributable") {
        finalizedBy(signMacQtRuntime)
    }
}

android {
    namespace = "org.jamulus.multiplatform"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.jamulussoftware.jamulus"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += androidAbi.get()
        }
    }

    sourceSets.getByName("main").assets.srcDir(repositoryDirectory.resolve("src/res"))
    sourceSets.getByName("main").jniLibs.srcDir(generatedAndroidJniLibs)
}

tasks.named("preBuild").configure {
    dependsOn(buildJamulusNativeAndroid)
}

compose.desktop {
    application {
        mainClass = "org.jamulus.multiplatform.MainKt"

        nativeDistributions {
            packageName = "Jamulus"
            packageVersion = "1.0.0"
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            macOS {
                bundleID = "org.jamulus.multiplatform"
                iconFile.set(project.file("src/desktopMain/resources/jamulus-multi.icns"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/jamulus-multi.ico"))
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/jamulus-multi.png"))
            }
        }
    }
}

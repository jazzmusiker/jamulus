// Copyright (c) 2026 Thomas Mueller
// Author(s): Thomas Mueller
// Licensed under AGPL 3.0 or any later version. See COPYING for details.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec

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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.jamulussoftware.jamulus"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets.getByName("main").assets.srcDir(repositoryDirectory.resolve("src/res"))
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
            }
        }
    }
}

/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    runCatching { DesktopNativeLibrary.load() }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Jamulus"
        ) {
            JamulusApp(isAndroid = false)
        }
    }
}

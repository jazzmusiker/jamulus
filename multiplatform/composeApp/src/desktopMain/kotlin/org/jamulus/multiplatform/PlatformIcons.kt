/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun loadJamulusIcon(resourcePath: String): ImageBitmap? = runCatching {
    val path = resourcePath.removePrefix("/")
    val bytes = object {}.javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
        ?: return null
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

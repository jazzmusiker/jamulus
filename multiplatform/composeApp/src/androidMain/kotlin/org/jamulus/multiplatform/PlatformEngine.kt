/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

actual fun createJamulusEngine(): JamulusEngine = UnavailableJamulusEngine(
    "Die native Jamulus-Engine ist noch nicht mit der Compose-App verbunden."
)

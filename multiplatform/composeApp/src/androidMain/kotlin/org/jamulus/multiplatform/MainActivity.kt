/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

import android.app.Activity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import org.qtproject.qt.android.QtNative
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private val audioPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        showJamulusApp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("Qt6Core_${Build.SUPPORTED_ABIS.first()}")
        setQtActivity(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            showJamulusApp()
        }
    }

    private fun showJamulusApp() {
        setContent {
            JamulusApp(isAndroid = true)
        }
    }

    override fun onDestroy() {
        if (currentQtActivity() === this) {
            setQtActivity(null)
        }
        super.onDestroy()
    }

    private fun setQtActivity(activity: Activity?) {
        val method = QtNative::class.java.declaredMethods.firstOrNull {
            it.name == "setActivity" && it.parameterTypes.firstOrNull() == Activity::class.java
        } ?: error("QtNative.setActivity(Activity) was not found")
        method.isAccessible = true
        val arguments = if (method.parameterCount == 1) {
            arrayOf<Any?>(activity)
        } else {
            arrayOf<Any?>(activity, null)
        }
        method.invoke(null, *arguments)
    }

    private fun currentQtActivity(): Activity? {
        val method = QtNative::class.java.declaredMethods.firstOrNull {
            it.name == "activity" && it.parameterCount == 0
        } ?: return null
        method.isAccessible = true
        return method.invoke(null) as? Activity
    }
}

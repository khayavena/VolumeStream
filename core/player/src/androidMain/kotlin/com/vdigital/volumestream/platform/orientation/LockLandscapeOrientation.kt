package com.vdigital.volumestream.platform.orientation

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun LockLandscapeOrientation() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            // Restore host activity policy (manifest/default) instead of forcing portrait.
            // For TV this keeps landscape, and for phone it follows the activity's config.
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

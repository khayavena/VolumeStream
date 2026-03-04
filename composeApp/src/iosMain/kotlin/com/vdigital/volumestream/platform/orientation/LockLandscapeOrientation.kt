package com.vdigital.volumestream.platform.orientation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
actual fun LockLandscapeOrientation() {
    DisposableEffect(Unit) {
        OrientationManager.forceLandscape = true
        onDispose {
            OrientationManager.forceLandscape = false
        }
    }
}

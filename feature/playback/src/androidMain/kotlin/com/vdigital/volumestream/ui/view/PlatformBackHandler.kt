package com.vdigital.volumestream.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Intercepts hardware back key, gesture back, and predictive-back on Android 14+
    BackHandler(enabled = enabled, onBack = onBack)
}


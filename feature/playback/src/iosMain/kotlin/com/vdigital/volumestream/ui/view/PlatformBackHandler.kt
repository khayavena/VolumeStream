package com.vdigital.volumestream.ui.view

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS handles back navigation natively via swipe-back gesture — no-op here.
}


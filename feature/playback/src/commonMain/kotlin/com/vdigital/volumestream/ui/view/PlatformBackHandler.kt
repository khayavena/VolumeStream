package com.vdigital.volumestream.ui.view

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform back gesture / hardware back button.
 * - Android: uses BackHandler from activity-compose to catch both the
 *   hardware back key and the predictive-back gesture.
 * - iOS: no-op — navigation is handled by swipe-back gesture natively.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)


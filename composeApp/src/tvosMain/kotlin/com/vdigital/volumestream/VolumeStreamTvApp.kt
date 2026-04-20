package com.vdigital.volumestream

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vdigital.volumestream.ui.view.TvNavigationShell
import org.koin.compose.KoinContext

private val GreenAccent = Color(0xFF00E676)

private val TvDarkColors = darkColors(
    primary         = GreenAccent,
    primaryVariant  = Color(0xFF00BFA5),
    secondary       = GreenAccent,
    background      = Color(0xFF000000),
    surface         = Color(0xFF141414),
    onPrimary       = Color.Black,
    onBackground    = Color.White,
    onSurface       = Color.White,
)

/**
 * Root composable for the Apple TV (tvOS) entry point.
 *
 * Uses standard [MaterialTheme] with a dark palette — no `androidx.tv:tv-material`
 * dependency is needed on tvOS; D-pad / Apple TV Remote focus is handled
 * automatically by Compose Multiplatform's built-in focus system.
 *
 * Navigation chrome is provided by [TvNavigationShell] — a persistent side panel
 * (replaces the phone's bottom navigation bar) that is naturally D-pad-navigable
 * because every nav item uses [androidx.compose.foundation.clickable].
 *
 * Koin is initialised in [TvMainViewController] → Compose entry point before this
 * composable is first composed; [KoinContext] surfaces the Koin instance into the
 * Compose composition tree.
 */
@Composable
fun VolumeStreamTvApp() {
    KoinContext {
        MaterialTheme(colors = TvDarkColors) {
            TvNavigationShell()
        }
    }
}


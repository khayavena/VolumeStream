package com.vdigital.volumestream

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vdigital.volumestream.ui.view.TvNavigationShell
import org.koin.compose.KoinContext

private val GreenAccent = Color(0xFF00E676)

private val TvDarkColors = darkColors(
    primary        = GreenAccent,
    primaryVariant = Color(0xFF00BFA5),
    secondary      = GreenAccent,
    background     = Color(0xFF000000),
    surface        = Color(0xFF141414),
    onPrimary      = Color.Black,
    onBackground   = Color.White,
    onSurface      = Color.White,
)

/**
 * Root composable for the Android TV / Google TV entry point.
 *
 * Mirrors [VolumeStreamApp] — starts Koin via [KoinApplication] so that
 * every [koinViewModel] call inside [TvNavigationShell] finds a live Koin context.
 */
@Composable
fun VolumeStreamTvApp() {
    KoinContext {
        MaterialTheme(colors = TvDarkColors) {
            TvNavigationShell()
        }
    }
}

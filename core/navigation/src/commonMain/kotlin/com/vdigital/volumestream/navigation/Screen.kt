package com.vdigital.volumestream.navigation

/**
 * Sealed class representing all top-level navigation destinations in the app.
 * Centralising routes here prevents string literals from leaking across modules.
 */
sealed class Screen(val route: String) {
    data object Splash    : Screen("splash")
    data object Login     : Screen("login")
    data object Register  : Screen("register")
    data object Home      : Screen("home")
    data object Search    : Screen("search")
    data object Profile   : Screen("profile")
    data object Settings  : Screen("settings")
    data object Downloads : Screen("downloads")
    data object Play      : Screen("play")
}

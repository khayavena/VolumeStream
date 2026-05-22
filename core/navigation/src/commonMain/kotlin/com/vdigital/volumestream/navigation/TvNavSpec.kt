package com.vdigital.volumestream.navigation

/** Shared TV navigation contract for Android TV and Apple TV shells. */
data class TvNavSpec(
    val route: String,
    val label: String,
    val iconToken: String,
    val sfSymbol: String,
)

fun tvNavSpecs(downloadsEnabled: Boolean): List<TvNavSpec> = buildList {
    add(TvNavSpec(route = Screen.Home.route, label = "Home", iconToken = "home", sfSymbol = "house.fill"))
    add(TvNavSpec(route = Screen.Search.route, label = "Search", iconToken = "search", sfSymbol = "magnifyingglass"))
    if (downloadsEnabled) {
        add(TvNavSpec(route = Screen.Downloads.route, label = "Downloads", iconToken = "downloads", sfSymbol = "list.bullet.rectangle.portrait"))
    }
    add(TvNavSpec(route = Screen.Profile.route, label = "Profile", iconToken = "profile", sfSymbol = "person.fill"))
    add(TvNavSpec(route = Screen.Settings.route, label = "Settings", iconToken = "settings", sfSymbol = "gearshape.fill"))
}


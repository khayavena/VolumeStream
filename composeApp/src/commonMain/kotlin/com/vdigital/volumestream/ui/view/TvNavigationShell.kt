package com.vdigital.volumestream.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vdigital.volumestream.navigation.Screen
import com.vdigital.volumestream.ui.viewmodel.AuthUiState
import com.vdigital.volumestream.ui.viewmodel.AuthViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

private val SideNavBg   = Color(0xFF1A1A1A)
private val Accent      = Color(0xFF00E676)
private val SelectedBg  = Color(0x3300E676)
private val FocusedBg   = Color(0x1A00E676)

/** Routes on which the side navigation panel should be hidden. */
private val NO_SIDE_NAV_ROUTES = setOf(
    Screen.Play.route,
    Screen.Splash.route,
    Screen.Login.route,
    Screen.Register.route,
)

private data class TvNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
)

private val NAV_ITEMS = listOf(
    TvNavItem("Home",      Screen.Home.route,      Icons.Default.Home),
    TvNavItem("Search",    Screen.Search.route,     Icons.Default.Search),
    TvNavItem("Downloads", Screen.Downloads.route,  Icons.AutoMirrored.Filled.List),
    TvNavItem("Profile",   Screen.Profile.route,    Icons.Default.Person),
    TvNavItem("Settings",  Screen.Settings.route,   Icons.Default.Settings),
)

/**
 * Platform-agnostic TV navigation shell.
 *
 * Renders a **persistent 220dp side panel** on the left with focusable nav
 * items, and a [NavHost] filling the remaining space.  This is the navigation
 * chrome used by the **Apple TV (tvOS)** entry point.
 *
 * Focus / D-pad navigation:
 * - Apple TV Remote's directional pad moves Compose focus between items.
 * - Selecting a focused item (click on remote) fires [Modifier.clickable].
 * - No platform-specific TV library is required — Compose Multiplatform's
 *   built-in focus traversal handles the remote automatically on tvOS ≥ 17.
 *
 * Session revocation is handled identically to [MainNavigationControllerView]:
 * any 401 TOKEN_INVALID response emits [SessionRevokedBus] which navigates
 * to the Login screen from here.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun TvNavigationShell() {
    val navController  = rememberNavController()
    val authViewModel: AuthViewModel = koinViewModel()
    val authState by authViewModel.uiState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthUiState.SessionRevoked) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
                launchSingleTop = true
            }
            authViewModel.resetState()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navHost: @Composable () -> Unit = {
        NavHost(
            navController    = navController,
            startDestination = Screen.Splash.route,
            modifier         = Modifier.fillMaxSize()
        ) {
            composable(Screen.Splash.route)    { SplashScreen(navController = navController) }
            composable(Screen.Login.route)     { LoginScreen(navController = navController) }
            composable(Screen.Register.route)  { RegisterScreen(navController = navController) }
            composable(Screen.Home.route)      { HomeScreen(navController = navController, isTvLayout = true) }
            composable(Screen.Search.route)    { SearchScreen(navController = navController) }
            composable(Screen.Downloads.route) { DownloadsScreen(navController = navController) }
            composable(Screen.Profile.route)   { ProfileScreen(navController = navController) }
            composable(Screen.Settings.route)  { SettingsScreen() }
            composable(Screen.Play.route)      { PlaybackView(onBack = { navController.popBackStack() }) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (currentRoute in NO_SIDE_NAV_ROUTES) {
            // Full-screen — no nav chrome (splash, login, register, playback).
            navHost()
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── Side navigation panel ────────────────────────────────────
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(SideNavBg)
                        .padding(vertical = 32.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Brand header
                    Text(
                        text  = "VolumeStream",
                        style = MaterialTheme.typography.h6.copy(
                            color    = Accent,
                            fontSize = 18.sp,
                        ),
                        modifier = Modifier.padding(start = 8.dp, bottom = 20.dp)
                    )

                    NAV_ITEMS.forEach { item ->
                        val selected = currentRoute == item.route
                        TvSideNavItem(
                            item     = item,
                            selected = selected,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }

                // ── Main content area ────────────────────────────────────────
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    navHost()
                }
            }
        }
    }
}

@Composable
private fun TvSideNavItem(
    item: TvNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        selected -> SelectedBg
        else     -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = item.icon,
            contentDescription = item.label,
            tint               = if (selected) Accent else Color.White,
            modifier           = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text  = item.label,
            color = if (selected) Accent else Color.White,
            style = MaterialTheme.typography.body1,
        )
    }
}


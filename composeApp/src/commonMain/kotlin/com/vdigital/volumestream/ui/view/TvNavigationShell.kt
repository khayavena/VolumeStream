package com.vdigital.volumestream.ui.view

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// ── Design tokens ────────────────────────────────────────────────────────────
private val SideNavBg       = Color(0xFF0D0D0D)
private val SideNavBgExpand = Color(0xFF141414)
private val Accent          = Color(0xFF00E676)
private val SelectedBg      = Color(0x4400E676)
private val FocusedBg       = Color(0x2200E676)
private val NavRailCollapsed = 72.dp
private val NavRailExpanded  = 220.dp

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
 * DStv / Leanback-style TV navigation shell.
 *
 * The side panel **collapses to a 72dp icon-only rail** when no nav item has
 * focus, and **smoothly expands to 220dp** (icon + label) when any item
 * receives D-pad / remote focus — exactly like the DStv Android TV app.
 *
 * Focus model:
 * - Each [TvSideNavItem] is [focusable] and responds to [onFocusChanged].
 * - When any item gains focus the whole rail expands via [animateDpAsState].
 * - Selecting (OK / Enter) navigates through the nav controller.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun TvNavigationShell() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = koinViewModel()
    val authState by authViewModel.uiState.collectAsState()
    var showPlayer by remember { mutableStateOf(false) }

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

    // Whether any nav-rail item currently holds D-pad focus
    var navHasFocus by remember { mutableStateOf(false) }

    val showSideNav = !showPlayer && currentRoute != null && currentRoute !in NO_SIDE_NAV_ROUTES

    // Rail width animates smoothly: 0dp (hidden) → 72dp (collapsed) → 220dp (expanded).
    // The Column is ALWAYS present in the Row so the content Box is never
    // added/removed from the layout tree — prevents the remeasure jump on back press.
    val railWidth by animateDpAsState(
        targetValue = when {
            !showSideNav -> 0.dp
            navHasFocus  -> NavRailExpanded
            else         -> NavRailCollapsed
        },
        animationSpec = tween(durationMillis = 200),
        label = "railWidth"
    )
    val railBg by animateColorAsState(
        targetValue = if (navHasFocus) SideNavBgExpand else SideNavBg,
        animationSpec = tween(durationMillis = 200),
        label = "railBg"
    )

    val onPlay: () -> Unit = { showPlayer = true }
    val onBackFromPlayer: () -> Unit = { showPlayer = false }

    val navHost: @Composable () -> Unit = {
        NavHost(
            navController    = navController,
            startDestination = Screen.Splash.route,
            modifier         = Modifier.fillMaxSize()
        ) {
            composable(Screen.Splash.route)    { SplashScreen(navController = navController) }
            composable(Screen.Login.route)     { LoginScreen(navController = navController) }
            composable(Screen.Register.route)  { RegisterScreen(navController = navController) }
            composable(Screen.Home.route)      { HomeScreen(navController = navController, isTvLayout = true, onPlay = onPlay) }
            composable(Screen.Search.route)    {
                SearchScreen(
                    navController = navController,
                    isTvLayout = true,
                    onPlay = onPlay,
                )
            }
            composable(Screen.Downloads.route) { DownloadsScreen(navController = navController) }
            composable(Screen.Profile.route)   { ProfileScreen(navController = navController) }
            composable(Screen.Settings.route)  { SettingsScreen() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

                    // ── Side rail ─────────────────────────────────────────────────
                    // ALWAYS in the Row — never conditionally added/removed.
                    // clipToBounds() hides all content when width is animating toward 0dp.
                    // weight(1f) on the content Box is therefore stable and never
                    // remeasured with different parent constraints on back press.
            Column(
                modifier = Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(railBg, railBg.copy(alpha = 0.95f))
                        )
                    )
                    .padding(top = 40.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Only render content when the rail is wide enough to show it
                if (railWidth >= NavRailCollapsed) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (navHasFocus) {
                            Text(
                                text       = "VolumeStream",
                                color      = Accent,
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Accent)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    NAV_ITEMS.forEach { item ->
                        val selected = currentRoute == item.route
                        TvSideNavItem(
                            item     = item,
                            selected = selected,
                            expanded = navHasFocus,
                            onFocus  = { navHasFocus = true },
                            onBlur   = { navHasFocus = false },
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
            }

            // ── Main content ──────────────────────────────────────────────
            // weight(1f) only — fillMaxWidth() conflicts with weight in a Row.
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                navHost()
            }
        }

        Crossfade(targetState = showPlayer, label = "tvPlayerCrossfade") { isPlayerVisible ->
            if (isPlayerVisible) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    PlaybackView(onBack = onBackFromPlayer)
                }
            }
        }
    }
}

@Composable
private fun TvSideNavItem(
    item: TvNavItem,
    selected: Boolean,
    expanded: Boolean,
    onFocus: () -> Unit,
    onBlur: () -> Unit,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = when {
        selected  -> SelectedBg
        isFocused -> FocusedBg
        else      -> Color.Transparent
    }
    val iconTint = when {
        selected  -> Accent
        isFocused -> Color.White
        else      -> Color(0xFFAAAAAA)
    }
    val bgAnim by animateColorAsState(
        targetValue = bg,
        animationSpec = tween(150),
        label = "itemBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgAnim)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocus() else onBlur()
            }
            .focusable()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Spacer(Modifier.width(14.dp))
        Icon(
            imageVector        = item.icon,
            contentDescription = item.label,
            tint               = iconTint,
            modifier           = Modifier.size(24.dp)
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                text  = item.label,
                color = if (selected) Accent else Color.White,
                style = MaterialTheme.typography.body1.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize   = 15.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                        .background(Accent)
                )
            }
        }
    }
}

package com.vdigital.volumestream.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vdigital.volumestream.navigation.Screen
import com.vdigital.volumestream.ui.viewmodel.AuthUiState
import com.vdigital.volumestream.ui.viewmodel.AuthViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

/** Routes that should never show the bottom navigation bar. */
private val NO_BOTTOM_NAV_ROUTES = setOf(
    Screen.Play.route,
    Screen.Splash.route,
    Screen.Login.route,
    Screen.Register.route,
)

@OptIn(KoinExperimentalAPI::class)
@Composable
fun MainNavigationControllerView() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = koinViewModel()
    val authState by authViewModel.uiState.collectAsState()

    // When the server revokes the session (401 TOKEN_INVALID), clear the entire
    // back-stack and send the user back to the login screen immediately.
    // This prevents the retry storm where multiple in-flight requests all bounce
    // with 401 and the app just keeps retrying with the dead token.
    LaunchedEffect(authState) {
        if (authState is AuthUiState.SessionRevoked) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
                launchSingleTop = true
            }
            authViewModel.resetState()
        }
    }

    Scaffold(
        modifier = Modifier.background(Color.Black),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute !in NO_BOTTOM_NAV_ROUTES) {
                BottomNavigationBar(navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Splash.route)    { SplashScreen(navController = navController) }
            composable(Screen.Login.route)     { LoginScreen(navController = navController) }
            composable(Screen.Register.route)  { RegisterScreen(navController = navController) }
            composable(Screen.Home.route)      { HomeScreen(navController = navController) }
            composable(Screen.Search.route)    { SearchScreen(navController = navController) }
            composable(Screen.Downloads.route) { DownloadsScreen(navController = navController) }
            composable(Screen.Profile.route)   { ProfileScreen(navController = navController) }
            composable(Screen.Settings.route)  { SettingsScreen() }
            composable(Screen.Play.route) { backStackEntry ->
                PlaybackView(
                    playbackInstanceKey = "play:${backStackEntry.hashCode()}",
                    isTvLayout = false,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem("Home",      Screen.Home.route,      Icons.Default.Home),
        BottomNavItem("Search",    Screen.Search.route,    Icons.Default.Search),
        BottomNavItem("Downloads", Screen.Downloads.route, Icons.AutoMirrored.Filled.List),
        BottomNavItem("Profile",   Screen.Profile.route,   Icons.Default.Person),
        BottomNavItem("Settings",  Screen.Settings.route,  Icons.Default.Settings),
    )
    BottomNavigation(modifier = Modifier.background(color = Color.Black)) {
        val currentRoute = currentRoute(navController)
        items.forEach { item ->
            BottomNavigationItem(
                modifier  = Modifier.background(color = Color.Black),
                icon      = { Icon(item.icon, contentDescription = item.name) },
                label     = { Text(item.name) },
                selected  = currentRoute == item.route,
                onClick   = {
                    navController.navigate(item.route) {
                        popUpTo(Screen.Home.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                }
            )
        }
    }
}

@Composable
fun currentRoute(navController: NavController): String? =
    navController.currentBackStackEntryAsState().value?.destination?.route

data class BottomNavItem(val name: String, val route: String, val icon: ImageVector)

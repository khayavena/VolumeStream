package com.vdigital.volumestream.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vdigital.volumestream.navigation.Screen
import com.vdigital.volumestream.ui.viewmodel.ProfileViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

private val GreenAccent = Color(0xFF00E676)

@OptIn(KoinExperimentalAPI::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val signedOut by viewModel.signedOut.collectAsState()

    // When sign-out completes, clear the entire back stack and go to Login.
    LaunchedEffect(signedOut) {
        if (signedOut) {
            navController.navigate(Screen.Login.route) {
                navController.graph.startDestinationRoute?.let { 
                    popUpTo(it) { inclusive = true }
                }
            }
        }
    }

    // Derive initials from the email address (e.g. "alice@example.com" → "AL")
    val email    = viewModel.userEmail ?: "user@volumestream.com"
    val initials = email.take(2).uppercase()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color(0xFF1E1E1E), CircleShape)
                .border(2.dp, GreenAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("VolumeStream User", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(email, color = Color.Gray, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(24.dp))

        // Subscription card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Subscription", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Premium Plan", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Box(
                        modifier = Modifier
                            .background(GreenAccent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("ACTIVE", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Renews March 26, 2026", color = Color.Gray, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Watch Stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Watch Stats", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WatchStatItem(value = "24", label = "Videos Watched")
                    WatchStatItem(value = "18h", label = "Watch Time")
                    WatchStatItem(value = "6",   label = "Downloads")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account actions
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF1E1E1E)
        ) {
            Column {
                ProfileMenuItem(
                    label   = "Watch History",
                    onClick = { navController.navigate(Screen.Downloads.route) }
                )
                Divider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
                ProfileMenuItem(
                    label   = "My Downloads",
                    onClick = { navController.navigate(Screen.Downloads.route) }
                )
                Divider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
                ProfileMenuItem(label = "Manage Account")
                Divider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
                ProfileMenuItem(
                    label      = "Sign Out",
                    labelColor = Color(0xFFE50914),
                    onClick    = { viewModel.signOut() }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun WatchStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = GreenAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun ProfileMenuItem(
    label: String,
    labelColor: Color = Color.White,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = labelColor, fontSize = 15.sp)
        Text("›", color = GreenAccent, fontSize = 20.sp)
    }
}

package com.vdigital.volumestream.ui.view

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vdigital.volumestream.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GreenAccent   = Color(0xFF00E676)
private val GreenDark     = Color(0xFF00C853)
private val GreenGlow     = Color(0x3300E676)
private val GreenGlowFade = Color(0x0000E676)

@Composable
fun SplashScreen(navController: NavHostController) {

    // ── Animatables ──────────────────────────────────────────────────────────
    val circleScale   = remember { Animatable(0f) }
    val circleAlpha   = remember { Animatable(0f) }
    val logoAlpha     = remember { Animatable(0f) }
    val titleAlpha    = remember { Animatable(0f) }
    val titleOffsetY  = remember { Animatable(30f) }
    val taglineAlpha  = remember { Animatable(0f) }

    // Pulsing outer ring
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.22f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // ── Animation sequence ───────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        // 1. Circle springs in
        launch {
            circleAlpha.animateTo(1f, tween(300))
            circleScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
        delay(350)

        // 2. Logo initials fade in
        logoAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        delay(100)

        // 3. Title slides up and fades in
        launch { titleOffsetY.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
        launch { titleAlpha.animateTo(1f, tween(500)) }
        delay(300)

        // 4. Tagline fades in
        taglineAlpha.animateTo(1f, tween(500))
        delay(1000)

        // 5. Navigate to Home
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(contentAlignment = Alignment.Center) {

                // Outer glow ring (pulsing)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(circleScale.value * pulseScale)
                        .alpha(circleAlpha.value * pulseAlpha)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(GreenGlow, GreenGlowFade)
                            ),
                            shape = CircleShape
                        )
                )

                // Middle ring border
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .scale(circleScale.value)
                        .alpha(circleAlpha.value * 0.35f)
                        .background(Color.Transparent, CircleShape)
                        .then(
                            Modifier.background(
                                brush = Brush.radialGradient(
                                    colors = listOf(GreenAccent.copy(alpha = 0.18f), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                        )
                )

                // Main filled circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(circleScale.value)
                        .alpha(circleAlpha.value)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(GreenAccent, GreenDark)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text     = "VS",
                        color    = Color.Black,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.alpha(logoAlpha.value)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App name
            Text(
                text       = "VolumeStream",
                color      = Color.White,
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .alpha(titleAlpha.value)
                    .offset(y = titleOffsetY.value.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text       = "Stream without limits",
                color      = GreenAccent,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.alpha(taglineAlpha.value)
            )
        }
    }
}

package com.vdigital.volumestream.platform.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vditital.data.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVKit.AVPlayerViewController
import platform.UIKit.UIAction
import platform.UIKit.UIColor
import platform.UIKit.UIControl
import platform.UIKit.UIControlEventTouchDown
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

/**
 * iOS video player view using AVPlayerViewController embedded via UIKitViewController.
 *
 * Why AVPlayerViewController + UIKitViewController (not AVPlayerLayer + UIKitView):
 *   • UIKitViewController automatically calls addChild() / didMove(toParent:) so
 *     the VC receives proper lifecycle events and AVFoundation renders every frame.
 *   • AVPlayerLayer without proper VC containment stalls after the first decoded
 *     frame because CALayer display callbacks are never scheduled by UIKit.
 *   • showsPlaybackControls = false keeps the Apple transport bar hidden so the
 *     Compose controls overlay is the only UI layer the user sees.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@Composable
actual fun PlatformMediaPlayerView(
    modifier: Modifier,
    playbackStateController: PlaybackStateController,
    onTap: () -> Unit,
    isZoomed: Boolean
) {
    val targetVideoGravity = if (isZoomed)
        AVLayerVideoGravityResizeAspectFill
    else
        AVLayerVideoGravityResizeAspect

    AppLogger.d(
        "Diag.Layer",
        "compose controller=${playbackStateController.hashCode()} " +
        "player=${playbackStateController.avPlayer.hashCode()} zoomed=$isZoomed"
    )

    // Stable reference so the UIAction closure always invokes the latest lambda.
    val onTapRef = remember { mutableStateOf(onTap) }
    onTapRef.value = onTap

    // AVPlayerViewController — created once per controller instance.
    val playerVC = remember(playbackStateController) {
        AVPlayerViewController().also { vc ->
            vc.player = playbackStateController.avPlayer
            vc.showsPlaybackControls = false
            vc.videoGravity = targetVideoGravity
            AppLogger.d(
                "Diag.Layer",
                "AVPlayerViewController created " +
                "controller=${playbackStateController.hashCode()} " +
                "player=${playbackStateController.avPlayer.hashCode()}"
            )
        }
    }

    // Transparent UIControl overlay placed on top of the player view to forward
    // taps to Compose without blocking the video.  Created once per controller.
    val tapOverlay = remember(playbackStateController) {
        UIControl().apply {
            backgroundColor = UIColor.clearColor
            // Fill the player view automatically as it resizes.
            autoresizingMask =
                UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            addAction(
                UIAction.actionWithHandler { _ -> onTapRef.value() },
                UIControlEventTouchDown
            )
        }
    }

    UIKitViewController(
        factory = { playerVC },
        update = {
            // Use the captured playerVC reference directly to avoid UIViewController cast.
            // Re-bind player if controller changed (e.g. ViewModel recreated).
            if (playerVC.player !== playbackStateController.avPlayer) {
                playerVC.player = playbackStateController.avPlayer
                AppLogger.d(
                    "Diag.Layer",
                    "AVPlayerViewController player rebound " +
                    "controller=${playbackStateController.hashCode()}"
                )
            }
            // Keep gravity in sync with the zoom toggle.
            if (playerVC.videoGravity != targetVideoGravity) {
                playerVC.videoGravity = targetVideoGravity
            }
            // Attach tap overlay to the player view exactly once.
            val vcView = playerVC.view ?: return@UIKitViewController
            if (tapOverlay.superview !== vcView) {
                tapOverlay.removeFromSuperview()
                tapOverlay.setFrame(vcView.bounds)
                vcView.addSubview(tapOverlay)
            }
        },
        onRelease = {
            AppLogger.i(
                "Diag.Layer",
                "AVPlayerViewController onRelease controller=${playbackStateController.hashCode()}"
            )
            tapOverlay.removeFromSuperview()
            playerVC.player = null
        },
        modifier = modifier
    )
}

package com.vdigital.volumestream.platform.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRect
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIAction
import platform.UIKit.UIColor
import platform.UIKit.UIControl
import platform.UIKit.UIControlEventTouchDown
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMediaPlayerView(
    modifier: Modifier,
    playbackStateController: PlaybackStateController,
    onTap: () -> Unit
) {
    val playbackLayer = remember { AVPlayerLayer() }
    val avPlayerViewController = remember { AVPlayerViewController() }
    avPlayerViewController.player = remember { playbackStateController.avPlayer }
    avPlayerViewController.showsPlaybackControls = false
    playbackLayer.player = avPlayerViewController.player

    // Release all player resources when this composable leaves the composition.
    DisposableEffect(Unit) {
        onDispose {
            avPlayerViewController.player?.let { (it as AVPlayer).pause() }
            avPlayerViewController.player = null
            playbackStateController.release()
        }
    }

    // Stable ref so the tap action always calls the latest onTap lambda
    // even after recompositions (factory only runs once).
    val onTapRef = remember { mutableStateOf(onTap) }
    onTapRef.value = onTap

    // Transparent UIControl on top of the video - intercepts all taps and
    // forwards them to Compose so the auto-hide timer works correctly on iOS.
    val overlay = remember {
        UIControl().apply { backgroundColor = UIColor.clearColor }
    }
    remember(overlay) {
        overlay.addAction(
            UIAction.actionWithHandler { _ -> onTapRef.value() },
            UIControlEventTouchDown
        )
    }

    UIKitView(
        factory = {
            val playerContainer = UIView()
            playerContainer.addSubview(avPlayerViewController.view)
            playerContainer.addSubview(overlay)   // overlay sits on top of video
            playerContainer
        },
        onResize = { view: UIView, rect: CValue<CGRect> ->
            CATransaction.begin()
            CATransaction.setValue(true, kCATransactionDisableActions)
            view.layer.setFrame(rect)
            playbackLayer.setFrame(rect)
            avPlayerViewController.view.layer.frame = rect
            overlay.layer.setFrame(rect)   // keep overlay filling the container
            CATransaction.commit()
        },
        update = {
            // Do NOT call play() here — this block runs on every recomposition,
            // which would restart playback immediately after every pause.
            // Playback is started explicitly via PlaybackStateController.play().
        }, onRelease = {
            avPlayerViewController.player?.let { (it as AVPlayer).pause() }
            avPlayerViewController.player = null
        },
        modifier = modifier
    )
}
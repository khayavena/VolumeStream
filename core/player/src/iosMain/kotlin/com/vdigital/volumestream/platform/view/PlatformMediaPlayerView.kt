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
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
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
    onTap: () -> Unit,
    isZoomed: Boolean
) {
    val playbackLayer = remember { AVPlayerLayer() }
    val avPlayerViewController = remember { AVPlayerViewController() }
    avPlayerViewController.player = remember { playbackStateController.avPlayer }
    avPlayerViewController.showsPlaybackControls = false
    avPlayerViewController.videoGravity = if (isZoomed)
        AVLayerVideoGravityResizeAspectFill
    else
        platform.AVFoundation.AVLayerVideoGravityResizeAspect
    playbackLayer.player = avPlayerViewController.player

    DisposableEffect(Unit) {
        onDispose {
            avPlayerViewController.player?.let { (it as AVPlayer).pause() }
            avPlayerViewController.player = null
            playbackStateController.release()
        }
    }

    val onTapRef = remember { mutableStateOf(onTap) }
    onTapRef.value = onTap

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
            val container = UIView()
            container.addSubview(avPlayerViewController.view)
            container.addSubview(overlay)
            container
        },
        onResize = { view: UIView, rect: CValue<CGRect> ->
            CATransaction.begin()
            CATransaction.setValue(true, kCATransactionDisableActions)
            view.layer.setFrame(rect)
            playbackLayer.setFrame(rect)
            avPlayerViewController.view.layer.frame = rect
            overlay.layer.setFrame(rect)
            CATransaction.commit()
        },
        update = {},
        onRelease = {
            avPlayerViewController.player?.let { (it as AVPlayer).pause() }
            avPlayerViewController.player = null
        },
        modifier = modifier
    )
}

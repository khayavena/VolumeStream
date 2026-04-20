package com.vdigital.volumestream

import androidx.compose.ui.window.ComposeUIViewController
import com.vditital.data.util.AppLogger

/**
 * tvOS entry point bridged from Swift via [ComposeUIViewController].
 *
 * The resulting [UIViewController] is returned to the tvOS [ContentView] SwiftUI
 * wrapper in `tvOSApp/`.  It renders the TV-specific [VolumeStreamTvApp]
 * composable which uses a persistent side [TvNavigationShell] instead of the
 * phone's bottom navigation bar.
 */
fun TvMainViewController() = ComposeUIViewController {
    AppLogger.init()
    VolumeStreamTvApp()
}


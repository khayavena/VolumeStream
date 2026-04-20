package com.vdigital.volumestream.platform.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vdigital.volumestream.VolumeStreamTvApp

/**
 * Entry-point for Android TV / Google TV.
 *
 * Registered with the LEANBACK_LAUNCHER category so it appears in the TV
 * home-screen grid.  The regular [MainActivity] (LAUNCHER) is unchanged —
 * phones and tablets continue to use it.
 *
 * The TV UI is provided by [VolumeStreamTvApp] which swaps the phone's
 * [BottomNavigation] for a D-pad-friendly side [NavigationDrawer] from
 * `androidx.tv:tv-material`.
 */
class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolumeStreamTvApp()
        }
    }
}


package com.vdigital.volumestream.platform.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vdigital.volumestream.VolumeStreamApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VolumeStreamApp()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    VolumeStreamApp()
}
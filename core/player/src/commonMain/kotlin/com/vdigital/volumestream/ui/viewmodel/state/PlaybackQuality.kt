package com.vdigital.volumestream.ui.viewmodel.state

sealed class PlaybackQuality(val label: String, val maxHeight: Int, val maxBitrate: Long) {
    data object Auto   : PlaybackQuality("Auto",  Int.MAX_VALUE, Long.MAX_VALUE)
    data object Q360p  : PlaybackQuality("360p",   360,     800_000L)
    data object Q480p  : PlaybackQuality("480p",   480,   1_500_000L)
    data object Q720p  : PlaybackQuality("720p",   720,   3_000_000L)
    data object Q1080p : PlaybackQuality("1080p", 1080,   8_000_000L)
    data object Q1440p : PlaybackQuality("1440p", 1440,  16_000_000L)
    data object Q4K    : PlaybackQuality("4K",    2160,  40_000_000L)

    companion object {
        val all: List<PlaybackQuality> by lazy { listOf(Auto, Q360p, Q480p, Q720p, Q1080p, Q1440p, Q4K) }
    }
}

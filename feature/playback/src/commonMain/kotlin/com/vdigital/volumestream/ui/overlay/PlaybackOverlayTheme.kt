package com.vdigital.volumestream.ui.overlay

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Shared design tokens for the player overlay UI.
 *
 * `internal` visibility — accessible anywhere within the `:feature:playback` module
 * (both `ui.overlay` and `ui.view` packages) but not exported to other modules.
 */
internal val GreenAccent      = Color(0xFF00E676)
internal val ControlsBarBg    = Color(0x8C000000)
internal val TopScrimBrush    = Brush.verticalGradient(listOf(Color(0x8F000000), Color.Transparent))
internal val BottomScrimBrush = Brush.verticalGradient(listOf(Color.Transparent, Color(0x96000000)))
internal val TvChipBg         = Color(0xB3000000)
internal val TvChipFocusedBg  = Color(0x6600E676)
/** Slightly translucent dark background for TV track cards (carousel). */
internal val TvCardBg         = Color(0xCC111111)
/** Muted label colour used for secondary text in the TV carousel. */
internal val TvCarouselLabel  = Color(0xFFAAAAAA)


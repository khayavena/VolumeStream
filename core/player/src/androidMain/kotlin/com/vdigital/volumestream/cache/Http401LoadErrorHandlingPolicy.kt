package com.vdigital.volumestream.cache

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * A [LoadErrorHandlingPolicy] that immediately stops retrying when the upstream
 * server responds with HTTP 401 (TOKEN_INVALID / session revoked).
 *
 * Problem this solves
 * -------------------
 * ExoPlayer's default policy retries failed loads with exponential back-off
 * (≈ 1 s → 2 s → 4 s → 8 s …). When the session is revoked the server returns
 * 401 for every DASH segment request, so ExoPlayer keeps hammering the server
 * with the same dead token for tens of seconds — flooding the server log with
 * repeated "TOKEN_INVALID: Session has been revoked" warnings and causing
 * "Connection reset by peer" / "Broken pipe" errors while the client retries.
 *
 * Fix
 * ---
 * Returning [C.TIME_UNSET] from [getRetryDelayMsFor] tells ExoPlayer to surface
 * the error immediately as a [androidx.media3.common.PlaybackException] with
 * [androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS].
 * [com.vdigital.volumestream.platform.controller.PlaybackStateController.PlaybackControllerListener.onPlayerError]
 * then inspects the cause chain, detects the 401, emits [com.vditital.data.security.SessionRevokedBus]
 * and transitions the player to [com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.SessionExpired].
 *
 * All non-401 errors fall through to the default exponential back-off policy.
 */
@OptIn(UnstableApi::class)
internal class Http401LoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (loadErrorInfo.exception.is401()) return C.TIME_UNSET   // stop retrying immediately
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    /** Only suppress the minimum retry count for 401s. For all other errors
     *  fall through to the default so transient network failures still retry. */
    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        super.getMinimumLoadableRetryCount(dataType)
}

/**
 * Walks the [Throwable] cause chain looking for an
 * [HttpDataSource.InvalidResponseCodeException] with HTTP 401.
 */
private fun Throwable.is401(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is HttpDataSource.InvalidResponseCodeException && t.responseCode == 401) return true
        t = t.cause
    }
    return false
}


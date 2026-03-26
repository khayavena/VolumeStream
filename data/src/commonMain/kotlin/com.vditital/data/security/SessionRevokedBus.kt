package com.vditital.data.security

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide bus that emits once whenever the server returns 401 TOKEN_INVALID
 * (i.e. the session has been revoked server-side).
 *
 * Any ViewModel or screen that has an authenticated context should collect this
 * flow and redirect the user to the login screen.
 */
object SessionRevokedBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Observed by AuthViewModel / any screen that needs to react to forced logout. */
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    /** Called by the Ktor HttpSend interceptor when a 401 is received. */
    fun emit() { _events.tryEmit(Unit) }
}


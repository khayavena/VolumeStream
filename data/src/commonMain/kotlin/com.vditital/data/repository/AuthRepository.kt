package com.vditital.data.repository

import com.vditital.data.repository.state.ResultState

interface AuthRepository {
    suspend fun login(email: String, password: String): ResultState<Unit>
    suspend fun register(email: String, password: String): ResultState<Unit>
    fun isLoggedIn(): Boolean
    fun getCurrentUserEmail(): String?
    fun logout()
    /** Returns a valid JWT, refreshing it if expired. Returns null when not logged in. */
    suspend fun ensureValidJwt(): String?
}


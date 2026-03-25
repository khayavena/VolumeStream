package com.vditital.data.datasource

import com.vditital.data.model.AuthResponse
import com.vditital.data.model.RefreshTokenResponse

interface AuthDataSource {
    suspend fun login(email: String, password: String): AuthResponse
    suspend fun register(email: String, password: String): AuthResponse
    suspend fun refreshToken(jwt: String): RefreshTokenResponse
}


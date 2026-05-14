package com.vditital.data.repository

import com.vditital.data.model.UserProfile
import com.vditital.data.repository.state.ResultState

interface ProfileRepository {
    suspend fun loadMyProfile(): ResultState<UserProfile>
    suspend fun saveMyProfile(fullName: String, phone: String, address: String): ResultState<UserProfile>
    suspend fun upgradeCurrentUserToAdmin(): ResultState<String>
}


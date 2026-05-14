package com.vditital.data.datasource

import com.vditital.data.model.CreateProfileRequest
import com.vditital.data.model.UserProfile

interface ProfileDataSource {
    suspend fun createProfile(jwt: String, request: CreateProfileRequest): UserProfile
    suspend fun viewProfileById(jwt: String, profileId: String): UserProfile
    suspend fun viewProfileByUserId(jwt: String, userId: String): UserProfile
    suspend fun updateProfile(jwt: String, profile: UserProfile): UserProfile
    suspend fun upgradeAdmin(jwt: String): String
}


package com.vditital.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateProfileRequest(
    @SerialName("fullName") val fullName: String,
    @SerialName("phone") val phone: String,
    @SerialName("address") val address: String,
)

@Serializable
data class UserProfile(
    @SerialName("id") val id: String,
    @SerialName("userId") val userId: String,
    @SerialName("fullName") val fullName: String,
    @SerialName("phone") val phone: String,
    @SerialName("address") val address: String,
)


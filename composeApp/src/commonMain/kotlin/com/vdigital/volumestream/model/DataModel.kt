package com.vdigital.volumestream.model

import kotlinx.serialization.Serializable

@Serializable
data class DataModel(
    val userId: Int = 0,
    val id: Int = 0,
    val title: String = "",
    val completed: Boolean = false
)
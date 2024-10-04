package com.vditital.data.datasource

import io.ktor.client.HttpClient


expect class RemoteApiClientFactory {
    fun create(): HttpClient
}

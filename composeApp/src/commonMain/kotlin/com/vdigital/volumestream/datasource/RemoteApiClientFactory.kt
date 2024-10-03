package com.vdigital.volumestream.datasource

import io.ktor.client.HttpClient


expect class RemoteApiClientFactory {
    fun create(): HttpClient
}

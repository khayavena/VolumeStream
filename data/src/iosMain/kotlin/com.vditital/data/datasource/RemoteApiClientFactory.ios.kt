package com.vditital.data.datasource

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

// NOTE: ktor-client-logging requires ktor-client-observer which is not available
// in the Kotlin/Native (iOS) binary strip. Logging is intentionally omitted here
// to avoid the IrLinkageError crash at startup on iOS.
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class RemoteApiClientFactory {
    @OptIn(ExperimentalSerializationApi::class)
    actual fun create(): HttpClient {
        return HttpClient(Darwin) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    explicitNulls = false
                })
            }
        }
    }
}
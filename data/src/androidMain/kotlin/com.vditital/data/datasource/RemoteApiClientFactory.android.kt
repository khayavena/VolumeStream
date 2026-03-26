package com.vditital.data.datasource

import com.vditital.data.security.SessionRevokedBus
import com.vditital.data.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class RemoteApiClientFactory {

    @OptIn(ExperimentalSerializationApi::class)
    actual fun create(): HttpClient {
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    explicitNulls = false
                })
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) { AppLogger.d("Ktor", message) }
                }
                level = LogLevel.HEADERS
            }
        }

        // On 401: signal SessionRevokedBus so AuthViewModel clears the JWT and
        // redirects to login — stops the retry storm of concurrent requests all
        // hammering the server with the same revoked token.
        client.plugin(HttpSend).intercept { request ->
            val call = execute(request)
            if (call.response.status == HttpStatusCode.Unauthorized) {
                AppLogger.w("Ktor", "401 Unauthorized — signalling session revoked")
                SessionRevokedBus.emit()
            }
            call
        }

        return client
    }
}
package com.vditital.data.datasource

import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.model.DataModel
import com.vditital.data.model.MediaFeedResponse
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.model.toPlaybackMediaItem
import com.vditital.data.security.TokenStore
import com.vditital.data.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import kotlinx.serialization.json.Json

class RemotePlaybackDataSourceImpl(
    private val httpClient: HttpClient,
    private val apiHost: String,
    private val tokenStore: TokenStore,
    private val config: StreamVaultConfig = StreamVaultConfig()
) : RemotePlaybackDataSource {

    private val protocol  get() = if (config.useHttps) URLProtocol.HTTPS else URLProtocol.HTTP
    private val port      get() = config.apiPort
    private val feedPath  get() = "${config.apiBasePath}/media/feed"

    private val json = Json {
        isLenient         = true
        ignoreUnknownKeys = true
        explicitNulls     = false
    }

    override suspend fun fetchFeed(): Map<String, MutableList<PlaybackMediaItem>> {
        val jwt = tokenStore.getJwt()
        AppLogger.d("DataSource", "fetchFeed from $apiHost:$port/$feedPath  jwt=${jwt != null}")

        val response = httpClient.get {
            url {
                protocol = this@RemotePlaybackDataSourceImpl.protocol
                host     = apiHost
                port     = this@RemotePlaybackDataSourceImpl.port
                appendPathSegments(feedPath.split("/"))
            }
            if (jwt != null) bearerAuth(jwt)
            headers.append(HttpHeaders.Accept, "application/json")
        }

        val rawBody = response.bodyAsText()
        AppLogger.d("DataSource", "HTTP ${response.status}  body=${rawBody.take(200)}")

        val feedResponse = json.decodeFromString<MediaFeedResponse>(rawBody)
        AppLogger.d("DataSource", "Parsed ${feedResponse.categories.size} categories")

        return feedResponse.categories.mapValues { (_, items) ->
            items.map { it.toPlaybackMediaItem(apiHost, config) }.toMutableList()
        }
    }

    override suspend fun fetchData(): MutableList<PlaybackMediaItem> =
        fetchFeed().values.flatten().toMutableList()

    override suspend fun fetchDataModel(): DataModel = DataModel()
}

package com.vditital.data.datasource

import com.vditital.data.model.DataModel
import com.vditital.data.model.MediaFeedResponse
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.model.toPlaybackMediaItem
import com.vditital.data.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.serialization.json.Json

private const val API_HOST       = "localhost"
private const val API_PORT       = 8080
private const val USE_HTTPS      = false
private const val DEFAULT_PAGE   = 1
private const val DEFAULT_LIMIT  = 20

class RemotePlaybackDataSourceImpl(private val httpClient: HttpClient) : RemotePlaybackDataSource {

    // Base endpoint: http://localhost:8080/api/v1/media?page=1&pageSize=20
    private val feedPath = "api/v1/media"

    // Lenient Json matching the iOS/Android ContentNegotiation config
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun fetchFeed(): Map<String, MutableList<PlaybackMediaItem>> {
        AppLogger.d("DataSource", "Requesting feed page=$DEFAULT_PAGE pageSize=$DEFAULT_LIMIT from $API_HOST:$API_PORT/$feedPath")

        val httpResponse = httpClient.get {
            url {
                protocol = if (USE_HTTPS) URLProtocol.HTTPS else URLProtocol.HTTP
                host = API_HOST
                port = API_PORT
                path(feedPath)
                parameters.append("page", DEFAULT_PAGE.toString())
                parameters.append("pageSize", DEFAULT_LIMIT.toString())
            }
            headers {
                append(HttpHeaders.Accept, "application/json")
            }
        }

        val rawBody = httpResponse.bodyAsText()
        AppLogger.d("DataSource", "HTTP status: ${httpResponse.status}")
        AppLogger.d("DataSource", "Raw response body: $rawBody")

        val response = json.decodeFromString<MediaFeedResponse>(rawBody)
        AppLogger.d("DataSource", "Parsed ${response.items.size} items across categories")

        return response.items
            .groupBy { it.categoryName.ifBlank { "Other" } }
            .mapValues { (_, items) -> items.map { it.toPlaybackMediaItem() }.toMutableList() }
    }

    override suspend fun fetchData(): MutableList<PlaybackMediaItem> {
        return fetchFeed().values.flatten().toMutableList()
    }

    override suspend fun fetchDataModel(): DataModel {
        return try {
            val result: DataModel = httpClient.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "jsonplaceholder.typicode.com"
                    path("todos/1")
                    parameters.append("id", "123")
                }
                headers {
                    append(HttpHeaders.Authorization, "Bearer token")
                    append(HttpHeaders.ContentType, "application/json")
                }
            }.body()
            AppLogger.d("DataSource", "fetchDataModel success: $result")
            result
        } catch (e: Exception) {
            AppLogger.e("DataSource", "fetchDataModel failed", e)
            DataModel()
        }
    }
}



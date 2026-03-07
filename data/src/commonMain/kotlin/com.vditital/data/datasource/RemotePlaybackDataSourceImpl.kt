package com.vditital.data.datasource

import com.vditital.data.model.DataModel
import com.vditital.data.model.MediaFeedResponse
import com.vditital.data.model.MediaItemDto
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.model.toPlaybackMediaItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.path

// ---------------------------------------------------------------------------
// Base URL configuration
// Change API_HOST / API_PORT / API_HTTPS to point at your backend.
// When USE_MOCK_DATA = true the app shows local sample content so the UI
// can be developed without a running server.
// ---------------------------------------------------------------------------
private const val API_HOST    = "localhost"
private const val API_PORT    = 8080
private const val USE_HTTPS   = false
private const val USE_MOCK_DATA = true   // ← flip to false when backend is live

// ---------------------------------------------------------------------------
// Sample mock feed — used when USE_MOCK_DATA = true
// ---------------------------------------------------------------------------
private val MOCK_FEED: Map<String, List<MediaItemDto>> = mapOf(
    "Trending" to listOf(
        MediaItemDto(
            id = "mock-1",
            title = "Big Buck Bunny",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            downloadUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            artworkUrl = "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg",
            description = "A large and lovable rabbit deals with bullying"
        ),
        MediaItemDto(
            id = "mock-2",
            title = "Elephant Dream",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            downloadUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            artworkUrl = "https://orange.blender.org/wp-content/themes/orange/images/media/video/elephants_dream_16x9.jpg",
            description = "The world's first open movie"
        ),
        MediaItemDto(
            id = "mock-3",
            title = "Subaru Outback Ad",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            downloadUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            artworkUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/SubaruOutbackOnStreetAndDirt.jpg",
            description = "Subaru Outback"
        )
    ),
    "Featured" to listOf(
        MediaItemDto(
            id = "mock-4",
            title = "For Bigger Blazes",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            downloadUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            artworkUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
            description = "For Bigger Blazes"
        ),
        MediaItemDto(
            id = "mock-5",
            title = "For Bigger Escapes",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            downloadUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            artworkUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerEscapes.jpg",
            description = "For Bigger Escapes"
        )
    ),
    "Popular" to listOf(
        MediaItemDto(
            id = "mock-6",
            title = "Volkswagen GTI Review",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4",
            downloadUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4",
            artworkUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/VolkswagenGTIReview.jpg",
            description = "VW GTI Review"
        ),
        MediaItemDto(
            id = "mock-7",
            title = "We Are Going On Bullrun",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            downloadUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            artworkUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/WeAreGoingOnBullrun.jpg",
            description = "We Are Going On Bullrun"
        )
    )
)

class RemotePlaybackDataSourceImpl(private val httpClient: HttpClient) : RemotePlaybackDataSource {
    private val endPoint = "todos/1"
    private val feedPath = "api/v1/media/feed"

    override suspend fun fetchFeed(): Map<String, MutableList<PlaybackMediaItem>> {
        if (USE_MOCK_DATA) {
            println("[DataSource] USE_MOCK_DATA=true — returning local mock feed")
            return MOCK_FEED.mapValues { (_, items) ->
                items.map { it.toPlaybackMediaItem() }.toMutableList()
            }
        }
        // Let exceptions propagate to the repository layer so that ResultState.Error
        // is returned to the ViewModel instead of silently swallowing failures.
        val response = httpClient.get {
            url {
                protocol = if (USE_HTTPS) URLProtocol.HTTPS else URLProtocol.HTTP
                host = API_HOST
                port = API_PORT
                path(feedPath)
            }
        }.body<MediaFeedResponse>()
        println("[DataSource] Fetched feed: $response")
        return response.categories.mapValues { (_, items) ->
            items.map { it.toPlaybackMediaItem() }.toMutableList()
        }
    }

    override suspend fun fetchData(): MutableList<PlaybackMediaItem> {
        return fetchFeed().values.flatten().toMutableList()
    }

    override suspend fun fetchDataModel(): DataModel {
        return try {
            httpClient.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "jsonplaceholder.typicode.com"
                    path(endPoint)
                    parameters.append("id", "123")
                }
                headers {
                    append(HttpHeaders.Authorization, "Bearer token")
                    append(HttpHeaders.ContentType, "application/json")
                }
            }.body<DataModel>()
        } catch (e: Exception) {
            e.printStackTrace()
            println("[DataSource] fetchDataModel failed: $e")
            DataModel()
        }
    }
}



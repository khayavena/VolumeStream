package com.vditital.data.datasource


import com.vditital.data.model.DataModel
import com.vditital.data.model.PlaybackMediaItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.path
class RemotePlaybackDataSourceImpl(private val httpClient: HttpClient) : RemotePlaybackDataSource {
    private val endPoint = "todos/1"

    override suspend fun fetchData(): MutableList<PlaybackMediaItem> {
        return try {
            mutableListOf(
                PlaybackMediaItem(
                    id = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
                    title = "Big Buck Bunny",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/07/19/E36B_1079965_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e",
                    title = "Elephant Dream",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/06/19/IS20_1070794_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f",
                    title = "For Bigger Blazes",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/09/13/E36B_1080099_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a",
                    title = "For Bigger Escapes",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/09/04/E36B_1078979_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b",
                    title = "Subaru Outback",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/08/30/E36B_1069995_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "f6a7b8c9-d0e1-4f2a-3b4c-5d6e7f8a9b0c",
                    title = "Tears of Steel",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    artworkUrl = "https://cdn.dstv.com/mms.dstv.com/content/images/dstv/boxoffice/untitled%20folder%205/badboysrideordie_pp.jpg"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
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
            print("Failed with exception")
            return DataModel()
        }
    }
}

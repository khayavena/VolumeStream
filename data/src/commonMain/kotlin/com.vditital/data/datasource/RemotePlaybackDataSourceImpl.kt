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
                    id = "1",
                    title = "Big Buck Bunny",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/07/19/E36B_1079965_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "2",
                    title = "Elephant Dream",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/06/19/IS20_1070794_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "3",
                    title = "For Bigger Blazes",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/09/13/E36B_1080099_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "4",
                    title = "For Bigger Escapes",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/09/04/E36B_1078979_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "5",
                    title = "Subaru Outback",
                    isDownloaded = false,
                    streamUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                    artworkUrl = "https://cdn.dstv.com/dstvcms/2024/08/30/E36B_1069995_PP_med.jpg"
                ),
                PlaybackMediaItem(
                    id = "6",
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

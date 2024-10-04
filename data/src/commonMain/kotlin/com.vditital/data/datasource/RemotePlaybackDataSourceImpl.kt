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
        TODO("Not yet implemented")
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

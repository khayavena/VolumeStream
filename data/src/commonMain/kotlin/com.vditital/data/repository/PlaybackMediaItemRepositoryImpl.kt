package com.vditital.data.repository

import com.vditital.data.repository.state.MediaItemDataState
import com.vditital.data.datasource.RemotePlaybackDataSource
import com.vditital.data.model.DataModel
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.state.ResultState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

class PlaybackMediaItemRepositoryImpl(private val dataSource: RemotePlaybackDataSource) :
    PlaybackMediaItemRepository {
    private val remoteDataSourceData = mutableListOf<PlaybackMediaItem>()

    // ── In-memory cache ───────────────────────────────────────────────────────
    private val cacheMutex = Mutex()
    private val cacheTtl   = 5.minutes

    private var cachedItems: MutableList<PlaybackMediaItem>? = null
    private var cachedFeed:  Map<String, MutableList<PlaybackMediaItem>>? = null
    private var cacheStamp:  TimeSource.Monotonic.ValueTimeMark? = null

    private fun isCacheValid(): Boolean {
        val stamp = cacheStamp ?: return false
        return stamp.elapsedNow() < cacheTtl
    }

    /** Invalidates the cache (e.g. after a pull-to-refresh). */
    override suspend fun invalidateCache() = cacheMutex.withLock {
        cachedItems = null
        cachedFeed  = null
        cacheStamp  = null
    }
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun fetchMediaItems(): MediaItemDataState {
        return try {
            MediaItemDataState.Success(dataSource.fetchData())
        } catch (e: Exception) {
            MediaItemDataState.Failure(e)
        }
    }

    override suspend fun getMediaItemsState(): ResultState<MutableList<PlaybackMediaItem>> =
        cacheMutex.withLock {
            cachedItems?.takeIf { isCacheValid() }?.let { return@withLock ResultState.Success(it) }
            return@withLock try {
                val data = dataSource.fetchData()
                cachedItems = data
                cacheStamp  = TimeSource.Monotonic.markNow()
                ResultState.Success(data)
            } catch (e: Exception) {
                ResultState.Error(e)
            }
        }

    override suspend fun getMediaItemsByCategoryState(): ResultState<Map<String, MutableList<PlaybackMediaItem>>> =
        cacheMutex.withLock {
            cachedFeed?.takeIf { isCacheValid() }?.let { return@withLock ResultState.Success(it) }
            return@withLock try {
                val data = dataSource.fetchFeed()
                cachedFeed = data
                // Derive flat list from feed so both caches are filled in one call
                cachedItems = data.values.flatten().toMutableList()
                cacheStamp  = TimeSource.Monotonic.markNow()
                ResultState.Success(data)
            } catch (e: Exception) {
                ResultState.Error(e)
            }
        }

    override suspend fun fetchDataModel(): DataModel {
        return dataSource.fetchDataModel()
    }

    override suspend fun fetchAndSaveMediaItems() {
        val mediaItems = dataSource.fetchData()
        remoteDataSourceData.clear()
        remoteDataSourceData.addAll(mediaItems)
    }
}
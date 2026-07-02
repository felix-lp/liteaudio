package app.liteaudio.data.repo

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import app.liteaudio.data.db.TrackDao
import app.liteaudio.data.db.TrackEntity
import app.liteaudio.media.cache.CacheHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Byte truth lives in the Media3 cache, not in the DB. This repo derives
 * live per-track cache status from SimpleCache spans.
 */
sealed interface CacheStatus {
    data object None : CacheStatus
    data class Partial(val bytes: Long, val total: Long?) : CacheStatus {
        val fraction: Float
            get() = if (total != null && total > 0) bytes.toFloat() / total else 0f
    }
    data class Full(val bytes: Long) : CacheStatus
}

@UnstableApi
class CacheStatusRepository(
    private val cacheHolder: CacheHolder,
    private val trackDao: TrackDao,
) {
    fun statusOf(track: TrackEntity?): CacheStatus {
        val itag = track?.selectedItag ?: return CacheStatus.None
        val key = cacheHolder.cacheKey(track.videoId, itag)
        val total = track.contentLength
        val bytes = cacheHolder.cachedBytes(key, total)
        return when {
            bytes <= 0 -> CacheStatus.None
            total != null && total > 0 && bytes >= total -> CacheStatus.Full(bytes)
            else -> CacheStatus.Partial(bytes, total)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(videoId: String): Flow<CacheStatus> =
        trackDao.observe(videoId)
            .distinctUntilChanged { a, b ->
                a?.selectedItag == b?.selectedItag && a?.contentLength == b?.contentLength
            }
            .flatMapLatest { track ->
                val itag = track?.selectedItag
                    ?: return@flatMapLatest flowOf<CacheStatus>(CacheStatus.None)
                observeKey(track, cacheHolder.cacheKey(track.videoId, itag))
            }
            .flowOn(Dispatchers.IO)

    private fun observeKey(track: TrackEntity, key: String): Flow<CacheStatus> = callbackFlow {
        fun emitNow() {
            trySend(statusOf(track))
        }
        val listener = object : Cache.Listener {
            override fun onSpanAdded(cache: Cache, span: CacheSpan) = emitNow()
            override fun onSpanRemoved(cache: Cache, span: CacheSpan) = emitNow()
            override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) = Unit
        }
        cacheHolder.cache.addListener(key, listener)
        emitNow()
        awaitClose { cacheHolder.cache.removeListener(key, listener) }
    }.conflate()

    /** Aggregate stats for a playlist header: cached & downloaded counts. */
    suspend fun countCached(tracks: List<TrackEntity>): Int =
        tracks.count { statusOf(it) is CacheStatus.Full }

    fun lruBytes(): Long = cacheHolder.evictor.lruBytes()

    fun totalBytes(): Long = cacheHolder.cache.cacheSpace
}

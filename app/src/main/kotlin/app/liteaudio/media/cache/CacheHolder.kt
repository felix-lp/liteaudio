package app.liteaudio.media.cache

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * The single SimpleCache instance for the whole app: playback, prefetch and
 * downloads all read/write here. Cache key = "videoId:itag" — stable across
 * googlevideo URL rotation, so re-resolved URLs land on the same bytes.
 */
@UnstableApi
class CacheHolder(context: Context, initialLimitBytes: Long) {

    val evictor = PinningCacheEvictor(initialLimitBytes)

    private val databaseProvider = StandaloneDatabaseProvider(context)

    val cache: SimpleCache = SimpleCache(
        File(context.filesDir, "mediacache"),
        evictor,
        databaseProvider,
    )

    fun cacheKey(videoId: String, itag: Int): String = "$videoId:$itag"

    /** Total bytes for a key (all cached spans). */
    fun cachedBytes(key: String, contentLength: Long?): Long =
        if (contentLength != null && contentLength > 0) {
            cache.getCachedBytes(key, 0, contentLength)
        } else {
            cache.getCachedSpans(key).sumOf { it.length }
        }

    fun isFullyCached(key: String, contentLength: Long?): Boolean {
        if (contentLength == null || contentLength <= 0) return false
        return cache.getCachedBytes(key, 0, contentLength) >= contentLength
    }

    /** Remove every cached byte of a key (explicit "free space now"). */
    fun removeKey(key: String) {
        runCatching { cache.removeResource(key) }
    }

    fun release() {
        cache.release()
    }
}

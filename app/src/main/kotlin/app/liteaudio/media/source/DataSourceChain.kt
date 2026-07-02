package app.liteaudio.media.source

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.PriorityDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import app.liteaudio.core.StatusBus
import app.liteaudio.extractor.OkHttpDownloader
import app.liteaudio.media.cache.CacheHolder
import app.liteaudio.R
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Internal track URI understood by [UrlResolver].
 * MediaItems never carry googlevideo URLs — those rot in ~6h.
 */
object TrackUri {
    const val SCHEME = "liteaudio"

    fun forVideo(videoId: String): Uri = Uri.parse("$SCHEME://v/$videoId")

    fun videoId(uri: Uri): String? =
        if (uri.scheme == SCHEME) uri.lastPathSegment else null
}

/**
 * Swaps the internal liteaudio:// URI for a fresh googlevideo URL and pins the
 * cache key to videoId:itag. For fully-cached content CacheDataSource never
 * opens upstream, so a stale URL costs nothing offline.
 */
@UnstableApi
class UrlResolver(
    private val urlStore: StreamUrlStore,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = TrackUri.videoId(dataSpec.uri) ?: return dataSpec
        // Blocking is fine: we're on the player's loader thread and resolution
        // is the actual work to do. Extractor errors propagate as IOException
        // subclasses into the retry policy.
        val resolved = runBlocking { urlStore.resolve(videoId) }
        return dataSpec.buildUpon()
            .setUri(Uri.parse(resolved.url))
            .setKey(resolved.cacheKey)
            .build()
    }
}

/**
 * Detects a dead googlevideo URL (HTTP 403) mid-transfer, invalidates it so the
 * next open re-resolves, and reports the refresh to the status strip.
 */
@UnstableApi
class UrlExpiryDataSource(
    private val upstream: DataSource,
    private val urlStore: StreamUrlStore,
    private val statusBus: StatusBus,
) : DataSource by upstream {

    private var currentKey: String? = null

    override fun open(dataSpec: DataSpec): Long {
        currentKey = dataSpec.key
        return try {
            upstream.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            handle403(e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        upstream.read(buffer, offset, length)
    } catch (e: HttpDataSource.InvalidResponseCodeException) {
        handle403(e)
        throw e
    }

    private fun handle403(e: HttpDataSource.InvalidResponseCodeException) {
        if (e.responseCode == 403 || e.responseCode == 410) {
            val key = currentKey ?: return
            runBlocking { urlStore.invalidate(key) }
            statusBus.toast(
                StatusBus.Text.Res(R.string.status_url_refresh),
                StatusBus.Severity.Warning,
            )
        }
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val urlStore: StreamUrlStore,
        private val statusBus: StatusBus,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            UrlExpiryDataSource(upstreamFactory.createDataSource(), urlStore, statusBus)
    }
}

/**
 * Builds the full chains:
 *
 * playback:  ResolvingDataSource → CacheDataSource(write-through)
 *              → PriorityDataSource(PLAYBACK) → UrlExpiry → OkHttp
 * transfer:  same at PRIORITY_DOWNLOAD (used by CacheWriter in TransferEngine)
 */
@UnstableApi
class MediaSourceChain(
    baseClient: OkHttpClient,
    private val cacheHolder: CacheHolder,
    private val urlStore: StreamUrlStore,
    private val statusBus: StatusBus,
    val priorityTaskManager: PriorityTaskManager,
) {
    // Media client: patient reads for a 2G channel. Infinite patience lives in
    // retries, not in socket waits — a dead socket must eventually throw.
    private val mediaClient = baseClient.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val httpFactory = OkHttpDataSource.Factory(mediaClient)
        .setUserAgent(OkHttpDownloader.USER_AGENT)

    private fun upstreamFactory(priority: Int): DataSource.Factory {
        val expiryFactory = UrlExpiryDataSource.Factory(httpFactory, urlStore, statusBus)
        return DataSource.Factory {
            PriorityDataSource(expiryFactory.createDataSource(), priorityTaskManager, priority)
        }
    }

    private fun cacheFactory(priority: Int, blockOnCache: Boolean): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cacheHolder.cache)
            .setUpstreamDataSourceFactory(upstreamFactory(priority))
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory().setCache(cacheHolder.cache),
            )
            .setFlags(if (blockOnCache) CacheDataSource.FLAG_BLOCK_ON_CACHE else 0)

    /** Factory for the player: resolve URL lazily, then cached read-write. */
    fun playbackFactory(): DataSource.Factory =
        ResolvingDataSource.Factory(
            cacheFactory(C.PRIORITY_PLAYBACK, blockOnCache = true),
            UrlResolver(urlStore),
        )

    /**
     * CacheDataSource for CacheWriter transfers (prefetch + download queue).
     * CacheWriter requires the concrete CacheDataSource type, so the transfer
     * path resolves URLs itself (suspend) instead of via ResolvingDataSource.
     */
    fun newTransferCacheDataSource(): CacheDataSource =
        cacheFactory(C.PRIORITY_DOWNLOAD, blockOnCache = false).createDataSource()
}

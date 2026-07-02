package app.liteaudio.media.source

import app.liteaudio.data.db.StreamUrlDao
import app.liteaudio.data.db.StreamUrlEntity
import app.liteaudio.data.db.TrackDao
import app.liteaudio.data.settings.AudioFormat
import app.liteaudio.data.settings.SettingsRepository
import app.liteaudio.extractor.ExtractorError
import app.liteaudio.extractor.ResolvedStream
import app.liteaudio.extractor.YtExtractor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * videoId:itag → (url, expiresAt), memory + Room. The single authority for
 * "give me a playable URL for this track": fixes the track's itag on first
 * resolution (so cache keys never drift), refreshes expired/invalidated URLs
 * through NewPipeExtractor.
 */
class StreamUrlStore(
    private val extractor: YtExtractor,
    private val streamUrlDao: StreamUrlDao,
    private val trackDao: TrackDao,
    private val settings: SettingsRepository,
) {
    private val memory = ConcurrentHashMap<String, StreamUrlEntity>()
    private val resolveMutex = Mutex()

    data class Resolved(
        val videoId: String,
        val itag: Int,
        val cacheKey: String,
        val url: String,
        val contentLength: Long?,
    )

    /** The itag this track will use, without any network I/O; null if unknown yet. */
    suspend fun knownItag(videoId: String): Int? = trackDao.get(videoId)?.selectedItag

    /**
     * Return a fresh URL for the track, resolving via the extractor when needed.
     * [forDownload] selects the download format for tracks with no fixed itag yet.
     */
    suspend fun resolve(videoId: String, forDownload: Boolean = false): Resolved {
        // fast path: known itag + fresh cached URL
        knownItag(videoId)?.let { itag ->
            val key = "$videoId:$itag"
            freshEntry(key)?.let { entry ->
                return Resolved(videoId, itag, key, entry.url, trackDao.get(videoId)?.contentLength)
            }
        }
        return resolveMutex.withLock {
            // re-check under the lock — another caller may have just resolved it
            knownItag(videoId)?.let { itag ->
                val key = "$videoId:$itag"
                freshEntry(key)?.let { entry ->
                    return@withLock Resolved(videoId, itag, key, entry.url, trackDao.get(videoId)?.contentLength)
                }
            }
            resolveViaExtractor(videoId, forDownload)
        }
    }

    private suspend fun resolveViaExtractor(videoId: String, forDownload: Boolean): Resolved {
        val (meta, streams) = extractor.resolveVideo(videoId)

        val existing = trackDao.get(videoId)
        val preferred: AudioFormat = when {
            existing?.selectedItag != null -> AudioFormat.byItag(existing.selectedItag)
            forDownload -> settings.settings.value.downloadFormat
            else -> settings.settings.value.streamFormat
        }
        val stream: ResolvedStream = extractor.pickStream(videoId, streams, preferred)
            ?: throw ExtractorError.Deleted()

        // fix the itag + metadata on the track row
        if (existing == null) {
            trackDao.upsert(
                app.liteaudio.data.db.TrackEntity(
                    videoId = videoId,
                    title = meta.title,
                    uploader = meta.uploader,
                    durationSec = meta.durationSec,
                    thumbnailUrl = meta.thumbnailUrl,
                    selectedItag = stream.itag,
                    selectedMime = stream.mimeType,
                    selectedBitrate = stream.averageBitrate,
                    contentLength = stream.contentLength,
                ),
            )
        } else {
            trackDao.setSelectedStream(
                videoId = videoId,
                itag = stream.itag,
                mime = stream.mimeType,
                bitrate = stream.averageBitrate,
                length = stream.contentLength ?: existing.contentLength,
            )
            if (existing.unavailableReason != null) trackDao.setUnavailable(videoId, null)
        }

        val entity = StreamUrlEntity(stream.cacheKey, stream.url, stream.expiresAtMs)
        memory[stream.cacheKey] = entity
        streamUrlDao.upsert(entity)

        return Resolved(videoId, stream.itag, stream.cacheKey, stream.url, stream.contentLength)
    }

    private suspend fun freshEntry(cacheKey: String): StreamUrlEntity? {
        val now = System.currentTimeMillis()
        val margin = 60_000L
        memory[cacheKey]?.let { if (it.expiresAt - margin > now) return it else memory.remove(cacheKey) }
        val db = streamUrlDao.get(cacheKey) ?: return null
        return if (db.expiresAt - margin > now) {
            memory[cacheKey] = db
            db
        } else {
            streamUrlDao.delete(cacheKey)
            null
        }
    }

    /** Called on HTTP 403: the URL is dead no matter what its expiry claims. */
    suspend fun invalidate(cacheKey: String) {
        memory.remove(cacheKey)
        streamUrlDao.delete(cacheKey)
    }
}

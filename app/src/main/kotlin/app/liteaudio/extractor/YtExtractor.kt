package app.liteaudio.extractor

import android.net.Uri
import app.liteaudio.data.settings.AudioFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class TrackMeta(
    val videoId: String,
    val title: String,
    val uploader: String,
    val durationSec: Int,
    val thumbnailUrl: String?,
)

data class AudioStreamMeta(
    val itag: Int,
    val url: String,
    val mimeType: String?,
    val averageBitrate: Int,
    val contentLength: Long?,
)

data class ResolvedStream(
    val videoId: String,
    val itag: Int,
    val url: String,
    val mimeType: String?,
    val averageBitrate: Int,
    val contentLength: Long?,
    val expiresAtMs: Long,
) {
    val cacheKey: String get() = "$videoId:$itag"
}

data class PlaylistMeta(
    val ytPlaylistId: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val approxTrackCount: Long,
)

sealed interface PlaylistPage {
    data class Header(val meta: PlaylistMeta) : PlaylistPage
    data class Items(val tracks: List<TrackMeta>, val totalSoFar: Int) : PlaylistPage
    data object End : PlaylistPage
}

sealed interface ParsedUrl {
    data class Video(val videoId: String) : ParsedUrl
    data class Playlist(val playlistUrl: String) : ParsedUrl
    data object Invalid : ParsedUrl
}

/**
 * The only YouTube-aware component. All calls are serialized through a mutex:
 * on a 2G channel we never hammer YouTube with parallel metadata requests.
 */
class YtExtractor(metadataClient: OkHttpClient) {

    private val mutex = Mutex()
    private val youtube = ServiceList.YouTube

    init {
        NewPipe.init(
            OkHttpDownloader(metadataClient),
            Localization("ru", "RU"),
            ContentCountry("RU"),
        )
    }

    /** Recognize a pasted URL: playlist, video, or garbage. */
    fun parseUrl(raw: String): ParsedUrl {
        val text = raw.trim()
        if (text.isEmpty()) return ParsedUrl.Invalid
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return ParsedUrl.Invalid
        val host = (uri.host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
        val isYt = host == "youtube.com" || host == "youtu.be" || host == "music.youtube.com"
        if (!isYt) return ParsedUrl.Invalid

        val listId = uri.getQueryParameter("list")
        if (listId != null && !listId.startsWith("RD")) { // RD* are mixes — not real playlists
            return ParsedUrl.Playlist("https://www.youtube.com/playlist?list=$listId")
        }
        val videoId = when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()
            uri.path?.startsWith("/watch") == true -> uri.getQueryParameter("v")
            uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1)
            uri.path?.startsWith("/live/") == true -> uri.pathSegments.getOrNull(1)
            else -> null
        }
        return if (videoId != null && videoId.length == 11) {
            ParsedUrl.Video(videoId)
        } else ParsedUrl.Invalid
    }

    /** Full stream resolution for one video: metadata + all audio streams. */
    suspend fun resolveVideo(videoId: String): Pair<TrackMeta, List<AudioStreamMeta>> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val info = StreamInfo.getInfo(youtube, watchUrl(videoId))
                    val meta = TrackMeta(
                        videoId = videoId,
                        title = info.name.orEmpty(),
                        uploader = info.uploaderName.orEmpty(),
                        durationSec = info.duration.toInt().coerceAtLeast(0),
                        thumbnailUrl = pickThumbnail(info.thumbnails),
                    )
                    val streams = info.audioStreams.mapNotNull { s ->
                        val url = if (s.isUrl) s.content else return@mapNotNull null
                        AudioStreamMeta(
                            itag = s.itag,
                            url = url,
                            mimeType = s.format?.mimeType,
                            averageBitrate = s.averageBitrate,
                            contentLength = s.itagItem?.contentLength?.takeIf { it > 0 },
                        )
                    }
                    meta to streams
                } catch (t: Throwable) {
                    throw ExtractorError.from(t)
                }
            }
        }

    /**
     * Pick the best audio stream for [preferred] with its fallback chain.
     * Returns null when the video exposes no usable audio streams at all.
     */
    fun pickStream(videoId: String, streams: List<AudioStreamMeta>, preferred: AudioFormat): ResolvedStream? {
        val chain = AudioFormat.fallbackChain(preferred)
        val byItag = streams.associateBy { it.itag }
        val chosen = chain.firstNotNullOfOrNull { byItag[it] }
            ?: streams.minByOrNull { it.averageBitrate } // last resort: smallest bitrate
            ?: return null
        return ResolvedStream(
            videoId = videoId,
            itag = chosen.itag,
            url = chosen.url,
            mimeType = chosen.mimeType,
            averageBitrate = chosen.averageBitrate,
            contentLength = chosen.contentLength,
            expiresAtMs = parseExpiry(chosen.url),
        )
    }

    /**
     * Fetch a playlist page by page. Each emission is committed by the caller,
     * so an interrupted fetch on 2G loses nothing.
     */
    fun resolvePlaylist(playlistUrl: String): Flow<PlaylistPage> = flow {
        try {
            val info = mutex.withLock { PlaylistInfo.getInfo(youtube, playlistUrl) }
            val ytId = runCatching {
                youtube.playlistLHFactory.getId(playlistUrl)
            }.getOrElse { Uri.parse(playlistUrl).getQueryParameter("list") ?: playlistUrl }
            emit(
                PlaylistPage.Header(
                    PlaylistMeta(
                        ytPlaylistId = ytId,
                        title = info.name.orEmpty(),
                        uploader = info.uploaderName?.takeIf { it.isNotBlank() },
                        thumbnailUrl = pickThumbnail(info.thumbnails),
                        approxTrackCount = info.streamCount,
                    ),
                ),
            )

            var total = 0
            var items = info.relatedItems.filterIsInstance<StreamInfoItem>()
            var nextPage = info.nextPage
            // header parsed, playlist claims tracks, but zero items and no
            // continuation: the item parser is broken — do not end silently
            if (items.isEmpty() && nextPage == null && info.streamCount > 0) {
                android.util.Log.e(TAG, "playlist has ${info.streamCount} tracks but 0 items parsed")
                throw ExtractorError.ParseBroken()
            }
            while (true) {
                val tracks = items.mapNotNull { toTrackMeta(it) }
                android.util.Log.i(
                    TAG,
                    "playlist page: ${items.size} items -> ${tracks.size} tracks, next=${nextPage != null}",
                )
                // items came in but none parsed: that's extractor breakage,
                // not an empty playlist — fail loudly instead of a silent zero
                if (items.isNotEmpty() && tracks.isEmpty()) {
                    throw ExtractorError.ParseBroken()
                }
                total += tracks.size
                if (tracks.isNotEmpty()) emit(PlaylistPage.Items(tracks, total))
                if (nextPage == null) break
                val page = mutex.withLock {
                    PlaylistInfo.getMoreItems(youtube, playlistUrl, nextPage)
                }
                items = page.items.filterIsInstance<StreamInfoItem>()
                nextPage = page.nextPage
            }
            emit(PlaylistPage.End)
        } catch (t: Throwable) {
            throw ExtractorError.from(t)
        }
    }.flowOn(Dispatchers.IO)

    private fun toTrackMeta(item: StreamInfoItem): TrackMeta? {
        val videoId = extractVideoId(item.url) ?: run {
            android.util.Log.w(TAG, "cannot extract videoId from url=${item.url}")
            return null
        }
        return TrackMeta(
            videoId = videoId,
            title = item.name.orEmpty(),
            uploader = item.uploaderName.orEmpty(),
            durationSec = item.duration.toInt().coerceAtLeast(0),
            thumbnailUrl = pickThumbnail(item.thumbnails),
        )
    }

    /** LinkHandlerFactory first, manual URL parsing as a fallback. */
    private fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        runCatching { youtube.streamLHFactory.getId(url) }.getOrNull()?.let { return it }
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        uri.getQueryParameter("v")?.takeIf { it.length == 11 }?.let { return it }
        val segments = uri.pathSegments ?: return null
        for (i in segments.indices) {
            if (segments[i] in listOf("shorts", "live", "embed") && i + 1 < segments.size) {
                return segments[i + 1].takeIf { it.length == 11 }
            }
        }
        if ((uri.host ?: "").endsWith("youtu.be")) {
            return segments.firstOrNull()?.takeIf { it.length == 11 }
        }
        return null
    }

    private fun pickThumbnail(images: List<Image>?): String? {
        if (images.isNullOrEmpty()) return null
        // smallest image that is still >= 160px wide; byte economy over beauty
        return images
            .sortedBy { if (it.width > 0) it.width else Int.MAX_VALUE }
            .firstOrNull { it.width >= 160 }
            ?.url
            ?: images.first().url
    }

    companion object {
        private const val TAG = "YtExtractor"

        fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

        /** googlevideo URLs carry their expiry as an `expire` unix-seconds param. */
        fun parseExpiry(url: String): Long {
            val fromParam = runCatching {
                Uri.parse(url).getQueryParameter("expire")?.toLongOrNull()
            }.getOrNull()
            return if (fromParam != null && fromParam > 0) {
                fromParam * 1000
            } else {
                System.currentTimeMillis() + DEFAULT_TTL_MS
            }
        }

        /** conservative fallback TTL when the URL has no expire param */
        const val DEFAULT_TTL_MS = 4L * 60 * 60 * 1000
    }
}

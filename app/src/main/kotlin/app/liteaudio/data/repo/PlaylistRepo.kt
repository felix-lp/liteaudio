package app.liteaudio.data.repo

import app.liteaudio.R
import app.liteaudio.core.StatusBus
import app.liteaudio.data.db.LiteDb
import app.liteaudio.data.db.PlaylistEntity
import app.liteaudio.data.db.PlaylistTrackEntity
import app.liteaudio.data.db.PlaylistType
import app.liteaudio.data.db.TrackEntity
import app.liteaudio.extractor.ExtractorError
import app.liteaudio.extractor.ParsedUrl
import app.liteaudio.extractor.PlaylistPage
import app.liteaudio.extractor.TrackMeta
import app.liteaudio.extractor.YtExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Playlist + track operations. Extraction jobs run in the app scope and report
 * through the status strip; paged playlist content is committed to Room as it
 * arrives, so an interrupted 2G fetch keeps everything already received.
 */
class PlaylistRepo(
    private val db: LiteDb,
    private val extractor: YtExtractor,
    private val statusBus: StatusBus,
    private val scope: CoroutineScope,
) {
    private val playlistDao = db.playlistDao()
    private val trackDao = db.trackDao()

    fun observePlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observeAll()
    fun observePlaylist(id: Long) = playlistDao.observe(id)
    fun observeTracks(playlistId: Long) = trackDao.observePlaylistTracks(playlistId)
    suspend fun tracksOf(playlistId: Long) = trackDao.getPlaylistTracks(playlistId)

    fun parseUrl(url: String): ParsedUrl = extractor.parseUrl(url)

    /** Add a YouTube playlist by URL; extraction continues in background. */
    fun addYoutubePlaylist(playlistUrl: String): Job = scope.launch {
        runExtraction(playlistUrl, existingId = null)
    }

    /** Manual refresh of a YouTube playlist (the only kind of refresh). */
    fun refresh(playlistId: Long): Job = scope.launch {
        val playlist = playlistDao.get(playlistId) ?: return@launch
        val ytId = playlist.ytPlaylistId ?: return@launch
        runExtraction("https://www.youtube.com/playlist?list=$ytId", existingId = playlistId)
    }

    private suspend fun runExtraction(playlistUrl: String, existingId: Long?) {
        val errorId = "playlist:$playlistUrl"
        statusBus.clearError(errorId)
        statusBus.set(
            StatusBus.Key.Extraction,
            StatusBus.Entry(
                text = StatusBus.Text.Res(R.string.status_extracting_playlist, listOf(0)),
                severity = StatusBus.Severity.Progress,
                indeterminate = true,
            ),
        )
        var playlistId = existingId
        var received = 0
        var isRefresh = existingId != null
        val seenIds = mutableListOf<String>()
        try {
            extractor.resolvePlaylist(playlistUrl).collect { page ->
                when (page) {
                    is PlaylistPage.Header -> {
                        val meta = page.meta
                        val existing = playlistId?.let { playlistDao.get(it) }
                            ?: playlistDao.getByYtId(meta.ytPlaylistId)
                        playlistId = if (existing == null) {
                            isRefresh = false
                            playlistDao.upsert(
                                PlaylistEntity(
                                    type = PlaylistType.YOUTUBE,
                                    ytPlaylistId = meta.ytPlaylistId,
                                    title = meta.title,
                                    uploader = meta.uploader,
                                    thumbnailUrl = meta.thumbnailUrl,
                                    trackCount = meta.approxTrackCount.toInt(),
                                ),
                            )
                        } else {
                            playlistDao.update(
                                existing.copy(
                                    title = meta.title,
                                    uploader = meta.uploader,
                                    thumbnailUrl = meta.thumbnailUrl ?: existing.thumbnailUrl,
                                ),
                            )
                            existing.id
                        }
                    }

                    is PlaylistPage.Items -> {
                        val id = playlistId ?: return@collect
                        val tracks = page.tracks.map { it.toEntity() }
                        // refresh replaces positions wholesale, first page clears old links
                        if (isRefresh && received == 0) trackDao.unlinkAll(id)
                        trackDao.replacePlaylistContent(id, tracks, received)
                        received += tracks.size
                        seenIds += tracks.map { it.videoId }
                        statusBus.set(
                            StatusBus.Key.Extraction,
                            StatusBus.Entry(
                                text = StatusBus.Text.Res(
                                    R.string.status_extracting_playlist,
                                    listOf(received),
                                ),
                                severity = StatusBus.Severity.Progress,
                                indeterminate = true,
                            ),
                        )
                    }

                    PlaylistPage.End -> {
                        val id = playlistId ?: return@collect
                        playlistDao.updateStats(id, received, System.currentTimeMillis())
                        val title = playlistDao.get(id)?.title ?: ""
                        statusBus.toast(
                            StatusBus.Text.Res(R.string.status_playlist_added, listOf(title)),
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            val err = ExtractorError.from(e)
            statusBus.error(
                id = errorId,
                text = StatusBus.Text.Res(err.stringRes),
                action = StatusBus.Action(StatusBus.Text.Res(R.string.action_retry)) {
                    statusBus.clearError(errorId)
                    if (playlistId != null) refresh(playlistId!!) else addYoutubePlaylist(playlistUrl)
                },
            )
        } finally {
            statusBus.clear(StatusBus.Key.Extraction)
            // partial fetch still counts: update stats so UI shows what we have
            playlistId?.let { id ->
                val count = trackDao.countTracks(id)
                if (count > 0) playlistDao.updateStats(id, count, null)
            }
        }
    }

    /** Add a single video (by URL) to a local playlist, resolving metadata in background. */
    fun addVideoToLocalPlaylist(videoId: String, playlistId: Long): Job = scope.launch {
        statusBus.set(
            StatusBus.Key.Extraction,
            StatusBus.Entry(
                text = StatusBus.Text.Res(R.string.status_extracting_video),
                severity = StatusBus.Severity.Progress,
                indeterminate = true,
            ),
        )
        try {
            // full resolve also fixes itag + contentLength for later caching
            val (meta, _) = extractor.resolveVideo(videoId)
            trackDao.upsert(
                trackDao.get(videoId)?.copy(
                    title = meta.title,
                    uploader = meta.uploader,
                    durationSec = meta.durationSec,
                    thumbnailUrl = meta.thumbnailUrl,
                ) ?: meta.toEntity(),
            )
            appendToLocal(playlistId, videoId)
            statusBus.toast(StatusBus.Text.Res(R.string.status_added_to_playlist))
        } catch (e: Throwable) {
            val err = ExtractorError.from(e)
            statusBus.error(
                id = "video:$videoId",
                text = StatusBus.Text.Res(err.stringRes),
                action = StatusBus.Action(StatusBus.Text.Res(R.string.action_retry)) {
                    statusBus.clearError("video:$videoId")
                    addVideoToLocalPlaylist(videoId, playlistId)
                },
            )
        } finally {
            statusBus.clear(StatusBus.Key.Extraction)
        }
    }

    /** Add an already-known track to a local playlist (no network). */
    suspend fun appendToLocal(playlistId: Long, videoId: String) {
        val pos = (trackDao.maxPosition(playlistId) ?: -1) + 1
        trackDao.link(listOf(PlaylistTrackEntity(playlistId, videoId, pos)))
        playlistDao.updateStats(playlistId, trackDao.countTracks(playlistId), null)
    }

    suspend fun createLocalPlaylist(title: String): Long =
        playlistDao.upsert(PlaylistEntity(type = PlaylistType.LOCAL, title = title))

    suspend fun rename(playlistId: Long, title: String) = playlistDao.rename(playlistId, title)

    suspend fun delete(playlistId: Long) = playlistDao.delete(playlistId)

    suspend fun removeTrack(playlistId: Long, videoId: String) {
        trackDao.unlink(playlistId, videoId)
        playlistDao.updateStats(playlistId, trackDao.countTracks(playlistId), null)
    }

    suspend fun moveTrack(playlistId: Long, fromPos: Int, toPos: Int) {
        val rows = trackDao.getPlaylistTracks(playlistId)
        val ids = rows.sortedBy { it.position }.map { it.track.videoId }.toMutableList()
        if (fromPos !in ids.indices || toPos !in ids.indices) return
        val id = ids.removeAt(fromPos)
        ids.add(toPos, id)
        trackDao.unlinkAll(playlistId)
        trackDao.link(ids.mapIndexed { i, v -> PlaylistTrackEntity(playlistId, v, i) })
    }

    private fun TrackMeta.toEntity() = TrackEntity(
        videoId = videoId,
        title = title,
        uploader = uploader,
        durationSec = durationSec,
        thumbnailUrl = thumbnailUrl,
    )
}

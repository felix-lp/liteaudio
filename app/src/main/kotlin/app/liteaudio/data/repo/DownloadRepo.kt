package app.liteaudio.data.repo

import app.liteaudio.data.db.DownloadDao
import app.liteaudio.data.db.DownloadEntity
import app.liteaudio.data.db.DownloadState
import app.liteaudio.data.db.TrackDao
import app.liteaudio.media.cache.CacheHolder
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.Flow

/**
 * Intent layer for explicit downloads (the Mihon-style queue). Byte truth is
 * the cache; this table holds order, state and progress for the UI.
 */
@UnstableApi
class DownloadRepo(
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val cacheHolder: CacheHolder,
) {
    fun observeAll(): Flow<List<DownloadEntity>> = downloadDao.observeAll()
    fun observeQueue(): Flow<List<DownloadEntity>> = downloadDao.observeQueue()
    fun observeDone(): Flow<List<DownloadEntity>> = downloadDao.observeDone()
    fun observePinKeys(): Flow<List<String>> = downloadDao.observePinKeys()

    suspend fun enqueue(videoIds: List<String>) {
        var pos = (downloadDao.maxQueuePosition() ?: -1) + 1
        for (videoId in videoIds) {
            val existing = downloadDao.get(videoId)
            if (existing != null && existing.state != DownloadState.ERROR) continue
            // already fully cached? register as DONE instantly — zero network
            val track = trackDao.get(videoId)
            val key = track?.selectedItag?.let { cacheHolder.cacheKey(videoId, it) }
            val done = key != null && cacheHolder.isFullyCached(key, track.contentLength)
            downloadDao.upsert(
                DownloadEntity(
                    videoId = videoId,
                    state = if (done) DownloadState.DONE else DownloadState.QUEUED,
                    queuePosition = pos++,
                    bytesDone = if (done) track?.contentLength ?: 0 else 0,
                    bytesTotal = track?.contentLength,
                    completedAt = if (done) System.currentTimeMillis() else null,
                ),
            )
        }
    }

    suspend fun pause(videoId: String) = downloadDao.setState(videoId, DownloadState.PAUSED)

    suspend fun resume(videoId: String) = downloadDao.setState(videoId, DownloadState.QUEUED)

    suspend fun retry(videoId: String) = downloadDao.setState(videoId, DownloadState.QUEUED)

    suspend fun pauseAll() = downloadDao.pauseAll()

    suspend fun resumeAll() = downloadDao.resumeAll()

    /** Remove from queue; bytes stay in LRU cache (unpin happens via pin-keys flow). */
    suspend fun remove(videoId: String) = downloadDao.delete(videoId)

    /** Delete a finished download AND its bytes ("free space now"). */
    suspend fun deleteWithBytes(videoId: String) {
        val track = trackDao.get(videoId)
        downloadDao.delete(videoId)
        val itag = track?.selectedItag ?: return
        cacheHolder.removeKey(cacheHolder.cacheKey(videoId, itag))
    }

    /** Reorder within the pending queue (drag & drop priority). */
    suspend fun move(videoId: String, newIndex: Int) {
        val current = downloadDao.queueSnapshot().toMutableList()
        val idx = current.indexOfFirst { it.videoId == videoId }
        if (idx == -1) return
        val item = current.removeAt(idx)
        current.add(newIndex.coerceIn(0, current.size), item)
        current.forEachIndexed { i, e -> downloadDao.setQueuePosition(e.videoId, i) }
    }
}

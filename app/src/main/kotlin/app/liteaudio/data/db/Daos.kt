package app.liteaudio.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE ytPlaylistId = :ytId")
    suspend fun getByYtId(ytId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlists SET trackCount = :count, lastRefreshedAt = :refreshedAt WHERE id = :id")
    suspend fun updateStats(id: Long, count: Int, refreshedAt: Long?)
}

data class PlaylistTrackRow(
    @Embedded val track: TrackEntity,
    val position: Int,
)

@Dao
interface TrackDao {
    @Query(
        """SELECT t.*, pt.position FROM tracks t
           INNER JOIN playlist_tracks pt ON pt.videoId = t.videoId
           WHERE pt.playlistId = :playlistId
           ORDER BY pt.position""",
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<PlaylistTrackRow>>

    @Query(
        """SELECT t.*, pt.position FROM tracks t
           INNER JOIN playlist_tracks pt ON pt.videoId = t.videoId
           WHERE pt.playlistId = :playlistId
           ORDER BY pt.position""",
    )
    suspend fun getPlaylistTracks(playlistId: Long): List<PlaylistTrackRow>

    @Query("SELECT * FROM tracks WHERE videoId = :videoId")
    suspend fun get(videoId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE videoId IN (:videoIds)")
    suspend fun getAll(videoIds: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE videoId = :videoId")
    fun observe(videoId: String): Flow<TrackEntity?>

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tracks: List<TrackEntity>): List<Long>

    @Query(
        """UPDATE tracks SET title = :title, uploader = :uploader,
           durationSec = :durationSec, thumbnailUrl = :thumbnailUrl
           WHERE videoId = :videoId""",
    )
    suspend fun updateMeta(videoId: String, title: String, uploader: String, durationSec: Int, thumbnailUrl: String?)

    @Query("UPDATE tracks SET selectedItag = :itag, selectedMime = :mime, selectedBitrate = :bitrate, contentLength = :length WHERE videoId = :videoId")
    suspend fun setSelectedStream(videoId: String, itag: Int, mime: String?, bitrate: Int?, length: Long?)

    @Query("UPDATE tracks SET unavailableReason = :reason WHERE videoId = :videoId")
    suspend fun setUnavailable(videoId: String, reason: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun link(links: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun unlinkAll(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun unlink(playlistId: Long, videoId: String)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int?

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun countTracks(playlistId: Long): Int

    /**
     * Insert new tracks, refresh metadata of existing ones WITHOUT touching
     * selectedItag/contentLength (cache keys must never drift), then link.
     */
    @Transaction
    suspend fun replacePlaylistContent(playlistId: Long, tracks: List<TrackEntity>, startPosition: Int) {
        val results = insertIgnore(tracks)
        tracks.forEachIndexed { i, t ->
            if (results[i] == -1L) {
                updateMeta(t.videoId, t.title, t.uploader, t.durationSec, t.thumbnailUrl)
            }
        }
        link(
            tracks.mapIndexed { i, t ->
                PlaylistTrackEntity(playlistId, t.videoId, startPosition + i)
            },
        )
    }
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM play_queue ORDER BY position")
    suspend fun getQueue(): List<PlayQueueEntity>

    @Query("SELECT * FROM play_queue ORDER BY position")
    fun observeQueue(): Flow<List<PlayQueueEntity>>

    @Query("DELETE FROM play_queue")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlayQueueEntity>)

    @Transaction
    suspend fun replaceQueue(items: List<PlayQueueEntity>) {
        clearQueue()
        insertAll(items)
    }

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun getState(): QueueStateEntity?

    @Upsert
    suspend fun saveState(state: QueueStateEntity)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY queuePosition")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE state != 'DONE' ORDER BY queuePosition")
    fun observeQueue(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE state != 'DONE' ORDER BY queuePosition")
    suspend fun queueSnapshot(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state = 'DONE' ORDER BY completedAt DESC")
    fun observeDone(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    suspend fun get(videoId: String): DownloadEntity?

    @Query("SELECT videoId FROM downloads")
    suspend fun allVideoIds(): List<String>

    @Query("SELECT videoId FROM downloads")
    fun observeAllVideoIds(): Flow<List<String>>

    /** Cache keys that must never be evicted: every download with a fixed itag. */
    @Query(
        """SELECT d.videoId || ':' || t.selectedItag FROM downloads d
           INNER JOIN tracks t ON t.videoId = d.videoId
           WHERE t.selectedItag IS NOT NULL""",
    )
    fun observePinKeys(): Flow<List<String>>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET state = :state, errorType = :error WHERE videoId = :videoId")
    suspend fun setState(videoId: String, state: DownloadState, error: String? = null)

    @Query("UPDATE downloads SET bytesDone = :done, bytesTotal = :total WHERE videoId = :videoId")
    suspend fun setProgress(videoId: String, done: Long, total: Long?)

    @Query("UPDATE downloads SET state = 'DONE', completedAt = :at, bytesDone = :bytes, bytesTotal = :bytes WHERE videoId = :videoId")
    suspend fun markDone(videoId: String, at: Long, bytes: Long)

    @Query("DELETE FROM downloads WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("SELECT MAX(queuePosition) FROM downloads")
    suspend fun maxQueuePosition(): Int?

    @Query("UPDATE downloads SET queuePosition = :position WHERE videoId = :videoId")
    suspend fun setQueuePosition(videoId: String, position: Int)

    @Query("UPDATE downloads SET state = 'PAUSED' WHERE state IN ('QUEUED','RUNNING','ERROR')")
    suspend fun pauseAll()

    @Query("UPDATE downloads SET state = 'QUEUED', errorType = NULL WHERE state IN ('PAUSED','ERROR','RUNNING')")
    suspend fun resumeAll()

    /** Round-robin retry: errored entries re-enter the queue at the back. */
    @Query("UPDATE downloads SET state = 'QUEUED' WHERE state = 'ERROR'")
    suspend fun resumeErrored()
}

@Dao
interface StreamUrlDao {
    @Query("SELECT * FROM stream_urls WHERE cacheKey = :cacheKey")
    suspend fun get(cacheKey: String): StreamUrlEntity?

    @Upsert
    suspend fun upsert(entity: StreamUrlEntity)

    @Query("DELETE FROM stream_urls WHERE cacheKey = :cacheKey")
    suspend fun delete(cacheKey: String)

    @Query("DELETE FROM stream_urls WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long)
}

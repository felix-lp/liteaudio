package app.liteaudio.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PlaylistType { YOUTUBE, LOCAL }

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: PlaylistType,
    val ytPlaylistId: String? = null,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val trackCount: Int = 0,
    val lastRefreshedAt: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val uploader: String = "",
    val durationSec: Int = 0,
    val thumbnailUrl: String? = null,
    /** itag actually used for this track; fixed at first fetch so cache keys stay stable */
    val selectedItag: Int? = null,
    val selectedMime: String? = null,
    val selectedBitrate: Int? = null,
    val contentLength: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val unavailableReason: String? = null,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["videoId"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("videoId"), Index("playlistId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val videoId: String,
    val position: Int,
)

enum class QueueItemSource { USER_NEXT, USER_LAST, PLAYLIST }

@Entity(tableName = "play_queue")
data class PlayQueueEntity(
    @PrimaryKey val position: Int,
    val videoId: String,
    val addedBy: QueueItemSource,
    /** id of the source playlist for PLAYLIST items (for the queue screen header) */
    val sourcePlaylistId: Long? = null,
)

@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0, // single row
    val currentPosition: Int = 0,
    val positionMs: Long = 0,
    val shuffleOn: Boolean = false,
    val shuffleSeed: Long = 0,
    val repeatMode: Int = 0, // Player.REPEAT_MODE_*
)

enum class DownloadState { QUEUED, RUNNING, PAUSED, ERROR, DONE }

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val state: DownloadState,
    val queuePosition: Int,
    val bytesDone: Long = 0,
    val bytesTotal: Long? = null,
    val errorType: String? = null,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(tableName = "stream_urls")
data class StreamUrlEntity(
    @PrimaryKey val cacheKey: String, // "videoId:itag"
    val url: String,
    val expiresAt: Long,
)

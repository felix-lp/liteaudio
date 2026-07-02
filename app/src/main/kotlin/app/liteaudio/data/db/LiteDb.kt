package app.liteaudio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun playlistTypeToString(v: PlaylistType): String = v.name

    @TypeConverter
    fun stringToPlaylistType(v: String): PlaylistType = PlaylistType.valueOf(v)

    @TypeConverter
    fun queueSourceToString(v: QueueItemSource): String = v.name

    @TypeConverter
    fun stringToQueueSource(v: String): QueueItemSource = QueueItemSource.valueOf(v)

    @TypeConverter
    fun downloadStateToString(v: DownloadState): String = v.name

    @TypeConverter
    fun stringToDownloadState(v: String): DownloadState = DownloadState.valueOf(v)
}

@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        PlaylistTrackEntity::class,
        PlayQueueEntity::class,
        QueueStateEntity::class,
        DownloadEntity::class,
        StreamUrlEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class LiteDb : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    abstract fun queueDao(): QueueDao
    abstract fun downloadDao(): DownloadDao
    abstract fun streamUrlDao(): StreamUrlDao

    companion object {
        fun create(context: Context): LiteDb =
            Room.databaseBuilder(context, LiteDb::class.java, "liteaudio.db")
                .build()
    }
}

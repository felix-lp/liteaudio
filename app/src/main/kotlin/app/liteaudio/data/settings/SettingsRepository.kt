package app.liteaudio.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Concrete audio format options surfaced in settings (itag-based, per plan). */
enum class AudioFormat(
    val itag: Int,
    val label: String,
    val approxKbps: Int,
    val container: String,
) {
    OPUS_50(249, "Opus ~50 kbps", 50, "WebM"),
    OPUS_70(250, "Opus ~70 kbps", 70, "WebM"),
    OPUS_160(251, "Opus ~160 kbps", 160, "WebM"),
    AAC_48(139, "AAC ~48 kbps", 48, "M4A"),
    AAC_128(140, "AAC 128 kbps", 128, "M4A");

    /** ≈ bytes for a 4-minute track, for the settings hint. */
    val bytesPer4Min: Long get() = approxKbps.toLong() * 1000 / 8 * 240

    companion object {
        fun byItag(itag: Int): AudioFormat = entries.firstOrNull { it.itag == itag } ?: OPUS_50

        /** Fallback order when the preferred itag is missing for a video. */
        fun fallbackChain(preferred: AudioFormat): List<Int> = when (preferred) {
            OPUS_50 -> listOf(249, 250, 139, 140, 251)
            OPUS_70 -> listOf(250, 249, 139, 140, 251)
            OPUS_160 -> listOf(251, 250, 249, 140, 139)
            AAC_48 -> listOf(139, 249, 250, 140, 251)
            AAC_128 -> listOf(140, 251, 250, 249, 139)
        }
    }
}

enum class NetworkDeathBehavior { WAIT_FOREVER, SKIP_TO_CACHED }

data class AppSettings(
    val streamFormat: AudioFormat = AudioFormat.OPUS_50,
    val downloadFormat: AudioFormat = AudioFormat.OPUS_50,
    val prefetchCount: Int = 2,
    val cacheLimitBytes: Long = 2L * 1024 * 1024 * 1024,
    val onNetworkDeath: NetworkDeathBehavior = NetworkDeathBehavior.WAIT_FOREVER,
    val accentColor: Long = 0xFFFF7A00,
    val language: String = "", // "" = system
)

class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store = context.dataStore

    private object Keys {
        val streamItag = intPreferencesKey("stream_itag")
        val downloadItag = intPreferencesKey("download_itag")
        val prefetchCount = intPreferencesKey("prefetch_count")
        val cacheLimitBytes = longPreferencesKey("cache_limit_bytes")
        val deathSkip = booleanPreferencesKey("network_death_skip")
        val accentColor = longPreferencesKey("accent_color")
        val language = stringPreferencesKey("language")
    }

    val settings: StateFlow<AppSettings> = store.data
        .map { p ->
            AppSettings(
                streamFormat = AudioFormat.byItag(p[Keys.streamItag] ?: AudioFormat.OPUS_50.itag),
                downloadFormat = AudioFormat.byItag(p[Keys.downloadItag] ?: AudioFormat.OPUS_50.itag),
                prefetchCount = (p[Keys.prefetchCount] ?: 2).coerceIn(0, 10),
                cacheLimitBytes = p[Keys.cacheLimitBytes] ?: (2L * 1024 * 1024 * 1024),
                onNetworkDeath = if (p[Keys.deathSkip] == true) {
                    NetworkDeathBehavior.SKIP_TO_CACHED
                } else NetworkDeathBehavior.WAIT_FOREVER,
                accentColor = p[Keys.accentColor] ?: 0xFFFF7A00,
                language = p[Keys.language] ?: "",
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun setStreamFormat(f: AudioFormat) = store.edit { it[Keys.streamItag] = f.itag }
    suspend fun setDownloadFormat(f: AudioFormat) = store.edit { it[Keys.downloadItag] = f.itag }
    suspend fun setPrefetchCount(n: Int) = store.edit { it[Keys.prefetchCount] = n.coerceIn(0, 10) }
    suspend fun setCacheLimitBytes(v: Long) = store.edit { it[Keys.cacheLimitBytes] = v }
    suspend fun setNetworkDeath(b: NetworkDeathBehavior) = store.edit {
        it[Keys.deathSkip] = b == NetworkDeathBehavior.SKIP_TO_CACHED
    }
    suspend fun setAccentColor(argb: Long) = store.edit { it[Keys.accentColor] = argb }
    suspend fun setLanguage(lang: String) = store.edit { it[Keys.language] = lang }
}

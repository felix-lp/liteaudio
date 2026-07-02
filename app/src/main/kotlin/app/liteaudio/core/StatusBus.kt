package app.liteaudio.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The app's nervous system: a map of live status slots feeding the status strip.
 * Producers set/clear their slot; the strip renders the highest-severity entry
 * and expands to the full list on tap.
 */
class StatusBus(private val scope: CoroutineScope) {

    sealed interface Key {
        data object Extraction : Key
        data object Playback : Key
        data object Transfer : Key
        data object Network : Key
        data class Error(val id: String) : Key
        data class Toast(val id: Long) : Key
    }

    enum class Severity { Info, Progress, Warning, Error }

    data class Entry(
        val text: Text,
        val severity: Severity = Severity.Info,
        val progress: Float? = null, // 0..1, or null for no bar; NaN for indeterminate
        val indeterminate: Boolean = false,
        /** elapsedRealtime millis of the next retry, for live countdowns */
        val countdownToElapsedRealtime: Long? = null,
        val action: Action? = null,
        val detail: Text? = null,
    )

    data class Action(val label: Text, val run: () -> Unit)

    /** String-resource-friendly text: either literal or res id + args. */
    sealed interface Text {
        data class Literal(val value: String) : Text
        data class Res(val id: Int, val args: List<Any> = emptyList()) : Text
    }

    private val _slots = MutableStateFlow<Map<Key, Entry>>(emptyMap())
    val slots: StateFlow<Map<Key, Entry>> = _slots.asStateFlow()

    fun set(key: Key, entry: Entry) = _slots.update { it + (key to entry) }

    fun clear(key: Key) = _slots.update { it - key }

    fun update(key: Key, transform: (Entry?) -> Entry?) = _slots.update { map ->
        val next = transform(map[key])
        if (next == null) map - key else map + (key to next)
    }

    private var toastCounter = 0L

    /** Transient toast that rides through the strip and disappears. */
    fun toast(text: Text, severity: Severity = Severity.Info, durationMs: Long = 3000) {
        val key = Key.Toast(toastCounter++)
        set(key, Entry(text = text, severity = severity))
        scope.launch {
            delay(durationMs)
            clear(key)
        }
    }

    fun error(id: String, text: Text, detail: Text? = null, action: Action? = null) {
        set(Key.Error(id), Entry(text = text, severity = Severity.Error, detail = detail, action = action))
    }

    fun clearError(id: String) = clear(Key.Error(id))
}

/** Priority for picking the strip's headline entry. */
fun StatusBus.Entry.rank(): Int = when (severity) {
    StatusBus.Severity.Error -> 0
    StatusBus.Severity.Warning -> 1
    StatusBus.Severity.Progress -> 2
    StatusBus.Severity.Info -> 3
}

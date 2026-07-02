package app.liteaudio.media.cache

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * The two-tier byte economy on ONE SimpleCache:
 *
 *  - keys in [pinnedKeys] (explicit downloads) are never evicted and do not
 *    count against the LRU budget;
 *  - everything else is plain LRU bounded by [limitBytes].
 *
 * Callbacks run on the cache's internal threads: plain synchronized state,
 * no coroutines here. The pinned set and limit are mirrored from outside
 * (Room + settings) via [setPinnedKeys]/[setLimitBytes].
 */
@UnstableApi
class PinningCacheEvictor(
    initialLimitBytes: Long,
) : CacheEvictor {

    private val lock = Any()

    private var limitBytes: Long = initialLimitBytes
    private val pinnedKeys = HashSet<String>()

    /** LRU-ordered spans of NON-pinned content only. */
    private val lruSpans = TreeSet<CacheSpan>(::compareByAccess)
    private var lruBytes: Long = 0

    private var cache: Cache? = null

    fun setLimitBytes(newLimit: Long) {
        val c: Cache?
        synchronized(lock) {
            limitBytes = newLimit
            c = cache
        }
        if (c != null) evictWhileOverLimit(c)
    }

    fun setPinnedKeys(keys: Set<String>) {
        val c: Cache?
        synchronized(lock) {
            pinnedKeys.clear()
            pinnedKeys.addAll(keys)
            // Rebuilding lru bookkeeping lazily: spans of newly-unpinned keys
            // re-enter LRU tracking on their next touch. Newly-pinned spans are
            // dropped from the LRU set now so they can't be evicted.
            val it = lruSpans.iterator()
            while (it.hasNext()) {
                val span = it.next()
                if (span.key in pinnedKeys) {
                    it.remove()
                    lruBytes -= span.length
                }
            }
            c = cache
        }
        if (c != null) evictWhileOverLimit(c)
    }

    fun pin(key: String) {
        synchronized(lock) {
            pinnedKeys.add(key)
            val it = lruSpans.iterator()
            while (it.hasNext()) {
                val span = it.next()
                if (span.key == key) {
                    it.remove()
                    lruBytes -= span.length
                }
            }
        }
    }

    fun unpin(key: String, cacheForReindex: Cache?) {
        synchronized(lock) {
            pinnedKeys.remove(key)
        }
        // demote: register the key's existing spans into LRU accounting
        val c = cacheForReindex ?: return
        val spans = runCatching { c.getCachedSpans(key) }.getOrNull() ?: return
        synchronized(lock) {
            for (span in spans) {
                if (lruSpans.add(span)) lruBytes += span.length
            }
        }
        evictWhileOverLimit(c)
    }

    /** Non-pinned bytes currently tracked (the "cache" figure in settings). */
    fun lruBytes(): Long = synchronized(lock) { lruBytes }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        synchronized(lock) { this.cache = cache }
        if (length != androidx.media3.common.C.LENGTH_UNSET.toLong()) {
            evictToFit(cache, key, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            this.cache = cache
            if (span.key !in pinnedKeys) {
                lruSpans.add(span)
                lruBytes += span.length
            }
        }
        evictWhileOverLimit(cache)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            if (lruSpans.remove(span)) lruBytes -= span.length
        }
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    private fun evictToFit(cache: Cache, key: String, incomingLength: Long) {
        val pinned = synchronized(lock) { key in pinnedKeys }
        if (pinned) return
        evict(cache) { currentLruBytes -> currentLruBytes + incomingLength > limitBytesSnapshot() }
    }

    private fun evictWhileOverLimit(cache: Cache) {
        evict(cache) { currentLruBytes -> currentLruBytes > limitBytesSnapshot() }
    }

    private fun limitBytesSnapshot(): Long = synchronized(lock) { limitBytes }

    private inline fun evict(cache: Cache, over: (Long) -> Boolean) {
        while (true) {
            val victim: CacheSpan?
            synchronized(lock) {
                victim = if (over(lruBytes)) lruSpans.firstOrNull() else null
            }
            if (victim == null) break
            runCatching { cache.removeSpan(victim) }
                .onFailure {
                    // drop from bookkeeping to avoid a hot loop on a stuck span
                    synchronized(lock) {
                        if (lruSpans.remove(victim)) lruBytes -= victim.length
                    }
                }
        }
    }

    private companion object {
        fun compareByAccess(a: CacheSpan, b: CacheSpan): Int {
            val byTime = a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
            if (byTime != 0) return byTime
            // tie-break so distinct spans never collapse in the TreeSet
            val byKey = a.key.compareTo(b.key)
            if (byKey != 0) return byKey
            return a.position.compareTo(b.position)
        }
    }
}

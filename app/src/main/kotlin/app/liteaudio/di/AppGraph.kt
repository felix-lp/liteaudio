package app.liteaudio.di

import android.content.Context
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import app.liteaudio.core.NetworkMonitor
import app.liteaudio.core.StatusBus
import app.liteaudio.data.db.LiteDb
import app.liteaudio.data.repo.CacheStatusRepository
import app.liteaudio.data.repo.DownloadRepo
import app.liteaudio.data.repo.PlaylistRepo
import app.liteaudio.data.settings.SettingsRepository
import app.liteaudio.extractor.YtExtractor
import app.liteaudio.media.cache.CacheHolder
import app.liteaudio.media.playback.PlayerController
import app.liteaudio.media.playback.QueuePersistence
import app.liteaudio.media.playback.StallWatcher
import app.liteaudio.media.source.MediaSourceChain
import app.liteaudio.media.source.StreamUrlStore
import app.liteaudio.media.transfer.TransferEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Manual DI container: one instance, constructed in Application.onCreate()
 * on the main thread (ExoPlayer needs that). No framework.
 */
@UnstableApi
class AppGraph(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val statusBus = StatusBus(appScope)
    val networkMonitor = NetworkMonitor(context)
    val settings = SettingsRepository(context, appScope)

    val db = LiteDb.create(context)

    private val baseClient = OkHttpClient.Builder().build()

    val extractor = YtExtractor(baseClient)

    val cacheHolder = CacheHolder(context, settings.settings.value.cacheLimitBytes)

    val urlStore = StreamUrlStore(extractor, db.streamUrlDao(), db.trackDao(), settings)

    val priorityTaskManager = PriorityTaskManager()

    val sourceChain = MediaSourceChain(
        baseClient = baseClient,
        cacheHolder = cacheHolder,
        urlStore = urlStore,
        statusBus = statusBus,
        priorityTaskManager = priorityTaskManager,
    )

    val playerController = PlayerController(context, sourceChain, statusBus, appScope)

    val cacheStatus = CacheStatusRepository(cacheHolder, db.trackDao())

    val playlistRepo = PlaylistRepo(db, extractor, statusBus, appScope)

    val downloadRepo = DownloadRepo(db.downloadDao(), db.trackDao(), cacheHolder)

    val queuePersistence = QueuePersistence(playerController, db.queueDao(), db.trackDao(), appScope)

    val stallWatcher = StallWatcher(
        controller = playerController,
        cacheStatus = cacheStatus,
        trackDao = db.trackDao(),
        settings = settings,
        statusBus = statusBus,
        scope = appScope,
    )

    val transferEngine = TransferEngine(
        context = context.applicationContext,
        chain = sourceChain,
        cacheHolder = cacheHolder,
        urlStore = urlStore,
        trackDao = db.trackDao(),
        downloadDao = db.downloadDao(),
        controller = playerController,
        settings = settings,
        statusBus = statusBus,
        scope = appScope,
    )

    init {
        // mirror pinned download keys into the evictor
        appScope.launch {
            downloadRepo.observePinKeys().collectLatest { keys ->
                cacheHolder.evictor.setPinnedKeys(keys.toSet())
            }
        }
        // mirror the configurable LRU limit
        appScope.launch {
            settings.settings.map { it.cacheLimitBytes }.distinctUntilChanged().collectLatest {
                cacheHolder.evictor.setLimitBytes(it)
            }
        }
        // restore the persisted play queue, then start persisting changes
        appScope.launch {
            queuePersistence.restore()
            queuePersistence.start()
        }
        transferEngine.start()
    }
}

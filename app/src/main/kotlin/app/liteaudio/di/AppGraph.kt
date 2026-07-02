package app.liteaudio.di

import android.content.Context
import app.liteaudio.core.NetworkMonitor
import app.liteaudio.core.StatusBus
import app.liteaudio.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI container: one instance, constructed in Application.onCreate().
 * Everything is constructor-injected from here; no framework.
 */
class AppGraph(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val statusBus = StatusBus(appScope)
    val networkMonitor = NetworkMonitor(context)
    val settings = SettingsRepository(context, appScope)
}

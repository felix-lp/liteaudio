package app.liteaudio.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.liteaudio.R
import app.liteaudio.di.AppGraph
import app.liteaudio.ui.design.components.LiteIcons
import app.liteaudio.ui.design.theme.Lite
import app.liteaudio.ui.downloads.DownloadsScreen
import app.liteaudio.ui.library.LibraryScreen
import app.liteaudio.ui.settings.SettingsScreen
import app.liteaudio.ui.status.StatusStrip

object Routes {
    const val LIBRARY = "library"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val PLAYLIST = "playlist/{playlistId}"
    fun playlist(id: Long) = "playlist/$id"
}

@Composable
fun AppRoot(graph: AppGraph) {
    val navController: NavHostController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val slots by graph.statusBus.slots.collectAsStateWithLifecycle()
    val network by graph.networkMonitor.state.collectAsStateWithLifecycle()

    val tabs = listOf(
        TabSpec(Routes.LIBRARY, LiteIcons.Library, stringResource(R.string.tab_library)),
        TabSpec(Routes.DOWNLOADS, LiteIcons.Download, stringResource(R.string.tab_downloads)),
        TabSpec(Routes.SETTINGS, LiteIcons.Settings, stringResource(R.string.tab_settings)),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Lite.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        StatusStrip(slots = slots, network = network)

        Box(Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Routes.LIBRARY,
            ) {
                composable(Routes.LIBRARY) { LibraryScreen(graph, navController) }
                composable(Routes.DOWNLOADS) { DownloadsScreen(graph) }
                composable(Routes.SETTINGS) { SettingsScreen(graph) }
            }
        }

        // Mini-player mounts here once playback lands (M4)

        TabBar(
            tabs = tabs,
            activeRoute = currentRoute,
            onSelect = { route ->
                if (route != currentRoute) {
                    navController.navigate(route) {
                        popUpTo(Routes.LIBRARY) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
        )
    }
}

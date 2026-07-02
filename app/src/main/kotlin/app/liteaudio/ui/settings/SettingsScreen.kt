package app.liteaudio.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.liteaudio.R
import app.liteaudio.di.AppGraph
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.theme.Lite

@Composable
fun SettingsScreen(graph: AppGraph) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LiteText(
            text = stringResource(R.string.tab_settings),
            style = Lite.type.secondary,
            color = Lite.colors.textSecondary,
        )
    }
}

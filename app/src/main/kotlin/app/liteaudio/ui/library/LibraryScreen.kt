package app.liteaudio.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.liteaudio.R
import app.liteaudio.di.AppGraph
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.theme.Lite

@Composable
fun LibraryScreen(graph: AppGraph, navController: NavHostController) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LiteText(
            text = stringResource(R.string.library_empty_hint),
            style = Lite.type.secondary,
            color = Lite.colors.textSecondary,
            maxLines = 3,
        )
    }
}

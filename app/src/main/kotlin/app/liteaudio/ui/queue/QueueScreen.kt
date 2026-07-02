package app.liteaudio.ui.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.liteaudio.R
import app.liteaudio.data.db.QueueItemSource
import app.liteaudio.di.AppGraph
import app.liteaudio.ui.design.components.LiteDivider
import app.liteaudio.ui.design.components.LiteIconButton
import app.liteaudio.ui.design.components.LiteIcons
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.TrackRow
import app.liteaudio.ui.design.theme.Lite

/** Spotify-style queue: now playing / manual block / context block. */
@Composable
fun QueueScreen(graph: AppGraph, navController: NavHostController) {
    val state by graph.playerController.state.collectAsState()
    val playlists by graph.playlistRepo.observePlaylists().collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Lite.dimens.spacing3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiteIconButton(icon = LiteIcons.ChevronLeft, onClick = { navController.popBackStack() })
            LiteText(stringResource(R.string.player_queue), style = Lite.type.title)
        }
        LiteDivider()

        if (state.queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LiteText(
                    stringResource(R.string.queue_empty),
                    style = Lite.type.secondary,
                    color = Lite.colors.textSecondary,
                )
            }
            return@Column
        }

        val current = state.currentIndex
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.queue, key = { i, item -> "$i:${item.videoId}" }) { index, item ->
                Column {
                // section headers
                if (index == current) {
                    Header(stringResource(R.string.queue_now_playing))
                } else if (index == current + 1) {
                    if (item.source != QueueItemSource.PLAYLIST) {
                        Header(stringResource(R.string.queue_next_manual))
                    } else {
                        val name = playlists.firstOrNull { it.id == item.sourcePlaylistId }?.title ?: ""
                        Header(stringResource(R.string.queue_next_from, name))
                    }
                } else if (index > current + 1 &&
                    item.source == QueueItemSource.PLAYLIST &&
                    state.queue[index - 1].source != QueueItemSource.PLAYLIST
                ) {
                    val name = playlists.firstOrNull { it.id == item.sourcePlaylistId }?.title ?: ""
                    Header(stringResource(R.string.queue_next_from, name))
                }

                val status by graph.cacheStatus.observe(item.videoId).collectAsState(initial = null)
                TrackRow(
                    title = item.title,
                    subtitle = item.uploader,
                    thumbnailUrl = item.thumbnailUrl,
                    cacheStatus = status,
                    pinned = false,
                    isCurrent = index == current,
                    unavailable = false,
                    onClick = { graph.playerController.skipTo(index) },
                    onLongClick = {},
                    trailing = {
                        if (index != current) {
                            LiteIconButton(
                                icon = LiteIcons.Close,
                                onClick = { graph.playerController.removeAt(index) },
                                size = 30.dp,
                                iconSize = 14.dp,
                            )
                        }
                    },
                )
                }
            }
            item { Spacer(Modifier.height(Lite.dimens.spacing4)) }
        }
    }
}

@Composable
private fun Header(text: String) {
    LiteText(
        text = text,
        style = Lite.type.caption,
        color = Lite.colors.textSecondary,
        modifier = Modifier.padding(
            start = Lite.dimens.spacing4,
            top = Lite.dimens.spacing3,
            bottom = Lite.dimens.spacing1,
        ),
    )
}

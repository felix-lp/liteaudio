package app.liteaudio.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.liteaudio.R
import app.liteaudio.data.db.DownloadState
import app.liteaudio.data.db.PlaylistType
import app.liteaudio.data.db.QueueItemSource
import app.liteaudio.data.db.TrackEntity
import app.liteaudio.data.repo.CacheStatus
import app.liteaudio.di.AppGraph
import app.liteaudio.media.playback.PlayerController
import app.liteaudio.ui.design.components.LiteActionSheet
import app.liteaudio.ui.design.components.LiteDivider
import app.liteaudio.ui.design.components.LiteIconButton
import app.liteaudio.ui.design.components.LiteIcons
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.MenuRow
import app.liteaudio.ui.design.components.PressableButton
import app.liteaudio.ui.design.components.TrackRow
import app.liteaudio.ui.design.components.formatDuration
import app.liteaudio.ui.design.theme.Lite
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(graph: AppGraph, navController: NavHostController, playlistId: Long) {
    val playlistFlow = remember(playlistId) { graph.playlistRepo.observePlaylist(playlistId) }
    val playlist by playlistFlow.collectAsState(initial = null)
    val rowsFlow = remember(playlistId) { graph.playlistRepo.observeTracks(playlistId) }
    val rows by rowsFlow.collectAsState(initial = emptyList())
    val downloadsFlow = remember { graph.downloadRepo.observeAll() }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val playerState by graph.playerController.state.collectAsState()
    val scope = rememberCoroutineScope()

    var trackMenu by remember { mutableStateOf<TrackEntity?>(null) }
    var addToPlaylist by remember { mutableStateOf<TrackEntity?>(null) }

    val downloadedIds = remember(downloads) {
        downloads.filter { it.state == DownloadState.DONE }.map { it.videoId }.toSet()
    }

    val p = playlist ?: return

    fun toQueueItem(t: TrackEntity) = PlayerController.QueueItem(
        videoId = t.videoId,
        title = t.title,
        uploader = t.uploader,
        thumbnailUrl = t.thumbnailUrl,
        source = QueueItemSource.PLAYLIST,
        sourcePlaylistId = playlistId,
    )

    fun playFrom(index: Int, shuffled: Boolean = false) {
        graph.playerController.playContext(
            tracks = rows.map { toQueueItem(it.track) },
            startIndex = index,
            shuffled = shuffled,
        )
    }

    Column(Modifier.fillMaxSize()) {
        // compact header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Lite.dimens.spacing3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiteIconButton(icon = LiteIcons.ChevronLeft, onClick = { navController.popBackStack() }, iconSize = 18.dp)
            AsyncImage(
                model = p.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Lite.colors.surfacePressed),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Spacer(Modifier.width(Lite.dimens.spacing3))
            Column(Modifier.weight(1f)) {
                LiteText(p.title, style = Lite.type.heading, maxLines = 2)
                LiteText(
                    text = p.uploader ?: "",
                    style = Lite.type.caption,
                    color = Lite.colors.textTertiary,
                )
                LiteText(
                    text = stringResource(
                        R.string.playlist_stats,
                        rows.size,
                        rows.count { graph.cacheStatus.statusOf(it.track) is CacheStatus.Full },
                        rows.count { it.track.videoId in downloadedIds },
                    ),
                    style = Lite.type.caption,
                    color = Lite.colors.textTertiary,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Lite.dimens.spacing3, vertical = Lite.dimens.spacing1),
        ) {
            PressableButton(
                text = stringResource(R.string.playlist_play),
                onClick = { playFrom(0) },
                accent = true,
                icon = LiteIcons.Play,
                compact = true,
            )
            Spacer(Modifier.width(Lite.dimens.spacing2))
            PressableButton(
                text = stringResource(R.string.playlist_shuffle),
                onClick = { playFrom(0, shuffled = true) },
                icon = LiteIcons.Shuffle,
                compact = true,
            )
            Spacer(Modifier.width(Lite.dimens.spacing2))
            PressableButton(
                text = stringResource(R.string.playlist_action_download_all),
                onClick = {
                    scope.launch { graph.downloadRepo.enqueue(rows.map { it.track.videoId }) }
                },
                icon = LiteIcons.Download,
                compact = true,
            )
        }
        LiteDivider()

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, r -> r.track.videoId }) { index, row ->
                val track = row.track
                val statusFlow = remember(track.videoId) { graph.cacheStatus.observe(track.videoId) }
                val status by statusFlow.collectAsState(initial = null)
                TrackRow(
                    title = track.title,
                    subtitle = "${track.uploader} · ${formatDuration(track.durationSec)}",
                    thumbnailUrl = track.thumbnailUrl,
                    cacheStatus = status,
                    pinned = track.videoId in downloadedIds,
                    isCurrent = playerState.currentVideoId == track.videoId,
                    unavailable = track.unavailableReason != null,
                    onClick = { playFrom(index) },
                    onLongClick = { trackMenu = track },
                    leading = {
                        LiteText(
                            text = (index + 1).toString(),
                            style = Lite.type.mono,
                            color = Lite.colors.textTertiary,
                            modifier = Modifier.width(24.dp),
                        )
                    },
                )
            }
            item { Spacer(Modifier.height(Lite.dimens.spacing4)) }
        }
    }

    trackMenu?.let { track ->
        LiteActionSheet(title = track.title, onDismiss = { trackMenu = null }) {
            MenuRow(stringResource(R.string.track_action_play_next), icon = LiteIcons.Next, onClick = {
                graph.playerController.playNext(toQueueItem(track).copy(source = QueueItemSource.USER_NEXT))
                graph.statusBus.toast(app.liteaudio.core.StatusBus.Text.Res(R.string.status_added_to_queue))
                trackMenu = null
            })
            MenuRow(stringResource(R.string.track_action_enqueue), icon = LiteIcons.Queue, onClick = {
                graph.playerController.enqueue(toQueueItem(track).copy(source = QueueItemSource.USER_LAST))
                graph.statusBus.toast(app.liteaudio.core.StatusBus.Text.Res(R.string.status_added_to_queue))
                trackMenu = null
            })
            MenuRow(stringResource(R.string.track_action_download), icon = LiteIcons.Download, onClick = {
                scope.launch { graph.downloadRepo.enqueue(listOf(track.videoId)) }
                trackMenu = null
            })
            MenuRow(stringResource(R.string.track_action_add_to_playlist), icon = LiteIcons.PlaylistAdd, onClick = {
                addToPlaylist = track
                trackMenu = null
            })
            if (p.type == PlaylistType.LOCAL) {
                MenuRow(stringResource(R.string.track_action_remove_from_playlist), icon = LiteIcons.Close, onClick = {
                    scope.launch { graph.playlistRepo.removeTrack(playlistId, track.videoId) }
                    trackMenu = null
                })
            }
            MenuRow(stringResource(R.string.track_action_remove_from_cache), icon = LiteIcons.Trash, destructive = true, onClick = {
                scope.launch { graph.downloadRepo.deleteWithBytes(track.videoId) }
                trackMenu = null
            })
        }
    }

    addToPlaylist?.let { track ->
        PickLocalPlaylistDialog(
            graph = graph,
            onDismiss = { addToPlaylist = null },
            onPicked = { targetId ->
                scope.launch { graph.playlistRepo.appendToLocal(targetId, track.videoId) }
                graph.statusBus.toast(app.liteaudio.core.StatusBus.Text.Res(R.string.status_added_to_playlist))
                addToPlaylist = null
            },
        )
    }
}

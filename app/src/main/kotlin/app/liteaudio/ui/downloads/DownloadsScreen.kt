package app.liteaudio.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.liteaudio.R
import app.liteaudio.data.db.DownloadEntity
import app.liteaudio.data.db.DownloadState
import app.liteaudio.data.db.TrackEntity
import app.liteaudio.di.AppGraph
import app.liteaudio.ui.design.components.LiteActionSheet
import app.liteaudio.ui.design.components.LiteDivider
import app.liteaudio.ui.design.components.LiteIconButton
import app.liteaudio.ui.design.components.LiteIcons
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.MenuRow
import app.liteaudio.ui.design.components.PressableButton
import app.liteaudio.ui.design.components.ProgressStrip
import app.liteaudio.ui.design.components.formatBytes
import app.liteaudio.ui.design.theme.Lite
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Mihon-style download queue: one active task, visible order, honest states. */
@Composable
fun DownloadsScreen(graph: AppGraph) {
    val queueFlow = remember { graph.downloadRepo.observeQueue() }
    val queue by queueFlow.collectAsState(initial = emptyList())
    val doneFlow = remember { graph.downloadRepo.observeDone() }
    val done by doneFlow.collectAsState(initial = emptyList())
    val playerBusyFlow = remember { graph.playerController.state.map { it.isBuffering } }
    val playerBusy by playerBusyFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    var menuTarget by remember { mutableStateOf<DownloadEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Lite.dimens.spacing4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiteText(stringResource(R.string.tab_downloads), style = Lite.type.title)
            Spacer(Modifier.weight(1f))
            if (queue.isNotEmpty()) {
                val anyActive = queue.any { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }
                PressableButton(
                    text = stringResource(
                        if (anyActive) R.string.downloads_pause_all else R.string.downloads_resume_all,
                    ),
                    onClick = {
                        scope.launch {
                            if (anyActive) graph.downloadRepo.pauseAll() else graph.downloadRepo.resumeAll()
                        }
                    },
                    compact = true,
                )
            }
        }

        // summary line
        if (queue.isNotEmpty()) {
            val doneBytes = queue.sumOf { it.bytesDone }
            val totalBytes = queue.sumOf { it.bytesTotal ?: 0 }
            LiteText(
                text = stringResource(
                    R.string.downloads_progress_summary,
                    queue.count { it.state == DownloadState.RUNNING || it.bytesDone > 0 },
                    queue.size,
                    formatBytes(doneBytes),
                    if (totalBytes > 0) formatBytes(totalBytes) else "?",
                ),
                style = Lite.type.caption,
                color = Lite.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Lite.dimens.spacing4),
            )
            Spacer(Modifier.height(Lite.dimens.spacing2))
        }
        LiteDivider()

        if (queue.isEmpty() && done.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LiteText(
                    stringResource(R.string.downloads_empty),
                    style = Lite.type.secondary,
                    color = Lite.colors.textSecondary,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (queue.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.downloads_section_queue)) }
                items(queue, key = { it.videoId }) { entry ->
                    DownloadRow(
                        graph = graph,
                        entry = entry,
                        playerBusy = playerBusy,
                        onMenu = { menuTarget = entry },
                    )
                }
            }
            if (done.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.downloads_section_done)) }
                items(done, key = { it.videoId }) { entry ->
                    DownloadRow(
                        graph = graph,
                        entry = entry,
                        playerBusy = false,
                        onMenu = { menuTarget = entry },
                    )
                }
            }
            item { Spacer(Modifier.height(Lite.dimens.spacing4)) }
        }
    }

    menuTarget?.let { entry ->
        LiteActionSheet(title = entry.videoId, onDismiss = { menuTarget = null }) {
            when (entry.state) {
                DownloadState.PAUSED, DownloadState.ERROR -> {
                    MenuRow(stringResource(R.string.action_retry), icon = LiteIcons.Refresh, onClick = {
                        scope.launch { graph.downloadRepo.retry(entry.videoId) }
                        menuTarget = null
                    })
                }
                DownloadState.QUEUED, DownloadState.RUNNING -> {
                    MenuRow(stringResource(R.string.action_pause), icon = LiteIcons.Pause, onClick = {
                        scope.launch { graph.downloadRepo.pause(entry.videoId) }
                        menuTarget = null
                    })
                }
                DownloadState.DONE -> Unit
            }
            MenuRow(
                stringResource(
                    if (entry.state == DownloadState.DONE) R.string.track_action_remove_download
                    else R.string.action_delete,
                ),
                icon = LiteIcons.Trash,
                destructive = true,
                onClick = {
                    scope.launch { graph.downloadRepo.deleteWithBytes(entry.videoId) }
                    menuTarget = null
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
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

@Composable
private fun DownloadRow(
    graph: AppGraph,
    entry: DownloadEntity,
    playerBusy: Boolean,
    onMenu: () -> Unit,
) {
    val colors = Lite.colors
    val trackFlow = remember(entry.videoId) { graph.db.trackDao().observe(entry.videoId) }
    val track by trackFlow.collectAsState(initial = null)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Lite.dimens.spacing4, vertical = Lite.dimens.spacing2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LiteText(track?.title ?: entry.videoId, style = Lite.type.body)
                val stateLabel = when {
                    entry.state == DownloadState.RUNNING && playerBusy ->
                        stringResource(R.string.download_state_waiting_channel)
                    entry.state == DownloadState.RUNNING -> stringResource(R.string.download_state_running)
                    entry.state == DownloadState.QUEUED -> stringResource(R.string.download_state_queued)
                    entry.state == DownloadState.PAUSED -> stringResource(R.string.download_state_paused)
                    entry.state == DownloadState.ERROR ->
                        "${stringResource(R.string.download_state_error)}${entry.errorType?.let { " · $it" } ?: ""}"
                    else -> formatBytes(entry.bytesDone)
                }
                LiteText(
                    text = stateLabel,
                    style = Lite.type.caption,
                    color = when (entry.state) {
                        DownloadState.ERROR -> colors.error
                        DownloadState.RUNNING -> colors.accent
                        else -> colors.textTertiary
                    },
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Spacer(Modifier.width(Lite.dimens.spacing2))
            if (entry.state != DownloadState.DONE) {
                LiteText(
                    text = "${formatBytes(entry.bytesDone)}${entry.bytesTotal?.let { " / ${formatBytes(it)}" } ?: ""}",
                    style = Lite.type.mono,
                    color = colors.textTertiary,
                )
            }
            LiteIconButton(icon = LiteIcons.More, onClick = onMenu, size = 30.dp, iconSize = 14.dp)
        }
        if (entry.state == DownloadState.RUNNING || (entry.state != DownloadState.DONE && entry.bytesDone > 0)) {
            Spacer(Modifier.height(4.dp))
            val fraction = entry.bytesTotal?.takeIf { it > 0 }?.let {
                entry.bytesDone.toFloat() / it
            }
            ProgressStrip(
                progress = fraction,
                height = 3.dp,
                color = if (entry.state == DownloadState.ERROR) colors.error else colors.accent,
            )
        }
    }
}

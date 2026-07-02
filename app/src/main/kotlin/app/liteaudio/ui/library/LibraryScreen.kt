package app.liteaudio.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.liteaudio.R
import app.liteaudio.data.db.PlaylistEntity
import app.liteaudio.data.db.PlaylistType
import app.liteaudio.di.AppGraph
import app.liteaudio.extractor.ParsedUrl
import app.liteaudio.ui.design.components.DialogButtons
import app.liteaudio.ui.design.components.LiteActionSheet
import app.liteaudio.ui.design.components.LiteDialog
import app.liteaudio.ui.design.components.LiteDivider
import app.liteaudio.ui.design.components.LiteIconButton
import app.liteaudio.ui.design.components.LiteIcons
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.LiteTextField
import app.liteaudio.ui.design.components.MenuRow
import app.liteaudio.ui.design.components.liteClickable
import app.liteaudio.ui.design.theme.Lite
import app.liteaudio.ui.nav.Routes
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(graph: AppGraph, navController: NavHostController) {
    val playlistsFlow = remember { graph.playlistRepo.observePlaylists() }
    val playlists by playlistsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showAddMenu by remember { mutableStateOf(false) }
    var showAddUrl by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingVideoId by remember { mutableStateOf<String?>(null) }
    var contextTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renameTarget by remember { mutableStateOf<PlaylistEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Lite.dimens.spacing4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiteText(stringResource(R.string.tab_library), style = Lite.type.title)
            Spacer(Modifier.weight(1f))
            LiteIconButton(icon = LiteIcons.Add, onClick = { showAddMenu = true }, raised = true)
        }
        LiteDivider()

        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(Lite.dimens.spacing4), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LiteText(
                        stringResource(R.string.library_empty_hint),
                        style = Lite.type.secondary,
                        color = Lite.colors.textSecondary,
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(Lite.dimens.spacing3))
                    app.liteaudio.ui.design.components.PressableButton(
                        text = stringResource(R.string.library_add_by_url),
                        onClick = { showAddUrl = true },
                        accent = true,
                        icon = LiteIcons.Link,
                    )
                }
            }
        } else {
            val youtube = playlists.filter { it.type == PlaylistType.YOUTUBE }
            val local = playlists.filter { it.type == PlaylistType.LOCAL }
            LazyColumn(Modifier.fillMaxSize()) {
                if (youtube.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.library_section_youtube)) }
                    items(youtube, key = { it.id }) { p ->
                        PlaylistRow(
                            playlist = p,
                            onClick = { navController.navigate(Routes.playlist(p.id)) },
                            onLongClick = { contextTarget = p },
                        )
                    }
                }
                if (local.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.library_section_local)) }
                    items(local, key = { it.id }) { p ->
                        PlaylistRow(
                            playlist = p,
                            onClick = { navController.navigate(Routes.playlist(p.id)) },
                            onLongClick = { contextTarget = p },
                        )
                    }
                }
            }
        }
    }

    // ---- dialogs ----

    if (showAddMenu) {
        LiteActionSheet(title = null, onDismiss = { showAddMenu = false }) {
            MenuRow(stringResource(R.string.library_add_by_url), icon = LiteIcons.Link, onClick = {
                showAddMenu = false
                showAddUrl = true
            })
            MenuRow(stringResource(R.string.library_create_playlist), icon = LiteIcons.PlaylistAdd, onClick = {
                showAddMenu = false
                showCreate = true
            })
        }
    }

    if (showAddUrl) {
        AddByUrlDialog(
            graph = graph,
            onDismiss = { showAddUrl = false },
            onVideo = { videoId ->
                showAddUrl = false
                pendingVideoId = videoId
            },
        )
    }

    pendingVideoId?.let { videoId ->
        PickLocalPlaylistDialog(
            graph = graph,
            onDismiss = { pendingVideoId = null },
            onPicked = { playlistId ->
                graph.playlistRepo.addVideoToLocalPlaylist(videoId, playlistId)
                pendingVideoId = null
            },
        )
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                scope.launch { graph.playlistRepo.createLocalPlaylist(name) }
                showCreate = false
            },
        )
    }

    contextTarget?.let { p ->
        LiteActionSheet(title = p.title, onDismiss = { contextTarget = null }) {
            MenuRow(stringResource(R.string.playlist_action_download_all), icon = LiteIcons.Download, onClick = {
                contextTarget = null
                scope.launch {
                    val ids = graph.playlistRepo.tracksOf(p.id).map { it.track.videoId }
                    graph.downloadRepo.enqueue(ids)
                }
            })
            if (p.type == PlaylistType.YOUTUBE) {
                MenuRow(stringResource(R.string.playlist_action_refresh), icon = LiteIcons.Refresh, onClick = {
                    contextTarget = null
                    graph.playlistRepo.refresh(p.id)
                })
            }
            if (p.type == PlaylistType.LOCAL) {
                MenuRow(stringResource(R.string.playlist_action_rename), icon = LiteIcons.More, onClick = {
                    contextTarget = null
                    renameTarget = p
                })
            }
            MenuRow(stringResource(R.string.playlist_action_delete), icon = LiteIcons.Trash, destructive = true, onClick = {
                contextTarget = null
                deleteTarget = p
            })
        }
    }

    deleteTarget?.let { p ->
        LiteDialog(title = stringResource(R.string.playlist_delete_confirm_title), onDismiss = { deleteTarget = null }) {
            LiteText(p.title, style = Lite.type.secondary, color = Lite.colors.textSecondary)
            Spacer(Modifier.height(Lite.dimens.spacing3))
            MenuRow(stringResource(R.string.playlist_delete_keep_files), onClick = {
                scope.launch { graph.playlistRepo.delete(p.id) }
                deleteTarget = null
            })
            MenuRow(stringResource(R.string.playlist_delete_with_files), destructive = true, onClick = {
                scope.launch {
                    val tracks = graph.playlistRepo.tracksOf(p.id)
                    graph.playlistRepo.delete(p.id)
                    tracks.forEach { graph.downloadRepo.deleteWithBytes(it.track.videoId) }
                }
                deleteTarget = null
            })
            MenuRow(stringResource(R.string.action_cancel), onClick = { deleteTarget = null })
        }
    }

    renameTarget?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.title) }
        LiteDialog(title = stringResource(R.string.playlist_action_rename), onDismiss = { renameTarget = null }) {
            LiteTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Lite.dimens.spacing3))
            DialogButtons(
                confirmLabel = stringResource(R.string.action_rename),
                onConfirm = {
                    scope.launch { graph.playlistRepo.rename(p.id, name.trim()) }
                    renameTarget = null
                },
                cancelLabel = stringResource(R.string.action_cancel),
                onCancel = { renameTarget = null },
                confirmEnabled = name.isNotBlank(),
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
private fun PlaylistRow(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = Lite.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(Lite.dimens.playlistRowHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Lite.dimens.spacing4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = playlist.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(Lite.dimens.thumbMedium)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfacePressed),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(Modifier.width(Lite.dimens.spacing3))
        Column(Modifier.weight(1f)) {
            LiteText(playlist.title, style = Lite.type.body)
            val subtitle = buildString {
                playlist.uploader?.let { append(it).append(" · ") }
                append(stringResource(R.string.library_tracks_count, playlist.trackCount))
            }
            LiteText(
                subtitle,
                style = Lite.type.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        LiteIconButton(icon = LiteIcons.More, onClick = onLongClick, size = 32.dp, iconSize = 16.dp)
    }
}

@Composable
private fun AddByUrlDialog(
    graph: AppGraph,
    onDismiss: () -> Unit,
    onVideo: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var url by remember {
        val clip = clipboard.getText()?.text ?: ""
        mutableStateOf(if (graph.playlistRepo.parseUrl(clip) != ParsedUrl.Invalid) clip else "")
    }
    var invalid by remember { mutableStateOf(false) }

    LiteDialog(title = stringResource(R.string.add_url_title), onDismiss = onDismiss) {
        LiteTextField(
            value = url,
            onValueChange = {
                url = it
                invalid = false
            },
            hint = stringResource(R.string.add_url_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        if (invalid) {
            Spacer(Modifier.height(Lite.dimens.spacing1))
            LiteText(
                stringResource(R.string.add_url_invalid),
                style = Lite.type.caption,
                color = Lite.colors.error,
            )
        }
        Spacer(Modifier.height(Lite.dimens.spacing3))
        DialogButtons(
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = {
                when (val parsed = graph.playlistRepo.parseUrl(url)) {
                    is ParsedUrl.Playlist -> {
                        graph.playlistRepo.addYoutubePlaylist(parsed.playlistUrl)
                        onDismiss()
                    }
                    is ParsedUrl.Video -> onVideo(parsed.videoId)
                    ParsedUrl.Invalid -> invalid = true
                }
            },
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDismiss,
            confirmEnabled = url.isNotBlank(),
        )
    }
}

@Composable
fun PickLocalPlaylistDialog(
    graph: AppGraph,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    val playlistsFlow = remember { graph.playlistRepo.observePlaylists() }
    val playlists by playlistsFlow.collectAsState(initial = emptyList())
    val local = playlists.filter { it.type == PlaylistType.LOCAL }
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }

    LiteDialog(title = stringResource(R.string.add_video_pick_playlist), onDismiss = onDismiss) {
        local.forEach { p ->
            MenuRow(p.title, onClick = { onPicked(p.id) })
        }
        if (local.isNotEmpty()) LiteDivider()
        Spacer(Modifier.height(Lite.dimens.spacing2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiteTextField(
                value = newName,
                onValueChange = { newName = it },
                hint = stringResource(R.string.new_playlist_name_hint),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Lite.dimens.spacing2))
            app.liteaudio.ui.design.components.PressableButton(
                text = stringResource(R.string.action_create),
                onClick = {
                    scope.launch {
                        val id = graph.playlistRepo.createLocalPlaylist(newName.trim())
                        onPicked(id)
                    }
                },
                accent = true,
                enabled = newName.isNotBlank(),
                compact = true,
            )
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    LiteDialog(title = stringResource(R.string.library_create_playlist), onDismiss = onDismiss) {
        LiteTextField(
            value = name,
            onValueChange = { name = it },
            hint = stringResource(R.string.new_playlist_name_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Lite.dimens.spacing3))
        DialogButtons(
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { onCreate(name.trim()) },
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDismiss,
            confirmEnabled = name.isNotBlank(),
        )
    }
}

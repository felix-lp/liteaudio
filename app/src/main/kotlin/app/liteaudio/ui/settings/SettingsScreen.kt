package app.liteaudio.ui.settings

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.liteaudio.BuildConfig
import app.liteaudio.R
import app.liteaudio.data.settings.AppSettings
import app.liteaudio.data.settings.AudioFormat
import app.liteaudio.data.settings.NetworkDeathBehavior
import app.liteaudio.di.AppGraph
import app.liteaudio.ui.design.components.DialogButtons
import app.liteaudio.ui.design.components.LiteDialog
import app.liteaudio.ui.design.components.LiteDivider
import app.liteaudio.ui.design.components.LiteSlider
import app.liteaudio.ui.design.components.LiteSwitch
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.MenuRow
import app.liteaudio.ui.design.components.formatBytes
import app.liteaudio.ui.design.components.liteClickable
import app.liteaudio.ui.design.theme.Lite
import kotlinx.coroutines.launch

private val ACCENT_PALETTE = listOf(
    0xFFFF7A00, // Walkman orange (default)
    0xFF5EB5F7, // Telegram blue
    0xFF46A758, // green
    0xFFE5484D, // red
    0xFFB18CFF, // violet
    0xFFF7C325, // amber
    0xFF2BC8C8, // teal
)

@Composable
fun SettingsScreen(graph: AppGraph) {
    val settings by graph.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var pickStreamFormat by remember { mutableStateOf(false) }
    var pickDownloadFormat by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Lite.dimens.spacing4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiteText(stringResource(R.string.tab_settings), style = Lite.type.title)
        }
        LiteDivider()

        // ---- Network & quality ----
        GroupHeader(stringResource(R.string.settings_group_network))
        SettingRow(
            title = stringResource(R.string.settings_stream_format),
            value = settings.streamFormat.describe(),
            onClick = { pickStreamFormat = true },
        )
        SettingRow(
            title = stringResource(R.string.settings_download_format),
            value = settings.downloadFormat.describe(),
            onClick = { pickDownloadFormat = true },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Lite.dimens.spacing4, vertical = Lite.dimens.spacing2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                LiteText(stringResource(R.string.settings_on_network_death), style = Lite.type.body)
                LiteText(
                    text = stringResource(
                        if (settings.onNetworkDeath == NetworkDeathBehavior.SKIP_TO_CACHED) {
                            R.string.settings_death_skip
                        } else R.string.settings_death_wait,
                    ),
                    style = Lite.type.caption,
                    color = Lite.colors.textTertiary,
                )
            }
            LiteSwitch(
                checked = settings.onNetworkDeath == NetworkDeathBehavior.SKIP_TO_CACHED,
                onCheckedChange = { skip ->
                    scope.launch {
                        graph.settings.setNetworkDeath(
                            if (skip) NetworkDeathBehavior.SKIP_TO_CACHED else NetworkDeathBehavior.WAIT_FOREVER,
                        )
                    }
                },
            )
        }
        PrefetchRow(graph, settings)
        LiteDivider()

        // ---- Storage ----
        GroupHeader(stringResource(R.string.settings_group_storage))
        CacheLimitRow(graph, settings)
        val lru = remember(settings) { graph.cacheStatus.lruBytes() }
        val total = remember(settings) { graph.cacheStatus.totalBytes() }
        LiteText(
            text = stringResource(
                R.string.settings_storage_usage,
                formatBytes(lru),
                formatBytes((total - lru).coerceAtLeast(0)),
            ),
            style = Lite.type.caption,
            color = Lite.colors.textTertiary,
            modifier = Modifier.padding(horizontal = Lite.dimens.spacing4),
        )
        SettingRow(
            title = stringResource(R.string.settings_clear_cache),
            value = "",
            onClick = { confirmClearCache = true },
        )
        LiteDivider()

        // ---- Appearance ----
        GroupHeader(stringResource(R.string.settings_group_appearance))
        LiteText(
            stringResource(R.string.settings_accent),
            style = Lite.type.body,
            modifier = Modifier.padding(horizontal = Lite.dimens.spacing4, vertical = Lite.dimens.spacing1),
        )
        Row(Modifier.padding(horizontal = Lite.dimens.spacing4, vertical = Lite.dimens.spacing2)) {
            ACCENT_PALETTE.forEach { argb ->
                val selected = settings.accentColor == argb
                Box(
                    Modifier
                        .padding(end = Lite.dimens.spacing2)
                        .size(if (selected) 30.dp else 26.dp)
                        .background(Color(argb), CircleShape)
                        .then(
                            if (selected) {
                                Modifier.background(Color.White.copy(alpha = 0.25f), CircleShape)
                            } else Modifier,
                        )
                        .liteClickable {
                            scope.launch { graph.settings.setAccentColor(argb) }
                        },
                )
            }
        }
        LanguageRow(graph, settings)
        LiteDivider()

        // ---- About ----
        GroupHeader(stringResource(R.string.settings_group_about))
        InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
        InfoRow(stringResource(R.string.settings_extractor_version), BuildConfig.NEWPIPE_VERSION)
        Spacer(Modifier.height(Lite.dimens.spacing4))
    }

    if (pickStreamFormat) {
        FormatPicker(
            title = stringResource(R.string.settings_stream_format),
            selected = settings.streamFormat,
            onPick = {
                scope.launch { graph.settings.setStreamFormat(it) }
                pickStreamFormat = false
            },
            onDismiss = { pickStreamFormat = false },
        )
    }
    if (pickDownloadFormat) {
        FormatPicker(
            title = stringResource(R.string.settings_download_format),
            selected = settings.downloadFormat,
            onPick = {
                scope.launch { graph.settings.setDownloadFormat(it) }
                pickDownloadFormat = false
            },
            onDismiss = { pickDownloadFormat = false },
        )
    }
    if (confirmClearCache) {
        LiteDialog(
            title = stringResource(R.string.settings_clear_cache),
            onDismiss = { confirmClearCache = false },
        ) {
            LiteText(
                stringResource(R.string.settings_clear_cache_confirm),
                style = Lite.type.secondary,
                color = Lite.colors.textSecondary,
                maxLines = 3,
            )
            Spacer(Modifier.height(Lite.dimens.spacing3))
            DialogButtons(
                confirmLabel = stringResource(R.string.action_ok),
                onConfirm = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val cache = graph.cacheHolder.cache
                        val pinned = mutableSetOf<String>()
                        graph.db.downloadDao().allVideoIds().forEach { id ->
                            graph.db.trackDao().get(id)?.selectedItag?.let { itag ->
                                pinned += graph.cacheHolder.cacheKey(id, itag)
                            }
                        }
                        cache.keys.toList().forEach { key ->
                            if (key !in pinned) graph.cacheHolder.removeKey(key)
                        }
                    }
                    confirmClearCache = false
                },
                cancelLabel = stringResource(R.string.action_cancel),
                onCancel = { confirmClearCache = false },
            )
        }
    }
}

@Composable
private fun AudioFormat.describe(): String =
    "$label · $container · " + stringResource(
        R.string.settings_format_size_estimate,
        formatBytes(bytesPer4Min),
    )

@Composable
private fun GroupHeader(text: String) {
    LiteText(
        text = text,
        style = Lite.type.caption,
        color = Lite.colors.accent,
        modifier = Modifier.padding(
            start = Lite.dimens.spacing4,
            top = Lite.dimens.spacing4,
            bottom = Lite.dimens.spacing1,
        ),
    )
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .liteClickable(onClick = onClick)
            .padding(horizontal = Lite.dimens.spacing4, vertical = Lite.dimens.spacing2),
    ) {
        LiteText(title, style = Lite.type.body)
        if (value.isNotEmpty()) {
            LiteText(
                value,
                style = Lite.type.caption,
                color = Lite.colors.textTertiary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Lite.dimens.spacing4, vertical = Lite.dimens.spacing2),
    ) {
        LiteText(title, style = Lite.type.body, modifier = Modifier.weight(1f))
        LiteText(value, style = Lite.type.mono, color = Lite.colors.textTertiary)
    }
}

@Composable
private fun PrefetchRow(graph: AppGraph, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Lite.dimens.spacing4, vertical = Lite.dimens.spacing2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            LiteText(stringResource(R.string.settings_prefetch_count), style = Lite.type.body)
            LiteText(
                text = if (settings.prefetchCount == 0) {
                    stringResource(R.string.settings_prefetch_off)
                } else settings.prefetchCount.toString(),
                style = Lite.type.caption,
                color = Lite.colors.textTertiary,
            )
        }
        Spacer(Modifier.width(Lite.dimens.spacing3))
        Box(Modifier.width(140.dp)) {
            LiteSlider(
                position = settings.prefetchCount / 10f,
                cacheFill = 0f,
                onSeek = { f ->
                    scope.launch { graph.settings.setPrefetchCount((f * 10).toInt()) }
                },
            )
        }
    }
}

@Composable
private fun CacheLimitRow(graph: AppGraph, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val minBytes = 256L * 1024 * 1024
    val maxBytes = 16L * 1024 * 1024 * 1024
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Lite.dimens.spacing4, vertical = Lite.dimens.spacing2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            LiteText(stringResource(R.string.settings_cache_limit), style = Lite.type.body)
            LiteText(
                text = formatBytes(settings.cacheLimitBytes),
                style = Lite.type.caption,
                color = Lite.colors.textTertiary,
            )
        }
        Spacer(Modifier.width(Lite.dimens.spacing3))
        Box(Modifier.width(140.dp)) {
            val fraction = (settings.cacheLimitBytes - minBytes).toFloat() / (maxBytes - minBytes)
            LiteSlider(
                position = fraction.coerceIn(0f, 1f),
                cacheFill = 0f,
                onSeek = { f ->
                    val raw = minBytes + ((maxBytes - minBytes) * f).toLong()
                    // snap to 256MB steps
                    val step = 256L * 1024 * 1024
                    val snapped = (raw / step) * step
                    scope.launch { graph.settings.setCacheLimitBytes(snapped.coerceIn(minBytes, maxBytes)) }
                },
            )
        }
    }
}

@Composable
private fun LanguageRow(graph: AppGraph, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val label = when (settings.language) {
        "ru" -> "Русский"
        "en" -> "English"
        else -> stringResource(R.string.settings_language_system)
    }
    SettingRow(
        title = stringResource(R.string.settings_language),
        value = label,
        onClick = { expanded = true },
    )
    if (expanded) {
        LiteDialog(title = stringResource(R.string.settings_language), onDismiss = { expanded = false }) {
            listOf(
                "" to stringResource(R.string.settings_language_system),
                "ru" to "Русский",
                "en" to "English",
            ).forEach { (code, name) ->
                MenuRow(name, onClick = {
                    scope.launch { graph.settings.setLanguage(code) }
                    AppCompatDelegate.setApplicationLocales(
                        if (code.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(code),
                    )
                    expanded = false
                })
            }
        }
    }
}

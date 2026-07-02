package app.liteaudio.ui.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.liteaudio.data.repo.CacheStatus
import app.liteaudio.ui.design.theme.Lite
import coil3.compose.AsyncImage

/**
 * Storage-state glyph: the byte economy made visible on every row.
 *  - nothing: not cached
 *  - ring with fill: partially cached (real bytes fraction)
 *  - accent dot: fully in LRU cache
 *  - pin: downloaded (pinned forever)
 */
@Composable
fun CacheGlyph(
    status: CacheStatus?,
    pinned: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Lite.colors
    Box(modifier.size(16.dp), contentAlignment = Alignment.Center) {
        when {
            pinned -> LiteIcon(LiteIcons.Pin, tint = colors.accent, size = 13.dp)
            status is CacheStatus.Full -> Box(
                Modifier
                    .size(8.dp)
                    .background(colors.accent, CircleShape),
            )
            status is CacheStatus.Partial -> {
                val fraction = status.fraction.coerceIn(0.03f, 1f)
                Canvas(Modifier.size(14.dp)) {
                    drawCircle(
                        color = colors.textTertiary,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawArc(
                        color = colors.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            else -> Unit
        }
    }
}

/** Dense track row: number/thumb, title, uploader · duration, cache glyph, menu. */
@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    cacheStatus: CacheStatus?,
    pinned: Boolean,
    isCurrent: Boolean,
    unavailable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Lite.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(Lite.dimens.rowHeight)
            .then(
                if (isCurrent) {
                    Modifier.background(colors.accent.copy(alpha = 0.08f))
                } else Modifier,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Lite.dimens.spacing3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Lite.dimens.spacing2))
        }
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(Lite.dimens.thumbSmall)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfacePressed),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(Modifier.width(Lite.dimens.spacing3))
        Column(Modifier.weight(1f)) {
            LiteText(
                text = title,
                style = Lite.type.body,
                color = when {
                    unavailable -> colors.textTertiary
                    isCurrent -> colors.accent
                    else -> colors.textPrimary
                },
            )
            LiteText(
                text = subtitle,
                style = Lite.type.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Spacer(Modifier.width(Lite.dimens.spacing2))
        CacheGlyph(status = cacheStatus, pinned = pinned)
        if (trailing != null) {
            Spacer(Modifier.width(Lite.dimens.spacing1))
            trailing()
        }
    }
}

fun formatDuration(totalSec: Int): String {
    if (totalSec <= 0) return "—"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "%.0f КБ".format(bytes / 1024f)
    bytes < 1024L * 1024 * 1024 -> "%.1f МБ".format(bytes / (1024f * 1024f))
    else -> "%.2f ГБ".format(bytes / (1024f * 1024f * 1024f))
}

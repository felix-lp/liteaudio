package app.liteaudio.ui.design.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Hand-built 24x24 icon set — no material-icons dependency. */
object LiteIcons {

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero,
            ) { block() }
        }.build()

    private fun strokeIcon(name: String, width: Float = 2f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) { block() }
        }.build()

    val Play: ImageVector by lazy {
        icon("play") { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }
    }

    val Pause: ImageVector by lazy {
        icon("pause") {
            moveTo(7f, 5f); lineTo(10.5f, 5f); lineTo(10.5f, 19f); lineTo(7f, 19f); close()
            moveTo(13.5f, 5f); lineTo(17f, 5f); lineTo(17f, 19f); lineTo(13.5f, 19f); close()
        }
    }

    val Next: ImageVector by lazy {
        icon("next") {
            moveTo(6f, 6f); lineTo(14f, 12f); lineTo(6f, 18f); close()
            moveTo(15.5f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(15.5f, 18f); close()
        }
    }

    val Prev: ImageVector by lazy {
        icon("prev") {
            moveTo(18f, 6f); lineTo(10f, 12f); lineTo(18f, 18f); close()
            moveTo(8.5f, 6f); lineTo(6f, 6f); lineTo(6f, 18f); lineTo(8.5f, 18f); close()
        }
    }

    val Shuffle: ImageVector by lazy {
        strokeIcon("shuffle") {
            moveTo(3f, 7f); lineTo(7f, 7f); lineTo(15f, 17f); lineTo(19f, 17f)
            moveTo(3f, 17f); lineTo(7f, 17f); lineTo(9.5f, 13.9f)
            moveTo(12.5f, 10.1f); lineTo(15f, 7f); lineTo(19f, 7f)
            moveTo(17f, 4.5f); lineTo(19.8f, 7f); lineTo(17f, 9.5f)
            moveTo(17f, 14.5f); lineTo(19.8f, 17f); lineTo(17f, 19.5f)
        }
    }

    val Repeat: ImageVector by lazy {
        strokeIcon("repeat") {
            moveTo(4f, 11f); lineTo(4f, 9f); curveTo(4f, 7.3f, 5.3f, 6f, 7f, 6f); lineTo(20f, 6f)
            moveTo(17.5f, 3.5f); lineTo(20f, 6f); lineTo(17.5f, 8.5f)
            moveTo(20f, 13f); lineTo(20f, 15f); curveTo(20f, 16.7f, 18.7f, 18f, 17f, 18f); lineTo(4f, 18f)
            moveTo(6.5f, 20.5f); lineTo(4f, 18f); lineTo(6.5f, 15.5f)
        }
    }

    val Library: ImageVector by lazy {
        icon("library") {
            // stacked albums
            moveTo(3f, 5f); lineTo(15f, 5f); lineTo(15f, 7f); lineTo(3f, 7f); close()
            moveTo(3f, 9f); lineTo(15f, 9f); lineTo(15f, 11f); lineTo(3f, 11f); close()
            moveTo(3f, 13f); lineTo(15f, 13f); lineTo(15f, 15f); lineTo(3f, 15f); close()
            moveTo(3f, 17f); lineTo(11f, 17f); lineTo(11f, 19f); lineTo(3f, 19f); close()
            // play triangle
            moveTo(15f, 14f); lineTo(21f, 17.5f); lineTo(15f, 21f); close()
        }
    }

    val Download: ImageVector by lazy {
        strokeIcon("download") {
            moveTo(12f, 4f); lineTo(12f, 15f)
            moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f)
            moveTo(4f, 19f); lineTo(20f, 19f)
        }
    }

    val Settings: ImageVector by lazy {
        strokeIcon("settings") {
            // three sliders
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 17f); lineTo(20f, 17f)
            // knobs
            moveTo(9f, 5.2f); lineTo(9f, 8.8f)
            moveTo(15f, 10.2f); lineTo(15f, 13.8f)
            moveTo(7f, 15.2f); lineTo(7f, 18.8f)
        }
    }

    val Add: ImageVector by lazy {
        strokeIcon("add", 2.4f) {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    val Queue: ImageVector by lazy {
        strokeIcon("queue") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 11f); lineTo(20f, 11f)
            moveTo(4f, 16f); lineTo(12f, 16f)
        }
    }

    val More: ImageVector by lazy {
        icon("more") {
            moveTo(12f, 4f); curveTo(13.1f, 4f, 14f, 4.9f, 14f, 6f); curveTo(14f, 7.1f, 13.1f, 8f, 12f, 8f); curveTo(10.9f, 8f, 10f, 7.1f, 10f, 6f); curveTo(10f, 4.9f, 10.9f, 4f, 12f, 4f); close()
            moveTo(12f, 10f); curveTo(13.1f, 10f, 14f, 10.9f, 14f, 12f); curveTo(14f, 13.1f, 13.1f, 14f, 12f, 14f); curveTo(10.9f, 14f, 10f, 13.1f, 10f, 12f); curveTo(10f, 10.9f, 10.9f, 10f, 12f, 10f); close()
            moveTo(12f, 16f); curveTo(13.1f, 16f, 14f, 16.9f, 14f, 18f); curveTo(14f, 19.1f, 13.1f, 20f, 12f, 20f); curveTo(10.9f, 20f, 10f, 19.1f, 10f, 18f); curveTo(10f, 16.9f, 10.9f, 16f, 12f, 16f); close()
        }
    }

    val Close: ImageVector by lazy {
        strokeIcon("close", 2.2f) {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Check: ImageVector by lazy {
        strokeIcon("check", 2.4f) {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f)
        }
    }

    val Pin: ImageVector by lazy {
        strokeIcon("pin") {
            // padlock body + shackle
            moveTo(6.5f, 11f); lineTo(17.5f, 11f); lineTo(17.5f, 20f); lineTo(6.5f, 20f); close()
            moveTo(8.5f, 11f); lineTo(8.5f, 8f)
            curveTo(8.5f, 5.8f, 10f, 4f, 12f, 4f)
            curveTo(14f, 4f, 15.5f, 5.8f, 15.5f, 8f)
            lineTo(15.5f, 11f)
        }
    }

    val Error: ImageVector by lazy {
        icon("error") {
            moveTo(12f, 3f); lineTo(22f, 20f); lineTo(2f, 20f); close()
            moveTo(11f, 9f); lineTo(13f, 9f); lineTo(12.7f, 14.5f); lineTo(11.3f, 14.5f); close()
            moveTo(12f, 16f); curveTo(12.7f, 16f, 13.2f, 16.5f, 13.2f, 17.2f); curveTo(13.2f, 17.9f, 12.7f, 18.4f, 12f, 18.4f); curveTo(11.3f, 18.4f, 10.8f, 17.9f, 10.8f, 17.2f); curveTo(10.8f, 16.5f, 11.3f, 16f, 12f, 16f); close()
        }
    }

    val Refresh: ImageVector by lazy {
        strokeIcon("refresh") {
            moveTo(19f, 12f)
            curveTo(19f, 15.9f, 15.9f, 19f, 12f, 19f)
            curveTo(8.1f, 19f, 5f, 15.9f, 5f, 12f)
            curveTo(5f, 8.1f, 8.1f, 5f, 12f, 5f)
            curveTo(14.5f, 5f, 16.7f, 6.3f, 18f, 8.2f)
            moveTo(18.5f, 4.5f); lineTo(18.2f, 8.4f); lineTo(14.3f, 8.1f)
        }
    }

    val Trash: ImageVector by lazy {
        strokeIcon("trash") {
            moveTo(5f, 7f); lineTo(19f, 7f)
            moveTo(9f, 7f); lineTo(9f, 5f); lineTo(15f, 5f); lineTo(15f, 7f)
            moveTo(7f, 7f); lineTo(7.8f, 19f); lineTo(16.2f, 19f); lineTo(17f, 7f)
            moveTo(10.2f, 10.5f); lineTo(10.5f, 16f)
            moveTo(13.8f, 10.5f); lineTo(13.5f, 16f)
        }
    }

    val Drag: ImageVector by lazy {
        strokeIcon("drag", 2f) {
            moveTo(5f, 9f); lineTo(19f, 9f)
            moveTo(5f, 15f); lineTo(19f, 15f)
        }
    }

    val Link: ImageVector by lazy {
        strokeIcon("link") {
            moveTo(10f, 14f); lineTo(14f, 10f)
            moveTo(8.5f, 12f); lineTo(6.5f, 14f); curveTo(4.8f, 15.7f, 4.8f, 18.2f, 6.5f, 19.5f); curveTo(8f, 20.8f, 10.3f, 20.7f, 12f, 19f); lineTo(14f, 17f)
            moveTo(15.5f, 12f); lineTo(17.5f, 10f); curveTo(19.2f, 8.3f, 19.2f, 5.8f, 17.5f, 4.5f); curveTo(16f, 3.2f, 13.7f, 3.3f, 12f, 5f); lineTo(10f, 7f)
        }
    }

    val Pause2: ImageVector get() = Pause

    val PlaylistAdd: ImageVector by lazy {
        strokeIcon("playlist_add") {
            moveTo(4f, 6f); lineTo(16f, 6f)
            moveTo(4f, 10f); lineTo(16f, 10f)
            moveTo(4f, 14f); lineTo(11f, 14f)
            moveTo(17f, 12f); lineTo(17f, 20f)
            moveTo(13f, 16f); lineTo(21f, 16f)
        }
    }

    val ChevronDown: ImageVector by lazy {
        strokeIcon("chevron_down", 2.2f) {
            moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        strokeIcon("chevron_right", 2.2f) {
            moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
        }
    }

    val ChevronLeft: ImageVector by lazy {
        strokeIcon("chevron_left", 2.2f) {
            moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f)
        }
    }
}

package app.liteaudio.ui.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.liteaudio.ui.design.theme.Lite

/** Glass dialog: raised glossy panel on a scrim, compact paddings. */
@Composable
fun LiteDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Lite.dimens.spacing4)) {
                LiteText(title, style = Lite.type.title)
                Spacer(Modifier.height(Lite.dimens.spacing3))
                content()
            }
        }
    }
}

@Composable
fun DialogButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    cancelLabel: String,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
        PressableButton(text = cancelLabel, onClick = onCancel)
        Spacer(Modifier.width(Lite.dimens.spacing2))
        PressableButton(text = confirmLabel, onClick = onConfirm, accent = true, enabled = confirmEnabled)
    }
}

/** One row of a context menu / action sheet. */
@Composable
fun MenuRow(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val colors = Lite.colors
    val tint = if (destructive) colors.error else colors.textPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .liteClickable(onClick = onClick)
            .padding(horizontal = Lite.dimens.spacing4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LiteIcon(icon, tint = tint, size = 18.dp)
            Spacer(Modifier.width(Lite.dimens.spacing3))
        }
        LiteText(text, style = Lite.type.body, color = tint)
    }
}

/** Bottom-sheet-style action menu rendered as a dialog (no material). */
@Composable
fun LiteActionSheet(
    title: String?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = Lite.dimens.spacing2)) {
                if (title != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = Lite.dimens.spacing4,
                                vertical = Lite.dimens.spacing2,
                            ),
                    ) {
                        LiteText(title, style = Lite.type.secondary, color = Lite.colors.textSecondary, maxLines = 2)
                    }
                    LiteDivider()
                }
                content()
            }
        }
    }
}

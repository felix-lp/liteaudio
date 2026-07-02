package app.liteaudio.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.liteaudio.R
import app.liteaudio.data.settings.AudioFormat
import app.liteaudio.ui.design.components.LiteDialog
import app.liteaudio.ui.design.components.LiteIcon
import app.liteaudio.ui.design.components.LiteIcons
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.formatBytes
import app.liteaudio.ui.design.components.liteClickable
import app.liteaudio.ui.design.theme.Lite

/** Concrete stream formats with honest size estimates — no vague low/high. */
@Composable
fun FormatPicker(
    title: String,
    selected: AudioFormat,
    onPick: (AudioFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    LiteDialog(title = title, onDismiss = onDismiss) {
        AudioFormat.entries.forEach { format ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .liteClickable { onPick(format) }
                    .padding(horizontal = Lite.dimens.spacing2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    LiteText("${format.label} · ${format.container}", style = Lite.type.body)
                    LiteText(
                        text = stringResource(
                            R.string.settings_format_size_estimate,
                            formatBytes(format.bytesPer4Min),
                        ),
                        style = Lite.type.caption,
                        color = Lite.colors.textTertiary,
                    )
                }
                if (format == selected) {
                    Spacer(Modifier.width(Lite.dimens.spacing2))
                    LiteIcon(LiteIcons.Check, tint = Lite.colors.accent, size = 16.dp)
                }
            }
        }
    }
}

package app.liteaudio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liteaudio.ui.design.theme.LiteTheme
import app.liteaudio.ui.nav.AppRoot

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as LiteAudioApp).graph
        setContent {
            val settings by graph.settings.settings.collectAsStateWithLifecycle()
            LiteTheme(accent = Color(settings.accentColor)) {
                AppRoot(graph)
            }
        }
    }
}

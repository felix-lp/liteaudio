package app.liteaudio

import android.app.Application
import androidx.media3.common.util.UnstableApi
import app.liteaudio.di.AppGraph

@UnstableApi
class LiteAudioApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

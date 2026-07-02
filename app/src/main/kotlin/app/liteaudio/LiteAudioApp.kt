package app.liteaudio

import android.app.Application
import app.liteaudio.di.AppGraph

class LiteAudioApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

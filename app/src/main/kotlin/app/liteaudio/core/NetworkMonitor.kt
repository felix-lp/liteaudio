package app.liteaudio.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connectivity is informational only: on 2G "validated" signals lie,
 * so nothing gates network attempts on this — it feeds the status strip
 * and the stall watcher.
 */
class NetworkMonitor(context: Context) {

    sealed interface State {
        data class Online(val metered: Boolean, val transport: Transport) : State
        data object Offline : State
    }

    enum class Transport { Wifi, Cellular, Other }

    private val _state = MutableStateFlow<State>(State.Offline)
    val state: StateFlow<State> = _state.asStateFlow()

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.Wifi
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.Cellular
                    else -> Transport.Other
                }
                _state.value = State.Online(
                    metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                    transport = transport,
                )
            }

            override fun onLost(network: Network) {
                _state.value = State.Offline
            }

            override fun onUnavailable() {
                _state.value = State.Offline
            }
        })
        // seed initial state
        val active = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (active != null && active.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            val transport = when {
                active.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.Wifi
                active.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.Cellular
                else -> Transport.Other
            }
            _state.value = State.Online(
                metered = !active.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                transport = transport,
            )
        }
    }
}

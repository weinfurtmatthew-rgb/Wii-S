package app.wiis.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the status pill shows. [batteryPercent] is null until the first battery broadcast. */
data class DeviceStatus(
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val online: Boolean = false,
    val wifi: Boolean = false,
)

/**
 * Battery and network state, pushed by the system (a sticky battery broadcast and the default
 * network callback), so nothing polls. Started once for the life of the process.
 */
class SystemStatus(context: Context) {
    private val app = context.applicationContext
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private var started = false

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBattery(intent)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _status.update {
                it.copy(
                    online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                )
            }
        }

        override fun onLost(network: Network) = _status.update { it.copy(online = false, wifi = false) }
    }

    fun start() {
        if (started) return
        started = true
        val sticky = ContextCompat.registerReceiver(
            app, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let(::onBattery)
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val state = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
        _status.update {
            it.copy(
                batteryPercent = percent ?: it.batteryPercent,
                charging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
    }
}

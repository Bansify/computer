package com.gothwad.computer.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

// ==========================================
// 1. USAGE STATS (MOST FREQUENTLY USED APPS)
// ==========================================
object UsageTracker {
    fun hasPermission(context: Context): Boolean {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    fun getMostUsedPackageNames(context: Context, limit: Int = 12): List<String> {
        if (!hasPermission(context)) return emptyList()
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
            val end = System.currentTimeMillis()
            val start = end - (1000L * 60 * 60 * 24 * 7) // Last 7 days
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end) ?: return emptyList()

            stats.filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }
                .groupBy { it.packageName }
                .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }
                .entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { it.key }
        }.getOrDefault(emptyList())
    }
}

// ==========================================
// 2. BLUETOOTH & REMOTE BATTERY STATUS
// ==========================================
data class BluetoothDeviceStatus(
    val connected: Boolean = false,
    val name: String = "Bluetooth device",
    val batteryLevel: Int = -1, // -1 means unknown; never invent a percentage.
    val isRemote: Boolean = false,
    val statusKnown: Boolean = false,
)

/** ACL broadcasts are observations, not a reliable initial connection snapshot on all OEMs. */
fun bluetoothStatusFlow(context: Context): Flow<BluetoothDeviceStatus> = callbackFlow {
    var status = BluetoothDeviceStatus()
    trySend(status)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val name = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try { device?.name } catch (_: SecurityException) { null }
                    } else null
                    val displayName = name ?: "Bluetooth device"
                    status = BluetoothDeviceStatus(connected = true, name = displayName, statusKnown = true)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // There may be other devices; do not claim all devices are disconnected.
                    status = BluetoothDeviceStatus()
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_OFF) {
                        status = BluetoothDeviceStatus(connected = false, statusKnown = true)
                    }
                }
            }
            trySend(status)
        }
    }
    val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    }
    // Bluetooth system broadcasts may originate outside the system UID. Events only inform UI.
    val registered = runCatching {
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }.isSuccess
    awaitClose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
}

// ==========================================
// 3. AUDIO & TV CONTROL HELPER
// ==========================================
object TvControlHelper {
    fun getVolume(context: Context): Pair<Int, Int> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return Pair(5, 15)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return Pair(current, max)
    }

    fun setVolume(context: Context, volume: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
        }
    }

    fun isMuted(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }
    }

    fun toggleMute(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val mute = !am.isStreamMute(AudioManager.STREAM_MUSIC)
                am.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                mute
            } else {
                false
            }
        }.getOrDefault(false)
    }
}

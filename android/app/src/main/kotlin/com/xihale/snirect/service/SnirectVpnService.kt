package com.xihale.snirect.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xihale.snirect.MainActivity
import com.xihale.snirect.data.model.CoreConfig
import com.xihale.snirect.data.model.LogLevel
import com.xihale.snirect.data.repository.ConfigRepository
import core.Core
import core.EngineCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import com.xihale.snirect.util.AppLogger

class SnirectVpnService : VpnService(), EngineCallbacks {

    companion object {
        const val ACTION_START = "com.xihale.snirect.START"
        const val ACTION_STOP = "com.xihale.snirect.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "SnirectVpnStatus"
        private const val NOTIFICATION_ID = 1
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: ConfigRepository

    override fun onCreate() {
        super.onCreate()
        repository = ConfigRepository(this)
    }

    private var isDestroyed = false

    override fun onDestroy() {
        isDestroyed = true
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i("VPN Service: onStartCommand called, action=${intent?.action}")
        val action = intent?.action
        if (action == ACTION_STOP) {
            AppLogger.i("VPN Service: Received STOP action")
            stopVpn()
        } else if (action == ACTION_START) {
            AppLogger.i("VPN Service: Received START action, isRunning=$isRunning")
            if (!isRunning) {
                startVpn()
            } else {
                AppLogger.w("VPN Service: Already running, ignoring START")
            }
        } else {
            AppLogger.w("VPN Service: Unknown action: $action")
        }
        return START_STICKY
    }

    private fun startVpn() {
        AppLogger.i("Starting VPN Service...")
        createNotificationChannel()
        
        // Android 14+ requires explicit service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification("启动中..."), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification("启动中..."))
        }
        
        serviceScope.launch {
            try {
                setupVpn()
                AppLogger.i("VPN Setup Complete")
                VpnStatusManager.updateStatus(true, "ACTIVE")
                updateNotification("ACTIVE")
            } catch (e: Exception) {
                AppLogger.e("VPN Start Failed", e)
                VpnStatusManager.updateStatus(false, "连接失败: ${e.message}")
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        AppLogger.i("Stopping VPN Service...")
        try { Core.stopEngine() } catch (e: Exception) { AppLogger.e("Stop Core Error", e) }
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        isRunning = false
        VpnStatusManager.updateStatus(false, "已断开")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private suspend fun setupVpn() {
        if (vpnInterface != null) return

        AppLogger.i("VPN Setup: Loading configuration...")
        val nameservers = repository.nameservers.first()
        val bootstrapDns = repository.bootstrapDns.first()
        val checkHostname = repository.checkHostname.first()
        val mtuValue = repository.mtu.first()
        val ipv6Enabled = repository.enableIpv6.first()
        val logLvl = repository.logLevel.first()
        val rules = repository.getMergedRules()

        AppLogger.i("VPN Setup: Config loaded - MTU=$mtuValue, IPv6=$ipv6Enabled, LogLevel=$logLvl, Rules=${rules.size}")

        val builder = Builder()
            .setSession("Snirect")
            .setMtu(mtuValue)
            .addAddress("10.0.0.1", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("10.0.0.2")
            .addAddress("fd00::1", 128)
            .addRoute("::", 0)
            .addDisallowedApplication("com.android.providers.downloads")
        
        AppLogger.i("VPN Setup: Establishing TUN interface...")
        vpnInterface = builder.establish()
        
        vpnInterface?.let { pfd ->
            val fd = pfd.fd
            AppLogger.i("VPN Setup: TUN FD=$fd established successfully")
            
            val config = CoreConfig(
                rules = rules,
                nameservers = nameservers,
                bootstrapDns = bootstrapDns,
                checkHostname = checkHostname,
                mtu = mtuValue,
                enableIpv6 = ipv6Enabled,
                logLevel = logLvl
            )
            val configJson = Json.encodeToString(config)
            AppLogger.i("VPN Setup: Starting core engine with FD=$fd...")
            AppLogger.d("VPN Setup: Config JSON length=${configJson.length}")
            
            try {
                Core.startEngine(fd.toLong(), configJson, this)
                AppLogger.i("VPN Setup: Core.startEngine() call completed")
                isRunning = true
            } catch (e: Exception) {
                AppLogger.e("VPN Setup: Core.startEngine() threw exception", e)
                throw e
            }
        } ?: run {
            AppLogger.e("VPN Setup: Failed to establish TUN interface")
            stopVpn()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Snirect VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    override fun onStatusChanged(status: String?) {
        if (isDestroyed) return

        status?.let { msg ->
            val level = when {
                msg.contains("[ERROR]") -> LogLevel.ERROR
                msg.contains("[WARN]") -> LogLevel.WARN
                msg.contains("[DEBUG]") -> LogLevel.DEBUG
                else -> LogLevel.INFO
            }
            
            val cleanMsg = msg
                .replace("[ERROR]", "")
                .replace("[WARN]", "")
                .replace("[DEBUG]", "")
                .replace("[INFO]", "")
                .trim()
            
            when (level) {
                LogLevel.ERROR -> AppLogger.e(cleanMsg)
                LogLevel.WARN -> AppLogger.w(cleanMsg)
                LogLevel.DEBUG -> AppLogger.d(cleanMsg)
                LogLevel.INFO -> AppLogger.i(cleanMsg)
            }
            
            if (!isDestroyed) {
                updateNotification(cleanMsg)
            }
            
            if (cleanMsg.contains("Running", true) || cleanMsg.contains("Starting...", true)) {
                VpnStatusManager.updateStatus(true, "ACTIVE")
            }
        }
    }

    override fun onSpeedUpdated(up: Long, down: Long) {
        VpnStatusManager.updateSpeed(up, down)
    }

    override fun protect(fd: Long): Boolean {
        val success = protect(fd.toInt())
        if (!success) {
            AppLogger.e("VPN: Failed to protect socket fd=$fd")
        }
        return success
    }
}

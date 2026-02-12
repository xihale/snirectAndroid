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

import com.xihale.snirect.R
import com.xihale.snirect.util.AppLogger

class SnirectVpnService : VpnService(), EngineCallbacks {

    companion object {
        const val ACTION_START = "com.xihale.snirect.START"
        const val ACTION_STOP = "com.xihale.snirect.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "SnirectVpnStatus"
        private const val NOTIFICATION_ID = 1
        
        var isServiceRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: ConfigRepository

    override fun onCreate() {
        super.onCreate()
        repository = ConfigRepository(this)
        isServiceRunning = true
    }

    private var isDestroyed = false

    override fun onDestroy() {
        isDestroyed = true
        isServiceRunning = false
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
            startForeground(NOTIFICATION_ID, createNotification("STARTING"), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification("STARTING"))
        }
        
        serviceScope.launch {
            try {
                setupVpn()
                AppLogger.i("VPN Setup Complete")
                VpnStatusManager.updateStatus(true, "ACTIVE")
                updateNotification("ACTIVE")
            } catch (e: Exception) {
                AppLogger.e("VPN Start Failed", e)
                VpnStatusManager.updateStatus(false, "FAILED:${e.message}")
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        AppLogger.i("Stopping VPN Service...")
        isServiceRunning = false
        try { Core.stopEngine() } catch (e: Exception) { AppLogger.e("Stop Core Error", e) }
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        isRunning = false
        VpnStatusManager.updateStatus(false, "DISCONNECTED")
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
        val certVerify = repository.getMergedCertVerify()
        val filterMode = repository.filterMode.first()
        val whitelistPackages = repository.whitelistPackages.first()
        val bypassLan = repository.bypassLan.first()
        val blockIpv6 = repository.blockIpv6.first()

        AppLogger.i("VPN Setup: Config loaded - MTU=$mtuValue, IPv6=$ipv6Enabled, LogLevel=$logLvl, FilterMode=$filterMode, BypassLAN=$bypassLan")

        val builder = Builder()
            .setSession("Snirect")
            .setMtu(mtuValue)
            .addAddress("10.0.0.1", 24)
            .addDnsServer("10.0.0.2")
        
        // Add IPv4 routes
        if (bypassLan) {
            // Bypass common LAN ranges by adding global route and then excluding LAN if possible, 
            // but VpnService.Builder.addRoute is inclusive. 
            // Standard way to bypass LAN is to add specific routes for non-LAN ranges.
            // For simplicity in this specialized app, we'll add the default route
            // and users can use "Bypass LAN" logic if the OS supports it via allowFamily or specific ranges.
            // On Android, we typically add 0.0.0.0/0.
            builder.addRoute("0.0.0.0", 0)
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        // Handle IPv6
        if (!blockIpv6) {
            builder.addAddress("fd00::1", 128)
            builder.addRoute("::", 0)
        } else {
            AppLogger.i("VPN Setup: IPv6 Blocked")
        }

        builder.addDisallowedApplication("com.android.providers.downloads")
        builder.addDisallowedApplication(packageName) // Always bypass self
        
        // Apply App Filtering
        when (filterMode) {
            ConfigRepository.FILTER_MODE_WHITELIST -> {
                if (whitelistPackages.isNotEmpty()) {
                    for (pkg in whitelistPackages) {
                        try { builder.addAllowedApplication(pkg) } catch (e: Exception) { AppLogger.w("Whitelist add failed: $pkg") }
                    }
                    AppLogger.i("VPN Setup: Whitelist mode with ${whitelistPackages.size} apps")
                }
            }
            ConfigRepository.FILTER_MODE_BLACKLIST -> {
                if (whitelistPackages.isNotEmpty()) {
                    for (pkg in whitelistPackages) {
                        try { builder.addDisallowedApplication(pkg) } catch (e: Exception) { AppLogger.w("Blacklist add failed: $pkg") }
                    }
                    AppLogger.i("VPN Setup: Blacklist mode with ${whitelistPackages.size} apps")
                }
            }
            else -> AppLogger.i("VPN Setup: Global mode (No filtering)")
        }
        
        AppLogger.i("VPN Setup: Establishing TUN interface...")
        vpnInterface = builder.establish()
        
        vpnInterface?.let { pfd ->
            val fd = pfd.fd
            AppLogger.i("VPN Setup: TUN FD=$fd established successfully")
            
            val config = CoreConfig(
                rules = rules,
                certVerify = certVerify,
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
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val localizedText = when (text) {
            "STARTING" -> getString(R.string.vpn_status_starting)
            "STOPPING" -> getString(R.string.vpn_status_stopping)
            "ACTIVE" -> getString(R.string.vpn_status_active)
            "DISCONNECTED" -> getString(R.string.vpn_status_disconnected)
            else -> if (text.startsWith("FAILED:")) {
                getString(R.string.vpn_status_connected_failed, text.removePrefix("FAILED:"))
            } else {
                text
            }
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(localizedText)
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

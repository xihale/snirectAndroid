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

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
        } else if (action == ACTION_START) {
            if (!isRunning) {
                startVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        Log.d("SnirectVpnService", "VPN Service starting")
        createNotificationChannel()
        
        startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
        
        serviceScope.launch {
            try {
                setupVpn()
                isRunning = true
                updateNotification("VPN Connected")
            } catch (e: Exception) {
                Log.e("SnirectVpnService", "Failed to start VPN", e)
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        Log.d("SnirectVpnService", "Stopping VPN")
        try {
            Core.stopEngine()
        } catch (e: Exception) {
            // Ignore
        }
        
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("SnirectVpnService", "Error closing interface", e)
        }
        
        vpnInterface = null
        isRunning = false
        
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

        val builder = Builder()
            .setSession("Snirect")
            .setMtu(1500)
            .addAddress("10.0.0.1", 24)
            .addRoute("0.0.0.0", 0)
            .addDisallowedApplication("com.android.providers.downloads")

        vpnInterface = builder.establish()
        
        vpnInterface?.let { pfd ->
            val fd = pfd.fd
            
            val rules = repository.getRules()
            val dns = repository.dnsServer.first()
            val config = CoreConfig(rules, dns)
            val configJson = Json.encodeToString(config)
            
            Core.startEngine(fd.toLong(), configJson, this)
        } ?: run {
            Log.e("SnirectVpnService", "Failed to establish VPN interface")
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
        Log.i("SnirectGo", "Status: $status")
        status?.let { updateNotification(it) }
    }

    override fun onSpeedUpdated(up: Long, down: Long) {
        // Track speed
    }
}

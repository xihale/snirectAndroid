package com.xihale.snirect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AppLogger.i("BootReceiver: Received BOOT_COMPLETED")
            val repository = ConfigRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                val shouldActivate = repository.activateOnBoot.first()
                if (shouldActivate) {
                    AppLogger.i("BootReceiver: Auto-starting VPN service")
                    val vpnIntent = Intent(context, SnirectVpnService::class.java).apply {
                        action = SnirectVpnService.ACTION_START
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(vpnIntent)
                    } else {
                        context.startService(vpnIntent)
                    }
                } else {
                    AppLogger.i("BootReceiver: Auto-start on boot is disabled")
                }
            }
        }
    }
}

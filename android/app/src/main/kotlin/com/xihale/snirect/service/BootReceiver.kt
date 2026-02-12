package com.xihale.snirect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.util.AppLogger
import com.xihale.snirect.util.CertUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AppLogger.i("BootReceiver: Received BOOT_COMPLETED")
            val repository = ConfigRepository(context)
            CoroutineScope(Dispatchers.Main).launch {
                val shouldActivate = repository.activateOnBoot.first()
                if (shouldActivate) {
                    val skipCheck = repository.skipCertCheck.first()
                    if (!skipCheck && !CertUtil.isCaCertInstalled()) {
                        AppLogger.w("BootReceiver: Auto-start blocked - CA certificate not installed")
                        Toast.makeText(context, "Boot auto-start blocked: Please install CA certificate", Toast.LENGTH_LONG).show()
                        return@launch
                    }

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

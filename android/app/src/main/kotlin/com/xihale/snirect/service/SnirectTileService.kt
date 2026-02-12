package com.xihale.snirect.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.xihale.snirect.R
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.util.AppLogger
import com.xihale.snirect.util.CertUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SnirectTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartListening() {
        super.onStartListening()
        AppLogger.d("Tile: onStartListening")
        
        VpnStatusManager.isRunning
            .onEach { isRunning ->
                updateTile(isRunning)
            }
            .launchIn(serviceScope)
    }

    private fun updateTile(isRunning: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = VpnStatusManager.isRunning.value
        AppLogger.i("Tile: onClick, current isRunning=$isRunning")
        
        if (isRunning) {
            val intent = Intent(this, SnirectVpnService::class.java).apply {
                action = SnirectVpnService.ACTION_STOP
            }
            startService(intent)
        } else {
            serviceScope.launch {
                val repository = ConfigRepository(applicationContext)
                val skipCheck = repository.skipCertCheck.first()
                if (!skipCheck && !CertUtil.isCaCertInstalled()) {
                    Toast.makeText(applicationContext, getString(R.string.toast_cert_install_required), Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                val intent = Intent(applicationContext, SnirectVpnService::class.java).apply {
                    action = SnirectVpnService.ACTION_START
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
    }
}

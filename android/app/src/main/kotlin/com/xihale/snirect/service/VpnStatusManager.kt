package com.xihale.snirect.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStatusManager {
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _statusText = MutableStateFlow("DISCONNECTED")
    val statusText = _statusText.asStateFlow()

    private val _uploadSpeed = MutableStateFlow(0L)
    val uploadSpeed = _uploadSpeed.asStateFlow()

    private val _downloadSpeed = MutableStateFlow(0L)
    val downloadSpeed = _downloadSpeed.asStateFlow()

    fun updateStatus(running: Boolean, text: String) {
        _isRunning.value = running
        _statusText.value = text
    }

    fun updateSpeed(up: Long, down: Long) {
        _uploadSpeed.value = up
        _downloadSpeed.value = down
    }
}

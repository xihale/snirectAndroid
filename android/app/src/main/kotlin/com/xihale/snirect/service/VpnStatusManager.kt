package com.xihale.snirect.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStatusManager {
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _statusText = MutableStateFlow("Disconnected")
    val statusText = _statusText.asStateFlow()

    private val _uploadSpeed = MutableStateFlow("0 B/s")
    val uploadSpeed = _uploadSpeed.asStateFlow()

    private val _downloadSpeed = MutableStateFlow("0 B/s")
    val downloadSpeed = _downloadSpeed.asStateFlow()

    fun updateStatus(running: Boolean, text: String) {
        _isRunning.value = running
        _statusText.value = text
    }

    fun updateSpeed(up: Long, down: Long) {
        _uploadSpeed.value = formatSpeed(up)
        _downloadSpeed.value = formatSpeed(down)
    }

    private fun formatSpeed(bytes: Long): String {
        if (bytes < 1024) return "$bytes B/s"
        if (bytes < 1024 * 1024) return String.format("%.1f KB/s", bytes / 1024.0)
        return String.format("%.1f MB/s", bytes / (1024.0 * 1024.0))
    }
}

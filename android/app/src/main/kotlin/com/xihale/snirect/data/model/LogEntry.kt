package com.xihale.snirect.data.model

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR;

    companion object {
        fun fromString(level: String): LogLevel {
            return when (level.lowercase()) {
                "debug" -> DEBUG
                "warn" -> WARN
                "error" -> ERROR
                else -> INFO
            }
        }
    }
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val message: String
)

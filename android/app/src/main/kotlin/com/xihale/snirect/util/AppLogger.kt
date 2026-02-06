package com.xihale.snirect.util

import android.util.Log
import com.xihale.snirect.MainActivity
import com.xihale.snirect.data.model.LogEntry
import com.xihale.snirect.data.model.LogLevel

object AppLogger {
    private const val TAG = "Snirect"

    fun d(message: String) {
        Log.d(TAG, message)
        MainActivity.log("[DEBUG] $message")
    }

    fun i(message: String) {
        Log.i(TAG, message)
        MainActivity.log("[INFO] $message")
    }

    fun w(message: String) {
        Log.w(TAG, message)
        MainActivity.log("[WARN] $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        MainActivity.log("[ERROR] $message ${throwable?.message ?: ""}")
    }
}

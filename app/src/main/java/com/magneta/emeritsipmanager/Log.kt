package com.magneta.emeritsipmanager

object Log {

    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR, OFF
    }

    var logLevel: LogLevel = LogLevel.DEBUG

    fun logLevelSet(value: Int) {
        when (value) {
            0 -> logLevel = LogLevel.DEBUG
            1 -> logLevel = LogLevel.DEBUG
            2 -> logLevel = LogLevel.DEBUG
            3 -> logLevel = LogLevel.DEBUG
            4 -> logLevel = LogLevel.DEBUG
        }
    }

    fun d(tag: String, msg: String) {
        if (logLevel < LogLevel.INFO) android.util.Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (logLevel < LogLevel.WARN) android.util.Log.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (logLevel < LogLevel.ERROR) android.util.Log.w(tag, msg)
    }

    fun e(tag: String, msg: String) {
        if (logLevel < LogLevel.OFF) android.util.Log.w(tag, msg)
    }
}
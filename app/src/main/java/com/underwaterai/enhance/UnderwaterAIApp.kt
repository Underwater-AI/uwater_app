package com.underwaterai.enhance

import android.app.Application
import com.underwaterai.enhance.utils.AppLogger

class UnderwaterAIApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize file logger first so all subsequent logs are captured
        AppLogger.init(this)
        AppLogger.i(TAG, "UnderwaterAI Application initialized")
        AppLogger.i(TAG, "Available processors: ${Runtime.getRuntime().availableProcessors()}")
        AppLogger.i(TAG, "Max memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")

        // Install global crash handler so native/unexpected crashes are logged to file
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e(TAG, "UNCAUGHT EXCEPTION on thread '${thread.name}'", throwable)
            AppLogger.flush()
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val TAG = "UnderwaterAI"
    }
}

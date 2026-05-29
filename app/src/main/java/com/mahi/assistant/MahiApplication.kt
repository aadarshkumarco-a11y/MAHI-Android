package com.mahi.assistant

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MahiApplication : Application() {

    override fun onCreate() {
        try {
            super.onCreate()
            instance = this
            setupCrashHandler()
            initializeApp()
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: MahiApplication.onCreate failed", e)
            // Don't rethrow — let the app at least try to start
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
            // Try to continue — don't kill the process for non-fatal issues
            // Only pass truly fatal errors to the default handler
            if (throwable is VirtualMachineError || throwable is ThreadDeath || throwable is OutOfMemoryError) {
                defaultHandler?.uncaughtException(thread, throwable)
            } else {
                // For most exceptions, just log and continue
                // This prevents the app from crashing on launch due to initialization errors
                Log.e(TAG, "Suppressed crash — app will attempt to continue", throwable)
            }
        }
    }

    private fun initializeApp() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "MAHI Assistant running in DEBUG mode")
        }
        Log.i(TAG, "MAHI AI Assistant initialized successfully")
    }

    companion object {
        private const val TAG = "MahiApplication"

        @Volatile
        private lateinit var instance: MahiApplication

        fun getInstance(): MahiApplication = instance
    }
}

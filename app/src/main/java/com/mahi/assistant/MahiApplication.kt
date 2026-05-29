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
            initializeApp()
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: MahiApplication.onCreate failed", e)
            // Don't rethrow — let the app at least try to start
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

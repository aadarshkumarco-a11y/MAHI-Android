package com.mahi.assistant

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MahiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeApp()
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

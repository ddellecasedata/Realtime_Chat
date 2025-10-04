package com.things5.realtimechat

import android.app.Application
import android.util.Log
import androidx.multidex.MultiDexApplication

class RealtimeChatApplication : MultiDexApplication() {
    
    override fun onCreate() {
        super.onCreate()
        Log.d("RealtimeChatApp", "Application initialized")
    }
}

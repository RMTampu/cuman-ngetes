package com.example.trafficmarker

import android.app.Application
import android.app.NotificationManager
import android.os.Build
import com.example.trafficmarker.store.MarkerStore

class TrafficMarkerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        MarkerStore.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .deleteNotificationChannel("marker_alerts")
        }
    }
}

object AppContext {
    lateinit var app: Application
        private set
    fun init(application: Application) { app = application }
}

package com.example.trafficmarker

import android.app.Application
import com.example.trafficmarker.store.MarkerStore

class TrafficMarkerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        MarkerStore.init(this)
    }
}

object AppContext {
    lateinit var app: Application
        private set
    fun init(application: Application) { app = application }
}

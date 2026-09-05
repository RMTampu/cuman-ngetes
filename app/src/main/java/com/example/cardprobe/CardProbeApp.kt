package com.example.cardprobe

import android.app.Application
import com.example.cardprobe.probe.ProbeStore

class CardProbeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ProbeStore.init(this)
    }
}

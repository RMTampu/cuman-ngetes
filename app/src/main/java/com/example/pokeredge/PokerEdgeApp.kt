package com.example.pokeredge

import android.app.Application
import com.example.pokeredge.store.GameStateStore

class PokerEdgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GameStateStore.init(this)
    }
}

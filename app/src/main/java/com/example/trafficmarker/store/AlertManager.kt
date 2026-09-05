package com.example.trafficmarker.store

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.example.trafficmarker.R
import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.model.TrafficEvent
import java.util.concurrent.atomic.AtomicInteger

object AlertManager {
    private const val CHANNEL = "marker_alerts"
    private lateinit var context: Context
    private val seq = AtomicInteger(1000)

    fun init(ctx: Context) {
        context = ctx.applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL, "Alarm Traffic Marker", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarm saat trafik yang ditandai muncul kembali"
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
        }
        manager.createNotificationChannel(channel)
    }

    fun fire(marker: Marker, event: TrafficEvent) {
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("Traffic Marker terdeteksi")
            .setContentText("${event.host}:${event.port} • ${event.direction} • ${event.sizeBytes} B")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(seq.incrementAndGet(), n)
    }
}

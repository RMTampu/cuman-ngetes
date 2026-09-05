package com.example.trafficmarker.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.trafficmarker.R
import com.example.trafficmarker.diagnostic.DiagnosticStore
import com.example.trafficmarker.engine.QuickMarkerEngine
import com.example.trafficmarker.store.MarkerStore
import com.example.trafficmarker.store.SessionStore

class MarkerBubbleService : Service() {
    companion object {
        private const val CHANNEL = "marker_bubble"
        private const val NOTIFICATION_ID = 2201

        fun start(context: Context) {
            val intent = Intent(context, MarkerBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MarkerBubbleService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Traffic Marker")
                .setContentText("Bubble penanda aktif")
                .setOngoing(true)
                .build()
        )
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) showBubble() else stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bubble?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        bubble = null
        panel = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Traffic Marker Bubble", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun layoutParams(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 300
        }

    private fun showBubble() {
        if (bubble != null) return
        val text = TextView(this).apply {
            this.text = "●"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(56, 217, 169))
            setBackgroundColor(Color.argb(220, 20, 24, 30))
            setPadding(16, 8, 16, 8)
        }
        val lp = layoutParams(96, 96)
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        text.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    lp.x = startX + dx
                    lp.y = startY + dy
                    wm.updateViewLayout(text, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel(lp.x + 100, lp.y)
                    true
                }
                else -> false
            }
        }

        bubble = text
        wm.addView(text, lp)
    }

    private fun isVpnTransportActive(): Boolean {
        return runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrDefault(false)
    }

    private fun diagnosticSummary(): String {
        return DiagnosticStore.snapshot(isVpnTransportActive())
            .lineSequence()
            .take(9)
            .joinToString("\n")
    }

    private fun togglePanel(x: Int, y: Int) {
        panel?.let {
            runCatching { wm.removeView(it) }
            panel = null
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            setBackgroundColor(Color.argb(245, 20, 24, 30))
        }
        val status = TextView(this).apply {
            text = "Session: " + SessionStore.size() + " event"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, 12)
        }
        val diagnostic = TextView(this).apply {
            text = "PENGECEKAN DATA\n" + diagnosticSummary()
            setTextColor(Color.LTGRAY)
            textSize = 10f
            setPadding(0, 0, 0, 12)
        }
        val refreshDiagnostic = TextView(this).apply {
            text = "REFRESH DIAGNOSTIK"
            setTextColor(Color.CYAN)
            textSize = 13f
            setPadding(12, 10, 12, 10)
            setOnClickListener {
                diagnostic.text = "PENGECEKAN DATA\n" + diagnosticSummary()
            }
        }
        val mark = TextView(this).apply {
            text = "TANDAI SEKARANG"
            setTextColor(Color.rgb(56, 217, 169))
            textSize = 16f
            setPadding(12, 14, 12, 14)
            setOnClickListener {
                val candidate = QuickMarkerEngine.choose(SessionStore.recent(2000))
                if (candidate == null) {
                    Toast.makeText(this@MarkerBubbleService, "Belum ada trafik dalam 2 detik terakhir", Toast.LENGTH_SHORT).show()
                } else {
                    val marker = MarkerStore.addFrom(candidate)
                    status.text = "Ditandai " + marker.host + ":" + marker.port + " • " + marker.centerSize + " B"
                    Toast.makeText(this@MarkerBubbleService, "Marker tersimpan", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val close = TextView(this).apply {
            text = "Tutup panel"
            setTextColor(Color.LTGRAY)
            setPadding(12, 12, 12, 8)
            setOnClickListener { togglePanel(x, y) }
        }
        root.addView(status)
        root.addView(diagnostic)
        root.addView(refreshDiagnostic)
        root.addView(mark)
        root.addView(close)

        val lp = layoutParams(520, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            this.x = x.coerceAtLeast(0)
            this.y = y.coerceAtLeast(0)
        }
        panel = root
        wm.addView(root, lp)
    }
}

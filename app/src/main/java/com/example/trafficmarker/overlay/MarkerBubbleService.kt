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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.trafficmarker.R
import com.example.trafficmarker.diagnostic.DiagnosticStore
import com.example.trafficmarker.engine.ManualLookaheadStore
import com.example.trafficmarker.engine.MomentFingerprintEngine
import com.example.trafficmarker.store.MarkerStore
import com.example.trafficmarker.store.SessionStore
import kotlin.math.max

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
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: View? = null
    private var panel: View? = null
    private var titlePrompt: View? = null
    private var lookaheadText: TextView? = null
    private var markerStatus: TextView? = null

    private val panelTick = object : Runnable {
        override fun run() {
            if (panel != null) {
                lookaheadText?.text = ManualLookaheadStore.snapshot()
                handler.postDelayed(this, 900L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Traffic Marker")
                .setContentText("Capture metadata aktif")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
        )
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) showBubble() else stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        bubble?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        titlePrompt?.let { runCatching { wm.removeView(it) } }
        bubble = null
        panel = null
        titlePrompt = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Traffic Marker Capture",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun layoutParams(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
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

    private fun diagnosticSummary(): String =
        DiagnosticStore.snapshot(isVpnTransportActive())
            .lineSequence()
            .take(9)
            .joinToString("\n")

    private fun togglePanel(x: Int, y: Int) {
        panel?.let {
            handler.removeCallbacks(panelTick)
            runCatching { wm.removeView(it) }
            panel = null
            lookaheadText = null
            markerStatus = null
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            setBackgroundColor(Color.argb(248, 20, 24, 30))
        }

        val status = TextView(this).apply {
            text = "Session: " + SessionStore.size() + " event • Marker: " + MarkerStore.all().size
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }
        markerStatus = status

        val mark = TextView(this).apply {
            text = "TANDAI MOMEN + JUDUL"
            setTextColor(Color.rgb(56, 217, 169))
            textSize = 16f
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                val anchorMs = System.currentTimeMillis()
                showTitlePrompt(anchorMs)
            }
        }

        val load20 = TextView(this).apply {
            text = "LOAD 20 MANUAL"
            setTextColor(Color.rgb(100, 200, 255))
            textSize = 16f
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                ManualLookaheadStore.start(MarkerStore.all())
                lookaheadText?.text = ManualLookaheadStore.snapshot()
                Toast.makeText(
                    this@MarkerBubbleService,
                    "LOAD 20 dimulai. Hasil hanya tampil di bubble.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val lookahead = TextView(this).apply {
            text = ManualLookaheadStore.snapshot()
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(8, 10, 8, 10)
            setBackgroundColor(Color.rgb(27, 33, 40))
        }
        lookaheadText = lookahead

        val diagnostic = TextView(this).apply {
            text = "DIAGNOSTIK\n" + diagnosticSummary()
            setTextColor(Color.LTGRAY)
            textSize = 9.5f
            setPadding(0, 10, 0, 8)
        }

        val refreshDiagnostic = TextView(this).apply {
            text = "REFRESH DIAGNOSTIK"
            setTextColor(Color.CYAN)
            textSize = 12f
            setPadding(12, 8, 12, 8)
            setOnClickListener {
                diagnostic.text = "DIAGNOSTIK\n" + diagnosticSummary()
                status.text = "Session: " + SessionStore.size() + " event • Marker: " + MarkerStore.all().size
            }
        }

        val close = TextView(this).apply {
            text = "Tutup panel"
            setTextColor(Color.LTGRAY)
            setPadding(12, 10, 12, 6)
            setOnClickListener { togglePanel(x, y) }
        }

        root.addView(status)
        root.addView(mark)
        root.addView(load20)
        root.addView(lookahead)
        root.addView(refreshDiagnostic)
        root.addView(diagnostic)
        root.addView(close)

        val lp = layoutParams(580, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            this.x = x.coerceAtLeast(0)
            this.y = y.coerceAtLeast(0)
        }
        panel = root
        wm.addView(root, lp)
        handler.removeCallbacks(panelTick)
        handler.post(panelTick)
    }

    private fun showTitlePrompt(anchorMs: Long) {
        if (titlePrompt != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 22, 24, 22)
            setBackgroundColor(Color.rgb(24, 29, 36))
        }

        val label = TextView(this).apply {
            text = "Judul penanda"
            textSize = 16f
            setTextColor(Color.WHITE)
        }

        val input = EditText(this).apply {
            hint = "Contoh: Scatter 5x"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            isSingleLine = true
        }

        val info = TextView(this).apply {
            text = "Judul yang sama akan menambah sampel ke marker yang sama."
            textSize = 11f
            setTextColor(Color.LTGRAY)
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val save = TextView(this).apply {
            text = "SIMPAN"
            setTextColor(Color.rgb(56, 217, 169))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(18, 14, 18, 14)
            setOnClickListener {
                val title = input.text?.toString()?.trim().orEmpty()
                if (title.isBlank()) {
                    input.error = "Judul wajib diisi"
                    return@setOnClickListener
                }
                removeTitlePrompt(input)
                saveMomentAfterWindow(title, anchorMs)
            }
        }

        val cancel = TextView(this).apply {
            text = "BATAL"
            setTextColor(Color.LTGRAY)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(18, 14, 18, 14)
            setOnClickListener { removeTitlePrompt(input) }
        }

        row.addView(save, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(cancel, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(label)
        root.addView(input)
        root.addView(info)
        root.addView(row)

        val lp = WindowManager.LayoutParams(
            620,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        titlePrompt = root
        wm.addView(root, lp)
        input.requestFocus()
        handler.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 150L)
    }

    private fun removeTitlePrompt(input: EditText) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        titlePrompt?.let { runCatching { wm.removeView(it) } }
        titlePrompt = null
    }

    private fun saveMomentAfterWindow(title: String, anchorMs: Long) {
        markerStatus?.text = "Menyimpan momen \"" + title + "\"…"
        val readyAt = anchorMs + MomentFingerprintEngine.DEFAULT_AFTER_MS
        val delay = max(0L, readyAt - System.currentTimeMillis())

        handler.postDelayed({
            val from = anchorMs - MomentFingerprintEngine.DEFAULT_BEFORE_MS
            val to = anchorMs + MomentFingerprintEngine.DEFAULT_AFTER_MS
            val events = SessionStore.range(from, to)
            val sample = MomentFingerprintEngine.createSample(anchorMs, events)

            if (sample.bursts.isEmpty()) {
                markerStatus?.text = "Gagal: tidak ada burst di window momen"
                Toast.makeText(this, "Tidak ada trafik untuk disimpan", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }

            val marker = MarkerStore.addMomentSample(title, sample)
            markerStatus?.text =
                "Marker: " + marker.title + " • " + marker.samples.size + " sampel • " +
                    sample.bursts.size + " burst"
            Toast.makeText(
                this,
                "Momen \"" + marker.title + "\" tersimpan",
                Toast.LENGTH_SHORT
            ).show()
        }, delay)
    }
}

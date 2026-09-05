package com.example.cardprobe.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.cardprobe.R
import com.example.cardprobe.probe.ProbeStore

class ProbeBubbleService : Service() {
    companion object {
        private const val CHANNEL = "card_probe_overlay"
        private const val NOTIFICATION_ID = 3301

        fun start(context: Context) {
            val i = Intent(context, ProbeBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProbeBubbleService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var status: TextView? = null
    private var detail: TextView? = null
    private var detailVisible = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Card Presence Probe")
                .setContentText("Probe metadata timing aktif")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
        )
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) showPanel() else stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }

    private fun showPanel() {
        if (root != null) return

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(5), dp(7), dp(7))
            setBackgroundColor(Color.argb(242, 16, 21, 27))
        }

        val header = label("≡ CARD PROBE", Color.LTGRAY, 10.5f)
        val deal = label("1 • KARTU TERTUTUP / DEAL", Color.rgb(80, 210, 180), 12f).apply {
            setOnClickListener {
                val n = ProbeStore.markDeal()
                status?.text = "HAND " + n + " • menunggu REVEAL"
                Toast.makeText(this@ProbeBubbleService, "Anchor DEAL tersimpan", Toast.LENGTH_SHORT).show()
            }
        }
        val reveal = label("2 • REVEAL", Color.rgb(255, 200, 90), 12f).apply {
            setOnClickListener {
                if (ProbeStore.activeDeal() == null) {
                    Toast.makeText(this@ProbeBubbleService, "Tekan DEAL terlebih dahulu", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val revealAt = System.currentTimeMillis()
                status?.text = "Mengumpulkan window reveal 1,2 detik…"

                handler.postDelayed({
                    val trial = ProbeStore.finishReveal(revealAt)
                    refreshText()
                    if (trial != null) {
                        Toast.makeText(
                            this@ProbeBubbleService,
                            "Hand tersimpan • deal ↓" + trial.dealInBytes +
                                " B • reveal ↓" + trial.revealInBytes + " B",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }, 1250L)
            }
        }
        val session = label("SESI BARU (TARGET REOPEN)", Color.rgb(120, 190, 255), 10.5f).apply {
            setOnClickListener {
                ProbeStore.cancelActive()
                val next = ProbeStore.newCaptureSession()
                refreshText()
                Toast.makeText(
                    this@ProbeBubbleService,
                    "Sesi capture baru dimulai • dataset lama tetap ada • calon S" + next,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val details = label("DETAIL HAND ▸", Color.LTGRAY, 10.5f).apply {
            setOnClickListener {
                detailVisible = !detailVisible
                detail?.visibility = if (detailVisible) View.VISIBLE else View.GONE
                text = if (detailVisible) "DETAIL HAND ▾" else "DETAIL HAND ▸"
                if (detailVisible) detail?.text = ProbeStore.detailText(6)
            }
        }
        val cancel = label("BATAL HAND", Color.GRAY, 10f).apply {
            setOnClickListener {
                ProbeStore.cancelActive()
                refreshText()
            }
        }
        val st = label(ProbeStore.summary(), Color.WHITE, 9.2f).apply {
            setBackgroundColor(Color.rgb(24, 30, 37))
        }
        status = st
        val detailText = label(ProbeStore.detailText(6), Color.LTGRAY, 8.2f).apply {
            visibility = View.GONE
            setBackgroundColor(Color.rgb(20, 25, 31))
        }
        detail = detailText

        box.addView(header)
        box.addView(deal)
        box.addView(reveal)
        box.addView(session)
        box.addView(details)
        box.addView(cancel)
        box.addView(st)
        box.addView(detailText)

        val screenW = resources.displayMetrics.widthPixels
        val width = minOf(dp(275), (screenW * 0.55f).toInt())
        val lp = params(width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = dp(12)
            y = dp(70)
        }

        makeDraggable(header, box, lp)
        root = box
        wm.addView(box, lp)
    }

    private fun refreshText() {
        status?.text = ProbeStore.summary()
        if (detailVisible) detail?.text = ProbeStore.detailText(6)
    }

    private fun label(textValue: String, color: Int, size: Float) =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            setPadding(dp(7), dp(5), dp(7), dp(5))
        }

    private fun params(width: Int, height: Int) =
        WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun makeDraggable(handle: View, window: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0

        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val screenW = resources.displayMetrics.widthPixels
                    val screenH = resources.displayMetrics.heightPixels
                    lp.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, maxOf(0, screenW - dp(80)))
                    lp.y = (startY + (e.rawY - downY).toInt()).coerceIn(0, maxOf(0, screenH - dp(50)))
                    runCatching { wm.updateViewLayout(window, lp) }
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Card Probe Overlay",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}

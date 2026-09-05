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
import com.example.trafficmarker.engine.ArrivalValidator
import com.example.trafficmarker.engine.ManualLookaheadStore
import com.example.trafficmarker.engine.MomentFingerprintEngine
import com.example.trafficmarker.recorder.StepRecorder
import com.example.trafficmarker.recorder.TrafficRecorder
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
    private var stepPrompt: View? = null

    private var lookaheadText: TextView? = null
    private var markerStatus: TextView? = null
    private var recorderText: TextView? = null
    private var stepText: TextView? = null
    private var arrivalText: TextView? = null
    private var diagnosticText: TextView? = null

    private val panelTick = object : Runnable {
        override fun run() {
            if (panel != null) {
                lookaheadText?.text = ManualLookaheadStore.snapshot()
                recorderText?.text = "RECORDER LIVE\n" + TrafficRecorder.recentText(6)
                stepText?.text = StepRecorder.summary()
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
                .setContentText("Capture metadata + recorder aktif")
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
        stepPrompt?.let { runCatching { wm.removeView(it) } }
        bubble = null
        panel = null
        titlePrompt = null
        stepPrompt = null
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
            y = 250
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
            .take(6)
            .joinToString("\n")

    private fun section(text: String, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = 13f
            setPadding(10, 8, 10, 8)
        }

    private fun togglePanel(x: Int, y: Int) {
        panel?.let {
            handler.removeCallbacks(panelTick)
            runCatching { wm.removeView(it) }
            panel = null
            lookaheadText = null
            markerStatus = null
            recorderText = null
            stepText = null
            arrivalText = null
            diagnosticText = null
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            setBackgroundColor(Color.argb(248, 20, 24, 30))
        }

        val status = section(
            "Session: " + SessionStore.size() +
                " • Marker: " + MarkerStore.all().size +
                " • Recorder: " + if (TrafficRecorder.isEnabled()) "ON" else "OFF"
        )
        markerStatus = status

        val mark = section("TANDAI MOMEN + JUDUL", Color.rgb(56, 217, 169)).apply {
            textSize = 15f
            setOnClickListener {
                showMarkerTitlePrompt(System.currentTimeMillis())
            }
        }

        val stepState = section(StepRecorder.summary(), Color.rgb(255, 215, 120)).apply {
            setBackgroundColor(Color.rgb(27, 33, 40))
        }
        stepText = stepState

        val stepRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val startStep = section("MULAI STEP", Color.rgb(100, 200, 255)).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                if (!TrafficRecorder.isEnabled()) TrafficRecorder.start(clearPrevious = false)
                if (StepRecorder.isActive()) {
                    Toast.makeText(this@MarkerBubbleService, "Step masih aktif", Toast.LENGTH_SHORT).show()
                } else {
                    val index = StepRecorder.startStep()
                    stepText?.text = StepRecorder.summary()
                    Toast.makeText(this@MarkerBubbleService, "S$index dimulai", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val finishStep = section("HASIL + LABEL", Color.rgb(255, 180, 100)).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                if (!StepRecorder.isActive()) {
                    Toast.makeText(this@MarkerBubbleService, "Tekan MULAI STEP terlebih dahulu", Toast.LENGTH_SHORT).show()
                } else {
                    showStepResultPrompt()
                }
            }
        }
        stepRow.addView(startStep, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        stepRow.addView(finishStep, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))

        val rec = section("RECORDER LIVE\n" + TrafficRecorder.recentText(6), Color.LTGRAY).apply {
            textSize = 9.5f
            setBackgroundColor(Color.rgb(24, 29, 36))
        }
        recorderText = rec

        val saveRecorder = section("SIMPAN RECORDER", Color.rgb(56, 217, 169)).apply {
            setOnClickListener {
                try {
                    val path = TrafficRecorder.saveJsonl(this@MarkerBubbleService)
                    Toast.makeText(this@MarkerBubbleService, "Tersimpan: $path", Toast.LENGTH_LONG).show()
                } catch (t: Throwable) {
                    Toast.makeText(
                        this@MarkerBubbleService,
                        "Gagal simpan recorder: " + (t.message ?: t.javaClass.simpleName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val validate = section("VALIDASI DATA DATANG LEBIH AWAL", Color.rgb(255, 210, 90)).apply {
            textSize = 14f
            setOnClickListener { runArrivalValidation() }
        }

        val arrival = section(
            "ARRIVAL PROOF\nBelum divalidasi. Rekam step + label hasil aktual terlebih dahulu.",
            Color.WHITE
        ).apply {
            textSize = 10.5f
            setBackgroundColor(Color.rgb(27, 33, 40))
        }
        arrivalText = arrival

        val load20 = section("LOAD 20 MANUAL (EKSPERIMEN)", Color.rgb(100, 200, 255)).apply {
            textSize = 14f
            setOnClickListener {
                if (MarkerStore.all().none { it.samples.isNotEmpty() }) {
                    Toast.makeText(this@MarkerBubbleService, "Belum ada marker momen", Toast.LENGTH_SHORT).show()
                } else {
                    ManualLookaheadStore.start(MarkerStore.all())
                    lookaheadText?.text = ManualLookaheadStore.snapshot()
                }
            }
        }

        val lookahead = section(ManualLookaheadStore.snapshot(), Color.LTGRAY).apply {
            textSize = 10f
            setBackgroundColor(Color.rgb(27, 33, 40))
        }
        lookaheadText = lookahead

        val refreshDiagnostic = section("REFRESH DIAGNOSTIK", Color.CYAN).apply {
            textSize = 11f
            setOnClickListener {
                diagnosticText?.text = "DIAGNOSTIK\n" + diagnosticSummary()
            }
        }

        val diagnostic = section("DIAGNOSTIK\n" + diagnosticSummary(), Color.GRAY).apply {
            textSize = 8.5f
        }
        diagnosticText = diagnostic

        val close = section("Tutup panel", Color.LTGRAY).apply {
            setOnClickListener { togglePanel(x, y) }
        }

        root.addView(status)
        root.addView(mark)
        root.addView(stepState)
        root.addView(stepRow)
        root.addView(rec)
        root.addView(saveRecorder)
        root.addView(validate)
        root.addView(arrival)
        root.addView(load20)
        root.addView(lookahead)
        root.addView(refreshDiagnostic)
        root.addView(diagnostic)
        root.addView(close)

        val lp = layoutParams(620, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            this.x = x.coerceAtLeast(0)
            this.y = y.coerceAtLeast(0)
        }
        panel = root
        wm.addView(root, lp)
        handler.removeCallbacks(panelTick)
        handler.post(panelTick)
    }

    private fun showMarkerTitlePrompt(anchorMs: Long) {
        if (titlePrompt != null || stepPrompt != null) return
        val root = promptRoot()
        val input = promptInput("Contoh: Bigwin")
        root.addView(promptLabel("Judul marker"))
        root.addView(input)
        root.addView(promptInfo("Judul yang sama menambah sampel ke marker yang sama."))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(promptButton("SIMPAN", Color.rgb(56, 217, 169)) {
            val title = input.text?.toString()?.trim().orEmpty()
            if (title.isBlank()) {
                input.error = "Judul wajib diisi"
            } else {
                removePrompt(input, isStep = false)
                saveMomentAfterWindow(title, anchorMs)
            }
        }, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(promptButton("BATAL", Color.LTGRAY) {
            removePrompt(input, isStep = false)
        }, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        titlePrompt = root
        showFocusablePrompt(root, input)
    }

    private fun showStepResultPrompt() {
        if (titlePrompt != null || stepPrompt != null) return
        val root = promptRoot()
        val input = promptInput("Normal / Bigwin / Scatter")
        root.addView(promptLabel("Label hasil aktual"))
        root.addView(input)
        root.addView(
            promptInfo(
                "Gunakan judul marker yang sama bila target benar-benar muncul. " +
                    "Untuk hasil biasa tulis Normal."
            )
        )

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(promptButton("SIMPAN HASIL", Color.rgb(255, 180, 100)) {
            val label = input.text?.toString()?.trim().orEmpty()
            if (label.isBlank()) {
                input.error = "Label wajib diisi"
            } else {
                val record = runCatching { StepRecorder.finishStep(label) }.getOrElse {
                    Toast.makeText(this, it.message ?: "Gagal menyimpan step", Toast.LENGTH_SHORT).show()
                    return@promptButton
                }
                removePrompt(input, isStep = true)
                stepText?.text = StepRecorder.summary()
                Toast.makeText(
                    this,
                    "S" + record.index + " = " + record.label + " • " + record.events.size + " event",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(promptButton("BATAL", Color.LTGRAY) {
            removePrompt(input, isStep = true)
        }, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        stepPrompt = root
        showFocusablePrompt(root, input)
    }

    private fun promptRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 22, 24, 22)
        setBackgroundColor(Color.rgb(24, 29, 36))
    }

    private fun promptLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.WHITE)
    }

    private fun promptInfo(text: String) = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(Color.LTGRAY)
    }

    private fun promptInput(hintText: String) = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        isSingleLine = true
    }

    private fun promptButton(textValue: String, color: Int, action: () -> Unit) =
        TextView(this).apply {
            text = textValue
            setTextColor(color)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12, 14, 12, 14)
            setOnClickListener { action() }
        }

    private fun showFocusablePrompt(root: View, input: EditText) {
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
        wm.addView(root, lp)
        input.requestFocus()
        handler.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 150L)
    }

    private fun removePrompt(input: EditText, isStep: Boolean) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        if (isStep) {
            stepPrompt?.let { runCatching { wm.removeView(it) } }
            stepPrompt = null
        } else {
            titlePrompt?.let { runCatching { wm.removeView(it) } }
            titlePrompt = null
        }
    }

    private fun runArrivalValidation() {
        val markers = MarkerStore.all().filter { it.samples.isNotEmpty() }
        val steps = StepRecorder.all()

        if (markers.isEmpty()) {
            arrivalText?.text = "ARRIVAL PROOF: NO DATA\nBelum ada marker momen."
            return
        }
        if (steps.isEmpty()) {
            arrivalText?.text = "ARRIVAL PROOF: NO DATA\nBelum ada step + ground-truth."
            return
        }

        arrivalText?.text = markers.joinToString("\n\n") { marker ->
            ArrivalValidator.format(ArrivalValidator.validate(marker, steps))
        }
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

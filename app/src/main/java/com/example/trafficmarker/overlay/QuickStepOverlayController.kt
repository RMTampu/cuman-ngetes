package com.example.trafficmarker.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.trafficmarker.recorder.StepRecorder
import com.example.trafficmarker.recorder.TrafficRecorder

class QuickStepOverlayController(
    private val context: Context,
    private val wm: WindowManager,
    private val onMarkerSelected: (String, Long) -> Unit,
    private val onStepChanged: () -> Unit
) {
    companion object {
        val RESULT_LABELS = listOf(
            "NORMAL",
            "BIGWIN",
            "SUPERWIN",
            "MEGAWIN",
            "EPIC WIN",
            "ULTIMATE WIN",
            "SCATTER"
        )
    }

    private enum class ResultMode { STEP, MARKER }

    private var autoMode = false
    private var resultMode = ResultMode.STEP
    private var markerAnchorMs: Long? = null

    private var modeWindow: View? = null
    private var resultWindow: View? = null
    private var modeStatus: TextView? = null
    private var resultHeader: TextView? = null

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun show() {
        if (modeWindow == null) showModeWindow()
        if (resultWindow == null) showResultWindow()
        refresh()
    }

    fun destroy() {
        modeWindow?.let { runCatching { wm.removeView(it) } }
        resultWindow?.let { runCatching { wm.removeView(it) } }
        modeWindow = null
        resultWindow = null
        modeStatus = null
        resultHeader = null
    }

    fun beginMarkerSelection(anchorMs: Long) {
        markerAnchorMs = anchorMs
        resultMode = ResultMode.MARKER
        show()
        refresh()
        Toast.makeText(
            context,
            "Pilih jenis penanda pada window HASIL",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun focusStepResults() {
        resultMode = ResultMode.STEP
        markerAnchorMs = null
        show()
        refresh()
    }

    fun turnAutoOn() {
        if (!TrafficRecorder.isEnabled()) TrafficRecorder.start(clearPrevious = false)
        autoMode = true
        if (!StepRecorder.isActive()) StepRecorder.startStep()
        resultMode = ResultMode.STEP
        markerAnchorMs = null
        refresh()
        onStepChanged()
    }

    fun turnAutoOff() {
        autoMode = false
        val cancelled = StepRecorder.cancelActiveStep()
        resultMode = ResultMode.STEP
        markerAnchorMs = null
        refresh()
        onStepChanged()
        if (cancelled != null) {
            Toast.makeText(
                context,
                "STEP AUTO OFF • S$cancelled dibatalkan",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showModeWindow() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(6))
            setBackgroundColor(Color.argb(238, 20, 24, 30))
        }

        val header = TextView(context).apply {
            text = "≡ STEP MODE"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }

        val status = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setOnClickListener {
                if (autoMode) turnAutoOff() else turnAutoOn()
            }
        }
        modeStatus = status

        root.addView(header)
        root.addView(status)

        val lp = baseParams(dp(145), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = dp(12)
            y = dp(90)
        }
        makeDraggable(header, root, lp)

        modeWindow = root
        wm.addView(root, lp)
    }

    private fun showResultWindow() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(6))
            setBackgroundColor(Color.argb(240, 20, 24, 30))
        }

        val header = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), dp(3), dp(4), dp(4))
        }
        resultHeader = header
        root.addView(header)

        val grid = GridLayout(context).apply {
            columnCount = 2
            rowCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }

        RESULT_LABELS.forEach { label ->
            val button = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 10.5f
                setTextColor(if (label == "NORMAL") Color.LTGRAY else Color.WHITE)
                setBackgroundColor(
                    if (label == "NORMAL") Color.rgb(47, 52, 60)
                    else Color.rgb(32, 91, 82)
                )
                setPadding(dp(5), dp(7), dp(5), dp(7))
                setOnClickListener { handleResult(label) }
            }
            val params = GridLayout.LayoutParams().apply {
                width = dp(112)
                height = WindowManager.LayoutParams.WRAP_CONTENT
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            grid.addView(button, params)
        }

        root.addView(grid)

        val lp = baseParams(dp(245), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = dp(165)
            y = dp(90)
        }
        makeDraggable(header, root, lp)

        resultWindow = root
        wm.addView(root, lp)
    }

    private fun handleResult(label: String) {
        when (resultMode) {
            ResultMode.MARKER -> {
                val anchor = markerAnchorMs ?: System.currentTimeMillis()
                onMarkerSelected(label, anchor)
                markerAnchorMs = null
                resultMode = ResultMode.STEP
                refresh()
            }

            ResultMode.STEP -> {
                if (!StepRecorder.isActive()) {
                    if (autoMode) {
                        StepRecorder.startStep()
                        refresh()
                        onStepChanged()
                    }
                    Toast.makeText(
                        context,
                        "Belum ada step aktif",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val record = runCatching { StepRecorder.finishStep(label) }.getOrElse {
                    Toast.makeText(
                        context,
                        it.message ?: "Gagal menyimpan hasil",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                if (autoMode) {
                    StepRecorder.startStep()
                }

                refresh()
                onStepChanged()

                val next = StepRecorder.currentStepIndex()
                Toast.makeText(
                    context,
                    "S${record.index} = ${record.label}" +
                        if (next != null) " • S$next dimulai" else "",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun refresh() {
        modeStatus?.apply {
            text = if (autoMode) {
                "● ON\n" + (StepRecorder.currentStepIndex()?.let { "S$it AKTIF" } ?: "SIAP")
            } else {
                "○ OFF"
            }
            setTextColor(
                if (autoMode) Color.rgb(56, 217, 169)
                else Color.LTGRAY
            )
        }

        resultHeader?.text = when (resultMode) {
            ResultMode.MARKER -> "≡ PILIH PENANDA"
            ResultMode.STEP -> {
                val index = StepRecorder.currentStepIndex()
                if (index == null) "≡ HASIL • STEP OFF"
                else "≡ HASIL S$index"
            }
        }
    }

    private fun baseParams(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun makeDraggable(
        handle: View,
        window: View,
        lp: WindowManager.LayoutParams
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val screenW = context.resources.displayMetrics.widthPixels
                    val screenH = context.resources.displayMetrics.heightPixels
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    lp.x = (startX + dx).coerceIn(0, maxOf(0, screenW - dp(80)))
                    lp.y = (startY + dy).coerceIn(0, maxOf(0, screenH - dp(50)))
                    runCatching { wm.updateViewLayout(window, lp) }
                    true
                }
                else -> false
            }
        }
    }
}

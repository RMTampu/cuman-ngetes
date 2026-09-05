package com.example.pokeredge.overlay

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
import com.example.pokeredge.R
import com.example.pokeredge.engine.PokerAdvisor
import com.example.pokeredge.model.Card
import com.example.pokeredge.store.GameStateStore

class PokerOverlayService : Service() {
    companion object {
        private const val CHANNEL = "poker_edge_overlay"
        private const val NOTIFICATION_ID = 5201

        fun start(context: Context) {
            val i = Intent(context, PokerOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PokerOverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private var root: View? = null
    private var summary: TextView? = null
    private var result: TextView? = null
    private var rank = 14
    private var suit = 0
    private var collapsed = false
    private lateinit var rankButton: TextView
    private lateinit var suitButton: TextView
    private lateinit var body: LinearLayout
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Poker Edge Companion")
                .setContentText("Overlay analisis poker aktif")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
        )
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) showPanel() else stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }

    private fun showPanel() {
        if (root != null) return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(6))
            setBackgroundColor(Color.argb(245, 12, 18, 24))
        }

        val header = line("≡ POKER EDGE     ▾", Color.rgb(90, 220, 185), 11f).apply {
            setOnClickListener {
                collapsed = !collapsed
                body.visibility = if (collapsed) View.GONE else View.VISIBLE
                text = if (collapsed) "≡ POKER EDGE     ▸" else "≡ POKER EDGE     ▾"
            }
        }

        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        rankButton = action("RANK A") {
            rank--
            if (rank < 2) rank = 14
            refreshPicker()
        }
        suitButton = action("SUIT ♠") {
            suit = (suit + 1) % 4
            refreshPicker()
        }

        val pickRow = row()
        pickRow.addView(rankButton, weight())
        pickRow.addView(suitButton, weight())
        body.addView(pickRow)

        val cardRow = row()
        cardRow.addView(action("+ HOLE") { addCard(true) }, weight())
        cardRow.addView(action("+ BOARD") { addCard(false) }, weight())
        cardRow.addView(action("UNDO") {
            GameStateStore.undoLastCard()
            refreshState()
        }, weight())
        body.addView(cardRow)

        val moneyRow = row()
        moneyRow.addView(action("POT -") {
            GameStateStore.adjustPot(-GameStateStore.snapshot().chipStep)
            refreshState()
        }, weight())
        moneyRow.addView(action("POT +") {
            GameStateStore.adjustPot(GameStateStore.snapshot().chipStep)
            refreshState()
        }, weight())
        moneyRow.addView(action("CALL -") {
            GameStateStore.adjustCall(-GameStateStore.snapshot().chipStep)
            refreshState()
        }, weight())
        moneyRow.addView(action("CALL +") {
            GameStateStore.adjustCall(GameStateStore.snapshot().chipStep)
            refreshState()
        }, weight())
        body.addView(moneyRow)

        val configRow = row()
        configRow.addView(action("LAWAN -") {
            GameStateStore.adjustOpponents(-1)
            refreshState()
        }, weight())
        configRow.addView(action("LAWAN +") {
            GameStateStore.adjustOpponents(1)
            refreshState()
        }, weight())
        configRow.addView(action("STEP") {
            GameStateStore.cycleChipStep()
            refreshState()
        }, weight())
        body.addView(configRow)

        val stateText = line("", Color.WHITE, 9.5f).apply {
            setBackgroundColor(Color.rgb(22, 29, 36))
        }
        summary = stateText
        body.addView(stateText)

        val analyze = action("ANALYZE") { analyze() }.apply {
            textSize = 12f
            setTextColor(Color.rgb(90, 220, 185))
        }
        body.addView(analyze)

        val resultText = line("Masukkan 2 hole card untuk mulai.", Color.rgb(245, 210, 100), 10f).apply {
            setBackgroundColor(Color.rgb(22, 29, 36))
        }
        result = resultText
        body.addView(resultText)

        body.addView(action("RESET HAND") {
            GameStateStore.resetHand()
            result?.text = "Hand direset."
            refreshState()
        })

        panel.addView(header)
        panel.addView(body)

        val screenW = resources.displayMetrics.widthPixels
        val width = minOf(dp(340), (screenW * 0.62f).toInt())
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(55)
        }

        makeDraggable(header, panel, lp)
        root = panel
        wm.addView(panel, lp)
        refreshPicker()
        refreshState()
    }

    private fun addCard(toHole: Boolean) {
        val card = Card(rank, suit)
        val ok = if (toHole) GameStateStore.addHole(card) else GameStateStore.addBoard(card)
        if (!ok) {
            Toast.makeText(
                this,
                "Kartu duplikat atau slot sudah penuh",
                Toast.LENGTH_SHORT
            ).show()
        }
        refreshState()
    }

    private fun analyze() {
        val state = GameStateStore.snapshot()
        if (state.hole.size != 2) {
            result?.text = "Butuh tepat 2 hole card."
            return
        }

        result?.text = "Menghitung equity…"
        Thread {
            val output = runCatching { PokerAdvisor.analyze(state) }
            mainHandler.post {
                output.onSuccess { a ->
                    result?.text =
                        a.recommendation + "\n" +
                        a.handName + "\n" +
                        a.detail
                }.onFailure {
                    result?.text = "Analisis gagal: " + (it.message ?: "error")
                }
            }
        }.start()
    }

    private fun refreshPicker() {
        rankButton.text = "RANK " + Card.rankText(rank)
        suitButton.text = "SUIT " + Card.suitText(suit)
    }

    private fun refreshState() {
        val s = GameStateStore.snapshot()
        summary?.text =
            "HOLE: " + cards(s.hole) + "\n" +
            "BOARD: " + cards(s.board) + "\n" +
            "POT " + s.pot + " • CALL " + s.call +
            " • LAWAN " + s.opponents + " • STEP " + s.chipStep
    }

    private fun cards(cards: List<Card>): String =
        if (cards.isEmpty()) "-" else cards.joinToString(" ") { it.short }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun weight() =
        LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f)

    private fun action(textValue: String, action: () -> Unit) =
        TextView(this).apply {
            text = textValue
            textSize = 9.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(7), dp(5), dp(7))
            setBackgroundColor(Color.rgb(31, 40, 49))
            setOnClickListener { action() }
        }

    private fun line(textValue: String, color: Int, size: Float) =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            setPadding(dp(7), dp(6), dp(7), dp(6))
        }

    private fun makeDraggable(handle: View, window: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(3) || kotlin.math.abs(dy) > dp(3)) moved = true
                    val screenW = resources.displayMetrics.widthPixels
                    val screenH = resources.displayMetrics.heightPixels
                    lp.x = (startX + dx).coerceIn(0, maxOf(0, screenW - dp(70)))
                    lp.y = (startY + dy).coerceIn(0, maxOf(0, screenH - dp(45)))
                    runCatching { wm.updateViewLayout(window, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) handle.performClick()
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
                    "Poker Edge Overlay",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}

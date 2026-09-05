package com.example.trafficmarker.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.trafficmarker.R
import com.example.trafficmarker.diagnostic.DiagnosticStore
import com.example.trafficmarker.model.TrafficEvent
import com.example.trafficmarker.net.LocalSocksServer
import com.example.trafficmarker.net.TrafficBus
import com.example.trafficmarker.net.UdpGatewayServer
import com.example.trafficmarker.overlay.MarkerBubbleService
import com.example.trafficmarker.store.MarkerStore
import com.example.trafficmarker.store.SessionStore
import com.ooimi.socks.ProxyModel
import com.ooimi.socks.SocksProxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private data class AppItem(val label: String, val packageName: String) {
        override fun toString(): String = label + "\n" + packageName
    }

    private val events = ArrayList<TrafficEvent>()
    private lateinit var eventAdapter: ArrayAdapter<String>
    private lateinit var list: ListView
    private lateinit var appSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var markerInfo: TextView
    private lateinit var diagnosticText: TextView
    private val diagnosticHandler = Handler(Looper.getMainLooper())
    private var selectedIndex = -1
    private var captureRequested = false

    private val diagnosticTick = object : Runnable {
        override fun run() {
            if (::diagnosticText.isInitialized) {
                diagnosticText.text = DiagnosticStore.snapshot(isVpnTransportActive())
            }
            diagnosticHandler.postDelayed(this, 1000L)
        }
    }

    private val listener = TrafficBus.Listener { event ->
        runOnUiThread {
            events.add(0, event)
            if (events.size > 250) events.removeAt(events.lastIndex)
            refreshEvents()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        loadApps()
        refreshMarkerInfo()
        TrafficBus.add(listener)
    }

    override fun onStart() {
        super.onStart()
        diagnosticHandler.removeCallbacks(diagnosticTick)
        diagnosticHandler.post(diagnosticTick)
    }

    override fun onStop() {
        diagnosticHandler.removeCallbacks(diagnosticTick)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (captureRequested && Settings.canDrawOverlays(this)) {
            MarkerBubbleService.start(this)
        }
    }

    override fun onDestroy() {
        TrafficBus.remove(listener)
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(16, 19, 24))
        }
        fun title(text: String, size: Float) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
        }

        root.addView(title("Traffic Marker", 24f))
        root.addView(title("Pilih aplikasi → tangkap metadata → tandai pola tanpa meninggalkan aplikasi target.", 13f).apply {
            setTextColor(Color.rgb(155, 167, 180))
            setPadding(0, 4, 0, 16)
        })

        appSpinner = Spinner(this)
        root.addView(appSpinner, LinearLayout.LayoutParams(-1, -2))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val start = Button(this).apply {
            text = "Mulai Tangkap"
            setOnClickListener { startCapture() }
        }
        val stop = Button(this).apply {
            text = "Stop"
            setOnClickListener { stopCapture() }
        }
        buttons.addView(start, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(stop, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)

        val liveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bubble = Button(this).apply {
            text = "Aktifkan Bubble"
            setOnClickListener { ensureBubblePermission() }
        }
        val openTarget = Button(this).apply {
            text = "Buka Target"
            setOnClickListener { openSelectedTarget() }
        }
        liveRow.addView(bubble, LinearLayout.LayoutParams(0, -2, 1f))
        liveRow.addView(openTarget, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(liveRow)

        status = title("Status: berhenti", 14f).apply {
            setPadding(0, 12, 0, 12)
        }
        root.addView(status)

        val diagHeader = title("PENGECEKAN DATA", 15f).apply {
            setTextColor(Color.rgb(56, 217, 169))
            setPadding(0, 8, 0, 4)
        }
        root.addView(diagHeader)

        diagnosticText = title("Belum ada sesi diagnostik.", 11f).apply {
            setTextColor(Color.rgb(210, 218, 226))
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(24, 29, 36))
        }
        root.addView(diagnosticText, LinearLayout.LayoutParams(-1, 360))

        val diagActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val refreshDiag = Button(this).apply {
            text = "Refresh"
            setOnClickListener { diagnosticText.text = DiagnosticStore.snapshot(isVpnTransportActive()) }
        }
        val copyDiag = Button(this).apply {
            text = "Salin Diagnostik"
            setOnClickListener { copyDiagnostics() }
        }
        diagActions.addView(refreshDiag, LinearLayout.LayoutParams(0, -2, 1f))
        diagActions.addView(copyDiag, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(diagActions)

        val markRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val mark = Button(this).apply {
            text = "Tandai Terpilih"
            setOnClickListener { markSelected() }
        }
        val clear = Button(this).apply {
            text = "Hapus Penanda"
            setOnClickListener {
                MarkerStore.clear()
                refreshMarkerInfo()
            }
        }
        markRow.addView(mark, LinearLayout.LayoutParams(0, -2, 1f))
        markRow.addView(clear, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(markRow)

        markerInfo = title("Penanda: 0", 13f).apply {
            setPadding(0, 8, 0, 8)
            setTextColor(Color.rgb(56, 217, 169))
        }
        root.addView(markerInfo)

        eventAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_activated_1, ArrayList())
        list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            adapter = eventAdapter
            setOnItemClickListener { _, _, position, _ -> selectedIndex = position }
            setOnItemLongClickListener { _, _, position, _ ->
                selectedIndex = position
                markSelected()
                true
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(title("HTTPS tidak didekripsi. Session hanya menyimpan metadata koneksi/chunk.", 11f).apply {
            setTextColor(Color.rgb(155, 167, 180))
            setPadding(0, 8, 0, 0)
        })
        return root
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .map { AppItem(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        appSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apps)
    }

    private fun ensureBubblePermission() {
        if (Settings.canDrawOverlays(this)) {
            MarkerBubbleService.start(this)
            Toast.makeText(this, "Bubble aktif", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
        )
    }

    private fun openSelectedTarget() {
        val item = appSpinner.selectedItem as? AppItem ?: return
        val launch = packageManager.getLaunchIntentForPackage(item.packageName)
        if (launch == null) {
            Toast.makeText(this, "Aplikasi target tidak dapat dibuka", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(launch)
    }

    private fun isVpnTransportActive(): Boolean {
        return runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrDefault(false)
    }

    private fun copyDiagnostics() {
        val text = DiagnosticStore.snapshot(isVpnTransportActive())
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Traffic Marker Diagnostics", text))
        Toast.makeText(this, "Diagnostik disalin", Toast.LENGTH_SHORT).show()
    }

    private fun currentIpv4Dns(): java.net.InetAddress? {
        return runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            val network = cm.activeNetwork ?: return@runCatching null
            cm.getLinkProperties(network)
                ?.dnsServers
                ?.firstOrNull { it.address.size == 4 }
        }.getOrNull()
    }

    private fun startCapture() {
        val item = appSpinner.selectedItem as? AppItem ?: return
        try {
            captureRequested = true
            val dns = currentIpv4Dns()
            DiagnosticStore.reset(item.packageName, dns?.hostAddress)
            SessionStore.start(clearPrevious = true)
            UdpGatewayServer.start(dns)
            LocalSocksServer.start()
            SocksProxy.configConnect("127.0.0.1", LocalSocksServer.PORT)
            SocksProxy.setProxyModel(ProxyModel.WHITE_LIST)
            SocksProxy.setAppList(mutableListOf(item.packageName))
            SocksProxy.setDnsService("1.1.1.1")
            SocksProxy.notificationTitle(
                R.drawable.ic_app,
                "Traffic Marker",
                "Memantau metadata trafik " + item.label
            )
            DiagnosticStore.setVpnRequested(true)
            SocksProxy.start(this)
            if (Settings.canDrawOverlays(this)) MarkerBubbleService.start(this)
            status.text = "Status: meminta izin VPN… • " + item.label + " • Session aktif"
        } catch (t: Throwable) {
            captureRequested = false
            SessionStore.stop()
            MarkerBubbleService.stop(this)
            LocalSocksServer.stop()
            UdpGatewayServer.stop()
            status.text = "Gagal mulai: " + (t.message ?: t.javaClass.simpleName)
        }
    }

    private fun stopCapture() {
        captureRequested = false
        DiagnosticStore.setVpnRequested(false)
        SessionStore.stop()
        MarkerBubbleService.stop(this)
        runCatching { SocksProxy.stop() }
        LocalSocksServer.stop()
        UdpGatewayServer.stop()
        status.text = "Status: berhenti • session " + SessionStore.size() + " event"
    }

    private fun markSelected() {
        val e = events.getOrNull(selectedIndex) ?: run {
            Toast.makeText(this, "Pilih satu trafik terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        val marker = MarkerStore.addFrom(e)
        refreshMarkerInfo()
        Toast.makeText(
            this,
            "Ditandai: " + marker.host + ":" + marker.port + " " + marker.direction + " ±" + marker.centerSize + " B",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun refreshMarkerInfo() {
        val all = MarkerStore.all()
        markerInfo.text = if (all.isEmpty()) {
            "Penanda: 0"
        } else {
            "Penanda: " + all.size + " • terakhir " + all.last().host + ":" + all.last().port
        }
    }

    private fun refreshEvents() {
        val f = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        eventAdapter.clear()
        eventAdapter.addAll(events.map { e ->
            val alarm = if (e.matched) "🔔 " else ""
            val arrow = when (e.direction.name) {
                "OUT", "UDP_OUT" -> "↑"
                "IN", "UDP_IN" -> "↓"
                else -> "•"
            }
            alarm + f.format(Date(e.timeMs)) + "  " + arrow + " " + e.host + ":" + e.port +
                "  " + e.direction + "  " + e.sizeBytes + " B"
        })
        eventAdapter.notifyDataSetChanged()
    }
}

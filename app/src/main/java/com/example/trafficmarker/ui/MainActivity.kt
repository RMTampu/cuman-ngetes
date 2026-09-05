package com.example.trafficmarker.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.trafficmarker.R
import com.example.trafficmarker.model.TrafficEvent
import com.example.trafficmarker.net.LocalSocksServer
import com.example.trafficmarker.net.TrafficBus
import com.example.trafficmarker.store.MarkerStore
import com.ooimi.socket.proxy.SocketProxy
import com.ooimi.socket.proxy.callback.SocketProxyStatusCallback
import com.ooimi.socket.proxy.config.SocksProxyConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private data class AppItem(val label: String, val packageName: String) {
        override fun toString(): String = "$label\n$packageName"
    }

    private val events = ArrayList<TrafficEvent>()
    private lateinit var eventAdapter: ArrayAdapter<String>
    private lateinit var list: ListView
    private lateinit var appSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var markerInfo: TextView
    private var selectedIndex = -1

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
            this.text = text; textSize = size; setTextColor(Color.WHITE)
        }
        root.addView(title("Traffic Marker", 24f))
        root.addView(title("Pilih aplikasi → tangkap metadata → tandai satu pola → alarm saat muncul lagi.", 13f).apply {
            setTextColor(Color.rgb(155, 167, 180)); setPadding(0, 4, 0, 16)
        })

        appSpinner = Spinner(this)
        root.addView(appSpinner, LinearLayout.LayoutParams(-1, -2))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val start = Button(this).apply { text = "Mulai Tangkap"; setOnClickListener { startCapture() } }
        val stop = Button(this).apply { text = "Stop"; setOnClickListener { stopCapture() } }
        buttons.addView(start, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(stop, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)

        status = title("Status: berhenti", 14f).apply { setPadding(0, 12, 0, 12) }
        root.addView(status)

        val markRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val mark = Button(this).apply { text = "Tandai Terpilih"; setOnClickListener { markSelected() } }
        val clear = Button(this).apply { text = "Hapus Penanda"; setOnClickListener { MarkerStore.clear(); refreshMarkerInfo() } }
        markRow.addView(mark, LinearLayout.LayoutParams(0, -2, 1f))
        markRow.addView(clear, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(markRow)

        markerInfo = title("Penanda: 0", 13f).apply { setPadding(0, 8, 0, 8); setTextColor(Color.rgb(56, 217, 169)) }
        root.addView(markerInfo)

        eventAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_activated_1, ArrayList())
        list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            adapter = eventAdapter
            setOnItemClickListener { _, _, position, _ -> selectedIndex = position }
            setOnItemLongClickListener { _, _, position, _ -> selectedIndex = position; markSelected(); true }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(title("HTTPS tidak didekripsi. Event adalah metadata koneksi/chunk: host/IP, port, arah, ukuran.", 11f).apply {
            setTextColor(Color.rgb(155, 167, 180)); setPadding(0, 8, 0, 0)
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

    private fun startCapture() {
        val item = appSpinner.selectedItem as? AppItem ?: return
        try {
            LocalSocksServer.start()
            SocketProxy.setProxyStatusCallback(object : SocketProxyStatusCallback() {
                override fun onStart() { runOnUiThread { status.text = "Status: aktif • ${item.label}" } }
                override fun onStop() { runOnUiThread { status.text = "Status: berhenti" } }
            })
            SocketProxy.startProxy(this, SocksProxyConfig().apply {
                notificationTitle = "Traffic Marker"
                notificationDesc = "Memantau metadata trafik ${item.label}"
                notificationIcon = R.drawable.ic_app
                socksServiceAddress = "127.0.0.1"
                socksServicePort = LocalSocksServer.PORT
                routers = arrayListOf("0.0.0.0/0")
                dnsServerAddress = "1.1.1.1"
                dnsServerPort = 53
                passModel = 1
                appList = arrayListOf(item.packageName)
                supportIpV6 = false
            })
            status.text = "Status: meminta izin VPN…"
        } catch (t: Throwable) {
            LocalSocksServer.stop()
            status.text = "Gagal mulai: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    private fun stopCapture() {
        runCatching { SocketProxy.stopProxy(this) }
        LocalSocksServer.stop()
        status.text = "Status: berhenti"
    }

    private fun markSelected() {
        val e = events.getOrNull(selectedIndex) ?: run {
            Toast.makeText(this, "Pilih satu trafik terlebih dahulu", Toast.LENGTH_SHORT).show(); return
        }
        val marker = MarkerStore.addFrom(e)
        refreshMarkerInfo()
        Toast.makeText(this, "Ditandai: ${marker.host}:${marker.port} ${marker.direction} ±${marker.centerSize} B", Toast.LENGTH_LONG).show()
    }

    private fun refreshMarkerInfo() {
        val all = MarkerStore.all()
        markerInfo.text = if (all.isEmpty()) "Penanda: 0" else "Penanda: ${all.size} • terakhir ${all.last().host}:${all.last().port}"
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
            "$alarm${f.format(Date(e.timeMs))}  $arrow ${e.host}:${e.port}  ${e.direction}  ${e.sizeBytes} B"
        })
        eventAdapter.notifyDataSetChanged()
    }
}

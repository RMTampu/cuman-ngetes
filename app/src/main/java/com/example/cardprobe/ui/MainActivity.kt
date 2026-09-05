package com.example.cardprobe.ui

import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cardprobe.R
import com.example.cardprobe.diagnostic.ProbeDiagnostics
import com.example.cardprobe.net.LocalSocksServer
import com.example.cardprobe.net.UdpGatewayServer
import com.example.cardprobe.overlay.ProbeBubbleService
import com.example.cardprobe.probe.ProbeStore
import com.ooimi.socks.ProxyModel
import com.ooimi.socks.SocksProxy
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private data class AppItem(val label: String, val packageName: String) {
        override fun toString(): String = label + "\n" + packageName
    }

    private lateinit var appSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var diagnostics: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var captureRequested = false

    private val tick = object : Runnable {
        override fun run() {
            result.text = ProbeStore.summary()
            diagnostics.text = ProbeDiagnostics.snapshot(isVpnActive())
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        loadApps()
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        super.onStop()
    }

    private fun buildUi(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(12, 17, 23))
        }

        content.addView(text("Card Presence Probe", 24f, Color.WHITE))
        content.addView(
            text(
                "Uji metadata saja: apakah trafik penting tampak datang saat kartu masih tertutup atau baru saat reveal.",
                12f,
                Color.rgb(160, 172, 185)
            )
        )

        appSpinner = Spinner(this)
        content.addView(appSpinner, LinearLayout.LayoutParams(-1, -2))

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("MULAI PROBE") { startProbe() }, LinearLayout.LayoutParams(0, -2, 1f))
        row1.addView(button("STOP") { stopProbe() }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("OVERLAY") { ensureOverlay() }, LinearLayout.LayoutParams(0, -2, 1f))
        row2.addView(button("BUKA TARGET") { openTarget() }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row2)

        status = text("Status: berhenti", 13f, Color.WHITE)
        content.addView(status)

        content.addView(text("HASIL DATASET", 14f, Color.rgb(80, 210, 180)))
        result = text(ProbeStore.summary(), 12f, Color.WHITE).apply {
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(23, 29, 36))
        }
        content.addView(result)

        content.addView(button("RESET DATASET") {
            ProbeStore.resetDataset()
            result.text = ProbeStore.summary()
            Toast.makeText(this, "Dataset dihapus", Toast.LENGTH_SHORT).show()
        })

        content.addView(text("DIAGNOSTIK", 14f, Color.rgb(120, 190, 255)))
        diagnostics = text("Belum ada capture.", 10f, Color.LTGRAY).apply {
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(23, 29, 36))
        }
        content.addView(diagnostics)

        content.addView(
            text(
                "Interpretasi: PREFETCH_CANDIDATE bukan bukti isi hole-card sudah diketahui. Aplikasi tidak membaca payload HTTPS atau nilai kartu.",
                11f,
                Color.rgb(160, 172, 185)
            )
        )

        return ScrollView(this).apply { addView(content) }
    }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun text(value: String, size: Float, color: Int) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, 8, 0, 8)
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

    private fun ensureOverlay() {
        if (Settings.canDrawOverlays(this)) {
            ProbeBubbleService.start(this)
            return
        }
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
        )
    }

    private fun openTarget() {
        val item = appSpinner.selectedItem as? AppItem ?: return
        val launch = packageManager.getLaunchIntentForPackage(item.packageName)
        if (launch == null) {
            Toast.makeText(this, "Target tidak dapat dibuka", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(launch)
    }

    private fun startProbe() {
        val item = appSpinner.selectedItem as? AppItem ?: return
        try {
            captureRequested = true
            ProbeStore.newCaptureSession()
            ProbeDiagnostics.reset(item.packageName)
            val dns = currentIpv4Dns()
            UdpGatewayServer.start(dns)
            LocalSocksServer.start()
            SocksProxy.configConnect("127.0.0.1", LocalSocksServer.PORT)
            SocksProxy.setProxyModel(ProxyModel.WHITE_LIST)
            SocksProxy.setAppList(mutableListOf(item.packageName))
            SocksProxy.setDnsService("1.1.1.1")
            SocksProxy.notificationTitle(
                R.drawable.ic_app,
                "Card Presence Probe",
                "Mengamati metadata timing " + item.label
            )
            ProbeDiagnostics.setVpnRequested(true)
            SocksProxy.start(this)
            if (Settings.canDrawOverlays(this)) ProbeBubbleService.start(this)
            status.text = "Status: meminta izin VPN • " + item.label
        } catch (t: Throwable) {
            captureRequested = false
            ProbeBubbleService.stop(this)
            LocalSocksServer.stop()
            UdpGatewayServer.stop()
            status.text = "Gagal: " + (t.message ?: t.javaClass.simpleName)
        }
    }

    private fun stopProbe() {
        captureRequested = false
        ProbeDiagnostics.setVpnRequested(false)
        ProbeBubbleService.stop(this)
        runCatching { SocksProxy.stop() }
        LocalSocksServer.stop()
        UdpGatewayServer.stop()
        status.text = "Status: berhenti"
    }

    private fun currentIpv4Dns(): java.net.InetAddress? {
        return runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            val network = cm.activeNetwork ?: return@runCatching null
            cm.getLinkProperties(network)?.dnsServers?.firstOrNull { it.address.size == 4 }
        }.getOrNull()
    }

    private fun isVpnActive(): Boolean = runCatching {
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.allNetworks.any {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }.getOrDefault(false)

    override fun onResume() {
        super.onResume()
        if (captureRequested && Settings.canDrawOverlays(this)) ProbeBubbleService.start(this)
    }
}

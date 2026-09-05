package com.example.pokeredge.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pokeredge.overlay.PokerOverlayService
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private data class AppItem(val label: String, val packageName: String) {
        override fun toString(): String = label + "\n" + packageName
    }

    private lateinit var appSpinner: Spinner
    private lateinit var permissionState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        updatePermission()
    }

    private fun buildUi(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(12, 18, 24))
        }

        content.addView(label("Poker Edge Companion", 25f, Color.WHITE))
        content.addView(
            label(
                "Overlay Texas Hold'em: masukkan kartu yang terlihat, pot, call, dan jumlah lawan. Aplikasi menghitung equity, pot odds, outs peningkatan, dan saran matematis.",
                12f,
                Color.rgb(165, 178, 190)
            )
        )

        appSpinner = Spinner(this)
        content.addView(appSpinner)

        permissionState = label("", 12f, Color.rgb(90, 220, 185))
        content.addView(permissionState)

        val row1 = row()
        row1.addView(button("IZIN OVERLAY") { requestOverlay() }, weight())
        row1.addView(button("MULAI OVERLAY") {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlay()
            } else {
                PokerOverlayService.start(this)
                Toast.makeText(this, "Poker Edge aktif", Toast.LENGTH_SHORT).show()
            }
        }, weight())
        content.addView(row1)

        val row2 = row()
        row2.addView(button("BUKA TARGET") { openTarget() }, weight())
        row2.addView(button("STOP OVERLAY") {
            PokerOverlayService.stop(this)
        }, weight())
        content.addView(row2)

        content.addView(label("CARA CEPAT", 15f, Color.rgb(90, 220, 185)))
        content.addView(
            label(
                "1. Pilih aplikasi poker demo.\n" +
                    "2. Aktifkan overlay lalu buka target.\n" +
                    "3. Pilih RANK dan SUIT, tekan +HOLE untuk 2 kartu Anda.\n" +
                    "4. Tambahkan flop/turn/river dengan +BOARD.\n" +
                    "5. Atur POT, CALL, LAWAN dan STEP.\n" +
                    "6. Tekan ANALYZE.\n\n" +
                    "Equity default dihitung melawan kartu lawan acak; ini bukan pembaca kartu tersembunyi.",
                12f,
                Color.LTGRAY
            )
        )

        return ScrollView(this).apply { addView(content) }
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .map { AppItem(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        appSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            apps
        )
    }

    private fun requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            updatePermission()
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

    private fun updatePermission() {
        permissionState.text =
            "Overlay: " + if (Settings.canDrawOverlays(this)) "DIIZINKAN" else "BELUM DIIZINKAN"
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f)

    private fun button(textValue: String, action: () -> Unit) =
        Button(this).apply {
            text = textValue
            setOnClickListener { action() }
        }

    private fun label(textValue: String, size: Float, color: Int) =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            setPadding(0, 8, 0, 8)
        }
}

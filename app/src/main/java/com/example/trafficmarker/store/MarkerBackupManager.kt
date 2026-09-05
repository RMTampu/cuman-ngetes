package com.example.trafficmarker.store

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.trafficmarker.model.Marker
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MarkerBackupManager {
    const val SAVE_FOLDER = "TrafficMarkerSave"
    private const val FORMAT = "traffic-marker-save"
    private const val FORMAT_VERSION = 2

    data class SaveResult(
        val uri: Uri,
        val displayName: String,
        val markerCount: Int
    )

    data class LoadResult(
        val importedCount: Int,
        val totalInFile: Int
    )

    fun saveAll(context: Context, markers: List<Marker>): SaveResult {
        require(markers.isNotEmpty()) { "Belum ada penanda untuk disimpan" }
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Save publik otomatis membutuhkan Android 10 atau lebih baru"
        }

        val root = JSONObject().apply {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("exportedAtMs", System.currentTimeMillis())
            put("markerCount", markers.size)
            put("markers", JSONArray().apply {
                markers.forEach { marker ->
                    put(MarkerStore.markerToJson(marker))
                }
            })
        }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "TrafficMarker-Markers-$stamp.json"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SAVE_FOLDER
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Gagal membuat file save di Download/$SAVE_FOLDER")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: error("Gagal membuka file save")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }

        return SaveResult(uri, name, markers.size)
    }

    fun loadFromUri(context: Context, uri: Uri): LoadResult {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: error("File save tidak dapat dibaca")

        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) {
            "File bukan save Traffic Marker"
        }
        val version = root.optInt("formatVersion", 1)
        require(version in 1..FORMAT_VERSION) {
            "Versi format save tidak didukung"
        }

        val array = root.optJSONArray("markers") ?: JSONArray()
        val imported = ArrayList<Marker>()
        for (i in 0 until array.length()) {
            imported += MarkerStore.markerFromJson(array.getJSONObject(i))
        }

        val added = MarkerStore.importUnique(imported)
        return LoadResult(
            importedCount = added,
            totalInFile = imported.size
        )
    }
}

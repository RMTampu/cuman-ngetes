#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

PINNED_COMMIT = "fc9fe0c56908846c9f1817ed3a50618ab6fb5556"

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_json_export_overlay.py <pcapdroid-root>")

root = Path(sys.argv[1]).resolve()


def read(rel: str) -> str:
    return (root / rel).read_text(encoding="utf-8")


def write(rel: str, data: str) -> None:
    (root / rel).write_text(data, encoding="utf-8")


def replace_once(rel: str, old: str, new: str) -> None:
    data = read(rel)
    count = data.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected exactly one match, found {count}")
    write(rel, data.replace(old, new, 1))


head = subprocess.check_output(
    ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
).strip()
if head != PINNED_COMMIT:
    raise RuntimeError(f"unexpected upstream commit: {head}")

# Capture stays live in the app, but saving becomes explicitly manual.
# PCAP remains available as an advanced technical archive, not the default output.
prefs_rel = "app/src/main/java/com/emanuelef/remote_capture/model/Prefs.java"
replace_once(
    prefs_rel,
    "public static final String DEFAULT_DUMP_MODE = DUMP_PCAP_FILE;",
    "public static final String DEFAULT_DUMP_MODE = DUMP_NONE;",
)

# Indonesian labels make the difference between JSON export and technical PCAP clear.
strings_rel = "app/src/main/res/values/strings.xml"
strings_path = root / strings_rel
strings_tree = ET.parse(strings_path)
strings_root = strings_tree.getroot()


def set_string(name: str, value: str) -> None:
    for child in strings_root.findall("string"):
        if child.attrib.get("name") == name:
            child.text = value
            return
    node = ET.SubElement(strings_root, "string", {"name": name})
    node.text = value


set_string("pcap_file", "Arsip teknis (PCAP)")
set_string(
    "pcap_file_info",
    "Format teknis, bukan JSON. Untuk menyimpan JSON, buka Muatan lalu tekan ikon simpan.",
)
set_string("export_ellipsis", "Simpan JSON")
set_string("json_export_binary_note", "Data asli biner atau terenkripsi disimpan aman sebagai Base64.")
set_string("json_export_readable_strings", "Teks yang terbaca")

ET.indent(strings_tree, space="    ")
strings_tree.write(strings_path, encoding="utf-8", xml_declaration=True)

# Make every manual payload export a valid UTF-8 JSON document. Plain JSON is parsed
# into a structured `data` field; text stays text; binary/TLS is preserved as Base64.
adapter_rel = "app/src/main/java/com/emanuelef/remote_capture/adapters/PayloadAdapter.java"
adapter = read(adapter_rel)

adapter = adapter.replace(
    "import android.view.LayoutInflater;\n",
    "import android.view.LayoutInflater;\nimport android.util.Base64;\n",
    1,
)
adapter = adapter.replace(
    "import com.google.gson.GsonBuilder;\n",
    "import com.google.gson.GsonBuilder;\nimport com.google.gson.JsonArray;\nimport com.google.gson.JsonElement;\nimport com.google.gson.JsonObject;\n",
    1,
)

anchor = '''    private void handleCopyExportButtons(PayloadViewHolder holder, boolean is_export) {\n        if(is_export && (mExportHandler == null))\n            return;\n\n        int payload_pos = holder.getAbsoluteAdapterPosition();\n'''
replacement = '''    private JsonElement tryParseJson(String text) {\n        if(text == null)\n            return null;\n\n        String trimmed = text.trim();\n        if(trimmed.isEmpty())\n            return null;\n\n        try {\n            return JsonParser.parseString(trimmed);\n        } catch (JsonSyntaxException e) {\n            return null;\n        }\n    }\n\n    private JsonArray extractReadableStrings(byte[] payload) {\n        JsonArray result = new JsonArray();\n        StringBuilder run = new StringBuilder();\n\n        for(byte raw : payload) {\n            int b = raw & 0xFF;\n            if((b >= 32) && (b <= 126)) {\n                if(run.length() < 240)\n                    run.append((char)b);\n            } else {\n                if(run.length() >= 4) {\n                    result.add(run.toString());\n                    if(result.size() >= 24)\n                        return result;\n                }\n                run.setLength(0);\n            }\n        }\n\n        if((run.length() >= 4) && (result.size() < 24))\n            result.add(run.toString());\n\n        return result;\n    }\n\n    private boolean isMostlyPrintable(byte[] payload) {\n        if(payload.length == 0)\n            return true;\n\n        int printable = 0;\n        for(byte raw : payload) {\n            int b = raw & 0xFF;\n            if((b == 9) || (b == 10) || (b == 13) || ((b >= 32) && (b <= 126)))\n                printable++;\n        }\n\n        return (printable * 100 / payload.length) >= 80;\n    }\n\n    private String buildJsonExport(AdapterChunk chunk) {\n        byte[] payload = chunk.mChunk.payload;\n        JsonObject root = new JsonObject();\n        root.addProperty("format", "salin-jaringan/v1");\n        root.addProperty("arah", chunk.mChunk.is_sent ? "kirim" : "terima");\n        root.addProperty("ukuran_byte", payload.length);\n\n        if(chunk.mChunk.httpContentType != null && !chunk.mChunk.httpContentType.isEmpty())\n            root.addProperty("tipe_konten", chunk.mChunk.httpContentType);\n        if(chunk.mChunk.httpPath != null && !chunk.mChunk.httpPath.isEmpty())\n            root.addProperty("jalur_http", chunk.mChunk.httpPath);\n\n        String decoded = new String(payload, StandardCharsets.UTF_8);\n        String body = decoded;\n        int sep = decoded.indexOf("\\r\\n\\r\\n");\n        if(sep >= 0) {\n            String headers = decoded.substring(0, sep);\n            if(!headers.isEmpty())\n                root.addProperty("header_http", headers);\n            body = decoded.substring(Math.min(sep + 4, decoded.length()));\n        }\n\n        JsonElement parsed = tryParseJson(body);\n        if(parsed != null) {\n            root.addProperty("jenis", "json");\n            root.add("data", parsed);\n        } else if(isMostlyPrintable(payload)) {\n            root.addProperty("jenis", "teks");\n            root.addProperty("data_teks", body);\n        } else {\n            root.addProperty("jenis", "biner_atau_terenkripsi");\n            root.addProperty("data_base64", Base64.encodeToString(payload, Base64.NO_WRAP));\n            JsonArray readable = extractReadableStrings(payload);\n            if(readable.size() > 0)\n                root.add("teks_terbaca", readable);\n            root.addProperty("catatan", mContext.getString(R.string.json_export_binary_note));\n        }\n\n        return prettyGson.toJson(root);\n    }\n\n    private void handleCopyExportButtons(PayloadViewHolder holder, boolean is_export) {\n        if(is_export && (mExportHandler == null))\n            return;\n\n        int payload_pos = holder.getAbsoluteAdapterPosition();\n        AdapterChunk exportChunk = getItem(payload_pos).adaptChunk;\n        if(exportChunk == null)\n            return;\n\n        // The save icon is intentionally simple: it always creates one valid JSON file.\n        // Raw bytes are preserved as Base64 when the payload itself is not text/JSON.\n        if(is_export) {\n            mExportHandler.exportPayload(buildJsonExport(exportChunk));\n            return;\n        }\n'''
if adapter.count(anchor) != 1:
    raise RuntimeError("PayloadAdapter.java: export handler anchor not found")
adapter = adapter.replace(anchor, replacement, 1)
write(adapter_rel, adapter)

# Detect structured JSON strings and save them with a .json extension and UTF-8 encoding.
export_rel = "app/src/main/java/com/emanuelef/remote_capture/activities/PayloadExportActivity.java"
exporter = read(export_rel)
exporter = exporter.replace(
    "import com.emanuelef.remote_capture.adapters.PayloadAdapter;\n",
    "import com.emanuelef.remote_capture.adapters.PayloadAdapter;\nimport com.google.gson.JsonParser;\nimport com.google.gson.JsonSyntaxException;\n",
    1,
)
exporter = exporter.replace(
    "import java.io.OutputStreamWriter;\n",
    "import java.io.OutputStreamWriter;\nimport java.nio.charset.StandardCharsets;\n",
    1,
)

old_string_export = '''    @Override\n    public void exportPayload(String payload) {\n        mStringPayloadToExport = payload;\n        mRawPayloadToExport = null;\n\n        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);\n        intent.addCategory(Intent.CATEGORY_OPENABLE);\n        intent.setType("text/plain");\n        intent.putExtra(Intent.EXTRA_TITLE, Utils.getUniqueFileName(this, "txt"));\n\n        Utils.launchFileDialog(this, intent, payloadExportLauncher);\n    }\n'''
new_string_export = '''    @Override\n    public void exportPayload(String payload) {\n        mStringPayloadToExport = payload;\n        mRawPayloadToExport = null;\n\n        boolean isJson = false;\n        try {\n            JsonParser.parseString(payload);\n            isJson = true;\n        } catch (JsonSyntaxException e) {\n            // Keep ordinary text export available for non-JSON callers.\n        }\n\n        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);\n        intent.addCategory(Intent.CATEGORY_OPENABLE);\n        intent.setType(isJson ? "application/json" : "text/plain");\n        intent.putExtra(Intent.EXTRA_TITLE, Utils.getUniqueFileName(this, isJson ? "json" : "txt"));\n\n        Utils.launchFileDialog(this, intent, payloadExportLauncher);\n    }\n'''
if exporter.count(old_string_export) != 1:
    raise RuntimeError("PayloadExportActivity.java: string export anchor not found")
exporter = exporter.replace(old_string_export, new_string_export, 1)
exporter = exporter.replace(
    "try (OutputStreamWriter writer = new OutputStreamWriter(out)) {",
    "try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {",
    1,
)
write(export_rel, exporter)

# Guardrails for CI: the default must not silently create PCAP, and the manual
# save path must be JSON + UTF-8.
assert "DEFAULT_DUMP_MODE = DUMP_NONE" in read(prefs_rel)
assert 'mExportHandler.exportPayload(buildJsonExport(exportChunk));' in read(adapter_rel)
assert 'Utils.getUniqueFileName(this, isJson ? "json" : "txt")' in read(export_rel)
assert "StandardCharsets.UTF_8" in read(export_rel)

print("JSON manual export overlay applied")

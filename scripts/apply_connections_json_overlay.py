#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

PINNED_COMMIT = "fc9fe0c56908846c9f1817ed3a50618ab6fb5556"

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_connections_json_overlay.py <pcapdroid-root>")

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

# The toolbar save action in the connections list is CSV upstream. Change it to JSON.
fragment_rel = "app/src/main/java/com/emanuelef/remote_capture/fragments/ConnectionsFragment.java"
fragment = read(fragment_rel)
fragment = fragment.replace(
    'String fname = Utils.getExportFileName(requireContext(), "csv");',
    'String fname = Utils.getExportFileName(requireContext(), "json");',
    1,
)
fragment = fragment.replace('intent.setType("*/*");', 'intent.setType("application/json");', 1)
fragment = fragment.replace('Log.d(TAG, "Writing CSV file: " + mCsvFname);',
                            'Log.d(TAG, "Writing JSON file: " + mCsvFname);', 1)
fragment = fragment.replace('String dump = mAdapter.dumpConnectionsCsv(mActionMode != null);',
                            'String dump = mAdapter.dumpConnectionsJson(mActionMode != null);', 1)
write(fragment_rel, fragment)

# Add a structured JSON exporter to the connections adapter, including captured payloads.
adapter_rel = "app/src/main/java/com/emanuelef/remote_capture/adapters/ConnectionsAdapter.java"
adapter = read(adapter_rel)
adapter = adapter.replace(
    "import android.util.SparseIntArray;\n",
    "import android.util.SparseIntArray;\nimport android.util.Base64;\n",
    1,
)
adapter = adapter.replace(
    "import com.emanuelef.remote_capture.model.ConnectionDescriptor;\n",
    "import com.emanuelef.remote_capture.model.ConnectionDescriptor;\nimport com.emanuelef.remote_capture.model.PayloadChunk;\n",
    1,
)
adapter = adapter.replace(
    "import java.util.ArrayList;\n",
    "import java.nio.charset.StandardCharsets;\nimport java.util.ArrayList;\n",
    1,
)
adapter = adapter.replace(
    "import java.util.Arrays;\n",
    "import java.util.Arrays;\n\nimport com.google.gson.GsonBuilder;\nimport com.google.gson.JsonArray;\nimport com.google.gson.JsonElement;\nimport com.google.gson.JsonObject;\nimport com.google.gson.JsonParser;\nimport com.google.gson.JsonSyntaxException;\n",
    1,
)

anchor = '''    public String dumpConnectionsCsv(boolean selectedOnly) {\n'''
method = r'''    private JsonElement tryParseJsonPayload(String text) {
        if(text == null)
            return null;

        String trimmed = text.trim();
        if(trimmed.isEmpty())
            return null;

        try {
            return JsonParser.parseString(trimmed);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private boolean isMostlyPrintablePayload(byte[] payload) {
        if((payload == null) || (payload.length == 0))
            return true;

        int printable = 0;
        for(byte raw : payload) {
            int b = raw & 0xFF;
            if((b == 9) || (b == 10) || (b == 13) || ((b >= 32) && (b <= 126)))
                printable++;
        }

        return (printable * 100 / payload.length) >= 80;
    }

    private JsonObject payloadChunkToJson(PayloadChunk chunk) {
        JsonObject out = new JsonObject();
        byte[] payload = (chunk.payload != null) ? chunk.payload : new byte[0];

        out.addProperty("arah", chunk.is_sent ? "kirim" : "terima");
        out.addProperty("waktu_ms", chunk.timestamp);
        out.addProperty("jenis_chunk", chunk.type.toString().toLowerCase());
        out.addProperty("ukuran_byte", payload.length);

        if((chunk.httpMethod != null) && !chunk.httpMethod.isEmpty())
            out.addProperty("metode_http", chunk.httpMethod);
        if((chunk.httpHost != null) && !chunk.httpHost.isEmpty())
            out.addProperty("host_http", chunk.httpHost);
        if((chunk.httpPath != null) && !chunk.httpPath.isEmpty())
            out.addProperty("jalur_http", chunk.httpPath);
        if((chunk.httpQuery != null) && !chunk.httpQuery.isEmpty())
            out.addProperty("query_http", chunk.httpQuery);
        if((chunk.httpContentType != null) && !chunk.httpContentType.isEmpty())
            out.addProperty("tipe_konten", chunk.httpContentType);
        if(chunk.httpResponseCode != 0)
            out.addProperty("kode_respons", chunk.httpResponseCode);

        String decoded = new String(payload, StandardCharsets.UTF_8);
        String body = decoded;
        int sep = decoded.indexOf("\r\n\r\n");
        if(sep >= 0)
            body = decoded.substring(Math.min(sep + 4, decoded.length()));

        JsonElement parsed = tryParseJsonPayload(body);
        if(parsed != null) {
            out.addProperty("format_data", "json");
            out.add("data", parsed);
        } else if(isMostlyPrintablePayload(payload)) {
            out.addProperty("format_data", "teks");
            out.addProperty("data_teks", body);
        } else {
            out.addProperty("format_data", "biner_atau_terenkripsi");
            out.addProperty("data_base64", Base64.encodeToString(payload, Base64.NO_WRAP));
        }

        return out;
    }

    public String dumpConnectionsJson(boolean selectedOnly) {
        JsonObject root = new JsonObject();
        JsonArray connections = new JsonArray();
        AppsResolver resolver = new AppsResolver(mContext);

        root.addProperty("format", "salin-jaringan/koneksi-v1");

        int count = getItemCount();
        for(int i = 0; i < count; i++) {
            ConnectionDescriptor conn = getItem(i);
            if(conn == null)
                continue;
            if(selectedOnly && !mSelectedItems.contains(conn.incr_id))
                continue;

            JsonObject item = new JsonObject();
            AppDescriptor app = resolver.getAppByUid(conn.uid, 0);

            item.addProperty("id", conn.incr_id);
            item.addProperty("uid", conn.uid);
            if(app != null) {
                item.addProperty("aplikasi", app.getName());
                item.addProperty("paket", app.getPackageName());
            }
            item.addProperty("protokol", conn.l7proto);
            item.addProperty("ip_sumber", conn.src_ip);
            item.addProperty("port_sumber", conn.src_port);
            item.addProperty("ip_tujuan", conn.dst_ip);
            item.addProperty("port_tujuan", conn.dst_port);
            if((conn.info != null) && !conn.info.isEmpty())
                item.addProperty("host", conn.info);
            if((conn.url != null) && !conn.url.isEmpty())
                item.addProperty("url", conn.url);
            item.addProperty("byte_kirim", conn.sent_bytes);
            item.addProperty("byte_terima", conn.rcvd_bytes);
            item.addProperty("waktu_awal_ms", conn.first_seen);
            item.addProperty("waktu_akhir_ms", conn.last_seen);
            item.addProperty("status", conn.getStatusLabel(mContext));
            item.addProperty("status_enkripsi", conn.getDecryptionStatusLabel(mContext));

            JsonArray payloads = new JsonArray();
            int payloadCount = conn.getNumPayloadChunks();
            for(int p = 0; p < payloadCount; p++) {
                PayloadChunk chunk = conn.getPayloadChunk(p);
                if(chunk != null)
                    payloads.add(payloadChunkToJson(chunk));
            }
            item.add("muatan", payloads);
            item.addProperty("muatan_terpotong", conn.isPayloadTruncated());
            connections.add(item);
        }

        root.addProperty("jumlah_koneksi", connections.size());
        root.add("koneksi", connections);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

'''
if adapter.count(anchor) != 1:
    raise RuntimeError("ConnectionsAdapter.java: CSV export anchor not found")
adapter = adapter.replace(anchor, method + anchor, 1)
write(adapter_rel, adapter)

# CI guardrails: both save paths must now be JSON, and the connection-list save must no longer call CSV.
assert 'Utils.getExportFileName(requireContext(), "json")' in read(fragment_rel)
assert 'intent.setType("application/json")' in read(fragment_rel)
assert 'dumpConnectionsJson(mActionMode != null)' in read(fragment_rel)
assert 'public String dumpConnectionsJson(boolean selectedOnly)' in read(adapter_rel)
assert 'data_base64' in read(adapter_rel)

print("Connections JSON overlay applied")

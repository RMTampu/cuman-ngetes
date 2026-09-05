#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
errors = []

required = [
    "AGENTS.md",
    "settings.gradle.kts",
    "build.gradle.kts",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/example/trafficmarker/ui/MainActivity.kt",
    "app/src/main/java/com/example/trafficmarker/diagnostic/DiagnosticStore.kt",
    "app/src/main/java/com/example/trafficmarker/net/LocalSocksServer.kt",
    "app/src/main/java/com/example/trafficmarker/net/TrafficBus.kt",
    "app/src/main/java/com/example/trafficmarker/net/UdpGatewayServer.kt",
    "app/src/main/java/com/example/trafficmarker/net/UdpgwProtocol.kt",
    "app/src/main/java/com/example/trafficmarker/store/SessionStore.kt",
    "app/src/main/java/com/example/trafficmarker/engine/QuickMarkerEngine.kt",
    "app/src/main/java/com/example/trafficmarker/engine/LookaheadEngine.kt",
    "app/src/main/java/com/example/trafficmarker/engine/LookaheadProvider.kt",
    "app/src/main/java/com/example/trafficmarker/overlay/MarkerBubbleService.kt",
    "app/src/main/java/com/example/trafficmarker/store/MarkerStore.kt",
    "app/src/main/java/com/example/trafficmarker/store/MarkerBackupManager.kt",
    "app/src/main/java/com/example/trafficmarker/store/AlertManager.kt",
]
for rel in required:
    if not (root / rel).is_file():
        errors.append(f"missing: {rel}")

for xml in root.glob("app/src/main/**/*.xml"):
    try:
        ET.parse(xml)
    except Exception as e:
        errors.append(f"invalid XML {xml.relative_to(root)}: {e}")

manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for permission in ["android.permission.INTERNET", "android.permission.FOREGROUND_SERVICE", "android.permission.SYSTEM_ALERT_WINDOW"]:
    if permission not in manifest:
        errors.append(f"manifest permission missing: {permission}")

app_gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
checks = {
    "targetSdk Android 11": r"targetSdk\s*=\s*30",
    "socks dependency pinned": r'com\.ooimi\.library:socks:1\.1\.1',
    "application id": r'applicationId\s*=\s*"com\.example\.trafficmarker"',
    "arm64 ABI filter": r'abiFilters\s*\+=\s*listOf\("arm64-v8a"\)',
}
for name, pattern in checks.items():
    if not re.search(pattern, app_gradle):
        errors.append(f"build config check failed: {name}")

main = (root / "app/src/main/java/com/example/trafficmarker/ui/MainActivity.kt").read_text(encoding="utf-8")
for token in ["SocksProxy.setAppList(mutableListOf(item.packageName))", "SocksProxy.setProxyModel(ProxyModel.WHITE_LIST)", "SocksProxy.start(this)", "LocalSocksServer.start()", "UdpGatewayServer.start(dns)", "SessionStore.start(clearPrevious = true)", "MarkerBubbleService.start(this)", "DiagnosticStore.reset(item.packageName", "PENGECEKAN DATA", "copyDiagnostics", "Simpan Penanda", "Load Penanda", "saveMarkersToDownload", "openMarkerSavePicker"]:
    if token not in main:
        errors.append(f"capture route missing: {token}")

socks = (root / "app/src/main/java/com/example/trafficmarker/net/LocalSocksServer.kt").read_text(encoding="utf-8")
for token in ["handleConnect", "handleUdpAssociate", "Direction.OUT", "Direction.IN", "Direction.UDP_OUT", "Direction.UDP_IN"]:
    if token not in socks:
        errors.append(f"forwarding component missing: {token}")

store = (root / "app/src/main/java/com/example/trafficmarker/store/MarkerStore.kt").read_text(encoding="utf-8")
for token in ["SharedPreferences", "findMatch", "5000L"]:
    if token not in store:
        errors.append(f"marker persistence/debounce missing: {token}")

if "SSL" in socks or "X509" in socks or "TrustManager" in socks:
    errors.append("unexpected TLS interception primitive detected")

udpgw = (root / "app/src/main/java/com/example/trafficmarker/net/UdpGatewayServer.kt").read_text(encoding="utf-8")
for token in ["const val PORT = 7300", "127.0.0.1", "UdpgwProtocol.readFrame", "DatagramSocket", "Direction.UDP_OUT", "Direction.UDP_IN"]:
    if token not in udpgw:
        errors.append(f"udpgw forwarding component missing: {token}")

bubble = (root / "app/src/main/java/com/example/trafficmarker/overlay/MarkerBubbleService.kt").read_text(encoding="utf-8")
for token in ["TYPE_APPLICATION_OVERLAY", "TANDAI SEKARANG", "QuickMarkerEngine.choose", "SessionStore.recent(2000)", "PENGECEKAN DATA", "DiagnosticStore.snapshot"]:
    if token not in bubble:
        errors.append(f"bubble component missing: {token}")

lookahead = (root / "app/src/main/java/com/example/trafficmarker/engine/LookaheadEngine.kt").read_text(encoding="utf-8")
for token in ["EXACT", "ESTIMATED", "UNAVAILABLE", "relativeStep = index + 1"]:
    if token not in lookahead:
        errors.append(f"lookahead engine missing: {token}")

provider = (root / "app/src/main/java/com/example/trafficmarker/engine/LookaheadProvider.kt").read_text(encoding="utf-8")
for token in ["MIN_WINDOW = 100", "MAX_WINDOW = 300", "coerceIn(MIN_WINDOW, MAX_WINDOW)", "LookaheadProvider"]:
    if token not in provider:
        errors.append(f"lookahead provider contract missing: {token}")

diagnostic = (root / "app/src/main/java/com/example/trafficmarker/diagnostic/DiagnosticStore.kt").read_text(encoding="utf-8")
for token in ["socksClients", "tcpConnectOk", "udpOutPackets", "busEvents", "12 LOG TERAKHIR"]:
    if token not in diagnostic:
        errors.append(f"diagnostic component missing: {token}")

if "DiagnosticStore.socksAccepted" not in socks or "DiagnosticStore.tcpConnect" not in socks:
    errors.append("SOCKS/TCP diagnostic instrumentation missing")

if "DiagnosticStore.udpGatewayAccepted" not in udpgw or "DiagnosticStore.udpPacket" not in udpgw:
    errors.append("UDP diagnostic instrumentation missing")

traffic_bus = (root / "app/src/main/java/com/example/trafficmarker/net/TrafficBus.kt").read_text(encoding="utf-8")
if "DiagnosticStore.busEvent" not in traffic_bus:
    errors.append("TrafficBus diagnostic instrumentation missing")

backup = (root / "app/src/main/java/com/example/trafficmarker/store/MarkerBackupManager.kt").read_text(encoding="utf-8")
for token in ["TrafficMarkerSave", "MediaStore.Downloads.RELATIVE_PATH", "traffic-marker-save", "loadFromUri", "MarkerStore.importUnique"]:
    if token not in backup:
        errors.append(f"marker backup component missing: {token}")

if "fun importUnique" not in store:
    errors.append("marker safe-import missing")

if ".overlay.MarkerBubbleService" not in manifest:
    errors.append("bubble service missing from manifest")

if errors:
    print("STATIC_VERIFY: FAIL")
    for e in errors:
        print("-", e)
    sys.exit(1)

print("STATIC_VERIFY: PASS")
print("targetSdk=30; diagnostics; live bubble; marker save/load in Download/TrafficMarkerSave; lookahead engine present")

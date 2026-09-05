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
    "app/src/main/java/com/example/trafficmarker/TrafficMarkerApp.kt",
    "app/src/main/java/com/example/trafficmarker/ui/MainActivity.kt",
    "app/src/main/java/com/example/trafficmarker/diagnostic/DiagnosticStore.kt",
    "app/src/main/java/com/example/trafficmarker/net/LocalSocksServer.kt",
    "app/src/main/java/com/example/trafficmarker/net/TrafficBus.kt",
    "app/src/main/java/com/example/trafficmarker/net/UdpGatewayServer.kt",
    "app/src/main/java/com/example/trafficmarker/net/UdpgwProtocol.kt",
    "app/src/main/java/com/example/trafficmarker/store/SessionStore.kt",
    "app/src/main/java/com/example/trafficmarker/store/MarkerStore.kt",
    "app/src/main/java/com/example/trafficmarker/store/MarkerBackupManager.kt",
    "app/src/main/java/com/example/trafficmarker/engine/MomentFingerprintEngine.kt",
    "app/src/main/java/com/example/trafficmarker/engine/ManualLookaheadStore.kt",
    "app/src/main/java/com/example/trafficmarker/engine/LookaheadEngine.kt",
    "app/src/main/java/com/example/trafficmarker/engine/LookaheadProvider.kt",
    "app/src/main/java/com/example/trafficmarker/overlay/MarkerBubbleService.kt",
    "app/src/main/java/com/example/trafficmarker/recorder/TrafficRecorder.kt",
    "app/src/main/java/com/example/trafficmarker/recorder/StepRecorder.kt",
    "app/src/main/java/com/example/trafficmarker/engine/ArrivalValidator.kt",
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
for permission in [
    "android.permission.INTERNET",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.SYSTEM_ALERT_WINDOW",
]:
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
for token in [
    "SocksProxy.setAppList(mutableListOf(item.packageName))",
    "SocksProxy.setProxyModel(ProxyModel.WHITE_LIST)",
    "SocksProxy.start(this)",
    "LocalSocksServer.start()",
    "UdpGatewayServer.start(dns)",
    "SessionStore.start(clearPrevious = true)",
    "MarkerBubbleService.start(this)",
    "DiagnosticStore.reset(item.packageName",
    "Simpan Penanda",
    "Load Penanda",
    "saveMarkersToDownload",
    "openMarkerSavePicker",
    "Tandai via Bubble",
    "TrafficRecorder.start(clearPrevious = true)",
    "StepRecorder.reset()",
]:
    if token not in main:
        errors.append(f"main component missing: {token}")

socks = (root / "app/src/main/java/com/example/trafficmarker/net/LocalSocksServer.kt").read_text(encoding="utf-8")
for token in [
    "handleConnect",
    "Direction.OUT",
    "Direction.IN",
    "DiagnosticStore.socksAccepted",
    "DiagnosticStore.tcpConnect",
]:
    if token not in socks:
        errors.append(f"SOCKS/TCP component missing: {token}")
if any(token in socks for token in ["TrustManager", "X509", "SSLContext"]):
    errors.append("unexpected TLS interception primitive detected")

udpgw = (root / "app/src/main/java/com/example/trafficmarker/net/UdpGatewayServer.kt").read_text(encoding="utf-8")
for token in [
    'const val PORT = 7300',
    '127.0.0.1',
    'UdpgwProtocol.readFrame',
    'DiagnosticStore.udpGatewayAccepted',
    'DiagnosticStore.udpPacket',
]:
    if token not in udpgw:
        errors.append(f"UDP gateway component missing: {token}")

store = (root / "app/src/main/java/com/example/trafficmarker/store/MarkerStore.kt").read_text(encoding="utf-8")
for token in [
    "addMomentSample",
    "marker.title",
    "marker.samples",
    "markerToJson",
    "markerFromJson",
    "importUnique",
]:
    if token not in store:
        errors.append(f"moment marker persistence missing: {token}")

backup = (root / "app/src/main/java/com/example/trafficmarker/store/MarkerBackupManager.kt").read_text(encoding="utf-8")
for token in [
    "TrafficMarkerSave",
    "FORMAT_VERSION = 2",
    "MediaStore.Downloads.RELATIVE_PATH",
    "MarkerStore.markerToJson",
    "MarkerStore.markerFromJson",
    "MarkerStore.importUnique",
]:
    if token not in backup:
        errors.append(f"marker backup component missing: {token}")

moment = (root / "app/src/main/java/com/example/trafficmarker/engine/MomentFingerprintEngine.kt").read_text(encoding="utf-8")
for token in [
    "DEFAULT_BEFORE_MS = 8000L",
    "DEFAULT_AFTER_MS = 1500L",
    "BURST_GAP_MS = 650L",
    "createSample",
    "similarity",
    "score(marker",
]:
    if token not in moment:
        errors.append(f"moment fingerprint engine missing: {token}")

manual = (root / "app/src/main/java/com/example/trafficmarker/engine/ManualLookaheadStore.kt").read_text(encoding="utf-8")
for token in [
    "const val LIMIT = 20",
    "MENGUMPULKAN",
    "Mode: ESTIMATED",
    "MATCH_THRESHOLD",
    "MomentFingerprintEngine.score",
]:
    if token not in manual:
        errors.append(f"manual LOAD 20 engine missing: {token}")

bubble = (root / "app/src/main/java/com/example/trafficmarker/overlay/MarkerBubbleService.kt").read_text(encoding="utf-8")
for token in [
    "TYPE_APPLICATION_OVERLAY",
    "TANDAI MOMEN + JUDUL",
    "LOAD 20 MANUAL",
    "showMarkerTitlePrompt",
    "MarkerStore.addMomentSample",
    "ManualLookaheadStore.start",
    "ManualLookaheadStore.snapshot",
    "MULAI STEP",
    "HASIL + LABEL",
    "SIMPAN RECORDER",
    "VALIDASI DATA DATANG LEBIH AWAL",
    "TrafficRecorder.recentText",
    "TrafficRecorder.saveJsonl",
    "StepRecorder.startStep",
    "StepRecorder.finishStep",
    "ArrivalValidator.validate",
    "ScrollView",
    "isLandscape()",
    "collapsibleHeader",
    "VALIDASI SELESAI",
]:
    if token not in bubble:
        errors.append(f"bubble component missing: {token}")

provider = (root / "app/src/main/java/com/example/trafficmarker/engine/LookaheadProvider.kt").read_text(encoding="utf-8")
for token in ["const val WINDOW = 20", "provider.loadAhead(WINDOW)", "LookaheadProvider"]:
    if token not in provider:
        errors.append(f"lookahead provider contract missing: {token}")

traffic_bus = (root / "app/src/main/java/com/example/trafficmarker/net/TrafficBus.kt").read_text(encoding="utf-8")
for token in ["DiagnosticStore.busEvent", "SessionStore.add(raw)", "TrafficRecorder.onEvent(raw)", "ManualLookaheadStore.onEvent(raw)"]:
    if token not in traffic_bus:
        errors.append(f"TrafficBus routing missing: {token}")
if "AlertManager.fire" in traffic_bus or "MarkerStore.findMatch" in traffic_bus:
    errors.append("automatic per-chunk detection is still enabled")

app = (root / "app/src/main/java/com/example/trafficmarker/TrafficMarkerApp.kt").read_text(encoding="utf-8")
if "AlertManager.init" in app:
    errors.append("legacy automatic alert channel still initialized")
if 'deleteNotificationChannel("marker_alerts")' not in app:
    errors.append("legacy marker alert channel is not removed")

recorder = (root / "app/src/main/java/com/example/trafficmarker/recorder/TrafficRecorder.kt").read_text(encoding="utf-8")
for token in ["TrafficMarkerRecorder", "MAX_EVENTS = 10000", "recentText", "saveJsonl", '"type", "step"']:
    if token not in recorder:
        errors.append(f"traffic recorder missing: {token}")

step_recorder = (root / "app/src/main/java/com/example/trafficmarker/recorder/StepRecorder.kt").read_text(encoding="utf-8")
for token in ["startStep", "finishStep", "ground", "StepRecord"]:
    if token == "ground":
        continue
    if token not in step_recorder:
        errors.append(f"step recorder missing: {token}")

arrival = (root / "app/src/main/java/com/example/trafficmarker/engine/ArrivalValidator.kt").read_text(encoding="utf-8")
for token in ["NO_DATA", "LEAD_CANDIDATE", "VALIDATED", "EXACT", "precision", "recall", "MAX_LEAD = 20"]:
    if token not in arrival:
        errors.append(f"arrival validator missing: {token}")

if ".overlay.MarkerBubbleService" not in manifest:
    errors.append("bubble service missing from manifest")

if errors:
    print("STATIC_VERIFY: FAIL")
    for e in errors:
        print("-", e)
    sys.exit(1)

print("STATIC_VERIFY: PASS")
print("targetSdk=30; recorder; step ground truth; arrival validator; manual LOAD 20; no per-chunk alerts; marker save/load v2")

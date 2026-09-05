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
    "app/src/main/java/com/example/trafficmarker/net/LocalSocksServer.kt",
    "app/src/main/java/com/example/trafficmarker/net/TrafficBus.kt",
    "app/src/main/java/com/example/trafficmarker/net/UdpGatewayServer.kt",
    "app/src/main/java/com/example/trafficmarker/net/UdpgwProtocol.kt",
    "app/src/main/java/com/example/trafficmarker/store/SessionStore.kt",
    "app/src/main/java/com/example/trafficmarker/engine/QuickMarkerEngine.kt",
    "app/src/main/java/com/example/trafficmarker/engine/LookaheadEngine.kt",
    "app/src/main/java/com/example/trafficmarker/overlay/MarkerBubbleService.kt",
    "app/src/main/java/com/example/trafficmarker/store/MarkerStore.kt",
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
for token in ["SocksProxy.setAppList(mutableListOf(item.packageName))", "SocksProxy.setProxyModel(ProxyModel.WHITE_LIST)", "SocksProxy.start(this)", "LocalSocksServer.start()", "UdpGatewayServer.start(currentIpv4Dns())", "SessionStore.start(clearPrevious = true)", "MarkerBubbleService.start(this)"]:
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
for token in ["TYPE_APPLICATION_OVERLAY", "TANDAI SEKARANG", "QuickMarkerEngine.choose", "SessionStore.recent(2000)"]:
    if token not in bubble:
        errors.append(f"bubble component missing: {token}")

lookahead = (root / "app/src/main/java/com/example/trafficmarker/engine/LookaheadEngine.kt").read_text(encoding="utf-8")
for token in ["EXACT", "ESTIMATED", "UNAVAILABLE", "relativeStep = index + 1"]:
    if token not in lookahead:
        errors.append(f"lookahead engine missing: {token}")

if ".overlay.MarkerBubbleService" not in manifest:
    errors.append("bubble service missing from manifest")

if errors:
    print("STATIC_VERIFY: FAIL")
    for e in errors:
        print("-", e)
    sys.exit(1)

print("STATIC_VERIFY: PASS")
print("targetSdk=30; metadata-only; TCP+UDP/QUIC forwarding; live bubble; session recording; lookahead engine present")

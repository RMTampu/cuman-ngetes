#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
errors = []

required = [
    "AGENTS.md",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/example/cardprobe/CardProbeApp.kt",
    "app/src/main/java/com/example/cardprobe/model/Models.kt",
    "app/src/main/java/com/example/cardprobe/engine/PresenceAnalyzer.kt",
    "app/src/main/java/com/example/cardprobe/probe/ProbeStore.kt",
    "app/src/main/java/com/example/cardprobe/diagnostic/ProbeDiagnostics.kt",
    "app/src/main/java/com/example/cardprobe/net/ProbeBus.kt",
    "app/src/main/java/com/example/cardprobe/net/LocalSocksServer.kt",
    "app/src/main/java/com/example/cardprobe/net/UdpGatewayServer.kt",
    "app/src/main/java/com/example/cardprobe/net/UdpgwProtocol.kt",
    "app/src/main/java/com/example/cardprobe/overlay/ProbeBubbleService.kt",
    "app/src/main/java/com/example/cardprobe/ui/MainActivity.kt",
]
for rel in required:
    if not (root / rel).is_file():
        errors.append("missing: " + rel)

legacy = root / "app/src/main/java/com/example/trafficmarker"
if legacy.exists():
    errors.append("legacy Traffic Marker source still present")

for xml in root.glob("app/src/main/**/*.xml"):
    try:
        ET.parse(xml)
    except Exception as e:
        errors.append("invalid XML " + str(xml.relative_to(root)) + ": " + str(e))

gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
for name, pattern in {
    "targetSdk 30": r"targetSdk\s*=\s*30",
    "arm64 only": r'abiFilters\s*\+=\s*listOf\("arm64-v8a"\)',
    "application id": r'applicationId\s*=\s*"com\.example\.cardprobe"',
    "socks dependency": r'com\.ooimi\.library:socks:1\.1\.1',
    "version 0.1.1": r'versionName\s*=\s*"0\.1\.1"',
}.items():
    if not re.search(pattern, gradle):
        errors.append("build config missing: " + name)

all_source = "\n".join(
    p.read_text(encoding="utf-8")
    for p in (root / "app/src/main/java/com/example/cardprobe").rglob("*.kt")
)

for banned in ["TrustManager", "X509TrustManager", "SSLContext", "LOAD 20", "ArrivalValidator", "MarkerStore"]:
    if banned in all_source:
        errors.append("banned legacy/interception token: " + banned)

for token in [
    "PREFETCH_CANDIDATE",
    "PREFETCH_CROSS_SESSION",
    "REVEAL_REQUIRES_NETWORK",
    "INCONCLUSIVE",
    "KARTU TERTUTUP / DEAL",
    "SESI BARU (TARGET REOPEN)",
    "ProbeStore.finishReveal",
    "ProbeStore.exportCsv",
    "dealTopEndpoint",
    "revealTopEndpoint",
    "Download/",
    "CardPresenceProbe",
    "SocksProxy.setAppList",
    "ProxyModel.WHITE_LIST",
    "UdpGatewayServer.start",
    "LocalSocksServer.start",
]:
    if token not in all_source:
        errors.append("probe component missing: " + token)

manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in [
    "android.permission.INTERNET",
    "android.permission.SYSTEM_ALERT_WINDOW",
    ".overlay.ProbeBubbleService",
]:
    if token not in manifest:
        errors.append("manifest missing: " + token)

if errors:
    print("STATIC_VERIFY: FAIL")
    for e in errors:
        print("-", e)
    sys.exit(1)

print("STATIC_VERIFY: PASS")
print("Card Presence Probe v0.1.1; cross-session validation; endpoint metadata; CSV export; no TLS decryption")

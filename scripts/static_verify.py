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
    "app/src/main/java/com/example/pokeredge/PokerEdgeApp.kt",
    "app/src/main/java/com/example/pokeredge/model/Card.kt",
    "app/src/main/java/com/example/pokeredge/model/PokerModels.kt",
    "app/src/main/java/com/example/pokeredge/engine/HandEvaluator.kt",
    "app/src/main/java/com/example/pokeredge/engine/EquityCalculator.kt",
    "app/src/main/java/com/example/pokeredge/engine/PokerAdvisor.kt",
    "app/src/main/java/com/example/pokeredge/store/GameStateStore.kt",
    "app/src/main/java/com/example/pokeredge/overlay/PokerOverlayService.kt",
    "app/src/main/java/com/example/pokeredge/ui/MainActivity.kt",
]
for rel in required:
    if not (root / rel).is_file():
        errors.append("missing: " + rel)

for legacy in [
    root / "app/src/main/java/com/example/cardprobe",
    root / "app/src/main/java/com/example/trafficmarker",
]:
    if legacy.exists():
        errors.append("legacy source still present: " + str(legacy.relative_to(root)))

for xml in root.glob("app/src/main/**/*.xml"):
    try:
        ET.parse(xml)
    except Exception as e:
        errors.append("invalid XML " + str(xml.relative_to(root)) + ": " + str(e))

gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
for name, pattern in {
    "targetSdk 30": r"targetSdk\s*=\s*30",
    "application id": r'applicationId\s*=\s*"com\.example\.pokeredge"',
    "version 0.2.0": r'versionName\s*=\s*"0\.2\.0"',
}.items():
    if not re.search(pattern, gradle):
        errors.append("build config missing: " + name)

all_source = "\n".join(
    p.read_text(encoding="utf-8")
    for p in (root / "app/src/main/java/com/example/pokeredge").rglob("*.kt")
)

for banned in [
    "SocksProxy",
    "VpnService",
    "TrustManager",
    "X509TrustManager",
    "SSLContext",
    "PREFETCH_CANDIDATE",
    "LOAD 20",
]:
    if banned in all_source:
        errors.append("banned legacy/interception token: " + banned)

for token in [
    "PokerAdvisor.analyze",
    "EquityCalculator.estimate",
    "HandEvaluator.evaluate",
    "improvementOuts",
    "POT +",
    "CALL +",
    "LAWAN +",
    "+ HOLE",
    "+ BOARD",
    "ANALYZE",
    "RESET HAND",
    "GameStateStore.addHole",
    "GameStateStore.addBoard",
]:
    if token not in all_source:
        errors.append("poker component missing: " + token)

manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in [
    "android.permission.SYSTEM_ALERT_WINDOW",
    ".overlay.PokerOverlayService",
]:
    if token not in manifest:
        errors.append("manifest missing: " + token)

if errors:
    print("STATIC_VERIFY: FAIL")
    for e in errors:
        print("-", e)
    sys.exit(1)

print("STATIC_VERIFY: PASS")
print("Poker Edge Companion v0.2.0; visible/manual inputs only; equity + pot odds + outs + overlay")

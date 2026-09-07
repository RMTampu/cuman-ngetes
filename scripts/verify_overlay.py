#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_overlay.py <upstream-root>")

root = Path(sys.argv[1]).resolve()

checks = {
    "app/src/main/AndroidManifest.xml": [
        'android:allowBackup="false"',
    ],
    "Bcore/src/main/java/top/niunaijun/blackbox/virtual/VirtualIdentityManager.java": [
        'KEY_PREFIX + userId + "|" + packageName',
        "rotateAndroidId",
        "SecureRandom",
    ],
    "Bcore/src/main/java/top/niunaijun/blackbox/fake/service/AndroidIdProxy.java": [
        "VirtualIdentityManager.getAndroidIdForCurrentGuest()",
    ],
    "app/src/main/res/menu/app_menu.xml": [
        "app_identity_reset",
    ],
    "app/src/main/java/top/niunaijun/blackboxa/view/apps/AppsFragment.kt": [
        "BlackBoxCore.get().stopPackage(info.packageName, userID)",
        "VirtualIdentityManager.rotateAndroidId(info.packageName, userID)",
    ],
}

for rel, needles in checks.items():
    path = root / rel
    if not path.exists():
        raise SystemExit(f"missing required file: {rel}")
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"{rel}: missing required marker: {needle}")

identity = (root / "Bcore/src/main/java/top/niunaijun/blackbox/virtual/VirtualIdentityManager.java").read_text(encoding="utf-8").lower()
for forbidden in ("imei", "meid", "sim_serial", "subscriberid"):
    if forbidden in identity:
        raise SystemExit(f"forbidden hardware identifier marker found: {forbidden}")

fragment = (root / "app/src/main/java/top/niunaijun/blackboxa/view/apps/AppsFragment.kt").read_text(encoding="utf-8")
start = fragment.index("private fun resetVirtualIdentity")
end = fragment.index("private fun clearApk", start)
rotation_block = fragment[start:end]
if "clearPackage" in rotation_block or "clearApkData" in rotation_block or "unInstall" in rotation_block:
    raise SystemExit("virtual-ID rotation must not clear or uninstall app data")

print("Space Lab overlay verification PASS")

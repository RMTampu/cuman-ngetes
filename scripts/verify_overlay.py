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
    "Bcore/src/main/java/top/niunaijun/blackbox/fake/service/context/providers/SystemProviderStub.java": [
        "\"android_id\".equals(arg)",
        "VirtualIdentityManager.getAndroidIdForCurrentGuest()",
        "result.putString(\"value\"",
    ],
    "app/src/main/res/menu/app_menu.xml": [
        "app_identity_reset",
    ],
    "app/src/main/java/top/niunaijun/blackboxa/view/apps/AppsFragment.kt": [
        "BlackBoxCore.get().stopPackage(info.packageName, userID)",
        "VirtualIdentityManager.rotateAndroidId(info.packageName, userID)",
    ],
    "Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IConnectivityManagerProxy.java": [
        "BRIConnectivityManagerStub.get().asInterface",
        "replaceSystemService(Context.CONNECTIVITY_SERVICE)",
        "@ScanClass(VpnCommonProxy.class)",
    ],
    "app/src/main/java/top/niunaijun/blackboxa/view/main/BlackBoxLoader.kt": [
        "fun useVpnNetwork(): Boolean",
        "override fun isUseVpnNetwork(): Boolean",
        "return false",
    ],
    "app/src/main/res/xml/setting.xml": [
        'android:key="network_info"',
        '@string/network_mode_summary',
        '@string/send_logs',
    ],
    "app/src/main/res/values/strings.xml": [
        '<string name="setting">Pengaturan</string>',
        '<string name="other">Lainnya</string>',
        '<string name="network_mode">Jaringan</string>',
        '<string name="virtual_id_reset">Ganti ID Virtual</string>',
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

network_proxy = (root / "Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IConnectivityManagerProxy.java").read_text(encoding="utf-8")
for forbidden in (
    "8.8.8.8",
    "8.8.4.4",
    "getPrivateDnsServerName",
    "isPrivateDnsActive",
    "createLinkProperties",
    "Created mock Network",
):
    if forbidden in network_proxy:
        raise SystemExit(f"network proxy still contains synthetic/broken networking marker: {forbidden}")

loader = (root / "app/src/main/java/top/niunaijun/blackboxa/view/main/BlackBoxLoader.kt").read_text(encoding="utf-8")
if "return try {\n            mUseVpnNetwork" in loader:
    raise SystemExit("VPN mode can still be enabled from saved preferences")
if "this.mUseVpnNetwork = enable" in loader:
    raise SystemExit("VPN toggle still persists enabled state")
if "return try {\n                                        mUseVpnNetwork" in loader:
    raise SystemExit("ClientConfiguration still exposes VPN mode")

setting_fragment = (root / "app/src/main/java/top/niunaijun/blackboxa/view/setting/SettingFragment.kt").read_text(encoding="utf-8")
if 'findPreference("use_vpn_network")' in setting_fragment:
    raise SystemExit("obsolete VPN switch still wired in settings UI")

for rel in (
    "app/src/main/res/values-zh-rCN/strings.xml",
    "app/src/main/res/values-zh-rTW/strings.xml",
):
    if (root / rel).exists():
        raise SystemExit(f"legacy locale can override Indonesian UI: {rel}")

strings = (root / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
for legacy in (
    ">Setting<",
    ">Others<",
    ">Choose App<",
    ">Installed App<",
    ">Hide Root<",
    ">Send Logs<",
):
    if legacy in strings:
        raise SystemExit(f"legacy English UI string remains: {legacy}")

print("Space Lab overlay verification PASS")

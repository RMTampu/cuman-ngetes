#!/usr/bin/env python3
from copy import deepcopy
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PINNED_COMMIT = "dbfd5e04f560adf02f88c8fd0a8d3588e39fa71f"

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_network_capture_overlay.py <pcapdroid-root>")

root = Path(sys.argv[1]).resolve()


def read(rel: str) -> str:
    return (root / rel).read_text(encoding="utf-8")


def write(rel: str, data: str) -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(data, encoding="utf-8")


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

# Branding, Android 11/arm64 delivery, and reduced package footprint.
build_gradle = read("app/build.gradle")
build_gradle = build_gradle.replace(
    'applicationId "com.emanuelef.remote_capture"',
    'applicationId "com.rmtampu.salinjaringan"',
    1,
)
build_gradle = build_gradle.replace(
    "        targetSdk 37\n",
    "        targetSdk 37\n\n        ndk {\n            abiFilters 'arm64-v8a'\n        }\n",
    1,
)
build_gradle, n = re.subn(
    r'resourceConfigurations \+= \["en".*?"zh-rCN"\]',
    'resourceConfigurations += ["in"]',
    build_gradle,
    count=1,
    flags=re.S,
)
if n != 1:
    raise RuntimeError("app/build.gradle: resourceConfigurations block not found")
write("app/build.gradle", build_gradle)

replace_once(
    "app/src/main/AndroidManifest.xml",
    'android:allowBackup="true"',
    'android:allowBackup="false"',
)

# Force the Indonesian translation as the default language while preserving
# non-translatable constants from the upstream default resource file.
base_strings = root / "app/src/main/res/values/strings.xml"
id_strings = root / "app/src/main/res/values-in/strings.xml"
base_tree = ET.parse(base_strings)
id_tree = ET.parse(id_strings)
base_root = base_tree.getroot()
id_root = id_tree.getroot()

translated = {}
for child in id_root:
    name = child.attrib.get("name")
    if name:
        translated[(child.tag, name)] = child

for idx, child in enumerate(list(base_root)):
    name = child.attrib.get("name")
    key = (child.tag, name) if name else None
    if key in translated:
        base_root.remove(child)
        base_root.insert(idx, deepcopy(translated[key]))


def set_string(name: str, value: str, translatable=None) -> None:
    for child in base_root.findall("string"):
        if child.attrib.get("name") == name:
            child.clear()
            child.tag = "string"
            child.attrib["name"] = name
            if translatable is not None:
                child.attrib["translatable"] = "true" if translatable else "false"
            child.text = value
            return
    node = ET.SubElement(base_root, "string", {"name": name})
    if translatable is not None:
        node.attrib["translatable"] = "true" if translatable else "false"
    node.text = value


set_string("pcapdroid_app_name", "Salin Jaringan", False)
set_string("target_apps", "Aplikasi target")
set_string("target_apps_help", "Pilih aplikasi yang trafiknya ingin disalin. Penangkapan tidak akan dimulai sebelum ada aplikasi target yang dipilih.")
set_string("capture_all_apps", "Belum ada aplikasi target. Pilih target terlebih dahulu.")
set_string("start_button", "Mulai Menyalin")
set_string("stop_button", "Berhenti")
set_string("pcap_file", "Simpan Salinan")
set_string("pcap_file_info", "Simpan salinan trafik aplikasi target ke penyimpanan perangkat.")
set_string("about_text", "Salin Jaringan menyalin trafik aplikasi target secara lokal melalui VPN Android tanpa mengirim data ke server VPN jarak jauh.")
set_string("select_target_first", "Pilih aplikasi target terlebih dahulu.")

ET.indent(base_tree, space="    ")
base_tree.write(base_strings, encoding="utf-8", xml_declaration=True)

# Debug build is deliberately used for test delivery; keep its visible name Indonesian too.
debug_strings = root / "app/src/debug/res/values/strings.xml"
if debug_strings.exists():
    data = debug_strings.read_text(encoding="utf-8")
    data = data.replace("PCAPdroid (beta)", "Salin Jaringan")
    debug_strings.write_text(data, encoding="utf-8")

# Never permit a capture session without a target-app filter. This guarantees
# the app does not silently copy device-wide traffic.
main_rel = "app/src/main/java/com/emanuelef/remote_capture/activities/MainActivity.java"
main = read(main_rel)
needle = '''    public void startCapture() {\n        if (VpnReconnectService.isAvailable())\n'''
replacement = '''    public void startCapture() {\n        if (!Prefs.isAppFilterEnabled(mPrefs) || Prefs.getAppFilterRaw(mPrefs) == null || Prefs.getAppFilterRaw(mPrefs).isEmpty()) {\n            Utils.showToastLong(this, R.string.select_target_first);\n            startActivity(new Intent(this, AppFilterActivity.class));\n            return;\n        }\n\n        if (VpnReconnectService.isAvailable())\n'''
if main.count(needle) != 1:
    raise RuntimeError("MainActivity.java: startCapture anchor not found")
write(main_rel, main.replace(needle, replacement, 1))

# Keep the existing local-VPN packet forwarding engine. TLS decryption remains
# off by default; no certificate/pinning bypass is added by this overlay.
print("Salin Jaringan overlay applied")

#!/usr/bin/env python3
from copy import deepcopy
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PINNED_COMMIT = "fc9fe0c56908846c9f1817ed3a50618ab6fb5556"

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

# Branding, Android 11/arm64 delivery, and Indonesian-only resources.
build_gradle = read("app/build.gradle")
build_gradle = build_gradle.replace(
    'applicationId "com.emanuelef.remote_capture"',
    'applicationId "com.rmtampu.salinjaringan"',
    1,
)
build_gradle = build_gradle.replace(
    "        targetSdk 35\n",
    "        targetSdk 35\n\n        ndk {\n            abiFilters 'arm64-v8a'\n        }\n",
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

# Make PCAP file output the normal behavior. Traffic still goes to its original
# destination; the app only keeps a local packet copy.
prefs_rel = "app/src/main/java/com/emanuelef/remote_capture/model/Prefs.java"
prefs = read(prefs_rel)
prefs = prefs.replace(
    "public static final String DEFAULT_DUMP_MODE = DUMP_NONE;",
    "public static final String DEFAULT_DUMP_MODE = DUMP_PCAP_FILE;",
    1,
)
prefs = prefs.replace('return(p.getString(PREF_FILENAME_PREFIX, "PCAPdroid_"));',
                      'return(p.getString(PREF_FILENAME_PREFIX, "SalinJaringan_"));', 1)
write(prefs_rel, prefs)

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

# Keep user-visible branding consistent.
for elem in base_root.iter():
    if elem.text:
        elem.text = elem.text.replace("PCAPdroid", "Salin Jaringan")
    if elem.tail:
        elem.tail = elem.tail.replace("PCAPdroid", "Salin Jaringan")


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
set_string("target_apps_help", "Pilih aplikasi yang trafiknya ingin disalin. Penyalinan tidak dapat dimulai sebelum ada aplikasi target yang dipilih.")
set_string("capture_all_apps", "Belum ada aplikasi target. Pilih target terlebih dahulu.")
set_string("start_button", "Mulai Menyalin")
set_string("stop_button", "Berhenti")
set_string("pcap_file", "Simpan Salinan")
set_string("pcap_file_info", "Simpan salinan trafik aplikasi target ke lokasi yang Anda pilih.")
set_string("about_text", "Salin Jaringan membuat salinan trafik aplikasi target secara lokal. Tujuan koneksi asli tidak diubah.")
set_string("select_target_first", "Pilih aplikasi target terlebih dahulu.")
set_string("no_activity_file_selection", "Tidak ada aplikasi sistem yang dapat membuka pemilih lokasi penyimpanan.")
set_string("file_saved_with_name", "Salinan disimpan sebagai \"%1$s\".")

ET.indent(base_tree, space="    ")
base_tree.write(base_strings, encoding="utf-8", xml_declaration=True)

# Debug build is deliberately used for test delivery; keep its visible name Indonesian too.
debug_strings = root / "app/src/debug/res/values/strings.xml"
if debug_strings.exists():
    data = debug_strings.read_text(encoding="utf-8")
    data = data.replace("PCAPdroid (beta)", "Salin Jaringan")
    data = data.replace("PCAPdroid", "Salin Jaringan")
    debug_strings.write_text(data, encoding="utf-8")

# Never permit device-wide capture: at least one target app is mandatory.
main_rel = "app/src/main/java/com/emanuelef/remote_capture/activities/MainActivity.java"
main = read(main_rel)
main = main.replace('        setTitle("PCAPdroid");', '        setTitle("Salin Jaringan");', 1)
needle = '''    public void startCapture() {\n        if (VpnReconnectService.isAvailable())\n'''
replacement = '''    public void startCapture() {\n        if (Prefs.getAppFilter(mPrefs).isEmpty()) {\n            Utils.showToastLong(this, R.string.select_target_first);\n            startActivity(new Intent(this, AppFilterActivity.class));\n            return;\n        }\n\n        if (VpnReconnectService.isAvailable())\n'''
if main.count(needle) != 1:
    raise RuntimeError("MainActivity.java: startCapture anchor not found")
write(main_rel, main.replace(needle, replacement, 1))

# No certificate-pinning bypass or anti-abuse bypass is added. HTTPS remains
# encrypted unless the target itself exposes plaintext through supported means.
print("Salin Jaringan overlay applied")

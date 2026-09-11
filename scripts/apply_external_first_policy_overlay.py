#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

PINNED_COMMIT = "fc9fe0c56908846c9f1817ed3a50618ab6fb5556"

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_external_first_policy_overlay.py <pcapdroid-root>")

root = Path(sys.argv[1]).resolve()
head = subprocess.check_output(
    ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
).strip()
if head != PINNED_COMMIT:
    raise RuntimeError(f"unexpected upstream commit: {head}")

strings_path = root / "app/src/main/res/values/strings.xml"
tree = ET.parse(strings_path)
strings_root = tree.getroot()


def set_string(name: str, value: str) -> None:
    for child in strings_root.findall("string"):
        if child.attrib.get("name") == name:
            child.text = value
            return
    raise RuntimeError(f"missing string resource: {name}")


set_string("runtime_data_title", "Data Runtime (Opsional)")
set_string(
    "runtime_data_info",
    "Fitur tambahan. Mode utama Salin Jaringan bekerja dari luar aplikasi target dan tidak membutuhkan rebuild. Data Runtime hanya terisi jika aplikasi target secara sukarela memakai Jembatan Runtime."
)
set_string(
    "runtime_data_empty",
    "Belum ada data runtime. Ini normal jika aplikasi target tidak memakai Jembatan Runtime. Gunakan tangkapan jaringan sebagai mode utama."
)

ET.indent(tree, space="    ")
tree.write(strings_path, encoding="utf-8", xml_declaration=True)

rendered = strings_path.read_text(encoding="utf-8")
assert "Data Runtime (Opsional)" in rendered
assert "tidak membutuhkan rebuild" in rendered
assert "Gunakan tangkapan jaringan sebagai mode utama" in rendered

print("External-first policy overlay applied")

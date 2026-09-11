# cuman-ngetes

Repository uji Android 11 arm64-v8a.

## Space Lab

Build virtual-space/multi-instance tetap dipertahankan dan dibangun oleh workflow `build-apk.yml`.

## Salin Jaringan

Build terpisah untuk menyalin trafik jaringan hanya dari aplikasi target yang dipilih pengguna.

- Bahasa antarmuka: Indonesia.
- Target perangkat: Android 11, arm64-v8a.
- Penangkapan memakai VPN lokal Android; tidak memakai server VPN jarak jauh.
- Aplikasi target wajib dipilih sebelum penangkapan dapat dimulai.
- Salinan dapat disimpan sebagai file PCAP pada penyimpanan perangkat.
- Trafik HTTPS tetap terenkripsi secara normal kecuali pengguna memakai mekanisme dekripsi yang memang didukung target; overlay ini tidak menambahkan bypass certificate pinning, Play Integrity, anti-cheat, atau sistem anti-abuse.
- APK uji diverifikasi tanda tangannya oleh GitHub Actions sebelum diunggah sebagai artifact.

Engine penangkapan dipin pada `NETWORK_CAPTURE.lock` dan berasal dari PCAPdroid (GPL-3.0-or-later). Modifikasi lokal diterapkan oleh `scripts/apply_network_capture_overlay.py` saat CI sehingga repository tetap ringan.

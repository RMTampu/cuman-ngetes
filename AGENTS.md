# AGENTS.md

Sebelum mengubah repository ini, baca file ini penuh.

## Batas dasar
- Target utama: Android 11 (API 30), arm64-compatible.
- Capture hanya metadata trafik; jangan menambahkan dekripsi TLS/HTTPS tersembunyi.
- Jangan memblokir koneksi target secara sengaja; forwarding harus tetap aktif.
- Penanda disimpan setelah satu kali konfigurasi dan alarm berjalan otomatis.
- Tindakan yang menghapus semua penanda harus berasal dari tindakan pengguna.

## Build
- Gunakan GitHub Actions untuk menghasilkan APK uji.
- Jangan memasukkan signing key privat ke repository.

# AGENTS.md

Sebelum mengubah repository ini, baca file ini penuh.

## Tujuan
- Aplikasi aktif adalah Card Presence Probe.
- Target utama Android 11 (API 30), arm64-v8a.
- Tujuan hanya menguji apakah ada indikasi metadata jaringan yang datang lebih awal sebelum reveal kartu pada aplikasi demo/lingkungan yang diizinkan.
- Jangan membuat predictor kartu, pembaca hole-card lawan, atau alat untuk memperoleh isi kartu tersembunyi.

## Batas teknis
- Capture hanya metadata endpoint, arah, waktu, dan jumlah byte.
- Jangan menambahkan dekripsi TLS/HTTPS, bypass certificate pinning, MITM tersembunyi, pembacaan credential, atau pembacaan payload terlindungi.
- Forwarding target harus tetap aktif; jangan memblokir koneksi target secara sengaja.
- Tombol DEAL/KARTU TERTUTUP dan REVEAL adalah ground-truth manual.
- PREFETCH_CANDIDATE hanya berarti pola timing konsisten dengan data yang mungkin sudah tersedia lebih awal. Status itu bukan bukti nilai kartu tersembunyi sudah diketahui client.
- PREFETCH_CROSS_SESSION hanya boleh muncul bila kandidat prefetch bertahan pada minimal 6 hand dan minimal 2 sesi capture.
- REVEAL_REQUIRES_NETWORK berarti ada trafik inbound signifikan yang konsisten di sekitar reveal.
- INCONCLUSIVE berarti metadata tidak cukup membedakan kedua kemungkinan.
- Dataset trial harus dipertahankan lintas restart aplikasi sampai pengguna memilih RESET DATASET.
- Pengguna dapat memulai SESI BARU tanpa menghapus dataset lama untuk menguji konsistensi lintas sesi.
- Dataset harus dapat diekspor ke Download/CardPresenceProbe.

## Build
- Gunakan GitHub Actions untuk menghasilkan APK uji.
- Jangan memasukkan signing key privat ke repository.

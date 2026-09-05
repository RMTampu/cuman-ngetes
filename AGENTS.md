# AGENTS.md

Sebelum mengubah repository ini, baca file ini penuh.

## Tujuan
- Aplikasi aktif adalah Poker Edge Companion.
- Target utama Android 11 (API 30), arm64-compatible.
- Aplikasi hanya memakai informasi permainan yang terlihat atau diinput pengguna.
- Fokus: Texas Hold'em demo/free-play, equity, pot odds, hand strength, outs peningkatan, dan saran matematis.

## Batas teknis
- Jangan membaca atau mengungkap hole-card tersembunyi milik lawan.
- Jangan menambahkan dekripsi TLS/HTTPS, bypass certificate pinning, MITM, pembacaan memory tersembunyi, credential interception, atau eksploitasi aplikasi target.
- Jangan mengotomatisasi sentuhan atau taruhan pada aplikasi target.
- Equity default dihitung terhadap kartu lawan acak bila range spesifik tidak tersedia.
- Input kartu harus menolak kartu duplikat.
- Overlay harus dapat dipindahkan dan tetap dapat dipakai pada landscape.
- Reset hand harus berasal dari tindakan pengguna.
- State hand dipertahankan saat aplikasi ditutup agar tidak hilang saat kembali dari target.

## Build
- Gunakan GitHub Actions untuk menghasilkan APK uji.
- Jangan memasukkan signing key privat ke repository.

# Salin Jaringan — External-First

Dokumen ini menetapkan aturan bahwa fungsi utama Salin Jaringan tidak boleh mengharuskan aplikasi target dibangun ulang.

## Aturan utama

1. Aplikasi target tetap apa adanya. Tidak ada kewajiban rebuild, repack, atau perubahan source hanya agar fungsi utama Salin Jaringan dapat digunakan.
2. Mode utama adalah pengamatan dari luar aplikasi target menggunakan mekanisme Android yang sah dan tersedia, terutama local VPN/network capture.
3. Jembatan Runtime tetap tersedia hanya sebagai fitur tambahan untuk aplikasi yang memang sengaja diberi integrasi. Fitur ini tidak boleh menjadi syarat.
4. Jika data sesudah parsing/decode tidak tersedia dari luar aplikasi target, Salin Jaringan harus menyatakan keterbatasannya dengan jelas dan tetap menyimpan data jaringan yang memang dapat diamati.
5. Jangan memakai hooking, pembacaan memori proses aplikasi lain, bypass TLS/pinning, Play Integrity/attestation, anti-cheat, atau sistem anti-abuse sebagai jalan pintas.
6. Jangan mengubah, menghapus, atau merusak data aplikasi target.

## Perilaku UI

- Pengguna dapat memakai Salin Jaringan tanpa memasang Jembatan Runtime pada aplikasi target.
- Menu Jembatan Runtime harus ditandai sebagai **opsional**.
- Jika tidak ada data runtime, UI harus menjelaskan bahwa kondisi tersebut normal dan pengguna tetap dapat memakai mode tangkapan jaringan.

## Tujuan

Menjaga aplikasi target yang sudah stabil tetap tidak tersentuh, sambil membuat Salin Jaringan berfungsi semaksimal mungkin dari luar aplikasi target dalam batas Android dan proteksi aplikasi yang berlaku.

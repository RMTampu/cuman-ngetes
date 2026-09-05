# AGENTS.md

Sebelum mengubah repository ini, baca file ini penuh.

## Batas dasar
- Target utama: Android 11 (API 30), arm64-compatible.
- Capture hanya metadata trafik; jangan menambahkan dekripsi TLS/HTTPS tersembunyi.
- Jangan memblokir koneksi target secara sengaja; forwarding harus tetap aktif.
- Marker utama adalah momen/window trafik dengan judul, bukan satu chunk TCP.
- Judul marker yang sama menambah sampel ke marker yang sama.
- Jangan memicu notifikasi deteksi otomatis per chunk.
- LOAD 20 hanya berjalan setelah tindakan manual pengguna dan hasilnya tampil di panel bubble.
- LOAD 20 harus diberi status ESTIMATED bila yang dihitung hanya burst TLS; jangan mengklaim urutan hasil server sebagai fakta.
- Save/Load marker harus mempertahankan judul dan seluruh sampel momen.
- Tindakan yang menghapus semua penanda harus berasal dari tindakan pengguna.

## Build
- Gunakan GitHub Actions untuk menghasilkan APK uji.
- Jangan memasukkan signing key privat ke repository.

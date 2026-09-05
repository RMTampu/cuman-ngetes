# Traffic Marker 0.3.0

Prototipe Android 11 untuk menangkap metadata trafik aplikasi target melalui VPN lokal, menyimpan momen penting sebagai marker berjudul, dan melakukan pemeriksaan manual 20 burst berikutnya dari panel bubble.

HTTPS tidak didekripsi. Semua pencocokan bekerja dari metadata TLS/TCP/UDP yang tersedia.

## Alur utama

1. Pilih aplikasi target.
2. Aktifkan bubble.
3. Mulai capture.
4. Buka aplikasi target.
5. Saat kejadian penting terlihat, buka bubble lalu pilih **TANDAI MOMEN + JUDUL**.
6. Tulis judul, misalnya `Scatter 5x`.
7. Traffic Marker menyimpan window sekitar momen: 8 detik sebelum sampai 1,5 detik sesudah.
8. Jika kejadian yang sama ditandai lagi dengan judul yang sama, sampel ditambahkan ke marker tersebut.
9. Tekan **LOAD 20 MANUAL** untuk mulai mengelompokkan maksimal 20 burst berikutnya.
10. Hasil pencocokan hanya tampil di panel bubble sebagai `+N • judul • confidence`.

## LOAD 20

`LOAD 20` bersifat manual dan tidak memicu notifikasi deteksi.

Contoh panel:

```
LOAD 20: 12/20 • MENGUMPULKAN
Mode: ESTIMATED (burst TLS, bukan isi HTTPS)

HASIL:
+4  Scatter 5x  91%
+11 Bonus Merah  84%
```

Posisi `+N` adalah urutan burst metadata setelah tombol LOAD ditekan. Karena isi HTTPS tidak dibuka, statusnya `ESTIMATED`, bukan klaim urutan hasil server yang pasti.

## Save / Load marker

**Simpan Penanda** mengekspor marker ke:

`Download/TrafficMarkerSave/`

Format v2 menyimpan:
- judul marker
- marker ID
- endpoint metadata
- seluruh sampel moment window
- seluruh burst signature tiap sampel

**Load Penanda** dapat membaca format v1 lama dan format v2 baru.

## Tidak ada alarm per chunk

Versi ini tidak lagi menyalakan alarm/notifikasi untuk setiap chunk TCP kecil. Channel deteksi lama juga dihapus saat aplikasi dibuka. Satu-satunya notifikasi yang tetap ada adalah foreground-service notification yang diperlukan Android agar capture tetap berjalan.

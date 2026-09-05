# Traffic Marker — Concept

Dokumen ini menyimpan arah konsep yang belum harus langsung diimplementasikan.

## 1. Live Bubble / Session Recording

### Masalah
Sebagian aplikasi target tidak boleh diminimalkan. Jika masuk background lalu dibuka lagi, aplikasi dapat memulai ulang sesi atau boot ulang.

### Konsep Live Bubble
Traffic Marker tetap menjalankan capture sebagai service, sedangkan aplikasi target tetap foreground.

Alur:
1. Pilih aplikasi target.
2. Mulai capture.
3. Buka aplikasi target.
4. Bubble/overlay Traffic Marker tetap tersedia di atas aplikasi target.
5. Tombol "Tandai Sekarang" mengambil kandidat trafik di sekitar waktu penekanan tanpa perlu keluar dari aplikasi target.
6. Kandidat dapat dijadikan fingerprint/penanda dan dipakai untuk alarm berikutnya.

### Konsep Session Recording
Mode cadangan jika overlay tidak cocok:
1. Rekam seluruh sesi metadata.
2. Pengguna tetap berada di aplikasi target.
3. Setelah sesi selesai, buka Traffic Marker.
4. Pilih event berdasarkan timestamp dan jadikan penanda.

## 2. Lookahead / Prefetch Scan 100–300 Data

### Tujuan
Setelah satu data/pola ditandai, Traffic Marker dapat memeriksa window data di depan, misalnya 100–300 item yang belum dikonsumsi oleh aplikasi target.

Contoh:
- Marker = pola X.
- Lookahead = 300 item.
- Sistem memuat item 1..300 yang memang sudah tersedia dari sumber yang sah/diizinkan.
- Setiap item dibandingkan dengan marker.
- Jika ditemukan pada item ke-137, tampilkan notice:
  "Marker ditemukan pada langkah 137 dari window 300."

Jika ada beberapa kecocokan, tampilkan semua posisi:
- #37
- #137
- #284

### Sliding Window
Saat pengguna maju, window ikut bergeser.
Contoh:
- posisi sekarang = 50
- lookahead = 300
- sistem mempertahankan cakupan 51..350
- setelah pengguna maju 10 langkah, cakupan menjadi 61..360

### Informasi Alarm
Alarm lookahead idealnya berisi:
- nama marker
- posisi relatif: +137 langkah
- posisi absolut jika sumber memiliki indeks
- confidence score
- jumlah kecocokan di window
- waktu terakhir window diperbarui

### Batas Teknis
Lookahead hanya dapat memastikan data di depan jika data tersebut:
- memang sudah tersedia di server/sumber sebelum dikonsumsi;
- dapat diambil melalui API, pagination, queue, stream buffer, atau mekanisme lain yang sah/diizinkan;
- mempunyai urutan/indeks yang dapat dipetakan.

Traffic capture biasa tidak dapat mengetahui secara pasti data yang:
- belum dibuat oleh server;
- dibuat acak/dinamis setelah aksi berikutnya;
- hanya dilepas satu-per-satu berdasarkan state server;
- tidak tersedia melalui antarmuka yang dapat diakses secara sah.

Jika hanya metadata HTTPS yang terlihat, sistem tidak boleh mengklaim mengetahui isi 100–300 item berikutnya. Dalam kondisi itu Lookahead hanya dapat bekerja jika ada identifier/fingerprint yang tersedia dari sumber data tersebut tanpa membuka TLS secara tersembunyi.

### Mode Hasil
- EXACT: item benar-benar tersedia dan posisi diketahui.
- ESTIMATED: hanya pola/fingerprint statistik; tampilkan confidence.
- UNAVAILABLE: sumber tidak menyediakan data masa depan; jangan membuat prediksi palsu.

## Prinsip
- Tidak mendekripsi HTTPS secara tersembunyi.
- Tidak membypass proteksi server/aplikasi.
- Tidak mengklaim prediksi masa depan jika datanya belum tersedia.
- Posisi marker harus berasal dari urutan data nyata, bukan tebakan.

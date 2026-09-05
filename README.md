# Traffic Marker 0.1

Prototipe Android untuk memilih satu aplikasi, meneruskan trafiknya melalui VPN lokal + SOCKS5 lokal, mencatat metadata trafik, menyimpan satu event sebagai signature, dan memberi alarm jika pola yang sama muncul lagi.

## Yang dideteksi
- host atau IP tujuan
- port
- arah CONNECT / OUT / IN / UDP_OUT / UDP_IN
- ukuran chunk
- waktu

HTTPS tidak didekripsi dan isi request/response tidak dibaca.

## Cara uji
1. Build APK melalui workflow GitHub Actions `Build Android APK`.
2. Instal APK di Android 11.
3. Pilih aplikasi target.
4. Tekan `Mulai Tangkap` dan izinkan VPN lokal.
5. Gunakan aplikasi target seperti biasa.
6. Kembali ke Traffic Marker, pilih satu event lalu `Tandai Terpilih` (atau tekan lama event).
7. Ulangi aktivitas di aplikasi target. Jika host/port/arah/ukuran masuk toleransi, notifikasi alarm muncul.

## Catatan prototipe
- HTTP/3/QUIC dan pola UDP tertentu bergantung pada dukungan tun2socks/SOCKS library.
- Event `OUT/IN` adalah chunk stream, bukan batas request HTTPS semantik.
- Jika aplikasi target memakai satu koneksi TLS yang sangat panjang, fingerprint ukuran adalah pendekatan statistik, bukan identitas payload.

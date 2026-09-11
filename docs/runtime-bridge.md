# Jembatan Runtime — Salin Jaringan

Jembatan Runtime dipakai untuk menyalin **data yang sudah diproses aplikasi**. Fitur ini ditujukan untuk aplikasi yang Anda miliki, kembangkan, atau memang Anda beri integrasi pengujian.

## Cara kerjanya

1. Pilih aplikasi target di Salin Jaringan.
2. Aplikasi target menerima data dari internet dan memprosesnya seperti biasa.
3. Setelah data sudah menjadi JSON/teks yang dipahami aplikasi, aplikasi target mengirim salinannya ke Salin Jaringan melalui `ContentProvider`.
4. Salin Jaringan hanya menerima kiriman dari UID aplikasi yang memang sedang dipilih sebagai target.
5. Data tersimpan lokal, maksimum 100 catatan, dan dapat diekspor menjadi satu file JSON UTF-8.

Fitur ini **tidak membaca memori aplikasi lain, tidak melakukan hooking, dan tidak melewati enkripsi atau proteksi aplikasi**. Aplikasi target harus mengirim data secara sadar melalui integrasi di bawah.

## Contoh Java di aplikasi target

```java
Bundle data = new Bundle();
data.putString("label", "respons API setelah parsing");
data.putString("json", jsonStringYangSudahDiproses);

getContentResolver().call(
        Uri.parse("content://com.rmtampu.salinjaringan.runtime"),
        "capture",
        null,
        data
);
```

Untuk teks biasa, gunakan `text` sebagai pengganti `json`:

```java
Bundle data = new Bundle();
data.putString("label", "teks hasil proses");
data.putString("text", teksYangSudahDiproses);

getContentResolver().call(
        Uri.parse("content://com.rmtampu.salinjaringan.runtime"),
        "capture",
        null,
        data
);
```

## Membaca hasil

Di Salin Jaringan buka menu samping **Data Runtime**. Di sana Anda dapat:

- melihat data terbaru;
- menyegarkan tampilan;
- menyimpan semua catatan sebagai `.json`;
- menghapus semua data runtime.

Setiap kiriman dibatasi 2 MB. Salin Jaringan menyimpan maksimum 100 catatan terbaru agar penyimpanan perangkat tidak terus bertambah.

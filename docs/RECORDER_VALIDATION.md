# Recorder & Arrival Validation Design

## Tujuan

Menjawab dua pertanyaan yang berbeda:

1. Kapan data jaringan benar-benar masuk ke perangkat?
2. Apakah pola untuk hasil tertentu sudah muncul beberapa step sebelum hasil aktual?

Traffic Marker tidak boleh menyimpulkan isi HTTPS hanya karena ada byte masuk.

## Recorder

Recorder aktif bersama capture dan menyimpan metadata setiap event:

- timestamp milidetik
- step index jika ada
- host/IP
- port
- arah
- ukuran byte

Preview event terbaru tampil di bubble.

Ekspor manual:
`Download/TrafficMarkerRecorder/TrafficRecorder-*.jsonl`

File berisi:
- record metadata format
- seluruh event
- seluruh step ground-truth dan label aktual

## Step Recorder

Pengguna menentukan batas step secara manual:

1. Tekan `MULAI STEP` tepat sebelum satu aksi.
2. Jalankan satu aksi.
3. Saat hasil benar-benar tampil, tekan `HASIL + LABEL`.
4. Isi hasil aktual:
   - `Normal`
   - atau judul marker yang benar-benar terjadi seperti `Bigwin`.

Dengan demikian satu step adalah unit nyata yang ditentukan pengguna, bukan tebakan dari burst TCP.

## Arrival Proof

Status:

### NO_DATA
Belum ada dataset.

### ARRIVED
Ada trafik yang sampai ke perangkat, tetapi belum ada bukti bahwa trafik tersebut mengkodekan target tertentu.

### STEP_LINKED
Pola tertentu memiliki hubungan dengan satu step, tetapi belum terbukti sebagai sinyal masa depan.

### LEAD_CANDIDATE
Pola marker ditemukan berulang pada lead yang sama, misalnya 2 step sebelum hasil aktual, tetapi masih memiliki error atau jumlah sampel belum cukup.

### VALIDATED
Hanya untuk dataset uji yang memenuhi:
- minimal 5 kejadian target
- precision = 100%
- recall = 100%
- false positive = 0
- false negative = 0
- lead step yang sama pada dataset uji

Status ini berarti "terbukti pada dataset uji", bukan jaminan universal.

### EXACT
Tidak boleh diberikan dari metadata TLS saja.
EXACT membutuhkan identifier deterministik atau data plaintext/API yang sah sehingga hubungan data ↔ hasil dapat dibuktikan langsung.

## Lead Validator

Untuk setiap marker berjudul:

1. Ambil semua step dengan label aktual yang sama.
2. Uji kandidat lead 1 sampai 20.
3. Cari apakah fingerprint marker muncul pada step N sebelum hasil aktual.
4. Hitung:
   - True Positive
   - False Positive
   - False Negative
   - Precision
   - Recall
5. Pilih lead dengan hasil terbaik.
6. Jangan menyebut prediksi valid jika masih memiliki false positive atau false negative.

Contoh:

```
ARRIVAL PROOF: VALIDATED
Target: Bigwin
Ground-truth: 7
Lead terbaik: +2
TP/FP/FN: 7/0/0
Precision: 100% • Recall: 100%

Terbukti pada dataset uji: pola muncul 2 step lebih awal.
```

Jika hasil:

```
TP/FP/FN: 3/8/2
Precision: 27%
Recall: 60%
```

maka marker tidak boleh dipakai sebagai sinyal masa depan.

## Prinsip penting

- Satu burst jaringan bukan satu step.
- Data masuk sebelum animasi selesai tidak otomatis berarti server mengirim beberapa hasil ke depan.
- Lead harus dibuktikan terhadap ground-truth step nyata.
- Sampel positif harus diuji bersama sampel negatif.
- Tidak ada notifikasi prediksi otomatis.
- Hasil analisis hanya ditampilkan di bubble.

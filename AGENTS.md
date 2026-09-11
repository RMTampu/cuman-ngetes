# AGENTS.md

Sebelum mengubah repository ini, baca file ini penuh.

## Project
- Project aktif: Space Lab, aplikasi virtual-space/multi-instance untuk pengujian.
- Target perangkat utama: Android 11, arm64-v8a.
- Build APK hanya melalui GitHub Actions.
- Repo ini sengaja ringan: source engine upstream diambil pada commit yang dipin saat CI, lalu overlay lokal diterapkan.

## Kemampuan dasar
- Data/login antar virtual user terisolasi oleh engine.
- Aplikasi yang sama dapat dipasang pada beberapa virtual user.
- Virtual Android ID bersifat stabil per virtual user + package.
- Pengguna dapat mengganti Virtual Android ID secara manual tanpa uninstall APK dan tanpa menghapus data aplikasi.
- Pergantian ID harus menghentikan proses guest dahulu agar profil baru aktif saat launch berikutnya.

## Batas
- Jangan mengubah IMEI, serial, MEID, SIM identity, atau hardware identifier asli perangkat.
- Jangan mencoba melewati Play Integrity/attestation, server-side binding, anti-cheat, atau sistem anti-abuse.
- Tidak ada sistem backup/snapshot/recovery data aplikasi dalam project ini.
- Jangan menambahkan fitur backup diam-diam.
- Jangan memasukkan signing key privat atau secret ke repository.

## Salin Jaringan — aturan external-first
- Aplikasi target yang sudah jadi tidak boleh diwajibkan rebuild hanya untuk memakai fungsi utama Salin Jaringan.
- Mode utama harus bekerja dari luar aplikasi target sejauh kemampuan Android mengizinkan, terutama melalui local VPN/network capture dan mekanisme Android resmi yang tidak membutuhkan perubahan aplikasi target.
- Jembatan Runtime adalah fitur opsional/eksperimental, bukan syarat penggunaan dan bukan standar wajib untuk aplikasi target.
- Jangan menjadikan modifikasi source, repack, hooking, pembacaan memori proses aplikasi lain, atau bypass proteksi sebagai solusi default.
- Jika data runtime setelah parsing tidak dapat diperoleh dari luar aplikasi target, tampilkan keterbatasan tersebut dengan jelas; jangan diam-diam memaksa rebuild aplikasi target.
- Perubahan Salin Jaringan harus mempertahankan aplikasi target apa adanya kecuali pengguna secara eksplisit meminta integrasi pada aplikasi yang memang mereka miliki/kembangkan.

## Build
- Pinned upstream harus diverifikasi sebelum overlay diterapkan.
- APK uji dihasilkan sebagai artifact GitHub Actions.
- Build harus menghasilkan arm64-v8a.

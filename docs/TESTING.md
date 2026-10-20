# Pengujian Qwen Baritone

## Target

Minecraft **26.2**, Fabric Loader **0.19.3**, Java **25**. Source berasal dari paket **1.6.0-hotfix.2**; basis upstream Baritone 1.19.0, commit `9fadf7cf95293d7f6678981e77a939b4a7978373`.

## Build persiapan publikasi

Build bersih source persiapan berhasil dengan JDK 25 dan Gradle 8.14.4: **184 tes lulus**, tanpa failure/error/skip (45 core, 139 Fabric). Seluruh berkas Java identik dengan source hotfix.2 yang diberikan; metadata nama/tautan fork dan dokumentasi diperbarui. Build memakai cache dependensi yang ada, dengan akses online; ini belum merupakan verifikasi runner GitHub dari cache kosong.

Percobaan pertama gagal karena inode penyimpanan sandbox habis; build ulang di area sementara berhasil, tanpa perubahan Java untuk memperbaiki build.

## Bukti rilis sebelumnya

Paket pengujian lokal rilis mencatat **184 unit test lulus** (45 core, 139 Fabric). Pengujian inventory hotfix.1 mencakup Shift+klik, konservasi item, cursor kosong, penjagaan saat GUI terbuka, pembatalan slot berubah, dan transaksi swap Elytra. Hotfix.2 mempertahankan perbaikan ini dan menambahkan resource loader untuk logo.

Ringkasan runtime rilis mencatat pemeriksaan menu/pengaturan/perintah serta gameplay singleplayer; screenshot README berasal dari client Minecraft lokal rilis tersebut, bukan mockup. Log mentah, world, dan konfigurasi pengujian tidak dimasukkan ke repositori untuk menghindari data lokal yang tidak perlu.

## Memverifikasi sendiri

```sh
./gradlew clean :fabric:clean :fabric:build test --no-daemon --max-workers=2
```

Laporan: `build/reports/tests/test/index.html` dan `fabric/build/reports/tests/test/index.html`. GitHub Actions menyimpan laporan tes dan JAR distribusi sebagai artifact setiap build. Status Actions yang sebenarnya baru tersedia setelah workflow berjalan di GitHub; keberadaan file workflow saja bukan bukti build CI sukses.

## Belum dijamin

Multiplayer berlatensi tinggi, semua anticheat/mod inventory, perjalanan Elytra penuh, seluruh skenario builder/mining, dan Gemini dengan kredensial nyata belum tervalidasi menyeluruh. Tes lokal tidak menjamin keamanan absolut, kompatibilitas semua modpack, atau bebas kehilangan world. Cadangkan world dan patuhi aturan server.

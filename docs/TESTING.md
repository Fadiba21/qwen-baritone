# Pengujian Qwen Baritone

## Hotfix.3: hasil paket teruji

Target: **Minecraft 26.2, Fabric Loader 0.19.3, Java 25**. Rilis fork **1.6.0-hotfix.3**, basis Baritone **1.19.0**. Publikasi ini memakai Java source dari source ZIP teruji dan JAR distribusi yang diberikan, bukan mengganti JAR dengan build baru.

Laporan lengkap tersedia di [HOTFIX-1.6.0-hotfix.3.md](../HOTFIX-1.6.0-hotfix.3.md). Hasil akhir: **72/72 pemeriksaan runtime lulus**, **184 eksekusi unit test, 0 gagal**, clean build Java 25 berhasil. Suite utama mencatat 2693 operasi, termasuk 1458 SWAP pada 729 pasangan bolak-balik, 486 callback tombol angka, dan 150 shift-click. Input X11 asli, drag 63 menjadi 21+21+21, menu server, builder dan mining nyata diuji terpisah. Uji diulang dengan Litematica 0.28.8 dan MaLiLib 0.29.6.

Percobaan awal yang gagal karena adaptor input dan urutan gerakan tetap disimpan dalam bukti; bukan dihitung lulus. Build awal kehabisan inode, lalu clean build penuh di memori berhasil. Publikasi GitHub memverifikasi seluruh checksum artefak dan kesamaan Java source; tidak mengklaim menjalankan ulang seluruh suite runtime.

## Bukti dan checksum

[Rilis v1.6.0-hotfix.3](https://github.com/Fadiba21/qwen-baritone/releases/tag/v1.6.0-hotfix.3) memuat JAR teruji, source ZIP asli, laporan, `bukti-pengujian-inventory-hotbar.zip`, screenshot asli dan `SHA256SUMS.txt`. Seluruh 10 berkas yang dirujuk checksum dipublikasikan sebagai aset rilis. Log/harness pengujian tetap dalam arsip bukti, tidak memenuhi pohon source dengan output runtime. Agent test-only tidak dimasukkan ke JAR distribusi.

Java source di branch ini identik dengan source ZIP teruji. CI Fabric, checksum Gradle wrapper, identitas/tautan fork, panduan kontribusi, dan perlindungan `.gitignore` yang sudah ada tetap dipertahankan; karena itu ZIP sumber asli bukan salinan byte-identik dari seluruh pohon dokumentasi/metadata GitHub.

## Memverifikasi sendiri

```sh
./gradlew clean :fabric:clean :fabric:build test --no-daemon --max-workers=2
```

Laporan: `build/reports/tests/test/index.html` dan `fabric/build/reports/tests/test/index.html`. GitHub Actions menyimpan laporan tes dan JAR distribusi sebagai artifact setiap build. Status CI harus dibaca dari run GitHub yang sebenarnya; hasil lokal bukan bukti CI sukses.

## Batas pengujian

Pengujian memakai client Linux/render software dan server singleplayer terintegrasi lokal. Belum mencakup server publik berlatensi/packet loss, plugin inventory pihak ketiga, disconnect/reconnect di tengah transaksi, semua mode creative/recipe, atau seluruh alur farm/Elytra panjang. Menu peti memakai ChestMenu nyata dengan SimpleContainer. Sejumlah aksi memakai callback GUI atau metode inventory melalui agent test-only; input X11 dan tugas aktif diuji terpisah. Tidak ada jaminan bebas bug untuk seluruh modpack/server; cadangkan world dan patuhi aturan server.

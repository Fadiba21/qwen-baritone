# Baritone QwenUI 1.6.0 — hotfix.1

Hotfix ini dibuat dari arsip `baritone-qwenui-1.6.0-source.zip` yang diberikan pengguna. Target source adalah **Minecraft 26.2, Fabric Loader 0.19.3 atau lebih baru, dan Java 25**. Angka `1.6.0` adalah versi QwenUI, bukan versi Minecraft. Metadata versi mod dipertahankan dalam bentuk `1.19.0+qwenui.1.6.0-hotfix.1`.

## Perbaikan utama

Saat `allowInventory` aktif, kode lama dapat menarik kembali blok dan pickaxe ke hotbar ketika pemain baru saja memindahkannya dengan Shift+klik di inventory. Perilaku ini berhasil direproduksi pada client Minecraft lokal sebelum patch inventory diterapkan.

`InventoryBehavior` kini menolak auto-swap saat layar GUI terbuka, menu aktif bukan inventory pemain, atau cursor membawa item. Penjagaan dilakukan pada maintenance tick dan pada fungsi swap itu sendiri, sehingga panggilan dari builder/throwaway tidak dapat melewatinya. Perpindahan manual tetap memakai implementasi vanilla; hotfix tidak mengganti aturan quick-move Minecraft.

Permintaan tertunda menyimpan salinan stack sumber dan tujuan, termasuk jumlah dan komponen. Permintaan dibatalkan bila isi slot berubah atau konteks tidak aman. Permintaan lain tidak lagi menimpa pekerjaan tertunda yang masih valid. Jika semua slot tujuan dilarang, fungsi mengembalikan `false`. Pemilihan throwaway dari main inventory juga tidak lagi mengaku berhasil atau memilih slot tujuan sebelum swap benar-benar dilakukan pada client.

Auto-swap Elytra tidak lagi menyimpan rangkaian klik PICKUP lintas tick. Transaksi dijalankan sinkron pada satu tick client, hanya dengan menu yang sesuai, GUI tertutup, dan cursor kosong; slot serta hasil akhir diperiksa. Ini bukan jaminan transaksi atomik pada server atau kompatibilitas semua anticheat.

Empat error kompilasi pada integrasi Litematica diperbaiki dengan mengimpor `LitematicaPlacements`, sehingga nama paket tidak tertutup field `baritone` di `BuilderProcess`.

## Build dan tes otomatis

Gunakan JDK 25 dan Gradle wrapper yang disertakan:

```sh
./gradlew clean :fabric:clean :fabric:build test --no-daemon --max-workers=2
```

Build yang diverifikasi memakai cache dependensi dengan flag `--offline`. JAR distribusi berada di `fabric/build/libs/baritone-qwenui-fabric-1.19.0+qwenui.1.6.0-hotfix.1.jar`; jangan memasang varian `-dev.jar` atau `-dev-shadow.jar`.

Clean build berhasil. Sebanyak **184 unit test** lulus: 45 core dan 139 Fabric. Empat tes baru memeriksa seluruh 32 kombinasi konteks izin otomatisasi, pemetaan 36 slot inventory/hotbar, kombinasi sumber-tujuan valid, dan penolakan indeks equipment/offhand/di luar rentang.

## Tes dalam game dan batasnya

Tes inventory dijalankan otomatis pada client Minecraft 26.2 yang sebenarnya, Fabric 0.19.3, world singleplayer lokal, identitas offline, dan renderer software. Ini dibedakan dari unit test; bukan klaim sesi bermain manual atau validasi semua kombinasi gameplay.

Regresi utama memverifikasi Shift+klik blok dan pickaxe, 50 perpindahan berulang, konservasi item dan cursor kosong, mode `allowInventory=false`, penjagaan direct-call, pembatalan sumber/tujuan yang berubah, pembatalan saat GUI dibuka, status kegagalan yang benar, serta auto-restock yang tetap bekerja saat konteks aman. Total **39 pemeriksaan runtime** lulus: 13 regresi utama, 23 edge case/integrasi, dan 3 smoke test UI/perintah versi. Tes tambahan mencakup input native GLFW/X11 untuk Shift+klik, split stack, tombol angka dan drag dua slot, inventory penuh, cursor non-kosong, urutan permintaan tertunda, kesesuaian inventory client/server, transaksi Elytra, F11/Escape, serta `#version`. Bukti rinci dan screenshot disertakan pada paket evidence terpisah. Dua percobaan tes native awal perlu koreksi koordinat/timing harness; log percobaannya tetap disimpan dan tidak dihitung sebagai hasil akhir mod.

Belum divalidasi: multiplayer berlatensi tinggi, anticheat server tertentu, seluruh kombinasi container/mod inventory pihak ketiga, perjalanan Elytra penuh, seluruh skenario builder/mining/pathfinding, integrasi placement Litematica yang terpasang, atau layanan AI dengan kredensial nyata. Tes Elytra menggunakan metode transaksi inventory aktual dalam client, tetapi tidak memulai penerbangan/pathfinding.

Log offline dapat memuat 401/profil/Realms karena token uji memang bukan akun Microsoft. Itu terpisah dari crash mod. Peringatan upstream tentang `sun.misc.Unsafe`, API deprecated/unchecked, dan kompatibilitas Gradle 9 masih ada; build memakai wrapper Gradle 8.14.4 dan tetap berhasil.

## Memasang

Tutup Minecraft, cadangkan world/config, pindahkan JAR Baritone/QwenUI lama keluar dari folder `mods`, lalu pasang hanya JAR hotfix ini. Jangan memasang dua salinan Baritone sekaligus. Hotfix tidak mengharuskan penghapusan konfigurasi pengguna.

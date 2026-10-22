# Qwen Baritone 1.6.0-hotfix.3

- Hapus autosort/restock saat idle dan eksekusi otomatis antrean swap lama yang menarik kembali item manual ke hotbar.
- Builder, placement, dan mining meminta item hanya saat dibutuhkan; pemanggil menunggu jika swap belum siap.
- Prioritaskan slot kosong secara deterministik. Batalkan permintaan kedaluwarsa atau berubah konteks, dan validasi ulang sebelum satu paket SWAP dikirim.
- Guard GUI/menu/cursor juga berlaku untuk pemilihan tool, builder, placement, dan bucket. Pauser menghitung tick diam berturut-turut.
- Paket pengujian melaporkan 72/72 pemeriksaan runtime akhir lulus, 184 eksekusi unit test tanpa kegagalan, dan clean build Java 25 berhasil. Lihat [laporan hotfix.3](HOTFIX-1.6.0-hotfix.3.md) untuk bukti, percobaan awal yang gagal, dan batas pengujian.

Catatan di bawah adalah riwayat rilis lama; pernyataan belum diuji/belum terselesaikan di sana bukan status hotfix.3.

# Perubahan Baritone QwenUI 1.6.0

Revisi dari 1.5.0. **Catatan jujur:** ditulis tanpa akses Gradle/Mojang di sandbox; kode Minecraft belum dikompilasi atau dijalankan di sini. Hanya logika murni (SettingGuide, UiPreferences, ResponsiveLayout) yang diuji, plus pengecekan sintaks.

## UI
- Navigasi baru: Beranda · Pengaturan · Perintah · Bangun · Makro · Keybind · Asisten AI · Preferensi, dengan judul dan subjudul bahasa biasa.
- Beranda: status tugas, 6 pintasan (Go to, Mine, Follow, Explore, Bangun, Pengaturan) dan Jeda/Lanjut/Hentikan.
- Pengaturan: nama setting yang mudah dibaca, filter "Penting", tombol kategori ‹ ›.
- Font Google Sans dihapus; hanya font Minecraft.
- Set Scale diganti stepper bilangan bulat −/+ (Otomatis, 1×, 2×…) yang langsung berlaku. Slider lama tidak berfungsi dan dihapus.

## Litematica
- Menu/tab Litematica dihapus; diganti tab **Bangun** (Dari Litematica / Dari berkas).
- "Bangun placement terpilih" membaca berkas placement yang sedang dibuka (posisi, rotasi, mirror, subregion) dan membangunnya di posisi aslinya, tidak bergantung chunk yang ter-render. `#litematica` tanpa argumen memakai placement terpilih.

## Belum terselesaikan
- Item hotbar "nyangkut": penyebab belum ditemukan; tidak ada kode QwenUI yang menyentuh inventori. Butuh `latest.log` dan uji dengan Baritone upstream saja.

# (Riwayat 1.5.0)

Revisi dari 1.4.0. Metadata fork: `1.19.0+qwenui.1.5.0`. **Catatan jujur:** perubahan ini ditulis tanpa akses Gradle/Mojang di lingkungan pembuat, jadi kode Minecraft belum dikompilasi di sana. Logika murni (panduan setting, skala) lolos 22 uji JUnit; rendering sudut/ikon diverifikasi lewat render PNG. Build dengan GitHub Actions atau `./gradlew :fabric:build` dan laporkan bila ada error kompilasi.

## Litematica dan `#build`
- Penyebab error "file corrupt atau bug": parser hanya menerima litematic **Versi 7**; Versi 5/6 ditolak dan setiap error sebenarnya ditelan. Sekarang Versi 5, 6, 7 dibaca (versi lebih baru dicoba dengan peringatan, Versi 4 diberi petunjuk simpan ulang di Litematica).
- `getBitsPerBlock` memakai `ceil(log)` yang bisa meleset 1 bit pada palet berukuran pangkat dua (membuat seluruh blok salah/rusak); diganti hitungan bit eksak. Volume region memakai `long` (tidak overflow).
- Properti blok yang tidak valid di versi ini dilewati (sebelumnya melempar exception dan membatalkan seluruh skematik); blok tak dikenal menjadi udara **dengan peringatan** berisi nama blok; nama blok lama dipetakan (`grass`→`short_grass`, dst.).
- Penjagaan: region kosong, palet kosong, data `BlockStates` terpotong, ukuran tak wajar, berkas bukan gzip.
- `#build` dan tombol GUI kini menampilkan **penyebab asli** (`getLastLoadError`) dan peringatan (`getLastLoadWarnings`); file stream ditutup (sebelumnya bocor).
- Tab Litematica punya mode **Berkas**: daftar `.minecraft/schematics` (.litematic/.schem/.schematic), pilih lalu **Bangun di sini** / **Hentikan build**. Tab ini bekerja tanpa Litematica terpasang.

## Tampilan tajam (tidak burik)
- Skala efektif kini selalu **bilangan bulat** piksel per unit UI; skala pecahan lama adalah penyebab teks buram.
- Setiap skala punya font sendiri (`google_sans_x1…x8`) dengan `oversample` = skala, sehingga tekstur huruf 1:1 dengan piksel layar. Bayangan teks dimatikan, posisi vertikal label dikoreksi.
- Sudut rounded dan ikon digambar per piksel layar dengan anti-aliasing (supersampling 4×4, di-cache). Ikon lama (kotak kasar) diganti ikon vektor baru.

## Panduan setting
Editor setting baru: area Baritone + tingkat (Dasar/Lanjutan/Berisiko), penjelasan, **cara mengisi + contoh perintah chat**, tombol nilai cepat (ON/OFF, min/bawaan/maks, nama blok umum, warna, enum Mirror/Rotation), tombol **÷2 / − / + / ×2**, slider untuk setting dengan rentang, dan **input angka manual tetap ada** dengan validasi. Daftar blok/item punya tombol `+ nama` dan `clear`. Kategori setting kini mengikuti area Baritone (Pergerakan, Hancurkan blok, Pasang blok, …). Pesan error nilai tidak valid menyebut tipe dan nilai yang ditolak.

## Lain-lain
- Keybind buka GUI bawaan **F11** (Controls → Baritone QwenUI); tombol Fullscreen vanilla dipindah sekali ke F10 agar tidak bentrok.
- Ringkasan: tombol Jeda, Lanjut, Bangun berkas, Setting yang diubah.
- Mengosongkan setting bertipe list/map kini bekerja (nilai kosong).

# (Riwayat 1.4.0)

Revisi dari paket 1.3.0 untuk Minecraft 26.2 / Fabric / Java 25. Metadata fork: `1.19.0+qwenui.1.4.0`.

## Font dan identitas Qwen

Preferensi kini menyediakan pilihan **Google Sans / Minecraft** yang tersimpan dalam konfigurasi. Pilihan berlaku pada label, input, tombol dan chat; konfigurasi lama memakai Sans tanpa kehilangan makro, keybind dan pengaturan lainnya.

Logo baru tanpa tulisan memakai bentuk voxel monokrom pada tile rounded. Logo dipasang di kiri tulisan header Baritone/Qwen, avatar AI, dan metadata ikon mod.

## Chat yang rapi

Latar pesan bukan lagi strip per baris. Satu balon rounded membungkus satu pesan, lebarnya mengikuti baris terpanjang dan tingginya mengikuti seluruh baris. Padding, jarak antarpesan, posisi avatar dan label dibuat terpisah. Pesan panjang memiliki wrapping dan scroll piksel; viewport chat mencegah isi menimpa tombol serta input.

## Chat umum dan konteks

Asisten dapat menjawab pertanyaan umum di luar Minecraft tanpa dipaksa menawarkan aksi mod. Riwayat sesi tetap menjadi konteks pertanyaan lanjutan; katalog mod tetap disertakan untuk operasi yang relevan. Pesan berurutan dengan peran yang sama digabung menjadi satu giliran Gemini tanpa membuang teks, termasuk status eksekusi dari client. Jawaban biasa dapat berisi teks tanpa aksi maupun tombol.

## AI menjalankan langsung

Respons aksi yang valid langsung melewati executor di thread client, **tanpa halaman tinjau atau klik persetujuan**. Tombol Litematica dari chat juga memakai jalur langsung. Status dispatch, kegagalan dan selesainya antrean ditambahkan ke chat; selesai antrean tidak diklaim sebagai selesai pekerjaan dunia.

Model diarahkan tidak mengklaim operasi telah selesai sebelum hasil client tersedia. Parser, validasi registry aktif, batas payload, pemeriksaan dunia, larangan command gabungan/shell, penolakan fitur tidak tersedia, pengaman callback lama, pembatalan request serta rollback konfigurasi best-effort tetap dipertahankan.

Tombol fitur tetap didukung sesuai katalog, bukan fitur/kode arbitrer yang dibuat model. Command membuka form, setting membuka editor, halaman menavigasi, dan Litematica memanggil adapter yang tersedia.

## Fitur lama tidak dihapus

Menu command, pencarian pengaturan, form parameter, makro, keybind, Litematica native, daftar model, tes koneksi, hapus chat, blur, animasi, zoom dan resize tetap ada. Alur review manual pada tab Litematica tetap ada; jalur aksi AI tidak memerlukannya. Litematica dan MaLiLib tetap mod terpisah.

## Pengujian dan batas

Unit test baru mencakup migrasi/persistensi font, normalisasi nilai tidak valid, ukuran balon dan scroll, prompt chat umum, histori percakapan serta penggabungan status berurutan. Pengujian build dan runtime aktual tercatat di `HASIL-UJI.md` pada paket rilis. Tidak ada API key Gemini pengguna atau key proyek lain yang dipakai.

Eksekusi langsung dapat mengubah dunia akibat interpretasi AI yang keliru. Cadangkan dunia dan gunakan STOP/End bila diperlukan. Tidak ada jaminan rollback dunia penuh ataupun bebas bug pada semua server/modpack.

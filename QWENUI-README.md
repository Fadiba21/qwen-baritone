> Catatan historis 1.6.0. Untuk instalasi/build rilis hotfix.3 gunakan [README](README.md); menu awal sekarang Beranda.

# Baritone QwenUI 1.6.0

Pembaruan dari source **1.5.0** (navigasi baru, tab Bangun, skala UI bulat, font Minecraft saja). Sebelumnya dari **1.3.0** untuk **Minecraft 26.2 · Fabric Loader 0.19.3+ · Java 25+**. Inti Baritone tetap 1.19.0; identitas fork adalah `1.19.0+qwenui.1.6.0`.

## Pasang

Tutup Minecraft, cadangkan dunia dan konfigurasi, lalu ganti JAR Baritone QwenUI lama dengan `baritone-qwenui-1.6.0-mc26.2-fabric.jar` di folder `mods`. Jangan memasang dua JAR Baritone sekaligus. Gunakan profil Fabric untuk Minecraft 26.2 dengan Java 25.

**Litematica dan MaLiLib tetap mod terpisah** dan harus cocok dengan Minecraft 26.2. Keduanya tidak dibundel dalam JAR ini. Mod Menu opsional. Modul kecil Fabric yang diperlukan QwenUI tetap disertakan; mod lain mungkin memerlukan Fabric API lengkap.

Buka panel dengan **F11** (bawaan; ubah di *Controls → Baritone QwenUI*). Karena F11 bawaan Minecraft adalah Fullscreen, saat pertama dijalankan mod memindahkan tombol Fullscreen ke **F10** agar F11 bisa membuka panel (hanya sekali; bisa diubah di Controls). **End** adalah stop darurat.

## Font, logo, dan balon chat

UI hanya memakai font **Minecraft** bawaan (font Google Sans sudah dihapus). Config lama yang berisi `fontFamily: sans` otomatis diabaikan.

Logo Qwen tanpa tulisan bertema voxel Minecraft dan monokrom dipasang di kiri tulisan header, avatar AI, dan ikon mod. Satu balon rounded membungkus satu pesan; lebar mengikuti baris terpanjang setelah wrapping dan tinggi mengikuti jumlah baris. Pesan panjang dapat digulir; viewport chat membatasi gambar agar tidak menimpa tombol/input.

## Latar, blur dan skala

Perpindahan tab tidak lagi memulai ulang penggelapan latar. Satu backdrop dengan opacity konstan dipakai di seluruh halaman, termasuk saat form dibangun ulang. Hover tombol dan indikator AI tetap dapat bergerak tanpa mengubah terang/gelap latar dunia.

Di **Preferensi**, tombol **Blur background: ON/OFF** hanya memengaruhi panel QwenUI. Intensitas ON mengikuti pengaturan blur menu Minecraft. Jika pengaturan global tersebut nol, ON tidak memaksakan perubahan pengaturan Minecraft.

**Skala UI** di Preferensi memakai tombol **−/+** dengan bilangan bulat (1×, 2×, 3×…) agar teks tetap tajam; **Otomatis** mengikuti GUI scale Minecraft. Skala langsung diterapkan dan dibatasi ukuran jendela (batas maksimum ditampilkan). Config lama `contentScalePercent` diabaikan.

**Ukuran jendela** mengatur panel, bukan zoom isi. Tarik tepi/sudut untuk mengubah ukuran dan bagian dalam untuk memindahkannya. Enter menyimpan, Esc membatalkan. **Reset tampilan** mengembalikan ukuran otomatis dan skala isi 100%. Bahasa, animasi, blur dan skala tersimpan di `config/qwenui.json`; konfigurasi 1.2.0 tetap dimuat tanpa menghapus makro/keybind. Jendela di bawah 640×360 tidak dijamin nyaman untuk seluruh form.

## Asisten AI interaktif

Masukkan **key Gemini sendiri di dalam mod**, muat daftar model, pilih model yang mendukung generateContent, lalu tekan **Test** jika ingin memeriksa koneksi. Test/chat nyata dapat memakai kuota Google. Tidak ada key bawaan atau key proyek lain yang dipakai.

Key berada di memori kecuali **Ingat key** diaktifkan. Opsi itu menyimpan key sebagai teks biasa di `config/qwenui.json`; jangan bagikan file tersebut. Key tidak dimasukkan ke katalog AI atau diagnostik.

Contoh permintaan: “Tampilkan tombol menambang dan pengaturan allowBreak,” atau “Buka fitur Litematica dan siapkan rencana membangun placement.” Respons dapat menyertakan tombol rounded berikon untuk membuka form command, detail pengaturan, halaman fitur, atau kontrol Litematica yang dapat dijalankan langsung. Tombol muncul di area percakapan dengan pagination jika banyak. Target yang tidak sesuai registry/API aktif tidak bisa dijalankan. Sebelum ada respons, tersedia pintasan ke perintah dan Litematica.

**Ikon tempat sampah** menghapus percakapan, draf pesan, tombol respons dan rencana setelah konfirmasi. Permintaan tertunda dibatalkan; balasan lama tidak boleh menghidupkan kembali chat/rencana. Key, model, makro tersimpan dan pekerjaan dunia yang sudah berjalan tidak ikut dihapus. Gunakan STOP/End untuk menghentikan pekerjaan.

## Chat umum berkonteks dan aksi langsung

Asisten bisa menjawab pertanyaan umum seperti AI biasa, termasuk topik di luar Minecraft. Riwayat sesi menjadi konteks untuk pertanyaan lanjutan; katalog mod dipakai ketika relevan. Pertanyaan biasa mengembalikan jawaban tanpa aksi. Riwayat bukan ingatan permanen: jumlah pesannya dibatasi, dan percakapan hilang setelah dihapus atau layar ditutup lalu dibuat ulang.

Sesuai permintaan pengguna, **aksi AI yang valid langsung dijalankan setelah respons diterima, tanpa tahap tinjau/persetujuan**. Setting diterapkan, command masuk antrean berurutan, makro dapat disimpan, dan fitur Litematica yang diekspos adapter dapat dipanggil. Status/kegagalan client ditambahkan ke chat agar konteks berikutnya tidak menganggap aksi yang gagal telah berhasil. Selesainya antrean tidak menjamin tugas Baritone terakhir sudah selesai.

Tombol command membuka form parameter, tombol setting membuka editor, dan tab **Bangun** membangun placement Litematica atau berkas skematik. Menu Litematica lama sudah dihapus.

Walaupun tanpa review, validasi registry, batas payload, stop darurat, pembatalan callback lama, serta penanganan kegagalan tetap ada. **Cadangkan dunia**, karena AI dapat salah memahami permintaan dan perubahan yang sudah terjadi tidak otomatis dipulihkan.

AI mendapat katalog seluruh command/alias dan setting Baritone yang terdaftar saat itu, termasuk addon yang memakai registry yang sama. Setting internal/read-only tetap tidak bisa diubah. Integrasi Litematica memakai operasi yang diizinkan, bukan kode atau refleksi arbitrer yang diminta model.

**Akses mod bukan akses tanpa batas:** semua command/setting Baritone yang diekspos tersedia untuk penjelasan/form/rencana; Litematica hanya melalui API native dan kontrol yang didukung pada versi terpasang. Pengaturan non-boolean dan editor lain tetap dapat dibuka melalui layar native, bukan diklaim sebagai aksi AI langsung. Tidak tersedia aksi shell, filesystem atau jaringan umum.

Prompt, riwayat chat dan katalog aktif dikirim ke Google saat chat nyata. Layanan eksternal tidak diperlukan untuk GUI Baritone biasa. Ketersediaan model mengikuti akun, region dan kemampuan model yang dipilih; mod tidak mengganti model diam-diam.

## Litematica

Tab **Alat / fitur** memberi pencarian/pagination untuk konfigurasi native, pemuatan schematic, schematic manager, placement, area selection, material list, verifier dan pengaturan placement terpilih. Ketersediaan mengikuti API dan keadaan aktif; beberapa layar membutuhkan placement terpilih/material list. Layar yang tidak didukung tetap nonaktif.

Opsi boolean yang benar-benar ditemukan di kelompok konfigurasi Litematica juga masuk katalog; perubahan toggle melalui tab alat ditinjau dahulu; permintaan aksi melalui AI dijalankan langsung setelah validasi. Tab **Placement** menampilkan indeks asli mulai dari 1, nama, lokasi dan orientasi yang tersedia. Memilih placement menyiapkan rencana `litematica <nomor>`, bukan langsung membangun. Material dan perilaku builder tetap mengikuti Baritone.

## Fitur lama tetap tersedia

**# Perintah** tetap halaman awal: pencarian, kategori, form parameter, saran native, dan input sintaks lanjutan opsional. **Pengaturan** berisi nilai aktif, deskripsi, editor, toggle boolean langsung yang dipilih sendiri, serta pemulihan default. **Makro** menyimpan urutan command dengan jeda/pengulangan terbatas dan hanya berjalan setelah dimulai. **Keybind** mendukung Ctrl/Shift/Alt + tombol dan tidak aktif saat mengetik di layar.

STOP/End menghentikan tugas dan antrean berikutnya, bukan mengembalikan blok yang sudah berubah. Cadangkan dunia sebelum otomatisasi. Rencana multioperasi tidak menjanjikan rollback perubahan dunia jika langkah berikutnya gagal. Kegagalan parsial harus diperiksa; rencana yang sudah dicoba dibuang agar tidak diterapkan ulang tanpa sengaja.

## Build dan pengujian

```sh
JAVA_HOME=/path/to/jdk-25 ./gradlew :fabric:remapJar :fabric:test test --rerun-tasks
```

JAR hasil build: `fabric/build/libs/baritone-qwenui-fabric-1.19.0+qwenui.1.6.0.jar`. Di Windows gunakan `gradlew.bat`. Source platform upstream lain tetap disertakan sebagai atribusi; konfigurasi fork ini membangun Fabric.

Lihat `HASIL-UJI.md` dalam paket rilis untuk hasil aktual, batas pengujian, log, XML JUnit dan screenshot. Screenshot berlabel **UJI UI OFFLINE** menggunakan respons tiruan melalui harness terpisah, bukan Gemini nyata. Harness tidak dibundel dalam mod. Tidak ada key pengguna atau proyek lain yang dipakai untuk pengujian.

## Lisensi

Basis upstream: Baritone v1.19.0, commit `9fadf7cf95293d7f6678981e77a939b4a7978373`. Fork mempertahankan LGPL-3.0-or-later dan atribusi upstream; lihat `LICENSE`. Font Google Sans sudah dihapus; UI menggunakan font Minecraft bawaan. Litematica/MaLiLib tetap proyek terpisah dan tidak didistribusikan di dalam JAR ini. Tidak ada klaim afiliasi dengan pengelola Baritone atau Google, dan tidak ada jaminan bebas bug pada semua modpack/server.

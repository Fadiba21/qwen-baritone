# Hasil uji Baritone QwenUI 1.4.0

## Yang berhasil diuji

Rilis untuk **Minecraft 26.2 / Fabric / Java 25** dibangun dari revisi 1.3.0. **173 unit test lulus: 132 QwenUI dan 41 Baritone**, tanpa failure, error, atau skipped. JAR distribusi yang sama digunakan pada client Minecraft asli, bukan mockup UI.

Pilihan font Sans/Minecraft, balon rounded pendek/panjang, wrapping dan scroll, tombol fitur, aksi AI langsung, penolakan command palsu, konfigurasi native Litematica, dan tampilan jendela kecil diperiksa. Semua delapan tab dibuka pada 1280×800 dan 640×360; kedua font diperiksa di jendela kecil. **114 audit halaman dan 114 audit chat** pada log final semuanya melaporkan **problems=0**. Pemeriksaan ini mencakup bounds widget serta geometri balon/teks, bukan seluruh kombinasi isi atau GPU. **24 screenshot asli** disertakan; log audit tersedia di `bukti-uji/runtime-ringkas.txt`.

**Tidak ada key Gemini pengguna atau key proyek lain yang dipakai. Koneksi Gemini live tidak diuji.** Percakapan pada screenshot merupakan respons simulasi berlabel **UJI UI OFFLINE**, melalui harness terpisah yang tidak masuk ke JAR distribusi. Perubahan setting dan command yang dipicu harness tetap dijalankan oleh executor mod asli.

## Build dan regresi

Perintah final:

```sh
JAVA_HOME=/path/to/jdk-25 ./gradlew :fabric:remapJar :fabric:test test --rerun-tasks
```

Hasil: **BUILD SUCCESSFUL**, 16 task dieksekusi. XML JUnit dan log build final disertakan. Tes baru mencakup migrasi/persistensi font, nilai tidak valid, ukuran balon, padding/scroll, chat umum, prompt aksi langsung, konteks riwayat, penggabungan status berurutan, dan urutan giliran ketika riwayat terpotong. Tes regresi command, makro, keybind, HTTP tiruan, daftar model, TestConnection, pembatalan request dan callback lama tetap dijalankan.

Tes koneksi tidak mencemari riwayat percakapan dengan pesan model tanpa pasangan user. Riwayat yang terpotong tetapi dimulai dengan status asisten diberi giliran konteks user sehingga isinya tetap terpelihara dan payload tidak dimulai dengan giliran model.

Build masih menampilkan peringatan upstream tentang Unsafe, API deprecated/unchecked dan Gradle 9. Berhasil build tidak berarti tidak ada peringatan.

## Aksi AI tanpa tinjau

Respons tiruan `version` melewati parser dan jalur `acceptReply → PlanExecutor.execute → antrean command`, **tanpa klik Tinjau/Terapkan**. Baritone asli mengembalikan versi `1.19.0+qwenui.1.4.0`. Pemeriksaan runtime menunjukkan `pending=0` dan `reviewing=false`.

Setting visual Baritone **renderPath** diuji **true → false → true** melalui aksi AI langsung. Toggle visual Litematica **Enable Rendering** diuji **true → false → true** melalui adapter mod asli. State runtime dan pemulihannya dicatat. Tidak ada penambangan atau pembangunan schematic baru yang dijalankan.

Command palsu `unsupported_fake_command` ditolak registry; kegagalan tampil di chat tanpa menyimpan rencana yang dapat diterapkan ulang. Status dispatch dan selesainya antrean tampil terpisah. Selesainya antrean bukan bukti pekerjaan dunia terakhir sudah selesai. Validasi dan stop darurat tetap ada walaupun tahap persetujuan dihapus dari jalur AI.

## Chat, font, dan layout

Pilihan font diubah melalui klik tombol Preferensi, lalu nilai tersimpan diperiksa. Font memengaruhi label, tombol, input dan chat. Unit test memastikan konfigurasi lama tidak kehilangan preferensi, makro dan keybind.

Satu balon membungkus satu pesan; ukuran diukur dari teks yang sudah dibungkus. Padding, label/avatar, jarak antarpesan, viewport dan indikator scroll dipisahkan. Teks panjang, baris baru dan kata panjang diperiksa. Pemotongan di batas viewport saat menggulir disengaja; pesan tidak digambar di atas tombol atau input. Pesan yang keluar viewport tetap dapat dibaca dengan scroll.

Tombol respons membuka editor **allowBreak** dengan nilai aktual. Contoh percakapan umum dan pertanyaan lanjutan ditampilkan secara offline; unit test memeriksa bahwa riwayat diteruskan dalam payload. Ini tidak membuktikan kualitas jawaban atau ingatan model Gemini live.

Penghapusan chat diuji lewat jalur internal yang sama dengan tombol konfirmasi; chat kembali kosong. Tombol Test tanpa key/model membuka halaman konfigurasi. Field key tetap kosong dan **Ingat key OFF**. Skala 75% dan permintaan 150% diperiksa; skala efektif dibatasi agar kontrol tetap terjangkau. Pengujian akhir tidak mengulang drag resize panel atau setiap operasi mouse/keyboard fitur lama.

## Litematica tetap terpisah

Client memakai **Litematica 0.28.8, MaLiLib 0.29.6**, Fabric Loader 0.19.3 dan Mod Menu 20.0.3 sebagai mod terpisah. Katalog runtime berisi **42 command, 248 setting Baritone, dan 110 entri fitur Litematica**. Jumlah entri tidak berarti seluruh fitur tersedia pada semua keadaan.

Pencarian alat dan tombol **Litematica configuration** membuka layar konfigurasi asli; Esc kembali ke QwenUI. Alur manual tab Litematica tetap mempertahankan review yang sudah ada; aksi dari AI tidak memakainya. Fallback tanpa Litematica dicakup unit test, bukan sesi runtime final ini. Tidak semua editor/placement/opsi diuji satu per satu.

## Integritas dan batas

Pengemasan memeriksa metadata versi, kelas UI, ikon/font, dan kesamaan SHA-256 antara JAR distribusi dan JAR client final. Implementasi Litematica/MaLiLib serta harness tidak boleh masuk ke JAR. Source, screenshot asli, bukti uji dan checksum disertakan; API key, konfigurasi akun dan dunia uji tidak disertakan.

Client keluar melalui **Save and Quit**, lalu **Quit Game**, dengan log `Client shutdown complete; cached worlds flushed.` Lingkungan pengujian adalah Linux dengan renderer software Mesa/llvmpipe dan identitas offline. Peringatan otorisasi Realms/profile offline bukan bukti kegagalan mod ataupun pengujian autentikasi nyata.

Belum diuji: Gemini live dengan akun/key/model pengguna, Windows/macOS, seluruh GPU/shader, server/modpack lain, semua parameter command, pembangunan schematic besar, semua editor Litematica, IME, serta jendela di bawah 640×360. AI hanya mengakses operasi katalog yang didukung, bukan shell/filesystem/kode arbitrer. Eksekusi langsung dapat salah memahami permintaan dan mengubah dunia; **cadangkan dunia**. STOP/End menghentikan pekerjaan berikutnya, bukan membatalkan blok yang sudah berubah. Tidak ada jaminan rollback dunia penuh atau bebas bug.

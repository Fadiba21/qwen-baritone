# Keamanan

## Data dan API key

Panel/perintah biasa bekerja tanpa Gemini. Fitur AI menghubungi endpoint HTTPS Google `generativelanguage.googleapis.com` saat pengguna memuat model, menguji koneksi, atau mengirim chat. Prompt, riwayat sesi, dan katalog fitur aktif (termasuk nilai pengaturan serta nama dan koordinat placement Litematica) dapat dikirim ke Google; kuota serta kebijakan Google berlaku.

Tidak ada API key bawaan. Key berada di memori kecuali **Ingat key** diaktifkan: opsi ini menyimpan teks biasa dalam `config/qwenui.json`, bukan penyimpanan terenkripsi. Jangan membagikan konfigurasi tersebut. Jangan memasukkan kredensial, alamat server privat, atau informasi pribadi ke chat yang tidak ingin dikirim ke penyedia AI.

## Otomatisasi dunia

Aksi AI yang lolos validasi langsung dijalankan tanpa konfirmasi tambahan. AI dapat salah; cadangkan world dan uji di singleplayer terlebih dahulu. **End/STOP** menghentikan tugas dan antrean berikutnya, tetapi tidak memulihkan blok atau item yang sudah berubah. Validasi registry dan batas payload bukan jaminan keamanan absolut atau kompatibilitas semua server.

## Melaporkan masalah

Jangan menaruh secret atau detail eksploit yang belum ditangani pada issue publik. Jika tab **Security → Advisories → Report a vulnerability** tersedia, gunakan jalur privat tersebut. Bila belum tersedia, buat issue singkat meminta kanal pelaporan privat, tanpa menyertakan exploit, token, log mentah, atau data pribadi. Tidak ada janji waktu respons.

Jika key telanjur terbuka, cabut/rotasi key di penyedianya; menghapusnya dari commit terbaru saja tidak menghapus riwayat. Bersihkan konfigurasi dan log sebelum berbagi bukti. Fokus pemeliharaan saat ini adalah source **1.6.0-hotfix.2 / Fabric Minecraft 26.2**.

Audit publikasi mencakup pemeriksaan statis berkas fork dan pemisahan data runtime, bukan penetration test atau sertifikasi keamanan dependensi.

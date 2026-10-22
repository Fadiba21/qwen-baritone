# Hotfix.3 — inventory dan hotbar

## Bug ditemukan dan diperbaiki

Bug berhasil direproduksi pada **JAR hotfix.2 asli** di Minecraft 26.2/Fabric 0.19.3. Setelah inventory ditutup, pickaxe dan cobblestone yang dipindahkan manual kembali ditarik ke hotbar. Antrean diamond–emerald juga dieksekusi ketika tidak ada permintaan tugas baru.

Penyebabnya bukan sekadar GUI atau nomor slot: tick inventory melakukan penataan otomatis ke slot tetap dan mengeksekusi ulang antrean lama. Guard hotfix sebelumnya hanya menunda masalah sampai GUI ditutup.

**Hotfix.3 menghapus penataan inventory saat idle dan auto-eksekusi antrean.** Builder, placement, dan mining masih boleh meminta item saat benar-benar dibutuhkan; auto-tool mengambil alat dari inventory pada saat mining, bukan ketika pemain sekadar menutup GUI. Pemanggil menerima status belum siap selama pertukaran tertunda.

## Pengaman tambahan

Pilihan slot sekarang deterministik, mengutamakan slot kosong. Permintaan kedaluwarsa bila tidak diperbarui. Perubahan stack/jumlah/komponen, inventory lain, selected slot, revisi menu, atau identitas pemain membatalkan antrean. Validasi diulang tepat sebelum satu SWAP dikirim; cursor harus kosong. GUI dan menu non-inventory juga mencegah pemilihan slot otomatis pada jalur builder/tool/placement/bucket. Pauser menghitung tick diam berturut-turut agar permintaan aktif tetap bisa selesai tanpa auto-drain antrean.

Saat hotbar penuh dan tugas aktif memang memerlukan item, swap yang disengaja masih diperbolehkan; item hotbar lama dikembalikan ke slot sumber. Ini berbeda dari bug swap tanpa permintaan saat idle.

## Hasil yang benar-benar dijalankan

**72/72 pemeriksaan runtime akhir lulus.** Suite utama menjalankan 2693 operasi tercatat pada client sungguhan dengan server singleplayer terintegrasi. Ini bukan hanya simulasi unit test.

| Pengujian | Hasil akhir |
|---|---|
| Regresi menutup inventory + 1.000 tick inventory tambahan | Tidak menarik item kembali ke hotbar |
| Semua 27 slot main inventory × 9 hotbar, 3 putaran | 729 pasangan bolak-balik; **1.458 SWAP** lulus per sesi |
| Semua 243 pasangan lewat callback tombol angka GUI | **486 input** lulus |
| Shift-click stack 64, stack 16, dan alat non-stack | 50 kali per jenis, **150 transfer** lulus |
| Split, drag/QUICK_CRAFT, PICKUP_ALL, cursor berisi | Jumlah sesuai; guard tidak bocor |
| Source/tujuan/jumlah/slot lain/selected berubah; GUI, cancel, pause | Tidak mengeksekusi antrean usang |
| Alat dengan damage dan enchantment berbeda | Komponen dipertahankan, client–server cocok |
| Cooldown, stationary-only, slot terlarang/invalid/kosong | Tidak ada sukses palsu atau antrean macet pada kasus yang diuji |
| Input X11 asli | Tombol angka, Shift+klik, klik kanan dan drag 63→21+21+21 lulus |
| Menu peti dari server, pause, chat, creative | Permintaan otomatis ditolak saat GUI terbuka |
| Builder dan mining nyata melalui perintah selection | Resume setelah GUI, restock, pasang/pecahkan blok lulus |
| Permintaan bertabrakan, hotbar penuh, allowInventory=false | Tidak ada swap/sukses palsu; demand baru tetap selesai |
| Crafting 2×2, offhand F, pertukaran Elytra | Hasil sesuai dan cursor aman pada kasus yang diuji |

Suite utama akhir: 44 pemeriksaan. Input/tugas/konteks tambahan: 13. Konflik/hotbar penuh: 8. Edge case: 7.

**Unit test: 184 eksekusi, 0 gagal. Clean build berhasil dengan Java 25.** Percobaan build awal terhenti karena inode penyimpanan habis; workspace build dipindahkan ke memori lalu clean build lengkap diulang dan lulus.

Ada dua sesi client: sesi awal tanpa Litematica, lalu pengulangan dengan **Litematica 0.28.8 + Malilib 0.29.6**. Sesi awal meluluskan matriks auto-SWAP tetapi uji tombol angka slot kesembilan gagal karena adaptor mengirim `0` alih-alih `9`. Pemetaan harness diperbaiki, seluruh suite diulang, dan hasil akhir di atas lulus. Log percobaan pertama tetap disertakan; kegagalan ini tidak disembunyikan atau dihitung sebagai lulus. Percobaan drag awal baru melewati dua slot setelah mouse ditekan, sehingga vanilla membagi 63 blok menjadi 31+31 dan menyisakan 1 di cursor. Urutan gerakan uji diperbaiki agar benar-benar melewati tiga slot; hasil akhirnya 21+21+21. Percobaan awal drag juga disimpan. Uji offhand awal mengirim mouse-move dan F tanpa jeda render sehingga mengenai hover slot sebelumnya; setelah jeda hover ditambahkan, swap offhand yang dituju lulus. Hasil awal tetap disertakan.

## Batas pengujian

Tidak mungkin menjamin semua kombinasi Minecraft hanya dari satu lingkungan. Yang diuji adalah client Linux/render software, Minecraft 26.2, Fabric 0.19.3, Java 25, dan server terintegrasi lokal. Belum diuji pada server publik dengan latency/packet loss, plugin inventory pihak ketiga, disconnect/reconnect di tengah transaksi, semua mode creative/recipe, atau seluruh alur farm/elytra flight panjang. Menu peti dibuka server sebagai ChestMenu nyata dengan SimpleContainer, bukan peti blok di dunia. Sejumlah aksi memakai callback GUI atau metode inventory mod melalui agent test-only; input X11 dan tugas aktif diuji terpisah. Agent pengujian **tidak dimasukkan ke JAR mod**.

Dengan demikian, klaimnya adalah bug yang direproduksi sudah tertutup dan matriks di atas lulus—bukan jaminan bahwa seluruh kemungkinan sudah teruji.

## Memasang

Tutup Minecraft. Ganti JAR Baritone lama dengan `baritone-qwenui-1.6.0-hotfix.3-mc26.2-fabric.jar`; jangan menyimpan dua versi mod Baritone di folder mods. Gunakan Minecraft 26.2, Fabric Loader 0.19.3 atau lebih baru yang kompatibel, dan Java 25. `allowInventory` tetap dapat digunakan untuk kebutuhan tugas aktif; tidak perlu mematikannya sebagai jalan pintas. Pengaturan dan world tidak dibundel atau ditimpa oleh paket ini.

Paket bukti memuat JSON hasil, log client/build, riwayat percobaan pertama, patch source, XML unit test, script/harness uji, dan screenshot asli. Checksum tersedia dalam SHA256SUMS.txt.

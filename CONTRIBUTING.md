# Berkontribusi

Terima kasih sudah membantu Qwen Baritone MC26.2 Fabric. Perubahan kontribusi ditujukan ke cabang `qwen-main`.

1. Fork repo, lalu buat branch kerja dari `qwen-main` di fork Anda. Buat perubahan pada branch itu dan ajukan pull request (PR) ke branch `qwen-main` di repo ini.
2. Gunakan Temurin JDK 25 dan Gradle Wrapper 8.14.4. Sebelum mengirim PR, jalankan dari root repo:

   ```sh
   ./gradlew clean :fabric:clean :fabric:build test --no-daemon --max-workers=2
   ```

   Pastikan build dan tes lulus, lalu jelaskan ringkas perubahan dan hasil pengujian di PR.
3. Untuk menyelaraskan fork, ambil perubahan dari upstream cabang `26.2` lalu **merge** ke branch kerja Anda. Jangan squash atau reset riwayat saat menyelaraskan upstream.
4. Jangan sertakan API key/token, kata sandi, konfigurasi lokal, log yang berisi rahasia, atau file dunia (world) pribadi. Periksa diff sebelum mengirim PR; sanitasi log dan konfigurasi sebelum melampirkannya.

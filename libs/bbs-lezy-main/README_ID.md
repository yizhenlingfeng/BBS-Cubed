# BBS Lezy

<p align="center"><img src="bbs-lezy.webp" alt="BBS Lezy" width="500"></p>

<p align="center">
  <a href="README.md">English</a>  |  <b>Bahasa Indonesia</b>
</p>

---

BBS Lezy adalah addon untuk membantu proses pembuatan konten Minecraft Leji dengan target video seperti Grox, Reff, Remanrhn, dll.

Addon ini dirancang untuk scene berskala besar dan kemudahan editing: batas render model (LOD) untuk ribuan actor, manajemen panel replay massal, **export video 2 track audio terpisah** (klip BBS + suara Minecraft), **pembacaan langsung berbagai format audio** (.mp3, .m4a, .opus, .flac, dll) tanpa konversi, serta perbaikan workflow lainnya. Semua fitur dapat dikonfigurasi langsung dari editor BBS tanpa perlu ngoprek file config secara manual.

## Fitur

**1. Batas render model (Limit Replay)**

Nggak semua model replay dirender setiap frame — hanya yang terdekat dengan kamera yang masuk hitungan. Berguna banget pas scene punya ribuan actor sampai GPU menangis. Model yang terpilih untuk di-hide dimanipulasi lewat runtime value BBS, jadi keyframe animasi nggak rusak. Pas rendering/export video, tinggal dimatiin biar semua model tergambar.

**2. Panel replay**

- **Select all replays** — pilih semua replay di film, termasuk yang ada di dalam folder tertutup (select-all bawaan BBS cuma yang terlihat di layar).
- **Select same model** — pilih semua replay yang model-nya sama dengan seleksi sekarang. Cocok untuk ambil semua hasil duplicate farm dalam satu kali klik.
- **Duplicate to total** — angka yang dimasukkan adalah **total** copy di seluruh seleksi, bukan per-replay. Misal: pilih 3 replay, input 150, hasilnya 150 copy (50 per replay), bukan 450. Tiap replay dapat kategori sendiri (`Duplicates N`) biar gampang dikontrol atau dihapus.
- **Tombol scroll atas/bawah** — lompat instant ke atas atau ke bawah daftar replay, tanpa animasi. Berguna pas daudarnya udah ribuan baris.
- **Reset replay actors** — menu klik kanan di panel list replay untuk me-respawn semua replay actor kembali ke posisi awal dan menyegarkan display-nya, tanpa perlu keluar-masuk dashboard.

**3. Audio export & codec**

- **Separate audio tracks (2 track audio terpisah)** — saat export video dengan opsi bawaan BBS **audio** dan **minecraft sounds** dua-duanya aktif, hasil video membawa **dua track audio terpisah** (Track 1: audio klip film BBS, Track 2: efek suara/SFX Minecraft) bukan satu track campuran. Setiap track dipaksa stereo 2-channel standar (AAC 192k) sehingga langsung rapi saat diimpor ke software editing (Premiere Pro, DaVinci Resolve, CapCut, Vegas Pro, dll). File WAV sementara otomatis dibersihkan setelah mux selesai.
- **Multi-codec audio langsung & dikenali di Pick Audio** — BBS kini bisa langsung membaca `.mp3`, `.m4a`, `.aac`, `.opus`, `.wma`, `.alac`, `.ape`, `.flac`, `.aif/.aiff`, dan `.ac3`. File-file ini langsung terdeteksi di menu "Pick audio...", bisa dipreview waveform-nya, diedit (offset, durasi, volume), dicut/split di timeline, dan dirender layaknya WAV bawaan. Decode berjalan on-demand via ffmpeg, sehingga file asli di disk tidak pernah diubah atau dikonversi.
- **Import file audio tanpa konversi** — file audio yang di-drag & drop ke dalam BBS langsung disalin **apa adanya** (byte-identical), tidak lagi dire-encode paksa menjadi WAV mono. Khusus file video (`.mp4`), audionya tetap diekstrak otomatis seperti biasa.

**4. Screen effect clips (Big Thanks to ElgatoPro300)**

Many thanks to ElgatoPro300 (who make BBS CML) to make this feature

https://github.com/user-attachments/assets/169defa4-20fe-452a-8dac-c3a78e5c6c52

- **Cinematic Effect** — satu clip yang menggabungkan efek kamera jadul: vintage film (flicker, goresan acak, desaturasi), framing / letterbox, film grain, dan optik (fisheye, chromatic aberration, VHS glitch, radial blur). Tiap efek punya parameter sendiri dan bisa dikombinasikan.
- **Color Grade** — color grading murni (saturation, hue, brightness, contrast, lift, gamma, gain) plus flat overlay tint.
- **Vignette** — penggelapan radial di tepi frame.
- **Letterbox** — bar hitam sinematik (tinggi, lebar, smoothness, warna, offset, rotasi, zoom).
- **Hierarki clip per track** — efek clip sekarang mengikuti urutan track di camera timeline. Tiap track dapat pass sendiri, dari track paling bawah ke atas, jadi Color Grade di track atas dibaca sebagai lapisan efek di atas grade track bawah — bukan dijumlahkan jadi satu look. Beberapa clip di track yang sama tetap dijumlahkan seperti biasa, berguna buat menumpuk grade. Film yang semua clipnya ada di satu track tetap cuma satu pass, jadi tidak ada biaya performa tambahan.

**5. Illusion (duplikasi visual)**

Thanks again to ElgatoPro300 :D

- Menambah duplikat visual di sekitar form atau model block **tanpa menambah entitas** di scene. Jadi satu actor bisa terlihat jadi 10 tanpa menambah beban entity Minecraft.
- Bisa diarahkan ke 6 arah (depan, belakang, kiri, kanan, atas, bawah), diatur jaraknya (spread / spacing), diberi opacity fade, dan punya toggle **Enabled** untuk menyalakan / mematikan semua duplikat.
- Opsi lanjutan: **Uniform Distance** (jarak antar duplikat sama rata), **Real** (duplikat ikut berinteraksi dengan block dunia, misal menapak blok), **Distort** (duplikat hancur jadi streak), dan **Gradual Transform** (transformasi naik dari model utama ke duplikat terakhir).
- Bisa di-keyframe lewat Dope Sheet sebagai track `illusion` dan `illusion_transform`, jadi jarak serta transformasi bisa dianimasikan sepanjang timeline — baik di Model Block maupun di Replay Actor.
- Semua parameter diatur dari section **Illusion** di panel Form Editor.

**6. Video export (CQP, codec & GPU)**

- **Video CQP** — atur kualitas/kompresi (0–51, default 18).
- **Video Codec** — pilih `h264` (default, kompatibilitas maksimum), `h265` (HEVC, kompresi lebih baik), atau `vp9` (WebM).
- **Hardware Acceleration (GPU)** — pakai encoder GPU (NVIDIA NVENC, AMD AMF di Windows / VA-API di Linux, Intel QSV dengan fallback VA-API di Linux) buat render jauh lebih cepat dan beban CPU lebih ringan. Default: **nyala**. Ada opsi **Auto-Detect GPU** atau pilih vendor tertentu.
- **Peringatan codec GPU** — kalau codec yang dipilih (mis. VP9) atau GPU / build ffmpeg tidak mendukung hardware encoder tersebut, muncul dialog yang nawarin encode pakai CPU untuk export itu saja.
- **Perbaikan Linux (QoL)** — slider terbatas membungkus kursor di tepi jendela pada Linux/XWayland seperti trackpad biasa, export video Linux otomatis mendeteksi dan memakai NVENC, Intel QSV (fallback ke VA-API bila QSV tidak terdeteksi), atau AMD VA-API sesuai render node, serta menyalakan/reload shaderpack Iris di dalam editor BBS menjadwalkan reload bersih saat editor ditutup.

## Dokumentasi

### Requirement

| Item      | Versi                                    |
| --------- | ---------------------------------------- |
| Minecraft | 1.20.1                                   |
| Java      | 17+                                      |
| Fabric    | Loader 0.16.14, Fabric API 0.92.1+1.20.1 |
| BBS FS    | 2.7-1.20.1                               |
| Sodium    | 0.5.8                                    |
| Iris      | Opsional (untuk dukungan shader)         |

### Install

1. Download file `.jar` dari [Releases](../../releases) (nggak perlu git clone).
2. Drop ke folder `mods` seperti addon Fabric pada umumnya.

### Setting Lezy

Semua setting bisa diubah dari dua tempat:

**Lewat settings screen BBS** (ikon gerigi → kategori BBS Lezy):

| Setting                 | Range   | Keterangan                                                                                                                                                                                                        |
| ----------------------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Enable render limit     | on/off  | Nyalain/matemin seluruh fitur batas render.                                                                                                                                                                       |
| Max rendered models     | 0–2000  | Jumlah model yang tetap dirender per frame. Sisanya di-hide. **0 = mati** (semua dirender).                                                                                                                       |
| Focus distance          | 0–256m  | Prioritas model di sekitar jarak ini dari kamera. **0 = model terdekat** yang menang. Bisa dipakai untuk ngeliat replay jauh tanpa naikin render limit.                                                           |
| Separate audio tracks   | on/off  | Hasil export bawa dua track audio terpisah (klip BBS + suara Minecraft). Cuma ngaruh pas opsi export BBS **audio** dan **minecraft sounds** dua-duanya nyala. Mati = satu track campuran kayak biasa.             |
| Open folder on import   | on/off  | Otomatis buka File Explorer ke folder tujuan saat drag & drop file ke BBS. Default: **mati** (tidak otomatis buka folder).                                                                                        |
| Baking batch percentage | 1–100%  | Mengatur seberapa agresif / persentase total replay yang diproses per kelompok frame saat Look At baking. Nilai tinggi = lebih cepat selesai, nilai rendah = progress bar lebih halus & stabil (default: **5%**). |
| Video CQP (Quality)     | 0–51    | Constant Quantization Parameter. Makin kecil makin bagus kualitasnya, makin besar makin kecil filenya. **0 = lossless**, 18 = default (high quality), 23 = seimbang, 28 = file kecil.                             |
| Video Codec             | pilihan | `h264` (default, kompatibilitas maksimum), `h265` (HEVC, kompresi lebih baik), atau `vp9` (WebM).                                                                                                                 |
| Hardware Acceleration   | on/off  | Pakai encoder GPU (NVIDIA NVENC / AMD AMF / Intel QSV) buat render jauh lebih cepat. Default: **nyala**.                                                                                                          |
| GPU Encoder             | pilihan | Vendor encoder yang dipakai: Auto-Detect (dari GPU OpenGL yang aktif), NVIDIA (NVENC), AMD (AMF), atau Intel (QSV).                                                                                               |

**Lewat toolbar preview film editor** — klik ikon mata (sebelah tombol motion path) untuk buka popup: toggle On/Off, slider Render Limit, slider Focus Distance. Perubahan otomatis kesimpan ke `bbslezy.json`.

File setting-nya ada di `<folder config BBS>/bbs/settings/bbslezy.json`.

### Build dari source

```bash
sh ./gradlew build
```

Hasilnya ada di `build/libs/bbs-lezy-<versi>.jar`.

> Catatan: BBS-nya sendiri harus sudah ter-publish ke maven local dulu (`sh ./gradlew publishToMavenLocal` dari folder BBS). Addon ini dikunci ke BBS 2.7-1.20.1 — versi BBS beda bikin dev environment aneh.

### Catatan teknis

- **Mixin UI bersifat fail-safe**: `bbslezy.mixins.json` pake `required: false` + `defaultRequire: 0`. Kalau BBS internal berubah dan mixin gagal, addon tetap jalan — cuma fitur panel replay dan audio yang ilang, fitur Limit Replay tetap aman karena lewat API resmi. Target mixin baru: `VideoExportSession` (two-track export), `AudioReader` (codec), `ToWAVImporter`/`WAVImporter` (no-conversion import), `UISoundOverlayPanel` (pick audio), `UIScreen` (drag-import auto open folder toggle), `UIProcessReplaysPanel` (Look at per-tick & async baking), `UIFormUndoHandler` (single compound undo).
- **Restore override hanya dilakukan di `RENDER_AFTER` dan `SHUTDOWN`**, bukan di render pass biasa, karena shadow dan name tag digambar setelahnya.
- **Budget dihitung sebagai squared distance** — nggak ada `sqrt` dan nggak ada alokasi `Vec3d` per form, biar murah pas ribuan actor.
- Form yang terkunci ke kamera (`anchor` punya target) nggak ikut di-cull, sama seperti behavior bawaan BBS.
- Editor click tetap bisa select actor yang lagi di-cap — pass picking di-skip dari culling.

## Credit

Biburan dan fitur besarnya berawal dari karya orang-orang ini, jadi terima kasih yang sebesar-besaranya:

- [McHorse](https://www.youtube.com/@McHorsesCreations) — penulis BBS FS itu sendiri.
- [ElgatoPro300](https://www.youtube.com/@ElGatoPro300) — buat **screen effect clips** dan **illusion**. Dua fitur itu hasil porting dari BBS CML miliknya, jadi credit-nya balik lagi ke dia.
- [Wemmpy](https://www.youtube.com/@Wemppy4) — soal teks FatalError dan support bahasa Indonesia.

dan AI yang bersedia untuk dipecut 😈

## License

MIT — bebas dipake dan dimodifikasi. Bosan dengan bug atau pengen nambahin fitur? Fork aja sendiri

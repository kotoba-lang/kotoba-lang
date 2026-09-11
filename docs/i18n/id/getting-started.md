# Mulai memakai Kotoba

**Bahasa:** [indeks](../README.md) ·
[English](../../getting-started.md) ·
**Bahasa Indonesia** ·
[Basa Jawa](../jv/getting-started.md) ·
[Basa Sunda](../su/getting-started.md) ·
[Español](../es/getting-started.md) ·
[עברית](../he/getting-started.md) ·
[한국어](../ko/getting-started.md) ·
[Italiano](../it/getting-started.md) ·
[العربية](../ar/getting-started.md) ·
[الدارجة](../ar-MA/getting-started.md) ·
[المصرية](../arz/getting-started.md) ·
[Deutsch](../de/getting-started.md)

> **Catatan terjemahan.** Terjemahan mesin dari panduan Inggris
> [getting started](../../getting-started.md). Tinjauan penutur asli:
> **belum diverifikasi**. Inggris tetap sumber. Kode, perintah, URL, dan
> pengenal tidak diubah.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` tidak punya URL
> Release bahasa. Tag CLI di `kotoba-lang/kotoba` bukan gerbang itu.

Jalur ini membawa dari mesin kosong ke program yang sudah diperiksa.
Pemasangan dan rilis biner dimiliki
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba), bukan
repositori kontrak bahasa ini.

## 1. Pasang CLI native

Di macOS atau Linux dengan Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 menolak memuat formula dari tap yang belum diminta untuk
dipercaya, jadi tanpa baris tengah `brew install` berhenti dengan
`Refusing to load formula … from untrusted tap`.

Atau pakai penginstal yang memverifikasi checksum dari repositori
implementasi:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

Halaman rilis adalah otoritas untuk platform dan artefak yang tersedia:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. Periksa pemasangan

```sh
kotoba selfhost check --json
```

Terima hanya respons `valid` dengan daftar masalah kosong. Memasang saja
tidak membuktikan bahwa sumber terkompilasi atau berjalan.

## 3. Bangun berkas sumber

Buat `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

Kompilasi untuk WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

Terima hanya respons `emitted`. Untuk pemeriksaan eksekusi yang tidak
bergantung host, ikuti
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
yang diterbitkan (Inggris): muat modul, tolak import tak terduga, dan
panggil `main` yang diekspor. Perintah CLI milik adapter boleh
mengembalikan `planned` / `adapter-required`; itu rencana, bukan bukti
eksekusi.

Kode baru khusus Kotoba memakai `.kotoba`. Sumber keluarga Clojure yang
dibagi memakai `.cljc` dan memilih perilaku Kotoba dengan `#?(:kotoba …)`.

## 4. Buat efek eksplisit

Sumber Kotoba tidak punya otoritas ambien ke sistem berkas, jaringan,
rahasia, jam, atau proses. Komponen menyatakan import; kebijakan memberi
subset terbatas; runtime hanya mengikat provider yang diberikan. Kebijakan
kosong menolak setiap efek host, termasuk `:host/http`. Itu produk untuk
kode yang tidak dipercaya: otoritas yang tidak diberikan tidak berjalan.

Deploy berbayar yang dihosting dari grant itu belum produk publik. Jangan
anggap `kotoba deploy` analog Deno Deploy yang bisa dibeli hari ini.

Mulai dari [capability values](../../lang/capability-values.md) (Inggris)
sebelum menulis kode ber-efek.

## 5. Kenali batas kompatibilitas

Kotoba berbentuk Clojure, bukan janji bahwa Clojure JVM atau ClojureScript
sebarang berjalan tanpa perubahan. Periksa klasifikasi sekarang sebelum
mengandalkan suatu bentuk:

- ikhtisar manusia: [language surface matrix](../../lang/surface-matrix.md)
- otoritas mesin: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- tata bahasa yang diterima: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

Lanjut ke [language reference](../../reference/language.md) dan
[tooling reference](../../reference/tooling.md) (keduanya Inggris).

# Kotoba

> **AI menulis bebas. Kotoba menarik batasnya.**

**Bahasa:** [indeks](../README.md) ·
[English](../../../README.md) ·
**Bahasa Indonesia** ·
[Basa Jawa](../jv/README.md) ·
[Basa Sunda](../su/README.md) ·
[Español](../es/README.md) ·
[עברית](../he/README.md) ·
[한국어](../ko/README.md) ·
[Italiano](../it/README.md) ·
[العربية](../ar/README.md) ·
[الدارجة](../ar-MA/README.md) ·
[المصرية](../arz/README.md) ·
[Deutsch](../de/README.md)

Kotoba adalah bahasa dan tumpukan komputasi yang intuitif, deklaratif, dan
security-first untuk agen AI — dan untuk manusia yang vibe-code bersama mereka.

**Perangkat lunak yang ada menambahkan keamanan di sekeliling program.
Kotoba membuat keamanan menjadi sifat seluruh komputasi.**

Halaman GitHub ini membantu menyelesaikan **satu pekerjaan**: memasang CLI
dan mengompilasi program yang sudah diperiksa. [README](../../../README.md)
Inggris tetap ikhtisar proyek. Kontrak normatif tetap berkas Inggris yang
bisa dibaca mesin.

> **Catatan terjemahan.** Terjemahan mesin. Tinjauan penutur asli:
> **belum diverifikasi**. Inggris adalah sumber.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` tidak punya URL
> Release bahasa. Tag CLI di
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) adalah
> binding implementasi, bukan gerbang itu.

[kotoba-lang.org](https://kotoba-lang.org) · implementasi dan CLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[Mulai](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

Ekspresi itu mengikuti jalur kompilasi yang sama dengan berkas. Itu
kenyamanan compile-and-run, bukan `eval` runtime tanpa batas.

## Satu pekerjaan

1. Pasang CLI (Homebrew atau penginstal yang memverifikasi checksum).
2. `kotoba selfhost check --json` — terima hanya respons `valid` dengan
   daftar masalah kosong.
3. Kompilasi `hello.kotoba` dan terima hanya respons `emitted`.

Langkah lengkap: [Mulai memakai Kotoba](getting-started.md).

## Ini bukan

- Deploy berbayar yang dihosting **belum** hidup. `kotoba deploy` hari ini
  bukan analog Deno Deploy yang bisa dibeli.
- Kotoba berbentuk Clojure, bukan janji bahwa Clojure JVM atau ClojureScript
  sebarang berjalan tanpa perubahan.
- Halaman ini tidak mengada-adakan angka GMV, traksi pelanggan, atau
  percepatan universal.

Kartu mulai situs yang sudah terbit (tidak diubah):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).

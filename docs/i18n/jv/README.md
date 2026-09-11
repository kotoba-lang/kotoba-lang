# Kotoba

> **AI nulis kanthi bebas. Kotoba nggambar watese.**

**Basa:** [indheks](../README.md) ·
[English](../../../README.md) ·
[Bahasa Indonesia](../id/README.md) ·
**Basa Jawa** ·
[Basa Sunda](../su/README.md) ·
[Español](../es/README.md) ·
[עברית](../he/README.md) ·
[한국어](../ko/README.md) ·
[Italiano](../it/README.md) ·
[العربية](../ar/README.md) ·
[الدارجة](../ar-MA/README.md) ·
[المصرية](../arz/README.md) ·
[Deutsch](../de/README.md)

Kotoba iku basa lan tumpukan komputasi sing intuitif, deklaratif, lan
security-first kanggo agen AI — uga kanggo manungsa sing vibe-code karo
dheweke.

**Piranti lunak sing ana nambah keamanan ing saubengé program. Kotoba
nggawé keamanan dadi sifat saka kabèh komputasi.**

Kaca GitHub iki mbantu ngrampungaké **siji pagawéan**: nginstal CLI lan
ngompilasi program sing wis dipriksa. [README](../../../README.md) Inggris
tetep ringkesan proyek. Kontrak normatif tetep berkas Inggris sing bisa
diwaca mesin.

> **Cathetan terjemahan.** Terjemahan mesin. Tinjauan penutur asli:
> **durung diverifikasi**. Inggris iku sumber.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` ora duwé URL
> Release basa. Tag CLI ing
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) iku
> binding implementasi, dudu gapura iku.

[kotoba-lang.org](https://kotoba-lang.org) · implementasi lan CLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[Miwiti](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

Ekspresi iku ngetutaké dalan kompilasi sing padha karo berkas. Iku
kalancaran compile-and-run, dudu `eval` runtime tanpa wates.

## Siji pagawéan

1. Instal CLI (Homebrew utawa penginstal sing mverifikasi checksum).
2. `kotoba selfhost check --json` — mung nampa wangsulan `valid` kanthi
   dhaptar masalah kosong.
3. Kompilasi `hello.kotoba` lan mung nampa wangsulan `emitted`.

Langkah jangkep: [Miwiti nganggo Kotoba](getting-started.md).

## Iki dudu

- Deploy mbayar sing di-host **durung** urip. `kotoba deploy` dina iki
  dudu analog Deno Deploy sing bisa dituku.
- Kotoba wujude Clojure, dudu janji yèn Clojure JVM utawa ClojureScript
  sembarang mlaku tanpa owah-owahan.
- Kaca iki ora nggawé angka GMV, traksi pelanggan, utawa kacepetan
  universal.

Kertu miwiti situs sing wis diterbitaké (ora diowahi):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).

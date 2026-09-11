# Kotoba

> **AI nulis bébas. Kotoba ngagambar watesna.**

**Basa:** [indeks](../README.md) ·
[English](../../../README.md) ·
[Bahasa Indonesia](../id/README.md) ·
[Basa Jawa](../jv/README.md) ·
**Basa Sunda** ·
[Español](../es/README.md) ·
[עברית](../he/README.md) ·
[한국어](../ko/README.md) ·
[Italiano](../it/README.md) ·
[العربية](../ar/README.md) ·
[الدارجة](../ar-MA/README.md) ·
[المصرية](../arz/README.md) ·
[Deutsch](../de/README.md)

Kotoba téh basa jeung tumpukan komputasi anu intuitif, deklaratif, jeung
security-first pikeun agén AI — ogé pikeun manusa anu vibe-code jeung
maranéhna.

**Parangkat lunak anu aya nambahan kaamanan di sabudeureun program.
Kotoba ngajadikeun kaamanan sipat sakabéh komputasi.**

Kaca GitHub ieu mantuan réngsé **hiji pagawéan**: masang CLI jeung
ngompilasi program anu geus dipariksa. [README](../../../README.md)
Inggris tetep tinjauan proyék. Kontrak normatif tetep berkas Inggris
anu bisa dibaca mesin.

> **Catetan tarjamahan.** Tarjamahan mesin. Tinjauan panyatur asli:
> **can diverifikasi**. Inggris téh sumber.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` teu boga URL
> Release basa. Tag CLI di
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) téh
> binding implementasi, lain gerbang éta.

[kotoba-lang.org](https://kotoba-lang.org) · implementasi jeung CLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[Mimitian](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

Éksprési éta nuturkeun jalur kompilasi anu sarua jeung berkas. Éta
kanyamanan compile-and-run, lain `eval` runtime tanpa wates.

## Hiji pagawéan

1. Pasang CLI (Homebrew atawa pamasang anu mariksa checksum).
2. `kotoba selfhost check --json` — nampa ukur réspon `valid` kalawan
   daptar masalah kosong.
3. Kompilasi `hello.kotoba` sarta nampa ukur réspon `emitted`.

Léngkah lengkep: [Mimitian make Kotoba](getting-started.md).

## Ieu lain

- Deploy mayar anu di-host **can** hirup. `kotoba deploy` poé ieu lain
  analog Deno Deploy anu bisa dibeuli.
- Kotoba wangunna Clojure, lain jangji yén Clojure JVM atawa ClojureScript
  sawenang-wenang jalan tanpa parobahan.
- Kaca ieu teu nyieun angka GMV, traksi palanggan, atawa percepatan
  universal.

Kartu mimiti situs anu geus diterbitkeun (teu dirobah):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).

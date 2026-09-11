# Mimitian make Kotoba

**Basa:** [indeks](../README.md) ·
[English](../../getting-started.md) ·
[Bahasa Indonesia](../id/getting-started.md) ·
[Basa Jawa](../jv/getting-started.md) ·
**Basa Sunda** ·
[Español](../es/getting-started.md) ·
[עברית](../he/getting-started.md) ·
[한국어](../ko/getting-started.md) ·
[Italiano](../it/getting-started.md) ·
[العربية](../ar/getting-started.md) ·
[الدارجة](../ar-MA/getting-started.md) ·
[المصرية](../arz/getting-started.md) ·
[Deutsch](../de/getting-started.md)

> **Catetan tarjamahan.** Tarjamahan mesin tina pituduh Inggris
> [getting started](../../getting-started.md). Tinjauan panyatur asli:
> **can diverifikasi**. Inggris tetep sumber. Kode, paréntah, URL, jeung
> pangenal teu dirobah.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` teu boga URL
> Release basa. Tag CLI di `kotoba-lang/kotoba` lain gerbang éta.

Jalur ieu nungtun tina mesin kosong ka program anu geus dipariksa.
Pamasangan jeung rilis binér milik
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba), lain
repositori kontrak basa ieu.

## 1. Pasang CLI native

Di macOS atawa Linux make Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 nampik muat formula tina tap anu can dititah percantenan, jadi
tanpa baris tengah `brew install` eureun ku
`Refusing to load formula … from untrusted tap`.

Atawa make pamasang anu mariksa checksum ti repositori implementasi:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

Kaca rilis téh otoritas pikeun platform jeung artefak anu sadia:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. Pariksa pamasangan

```sh
kotoba selfhost check --json
```

Nampa ukur réspon `valid` kalawan daptar masalah kosong. Masang wungkul
teu ngabuktikeun yén sumber kompilasi atawa jalan.

## 3. Jieun berkas sumber

Jieun `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

Kompilasi pikeun WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

Nampa ukur réspon `emitted`. Pikeun pamariksaan éksekusi anu teu gumantung
host, turutan
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
anu diterbitkeun (Inggris): muat modul, tolak import anu teu disangka,
jeung telepon `main` anu diékspor. Paréntah CLI milik adapter bisa
mulangkeun `planned` / `adapter-required`; éta rencana, lain bukti
éksekusi.

Kode anyar husus Kotoba make `.kotoba`. Sumber kulawarga Clojure anu
dibagi make `.cljc` sarta milih kabiasaan Kotoba ku `#?(:kotoba …)`.

## 4. Jieun épék jadi eksplisit

Sumber Kotoba teu boga otoritas ambien ka sistem berkas, jaringan,
rahasia, jam, atawa prosés. Komponén nyatakeun import; kawijakan méré
subset kawates; runtime ngan ngabeungkeut provider anu dipasihkeun.
Kawijakan kosong nampik unggal épék host, kaasup `:host/http`. Éta
produk pikeun kode anu teu dipercanten: otoritas anu teu dipasihkeun
teu jalan.

Deploy mayar anu di-host tina grant éta can produk publik. Ulah anggap
`kotoba deploy` analog Deno Deploy anu bisa dibeuli poé ieu.

Mimitian tina [capability values](../../lang/capability-values.md)
(Inggris) saméméh nulis kode mibanda épék.

## 5. Wanoh wates kompatibilitas

Kotoba wangunna Clojure, lain jangji yén Clojure JVM atawa ClojureScript
sawenang-wenang jalan tanpa parobahan. Pariksa klasifikasi ayeuna saméméh
ngandelkeun hiji wangun:

- tinjauan manusa: [language surface matrix](../../lang/surface-matrix.md)
- otoritas mesin: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- tata basa anu ditarima: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

Teruskeun [language reference](../../reference/language.md) jeung
[tooling reference](../../reference/tooling.md) (duanana Inggris).

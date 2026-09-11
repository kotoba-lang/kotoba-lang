# Miwiti nganggo Kotoba

**Basa:** [indheks](../README.md) ·
[English](../../getting-started.md) ·
[Bahasa Indonesia](../id/getting-started.md) ·
**Basa Jawa** ·
[Basa Sunda](../su/getting-started.md) ·
[Español](../es/getting-started.md) ·
[עברית](../he/getting-started.md) ·
[한국어](../ko/getting-started.md) ·
[Italiano](../it/getting-started.md) ·
[العربية](../ar/getting-started.md) ·
[الدارجة](../ar-MA/getting-started.md) ·
[المصرية](../arz/getting-started.md) ·
[Deutsch](../de/getting-started.md)

> **Cathetan terjemahan.** Terjemahan mesin saka pandhuan Inggris
> [getting started](../../getting-started.md). Tinjauan penutur asli:
> **durung diverifikasi**. Inggris tetep sumber. Kode, prentah, URL, lan
> pengenal ora diowahi.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` ora duwé URL
> Release basa. Tag CLI ing `kotoba-lang/kotoba` dudu gapura iku.

Dalan iki nuntun saka mesin kosong menyang program sing wis dipriksa.
Instalasi lan rilis biner dadi kagungané
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba), dudu
repositori kontrak basa iki.

## 1. Instal CLI native

Ing macOS utawa Linux nganggo Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 nolak muat formula saka tap sing durung dikongkon dipercaya,
mula tanpa baris tengah `brew install` mandheg kanthi
`Refusing to load formula … from untrusted tap`.

Utawa nganggo penginstal sing mverifikasi checksum saka repositori
implementasi:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

Kaca rilis iku otoritas kanggo platform lan artefak sing kasedhiya:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. Priksa instalasi

```sh
kotoba selfhost check --json
```

Mung nampa wangsulan `valid` kanthi dhaptar masalah kosong. Instalasi
waé ora mbuktekaké yèn sumber kompilasi utawa mlaku.

## 3. Gawe berkas sumber

Gawe `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

Kompilasi kanggo WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

Mung nampa wangsulan `emitted`. Kanggo pamariksaan eksekusi sing ora
gumantung host, tutna
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
sing diterbitaké (Inggris): muat modul, tolak import sing ora dikarepaké,
lan undhang `main` sing diekspor. Prentah CLI kagungané adapter bisa
mbalekake `planned` / `adapter-required`; iku rencana, dudu bukti
eksekusi.

Kode anyar mung-Kotoba nganggo `.kotoba`. Sumber kulawarga Clojure sing
dibagi nganggo `.cljc` lan milih prilaku Kotoba nganggo `#?(:kotoba …)`.

## 4. Gawe efek dadi eksplisit

Sumber Kotoba ora duwé otoritas ambien menyang sistem berkas, jaringan,
rahasia, jam, utawa proses. Komponen nyatakake import; kawicaksanan
mènèhi subset winates; runtime mung ngiket provider sing pinaringan.
Kawicaksanan kosong nolak saben efek host, kalebu `:host/http`. Iku
produk kanggo kode sing ora dipercaya: otoritas sing ora diparingi ora
mlaku.

Deploy mbayar sing di-host saka grant iku durung produk publik. Aja
anggep `kotoba deploy` analog Deno Deploy sing bisa dituku dina iki.

Wiwiti saka [capability values](../../lang/capability-values.md) (Inggris)
sadurunge nulis kode mawa efek.

## 5. Kenali wates kompatibilitas

Kotoba wujude Clojure, dudu janji yèn Clojure JVM utawa ClojureScript
sembarang mlaku tanpa owah-owahan. Priksa klasifikasi saiki sadurunge
ngandelake sawijining wujud:

- ringkesan manungsa: [language surface matrix](../../lang/surface-matrix.md)
- otoritas mesin: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- tata basa sing ditampa: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

Terusake [language reference](../../reference/language.md) lan
[tooling reference](../../reference/tooling.md) (loro-lorone Inggris).

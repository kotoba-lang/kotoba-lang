# Kotoba

> **L’IA scrive liberamente. Kotoba traccia il confine.**

**Lingue:** [indice](../README.md) ·
[English](../../../README.md) ·
[Bahasa Indonesia](../id/README.md) ·
[Basa Jawa](../jv/README.md) ·
[Basa Sunda](../su/README.md) ·
[Español](../es/README.md) ·
[עברית](../he/README.md) ·
[한국어](../ko/README.md) ·
**Italiano** ·
[العربية](../ar/README.md) ·
[الدارجة](../ar-MA/README.md) ·
[المصرية](../arz/README.md) ·
[Deutsch](../de/README.md)

Kotoba è un linguaggio e uno stack di calcolo intuitivi, dichiarativi e
security-first per agenti IA — e per umani che vibe-codano con loro.

**Il software esistente aggiunge sicurezza intorno al programma. Kotoba
rende la sicurezza una proprietà dell’intera computazione.**

Questa pagina GitHub aiuta a finire **un lavoro**: installare la CLI e
compilare un programma ammesso. Il [README](../../../README.md) inglese
resta la panoramica. I contratti normativi restano file inglesi
leggibili dalla macchina.

> **Avviso di traduzione.** Traduzione automatica. Revisione di parlante
> nativo: **non verificata**. L’inglese è la fonte.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` non ha URL di
> Release del linguaggio. I tag CLI su
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) sono un
> binding di implementazione, non quel cancello.

[kotoba-lang.org](https://kotoba-lang.org) · implementazione e CLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[Per iniziare](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

Quell’espressione segue lo stesso percorso di compilazione ammesso di un
file. È comodità compile-and-run, non `eval` runtime senza limiti.

## Il lavoro

1. Installare la CLI (Homebrew o l’installer con checksum).
2. `kotoba selfhost check --json` — accettare solo una risposta `valid`
   con lista problemi vuota.
3. Compilare `hello.kotoba` e accettare solo una risposta `emitted`.

Passi completi: [Per iniziare](getting-started.md).

## Cosa non è

- Il deploy ospitato a pagamento **non** è live. `kotoba deploy` non è
  oggi un analogo acquistabile di Deno Deploy.
- Kotoba ha forma Clojure; non promette che Clojure JVM o ClojureScript
  arbitrari girino invariati.
- Questa pagina non inventa GMV, trazione clienti o speedup universali.

Schede di avvio del sito già pubblicate (invariate):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).

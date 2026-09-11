# Kotoba

> **KI schreibt frei. Kotoba zieht die Grenze.**

**Sprachen:** [Übersicht](../README.md) ·
[English](../../../README.md) ·
[Bahasa Indonesia](../id/README.md) ·
[Basa Jawa](../jv/README.md) ·
[Basa Sunda](../su/README.md) ·
[Español](../es/README.md) ·
[עברית](../he/README.md) ·
[한국어](../ko/README.md) ·
[Italiano](../it/README.md) ·
[العربية](../ar/README.md) ·
[الدارجة](../ar-MA/README.md) ·
[المصرية](../arz/README.md) ·
**Deutsch**

Kotoba ist eine intuitive, deklarative, security-first Sprache und ein
Rechenstack für KI-Agenten — und für Menschen, die mit ihnen vibe-coden.

**Bestehende Software legt Sicherheit um das Programm. Kotoba macht Sicherheit
zu einer Eigenschaft der gesamten Berechnung.**

Diese GitHub-Seite hilft bei **einem Auftrag**: CLI installieren und ein
geprüftes Programm kompilieren. Die englische
[README](../../../README.md) bleibt die vollständige Projektübersicht.
Normative Verträge bleiben englische, maschinenlesbare Dateien.

> **Übersetzungshinweis.** Maschinenübersetzung. Muttersprachliche Prüfung:
> **nicht verifiziert**. Englisch ist die Quelle.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` hat keine
> Language-Release-URL. CLI-Tags auf
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) sind eine
> Implementierungsbindung, nicht dieses Tor.

[kotoba-lang.org](https://kotoba-lang.org) · Implementierung und CLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[Erste Schritte](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

Dieser Ausdruck folgt demselben zugelassenen Kompilierungspfad wie eine Datei.
Das ist Compile-und-Ausführen-Komfort, kein uneingeschränktes Runtime-`eval`.

## Der eine Auftrag

1. CLI installieren (Homebrew oder den Prüfsummen-Installer).
2. `kotoba selfhost check --json` — nur eine `valid`-Antwort mit leerer
   Problemliste akzeptieren.
3. `hello.kotoba` kompilieren und nur eine `emitted`-Antwort akzeptieren.

Vollständige Schritte: [Erste Schritte](getting-started.md).

## Was das nicht ist

- Gehostetes, abrechenbares Deploy ist **nicht** live. `kotoba deploy` ist
  heute kein kaufbares Deno-Deploy-Analog.
- Kotoba ist Clojure-förmig, kein Versprechen, dass beliebiges JVM-Clojure
  oder ClojureScript unverändert läuft.
- Diese Seite erfindet keine GMV-, Traktions- oder universellen
  Geschwindigkeitszahlen.

Bereits veröffentlichte Site-Startkarten (unverändert):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).

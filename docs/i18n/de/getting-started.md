# Erste Schritte mit Kotoba

**Sprachen:** [Übersicht](../README.md) ·
[English](../../getting-started.md) ·
[Bahasa Indonesia](../id/getting-started.md) ·
[Basa Jawa](../jv/getting-started.md) ·
[Basa Sunda](../su/getting-started.md) ·
[Español](../es/getting-started.md) ·
[עברית](../he/getting-started.md) ·
[한국어](../ko/getting-started.md) ·
[Italiano](../it/getting-started.md) ·
[العربية](../ar/getting-started.md) ·
[الدارجة](../ar-MA/getting-started.md) ·
[المصرية](../arz/getting-started.md) ·
**Deutsch**

> **Übersetzungshinweis.** Maschinenübersetzung der englischen
> [Getting-started-Anleitung](../../getting-started.md). Muttersprachliche
> Prüfung: **nicht verifiziert**. Englisch bleibt die Quelle. Code, Befehle,
> URLs und Bezeichner sind unverändert.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` hat keine
> Language-Release-URL. CLI-Tags auf `kotoba-lang/kotoba` sind nicht dieses Tor.

Dieser Pfad führt von einer leeren Maschine zu einem geprüften Programm.
Installation und Binär-Releases gehören
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba), nicht diesem
Sprachvertrags-Repository.

## 1. Native CLI installieren

Unter macOS oder Linux mit Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 lädt eine Formula aus einem Tap nicht, dem es nicht vertrauen
soll. Ohne die mittlere Zeile stoppt `brew install` mit
`Refusing to load formula … from untrusted tap`.

Alternativ den Prüfsummen-Installer aus dem Implementierungs-Repository:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

Die Release-Seite ist die Autorität für verfügbare Plattformen und Artefakte:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. Installation prüfen

```sh
kotoba selfhost check --json
```

Nur eine `valid`-Antwort mit leerer Problemliste akzeptieren. Installation
allein beweist nicht, dass eine Quelle kompiliert oder gelaufen ist.

## 3. Eine Quelldatei bauen

`hello.kotoba` anlegen:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

Für WebAssembly kompilieren:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

Nur eine `emitted`-Antwort akzeptieren. Für eine host-unabhängige
Ausführungsprüfung folgt dem veröffentlichten
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
(Englisch): Modul laden, unerwartete Imports ablehnen, exportiertes `main`
aufrufen. Ein adapter-eigener CLI-Befehl darf `planned` /
`adapter-required` zurückgeben; das ist ein Plan, kein Ausführungsbeweis.

Neuer Kotoba-only-Code verwendet `.kotoba`. Geteilte Clojure-Familie-Quelle
verwendet `.cljc` und wählt Kotoba-Verhalten mit `#?(:kotoba …)`.

## 4. Effekte ausdrücklich machen

Kotoba-Quelle hat keine ambiente Dateisystem-, Netz-, Secret-, Uhr- oder
Prozess-Autorität. Eine Komponente deklariert Imports; Policy gewährt eine
begrenzte Teilmenge; die Runtime bindet nur gewährte Provider. Eine leere
Policy verweigert jeden Host-Effekt, einschließlich `:host/http`. Das ist
das Produkt für nicht vertrauenswürdigen Code: nicht gewährte Autorität
läuft nicht.

Gehostetes, abrechenbares Deploy dieser Grants ist noch kein öffentliches
Produkt. `kotoba deploy` ist heute kein kaufbares Deno-Deploy-Analog.

Zuerst [Capability values](../../lang/capability-values.md) (Englisch)
lesen, bevor effektvoller Code geschrieben wird.

## 5. Die Kompatibilitätsgrenze kennen

Kotoba ist Clojure-förmig, kein Versprechen, dass beliebiges JVM-Clojure
oder ClojureScript unverändert läuft. Vor dem Verlassen auf eine Form die
aktuelle Klassifikation prüfen:

- menschliche Übersicht: [language surface matrix](../../lang/surface-matrix.md)
- Maschinenautorität: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- zugelassene Grammatik: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

Weiter mit der [Language reference](../../reference/language.md) und der
[Tooling reference](../../reference/tooling.md) (beide Englisch).

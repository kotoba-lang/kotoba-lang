# Per iniziare con Kotoba

**Lingue:** [indice](../README.md) ·
[English](../../getting-started.md) ·
[Bahasa Indonesia](../id/getting-started.md) ·
[Basa Jawa](../jv/getting-started.md) ·
[Basa Sunda](../su/getting-started.md) ·
[Español](../es/getting-started.md) ·
[עברית](../he/getting-started.md) ·
[한국어](../ko/getting-started.md) ·
**Italiano** ·
[العربية](../ar/getting-started.md) ·
[الدارجة](../ar-MA/getting-started.md) ·
[المصرية](../arz/getting-started.md) ·
[Deutsch](../de/getting-started.md)

> **Avviso di traduzione.** Traduzione automatica della guida inglese
> [getting started](../../getting-started.md). Revisione di parlante
> nativo: **non verificata**. L’inglese resta la fonte. Codice, comandi,
> URL e identificatori sono invariati.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` non ha URL di
> Release del linguaggio. I tag CLI su `kotoba-lang/kotoba` non sono quel
> cancello.

Questo percorso porta da una macchina vuota a un programma controllato.
Installazione e release binarie appartengono a
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba), non a questo
repository di contratto del linguaggio.

## 1. Installare la CLI nativa

Su macOS o Linux con Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 rifiuta di caricare una formula da un tap a cui non è stato
detto di fidarsi, quindi senza la riga di mezzo `brew install` si ferma
con `Refusing to load formula … from untrusted tap`.

In alternativa, l’installer che verifica il checksum pubblicato dal
repository di implementazione:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

La pagina delle release è l’autorità per piattaforme e artefatti
disponibili:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. Controllare l’installazione

```sh
kotoba selfhost check --json
```

Accettare solo una risposta `valid` con lista problemi vuota.
Installare non prova che un sorgente sia stato compilato o eseguito.

## 3. Costruire un file sorgente

Creare `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

Compilarlo per WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

Accettare solo una risposta `emitted`. Per un controllo di esecuzione
indipendente dall’host, seguire il
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
pubblicato (inglese): caricare il modulo, rifiutare import inattesi e
chiamare `main` esportato. Un comando CLI dell’adapter può restituire
`planned` / `adapter-required`; è un piano, non evidenza di esecuzione.

Il codice nuovo solo-Kotoba usa `.kotoba`. Il sorgente condiviso della
famiglia Clojure usa `.cljc` e seleziona il comportamento Kotoba con
`#?(:kotoba …)`.

## 4. Rendere gli effetti espliciti

Il sorgente Kotoba non ha autorità ambientale su filesystem, rete,
segreto, orologio o processo. Un componente dichiara gli import; la
policy concede un sottoinsieme delimitato; il runtime collega solo i
provider concessi. Una policy vuota nega ogni effetto host, incluso
`:host/http`. Questo è il prodotto per codice non fidato: l’autorità non
concessa non gira.

Il deploy ospitato a pagamento di quelle concessioni non è ancora un
prodotto pubblico. Non trattare `kotoba deploy` come un analogo di Deno
Deploy acquistabile oggi.

Iniziare da [capability values](../../lang/capability-values.md) (inglese)
prima di scrivere codice con effetti.

## 5. Conoscere il confine di compatibilità

Kotoba ha forma Clojure; non promette che Clojure JVM o ClojureScript
arbitrari girino invariati. Prima di affidarsi a una forma, controllare
la classificazione attuale:

- panoramica umana: [language surface matrix](../../lang/surface-matrix.md)
- autorità macchina: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- grammatica ammessa: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

Continuare con la [language reference](../../reference/language.md) e la
[tooling reference](../../reference/tooling.md) (entrambe in inglese).

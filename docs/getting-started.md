# Getting started with Kotoba

This path gets from an empty machine to a checked program. Installation and
binary releases are owned by [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba),
not by this language-contract repository.

## 1. Install the native CLI

On macOS or Linux with Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 refuses to load a formula from a tap it has not been told to trust,
so without the middle line `brew install` stops with `Refusing to load formula
… from untrusted tap`.

Alternatively, use the checksum-verifying installer published by the
implementation repository:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

The release page is the authority for available platforms and artifacts:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. Check the installation

```sh
kotoba selfhost check --json
kotoba -e '(+ 1 2)'
```

`-e` is compile-and-run sugar. It does not enable runtime `eval`.

## 3. Build a source file

Create `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

Compile it for WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

New Kotoba-only code uses `.kotoba`. Shared Clojure-family source uses `.cljc`
and selects Kotoba-specific behavior with a `#?(:kotoba …)` reader branch.

## 4. Make effects explicit

Kotoba source has no ambient filesystem, network, secret, clock, or process
authority. A component declares imports; policy grants a bounded subset; the
runtime links only the granted providers. An empty policy denies every host
effect, including `:host/http`. That is the product for untrusted code:
ungranted authority does not run.

Hosted billed deploy of those grants is not a public product yet. Do not treat
`kotoba deploy` as a Deno Deploy analog you can buy today.

Start with [capability values](lang/capability-values.md) before writing
effectful code.

## 5. Know the compatibility boundary

Kotoba is Clojure-shaped, not a promise that arbitrary JVM Clojure or
ClojureScript runs unchanged. Check the current classification before relying
on a form:

- human overview: [language surface matrix](lang/surface-matrix.md)
- machine authority: [`lang/surface-status.edn`](../lang/surface-status.edn)
- admitted grammar: [`lang/guest-grammar.edn`](../lang/guest-grammar.edn)

Continue with the [language reference](reference/language.md) and
[tooling reference](reference/tooling.md).

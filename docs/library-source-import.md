# Bundled library source imports

`kotoba library import kotoba.coll kotoba.spec kotoba.json --json` resolves
bundled source IDs. The installable `kotoba` checkout owns `lib/catalog.edn`;
this repository owns the public shape and resolver in
`lang/library-import.edn` and `kotoba.lang.library-import`.

The three project IDs are `kotoba.coll`, `kotoba.spec`, and `kotoba.json`.
They claim no DNS or ATProto namespace. Every legacy alias is declared on
exactly one catalog package. Resolution is exact and case-sensitive; aliases
are one hop, and a collision rejects the entire catalog. Unknown names fail
before any source is executed. Import discovery reads local metadata and does
not create a signed release, modify a lock, or fetch a public catalog.

A source program uses ordinary namespace imports:

```clojure
(ns example
  (:require [kotoba.coll :as coll]
            [kotoba.spec :as spec]
            [kotoba.json :as json]))

(defn main []
  [(coll/map-vals inc {:a 1})
   (spec/valid? {:type :int} 42)
   (json/encode {:ok true})])
```

Run it with the source compatibility host:

```sh
kotoba run example.cljk --runtime sci --library kotoba.coll --library kotoba.spec --library kotoba.json --json
```

The `--library` values may instead be `kotoba-lang/coll`,
`io.github.kotoba-lang/spec`, or `kotoba.lang.json` (or the other explicitly
listed aliases). A package ID selects source; it does not rename a namespace.
The retained `kotoba.lang.coll`, `kotoba.lang.spec`, and `kotoba.lang.json`
source APIs remain available too. Duplicate aliases of one ID select one copy.

SCI reads the CLJS branch of `.cljk`/`.cljc` sources and invokes the declared
namespace's `main` function, or `--function`, with repeated EDN `--arg` values.
Lisp entry names such as `+`, `-`, `/`, `valid?`, `value'` and Unicode names
are accepted; quote shell-sensitive names when passing them to the CLI. Prior
qualified selector spellings remain compatible. Naming is defined by the
[Kotoba naming Spec](lang/naming.md), and changes no execution authority.
`--source-path`/`-S` adds explicit project roots. Bundled source roots take
precedence, and an import path escaping the installed source tree is refused.

The runtime must be explicitly selected: `--library` with ordinary checked
Kotoba execution refuses instead of silently switching hosts. SCI is a local
script compatibility host with host authority; it does not apply Kotoba
capability policies or establish Q9/native qualification. `.kotoba`, policy,
and signed-package options are refused on this route. Checked Kotoba and the
signed `library inspect/publish` and `package add/run` workflows retain their
existing contracts. Signed subtree publication/admission remains a separate
release gate.

SCI consumers may depend on the same immutable language revision with
`:deps/root "lib/library-import"`. This portable profile uses the exact
authoritative resolver source and its text dependency, without adding the
full authority's compiler/JVM graph to a script host.

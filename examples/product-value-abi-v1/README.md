# Product Value ABI v1 — pure-product golden

Living contract for **pure-product** surface (option / if-some / string-from-i64).

| File | Role |
|---|---|
| `claim_sub.kotoba` | Golden source |

## Run (compiler CI is authoritative)

```clojure
(require '[kotoba.compiler.core :as c]
         '[kotoba.kir :as kir])
(def src (slurp "examples/product-value-abi-v1/claim_sub.kotoba"))
(def r (c/compile-source src :wasm32-kotoba-v1 {}
                         {:language-profile :pure-product}))
(kir/execute (:kir r) 'claim-sub [[[:option :string] false]])
;; => "anonymous"
```

Compiler tests: `kotoba.compiler.pure-product-profile-test`,
`kotoba.compiler.product-value-abi-v1-test`.

Semantics: `docs/lang/semantics-ssot.md`. Profile: `lang/pure-product-profile.edn`.

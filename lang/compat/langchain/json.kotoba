(ns langchain.json
  "In-language JSON encode/decode for langchain, backed by kotoba-lang/json.

  Before this namespace, langchain punted JSON parsing to the host: parser.cljc's
  `json-parser` took a host-injected `json-read` fn, and kotoba_db required a
  `:json-read`/`:json-write` in its injected io map — because the WASM premise
  forbade assuming a JSON parser was available. Now that kotoba-lang/json exists
  (pure Clojure, runs on kotoba-WASM), langchain has an in-language default;
  the host-injected seams remain as overrides for hosts that want to substitute
  a faster/native parser.

  This makes langchain a consumer of kotoba-lang/json — a real vertical lib
  depending on a foundational-stdlib lib (M5 for json, beyond http)."
  (:require [kotoba.lang.json :as json]))

(defn encode
  "Clojure data → JSON string (deterministic key order)."
  [data] (json/encode data))

(defn decode
  "JSON string → Clojure data (string keys; `null` → `nil`)."
  [^String s] (json/decode s))

;; the host-injected-seam defaults: a host may still override with a faster
;; native parser, but the out-of-the-box io map no longer requires one.
(def default-json-read  decode)
(def default-json-write encode)

(ns kotoba.lang.code-identity
  "Canonical identity for typed Kotoba definitions (CI1).

  This is deliberately not a source, package, or Wasm hash.  It identifies a
  closed pure definition's canonical typed KIR and direct definition identity
  closure.  Authority is still supplied exclusively by package policy and the
  capability runtime.

  ## What the identity seals

  `lang/code-identity.edn` names the canonical input:

      [:typed-kir :profile-version :desugar-contract-version
       :effect-row :interface :direct-definition-dependencies]

  All six participate.  Two of them are the reason this namespace exists at
  version 2:

  - **`:definition/effect-row`** is the authority the definition requires.  A
    payload that omits it gives two definitions with *different* effect rows
    the same identity, so a lock pinning the pure one would admit the
    effectful one.  The effect row is part of the semantic contract, not
    metadata about it.
  - **`:definition/desugar-contract-version`** is sealed because the ADR
    requires that \"a definition CID must never claim semantic equivalence
    across profile, type-rule, or canonical-KIR-version changes\".  Desugaring
    is what produces the KIR being hashed; if its contract moves, identical
    KIR no longer means identical meaning.

  ## Why the bytes are real DAG-CBOR

  Version 1 hashed `(pr-str canonical-edn)` and labelled the CID `dag-cbor`.
  Both halves were wrong.  The block did not decode as dag-cbor, and `pr-str`
  is not byte-identical across Clojure and ClojureScript, so the same
  definition could hash differently per implementation — which CI1's admission
  rule (\"byte-for-byte deterministic identity\") and CI6's cross-implementation
  conformance both forbid.

  Version 2 normalized to a closed, injective, tagged form and encoded it with
  a deterministic CBOR encoder.  Every value carries its type tag, so a
  keyword can never collide with the string of the same name.  Numbers are
  carried as exact decimal/hex *text* rather than CBOR integers because a
  64-bit KIR literal exceeds the JavaScript exact-integer range: text keeps
  `:clj` and `:cljs` on the same bytes.

  Version 3 preserves that semantic form but promotes dependency CIDs from
  tagged strings to real IPLD Links (DAG-CBOR tag 42). Generic DAG traversal
  can now discover the definition closure without knowing Kotoba's schema.

  Identity payloads are versioned; v3 CIDs are deliberately not v1/v2 CIDs."
  (:require [clojure.string :as str]
            [ipld.core :as ipld]
            [multiformats.core :as mf]))

(def payload-version
  "Bumped whenever the sealed inputs or the canonical encoding change. Old
  identities are not claimed to be equal to new ones — that is the point."
  3)

(def definition-required
  [:definition/profile-version
   :definition/desugar-contract-version
   :definition/kir
   :definition/effect-row
   :definition/interface
   :definition/dependencies])

(defn cid?
  "True for a structurally decodable CIDv1.  Definition identity always uses
  CIDv1, so older opaque identifiers cannot enter its closure."
  [x]
  (and (string? x)
       (not (str/blank? x))
       (str/starts-with? x "b")
       (try
         (let [bs (mf/cid->bytes x)]
           ;; CIDv1's first varint is encoded as the one byte 0x01.
           (= 1 (bit-and (nth bs 0) 0xff)))
         (catch #?(:clj Exception :cljs :default) _ false))))

;; ---------------------------------------------------------------------------
;; canonical normalization — a closed, injective, tagged value domain
;; ---------------------------------------------------------------------------

(def ^:private hex-digits "0123456789abcdef")

(defn- byte->hex [b]
  (let [v (bit-and b 0xff)]
    (str (nth hex-digits (bit-shift-right v 4))
         (nth hex-digits (bit-and v 0x0f)))))

(defn- bytes->hex [bs]
  (apply str (map byte->hex (seq bs))))

(defn- f64-bits-hex
  "IEEE-754 bits of a double as 16 lowercase hex characters. Hex text rather
  than a CBOR integer: the bit pattern routinely exceeds JavaScript's exact
  integer range, and an inexact identity is not an identity."
  [d]
  #?(:clj
     (let [bits (Double/doubleToLongBits ^double d)]
       (apply str (map (fn [shift] (byte->hex (bit-and (bit-shift-right bits shift) 0xff)))
                       [56 48 40 32 24 16 8 0])))
     :cljs
     (let [buf (js/ArrayBuffer. 8)
           view (js/DataView. buf)]
       (.setFloat64 view 0 d false)
       (apply str (map (fn [i] (byte->hex (.getUint8 view i))) (range 8))))))

(defn- stable-name [x]
  (if-let [n (namespace x)] (str n "/" (name x)) (name x)))

(defn- byte-array? [x]
  #?(:clj (bytes? x)
     :cljs (instance? js/Uint8Array x)))

(def ^:private max-exact-integer
  "2^53 - 1: the largest integer a ClojureScript number represents exactly."
  9007199254740991)

(defn f64
  "The admitted representation of an f64 literal inside typed KIR: a map
  carrying the exact IEEE-754 bits as 16 hex characters. Use this rather than
  a platform float — see `normalize`."
  [d]
  {::f64 (f64-bits-hex d)})

(defn i64
  "The admitted representation of an i64 literal outside ±(2^53-1): a map
  carrying the exact value as decimal text.

  A plain integer literal is fine inside that range and hashes identically on
  both implementations. Beyond it, a ClojureScript reader silently rounds —
  9007199254740993 is read back as 9007199254740992 — so the value is already
  wrong before any encoder sees it. Encoding integers as text does not help if
  the text was produced from a corrupted number, so the large case must be
  carried explicitly instead.

  `(i64 5)` and `5` denote the same value and therefore share one identity."
  [n]
  {::i64 (str n)})

(defn- f64-literal? [x]
  (and (map? x)
       (= 1 (count x))
       (string? (::f64 x))
       (= 16 (count (::f64 x)))
       (every? (fn [c] (str/includes? "0123456789abcdefABCDEF" (str c))) (::f64 x))))

(defn- i64-literal? [x]
  (and (map? x)
       (= 1 (count x))
       (string? (::i64 x))
       (re-matches #"-?(0|[1-9][0-9]*)" (::i64 x))))

(declare normalize)

(defn- normalize-members [coll]
  (mapv normalize coll))

;; A total order over the normalized domain, so sets and map keys have one
;; canonical sequence on every implementation. Deliberately not `compare` over
;; the source values: Clojure cannot compare a keyword with a string, and
;; `pr-str` ordering differs by platform.
(defn- rank [x]
  (cond (nil? x) 0 (boolean? x) 1 (string? x) 2 (vector? x) 3 :else 4))

(defn- cmp [a b]
  (let [ra (rank a) rb (rank b)]
    (if (not= ra rb)
      (compare ra rb)
      (cond
        (nil? a) 0
        (boolean? a) (compare a b)
        (string? a) (compare a b)
        (vector? a) (let [n (min (count a) (count b))]
                      (loop [i 0]
                        (if (= i n)
                          (compare (count a) (count b))
                          (let [c (cmp (nth a i) (nth b i))]
                            (if (zero? c) (recur (inc i)) c)))))
        :else 0))))

(defn normalize
  "EDN value -> closed tagged form. Every branch is explicit and anything
  outside the admitted domain throws rather than being coerced: an identity
  function that silently accepts an unknown type is an identity function that
  can be made to collide."
  [value]
  (cond
    (nil? value)       ["nil"]
    (boolean? value)   ["bool" value]

    ;; Exact-range integers hash identically on both implementations. Beyond
    ;; ±(2^53-1) a ClojureScript reader has already rounded the literal, so the
    ;; explicit `i64` form is the only representation that can be trusted --
    ;; refusing here is what makes that failure loud instead of silent.
    (integer? value)
    (if (<= (- max-exact-integer) value max-exact-integer)
      ["int" (str value)]
      (throw (ex-info "integer outside the exactly-representable range; carry it as (kotoba.lang.code-identity/i64 n)"
                      {:problem :definition/inexact-integer})))

    (string? value)    ["str" value]
    (keyword? value)   ["kw" (stable-name value)]
    (symbol? value)    ["sym" (stable-name value)]
    (byte-array? value) ["bytes" (bytes->hex value)]

    ;; An f64 literal is carried as its exact IEEE-754 bits, never as a
    ;; platform float. JavaScript has a single number type, so `2.0` is
    ;; `integer?` there and a `double` here: hashing raw platform floats would
    ;; make the same KIR encode differently per implementation. The explicit
    ;; form is the only admitted representation, and `f64-bits-hex` exists so
    ;; a frontend can produce it.
    (f64-literal? value) ["f64" (str/lower-case (::f64 value))]

    ;; Same tag as a plain integer: `(i64 5)` and `5` denote one value, so they
    ;; must share one identity. The wrapper is about how the value survives a
    ;; reader, not about what it means.
    (i64-literal? value) ["int" (::i64 value)]

    (number? value)
    (throw (ex-info "raw platform float is outside the canonical identity domain; carry f64 as {:kotoba.lang.code-identity/f64 \"<16 hex>\"}"
                    {:problem :definition/unencodable-float}))

    (map? value)
    ["map" (vec (sort-by first cmp
                         (mapv (fn [[k v]] [(normalize k) (normalize v)]) value)))]

    (set? value)
    ["set" (vec (sort cmp (normalize-members value)))]

    (vector? value) ["vec" (normalize-members value)]
    (or (list? value) (seq? value)) ["list" (normalize-members value)]

    :else
    (throw (ex-info "value outside the canonical identity domain"
                    {:problem :definition/uncanonical-value
                     :type (str (type value))}))))

;; ---------------------------------------------------------------------------
;; definition shape
;; ---------------------------------------------------------------------------

(defn- effect-row-problem [row]
  (cond
    (not (set? row))
    {:valid? false :message "definition effect row must be a set"}
    (not (every? keyword? row))
    {:valid? false :message "definition effect row members must be keywords"}))

(defn definition-error
  "Checks the closed pure-definition shape before identity is calculated.
  `:definition/name` is deliberately ignored: it is an author-facing alias,
  never semantic identity."
  [definition]
  (or
   (when-not (map? definition)
     {:valid? false :message "definition map required"})
   (some (fn [k]
           (when-not (contains? definition k)
             {:valid? false :message "definition missing required field" :data {:missing k}}))
         definition-required)
   (when-not (pos-int? (:definition/profile-version definition))
     {:valid? false :message "definition profile version must be positive integer"})
   (when-not (pos-int? (:definition/desugar-contract-version definition))
     {:valid? false :message "definition desugar contract version must be positive integer"})
   (when-not (map? (:definition/kir definition))
     {:valid? false :message "definition typed KIR map required"})
   (effect-row-problem (:definition/effect-row definition))
   (when-not (map? (:definition/interface definition))
     {:valid? false :message "definition interface map required"})
   (when-not (vector? (:definition/dependencies definition))
     {:valid? false :message "definition dependencies vector required"})
   (when-not (every? cid? (:definition/dependencies definition))
     {:valid? false :message "definition dependency CID required"})
   (when-not (= (count (:definition/dependencies definition))
                (count (set (:definition/dependencies definition))))
     {:valid? false :message "definition dependencies must be unique"})))

(defn identity-payload
  "The exact, versioned payload addressed by `definition-cid`, as ordinary EDN.
  Diagnostic: `canonical-bytes` is what is actually hashed."
  [definition]
  {:kotoba.definition-identity/version payload-version
   :profile-version (:definition/profile-version definition)
   :desugar-contract-version (:definition/desugar-contract-version definition)
   :typed-kir (:definition/kir definition)
   :effect-row (:definition/effect-row definition)
   :interface (:definition/interface definition)
   ;; Dependency order is not semantic; canonical sort also makes separately
   ;; compiled frontends converge on an identical closure.
   :dependencies (vec (sort (:definition/dependencies definition)))})

(defn identity-node
  "Closed DAG-CBOR node addressed by DefCID v3.

  The semantic payload remains the injective tagged Kotoba value. Dependency
  identities are outside that value as native IPLD Links so they are graph
  edges rather than strings."
  [definition]
  (let [payload (identity-payload definition)]
    {"format" "kotoba.definition-identity.v3"
     "semantic" (normalize (dissoc payload :dependencies))
     "dependencies" (mapv ipld/link (:dependencies payload))}))

(defn canonical-bytes
  "The canonical DAG-CBOR block for DEFINITION. These bytes — not any printed
  representation of them — are the thing the CID addresses."
  [definition]
  (if-let [error (definition-error definition)]
    (throw (ex-info (:message error) error))
    (ipld/encode (identity-node definition))))

(defn canonical-hex
  "Hex of `canonical-bytes`, for fixtures and cross-implementation diffing."
  [definition]
  (bytes->hex (canonical-bytes definition)))

(defn definition-cid
  "Returns the CIDv1 dag-cbor identity of DEFINITION's canonical typed KIR.
  Throws for a malformed definition rather than silently hashing a partial
  semantic contract."
  [definition]
  (mf/cidv1-dag-cbor (canonical-bytes definition)))

(defn verify-locked-definitions
  "Verifies resolved pure definitions against a package lock.

  LOCK is a normal `:kotoba.lock/version 1` map whose dependency entries may
  contain `:dep/definition-cids`. RESOLVED is a sequence of
  `{:dep/name string :definition definition-map :definition-cid cid}`. Every
  resolved identity must equal the canonical calculation and be explicitly
  listed by its dependency; aliases and versions cannot substitute code.
  Returns `{:ok? true}` or a fail-closed diagnostic."
  [lock resolved]
  (let [allowed (into {}
                      (map (fn [dep] [(:dep/name dep)
                                      (set (:dep/definition-cids dep))]))
                      (:deps lock))]
    (or
     (some (fn [{definition :definition
                 expected-cid :definition-cid
                 name :dep/name
                 :as entry}]
             (cond
               (not (contains? allowed name))
               {:ok? false :reason :definition/unknown-dependency :entry entry}

               (not (cid? expected-cid))
               {:ok? false :reason :definition/cid-invalid :entry entry}

               :else
               (let [actual (try (definition-cid definition)
                                 (catch #?(:clj Exception :cljs :default) _ nil))]
                 (cond
                   (nil? actual)
                   {:ok? false :reason :definition/invalid :entry entry}

                   (not= actual expected-cid)
                   {:ok? false :reason :definition/hash-mismatch
                    :expected expected-cid :actual actual :entry entry}

                   (not (contains? (get allowed name #{}) expected-cid))
                   {:ok? false :reason :definition/not-locked :entry entry}))))
           resolved)
     {:ok? true})))

(defn admit-build
  "CI4: the safe-build gate. Returns `{:ok? true}` or a fail-closed diagnostic.

  `verify-locked-definitions` alone is not a gate. It answers \"is everything
  presented consistent with the lock?\", and a build that presents *nothing*
  gets `{:ok? true}` — which is the mutable-name/version fallback the delivery
  rule forbids, reached by omission rather than by intent. This function adds
  the other half of the invariant:

  - every dependency whose lock entry pins `:dep/definition-cids` must actually
    resolve, and
  - every CID that entry pins must be accounted for by a resolved definition.

  So a lock that says \"this dependency is these definitions\" cannot be
  satisfied by linking a name."
  [lock resolved]
  (let [resolved (vec resolved)
        by-name (group-by :dep/name resolved)
        pinned (filter (comp seq :dep/definition-cids) (:deps lock))
        ;; Per-entry verification runs FIRST. Coverage is the weaker, less
        ;; specific rule: if a presented definition is itself invalid, unlocked,
        ;; or hashes to something else, saying so beats reporting that some
        ;; pinned CID went unresolved -- which is merely that failure's shadow.
        verified (verify-locked-definitions lock resolved)]
    (if-not (:ok? verified)
      verified
      (or
       (some (fn [dep]
               (let [name (:dep/name dep)
                     want (set (:dep/definition-cids dep))
                     got (set (keep :definition-cid (get by-name name)))]
                 (cond
                   (empty? (get by-name name))
                   {:ok? false :reason :definition/unresolved-dependency
                    :dep/name name :expected (vec (sort want))}

                   (seq (remove got want))
                   {:ok? false :reason :definition/unresolved-definition
                    :dep/name name :missing (vec (sort (remove got want)))})))
             pinned)
       {:ok? true}))))

;; ---------------------------------------------------------------------------
;; conformance (CI2 positive / CI3 negative fixtures)
;; ---------------------------------------------------------------------------

(defn check-case
  "Runs one `lang/code-identity-conformance` case. Same shape as the capability
  conformance checkers: `{:ok? true}` or `{:ok? false :actual ...}` so the
  manifest, not the test body, is the list of things that must hold."
  [tc data]
  (case (:type tc)
    :identity
    (let [outcome (try {:cid (definition-cid data)}
                       (catch #?(:clj Exception :cljs :default) e
                         {:problem (or (:problem (ex-data e)) :definition/invalid)
                          :message #?(:clj (.getMessage ^Exception e) :cljs (.-message e))}))]
      (if (= :accept (:kind tc))
        {:ok? (= (:expected-cid tc) (:cid outcome)) :actual outcome}
        {:ok? (and (contains? outcome :problem)
                   (or (nil? (:expected-problem tc))
                       (= (:expected-problem tc) (:problem outcome))))
         :actual outcome}))

    :admission
    (let [outcome (admit-build (:lock data) (:resolved data))]
      (if (= :accept (:kind tc))
        {:ok? (true? (:ok? outcome)) :actual outcome}
        {:ok? (and (false? (:ok? outcome))
                   (= (:expected-reason tc) (:reason outcome)))
         :actual outcome}))

    {:ok? false :actual {:problem :conformance/unknown-case-type :type (:type tc)}}))

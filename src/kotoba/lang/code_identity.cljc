(ns kotoba.lang.code-identity
  "Canonical identity for typed Kotoba definitions.

  This is deliberately not a source, package, or Wasm hash.  It identifies a
  closed pure definition's canonical typed KIR and direct definition identity
  closure.  Authority is still supplied exclusively by package policy and the
  capability runtime."
  (:require [clojure.string :as str]
            [multiformats.core :as mf]))

(def definition-required
  [:definition/profile-version
   :definition/kir
   :definition/interface
   :definition/dependencies])

(defn- utf8-bytes [s]
  #?(:clj (.getBytes ^String s "UTF-8")
     :cljs (.encode (js/TextEncoder.) s)))

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

(defn canonicalize
  "Recursively produces a deterministic EDN value.  Maps and sets have no
  source-order semantics, so their members are ordered by canonical EDN;
  vectors and lists retain their order because typed KIR uses them for ordered
  arguments and instruction sequences."
  [value]
  (cond
    (map? value)
    (into (sorted-map-by #(compare (pr-str %1) (pr-str %2)))
          (map (fn [[k v]] [(canonicalize k) (canonicalize v)]))
          value)

    (set? value)
    (into (sorted-set-by #(compare (pr-str %1) (pr-str %2)))
          (map canonicalize)
          value)

    (vector? value) (mapv canonicalize value)
    (list? value) (apply list (map canonicalize value))
    (seq? value) (doall (map canonicalize value))
    :else value))

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
   (when-not (map? (:definition/kir definition))
     {:valid? false :message "definition typed KIR map required"})
   (when-not (map? (:definition/interface definition))
     {:valid? false :message "definition interface map required"})
   (when-not (vector? (:definition/dependencies definition))
     {:valid? false :message "definition dependencies vector required"})
   (when-not (every? cid? (:definition/dependencies definition))
     {:valid? false :message "definition dependency CID required"})
   (when-not (= (count (:definition/dependencies definition))
                (count (set (:definition/dependencies definition))))
     {:valid? false :message "definition dependencies must be unique"}))

(defn identity-payload
  "The exact, versioned payload addressed by `definition-cid`."
  [definition]
  (canonicalize
   {:kotoba.definition-identity/version 1
    :profile-version (:definition/profile-version definition)
    :typed-kir (:definition/kir definition)
    :interface (:definition/interface definition)
    ;; Dependency order is not semantic; canonical sort also makes separately
    ;; compiled frontends converge on an identical closure.
    :dependencies (sort (:definition/dependencies definition))}))

(defn canonical-edn [definition]
  (pr-str (identity-payload definition)))

(defn definition-cid
  "Returns the CIDv1 dag-cbor identity of DEFINITION's canonical typed KIR.
  Throws for a malformed definition rather than silently hashing a partial
  semantic contract."
  [definition]
  (if-let [error (definition-error definition)]
    (throw (ex-info (:message error) error))
    (mf/cidv1-dag-cbor (utf8-bytes (canonical-edn definition)))))

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
                   {:ok? false :reason :definition/not-locked :entry entry})))))
           resolved)
     {:ok? true})))

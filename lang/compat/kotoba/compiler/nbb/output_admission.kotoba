(ns kotoba.compiler.nbb.output-admission
  "Integrity admission for a committed primary artifact/provenance pair.

  This deliberately does not authenticate a publisher.  It proves that the
  sealed provenance is structurally closed and names the exact verified
  artifact selected by the output-set commit marker."
  (:require [cljs.reader :as reader]
            [clojure.walk :as walk]
            [kotoba.artifact.core :as artifact]
            [kotoba.compiler.nbb.cli-support :as support]
            [kotoba.compiler.nbb.compile-cache :as digest]
            [kotoba.kir.compatibility :as compatibility]
            [kotoba.kir.cljs-i64 :as i64]
            [kotoba.verifier :as verifier]))

(def format :kotoba.output-admission/v1)

(def ^:private provenance-keys
  "The CLOSED set of keys a sealed provenance record may carry. Closed on
  purpose: a record with an extra key is a record this verifier does not
  understand, and admitting it would mean admitting whatever the extra key
  said. `:definitions` joined the set when the compiler began sealing the
  definition graph (ADR 0300) -- and this line is where that change was
  caught, by a verifier that refused rather than by a test that noticed."
  #{:format :builder :compiler :language :source-sha256 :policy-sha256
    :build-metadata-sha256 :hir-sha256 :kir-sha256 :target
    :target-profile-sha256 :compatibility-sha256 :definitions :outputs :sha256})

(def ^:private wasm-targets
  #{:wasm32-kotoba-v1 :wasm32-browser-kotoba-v1 :wasm32-wasi-kotoba-v1})

(defn- reject! [reason]
  (throw (ex-info "output-set provenance rejected"
                  {:phase :verify :reason reason})))

(defn- digest? [value]
  (and (string? value) (boolean (re-matches #"[0-9a-f]{64}" value))))

(defn- provenance! [decoded]
  (let [primary (get-in decoded [:outputs :primary])]
    (when-not (and (map? decoded)
                   (= provenance-keys (set (keys decoded)))
                   (= :kotoba.provenance/v1 (:format decoded))
                   (= :kotoba-compiler/v1 (:builder decoded))
                   (= compatibility/compiler-version (:compiler decoded))
                   (= compatibility/language-version (:language decoded))
                   (artifact/valid-seal? decoded)
                   (every? digest? ((juxt :source-sha256 :policy-sha256
                                          :build-metadata-sha256 :hir-sha256
                                          :kir-sha256 :target-profile-sha256
                                          :compatibility-sha256) decoded))
                   (= #{:primary} (set (keys (:outputs decoded))))
                   ;; The definition graph must be PRESENT and must say which
                   ;; contract it speaks. An absent key would mean an artifact
                   ;; built before ADR 0300 -- or one whose identity step was
                   ;; skipped -- and those must not verify as though they had
                   ;; been measured.
                   (map? (:definitions decoded))
                   (= :kotoba.definition-identity/v1
                      (:contract (:definitions decoded)))
                   (map? primary))
      (reject! :malformed-provenance))
    decoded))

(defn- wasm-size [value]
  (cond
    (i64/bigint-value? value) (js/Number value)
    (number? value) value
    :else -1))

(defn- admit-wasm! [artifact-bytes provenance]
  (let [primary (get-in provenance [:outputs :primary])
        size (wasm-size (:size primary))]
    (when-not (and (contains? wasm-targets (:target provenance))
                   (= #{:format :sha256 :size} (set (keys primary)))
                   (= :wasm (:format primary))
                   (digest? (:sha256 primary))
                   (js/Number.isSafeInteger size)
                   (= (.-length artifact-bytes) size)
                   (= (digest/sha256 artifact-bytes) (:sha256 primary))
                   (.validate js/WebAssembly artifact-bytes))
      (reject! :wasm-identity-mismatch))))

(defn- admit-native! [artifact-bytes provenance]
  ;; The bounded reader has already admitted this exact text before this
  ;; standard EDN decode.  The latter preserves JS integer representation
  ;; expected by the independent native verifier.
  (let [artifact-text (try
                        (.decode (js/TextDecoder. "utf-8" #js {:fatal true})
                                 artifact-bytes)
                        (catch :default _ (reject! :native-decode)))
        _ (support/parse-policy-material {:present? true :text artifact-text})
        native (-> (reader/read-string artifact-text)
                   (update :value
                           #(walk/postwalk (fn [x]
                                             (if (integer? x)
                                               (i64/->bigint x) x))
                                           %)))
        primary (get-in provenance [:outputs :primary])]
    (when-not (and (= #{:format :sha256} (set (keys primary)))
                   (= :kotoba.kexe/v1 (:format primary))
                   (digest? (:sha256 primary))
                   (= :kotoba.kexe/v1 (:format native))
                   (= (:target provenance) (:target native))
                   (= (:sha256 primary) (:sha256 native)))
      (reject! :native-identity-mismatch))
    (verifier/verify-artifact! native)))

(defn admit!
  [artifact-bytes provenance]
  (let [provenance (provenance! provenance)
        primary-format (get-in provenance [:outputs :primary :format])]
    (case primary-format
      :wasm (admit-wasm! artifact-bytes provenance)
      :kotoba.kexe/v1 (admit-native! artifact-bytes provenance)
      (reject! :unsupported-primary-format))
    {:format format
     :target (:target provenance)
     :committed true
     :provenance-sealed true
     :artifact-identity-verified true
     :target-verification (case primary-format
                            :wasm :webassembly-validate
                            :kotoba.kexe/v1 :independent-native-verifier)
     :publisher-authenticated false}))

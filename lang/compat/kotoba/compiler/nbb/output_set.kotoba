(ns kotoba.compiler.nbb.output-set
  "Deterministic commit marker for a primary artifact and its provenance."
  (:require [kotoba.compiler.nbb.compile-cache :as digest]
            ["node:path" :as path]))

(def format :kotoba.output-set/v1)

(defn- buffer [value]
  (if (string? value) (.from js/Buffer value "utf8") value))

(defn- identity [role name value]
  (let [bytes (buffer value)]
    {:role role :name name
     :sha256 (digest/sha256 bytes)
     :size (.-length bytes)}))

(defn descriptor [output artifact-value provenance-value]
  (let [payload {:format format
                 :files [(identity :artifact (.basename path output) artifact-value)
                         (identity :provenance
                                   (.basename path (str output ".provenance.edn"))
                                   provenance-value)]}]
    (assoc payload :sha256 (digest/sha256 (pr-str payload)))))

(defn serialize [output artifact-value provenance-value]
  (pr-str (descriptor output artifact-value provenance-value)))

(defn- bigint? [value]
  (= "[object BigInt]" (.call (.-toString (.-prototype js/Object)) value)))

(defn- mismatch! []
  (throw (ex-info "output set is not committed"
                  {:phase :verify :reason :output-set-mismatch})))

(defn- normalize-decoded-sizes [marker]
  ;; The bounded Kotoba EDN reader intentionally decodes integer tokens as
  ;; BigInt. Marker construction uses host byte counts (safe Numbers). Admit
  ;; only those two integer representations and normalize after the range
  ;; check so verification is parser-independent without accepting strings.
  (let [files (:files marker)]
    ;; Validate container shape before walking it. Malformed EDN is an
    ;; uncommitted set (exit 65), never an accidental internal error (exit 70)
    ;; or an allocation proportional to an attacker-selected collection.
    (when-not (and (map? marker) (vector? files) (= 2 (count files))
                   (every? map? files))
      (mismatch!))
    (assoc marker :files
           (mapv (fn [file]
                   (let [size (:size file)
                         normalized (if (bigint? size) (js/Number size) size)]
                     (when-not (and (or (number? size) (bigint? size))
                                    (js/Number.isSafeInteger normalized)
                                    (<= 0 normalized))
                       (mismatch!))
                     (assoc file :size normalized)))
                 files))))

(defn verify!
  "Verify a decoded marker against the exact output values. This proves a
  committed set, not publisher authenticity; provenance remains the seal."
  [output artifact-value provenance-value marker]
  (let [expected (descriptor output artifact-value provenance-value)
        actual (normalize-decoded-sizes marker)]
    (when-not (and (= format (:format actual))
                   (= expected actual))
      (mismatch!))
    actual))

(ns kotoba.lang.conformance-matrix
  "T1.2: required-backends matrix over lang/conformance/manifest.edn.

  Pure loaders + queries. Dual-backend *execution* is T1.3 (compiler /
  kotoba-kir / kotoba-wasm runners)."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(def manifest-path "lang/conformance/manifest.edn")
(def surface-status-path "lang/surface-status.edn")
(def pure-product-required #{:kir :wasm32-kotoba-v1})

#?(:clj
   (defn- slurp-repo-file [path what]
     (if-let [url (io/resource path)]
       (slurp url)
       (if (.exists (io/file path))
         (slurp path)
         (throw (ex-info (str what " missing") {:path path}))))))

(defn load-manifest
  "Parse conformance manifest. On cljs/nbb pass text or use inject."
  ([]
   #?(:clj (load-manifest (slurp-repo-file manifest-path "conformance manifest"))
      :cljs
      (throw (ex-info "conformance-matrix/load-manifest requires text inject on cljs"
                      {:path manifest-path}))))
  ([edn-text]
   (edn/read-string edn-text)))

(defn load-surface-status
  "Parse lang/surface-status.edn. Same inject rule as load-manifest on cljs."
  ([]
   #?(:clj (load-surface-status (slurp-repo-file surface-status-path "surface status"))
      :cljs
      (throw (ex-info "conformance-matrix/load-surface-status requires text inject on cljs"
                      {:path surface-status-path}))))
  ([edn-text]
   (edn/read-string edn-text)))

(defn backends [manifest]
  (:backends manifest))

(defn case-classes [manifest]
  (:case-classes manifest))

(defn cases [manifest]
  (:cases manifest))

(defn cases-for-class
  [manifest class-kw]
  (filterv #(= class-kw (:class %)) (cases manifest)))

(defn pure-product-cases
  "Cases under pure-product profile classes (run + compile-expr)."
  [manifest]
  (filterv (fn [c]
             (let [cls (get (case-classes manifest) (:class c))]
               (= :pure-product (:profile cls))))
           (cases manifest)))

(defn required-backends-for
  "Effective required backends for a case (case override wins)."
  [manifest case]
  (or (:required-backends case)
      (get-in (case-classes manifest) [(:class case) :required-backends])
      #{}))

(defn validate-matrix
  "Structural check of T1.2 matrix. Returns {:ok? bool :problems [...] }."
  [manifest]
  (let [problems (transient [])
        vers (:kotoba.lang.conformance/version manifest)
        backend-ids (set (keys (backends manifest)))
        classes (case-classes manifest)]
    (when-not (and (number? vers) (>= vers 2))
      (conj! problems {:type :version :got vers :need ">= 2"}))
    (when-not (seq backend-ids)
      (conj! problems {:type :no-backends}))
    (when-not (contains? classes :pure-product-run)
      (conj! problems {:type :missing-class :class :pure-product-run}))
    (when-not (= pure-product-required
                 (get-in classes [:pure-product-run :required-backends]))
      (conj! problems {:type :pure-product-required-mismatch
                       :got (get-in classes [:pure-product-run :required-backends])
                       :need pure-product-required}))
    (doseq [c (cases manifest)]
      (when-not (:id c)
        (conj! problems {:type :case-missing-id :case c}))
      (when-not (:class c)
        (conj! problems {:type :case-missing-class :id (:id c)}))
      (when-not (contains? classes (:class c))
        (conj! problems {:type :unknown-class :id (:id c) :class (:class c)}))
      (when-not (set? (:required-backends c))
        (conj! problems {:type :case-required-backends-not-set :id (:id c)}))
      (doseq [b (:required-backends c)]
        (when-not (contains? backend-ids b)
          (conj! problems {:type :unknown-backend :id (:id c) :backend b})))
      (when (and (= :pure-product-run (:class c))
                 (not= pure-product-required (:required-backends c)))
        (conj! problems {:type :pure-product-case-backends
                         :id (:id c)
                         :got (:required-backends c)})))
    (let [ps (persistent! problems)]
      {:ok? (empty? ps) :problems ps
       :case-count (count (cases manifest))
       :pure-product-count (count (pure-product-cases manifest))})))

;; --- surface-status <-> manifest agreement --------------------------------
;;
;; A `:pure-product-run` case declares `#{:kir :wasm32-kotoba-v1}` required --
;; the two compiler backends. lang/surface-status.edn separately records what
;; the compiler was *measured* to do with the same surface. Nothing connected
;; the two, so `:nested-let-destructuring` could sit in the manifest requiring
;; both compiler backends while surface-status recorded the compiler rejecting
;; the shape, and neither file was wrong on its own.
;;
;; The note that names the contradiction already existed for
;; :protocol-and-record-dispatch, hand-written as `:orphaned-conformance`.
;; This turns that one-off into the invariant: a measured compiler rejection on
;; an entry linked to a compiler-required case MUST carry the note, and a note
;; without a measured rejection is stale.

(def surface-status-sections [:invariants :collections :other-gaps])

(defn surface-entries
  "Every named surface-status entry, as [section name entry]."
  [surface-status]
  (for [section surface-status-sections
        [name entry] (get surface-status section)]
    [section name entry]))

(defn measured-compiler-rejection?
  "Did this entry measure the canonical compiler rejecting the surface?

  Two recorded shapes: one verdict (`:result :rejected`) and a per-source list
  where the compiler is one column (`:compiler :rejected`)."
  [entry]
  (let [m (:measurement entry)
        verdicts (cons m (:results m))]
    (boolean (some (fn [v] (and (map? v)
                                (or (= :rejected (:result v))
                                    (= :rejected (:compiler v)))))
                   verdicts))))

(defn linked-cases
  "Conformance case ids this entry claims, from either link form."
  [entry]
  (into #{} (remove nil?) [(:conformance entry)
                           (get-in entry [:orphaned-conformance :case])]))

(defn validate-claims
  "Cross-check surface-status against the conformance manifest.
  Returns `{:ok? bool :problems [...] :linked-case-count n}`."
  [manifest surface-status]
  (let [by-id (into {} (map (juxt :id identity)) (cases manifest))
        problems (transient [])
        linked (transient #{})]
    (doseq [[section name entry] (surface-entries surface-status)
            :let [ids (linked-cases entry)
                  rejected? (measured-compiler-rejection? entry)
                  note (:orphaned-conformance entry)]]
      (doseq [id ids]
        (conj! linked id)
        ;; A case can legitimately live in the compiler's own
        ;; resources/kotoba/lang-conformance/pilot-manifest.edn instead of this
        ;; one -- that is where the `*-kit` cases the compiler's runner executes
        ;; are declared. The link then has to say so, because a reader who looks
        ;; here and finds nothing cannot tell "executed elsewhere" from "dangling".
        (when-not (or (contains? by-id id) (:conformance-manifest entry))
          (conj! problems {:type :conformance-case-unknown
                           :section section :entry name :case id
                           :why "not in this manifest and no :conformance-manifest
                                 says which one declares it"})))
      (when (and rejected?
                 (some #(contains? pure-product-required %)
                       (mapcat #(required-backends-for manifest (get by-id %)) ids))
                 (nil? note))
        (conj! problems
               {:type :measured-rejection-without-orphan-note
                :section section :entry name :cases ids
                :why "the case requires the compiler backends and this entry
                      measured the compiler rejecting the surface; without
                      :orphaned-conformance the manifest asserts a requirement
                      contradicted by the record next to it"}))
      (when (and note (not rejected?))
        (conj! problems
               {:type :orphan-note-without-measured-rejection
                :section section :entry name
                :why "the note says a case is unexecuted because the compiler
                      rejects the surface, but no measurement here records a
                      rejection -- re-measure or drop the note"})))
    (let [ps (persistent! problems)]
      {:ok? (empty? ps) :problems ps
       :linked-case-count (count (persistent! linked))})))

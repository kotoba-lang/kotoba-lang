(ns kotoba.lang.conformance-matrix
  "T1.2: required-backends matrix over lang/conformance/manifest.edn.

  Pure loaders + queries. Dual-backend *execution* is T1.3 (compiler /
  kotoba-kir / kotoba-wasm runners)."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
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


;; --- required backends <-> the runners that actually drive them ------------
;;
;; `:required-backends` says which backends a case MUST pass. It does not say
;; where any of them is driven, and until 2026-09-03 nothing did. Measured that
;; day, `:bounded-set-literal-and-operations` declared `#{:kir
;; :wasm32-kotoba-v1}` and was executed on neither: amu's dual-backend runner
;; reads its own pilot manifest and never this one, and `kotoba`'s runner does
;; read this one but drives `kotoba.runtime/wasm-binary`, which `:backends`
;; does not name at all. A case can therefore be declared on two backends,
;; exercised on a third that is not declared, and look green from every angle.
;;
;; A case that records `:executed-by` opts into the check: for it, the required
;; backends must be EXACTLY the ones named there plus the ones recorded in
;; `:unexecuted-backends` with a date, a reason and a closing condition. A
;; backend cannot be in both -- an excuse that outlives what it excuses is the
;; same defect in the other direction -- and the count of cases carrying the
;; record is a floor, so the annotation can only grow.
;;
;; The other half of this cannot live here: a runner must assert that the case
;; it just executed names it. `kotoba.lang.collections-conformance-test` does
;; that, so `:executed-by` cannot claim a runner that does not run.

(def min-cases-with-execution-record
  "Floor on how many cases record where their required backends are driven.
  A ratchet: raise it when a case is annotated, never lower it. Without a
  floor, deleting every `:executed-by` would make this check pass by having
  nothing to check -- which is not the same as finding nothing wrong.

  Raised from 2 to 5 on 2026-09-03, when the three `collections/` cases that
  had no record at all gained one."
  5)

(def ^:private deferral-keys #{:as-of :reason :closes-when})

(defn cases-with-execution-record
  "Cases that record where their required backends are driven.

  `:unexecuted-backends` counts, not only `:executed-by`. This filtered on
  `:executed-by` alone until 2026-09-03, so a case that records ONLY
  deferrals -- one that no backend runs, which is exactly the case whose
  record most needs checking -- was invisible to `validate-execution`: its
  deferral entries were never checked for `:as-of`/`:reason`/`:closes-when`,
  and it never counted toward the floor. Measured on
  `:bounded-vector-literal-and-operations`, whose two required backends are
  both deferred."
  [manifest]
  (filterv #(or (:executed-by %) (:unexecuted-backends %)) (cases manifest)))

(defn validate-execution
  "Cross-check `:executed-by`/`:unexecuted-backends` against
  `:required-backends`. Returns `{:ok? bool :problems [...] :recorded n}`."
  [manifest]
  (let [backend-ids (set (keys (backends manifest)))
        recorded (cases-with-execution-record manifest)
        problems (transient [])]
    (doseq [c recorded
            :let [id (:id c)
                  required (required-backends-for manifest c)
                  executed (set (keys (:executed-by c)))
                  deferred (set (keys (:unexecuted-backends c)))]]
      (doseq [b (into executed deferred)]
        (when-not (contains? backend-ids b)
          (conj! problems {:type :execution-unknown-backend :id id :backend b})))
      (doseq [b (set/intersection executed deferred)]
        (conj! problems
               {:type :backend-both-executed-and-deferred :id id :backend b
                :why "an entry in :unexecuted-backends naming a backend that
                      :executed-by also names is an excuse that outlived what
                      it excused; move it or drop it"}))
      (doseq [b (set/difference required (into executed deferred))]
        (conj! problems
               {:type :required-backend-not-accounted-for :id id :backend b
                :why "the case requires this backend and records neither a
                      runner for it nor a dated reason there is none"}))
      (doseq [b (set/difference (into executed deferred) required)]
        (conj! problems
               {:type :backend-recorded-but-not-required :id id :backend b
                :why "the record names a backend the case does not require;
                      either the requirement was dropped and the record was
                      not, or the record is about the wrong case"}))
      (doseq [[b entry] (:unexecuted-backends c)]
        (let [missing (remove #(contains? entry %) deferral-keys)]
          (when (seq missing)
            (conj! problems
                   {:type :deferral-missing-keys :id id :backend b
                    :missing (vec (sort missing))}))))
      (doseq [[b runner] (:executed-by c)]
        (when-not (and (string? runner) (seq runner))
          (conj! problems
                 {:type :runner-not-named :id id :backend b
                  :why ":executed-by must name the repository and namespace
                        that drives the backend, so a reader can go and look"}))))
    (let [ps (persistent! problems)
          n (count recorded)]
      {:ok? (and (empty? ps) (>= n min-cases-with-execution-record))
       :problems (cond-> ps
                   (< n min-cases-with-execution-record)
                   (conj {:type :execution-record-floor
                          :got n :need min-cases-with-execution-record}))
       :recorded n})))

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

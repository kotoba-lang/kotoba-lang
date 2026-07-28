(ns kotoba.lang.conformance-matrix
  "T1.2: required-backends matrix over lang/conformance/manifest.edn.

  Pure loaders + queries. Dual-backend *execution* is T1.3 (compiler /
  kotoba-kir / kotoba-wasm runners)."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(def manifest-path "lang/conformance/manifest.edn")
(def pure-product-required #{:kir :wasm32-kotoba-v1})

(defn load-manifest
  "Parse conformance manifest. On cljs/nbb pass text or use inject."
  ([]
   #?(:clj
      (if-let [url (io/resource manifest-path)]
        (load-manifest (slurp url))
        (if (.exists (io/file manifest-path))
          (load-manifest (slurp manifest-path))
          (throw (ex-info "conformance manifest missing" {:path manifest-path}))))
      :cljs
      (throw (ex-info "conformance-matrix/load-manifest requires text inject on cljs"
                      {:path manifest-path}))))
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

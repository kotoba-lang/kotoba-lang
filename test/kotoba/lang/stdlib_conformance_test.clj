(ns kotoba.lang.stdlib-conformance-test
  "Runs `:portable-source-stdlib`, and pins the three refusals that decided
  what `lang/stdlib/core.kotoba` may contain.

  Until 2026-09-01 this case was declared and unread. Its `:prelude` key named
  a route no implementation has, and the module it named could not be compiled
  by any route at all -- so the case would have been red if anything had run
  it, and nothing did. Both halves are fixed: the module is a namespaced
  library reached through the project route, and this file executes the case.

  WHAT THIS FILE DOES NOT DO: it does not run the project linker. That lives in
  `kotoba-lang/amu`, which this repository does not depend on. It joins the two
  modules and performs the alias substitution the linker performs -- `core/f`
  becomes `f` -- which is enough to answer whether the two modules COMPUTE the
  expected value, and is not enough to answer whether the linker resolves them.
  That second question is measured through the CLI and recorded, with commands
  and exit codes, in `lang/stdlib.edn` `:artifact-reachability`."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [kotoba.compiler.frontend :as frontend]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private conformance-root "lang/conformance")
(def ^:private module-path "lang/stdlib/core.kotoba")
(def ^:private stdlib-manifest-path "lang/conformance/stdlib/manifest.edn")

(def ^:private case-ids [:portable-source-stdlib :portable-source-stdlib-extended
                         :portable-source-stdlib-ordered
                         :portable-source-stdlib-keyed])

(defn- conformance-cases []
  (let [cases (:cases (edn/read-string (slurp (str conformance-root "/manifest.edn"))))]
    (mapv (fn [id] (first (filter #(= id (:id %)) cases))) case-ids)))

(defn- conformance-case []
  (first (conformance-cases)))

(defn- stdlib-modules []
  (:modules (edn/read-string (slurp stdlib-manifest-path))))

;; Namespace to file, from the manifest rather than by convention -- the
;; conformance entries name which module they require, and version 4 added a
;; second one.
(defn- module-path-for [namespace-symbol]
  (:path (first (filter #(= namespace-symbol (:namespace %)) (stdlib-modules)))))

(defn- ns-form [forms]
  (first (filter #(and (seq? %) (= 'ns (first %))) forms)))

(defn- ns-clause [forms tag]
  (first (filter #(and (seq? %) (= tag (first %))) (nnext (ns-form forms)))))

(defn- body-forms [forms]
  (remove #(and (seq? %) (= 'ns (first %))) forms))

(defn- unalias
  "What the linker does to a qualified call, and only that: `core/f` -> `f`.
  Namespaced KEYWORDS are left alone; only symbols carry module aliases."
  [alias forms]
  (walk/postwalk
   (fn [node]
     (if (and (symbol? node) (= (str alias) (namespace node)))
       (symbol (name node))
       node))
   forms))

(defn- join-case [{:keys [entry]}]
  (let [entry-forms (sema/read-forms (slurp (str conformance-root "/" entry)))
        [required _ alias] (second (ns-clause entry-forms :require))
        module (sema/read-forms (slurp (module-path-for required)))
        source (->> (concat (body-forms module)
                            (unalias alias (body-forms entry-forms)))
                    (map pr-str)
                    (str/join "\n"))]
    (kir/lower (sema/analyze source))))

(deftest portable-source-stdlib-computes-its-expected-value
  ;; Both cases: the version-2 list and the version-3 additions. A case that
  ;; is declared and not found is a failure, not a skip.
  (doseq [case- (conformance-cases)]
    (is (some? case-) (str "declared case missing from lang/conformance/manifest.edn: " (pr-str case-ids)))
    (when case-
      (let [{:keys [id function args expect]} case-]
        (testing (name id)
          (is (= (:kotoba expect)
                 (long (kir/execute (join-case case-) (symbol (or function "main")) (vec args))))
              "the case's :expect {:kotoba n} is what the two modules compute"))))))

(deftest the-case-names-the-project-route-and-not-a-prelude
  (doseq [[case- entry] (map vector (conformance-cases)
                             ["stdlib/basic.kotoba" "stdlib/extended.kotoba"
                              "stdlib/ordered.kotoba" "stdlib/keyed.kotoba"])]
    (is (nil? (:prelude case-))
        "`:prelude` names a route no implementation has; see lang/stdlib.edn")
    (is (= ["."] (:source-paths case-)))
    (is (= entry (:entry case-)))))

(deftest the-entry-requires-the-module-rather-than-being-prepended-to-it
  (let [namespaces (set (map :namespace (stdlib-modules)))]
    (doseq [case- (conformance-cases)]
      (let [entry-forms (sema/read-forms (slurp (str conformance-root "/" (:entry case-))))
            require- (ns-clause entry-forms :require)]
        (is (some? require-) "the entry must be a project module")
        (is (contains? namespaces (first (second require-)))
            (str (:id case-) " must require a module the manifest declares"))
        (is (= :as (second (second require-))))))))

(deftest the-modules-export-exactly-the-frozen-public-names
  (doseq [{:keys [path namespace public-names]} (stdlib-modules)]
    (testing (str namespace)
      (let [forms (sema/read-forms (slurp path))
            exported (set (second (ns-clause forms :export)))]
        (is (= namespace (second (ns-form forms))))
        (is (= public-names exported)
            (str "extra=" (pr-str (set/difference exported public-names))
                 " missing=" (pr-str (set/difference public-names exported))))))))

(deftest each-module-is-mirrored-byte-for-byte-under-the-conformance-root
  ;; The conformance case can be compiled with either root on --source-path;
  ;; if the two copies drifted, one of the two roots would be compiling
  ;; something else and only one of them would be tested.
  (doseq [{:keys [path conformance-path namespace]} (stdlib-modules)]
    (is (= (slurp path) (slurp conformance-path))
        (str namespace ": conformance mirror must match the package SSoT"))))

(deftest no-frozen-public-name-is-a-reserved-name
  ;; Five of version 1's twenty-seven names had become reserved while the file
  ;; sat uncompiled. A module that defines one is refused outright, so this is
  ;; the check that the frozen list stays callable as the language grows.
  (doseq [{:keys [namespace public-names]} (stdlib-modules)]
    (let [collisions (filter frontend/reserved-function-names public-names)]
      (is (empty? collisions)
          (str "reserved, so " namespace " cannot define them: " (pr-str (sort collisions)))))))

(deftest every-core-export-is-single-arity
  ;; A multi-arity function CANNOT be exported: the module is analyzed before
  ;; it is linked, so `(defn f ([x] ...) ([x y] ...))` is already `f$arity$1`
  ;; when `(:export [f])` is resolved, and the linker answers
  ;; "export does not name a declared function". Measured 2026-09-01 against
  ;; amu 27d82d8; the reproduction is in lang/conformance/stdlib/manifest.edn.
  (let [forms (sema/read-forms (slurp module-path))
        exported (set (second (ns-clause forms :export)))
        multi (for [form (body-forms forms)
                    :when (and (seq? form) (= 'defn (first form))
                               (contains? exported (second form))
                               (not (vector? (nth form 2))))]
                (second form))]
    (is (empty? multi)
        (str "the linker cannot export these: " (pr-str (vec multi))))))

;; The three refusals that decided the contents. Each asserts the MESSAGE,
;; because several unrelated failures produce the same exception type.

(defn- refusal [source]
  (try (sema/analyze source) ::admitted
       (catch clojure.lang.ExceptionInfo error (.getMessage error))))

(deftest an-empty-record-type-is-refused
  ;; This is why `Some`/`None`/`Ok`/`Err` are gone rather than respelled.
  (is (= "record fields must be a non-empty unique bounded vector"
         (refusal "(defrecord None [])\n(defn main [] 1)")))
  (is (= ::admitted (refusal "(defrecord Some [value])\n(defn main [] 1)"))
      "one field is enough; the refusal is of the EMPTY vector, not of records"))

(deftest a-record-based-option-cannot-be-one-value-type
  ;; The second, independent reason: even spelled with a field, `Some` and
  ;; `None` are two nominal types, so a function returning either is refused.
  ;; An empty record type would therefore have bought nothing.
  (is (= "if branches must have the same value type"
         (refusal (str "(defrecord Some [value])\n(defrecord None [absent])\n"
                       "(defn pick [x] (if (> x 3) (->Some x) (->None 0)))\n"
                       "(defn main [] 1)")))))

(deftest the-language-owns-the-option-and-result-names
  (is (= "reserved function name"
         (refusal "(defn option-value [o d] d)\n(defn main [] 1)")))
  (is (= "reserved function name"
         (refusal "(defn some [p items] items)\n(defn main [] 1)"))))

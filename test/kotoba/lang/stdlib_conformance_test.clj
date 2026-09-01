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

(defn- conformance-case []
  (->> (:cases (edn/read-string (slurp (str conformance-root "/manifest.edn"))))
       (filter #(= :portable-source-stdlib (:id %)))
       first))

(defn- stdlib-module []
  (first (:modules (edn/read-string (slurp stdlib-manifest-path)))))

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

(def ^:private joined
  (delay
    (let [{:keys [entry]} (conformance-case)
          module (sema/read-forms (slurp module-path))
          entry-forms (sema/read-forms (slurp (str conformance-root "/" entry)))
          [_ _ alias] (second (ns-clause entry-forms :require))
          source (->> (concat (body-forms module)
                              (unalias alias (body-forms entry-forms)))
                      (map pr-str)
                      (str/join "\n"))]
      (kir/lower (sema/analyze source)))))

(deftest portable-source-stdlib-computes-its-expected-value
  (let [{:keys [function args expect]} (conformance-case)]
    (is (= (:kotoba expect)
           (long (kir/execute @joined (symbol (or function "main")) (vec args))))
        "the case's :expect {:kotoba n} is what the two modules compute")))

(deftest the-case-names-the-project-route-and-not-a-prelude
  (let [case- (conformance-case)]
    (is (nil? (:prelude case-))
        "`:prelude` names a route no implementation has; see lang/stdlib.edn")
    (is (= ["."] (:source-paths case-)))
    (is (= "stdlib/basic.kotoba" (:entry case-)))))

(deftest the-entry-requires-the-module-rather-than-being-prepended-to-it
  (let [entry-forms (sema/read-forms (slurp (str conformance-root "/"
                                                 (:entry (conformance-case)))))
        require- (ns-clause entry-forms :require)]
    (is (some? require-) "the entry must be a project module")
    (is (= 'stdlib.core (first (second require-))))
    (is (= :as (second (second require-))))))

(deftest the-module-exports-exactly-the-frozen-public-names
  (let [forms (sema/read-forms (slurp module-path))
        exported (set (second (ns-clause forms :export)))
        frozen (:public-names (stdlib-module))]
    (is (= 'stdlib.core (second (ns-form forms))))
    (is (= frozen exported)
        (str "extra=" (pr-str (set/difference exported frozen))
             " missing=" (pr-str (set/difference frozen exported))))))

(deftest no-frozen-public-name-is-a-reserved-name
  ;; Five of version 1's twenty-seven names had become reserved while the file
  ;; sat uncompiled. A module that defines one is refused outright, so this is
  ;; the check that the frozen list stays callable as the language grows.
  (let [frozen (:public-names (stdlib-module))
        collisions (filter frontend/reserved-function-names frozen)]
    (is (empty? collisions)
        (str "reserved, so stdlib.core cannot define them: " (pr-str (sort collisions))))))

(deftest every-export-is-single-arity
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

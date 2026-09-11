(ns kotoba.lang.host-clj-conformance-test
  "Executes the authority conformance cases that require the `:host-clj`
  backend.

  `lang/conformance/manifest.edn` has declared `:host-clj` and `:host-cljs`
  since it was written -- \"Clojure host embed for :clj expect keys / .clj
  entries\" -- and seven of its thirty-seven cases require one of them. Measured
  2026-08-12: no runner in kotoba, kotoba-lang or the compiler executed either,
  so thirteen recorded `:clj`/`:cljs` expect values were compared against
  nothing and two cases ran nowhere at all. The wasm runner in
  `kotoba.language-conformance-test` covers `:kotoba` expects only, which is
  why `:entry-extension-clj` has no `:kotoba` key to cover: the
  `entry_extensions` family exists to show the extension selects the host.

  This runner closes the `:clj` half. It is deliberately plain Clojure -- no
  compiler, no wasm -- because that is what `:host-clj` means: read the entry
  the way Clojure reads it, call the function, compare."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]])
  (:import (java.io PushbackReader)))

(def conformance-root (io/file "lang/conformance"))

(defn- manifest []
  (edn/read-string (slurp (io/file conformance-root "manifest.edn"))))

(defn- host-clj-cases [m]
  (->> (:cases m)
       (filter #(contains? (set (:required-backends %)) :host-clj))
       (filter #(contains? (:expect %) :clj))))

(defn- read-forms
  "Read an entry the way Clojure reads it: reader conditionals resolved with
  the default `:clj` feature. This is the whole point of the reader_target
  cases -- `branching.cljc` carries a `:clj` arm and a `:cljs` arm, and only
  the host decides which one exists."
  [file]
  (with-open [r (PushbackReader. (io/reader file))]
    (let [eof (Object.)]
      (loop [acc []]
        (let [form (read {:read-cond :allow :eof eof} r)]
          (if (identical? eof form) acc (recur (conj acc form))))))))

(defn- eval-in-fresh-ns [forms]
  (let [ns-sym (gensym "kotoba-host-clj-case-")
        prev *ns*]
    (try
      (binding [*ns* *ns*]
        (in-ns ns-sym)
        (clojure.core/refer-clojure)
        (let [result (last (mapv eval forms))]
          {:ns ns-sym :last result}))
      (finally
        (in-ns (ns-name prev))))))

(defn- run-plain-case [{:keys [entry function args]}]
  (let [forms (read-forms (io/file conformance-root entry))
        {:keys [ns]} (eval-in-fresh-ns forms)
        f (ns-resolve ns (symbol (or function "main")))]
    (when-not f
      (throw (ex-info "entry does not define the case function"
                      {:entry entry :function function})))
    (apply f args)))

(defn- run-source-path-case
  "Cases with `:source-paths` exist to pin which extension wins when the same
  namespace is available as .clj, .cljc and .kotoba. Resolving that honestly
  means letting Clojure's own loader choose, so the directory goes on a real
  classloader rather than being resolved by hand -- a hand-rolled search would
  test this file's idea of the order, not the host's."
  [{:keys [entry function args source-paths]}]
  (let [thread (Thread/currentThread)
        prev-cl (.getContextClassLoader thread)
        cl (clojure.lang.DynamicClassLoader. prev-cl)]
    (try
      (doseq [p source-paths]
        (.addURL cl (.toURL (.toURI (io/file conformance-root p)))))
      (.setContextClassLoader thread cl)
      ;; Both bindings are needed. `RT/baseLoader` prefers `Compiler/LOADER`
      ;; when it is bound, and it is bound while a test file is being loaded --
      ;; so setting only the context classloader leaves `require` looking at
      ;; the loader that was already in effect, which is how this first
      ;; reported "Could not locate demo/util.clj or demo/util.cljc".
      (with-bindings {clojure.lang.Compiler/LOADER cl
                      #'*use-context-classloader* true}
        (let [forms (read-forms (io/file conformance-root entry))
              ns-form (first forms)
              entry-ns (second ns-form)]
          (doseq [form forms] (eval form))
          (let [f (ns-resolve entry-ns (symbol (or function "main")))]
            (when-not f
              (throw (ex-info "entry does not define the case function"
                              {:entry entry :function function
                               :defined (keys (ns-publics entry-ns))})))
            (apply f args))))
      (finally
        (.setContextClassLoader thread prev-cl)))))

(deftest host-clj-cases-are-non-vacuous
  (let [cases (host-clj-cases (manifest))]
    (is (seq cases) "manifest declares :host-clj but no case requires it")
    (is (= 6 (count cases))
        (str "expected the six :host-clj cases measured 2026-08-12, got "
             (mapv :id cases)))))

(deftest host-clj-cases-produce-their-recorded-clj-expect
  (doseq [{:keys [id source-paths] :as case} (host-clj-cases (manifest))
          :when (not source-paths)]
    (testing (name id)
      (is (= (get-in case [:expect :clj]) (run-plain-case case))))))

;; The case that pins which extension wins when one namespace is available as
;; .clj, .cljc and .kotoba. Running it for the first time on 2026-08-12 found
;; two things the manifest had recorded without ever executing: `:function
;; "main"` where the entry defines `run`, and `:clj 11` where the Clojure
;; loader gives 2 -- 11 is what the .cljc variant (+10) returns, correct for
;; :cljs, where no .clj exists. Both are corrected in the manifest; this test
;; is what keeps them corrected.
(deftest namespace-extension-priority-resolves-clj-before-cljc
  (let [case (first (filter #(= :namespace-extension-priority (:id %))
                            (host-clj-cases (manifest))))]
    (is (some? case))
    (is (= "run" (:function case))
        "the entry defines run; a stale :function makes this case unrunnable")
    (is (= 2 (get-in case [:expect :clj])))
    (is (= (get-in case [:expect :clj]) (run-source-path-case case)))))

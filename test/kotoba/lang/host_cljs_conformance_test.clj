(ns kotoba.lang.host-cljs-conformance-test
  "Drives `scripts/host_cljs_conformance.cljs` and compares every `:cljs`
  expect the authority manifest records for a `:host-cljs` case.

  Why the runner is nbb and not this JVM: `:host-cljs` means \"ClojureScript
  host embed\", and the cases turn on which reader-conditional arm exists and
  which file extension a namespace resolves to. Evaluating them under Clojure
  would answer the `:clj` question again -- which
  `kotoba.lang.host-clj-conformance-test` already answers -- and prove nothing
  about the host under test. The same reasoning is why the sibling runner
  refuses to hand-roll extension priority.

  Absence of nbb fails this test rather than skipping it. A conformance runner
  that goes quiet when its host is missing reproduces the defect that made
  :host-cljs worth writing: seven recorded expects that nothing compared."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def conformance-root "lang/conformance")
(def runner "scripts/host_cljs_conformance.cljs")

(defn- manifest []
  (edn/read-string (slurp (io/file conformance-root "manifest.edn"))))

(defn- host-cljs-cases []
  (->> (:cases (manifest))
       (filter #(contains? (set (:required-backends %)) :host-cljs))
       (filter #(contains? (:expect %) :cljs))))

(defn- nbb [& args]
  (let [{:keys [exit out err]} (apply shell/sh "nbb" args)]
    (when-not (zero? exit)
      (throw (ex-info "nbb runner failed" {:exit exit :err err :args args})))
    (edn/read-string (str/trim (last (str/split-lines out))))))

(defn- plain-results []
  (:results (nbb runner conformance-root)))

(defn- staged-result
  "The one case whose entry requires another namespace. nbb takes a classpath
  only at startup -- there is no runtime add-classpath, checked 2026-08-12 --
  so the directory is staged first and named on the next invocation."
  [id]
  (let [{:keys [dir ns]} (nbb runner conformance-root "--stage" (name id))]
    (first (:results (nbb "--classpath" dir runner conformance-root
                          "--run-required" (name id) "--ns" ns)))))

(deftest host-cljs-cases-are-non-vacuous
  (is (= 6 (count (host-cljs-cases)))
      (str "expected the six :host-cljs cases measured 2026-08-12, got "
           (mapv :id (host-cljs-cases)))))

(deftest host-cljs-cases-produce-their-recorded-cljs-expect
  (let [cases (host-cljs-cases)
        by-id (into {} (map (juxt :id identity)) cases)
        results (concat (plain-results)
                        (keep #(when (:source-paths %) (staged-result (:id %))) cases))]
    (is (= (count cases) (count results))
        (str "every case must report a result, including one it could not run: "
             (pr-str (mapv :id results))))
    (doseq [{:keys [id value error] :as r} results]
      (testing (name id)
        (is (nil? error) (pr-str r))
        (is (= (get-in by-id [id :expect :cljs]) value))))))

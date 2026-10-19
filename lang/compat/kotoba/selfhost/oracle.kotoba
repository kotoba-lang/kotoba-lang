(ns kotoba.selfhost.oracle
  "Loads and calls the shipped Kotoba decisions.

  `kotoba/*.kotoba` holds the decisions; `resources/kotoba/selfhost/oracle/*.kir.edn`
  is the compiled artifact that ships. This namespace is the seam between them,
  and it is deliberately thin: it resolves a resource, executes an export, and
  does not decide anything itself.

  The compiler is NOT here. It produced the artifacts under `:test` and is not
  on this classpath (ADR-reliability-t63: the compiler is a build/analysis tool;
  the artifact is what runs). Regenerate with `clojure -M:test:gen`.

  A missing or unreadable artifact throws rather than falling back to a host
  reimplementation. A silent fallback is how a decision stops being the one that
  shipped, and this repo already lost an analyzer that way once."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kotoba.kir :as ir]))

(def oracles
  "Artifact id -> the .kotoba it was compiled from."
  {:safe-analyzer       "kotoba/safe_analyzer_core.kotoba"
   :capability-admission "kotoba/capability_admission_core.kotoba"})

(defn resource-path [id]
  (str "kotoba/selfhost/oracle/" (name id) ".kir.edn"))

(defn- load-kir [id]
  (let [path (resource-path id)
        r (io/resource path)]
    (when-not r
      (throw (ex-info "missing shipped oracle artifact — run `clojure -M:test:gen`"
                      {:oracle id :path path})))
    (edn/read-string (slurp r))))

(def ^:private kir-cache (atom {}))

(defn kir
  "The shipped KIR for `id`, read once."
  [id]
  (or (get @kir-cache id)
      (let [k (load-kir id)]
        (swap! kir-cache assoc id k)
        k)))

(defn call
  "Execute an export of a shipped oracle. Args and result are plain values."
  [id export args]
  (ir/execute (kir id) (symbol (name export)) (vec args)))

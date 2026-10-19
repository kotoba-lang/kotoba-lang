(ns aero.native
  "Native backend descriptors for aero solvers.

  The Rust `kami-engine-cfd` crate is a backend implementation, not the source
  of truth for the aero contract. This namespace keeps the backend boundary as
  CLJC data so hosts can choose a native executable/adapter without changing the
  case or result shape used by `aero.solver`."
  (:require [clojure.string :as str]))

(def kami-cfd-backend
  {:backend/id :kami-cfd/lbm
   :backend/name "kami-cfd lattice-Boltzmann"
   :backend/class :native
   :backend/repo "kotoba-lang/kami-engine-cfd"
   :backend/solver :lbm
   :backend/input-contract :aero/case
   :backend/output-contract :aero/result
   :backend/host-capabilities #{:native/process :fs/read :fs/write}
   :backend/status :adapter-required})

(def backends
  {:rom-buildup {:backend/id :aero/rom-buildup
                 :backend/name "aero component build-up"
                 :backend/class :cljc
                 :backend/solver :rom-buildup
                 :backend/input-contract :aero/case
                 :backend/output-contract :aero/result
                 :backend/host-capabilities #{}
                 :backend/status :available}
   :lbm kami-cfd-backend})

(def required-result-keys
  #{:Cd :CdA :Re :breakdown :fluid :solver})

(defn backend [solver]
  (get backends solver))

(defn native-backend? [descriptor]
  (= :native (:backend/class descriptor)))

(defn validate-result
  "Validate the shared aero result envelope that both CLJC and native backends
  must return. Returns {:ok? boolean :missing #{...} :solver keyword}."
  [result]
  (let [ks (set (keys result))
        missing (set (remove ks required-result-keys))]
    {:ok? (empty? missing)
     :missing missing
     :solver (:solver result)}))

(defn host-command
  "Produce a host-adapter command descriptor for a native backend. This is data,
  not execution; a CLI/process host can interpret it."
  [{:backend/keys [id solver repo]} case-path result-path]
  {:command/kind :native-process
   :command/backend id
   :command/repo repo
   :command/argv ["kami-cfd" "solve" "--solver" (name solver)
                  "--case" case-path "--out" result-path]
   :command/produces :aero/result})

(defn valid-command? [cmd]
  (and (= :native-process (:command/kind cmd))
       (seq (:command/argv cmd))
       (every? string? (:command/argv cmd))
       (str/ends-with? (last (:command/argv cmd)) ".edn")))


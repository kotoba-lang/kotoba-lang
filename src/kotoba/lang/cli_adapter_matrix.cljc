(ns kotoba.lang.cli-adapter-matrix
  "T9.1: validate lang/cli-adapter-matrix.edn against lang/cli.edn command ids."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            #?(:clj [clojure.java.io :as io])))

(def contract-path "lang/cli.edn")
(def matrix-path "lang/cli-adapter-matrix.edn")

(defn load-edn [path]
  #?(:clj
     (edn/read-string (slurp path))
     :cljs
     (throw (ex-info "load-edn requires path inject on cljs" {:path path}))))

(defn validate
  "Return {:ok? :problems}."
  ([]
   (validate (load-edn contract-path) (load-edn matrix-path)))
  ([contract matrix]
   (let [problems (transient [])
         cmd-ids (set (map :id (:kotoba.cli.contract/commands contract)))
         matrix-ids (set (keys (:commands matrix)))]
     (when-not (= 1 (:kotoba.cli.adapter-matrix/version matrix))
       (conj! problems {:type :matrix-version}))
     (when-not (= cmd-ids matrix-ids)
       (conj! problems {:type :command-id-mismatch
                        :only-contract (set/difference cmd-ids matrix-ids)
                        :only-matrix (set/difference matrix-ids cmd-ids)}))
     (doseq [[id entry] (:commands matrix)]
       (when-not (seq (:adapters entry))
         (conj! problems {:type :no-adapters :id id}))
       (doseq [a (:adapters entry)]
         (when-not (contains? (:hosts matrix) (:host a))
           (conj! problems {:type :unknown-host :id id :host (:host a)}))
         (when-not (#{:implemented :partial :contract-only} (:status a))
           (conj! problems {:type :bad-status :id id :status (:status a)}))))
     (let [ps (persistent! problems)]
       {:ok? (empty? ps) :problems ps
        :command-count (count cmd-ids)
        :implemented-check?
        (boolean (some #(and (= :compiler-cli (:host %))
                             (= :implemented (:status %)))
                       (get-in matrix [:commands :check :adapters])))}))))

#?(:clj
   (defn -main [& _]
     (let [r (validate)]
       (println "cli-adapter-matrix:"
                (if (:ok? r) "ok" "FAIL")
                "commands" (:command-count r)
                "check-implemented?" (:implemented-check? r))
       (doseq [p (:problems r)] (println " " (pr-str p)))
       (System/exit (if (:ok? r) 0 1)))))

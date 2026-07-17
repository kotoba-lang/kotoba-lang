#!/usr/bin/env nbb
;; --- nbb shims (auto, ADR-2607173000) ---------------------------------
(def ^:private __fs (js/require "node:fs"))
(def ^:private __path (js/require "node:path"))
(def ^:private __cp (js/require "node:child_process"))
(def ^:private __os (js/require "node:os"))
(def ^:private __crypto (js/require "node:crypto"))
(defn- __sh [& args]
  (let [opts (when (map? (last args)) (last args))
        cmd (if opts (butlast args) args)
        r (.spawnSync __cp (first cmd) (to-array (rest cmd))
                      (clj->js (merge {:encoding "utf8"} (when opts {:cwd (:dir opts)}))))]
    {:exit (or (.-status r) 1) :out (or (.-stdout r) "") :err (or (.-stderr r) "")}))
(defn- __shell [& args]
  (let [opts (when (map? (first args)) (first args))
        cmd (if opts (rest args) args)
        r (.spawnSync __cp (first cmd) (to-array (rest cmd))
                      (clj->js (merge {:stdio "inherit" :encoding "utf8"}
                                      (when opts {:cwd (:dir opts)}))))]
    (when-not (zero? (or (.-status r) 1))
      (throw (js/Error. (str "shell failed: " (pr-str cmd)))))
    {:exit (or (.-status r) 0) :out "" :err ""}))
;; -----------------------------------------------------------------------

(ns check-cli-contract
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def required-commands #{:run :check :db :git :rad :deploy :hinshitsu})

;; lang/cli.edn is stored as Datomic/Datascript tx-data (see schema.edn /
;; scripts/edn-datomize.cljs `wrap-map-preserve-ns!`); every key was
;; already namespaced so the transform did not rename anything, it only
;; pr-str'd non-scalar values into blob strings. Reverse exactly that.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity [tx-data]
  (into {} (map (fn [[k v]] [k (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(defn fail! [& parts]
  (binding [*out* *err*]
    (println (str/join " " parts)))
  (.exit js/process 1))

(defn assert! [pred & parts]
  (when-not pred
    (apply fail! parts)))

(defn duplicates [xs]
  (->> xs
       frequencies
       (filter (fn [[_ n]] (> n 1)))
       (map first)
       set))

(defn check-option! [command-id option-types opt]
  (assert! (keyword? (:id opt)) command-id "option missing keyword :id:" (pr-str opt))
  (assert! (vector? (:flags opt)) command-id (:id opt) "must declare vector :flags")
  (assert! (seq (:flags opt)) command-id (:id opt) "must declare at least one flag")
  (doseq [flag (:flags opt)]
    (assert! (and (string? flag) (str/starts-with? flag "-"))
             command-id (:id opt) "invalid flag:" (pr-str flag)))
  (assert! (contains? option-types (:type opt))
           command-id (:id opt) "unknown option :type" (:type opt))
  (when (= :enum (:type opt))
    (assert! (and (vector? (:values opt)) (seq (:values opt)) (every? keyword? (:values opt)))
             command-id (:id opt) ":enum option requires non-empty keyword :values vector")))

(defn check-command! [tier-labels option-types command]
  (assert! (keyword? (:id command)) "command missing keyword :id:" (pr-str command))
  (assert! (contains? tier-labels (:tier command))
           (:id command) "uses unknown :tier" (:tier command))
  (assert! (string? (:summary command)) (:id command) "must declare string :summary")
  (assert! (vector? (:options command)) (:id command) "must declare vector :options")
  (assert! (empty? (duplicates (map :id (:options command))))
           (:id command) "has duplicate option ids")
  (assert! (empty? (duplicates (mapcat :flags (:options command))))
           (:id command) "has duplicate option flags")
  (when-let [subcommands (:subcommands command)]
    (assert! (and (vector? subcommands) (seq subcommands) (every? keyword? subcommands))
             (:id command) ":subcommands must be a non-empty keyword vector"))
  (doseq [pos (:positionals command)]
    (assert! (keyword? (:id pos)) (:id command) "positional missing keyword :id")
    (assert! (contains? option-types (:type pos))
             (:id command) (:id pos) "unknown positional :type" (:type pos)))
  (doseq [opt (:options command)]
    (check-option! (:id command) option-types opt)))

(defn -main [& [path]]
  (let [path (or path "lang/cli.edn")
        contract (reconstitute-entity (edn/read-string (slurp path)))
        version (:kotoba.cli.contract/version contract)
        tier-labels (:kotoba.cli.contract/tier-labels contract)
        option-types (:kotoba.cli.contract/option-types contract)
        commands (:kotoba.cli.contract/commands contract)
        command-ids (set (map :id commands))
        option-count (count (mapcat :options commands))]
    (assert! (pos-int? version) "contract must declare positive integer version")
    (assert! (map? tier-labels) "contract must declare :kotoba.cli.contract/tier-labels")
    (assert! (map? option-types) "contract must declare :kotoba.cli.contract/option-types")
    (assert! (vector? commands) "contract must declare vector :kotoba.cli.contract/commands")
    (assert! (= required-commands command-ids)
             "contract commands mismatch. expected"
             (pr-str required-commands)
             "got"
             (pr-str command-ids))
    (assert! (empty? (duplicates (map :id commands))) "contract has duplicate command ids")
    (doseq [command commands]
      (check-command! tier-labels option-types command))
    (println "ok" path "commands" (count commands) "options" option-count)))

(apply -main *command-line-args*)

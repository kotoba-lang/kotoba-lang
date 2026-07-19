#!/usr/bin/env bb
(ns check-q9-migration
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]))

(def contract (edn/read-string (slurp "lang/q9-migration.edn")))
(def inventory (edn/read-string (slurp "lang/q9-inventory.edn")))
(def extension-audit (edn/read-string (slurp "lang/q9-kotoba-extension-audit.edn")))
(def tranche (edn/read-string (slurp "lang/q9-wave1-tranche-1.edn")))
(def soak-evidence (edn/read-string (slurp "lang/q9-wave1-tranche-1-soak.edn")))
(def component-roles (edn/read-string (slurp "lang/component-role-model.edn")))
(def workspace (-> "../../.." fs/canonicalize str))

(defn fail! [message data]
  (binding [*out* *err*]
    (println "Q9 FAIL:" message (pr-str data)))
  (System/exit 1))

(defn lines [& args]
  (let [{:keys [exit out err]}
        @(process/process args {:dir workspace :out :string :err :string})]
    (when-not (zero? exit)
      (fail! "inventory command failed" {:args args :exit exit :err err}))
    (remove str/blank? (str/split-lines out))))

(defn lines-allow-empty [& args]
  (let [{:keys [exit out err]}
        @(process/process args {:dir workspace :out :string :err :string})]
    (when-not (contains? #{0 1} exit)
      (fail! "inventory command failed" {:args args :exit exit :err err}))
    (remove str/blank? (str/split-lines out))))

(def plan-pattern #"(?:^|/)(?:migration-plan\.ya?ml|[^/]*migration[^/]*\.edn)$")

(defn measure [org]
  (let [root (str "orgs/" org)
        cljc (lines "rg" "--files" root "-g" "*.cljc")
        repos (into #{} (keep #(some->> (str/split % #"/")
                                        (take 3) seq (str/join "/"))) cljc)
        records (filter #(re-find plan-pattern %)
                        (lines "rg" "--files" root))]
    {:files (count cljc)
     :repositories (count repos)
     :migration-records (count records)}))

(defn live-kotoba-paths [organizations]
  (set (mapcat (fn [org]
                 (lines-allow-empty "rg" "--files" (str "orgs/" org) "-g" "*.kotoba"))
               organizations)))

(defn dependency-closed? [waves authorized]
  (every? (fn [wave]
            (set/subset? (get waves wave #{}) authorized))
          authorized))

(let [expected (get-in contract [:live-inventory :organizations])
      measured (into {} (map (fn [org] [org (measure org)]) (keys expected)))
      totals (apply merge-with + (vals measured))
      expected-totals (get-in contract [:live-inventory :totals])
      inventory-paths (mapcat :baseline-paths (:repositories inventory))
      extension-paths (map :path (:entries extension-audit))
      live-extension-paths (live-kotoba-paths (keys expected))
      tranche-paths (mapcat :baseline-paths (:repositories tranche))
      tranche-repos (map :repository (:repositories tranche))
      inventory-path-set (set inventory-paths)
      collision-repos (->> (:entries extension-audit)
                            (filter #(= :legacy-schema-dsl-extension-collision
                                        (:classification %)))
                            (map #(str/join "/" (take 3 (str/split (:path %) #"/"))))
                            set)
      authorized (get-in contract [:current-decision :authorized-waves])]
  (when-not (= expected measured)
    (fail! "live inventory drift; refresh classification authority before migrating"
           {:expected expected :measured measured}))
  (when-not (= expected-totals totals)
    (fail! "inventory totals drift" {:expected expected-totals :measured totals}))
  (when-not (= (:repository-count inventory) (:repositories totals)
               (count (:repositories inventory)))
    (fail! "path inventory repository count drift"
           {:declared (:repository-count inventory)
            :live (:repositories totals)
            :entries (count (:repositories inventory))}))
  (when-not (= (:path-count inventory) (:files totals)
               (count inventory-paths) (count (set inventory-paths)))
    (fail! "path inventory is incomplete or contains duplicates"
           {:declared (:path-count inventory)
            :live (:files totals)
            :entries (count inventory-paths)
            :unique (count (set inventory-paths))}))
  (when-not (= (:path-count extension-audit)
               (count extension-paths) (count (set extension-paths)))
    (fail! "Kotoba extension audit is incomplete or contains duplicates"
           {:declared (:path-count extension-audit)
            :entries (count extension-paths)
            :unique (count (set extension-paths))}))
  (when-not (= (set extension-paths) live-extension-paths)
    (fail! "Kotoba extension audit drift; regenerate before migrating"
           {:added (set/difference live-extension-paths (set extension-paths))
            :removed (set/difference (set extension-paths) live-extension-paths)}))
  (when-not (= (get-in contract [:kotoba-extension-preflight :observed])
               (assoc (:counts extension-audit) :kotoba-paths (:path-count extension-audit)))
    (fail! "Kotoba extension classification drift"
           {:contract (get-in contract [:kotoba-extension-preflight :observed])
            :audit (assoc (:counts extension-audit)
                          :kotoba-paths (:path-count extension-audit))}))
  (when-not (<= (count tranche-repos)
                (get-in contract [:waves :wave-1 :admission :max-repositories-per-tranche]))
    (fail! "Wave 1 tranche exceeds repository limit" {:repositories tranche-repos}))
  (when-not (= (count tranche-paths) (count (set tranche-paths)))
    (fail! "tranche contains duplicate baseline paths" {:paths tranche-paths}))
  (when-not (set/subset? (set tranche-paths) inventory-path-set)
    (fail! "tranche names paths outside the frozen Q9 inventory"
           {:unknown (set/difference (set tranche-paths) inventory-path-set)}))
  (when (some collision-repos tranche-repos)
    (fail! "tranche still contains a bare .kotoba schema DSL collision"
           {:repositories (filterv collision-repos tranche-repos)}))
  (doseq [[old new] (:schema-path-mapping tranche)]
    (when (fs/exists? (str workspace "/" old))
      (fail! "legacy schema path still exists after remediation" {:path old}))
    (when-not (fs/exists? (str workspace "/" new))
      (fail! "remediated schema path is missing" {:path new})))
  (let [{:keys [repository source qualification tests consumer-cutover soak]}
        (:pilot tranche)]
    (doseq [path [source qualification tests]]
      (when-not (fs/exists? (str workspace "/" repository "/" path))
        (fail! "Q9 pilot evidence path is missing"
               {:repository repository :path path})))
    (when-not (false? consumer-cutover)
      (fail! "pilot consumer cutover is forbidden before soak" {}))
    (when-not (and (< (:green-ci-runs soak) (:required-ci-runs soak))
                   (< (:calendar-days soak) (:required-calendar-days soak)))
      (fail! "pilot soak status must be explicit and incomplete before cutover" {:soak soak})))
  (when-not (= (set tranche-repos) (set (map :repository (:pilots tranche))))
    (fail! "every tranche repository must have one pilot evidence row"
           {:tranche tranche-repos :pilots (mapv :repository (:pilots tranche))}))
  (when-not (= (set tranche-repos)
               (set (map :repository (:repositories soak-evidence))))
    (fail! "soak evidence must cover exactly the tranche repositories"
           {:tranche tranche-repos
            :soak (mapv :repository (:repositories soak-evidence))}))
  (when-not (and (= 3 (get-in soak-evidence [:requirements :distinct-runs-per-repository]))
                 (= 604800 (get-in soak-evidence [:requirements :minimum-soak-seconds]))
                 (true? (get-in soak-evidence [:requirements :same-qualification-artifacts])))
    (fail! "soak evidence weakens the fleet rollback policy"
           {:requirements (:requirements soak-evidence)}))
  (when-not (and (= "lang/component-role-model.edn"
                    (get-in contract [:component-migration-model :authority]))
                 (true? (get-in contract [:component-migration-model
                                          :kotoba-provider-components-allowed]))
                 (true? (get-in component-roles [:runtime-roles :relational]))
                 (true? (get-in component-roles [:capability-invariant
                                                  :same-language-provider-does-not-bypass-imports]))
                 (some #{:raw-syscall-binding}
                       (get-in component-roles
                               [:native-tcb :must-remain-outside-component-authority])))
    (fail! "component-role migration authority drift"
           {:contract (:component-migration-model contract)
            :model "lang/component-role-model.edn"}))
  (when-not (and (false? (get-in soak-evidence [:gate :ready]))
                 (false? (get-in soak-evidence [:gate :consumer-cutover-authorized])))
    (fail! "soak authority must remain closed until a superseding accepted decision"
           {:gate (:gate soak-evidence)}))
  (doseq [{:keys [repository dir status]} (:pilots tranche)
          path [(str "src/" dir "/page_limit.kotoba")
                "kotoba-qualification.edn"
                (str "test/" dir "/kotoba_qualification_test.clj")]]
    (when-not (= :pass status)
      (fail! "tranche pilot is not qualified" {:repository repository :status status}))
    (when-not (fs/exists? (str workspace "/" repository "/" path))
      (fail! "tranche pilot evidence path is missing"
             {:repository repository :path path})))
  (let [expected-pins (get-in tranche [:pilot-contract :standalone-ci :pins])
        dep->pin {'io.github.kotoba-lang/kotoba (:kotoba expected-pins)
                  'io.github.kotoba-lang/compiler (:compiler expected-pins)
                  'io.github.kotoba-lang/kotoba-lang (:kotoba-lang expected-pins)}]
    (doseq [{:keys [repository]} (:pilots tranche)]
      (let [deps (edn/read-string (slurp (str workspace "/" repository "/deps.edn")))
            extra (get-in deps [:aliases :test :extra-deps])]
        (doseq [[dep sha] dep->pin]
          (when-not (= {:git/url (str "https://github.com/kotoba-lang/"
                                     (name dep) ".git")
                        :git/sha sha}
                       (get extra dep))
            (fail! "pilot standalone CI dependency pin drift"
                   {:repository repository :dependency dep
                    :expected sha :actual (get extra dep)})))
        (when (some :local/root (vals extra))
          (fail! "pilot CI may not depend on a sibling local/root"
                 {:repository repository})))))
  (when-not (false? (get-in tranche [:gate :implementation-authorized]))
    (fail! "collision-blocked tranche must not authorize implementation" {}))
  (when-not (dependency-closed? (:wave-dependencies contract) authorized)
    (fail! "authorized waves are not dependency-closed" {:authorized authorized}))
  (when-not (true? (get-in contract [:rollback-policy :oracle-retained-until-soak]))
    (fail! "oracle retention must remain mandatory" {}))
  (when-not (true? (get-in contract [:rollback-policy :production-deploy-requires-separate-authority]))
    (fail! "Q9 must not implicitly authorize deployment" {}))
  (println "Q9 INVENTORY PASS:" (:files totals) "files in"
           (:repositories totals) "repositories;" (:migration-records totals)
           "migration records")
  (println "Q9 WAVE PASS: authorized waves are dependency-closed; fleet completion remains false")
  (println "Q9 ROLLBACK PASS: oracle retention, soak, stop conditions, and separate deploy authority are required"))

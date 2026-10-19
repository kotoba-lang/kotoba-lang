(ns kotobase.operations-qualification
  "Fail-closed operational-security readiness evaluation."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]))

(def policy-path "qualification/operations-policy.edn")
(defn read-policy [] (edn/read-string (slurp policy-path)))

(defn evaluate [policy evidence]
  (let [required (:required-evidence policy)
        present (set (for [[kind receipt] evidence :when (:evidence/passed? receipt)] kind))
        errors (cond-> []
                 (not (set/subset? required present))
                 (conj :operations/missing-evidence)
                 (not (true? (get-in evidence [:secret-scan :evidence/no-secrets?])))
                 (conj :operations/secret-scan)
                 (not (true? (get-in evidence [:rotation-drill :evidence/keys-rotated?])))
                 (conj :operations/rotation-drill)
                 (not (true? (get-in evidence [:incident-playbook :evidence/exercised?])))
                 (conj :operations/incident-exercise))]
    {:operations/ready? (empty? errors) :operations/errors errors
     :operations/present present}))

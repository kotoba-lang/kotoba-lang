(ns kotobase.recovery-qualification
  "Fail-closed disaster recovery evidence evaluation for Kotobase."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]))

(def policy-path "qualification/recovery-policy.edn")

(defn read-policy []
  (edn/read-string (slurp policy-path)))

(defn evaluate
  [policy {:keys [backups restore-receipt corruption-repair lost-key-exercise]}]
  (let [sites (set (map :backup/site backups))
        backup-ok? (every? #(and (:backup/encrypted? %)
                                 (:backup/immutable? %)
                                 (:backup/digest-verified? %)) backups)
        restore-ok? (and (= :passed (:restore-drill/status restore-receipt))
                         (:restore-drill/destructive? restore-receipt)
                         (:restore-drill/digest-verified? restore-receipt)
                         (>= (count (:restore-drill/sites restore-receipt))
                             (:minimum-regions policy))
                         (<= (:restore-drill/rto-ms restore-receipt)
                             (:rto-limit-ms policy))
                         (<= (:restore-drill/rpo-ms restore-receipt)
                             (:rpo-limit-ms policy)))
        repair-ok? (every? #(true? (get corruption-repair %))
                            [:detected? :isolated? :rebuilt?
                             :digest-verified?])
        lost-key-ok? (and (true? (:failed-closed? lost-key-exercise))
                           (true? (:recovery-path-exercised?
                                   lost-key-exercise)))
        errors (cond-> []
                 (< (count sites) (:minimum-regions policy))
                 (conj :recovery/regions)
                 (not backup-ok?) (conj :recovery/backups)
                 (not restore-ok?) (conj :recovery/restore)
                 (not repair-ok?) (conj :recovery/corruption-repair)
                 (not lost-key-ok?) (conj :recovery/lost-key))]
    {:recovery/ready? (empty? errors)
     :recovery/sites sites
     :recovery/errors errors
     :recovery/backup-ok? backup-ok?
     :recovery/restore-ok? restore-ok?
     :recovery/repair-ok? repair-ok?
     :recovery/lost-key-ok? lost-key-ok?}))

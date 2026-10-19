(ns murakumo.fleet-view
  "Fold a kotoba-fleet coordination Datom log into one operator view — the
  coordination-plane counterpart to `status` (the mesh-plane view). Reads an
  append-only `[e a v t]` datom vector (as written by `kotoba.fleet.store`'s
  `:datoms`, e.g. spat to EDN by an agent/governor node), hydrates a MemStore,
  and prints `kotoba.fleet.view/snapshot`: per-work holders, active leases,
  pending proposals."
  (:require [kotoba.lang.edn :as edn]
            [kotoba.fleet.store :as store]
            [kotoba.fleet.view :as view]))

(defn load-store
  "Hydrate a MemStore from an EDN datom-log file (a vector of [e a v t] tuples)."
  [path]
  (let [datoms (edn/read-string (slurp path))
        db     (store/mem-store)]
    (store/transact! db datoms)
    db))

(defn -main
  "Args: <datom-log.edn> [now-ms]. `now` defaults to wall-clock so lease TTLs are
  evaluated against real time."
  [& [path now-str]]
  (if-not path
    (do (println "usage: murakumo fleet <datom-log.edn> [now-ms]")
        (println "  renders kotoba.fleet.view/snapshot (per-work holders, leases, pending proposals)"))
    (let [now  (or (some-> now-str Long/parseLong) (System/currentTimeMillis))
          snap (view/snapshot (load-store path) now)]
      (println (format "fleet @ now=%d   datoms=%d   pending-proposals=%d"
                       (:now snap) (:datoms snap) (:pending snap)))
      (println (format "%-40s %-12s %s" "WORK" "HOLDER" "LEASES"))
      (if (empty? (:works snap))
        (println "  (no work-units on the log)")
        (doseq [w (:works snap)]
          (println (format "%-40s %-12s %d" (:work w) (or (:holder w) "-") (:leases w))))))))

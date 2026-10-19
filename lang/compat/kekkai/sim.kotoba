(ns kekkai.sim
  "Demo: drive a tailnet control plane through one CoordinationActor.

    ingest   register a node + log a heartbeat + publish ACL (observe → datoms)
    admit n-pending  clean device → TailnetGovernor passes → machine approval
                     (interrupt) → admin approves → node authorized
    admit n-rogue    unowned tag + expired key → HARD HOLD
                     (a human cannot approve past it)
    access n-laptop  deny-by-default netmap from the ACL → reachable peers
    route  r-exit    exit node → high-stakes → admin sign-off
    phase 0          admission in observe-only phase → held (phase-disabled)

  Run: clojure -M:dev:run"
  (:require [langgraph.graph :as g]
            [kekkai.store :as store]
            [kekkai.operation :as op]))

(defn- line [& xs] (println (apply str xs)))

(defn- drive [actor tid req phase approve?]
  (let [res (g/run* actor {:request req :context {:phase phase}} {:thread-id tid})]
    (if (= :interrupted (:status res))
      (do (line "   ⏸  machine approval — admin review (reason: "
                (-> res :state :audit last :reason) ")")
          (let [r2 (g/run* actor {:approval {:status (if approve? :approved :rejected)
                                             :by "admin-alice"}}
                           {:thread-id tid :resume? true})]
            (line "   ▶  " (if approve? "承認" "却下") " → " (get-in r2 [:state :disposition]))
            r2))
      (do (line "   → " (get-in res [:state :disposition])
                (when-let [pr (-> res :state :audit last :phase-reason)] (str " (" pr ")")))
          res))))

(defn -main [& _]
  (let [st    (store/seed-db)
        actor (op/build st)]

    (line "── ingest (observe → EAVT ground datoms) ──")
    (drive actor "i1" {:op :node/register :node "n-tablet"
                       :value {:id "n-tablet" :hostname "alice-ipad" :os "ipados"
                               :did "did:key:zTablet" :user "alice" :tags ["tag:laptop"]
                               :key-expiry (+ store/demo-now 7776000) :status "pending"}} 3 true)
    (drive actor "i2" {:op :node/heartbeat :node "n-server"
                       :value {:last-seen store/demo-now :endpoint "198.51.100.9:41641"}} 3 true)
    (line "  registered nodes: " (mapv :id (store/all-nodes st)))

    (line "\n── admit n-pending (clean device → machine approval) ──")
    (drive actor "a-ok" {:op :node/admit :node "n-pending"} 3 true)
    (line "  n-pending status: " (:status (store/node st "n-pending")))

    (line "\n── admit n-rogue (unowned tag + expired key) ──")
    (drive actor "a-bad" {:op :node/admit :node "n-rogue"} 3 true)
    (line "  n-rogue status: " (:status (store/node st "n-rogue")))

    (line "\n── access/assess n-laptop (deny-by-default netmap) ──")
    (let [res (drive actor "x-lap" {:op :access/assess :node "n-laptop"} 3 true)]
      (line "  netmap peers: " (-> res :state :proposal :peers)))

    (line "\n── route/approve r-exit (exit node → admin sign-off) ──")
    (drive actor "r-exit" {:op :route/approve :node "n-gw" :route "r-exit"} 3 true)

    (line "\n── 段階導入: admit を phase 0 (observe-only) で ──")
    (drive actor "a-p0" {:op :node/admit :node "n-pending"} 0 true)

    (line "\n── tailnet 系譜台帳 (append-only; zero-trust 監査証跡) ──")
    (doseq [f (store/ledger st)] (line "  " (store/ledger-line f)))

    (line "\n── バックエンド差し替え: DatomicStore でも同一契約 ──")
    (let [ds (store/datomic-seed-db) da (op/build ds)]
      (drive da "d1" {:op :access/assess :node "n-laptop"} 3 true)
      (line "  DatomicStore netmap n-laptop: "
            (:peers (store/assessment-of ds "n-laptop"))))
    (line "\ndone.")))

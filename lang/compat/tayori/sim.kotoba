(ns tayori.sim
  "Demo: drive email/document correspondence through one CorrespondenceActor.

    ingest       register a contact + thread + inbound message (observe → facts)
    draft t-status   known boss, clean → phase 3 auto-commits (a casual commit)
    send  t-status   sending is always high-stakes → human sign-off → mock-channel sends
    draft t-blocked  drafting doesn't gate on consent → commits
    send  t-blocked  consent-blocked contact → HARD HOLD (un-overridable)
    revise d-memo    phase 3 auto-commits → docport/propose-revision! (a real PR candidate)
    publish d-memo   always high-stakes → human sign-off → docport/publish!
    phase 0          draft in ingest-only phase → held (phase-disabled)

  Run: clojure -M:dev:run"
  (:require [langgraph.graph :as g]
            [tayori.store :as store]
            [tayori.channel :as channel]
            [tayori.docport :as docport]
            [tayori.operation :as op]))

(defn- line [& xs] (println (apply str xs)))

(defn- drive [actor tid req phase approve?]
  (let [res (g/run* actor {:request req :context {:phase phase}} {:thread-id tid})]
    (if (= :interrupted (:status res))
      (do (line "   ⏸  human sign-off — review (reason: "
                (-> res :state :audit last :reason) ")")
          (let [r2 (g/run* actor {:approval {:status (if approve? :approved :rejected)
                                             :by "alice"}}
                           {:thread-id tid :resume? true})]
            (line "   ▶  " (if approve? "承認" "却下") " → " (get-in r2 [:state :disposition]))
            r2))
      (do (line "   → " (get-in res [:state :disposition])
                (when-let [pr (-> res :state :audit last :phase-reason)] (str " (" pr ")")))
          res))))

(defn -main [& _]
  (let [st        (store/seed-db)
        sent      (atom [])
        published (atom {})
        ch        (channel/mock-channel sent)
        dp        (docport/mock-doctarget published)
        actor     (op/build st {:channel ch :docport dp})]

    (line "── ingest (observe → ground facts) ──")
    (drive actor "i1" {:op :contact/register :contact "c-vendor"
                       :value {:id "c-vendor" :channel :email :address "vendor@example.com"
                               :consent :known :first-contact? false}} 3 true)
    (drive actor "i2" {:op :thread/register :thread "t-vendor"
                       :value {:id "t-vendor" :channel :email :external-id "gm-9"
                               :participants ["c-vendor"] :tenant "alice" :status :open}} 3 true)
    (drive actor "i3" {:op :message/ingest :thread "t-vendor"
                       :value {:thread-id "t-vendor" :from "c-vendor" :ts store/demo-now
                               :direction :inbound :body "見積もりの件、いかがでしょうか"}} 3 true)
    (line "  registered threads: " (mapv :id (store/all-threads st)))

    (line "\n── draft t-status (known boss, clean → phase 3 auto-commit) ──")
    (drive actor "d-status" {:op :reply/draft :thread "t-status"} 3 true)
    (line "  draft status: " (:status (store/draft-of st "t-status")))

    (line "\n── send t-status (sending is always high-stakes → human sign-off) ──")
    (drive actor "s-status" {:op :reply/send :thread "t-status"} 3 true)
    (line "  draft status: " (:status (store/draft-of st "t-status")))
    (line "  sent (mock-channel): " @sent)

    (line "\n── draft t-blocked (drafting doesn't gate on consent) ──")
    (drive actor "d-blocked" {:op :reply/draft :thread "t-blocked"} 3 true)

    (line "\n── send t-blocked (consent-blocked contact → HARD HOLD) ──")
    (drive actor "s-blocked" {:op :reply/send :thread "t-blocked"} 3 true)
    (line "  draft status (unchanged): " (:status (store/draft-of st "t-blocked")))

    (line "\n── revise d-memo (phase 3 auto-commit → real PR candidate) ──")
    (drive actor "r-memo" {:op :document/revise :document "d-memo"} 3 true)
    (line "  revision status/branch: " (select-keys (store/revision-of st "d-memo") [:status :branch]))

    (line "\n── publish d-memo (always high-stakes → human sign-off) ──")
    (drive actor "p-memo" {:op :document/publish :document "d-memo" :target "memos/status.md"} 3 true)
    (line "  published (mock-doctarget): " @published)

    (line "\n── 段階導入: draft を phase 0 (ingest-only) で ──")
    (drive actor "d-p0" {:op :reply/draft :thread "t-status"} 0 true)

    (line "\n── 通信文下書き監査台帳 (append-only) ──")
    (doseq [f (store/ledger st)] (line "  " (store/ledger-line f)))

    (line "\n── バックエンド差し替え: DatomicStore でも同一契約 ──")
    (let [ds (store/datomic-seed-db) da (op/build ds {:channel ch :docport dp})]
      (drive da "d1" {:op :reply/draft :thread "t-status"} 3 true)
      (line "  DatomicStore draft t-status: " (:status (store/draft-of ds "t-status"))))
    (line "\ndone.")))

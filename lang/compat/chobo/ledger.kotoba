(ns chobo.ledger
  "Audit-ledger substrate — the EAVT (activity/artifact/relation/decision/effect)
  model extracted from itonami. Portable .cljc, zero host effects.

  This is the common substrate both mise (retail order → activity) and itonami
  (services operation → activity) project onto. A Ledger is an append-only log
  of EAVT records. ILedgerStore is an injected port (mock in-memory); Datomic/
  D1/kotoba-server adapters are follow-ups.

  Status of an activity: :open → :done | :cancelled. Effects: :proposed →
  :approved → :applied | :rejected (destructive/financial effects require
  approval). The single invariant: the ledger is append-only — records are
  added, never mutated; state changes are new records (decision/effect)."
  (:refer-clojure :exclude [type]))

;; ---------------------------------------------------------------------------
;; records (EAVT quintuple)
;; ---------------------------------------------------------------------------

(defrecord Activity [id lane kind title state source source-id repo tenant actor artifacts parent created-at due-at])
(defrecord Artifact [id type title source source-id content-cid props])
(defrecord Relation [id type from to source props])
(defrecord Decision [id activity policy status decider decided-at note])
(defrecord Effect  [id activity kind risk status tool payload repo])

;; ---------------------------------------------------------------------------
;; ledger (append-only log)
;; ---------------------------------------------------------------------------

(defrecord Ledger [activities artifacts relations decisions effects])

(defn ledger [] (->Ledger [] [] [] [] []))

(defn- conj-log [log key record]
  (update log key conj (merge {:id (or (:id record) (str (gensym "rec_")))} record)))

(defn append-activity  [l a] (conj-log l :activities  a))
(defn append-artifact  [l a] (conj-log l :artifacts  a))
(defn append-relation  [l r] (conj-log l :relations  r))
(defn append-decision [l d] (conj-log l :decisions d))
(defn append-effect   [l e] (conj-log l :effects    e))

;; ---------------------------------------------------------------------------
;; activity status transitions
;; ---------------------------------------------------------------------------

(def activity-states #{:open :done :cancelled})
(def activity-transitions {:open #{:done :cancelled} :done #{} :cancelled #{}})

(defn activity [m]
  (merge {:state :open} (select-keys m [:id :lane :kind :title :source :source-id
                                        :repo :tenant :actor :artifacts :parent
                                        :created-at :due-at]) m))

(defn can-transition? [from to] (contains? (get activity-transitions from #{}) to))

(defn transition-activity
  "Return a new Activity with state = to, or nil if the transition is invalid."
  [a to]
  (when (can-transition? (:state a :open) to)
    (assoc a :state to)))

;; ---------------------------------------------------------------------------
;; effect lifecycle
;; ---------------------------------------------------------------------------

(def effect-states #{:proposed :approved :applied :rejected})
(def effect-transitions
  {:proposed #{:approved :rejected} :approved #{:applied :rejected}
   :applied #{} :rejected #{}})

(defn effect [m]
  (merge {:status :proposed :risk :low} (select-keys m [:id :activity :kind :tool :payload :repo]) m))

(defn can-approve-effect? [e] (contains? #{:proposed :approved} (:status e :proposed)))
(defn approve-effect [e] (when (= (:status e :proposed) :proposed) (assoc e :status :approved)))
(defn apply-effect [e] (when (= (:status e :approved) :approved) (assoc e :status :applied)))
(defn reject-effect [e] (when (contains? #{:proposed :approved} (:status e)) (assoc e :status :rejected)))

;; ---------------------------------------------------------------------------
;; queries
;; ---------------------------------------------------------------------------

(defn activities-by-lane [l lane] (filterv #(= (:lane %) lane) (:activities l)))
(defn activities-by-repo [l repo] (filterv #(= (:repo %) repo) (:activities l)))
(defn activities-by-tenant [l tenant] (filterv #(= (:tenant %) tenant) (:activities l)))
(defn effects-for-activity [l act-id] (filterv #(= (:activity %) act-id) (:effects l)))
(defn decisions-for-activity [l act-id] (filterv #(= (:activity %) act-id) (:decisions l)))
(defn open-activities [l] (filterv #(#{:open} (:state % :open)) (:activities l)))

;; ---------------------------------------------------------------------------
;; ILedgerStore port (injected)
;; ---------------------------------------------------------------------------

(defprotocol ILedgerStore
  (put-record! [this record] "Append a record; returns it (with id if absent).")
  (get-activity [this id])
  (list-activities [this] "All activities, newest-first where applicable.")
  (list-effects [this]))

(defrecord MockLedgerStore [state]
  ILedgerStore
  (put-record! [_ r]
    (let [r (if (:id r) r (assoc r :id (str "rec_" (hash r))))]
      (swap! state update :records conj r)
      r))
  (get-activity [s id]
    (some #(when (and (:lane %) (= (:id %) id)) %) (:records @state)))
  (list-activities [_] (filterv :lane (:records @state)))
  (list-effects [_] (filterv :activity (:records @state))))

(defn mock-ledger-store [] (->MockLedgerStore (atom {:records []})))

(ns shinkansen.bridge
  "Connect shitsuke's re-frame guest to shinkansen's content-addressed state
  chain (ADR-2609131808 D3).

  The guest (shitsuke/kotoba/reframe_core.kotoba compiled by amu, wrapped by
  shitsuke.kotoba.guest) answers `step`: db + event -> next db, as EDN text
  across the boundary. shinkansen.state addresses each resulting db VALUE.
  This namespace is the loop between the two:

    dispatch  chain guest event
      -> new db (guest computed)
      -> chain-entry (content address over the db's EDN text)
      -> chain appended

  The hash function and persistence are injected, as everywhere in
  shinkansen: this namespace is pure modulo the guest call and hashing."
  (:require [cljs.reader :as edn]
            [shinkansen.state :as state]))

(defn- guest-init-db [guest]
  (let [call (:call guest)]
    (edn/read-string (call "init-text" []))))

(defn make
  "Wrap a shitsuke guest (`shitsuke.kotoba.guest/guest` shape: a map with
  :call fn) into an app handle with a verified chain.

    {:guest guest            the shitsuke guest
     :cid-fn f               EDN text -> CID string (injected)
     :chain (atom [])        head-last entries
     :height (atom 0)}

  The chain starts EMPTY: the head db is the guest's own init until the
  first dispatch. `current-db` falls back to init so an app is usable
  before any dispatch."
  [guest cid-fn]
  {:guest guest
   :cid-fn cid-fn
   :chain (atom [])
   :height (atom 0)
   :init-db (guest-init-db guest)})

(defn current-db
  "The latest db value: the guest's answer at the current chain head."
  [{:keys [guest chain cid-fn]}]
  (if-let [head (last @chain)]
    (:db head)
    (guest-init-db guest)))

(defn dispatch
  "One app step: guest step -> new db -> chain entry. Returns the entry.
  Fail-closed: the entry is verified before it is appended, and a
  verification failure throws (a chain that stores entries it does not
  trust is not a chain)."
  [{:keys [guest chain height cid-fn] :as app} event]
  (let [db (current-db app)
        next-db (let [call (:call guest)]
                  (clojure.edn/read-string
                   (apply call "step-text" [(pr-str db) (pr-str event)])))
        prev-cid (:db-cid (last @chain))
        h (swap! height inc)
        entry (state/chain-entry {:prev-cid prev-cid :event event
                                  :db next-db :height h :cid-fn cid-fn})
        v (state/verify-entry entry cid-fn)]
    (if (:valid? v)
      (do (swap! chain conj entry) entry)
      (throw (ex-info "chain entry failed verification" v)))))

(defn chain
  "The verified chain, head last."
  [app] @(:chain app))

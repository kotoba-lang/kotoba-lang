(ns shinkansen.state
  "Content-addressed state: a re-frame db value becomes a CID, and a chain
  of dispatches becomes a chain of CIDs.

  This is the Unison-shaped half of shinkansen (ADR-2609131808 D3): a
  state transition's identity IS the content of the resulting db value.
  `step` takes the previous db (any EDN value), an event, the guest's
  `step` function (from shitsuke's `reframe_core.kotoba` via
  `shitsuke.kotoba.guest`), and answers a chain entry:

    {:db       <new db value>            the value the guest computed
     :db-cid   \"bafkrei…\"                its content address (raw CIDv1)
     :prev     <previous entry's CID>     nil at the chain head
     :event    <the event, verbatim>      what was dispatched
     :height   3                          chain length including this entry}

  The db value is serialized as EDN (the same text boundary shitsuke's
  guest bridge already uses — pr-str/read-string, keywords and i64s
  intact, deliberately not JSON) and hashed through the workspace's
  content-address library. The CID is computed OVER THE EDN BYTES, so
  two different paths that produce the same db value land on the same
  CID — that is the point.

  Nothing here writes to a store: the caller (Worker, browser host, test)
  owns persistence. This namespace is pure modulo hashing."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:const genesis-prev nil)

(defn- edn-text [v]
  (binding [*print-readably* true
            *print-namespace-maps* false]
    (pr-str v)))

(defn db-text
  "The EDN text whose hash IS the db's content address."
  [db]
  (edn-text db))

(defn chain-entry
  "Build one chain entry. `prev-cid` is the previous entry's CID, or nil
  at the head. `cid-fn` is (fn [edn-text] -> cid-string), injected so the
  hashing engine (workspace content-address lib, or the guest's
  :hash/sha256 capability through kbb) is the caller's choice."
  [{:keys [prev-cid event db height cid-fn]}]
  (let [text (db-text db)
        cid (cid-fn text)]
    {:db db
     :db-cid cid
     :prev prev-cid
     :event event
     :height (or height 0)
     :text text}))

(defn verify-entry
  "A chain entry's claim is checked against its own text: the recorded
  :db-cid must equal cid-fn over the entry's :text. A mismatched entry is
  a corrupted chain, not a valid state — fail closed with the reason."
  [{:keys [db-cid text] :as entry} cid-fn]
  (let [computed (cid-fn text)]
    (if (= computed db-cid)
      {:valid? true :entry entry}
      {:valid? false :reason "entry CID does not match its text"
       :recorded db-cid :computed computed})))

(defn walk-chain
  "Verify every entry in order (head first). Answers
  {:valid? bool :failed-at height :reason …}. An empty chain is valid —
  and says so explicitly rather than silently."
  [entries cid-fn]
  (if (empty? entries)
    {:valid? true :empty true :checked 0}
    (loop [es (vec entries) i 0]
      (if (>= i (count es))
        {:valid? true :checked i}
        (let [v (verify-entry (nth es i) cid-fn)]
          (if (:valid? v)
            (recur es (inc i))
            {:valid? false :failed-at (get-in es [i :height] i)
             :reason (:reason v)}))))))

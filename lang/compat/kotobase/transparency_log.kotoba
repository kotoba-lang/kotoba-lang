(ns kotobase.transparency-log
  "Key-epoch-aware transparency checkpoints and receipt retention."
  (:require [clojure.set :as set])
  (:import [java.math BigInteger]
           [java.security MessageDigest]))

(def genesis (apply str (repeat 64 "0")))

(defn digest [value]
  (format "%064x"
          (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA-256")
                                  (.getBytes (pr-str value) "UTF-8")))))

(defn append-leaf [state receipt]
  (let [index (count (:leaves state))
        leaf {:index index :receipt-cid (:receipt-cid receipt)
              :execution-identity-cid (:execution-identity-cid receipt)}
        root (digest {:previous-root (or (:root state) genesis)
                      :leaf leaf})]
    {:leaves (conj (vec (:leaves state)) leaf) :root root}))

(defn checkpoint
  [state {:keys [key-id key-epoch previous-checkpoint-cid issued-at signatures]}]
  {:format :kotobase.transparency/checkpoint-v1
   :tree-size (count (:leaves state)) :root (:root state)
   :key-id key-id :key-epoch key-epoch :issued-at issued-at
   :previous-checkpoint-cid previous-checkpoint-cid
   :signatures signatures})

(defn verify-checkpoint
  [policy key-schedule previous checkpoint verify-signature]
  (let [key (get key-schedule (:key-id checkpoint))
        witnesses (set (keys (:signatures checkpoint)))
        required (set (:required-witnesses policy))
        body (dissoc checkpoint :signatures)
        errors (cond-> []
                 (not= :kotobase.transparency/checkpoint-v1 (:format checkpoint))
                 (conj :transparency/format)
                 (not (and key
                           (= (:epoch key) (:key-epoch checkpoint))
                           (<= (:valid-from key) (:issued-at checkpoint))
                           (< (:issued-at checkpoint) (:valid-until key))))
                 (conj :transparency/key-epoch)
                 (not= (:previous-checkpoint-cid checkpoint)
                       (when previous (digest previous)))
                 (conj :transparency/checkpoint-chain)
                 (not (set/subset? required witnesses))
                 (conj :transparency/witness-threshold)
                 (not-every? (fn [[witness signature]]
                               (verify-signature witness body signature))
                             (:signatures checkpoint))
                 (conj :transparency/signature)
                 (and previous
                      (or (< (:tree-size checkpoint) (:tree-size previous))
                          (= (:root checkpoint) (:root previous))))
                 (conj :transparency/rollback))]
    {:transparency/valid? (empty? errors)
     :transparency/errors errors
     :transparency/checkpoint-cid (digest checkpoint)}))

(defn verify-rotation
  "Require a monotonic epoch transition cross-signed by both the retiring and
  incoming checkpoint keys. This prevents either key alone from rewriting the
  key schedule."
  [previous-key next-key rotation verify-signature]
  (let [body (dissoc rotation :signatures)
        signatures (:signatures rotation)
        errors (cond-> []
                 (not= (inc (:epoch previous-key)) (:epoch next-key))
                 (conj :rotation/non-monotonic-epoch)
                 (not= (:from-key-id rotation) (:id previous-key))
                 (conj :rotation/previous-key)
                 (not= (:to-key-id rotation) (:id next-key))
                 (conj :rotation/next-key)
                 (< (:effective-at rotation) (:valid-from next-key))
                 (conj :rotation/not-yet-valid)
                 (not (verify-signature (:id previous-key) body
                                        (get signatures (:id previous-key))))
                 (conj :rotation/previous-signature)
                 (not (verify-signature (:id next-key) body
                                        (get signatures (:id next-key))))
                 (conj :rotation/next-signature))]
    {:rotation/valid? (empty? errors) :rotation/errors errors
     :rotation/cid (digest rotation)}))

(defn retention-decision
  "Return receipt CIDs that may be deleted or crypto-shredded. Legal holds and
  the minimum class retention are fail-closed. Key retirement additionally
  requires a signed checkpoint strictly newer than every affected receipt."
  [policy now receipts legal-holds latest-checkpoint]
  (reduce
   (fn [decision receipt]
     (let [cid (:receipt-cid receipt)
           class (:retention-class receipt)
           minimum (get-in policy [:classes class :minimum-ms])
           expires (+ (:issued-at receipt) (or minimum Long/MAX_VALUE))
           held? (contains? legal-holds cid)
           checkpoint-newer? (> (:issued-at latest-checkpoint 0)
                                (:issued-at receipt))]
       (cond
         held? (update decision :retain conj cid)
         (or (nil? minimum) (< now expires)) (update decision :retain conj cid)
         (and (:encrypted? receipt) checkpoint-newer?)
         (update decision :crypto-shred conj cid)
         checkpoint-newer? (update decision :delete conj cid)
         :else (update decision :retain conj cid))))
   {:retain #{} :delete #{} :crypto-shred #{}} receipts))

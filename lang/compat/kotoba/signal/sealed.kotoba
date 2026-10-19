;; kotoba.signal.sealed — JVM consumer that runs the real X3DH + Double
;; Ratchet and emits kotoba.protocol.sealed maps (ADR-2608161600).
;;
;; Protocol is declaration only (no crypto, no sockets). Envelope object
;; wrap is CLJS/Web Crypto and is not called here. This ns:
;;   * publishes a public prekey bundle (IPNS value, not confidential)
;;   * opens a session (X3DH then ratchet/init-sender|init-receiver)
;;   * encrypts an EDN body so file keys live INSIDE the Signal plaintext
;;   * stores the session ciphertext under an object CID (composition)
;;   * appends that CID to a mailbox whose IPNS head is naming, not encryption
;;
;; JVM consumer. The CLJS sibling is sealed.cljs (Promise-shaped ratchet).
;; PQXDH is a named gap (X3DH today; a missing PQ prekey is absence, not a
;; hybrid upgrade).
;;
;; Outer attachment :wrapped-key is the sentinel "in-session". The real
;; file key must not appear on the content-protocol object — the mailbox
;; can read that object.
(ns kotoba.signal.sealed
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kotoba.protocol.sealed :as sealed]
            [kotoba.signal.ratchet :as ratchet]
            [kotoba.signal.x3dh :as x3dh])
  (:import (java.util Base64)))

(def in-session-wrap "in-session")

(def ^:private b64-enc (Base64/getUrlEncoder))
(def ^:private b64-dec (Base64/getUrlDecoder))

(defn- b64 ^String [^bytes bs]
  (.encodeToString b64-enc bs))

(defn- unb64 ^bytes [^String s]
  (.decode b64-dec s))

(defn- err [e]
  {:error e})

(defn bundle->public
  "X3DH bundle (byte arrays) → sealed prekey-bundle (strings). Public."
  [bundle]
  (sealed/prekey-bundle
   {:identity-pub (b64 (:ik bundle))
    :signed-prekey (cond-> {:pub (b64 (:spk bundle))
                            :sign-pub (:sign-pub bundle)}
                     (some? (:opk bundle)) (assoc :opk (b64 (:opk bundle)))
                     (some? (:opk-id bundle)) (assoc :opk-id (:opk-id bundle)))
    :signature (b64 (:spk-sig bundle))}))

(defn public->x3dh
  "Inverse of bundle->public. Needed so Alice can X3DH against a fetched
   IPNS value without keeping the original byte arrays around."
  [public]
  (when (and (map? public) (not (:error public)))
    (let [spk (:signed-prekey public)]
      {:ik (unb64 (:identity-pub public))
       :sign-pub (:sign-pub spk)
       :spk (unb64 (:pub spk))
       :spk-sig (unb64 (:signature public))
       :opk (when (:opk spk) (unb64 (:opk spk)))
       :opk-id (:opk-id spk)})))

(defn check-roles
  "IPNS naming key, libp2p PeerID, and Signal identity must differ."
  [roles]
  (sealed/key-roles roles))

(defn publish
  "Bob publishes a public prekey bundle. Returns
     {:public sealed-prekey-bundle
      :remaining identity-with-one-opk-popped}

   `accept` must be called with the identity that still holds the issued
   OPK (the argument here, not :remaining). Popping is local directory
   bookkeeping — IPFS cannot enforce once-only fetch
   (sealed/opk-once-on-content-addressed? is false).

   Optional `roles` runs sealed/key-roles first."
  ([identity] (publish identity nil))
  ([identity roles]
   (if-let [role-err (when roles
                       (let [checked (check-roles roles)]
                         (when (:error checked) checked)))]
     role-err
     (let [[bundle remaining] (x3dh/publish-bundle identity)]
       (if-not (x3dh/verify-bundle bundle)
         (err :unverified-bundle)
         (let [public (bundle->public bundle)]
           (if (:error public)
             public
             {:public public :remaining remaining})))))))

(defn initiate
  "Alice verifies Bob's public bundle, X3DHs, and becomes the ratchet
   sender. Handshake material (IK, EK, OPK id) is carried on the first
   sealed header so Bob can x3dh-respond without a side channel."
  ([alice-identity public] (initiate alice-identity public nil))
  ([alice-identity public roles]
   (if-let [role-err (when roles
                       (let [checked (check-roles roles)]
                         (when (:error checked) checked)))]
     role-err
     (let [raw (public->x3dh public)]
       (cond
         (nil? raw) (err :invalid-bundle)
         (not (x3dh/verify-bundle raw)) (err :unverified-bundle)
         :else
         (let [{:keys [shared-secret ek-pub opk-id]} (x3dh/x3dh-initiate alice-identity raw)]
           {:ok? true
            :role :initiator
            :identity alice-identity
            :ratchet (ratchet/init-sender shared-secret (:spk raw))
            :handshake {:ik-pub (:pub (:ik alice-identity))
                        :ek-pub ek-pub
                        :opk-id opk-id}}))))))

(defn- outer-attachment
  "Public descriptor. Never copies :file-key — that stays in the ratchet plaintext."
  [{:keys [cid size digest] :as a}]
  (if (contains? a :plaintext)
    (err :plaintext-in-attachment)
    (sealed/attachment (cond-> {:cid cid :wrapped-key in-session-wrap :alg :in-session}
                         (some? size) (assoc :size size)
                         (some? digest) (assoc :digest digest)))))

(defn- inner-body
  [{:keys [text attachments]}]
  {:text text
   :attachments (mapv #(select-keys % [:cid :file-key :size :digest :alg])
                      (or attachments []))})

(defn- wire-header
  [session env]
  (let [h (:header env)
        header {:dh-pub (b64 (:dh-pub h)) :n (:n h)}]
    (if-let [hs (:handshake session)]
      (cond-> (assoc header
                     :ik-pub (b64 (:ik-pub hs))
                     :ek-pub (b64 (:ek-pub hs)))
        (contains? hs :opk-id) (assoc :opk-id (:opk-id hs)))
      header)))

(defn- wire-ciphertext
  [env]
  (str (b64 (:iv env)) "." (b64 (:ciphertext env))))

(defn- unwire
  [msg]
  (let [h (:header msg)
        parts (str/split (:ciphertext msg) #"\." 2)]
    (when (and (= 2 (count parts)) (map? h) (:dh-pub h))
      {:header {:dh-pub (unb64 (:dh-pub h)) :n (:n h)}
       :iv (unb64 (nth parts 0))
       :ciphertext (unb64 (nth parts 1))})))

(defn encrypt
  "Returns [session' sealed-message]. File keys in `plaintext` attachments
   are written only into the ratchet plaintext. The outer message carries
   :wrapped-key \"in-session\"."
  [session plaintext]
  (cond
    (:error session) [session session]
    (not (:ratchet session)) [session (err :not-a-session)]
    (some #(contains? % :plaintext) (or (:attachments plaintext) []))
    [session (err :plaintext-in-attachment)]
    :else
    (try
      (let [outers (mapv outer-attachment (or (:attachments plaintext) []))]
        (if-let [att-err (first (filter :error outers))]
          [session att-err]
          (let [pt (.getBytes (pr-str (inner-body plaintext)) "UTF-8")
                [r' env] (ratchet/encrypt-message (:ratchet session) pt)
                msg (sealed/message {:construction :session
                                     :header (wire-header session env)
                                     :ciphertext (wire-ciphertext env)
                                     :attachments outers})
                session' (-> session
                             (assoc :ratchet r')
                             (dissoc :handshake))]
            [session' msg])))
      (catch clojure.lang.ExceptionInfo e
        (if (re-find #"cannot send before receiving" (.getMessage e))
          [session (err :cannot-send-before-receive)]
          (throw e))))))

(defn decrypt
  "Returns [session' plaintext-map]. Tamper throws AEADBadTagException
   (crypto failure, not a composition error)."
  [session msg]
  (cond
    (:error session) [session session]
    (:error msg) [session msg]
    (not (sealed/message? msg)) [session (err :not-a-sealed-message)]
    :else
    (let [env (unwire msg)]
      (if (nil? env)
        [session (err :invalid-wire)]
        (let [[r' pt] (ratchet/decrypt-message (:ratchet session) env)
              body (edn/read-string {:eof nil} (String. ^bytes pt "UTF-8"))]
          [(assoc session :ratchet r') body])))))

(defn accept
  "Bob reconstructs the X3DH secret from the first sealed header, becomes
   the ratchet receiver, and decrypts that first message.

   `bob-identity` must still hold the OPK named in the header. The
   `:remaining` identity from `publish` has already popped it."
  [bob-identity msg]
  (cond
    (:error msg) msg
    (not (sealed/message? msg)) (err :not-a-sealed-message)
    (not (string? (get-in msg [:header :ek-pub]))) (err :missing-handshake)
    :else
    (let [h (:header msg)
          sk (x3dh/x3dh-respond bob-identity
                                (unb64 (:ik-pub h))
                                (unb64 (:ek-pub h))
                                (:opk-id h))
          session {:ok? true
                   :role :responder
                   :identity bob-identity
                   :ratchet (ratchet/init-receiver sk (:spk bob-identity))}]
      (decrypt session msg))))

(defn store-msg
  "Put session ciphertext on the object plane. `hash-fn` is (fn [msg] cid).
   This ns does not hash — protocol forbids it, and neither does the
   ratchet."
  [msg hash-fn]
  (cond
    (:error msg) msg
    (not (ifn? hash-fn)) (err :hash-fn-required)
    :else (sealed/store msg (hash-fn msg))))

(defn put-in-mailbox
  "Append a stored-ciphertext CID to the mailbox and commit a new head
   through `head-fn` (fn [entries] cid)."
  [mb stored head-fn]
  (cond
    (:error mb) mb
    (:error stored) stored
    (not= :stored-ciphertext (:kind stored)) (err :not-stored-ciphertext)
    :else
    (-> mb
        (sealed/append (:cid stored))
        (sealed/commit-head head-fn))))

(defn publish-head
  "IPNS name → mailbox head. Naming, not encryption."
  [name head-cid]
  (sealed/publish-head name head-cid))

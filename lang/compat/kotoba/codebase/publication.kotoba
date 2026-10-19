(ns kotoba.codebase.publication
  "Signed namespace heads: who says this namespace advanced, and to what.

  Blocks need no authority -- a CID either matches its bytes or it does not, so
  anyone may serve them and nobody has to be trusted. A namespace HEAD is the
  opposite: it is the one mutable thing in the system, and `namespace demo is
  now at commit X` is a claim that means nothing without a signer.

  The trust model here is deliberately small, because the alternative is
  inventing a PKI:

  - a follower PINS a publisher DID the first time it follows a namespace, and
    afterwards refuses any record not signed by that key. Trust-on-first-use is
    an honest primitive -- it says exactly what it checked -- where a registry
    that vouches for keys would be a new root of trust wearing an index's
    clothes;
  - every record carries a monotonic sequence and links its predecessor, so a
    replayed or rolled-back head is rejected rather than applied. Without this,
    an attacker who cannot forge a signature can still re-serve an old signed
    record and quietly revert a follower;
  - a record is only accepted once its commit is present LOCALLY and verified,
    so a signature can never make a follower point at bytes it does not have;
  - two DIFFERENT heads at the same sequence are reported as what they are: a
    fork. Rejecting them as `not advanced` — which is what this did until
    2026-08-05 — throws away the only observation in the whole protocol that
    accuses the PUBLISHER rather than the transport. A re-served identical
    record is a duplicate and means nothing; a different head at a sequence
    already signed means the pinned key signed two histories, and no amount of
    signature checking can make that benign. See `forked?`.

  Revocation is `retire!`: the pin is removed and re-following requires an
  explicit new DID. There is no revocation broadcast, because there is nobody
  to broadcast to that a follower is obliged to believe."
  (:require [cbor.core :as cbor]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [ed25519.core :as ed]
            [kotoba.codebase.ir :as ir]
            [kotoba.codebase.semantic-code :as semantic]
            [kotoba.codebase.store :as store])
  (:import [java.nio.charset StandardCharsets]
           [java.nio.file Files StandardCopyOption]
           [java.util Base64]))

(def schema "kotoba.namespace-head.v1")

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn- encode-base64 [^bytes bytes]
  (.encodeToString (Base64/getEncoder) bytes))

(defn- decode-base64 ^bytes [^String text]
  (.decode (Base64/getDecoder) text))

;; ---------------------------------------------------------------------------
;; Records

(defn payload
  "The part of a head record that is signed.

  The signature is not inside what it signs, so verification is `strip,
  re-encode, check` rather than a second serialization format that has to agree
  with the first."
  [{:keys [namespace head release sequence previous publisher]}]
  (when-not (and (string? namespace) (seq namespace)
                 (string? head) (string? publisher)
                 (integer? sequence) (not (neg? sequence)))
    (fail! :publication/invalid-record {:namespace namespace :sequence sequence}))
  (cond-> {"schema" schema
           "version" 1
           "namespace" namespace
           "head" (semantic/cid-link head)
           "sequence" sequence
           "publisher" publisher}
    previous (assoc "previous" (semantic/cid-link previous))
    release (assoc "release" (semantic/cid-link release))))

(defn sign-record
  "Sign a head record with a raw 32-byte Ed25519 seed.

  The publisher DID is DERIVED from the seed rather than taken as an argument:
  a record whose stated publisher is not the key that signed it would verify
  under one DID and be attributed to another."
  [^bytes seed record]
  (let [publisher (ed/did-key-from-seed seed)
        body (payload (assoc record :publisher publisher))
        signature (ed/sign seed (cbor/encode body))]
    (assoc body "signature" (encode-base64 signature))))

(defn verify-record
  "Check a record's signature against the publisher it names.

  Returns the decoded record on success and fails closed otherwise."
  [record]
  (when-not (and (map? record) (= schema (get record "schema")))
    (fail! :publication/not-a-head-record {}))
  (let [signature (get record "signature")
        publisher (get record "publisher")
        body (dissoc record "signature")]
    (when-not (and (string? signature) (string? publisher))
      (fail! :publication/record-incomplete {}))
    (when-not (try (ed/verify-did publisher (cbor/encode body) (decode-base64 signature))
                   (catch Exception _ false))
      (fail! :publication/signature-invalid {:publisher publisher}))
    {:namespace (get record "namespace")
     :head (ir/link->cid (get record "head"))
     :release (when-let [link (get record "release")] (ir/link->cid link))
     :sequence (get record "sequence")
     :previous (when-let [link (get record "previous")] (ir/link->cid link))
     :publisher publisher
     :record record}))

(defn record-cid [record]
  (semantic/block-cid record))

(defn forked?
  "Do these two verified records claim the same sequence for different heads?

  This is the one thing a follower can observe that indicts the publisher. A
  sequence that fails to advance is ordinary: a mirror re-serving the record
  the follower already has looks exactly like that, and nothing is wrong. But
  the SAME sequence carrying a DIFFERENT head cannot be produced by a replay,
  a stale cache, or a lying host — the records are signed, so only the pinned
  key could have made both. Collapsing the two cases into one error, which is
  what `sequence-not-advanced` did alone, discards the evidence at exactly the
  moment it appears."
  [a b]
  (boolean (and a b
                (= (:sequence a) (:sequence b))
                (not= [(:head a) (:release a)]
                      [(:head b) (:release b)]))))

;; ---------------------------------------------------------------------------
;; Follow state
;;
;; Kept beside the store rather than inside the block directory: a pinned
;; publisher is this follower's decision, not a fact about the content, and
;; copying a store to another machine must not silently carry someone else's
;; trust decisions as if they were data.

(defn- follow-file [root namespace]
  (io/file root "follows"
           (str (.encodeToString (.withoutPadding (Base64/getUrlEncoder))
                                 (.getBytes ^String namespace StandardCharsets/UTF_8))
                ".edn")))

(defn following
  "The pinned publisher and last accepted sequence for NAMESPACE, or nil."
  [root namespace]
  (let [target (follow-file root namespace)]
    (when (.isFile target)
      (edn/read-string (slurp target)))))

(defn- write-follow! [root namespace state]
  (let [target (follow-file root namespace)]
    (.mkdirs (.getParentFile target))
    (let [tmp (Files/createTempFile (.toPath (.getParentFile target)) "follow-" ".tmp"
                                    (make-array java.nio.file.attribute.FileAttribute 0))]
      (try
        (Files/write tmp (.getBytes (pr-str state) StandardCharsets/UTF_8)
                     (make-array java.nio.file.OpenOption 0))
        (Files/move tmp (.toPath target)
                    (into-array StandardCopyOption [StandardCopyOption/ATOMIC_MOVE
                                                    StandardCopyOption/REPLACE_EXISTING]))
        (finally (Files/deleteIfExists tmp))))
    state))

(defn retire!
  "Stop following NAMESPACE. Re-following requires naming a publisher again."
  [root namespace]
  (let [target (follow-file root namespace)]
    (when (.isFile target) (.delete target))
    {:namespace namespace :following? false}))

;; ---------------------------------------------------------------------------
;; Publishing and accepting

(defn publish!
  "Sign NAMESPACE's current head and return the record to distribute.

  Nothing is sent anywhere: transport belongs to a host adapter, and a record
  is a value that can be handed over by any means without changing what it
  proves."
  ([root namespace ^bytes seed] (publish! root namespace seed {}))
  ([root namespace ^bytes seed {:keys [release-cid]}]
   (let [head (or (store/head root namespace)
                  (fail! :publication/no-head {:namespace namespace}))
         _ (when release-cid (store/get-block root release-cid))
         previous (following root namespace)
         sequence (inc (long (or (:sequence previous) -1)))
         record (sign-record seed {:namespace namespace :head head
                                   :release release-cid
                                   :sequence sequence
                                   :previous (:record-cid previous)})
         cid (record-cid record)]
     (write-follow! root namespace
                    {:publisher (get record "publisher")
                     :sequence sequence
                     :head head
                     :release release-cid
                     :record-cid cid})
     {:namespace namespace :head head :release release-cid :sequence sequence
      :publisher (get record "publisher")
      :record-cid cid :record record})))

(defn accept-head!
  "Verify RECORD and advance the local head for its namespace.

  `:publisher` is required the first time a namespace is followed and refused
  afterwards if it disagrees with the pin -- a follower that silently accepted
  a new key would have no trust model at all, only a habit."
  ([root record] (accept-head! root record {}))
  ([root record {:keys [publisher]}]
   (let [verified (verify-record record)
         namespace (:namespace verified)
         current (following root namespace)
         pinned (or (:publisher current) publisher)]
     (when-not pinned
       (fail! :publication/publisher-required
              {:namespace namespace
               :hint "first follow must name the publisher DID it is pinning"}))
     (when-not (= pinned (:publisher verified))
       (fail! :publication/publisher-mismatch
              {:namespace namespace :pinned pinned :offered (:publisher verified)}))
     (when (forked? current verified)
       ;; Reported before the sequence check, and separately from it: this is
       ;; not a stale offer, it is proof the pinned key signed two histories.
       ;; The follower keeps the head it already had -- there is no rule for
       ;; choosing between two things the publisher equally asserted.
       (fail! :publication/forked-head
              {:namespace namespace :publisher (:publisher verified)
               :sequence (:sequence verified)
               :held (:head current) :offered (:head verified)
               :held-record-cid (:record-cid current)
               :offered-record-cid (record-cid record)}))
     (when (and current (<= (:sequence verified) (long (:sequence current))))
       (fail! :publication/sequence-not-advanced
              {:namespace namespace :have (:sequence current)
               :offered (:sequence verified)}))
     (when (and current (not= (:record-cid current) (:previous verified)))
       (fail! :publication/broken-record-chain
              {:namespace namespace :expected (:record-cid current)
               :offered (:previous verified)}))
     ;; The commit must already be here. A signature says who is speaking; it
     ;; never conjures the bytes they are speaking about.
     (store/namespace-view root (:head verified))
     (when-let [release (:release verified)]
       (store/get-block root release))
     (let [expected (store/head root namespace)]
       (store/publish-head! root namespace (:head verified) expected (constantly true)))
     (write-follow! root namespace
                    {:publisher (:publisher verified)
                     :sequence (:sequence verified)
                    :head (:head verified)
                    :release (:release verified)
                     :record-cid (record-cid record)})
     {:namespace namespace :head (:head verified) :release (:release verified)
      :sequence (:sequence verified)
      :publisher (:publisher verified) :accepted? true})))

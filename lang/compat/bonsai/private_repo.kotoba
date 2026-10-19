(ns bonsai.private-repo
  "Provider-neutral confidential repository snapshots.

  A snapshot seals a complete Git bundle with `kotoba-lang/envelope`, addresses
  the randomized ciphertext with CIDv1(raw, sha2-256), and addresses the access
  descriptor with DAG-CBOR. The descriptor is recipient-scoped metadata; only
  the ciphertext CID is suitable for public IPNI discovery.

  Biscuit decides who may fetch/push/share/rotate/advertise. It does not decide
  which ref is canonical: signed `nekko.ref-event` policy remains that authority.
  Storage, HTTP, IPNI transport, clocks, keys, and token issuance are deliberately
  injected, so GitHub, Radicle, and git.kotobase.net are adapters rather than
  semantic dependencies."
  (:require [biscuit.authorizer :as biscuit]
            [bonsai.private-wire :as wire]
            [envelope.model :as envelope]
            [envelope.seal-jvm :as seal]
            [ipld.core :as ipld]
            [ipni.ad :as ipni-ad]
            [ipni.metadata :as ipni-metadata]
            [multiformats.core :as mf]))

(def version wire/version)
(def operations wire/operations)

(defn- byte-count [bytes]
  (alength ^bytes bytes))

(defn- same-bytes? [a b]
  (java.util.Arrays/equals ^bytes a ^bytes b))

(defn- kw-name [x]
  (when x (name x)))

(defn- recipient->wire [recipient]
  (cond-> {"id" (:recipient/id recipient)
           "kind" (kw-name (:recipient/kind recipient))
           "pub" (:recipient/pub recipient)
           "ephemeralPub" (:recipient/ephemeral-pub recipient)
           "iv" (:recipient/iv recipient)
           "wrapped" (:recipient/wrapped recipient)}
    (:recipient/kem recipient) (assoc "kem" (kw-name (:recipient/kem recipient)))
    (:recipient/pq-pub recipient) (assoc "pqPub" (:recipient/pq-pub recipient))
    (:recipient/pq-ct recipient) (assoc "pqCt" (:recipient/pq-ct recipient))))

(defn- wire->recipient [recipient]
  (cond-> {:recipient/id (get recipient "id")
           :recipient/kind (keyword (get recipient "kind"))
           :recipient/pub (get recipient "pub")
           :recipient/ephemeral-pub (get recipient "ephemeralPub")
           :recipient/iv (get recipient "iv")
           :recipient/wrapped (get recipient "wrapped")}
    (get recipient "kem") (assoc :recipient/kem (keyword (get recipient "kem")))
    (get recipient "pqPub") (assoc :recipient/pq-pub (get recipient "pqPub"))
    (get recipient "pqCt") (assoc :recipient/pq-ct (get recipient "pqCt"))))

(defn- envelope->wire [env]
  {"id" (:envelope/id env)
   "version" (:envelope/version env)
   "alg" (kw-name (:envelope/alg env))
   "kdf" (kw-name (:envelope/kdf env))
   "kem" (kw-name (:envelope/kem env))
   "chunkBytes" (:envelope/chunk-bytes env)
   "chunks" (:envelope/chunks env)
   "nonceEpoch" (:envelope/nonce-epoch env)
   "chunkEpochs" (into {} (map (fn [[k v]] [(str k) v]))
                         (:envelope/chunk-epochs env))
   "recipients" (mapv recipient->wire (:envelope/recipients env))})

(defn- wire->envelope [env]
  {:envelope/id (get env "id")
   :envelope/version (get env "version")
   :envelope/alg (keyword (get env "alg"))
   :envelope/kdf (keyword (get env "kdf"))
   :envelope/kem (keyword (get env "kem"))
   :envelope/chunk-bytes (get env "chunkBytes")
   :envelope/chunks (get env "chunks")
   :envelope/nonce-epoch (get env "nonceEpoch")
   :envelope/chunk-epochs (into {} (map (fn [[k v]] [(Long/parseLong k) v]))
                                (get env "chunkEpochs"))
   :envelope/recipients (mapv wire->recipient (get env "recipients"))})

(defn- assemble [rid epoch parent env ciphertext]
  (let [ciphertext-cid (str (mf/cidv1-raw ciphertext))
        descriptor (wire/descriptor-value {:rid rid :epoch epoch :parent parent
                                           :ciphertext-cid ciphertext-cid
                                           :envelope (envelope->wire env)})
        descriptor-bytes (ipld/encode descriptor)
        snapshot-cid (ipld/cid descriptor-bytes)]
    {:snapshot/version version
     :snapshot/rid rid
     :snapshot/epoch epoch
     :snapshot/parent parent
     :snapshot/cid snapshot-cid
     :snapshot/ciphertext-cid ciphertext-cid
     :snapshot/descriptor descriptor
     :snapshot/descriptor-bytes descriptor-bytes
     :snapshot/envelope env
     :snapshot/ciphertext ciphertext}))

(defn seal-snapshot
  "Seal one complete Git bundle. `recipients` are envelope recipient maps
  `{:id did :pub x25519-public-key}`. Returns ciphertext plus an IPLD
  descriptor; callers may persist both under their returned CIDs."
  [{:keys [rid epoch parent bundle-bytes recipients]
    :or {epoch 0}}]
  (when-not (wire/valid-rid? rid)
    (throw (ex-info "invalid repository id" {:reason :invalid-rid :rid rid})))
  (when-not (nat-int? epoch)
    (throw (ex-info "snapshot epoch must be a natural integer"
                    {:reason :invalid-epoch :epoch epoch})))
  (when-not (and bundle-bytes (pos? (byte-count bundle-bytes)))
    (throw (ex-info "a private snapshot requires non-empty bundle bytes"
                    {:reason :empty-bundle})))
  (when-not (seq recipients)
    (throw (ex-info "a private snapshot requires at least one recipient"
                    {:reason :no-recipients})))
  (let [{:keys [envelope chunks]}
        (seal/seal-object (str "bonsai:" rid) [bundle-bytes] recipients
                          {:envelope/nonce-epoch epoch
                           :envelope/chunk-epochs {0 epoch}})]
    (assemble rid epoch parent envelope (first chunks))))

(defn restore-snapshot
  "Rehydrate and verify a snapshot descriptor plus ciphertext bytes. Both CIDs
  are recomputed before any key is used."
  [descriptor-bytes ciphertext]
  (let [verified (wire/verify-snapshot descriptor-bytes ciphertext)
        descriptor (:snapshot/descriptor verified)
        rid (:snapshot/rid verified)
        epoch (:snapshot/epoch verified)
        parent (:snapshot/parent verified)
        env (wire->envelope (get descriptor "envelope"))]
    (when-not (envelope/valid? env)
      (throw (ex-info "invalid private snapshot descriptor"
                      {:reason :invalid-descriptor})))
    (let [restored (assemble rid epoch parent env ciphertext)]
      (when-not (same-bytes? descriptor-bytes (:snapshot/descriptor-bytes restored))
        (throw (ex-info "private snapshot descriptor is not canonical"
                        {:reason :noncanonical-descriptor})))
      restored)))

(defn open-snapshot
  "Verify and decrypt a snapshot for `recipient-id`. Returns the original Git
  bundle bytes."
  [snapshot recipient-id recipient-private-key]
  (let [verified (restore-snapshot (:snapshot/descriptor-bytes snapshot)
                                   (:snapshot/ciphertext snapshot))
        env (:snapshot/envelope verified)
        entry (seal/entry-for env recipient-id)]
    (when-not entry
      (throw (ex-info "recipient has no grant for private snapshot"
                      {:reason :recipient-not-granted :recipient recipient-id})))
    (first (seal/open-object env entry recipient-private-key
                             [(:snapshot/ciphertext verified)]))))

(defn share-snapshot
  "Add a recipient by re-wrapping the existing content key. Ciphertext stays
  byte-identical; the access descriptor becomes a child snapshot."
  [snapshot from-recipient-id from-private-key recipient]
  (let [snapshot (restore-snapshot (:snapshot/descriptor-bytes snapshot)
                                   (:snapshot/ciphertext snapshot))
        env (:snapshot/envelope snapshot)
        from-entry (seal/entry-for env from-recipient-id)]
    (when-not from-entry
      (throw (ex-info "sharing principal has no snapshot grant"
                      {:reason :recipient-not-granted :recipient from-recipient-id})))
    (assemble (:snapshot/rid snapshot)
              (:snapshot/epoch snapshot)
              (:snapshot/cid snapshot)
              (seal/share-with env from-entry from-private-key recipient)
              (:snapshot/ciphertext snapshot))))

(defn rotate-without
  "Meaningfully revoke `revoked-recipient-id`: open with an authorized key,
  mint a fresh content key, increment the epoch, and grant only `recipients`.
  Deleting a wrap without this rotation is intentionally not exposed."
  [snapshot opener-id opener-private-key revoked-recipient-id recipients]
  (when (some #(= revoked-recipient-id (:id %)) recipients)
    (throw (ex-info "revoked recipient is still in the next epoch"
                    {:reason :recipient-still-granted :recipient revoked-recipient-id})))
  (let [bundle (open-snapshot snapshot opener-id opener-private-key)
        rotated (seal-snapshot {:rid (:snapshot/rid snapshot)
                                :epoch (inc (:snapshot/epoch snapshot))
                                :parent (:snapshot/cid snapshot)
                                :bundle-bytes bundle
                                :recipients recipients})]
    (when (= (:snapshot/ciphertext-cid snapshot)
             (:snapshot/ciphertext-cid rotated))
      (throw (ex-info "content-key rotation reused ciphertext identity"
                      {:reason :rotation-did-not-change-ciphertext})))
    rotated))

(defn authorize
  "Verify a Biscuit and decide one exact repository operation. Tokens grant
  authority with a `right(rid, operation)` fact. The verifier contributes the
  current operation as context, so attenuated token checks still run."
  [token {:keys [rid operation root-public-key verify-fn]}]
  (if-not (and (wire/valid-rid? rid) (contains? operations operation))
    {:allowed? false :verified? false :reason :invalid-request}
    (biscuit/authorize
     token
     {:root-public-key root-public-key
      :verify-fn verify-fn
      :facts [['operation operation] ['repository rid]]
      :policies [{:kind :allow :body [['right rid operation]]}]})))

(defn private-advertisement
  "Build an IPNI advertisement for the randomized ciphertext only. `context-id`
  must be 16-64 opaque bytes supplied by the caller; deriving it from RID would
  leak repository identity through the public index."
  [snapshot {:keys [peer addrs context-id multihash]}]
  (let [snapshot (restore-snapshot (:snapshot/descriptor-bytes snapshot)
                                   (:snapshot/ciphertext snapshot))
        context-id (when (sequential? context-id) (vec context-id))]
    (cond
      (not (and context-id (<= 16 (count context-id) 64)
                (every? #(and (integer? %) (<= 0 % 255)) context-id)))
      {:error :opaque-context-id-required}

      (not (and (sequential? multihash) (seq multihash)))
      {:error :ciphertext-multihash-required}

      :else
      (ipni-ad/from-discover
       {:cid (:snapshot/ciphertext-cid snapshot) :peer peer :addrs addrs}
       {:context-id context-id
        :entries [multihash]
        :metadata (ipni-metadata/gateway-http-bytes)}))))

(defn ciphertext-unchanged?
  "True when sharing changed only the envelope descriptor, not stored bytes."
  [before after]
  (and (= (:snapshot/ciphertext-cid before) (:snapshot/ciphertext-cid after))
       (same-bytes? (:snapshot/ciphertext before) (:snapshot/ciphertext after))))

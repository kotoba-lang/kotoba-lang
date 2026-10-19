(ns kotoba.protocol.sealed
  "Client-held confidentiality over IPLD/IPNS (ADR-2608161600).

  IPFS, IPLD, and IPNS have no Storj-Uplink-shaped client encryption spec.
  This ns is the composition algebra. It does not hash, wrap, ratchet, or
  open sockets. Bytes live in:

    :object  — kotoba-lang/envelope. One content key, many wraps.
    :session — kotoba-lang/org-signal. X3DH + Double Ratchet.
    :hop     — kotoba-lang/noise. Adjacent-peer only.

  Mixing those constructions is :construction-mismatch. Putting a session
  ciphertext under an object CID is composition, not mixing.

  Noise is not E2EE. IPNS authenticates a pointer; it does not hide it.
  DAG-JOSE is a draft container; it is not a ratchet. One-time prekeys
  cannot be consumed-once on a content-addressed store alone.

  Convergent encryption is forbidden (ADR-2608070400 D5)."
  (:require [kotoba.protocol.vocab :as vocab]))

(def version 1)

(def constructions
  "What each construction actually promises. `spec-in-ipfs?` is the
  question this algebra exists to answer: none of the confidentiality
  constructions are in the IPFS/IPLD/IPNS core specs."
  {:object {:confidential? true
            :forward-secret? false
            :authentic? true
            :scope :at-rest
            :impl "envelope"
            :spec-in-ipfs? false}
   :session {:confidential? true
             :forward-secret? true
             :authentic? true
             :scope :sender-to-recipient
             :impl "org-signal"
             :spec-in-ipfs? false}
   :hop {:confidential? true
         :forward-secret? true
         :authentic? true
         :scope :adjacent-peer
         :impl "noise"
         :spec-in-ipfs? :libp2p-only}
   :ipns {:confidential? false
          :forward-secret? false
          :authentic? true
          :scope :pointer
          :impl "tech-ipfs-specs-ipns"
          :spec-in-ipfs? true}
   :dag-jose {:confidential? true
              :forward-secret? false
              :authentic? true
              :scope :container
              :impl nil
              :spec-in-ipfs? :draft-container-only}})

(defn protection
  "Declared protection for `kind`, or nil."
  [kind]
  (get constructions kind))

(defn spec-in-ipfs?
  "True only when the IPFS/IPLD/IPNS core specs themselves define it."
  [kind]
  (true? (:spec-in-ipfs? (protection kind))))

(defn hop-is-e2ee?
  "libp2p Noise encrypts the hop. It does not encrypt sender-to-recipient."
  []
  false)

(defn dag-jose-is-ratchet?
  "DAG-JOSE is a JWE/JWS container. It does not ratchet, rotate devices,
  or give forward secrecy."
  []
  false)

(defn convergent-allowed?
  "Forbidden. Identical plaintext must not produce identical ciphertext
  CIDs (ADR-2608070400 D5, envelope README)."
  []
  false)

(defn opk-once-on-content-addressed?
  "A one-time prekey published as an IPFS object can be fetched many
  times. Pure IPFS/IPNS cannot enforce single use."
  []
  false)

(defn e2ee-is-not-session-plane?
  "Signal E2EE is content-protocol (the ciphertext is the object).
  The session plane is mux / Noise / Yamux / head — hop and causal
  coordinates, not sender-to-recipient secrecy."
  []
  true)

;; ── key roles ────────────────────────────────────────────────────────────────

(defn key-roles
  "IPNS naming key, libp2p PeerID key, and Signal identity key must be
  three distinct secrets. Same bytes in two roles is :key-role-collision."
  [{:keys [ipns peer signal] :as roles}]
  (cond
    (not (map? roles)) {:error :invalid-roles :value roles}
    (not (and (string? ipns) (seq ipns)
              (string? peer) (seq peer)
              (string? signal) (seq signal)))
    {:error :missing-role :value roles}
    (or (= ipns peer) (= ipns signal) (= peer signal))
    {:error :key-role-collision
     :collided (cond
                 (= ipns peer) #{:ipns :peer}
                 (= ipns signal) #{:ipns :signal}
                 :else #{:peer :signal})}
    :else {:ok? true :roles {:ipns ipns :peer peer :signal signal}}))

;; ── prekey bundle (IPNS value) ───────────────────────────────────────────────

(defn prekey-bundle
  "Public prekey material a recipient publishes under their IPNS name.

  Structural only — signature verify is org-signal's job. PQ prekey is
  optional until org-signal grows PQXDH; its absence is recorded, not
  silently upgraded to hybrid."
  [{:keys [identity-pub signed-prekey pq-prekey expires-at signature]}]
  (cond
    (not (string? identity-pub)) {:error :missing-identity-pub}
    (not (map? signed-prekey)) {:error :missing-signed-prekey}
    (not (string? signature)) {:error :missing-signature}
    :else
    (cond-> {:version version
             :kind :prekey-bundle
             :identity-pub identity-pub
             :signed-prekey signed-prekey
             :signature signature
             :confidential? false}
      (some? pq-prekey) (assoc :pq-prekey pq-prekey)
      (some? expires-at) (assoc :expires-at expires-at))))

(defn bundle?
  [b]
  (boolean (and (map? b)
                (not (:error b))
                (= :prekey-bundle (:kind b))
                (false? (:confidential? b)))))

;; ── attachment (file key travels in the Signal message) ──────────────────────

(defn attachment
  "A large object is encrypted under a fresh file key, stored as an IPFS
  CID, and the wrapped key travels inside the session ciphertext.

  `:plaintext` on this map is always :plaintext-in-attachment — the
  content-protocol object must not carry bytes the mailbox can read."
  [{:keys [cid wrapped-key size digest alg] :as m}]
  (cond
    (contains? m :plaintext) {:error :plaintext-in-attachment}
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    (not (string? wrapped-key)) {:error :missing-wrapped-key}
    (and (some? size) (not (nat-int? size))) {:error :invalid-size :value size}
    (and (some? digest) (not (string? digest))) {:error :invalid-digest :value digest}
    :else
    (cond-> {:cid cid
             :wrapped-key wrapped-key
             :alg (or alg :xchacha20-poly1305)}
      (some? size) (assoc :size size)
      (some? digest) (assoc :digest digest))))

;; ── sealed message ───────────────────────────────────────────────────────────

(defn message
  "A Signal ciphertext plus optional attachment descriptors.

  `:construction` must be :session. Storing this map under a CID uses
  the :object construction on already-ciphertext — `store` records that
  composition. Using :object here as the message construction is
  :construction-mismatch (envelope has no per-message ratchet)."
  [{:keys [construction header ciphertext attachments]}]
  (cond
    (not= :session construction) {:error :construction-mismatch
                                  :expected :session
                                  :got construction}
    (not (map? header)) {:error :missing-header}
    (not (and (string? ciphertext) (seq ciphertext))) {:error :missing-ciphertext}
    (some :error (or attachments [])) (first (filter :error attachments))
    :else
    {:version version
     :kind :sealed-message
     :construction :session
     :header header
     :ciphertext ciphertext
     :attachments (vec (or attachments []))}))

(defn message?
  [m]
  (boolean (and (map? m)
                (not (:error m))
                (= :sealed-message (:kind m))
                (= :session (:construction m)))))

(defn store
  "Compose: put session ciphertext on the object plane. The CID is
  identity of ciphertext bytes. This is not mixing constructions."
  [msg cid]
  (cond
    (:error msg) msg
    (not (message? msg)) {:error :not-a-sealed-message}
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    :else
    {:kind :stored-ciphertext
     :construction :object
     :body-is :session-ciphertext
     :cid cid
     :message msg}))

;; ── mailbox (append-only CID log, IPNS head) ─────────────────────────────────

(defn mailbox
  []
  {:kind :sealed-mailbox
   :entries []
   :head nil
   :dirty? false})

(defn append
  "Append a stored-ciphertext CID. Previous entry CIDs do not change.
  The mailbox head is naming; identity of each message is its own CID."
  [mb cid]
  (cond
    (:error mb) mb
    (not= :sealed-mailbox (:kind mb)) {:error :not-a-mailbox}
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    :else
    (-> mb
        (update :entries conj cid)
        (assoc :dirty? true))))

(defn entries-prefix?
  "True iff `before` entries are a prefix of `after` — append-only."
  [before after]
  (let [a (:entries before)
        b (:entries after)]
    (boolean (and (vector? a) (vector? b)
                  (<= (count a) (count b))
                  (= a (subvec b 0 (count a)))))))

(defn commit-head
  "Seal a dirty mailbox as a head CID through an injected hasher.

  `hash-fn` is `(fn [entries] cid)`. Production binds whatever hashes
  the DAG-CBOR log. This ns does not hash. A dirty mailbox that hashes
  to the previous head is :head-unchanged."
  [mb hash-fn]
  (cond
    (:error mb) mb
    (not (:dirty? mb)) mb
    (not (ifn? hash-fn)) {:error :hash-fn-required}
    :else
    (let [prev (:head mb)
          cid (hash-fn (:entries mb))]
      (cond
        (not (vocab/cid? cid)) {:error :invalid-head-cid :value cid}
        (and (some? prev) (= prev cid)) {:error :head-unchanged :cid cid}
        :else (assoc mb :head cid :dirty? false)))))

(defn publish-head
  "IPNS name → mailbox head CID. Naming, not encryption. The name is
  unchanged (naming/publish-live invariant). Value CID may change."
  [name head-cid]
  (cond
    (not (vocab/ipns-name? name)) {:error :invalid-ipns-name :value name}
    (not (vocab/cid? head-cid)) {:error :invalid-cid :value head-cid}
    :else
    {:plane :naming
     :name name
     :value head-cid
     :confidential? false
     :mutates-name? false}))

;; ── archive object (identity stays private; location goes to the store) ──────

(defn wrap
  "One copy of the content key, sealed to one recipient. The content key
  itself is never stored — only wraps of it. Sharing re-wraps the key; it
  does not re-encrypt the object."
  [{:keys [recipient wrapped-key]}]
  (cond
    (not (and (string? recipient) (seq recipient)))
    {:error :missing-recipient}
    (not (and (string? wrapped-key) (seq wrapped-key)))
    {:error :missing-wrapped-key}
    :else {:recipient recipient :wrapped-key wrapped-key}))

(defn archive-object
  "A large object sealed with the :object construction and archived under
  its ciphertext CID (ADR-2608301039).

  Two CIDs, and they are not interchangeable (ADR-2608148200):

    :identity — CID of the plaintext. For a DataLad/git-annex object this
                is the annex key `SHA256E-s<n>--<sha256>` read as
                CIDv1(raw, sha2-256). It names what the object IS, and it
                does not reach the object store.
    :location — CID of the ciphertext. This is what the archive door
                verifies and what the store is keyed by.

  Wraps live on the datom plane, not in the object store. An archive
  object with no wraps is not sealed, it is lost.

  `attachment` is the session-path sibling: there the wrapped key travels
  inside the Signal ciphertext. An archive object has no session for a key
  to travel inside, so the wraps are named here and stored elsewhere.

  `:identity` = `:location` is an error *for this constructor only*. A
  plain unencrypted document may legitimately have one CID in both roles
  (ADR-2608148200); but this constructor requires wraps, so equal CIDs
  mean the bytes went out unsealed, or that the ciphertext was derived
  from the plaintext — which is convergent encryption (forbidden,
  ADR-2608070400 D5)."
  [{:keys [identity location wraps size alg] :as m}]
  (let [wrapped (mapv wrap (or wraps []))]
    (cond
      (contains? m :plaintext) {:error :plaintext-in-archive-object}
      (contains? m :content-key) {:error :content-key-in-archive-object}
      (and (contains? m :construction) (not= :object (:construction m)))
      {:error :construction-mismatch :expected :object :got (:construction m)}
      (not (vocab/cid? identity)) {:error :invalid-identity-cid :value identity}
      (not (vocab/cid? location)) {:error :invalid-location-cid :value location}
      (= identity location) {:error :identity-is-location :value location}
      (not (seq wrapped)) {:error :no-recipients}
      (some :error wrapped) (first (filter :error wrapped))
      (and (some? size) (not (nat-int? size))) {:error :invalid-size :value size}
      :else
      (cond-> {:version version
               :kind :archive-object
               :construction :object
               :identity identity
               :location location
               :wraps wrapped
               :alg (or alg :xchacha20-poly1305)}
        (some? size) (assoc :size size)))))

(defn archive-object?
  [o]
  (boolean (and (map? o)
                (not (:error o))
                (= :archive-object (:kind o))
                (= :object (:construction o)))))

(defn store-view
  "Exactly what the object store may learn: the location CID, and the size
  it would measure anyway.

  Identity, wraps and recipients are absent by construction, so a provider
  that logged everything it received still could not name the plaintext or
  say who can open it. This is the executable form of `the provider is
  outside the trust boundary` (ADR-2608070400 D1) — the reason the choice
  of S3 / R2 / B2 / IPFS is a durability question and not a trust one."
  [o]
  (cond
    (:error o) o
    (not (archive-object? o)) {:error :not-an-archive-object}
    :else (cond-> {:cid (:location o)}
            (some? (:size o)) (assoc :size (:size o)))))

(defn store-learns-identity?
  "False. The archive store is keyed by the ciphertext CID. The plaintext
  CID lives on the private plane that also holds the wraps, so replacing
  the provider never widens what is disclosed."
  []
  false)

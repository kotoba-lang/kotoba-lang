(ns kagi.key-rotation
  "Concrete identity-signing and recipient-KEM epoch workers. Plans are pure
  until their signed event passes durable rotation admission."
  (:require [kagi.crypto :as crypto]
            [kagi.identity :as identity]
            [kagi.key-registry :as registry]
            [kagi.persist :as persist]
            [kagi.rotation :as rotation]
            [kagi.rotation-admission :as admission]
            [kagi.secret-store :as secret-store])
  (:import [java.time Instant]
           [java.time.temporal ChronoUnit]
           [java.nio.file Files Paths StandardCopyOption CopyOption]
           [java.nio.charset StandardCharsets]))

(defn- active-record [record epoch parent custody]
  (let [now (str (Instant/now))]
    (-> record
        (assoc :key/epoch epoch :key/parent parent :key/custody-ref custody
               :key/created-at now :key/not-before now :key/state :preactive)
        (dissoc :key/activated-at :key/retired-at :key/revoked-at :key/destroyed-at)
        (registry/transition :active now))))

(defn prepare-authority
  "Generate a replacement hybrid signing identity and dual-sign its authority
  transition. The old identity is retained in history for verification and
  rollback ceremony; callers persist only after admit-authority! succeeds."
  [crypto-provider old-identity {:keys [custody-ref parent]}]
  (registry/authorize! (:signing-key old-identity) :sign (str (Instant/now)))
  (let [fresh (identity/generate-identity)
        old-key (:signing-key old-identity)
        new-key (active-record (:signing-key fresh) (inc (:key/epoch old-key))
                               (:key/id old-key) custody-ref)
        next-id (-> old-identity
                    (merge (select-keys fresh [:private-key :public-key :did :graph
                                               :private-b64 :public-b64
                                               :mldsa-private-b64 :mldsa-public-b64]))
                    (assoc :signing-key new-key)
                    (update :retired-signing-epochs (fnil conj [])
                            {:key (registry/transition old-key :decrypt-or-verify-only
                                                       (str (Instant/now)))
                             :public (identity/encode-bundle
                                      (identity/sign-public old-identity))}))
        event0 (rotation/new-event {:subject (:authority-id old-identity) :purpose :authority
                                    :from-key (:key/id old-key) :to-key (:key/id new-key)
                                    :from-epoch (:key/epoch old-key)
                                    :reason :scheduled
                                    :parents (cond-> [] parent (conj parent))})
        event (rotation/sign-normal crypto-provider event0
                                    (identity/sign-secret old-identity)
                                    (identity/sign-secret next-id))]
    {:event event :old old-identity :next next-id}))

(defn admit-authority!
  [rotation-store crypto-provider {:keys [event old next] :as plan} current]
  (assoc plan :admission
         (admission/admit! rotation-store crypto-provider event
                           {:current current
                            :old-public (identity/sign-public old)
                            :new-public (identity/sign-public next)})))

(defn- verify-kem-possession!
  [p public secret]
  (let [{:keys [ciphertext shared]} (crypto/kem-encap p public)
        recovered (crypto/kem-decap p secret ciphertext)]
    (when-not (java.util.Arrays/equals ^bytes shared ^bytes recovered)
      (throw (ex-info "new KEM private key failed possession self-test" {})))))

(defn prepare-kem
  "Generate and self-test a replacement hybrid KEM key. The current authority,
  not the non-signing KEM key, certifies the epoch transition."
  [p identity-map {:keys [custody-ref parent]}]
  (let [pair (crypto/kem-keypair p)
        _ (verify-kem-possession! p (:public pair) (:secret pair))
        old-key (:kem-key identity-map)
        now (Instant/now)
        base (registry/key-record
              {:id (str "kem:" (.encodeToString (.withoutPadding (java.util.Base64/getUrlEncoder))
                                                  (crypto/sha256 (:pq (:public pair)))))
               :purpose :recipient-kem :suite :kem-v1 :epoch (inc (:key/epoch old-key))
               :created-at (str now) :not-before (str now)
               :originator-not-after (str (.plus now 90 ChronoUnit/DAYS))
               :custody-ref custody-ref :parent (:key/id old-key)})
        new-key (registry/transition base :active (str now))
        next-id (-> identity-map
                    (assoc :kem-public (identity/encode-bundle (:public pair))
                           :kem-secret (identity/encode-bundle (:secret pair))
                           :kem-key new-key)
                    (update :retired-kem-epochs (fnil conj [])
                            {:key (registry/transition old-key :decrypt-or-verify-only (str now))
                             :public (:kem-public identity-map)
                             :secret (:kem-secret identity-map)}))
        event0 (rotation/new-event {:subject (:authority-id identity-map) :purpose :recipient-kem
                                    :from-key (:key/id old-key) :to-key (:key/id new-key)
                                    :from-epoch (:key/epoch old-key) :reason :scheduled
                                    :parents (cond-> [] parent (conj parent))})
        event (rotation/sign-authorized p event0 (:did identity-map)
                                        (identity/sign-secret identity-map))]
    {:event event :old identity-map :next next-id}))

(defn admit-kem!
  [rotation-store p {:keys [event old] :as plan} current]
  (assoc plan :admission
         (admission/admit-authorized! rotation-store p event
                                      {:current current
                                       :authorizer-public (identity/sign-public old)})))

(defn- atomic-write! [path value]
  (let [target (Paths/get path (make-array String 0))
        parent (.getParent (.toAbsolutePath target))
        _ (Files/createDirectories parent (make-array java.nio.file.attribute.FileAttribute 0))
        tmp (Files/createTempFile parent ".identity-rotation-" ".edn"
                                  (make-array java.nio.file.attribute.FileAttribute 0))]
    (Files/writeString tmp (persist/->edn value) StandardCharsets/UTF_8
                       (into-array java.nio.file.OpenOption []))
    (try
      (Files/move tmp target
                  (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                           StandardCopyOption/REPLACE_EXISTING]))
      (catch java.nio.file.AtomicMoveNotSupportedException _
        (Files/move tmp target
                    (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))))

(defn- committed-plan! [plan]
  (when-not (true? (get-in plan [:admission :stored?]))
    (throw (ex-info "identity rotation must pass durable admission before commit" {})))
  plan)

(defn commit-secret-backed!
  "Crash-recoverable identity epoch switch.

  1. stage new secrets under a new SecretStore ref;
  2. persist a non-secret pending journal;
  3. atomically replace the public identity file;
  4. remove the journal.

  The old secret ref is deliberately retained for a bounded operator-managed
  decrypt/rollback window."
  ([identity-path store new-secret-ref plan]
   (commit-secret-backed! identity-path store new-secret-ref plan {}))
  ([identity-path store new-secret-ref plan {:keys [additional-snapshots]
                                              :or {additional-snapshots {}}}]
  (committed-plan! plan)
  (let [next (:next plan)
        old-ref (:secret-ref (:old plan))
        public (-> (select-keys next identity/public-fields)
                   (assoc :secret-ref new-secret-ref :secret-provider :secret-store
                          :security/key-metadata-version 1)
                   (assoc-in [:signing-key :key/custody-ref] new-secret-ref)
                   (cond-> (:kem-key next)
                     (assoc-in [:kem-key :key/custody-ref] new-secret-ref)))
        secret (cond-> (select-keys next identity/secret-fields)
                 (:retired-kem-epochs next)
                 (assoc :retired-kem-epochs (:retired-kem-epochs next)))
        journal-path (str identity-path ".rotation.pending.edn")
        journal {:version 1 :event-id (get-in plan [:event :rotation/id])
                 :old-secret-ref old-ref :new-secret-ref new-secret-ref
                 :public public :additional-snapshots additional-snapshots}]
    (when-not (and (seq old-ref) (seq new-secret-ref) (not= old-ref new-secret-ref))
      (throw (ex-info "rotation requires distinct old and staged secret refs"
                      {:old-ref? (boolean (seq old-ref))
                       :new-ref? (boolean (seq new-secret-ref))})))
    (secret-store/put-edn! store new-secret-ref secret)
    (when-not (secret-store/exists? store new-secret-ref)
      (throw (ex-info "staged rotation secret is not durable" {})))
    (atomic-write! journal-path journal)
    (atomic-write! identity-path public)
    (doseq [[path snapshot] additional-snapshots]
      (atomic-write! path snapshot))
    (Files/deleteIfExists (Paths/get journal-path (make-array String 0)))
    {:committed? true :event-id (:event-id journal)
     :new-secret-ref new-secret-ref :old-secret-ref old-ref})))

(defn recover-pending!
  "Finish an interrupted identity switch from its non-secret journal. Safe to
  invoke repeatedly; refuses recovery if the staged secret is unavailable."
  [identity-path store]
  (let [journal-path (str identity-path ".rotation.pending.edn")
        p (Paths/get journal-path (make-array String 0))]
    (when (Files/exists p (make-array java.nio.file.LinkOption 0))
      (let [{:keys [new-secret-ref public event-id additional-snapshots]}
            (persist/<-edn (Files/readString p StandardCharsets/UTF_8))]
        (when-not (secret-store/exists? store new-secret-ref)
          (throw (ex-info "pending identity rotation secret is missing"
                          {:event-id event-id})))
        (atomic-write! identity-path public)
        (doseq [[path snapshot] additional-snapshots]
          (atomic-write! path snapshot))
        (Files/deleteIfExists p)
        {:recovered? true :event-id event-id :new-secret-ref new-secret-ref}))))

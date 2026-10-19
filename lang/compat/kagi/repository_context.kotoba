(ns kagi.repository-context
  "Non-interactive, repository-scoped Kagi context.

  The stable Kagi root VMK is unlocked by OS Keychain or an explicit host
  callback. Each repository gets an independent VMK epoch chain. Rotation is
  two phase: prepare returns a signed Kagi rotation event and wrapped key but
  does not change the current keyring; only a head that won remote CAS is
  adopted into the durable rotation DAG and local vault metadata."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kagi.crypto :as crypto]
            [kagi.identity :as identity]
            [kagi.persist :as persist]
            [kagi.rotation :as rotation]
            [kagi.rotation-admission :as admission]
            [kagi.rotation-store :as rotation-store]
            [kagi.secret-store :as secret-store]
            [kagi.unlock :as unlock])
  (:import [java.util UUID]))

(def ^:private keyring-version 1)
(def ^:private default-repository-id "default")

(defn- home-file [vault-home]
  (.getCanonicalFile
   (io/file (or vault-home
                (System/getenv "KAGI_HOME")
                (str (System/getProperty "user.home") "/.kagi")))))

(defn- repository-id! [repository-id]
  (let [value (or repository-id default-repository-id)]
    (when-not (and (string? value) (seq value) (<= (count value) 256))
      (throw (ex-info "bounded repository id is required"
                      {:type :kagi/repository-id-required})))
    value))

(defn- unlock-root-vmk [provider meta unlock-vmk-fn]
  (let [vmk (if unlock-vmk-fn
              (unlock-vmk-fn provider meta)
              (unlock/unlock-with-os-keychain
               provider meta (not-empty (System/getenv "KAGI_UNLOCK_REF"))))]
    (when-not vmk
      (throw (ex-info "Kagi VMK is not available non-interactively"
                      {:type :kagi/repository-context-locked
                       :methods (:methods (unlock/status meta))})))
    vmk))

(defn- keyring-path [repository-id]
  [:repository/vmk-keyrings repository-id])

(defn- keyring-for [meta repository-id]
  (or (get-in meta (keyring-path repository-id))
      ;; Compatibility for the short-lived unscoped development format.
      (when (= repository-id default-repository-id)
        (:repository/vmk-keyring meta))))

(defn- keyring-compartment [repository-id]
  (str "kagi/repository-vmk-keyring/v1\u0000" repository-id))

(defn repository-vmks
  "Open one repository's VMK keyring under Kagi's stable root VMK. Epoch 1
  remains root-VMK compatible; later random VMKs are persisted only wrapped."
  [provider root-vmk meta repository-id]
  (let [repository-id (repository-id! repository-id)
        keyring (keyring-for meta repository-id)
        _ (when (and keyring (not= keyring-version (:version keyring)))
            (throw (ex-info "unsupported repository VMK keyring"
                            {:type :kagi/repository-keyring-version
                             :version (:version keyring)})))
        kek (crypto/compartment-key provider root-vmk
                                    (keyring-compartment repository-id))]
    (reduce-kv
     (fn [result epoch wrapped]
       (when-not (pos-int? epoch)
         (throw (ex-info "invalid repository VMK epoch"
                         {:type :kagi/repository-keyring-invalid
                          :key/epoch epoch})))
       (assoc result epoch (crypto/unwrap-dek provider kek wrapped)))
     {1 root-vmk}
     (or (:keys keyring) {}))))

(defn- load-identity [identity-file identity-secret-store]
  (let [identity-map (edn/read-string (slurp identity-file))]
    (when-not (identity/secret-backed-identity? identity-map)
      (throw (ex-info "repository runtime requires SecretStore-backed identity"
                      {:type :kagi/repository-plaintext-identity-denied
                       :identity (.getPath identity-file)})))
    (identity/load-secret-backed-identity
     identity-map
     (or identity-secret-store
         (secret-store/store-for-ref (:secret-ref identity-map))))))

(defn- initial-key-id [repository-id]
  (str "repository-vmk:" repository-id ":1"))

(defn- build-context
  [provider home identity-file identity-secret-store root-vmk meta
   repository-id requested-epoch]
  (let [repository-id (repository-id! repository-id)
        keyring (keyring-for meta repository-id)
        vmks (repository-vmks provider root-vmk meta repository-id)
        current-epoch (long (or (:current-epoch keyring) 1))
        key-epoch (long (or requested-epoch current-epoch))
        vmk (get vmks key-epoch)]
    (when-not vmk
      (throw (ex-info "repository VMK epoch is unavailable"
                      {:type :kagi/repository-key-epoch-unavailable
                       :key/epoch key-epoch
                       :available-epochs (vec (sort (keys vmks)))})))
    (let [identity* (load-identity identity-file identity-secret-store)
          envelopes (or (:keys keyring) {})
          kek (crypto/compartment-key provider root-vmk
                                      (keyring-compartment repository-id))]
      {:provider provider :vmk vmk :vmks vmks :key-epoch key-epoch
       :current-key-epoch current-epoch
       :repository-id repository-id
       :key-envelopes envelopes
       :unwrap-repository-vmk
       (fn [epoch envelope]
         (when-not (and (pos-int? epoch) (map? envelope))
           (throw (ex-info "invalid repository VMK envelope"
                           {:type :kagi/repository-key-envelope-invalid
                            :key/epoch epoch})))
         (crypto/unwrap-dek provider kek envelope))
       :identity identity*
       :signing-secret (identity/sign-secret identity*)
       :signing-public (identity/sign-public identity*)
       :vault-home (.getPath home)})))

(defn- load-material
  [{:keys [vault-home provider unlock-vmk-fn repository-id
           identity-secret-store]
    :or {provider (crypto/jvm-provider)}}]
  (let [home (home-file vault-home)
        vault-file (io/file home "vault.edn")
        identity-file (io/file home "identity.edn")
        vault (persist/load* (.getPath vault-file))]
    (when-not (and vault (.isFile identity-file))
      (throw (ex-info "initialized Kagi vault and identity are required"
                      {:type :kagi/repository-context-missing
                       :vault (.getPath vault-file)
                       :identity (.getPath identity-file)})))
    {:home home :vault-file vault-file :identity-file identity-file
     :vault vault :meta (:meta vault) :provider provider
     :identity-secret-store identity-secret-store
     :repository-id (repository-id! repository-id)
     :root-vmk (unlock-root-vmk provider (:meta vault) unlock-vmk-fn)}))

(defn load-context
  [{:keys [key-epoch] :as options}]
  (let [{:keys [provider home identity-file identity-secret-store root-vmk
                meta repository-id]}
        (load-material options)]
    (build-context provider home identity-file identity-secret-store root-vmk
                   meta repository-id key-epoch)))

(defn prepare-repository-vmk-rotation
  "Stage the next random repository VMK and signed Kagi rotation event. Nothing
  durable changes until `adopt-repository-vmk!` receives the event from the
  head that actually won remote CAS."
  [{:keys [expected-epoch] :as options}]
  (when-not (pos-int? expected-epoch)
    (throw (ex-info "positive expected repository key epoch is required"
                    {:type :kagi/repository-key-epoch-required})))
  (let [{:keys [provider home identity-file identity-secret-store root-vmk
                meta repository-id]}
        (load-material options)
        keyring (keyring-for meta repository-id)
        current-epoch (long (or (:current-epoch keyring) 1))]
    (when-not (= (long expected-epoch) current-epoch)
      (throw (ex-info "repository VMK epoch is stale"
                      {:type :kagi/repository-key-epoch-stale
                       :expected expected-epoch :actual current-epoch})))
    (let [identity* (load-identity identity-file identity-secret-store)
          next-epoch (inc current-epoch)
          next-vmk (crypto/rand-bytes provider 32)
          kek (crypto/compartment-key provider root-vmk
                                      (keyring-compartment repository-id))
          envelope (crypto/wrap-dek provider kek next-vmk)
          from-key (or (:current-key-id keyring)
                       (initial-key-id repository-id))
          to-key (str "repository-vmk:" (UUID/randomUUID))
          parent (:current-event-id keyring)
          event0 (rotation/new-event
                  {:subject (str "repository:" repository-id)
                   :purpose :repository-vmk
                   :from-key from-key :to-key to-key
                   :from-epoch current-epoch :reason :scheduled
                   :parents (cond-> [] parent (conj parent))})
          event-base (assoc event0
                            :repository/id repository-id
                            :repository/key-envelope envelope)
          event (rotation/sign-authorized
                 provider event-base
                 (or (get-in identity* [:signing-key :key/id])
                     (:authority-id identity*))
                 (identity/sign-secret identity*))
          staged-keyring (-> (or keyring {:version keyring-version :keys {}})
                             (assoc :version keyring-version
                                    :current-epoch next-epoch
                                    :current-key-id to-key
                                    :current-event-id (:rotation/id event))
                             (assoc-in [:keys next-epoch] envelope))
          meta* (assoc-in meta (keyring-path repository-id) staged-keyring)]
      (assoc (build-context provider home identity-file identity-secret-store
                            root-vmk meta* repository-id next-epoch)
             :repository-rotation-event event
             :rotation/staged? true))))

(defn adopt-repository-vmk!
  "Admit a published head's signed repository rotation event into Kagi's
  durable DAG, then atomically advance the repository-scoped local keyring.
  Repeating the same event is idempotent; competing children fail closed."
  [{:keys [key-epoch key-envelope rotation-event] :as options}]
  (when-not (and (pos-int? key-epoch) (> key-epoch 1)
                 (map? key-envelope) (map? rotation-event))
    (throw (ex-info "remote key envelope and rotation event are required"
                    {:type :kagi/repository-key-proof-required})))
  (let [home (home-file (:vault-home options))
        lock-file (io/file home ".repository-vmk.lock")]
    (.mkdirs home)
    (with-open [stream (java.io.FileOutputStream. lock-file true)
                _lock (.lock (.getChannel stream))]
      (let [{:keys [provider home vault-file identity-file identity-secret-store
                    vault root-vmk meta repository-id]}
            (load-material options)
            keyring (keyring-for meta repository-id)
            current-epoch (long (or (:current-epoch keyring) 1))
            existing (get-in keyring [:keys key-epoch])
            identity* (load-identity identity-file identity-secret-store)
            expected-parent (:current-event-id keyring)
            current {:subject (str "repository:" repository-id)
                     :purpose :repository-vmk
                     :key-id (or (:current-key-id keyring)
                                 (initial-key-id repository-id))
                     :epoch current-epoch :parent expected-parent}
            same-envelope? (and existing
                                (= (persist/->edn existing)
                                   (persist/->edn key-envelope)))]
        (when-not (= key-envelope (:repository/key-envelope rotation-event))
          (throw (ex-info "rotation event carries a different key envelope"
                          {:type :kagi/repository-key-envelope-mismatch})))
        (when-not (= repository-id (:repository/id rotation-event))
          (throw (ex-info "rotation event belongs to another repository"
                          {:type :kagi/repository-rotation-scope-mismatch})))
        ;; Prove root ownership before DAG or vault mutation.
        (let [kek (crypto/compartment-key provider root-vmk
                                          (keyring-compartment repository-id))]
          (crypto/unwrap-dek provider kek key-envelope))
        (cond
          (and (= current-epoch key-epoch) same-envelope?)
          (build-context provider home identity-file identity-secret-store
                         root-vmk meta repository-id key-epoch)

          (not= key-epoch (inc current-epoch))
          (throw (ex-info "repository rotation events must be adopted in order"
                          {:type :kagi/repository-rotation-gap
                           :current current-epoch :incoming key-epoch}))

          :else
          (let [dag (rotation-store/file-store
                     (.getPath (io/file home "repository-rotation-dag.edn")))
                event-id (:rotation/id rotation-event)
                _ (if (rotation-store/event dag event-id)
                    (when-not (rotation/valid-authorized?
                               provider rotation-event
                               (identity/sign-public identity*))
                      (throw (ex-info "persisted repository rotation proof is invalid"
                                      {:type :kagi/repository-rotation-proof-invalid})))
                    (admission/admit-authorized!
                     dag provider rotation-event
                     {:current current
                      :authorizer-public (identity/sign-public identity*)}))
                next-keyring (-> (or keyring {:version keyring-version :keys {}})
                                 (assoc :version keyring-version
                                        :current-epoch key-epoch
                                        :current-key-id (:rotation/to-key rotation-event)
                                        :current-event-id event-id)
                                 (assoc-in [:keys key-epoch] key-envelope))
                meta* (assoc-in meta (keyring-path repository-id) next-keyring)]
            (persist/save! (.getPath vault-file) (assoc vault :meta meta*))
            (build-context provider home identity-file identity-secret-store
                           root-vmk meta* repository-id key-epoch)))))))

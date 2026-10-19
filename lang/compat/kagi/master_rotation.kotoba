(ns kagi.master-rotation
  "VMK epoch rotation by transactional re-wrapping of every item DEK. Payload
  ciphertext is unchanged; raw VMKs are never persisted."
  (:require [kagi.crypto :as crypto]
            [kagi.identity :as identity]
            [kagi.key-registry :as registry]
            [kagi.persist :as persist]
            [kagi.rotation :as rotation]
            [kagi.rotation-admission :as admission])
  (:import [java.time Instant]
           [java.time.temporal ChronoUnit]
           [java.util UUID]
           [java.nio.file Files Paths StandardCopyOption CopyOption]
           [java.nio.charset StandardCharsets]))

(defn new-vmk-key-record [now custody-ref epoch parent]
  (registry/transition
   (registry/key-record
    {:id (str "vmk:" (UUID/randomUUID)) :purpose :vmk :suite :data-v1
     :epoch epoch :created-at now :not-before now
     :originator-not-after
     (str (.plus (Instant/parse now) 365 ChronoUnit/DAYS))
     :custody-ref custody-ref :parent parent})
   :active now))

(defn plan
  "Create fresh VMK, unwrap each current item DEK with OLD-VMK, and re-wrap it
  under NEW-VMK. wrap-meta must return persisted unlock metadata which wraps
  the supplied VMK; returning raw VMK bytes is rejected."
  [p identity-map vault-state old-vmk
   {:keys [now custody-ref parent wrap-meta]
    :or {now (str (Instant/now))}}]
  (when-not (and (bytes? old-vmk) (= 32 (alength ^bytes old-vmk))
                 (seq custody-ref) (ifn? wrap-meta))
    (throw (ex-info "incomplete VMK rotation configuration" {})))
  (let [old-key (get-in vault-state [:meta :vmk-key])]
    (registry/authorize! old-key :unwrap now)
    (let [new-vmk (crypto/rand-bytes p 32)
          new-key (new-vmk-key-record now custody-ref (inc (:key/epoch old-key))
                                      (:key/id old-key))
          items (into {}
                      (map (fn [[id item]]
                             (let [old-kek (crypto/compartment-key
                                            p old-vmk (:item/compartment item))
                                   dek (crypto/unwrap-dek p old-kek (:item/wrap item))
                                   new-kek (crypto/compartment-key
                                            p new-vmk (:item/compartment item))]
                               [id (assoc item :item/wrap (crypto/wrap-dek p new-kek dek)
                                               :item/vmk-epoch (:key/epoch new-key))])))
                      (:items vault-state))
          meta* (wrap-meta new-vmk)
          _ (when (some #(and (bytes? %) (java.util.Arrays/equals ^bytes new-vmk ^bytes %))
                        (tree-seq coll? seq meta*))
              (throw (ex-info "wrap-meta attempted to persist raw VMK" {})))
          retired (registry/transition old-key :decrypt-or-verify-only now)
          next-state (-> vault-state
                         (assoc :items items)
                         (assoc :meta (-> meta*
                                          (assoc :vmk-key new-key)
                                          (update :retired-vmk-epochs (fnil conj []) retired))))
          event0 (rotation/new-event
                  {:subject (:authority-id identity-map) :purpose :vmk
                   :from-key (:key/id old-key) :to-key (:key/id new-key)
                   :from-epoch (:key/epoch old-key) :reason :scheduled
                   :parents (cond-> [] parent (conj parent))})
          event (rotation/sign-authorized p event0 (:did identity-map)
                                          (identity/sign-secret identity-map))]
      {:event event :old-key old-key :new-key new-key
       :new-vmk new-vmk :next-state next-state :identity identity-map})))

(defn admit!
  [rotation-store p {:keys [event identity] :as plan} current]
  (assoc plan :admission
         (admission/admit-authorized!
          rotation-store p event
          {:current current :authorizer-public (identity/sign-public identity)})))

(defn commit!
  "Atomically replace the vault snapshot only after durable DAG admission."
  [vault-path plan]
  (when-not (true? (get-in plan [:admission :stored?]))
    (throw (ex-info "VMK rotation must pass durable admission before commit" {})))
  (let [target (Paths/get vault-path (make-array String 0))
        parent (.getParent (.toAbsolutePath target))
        _ (Files/createDirectories parent (make-array java.nio.file.attribute.FileAttribute 0))
        tmp (Files/createTempFile parent ".vmk-rotation-" ".edn"
                                  (make-array java.nio.file.attribute.FileAttribute 0))]
    (Files/writeString tmp (persist/->edn (:next-state plan)) StandardCharsets/UTF_8
                       (into-array java.nio.file.OpenOption []))
    (try
      (Files/move tmp target
                  (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                           StandardCopyOption/REPLACE_EXISTING]))
      (catch java.nio.file.AtomicMoveNotSupportedException _
        (Files/move tmp target
                    (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))
    {:committed? true :event-id (get-in plan [:event :rotation/id])
     :vmk-epoch (get-in plan [:new-key :key/epoch])}))

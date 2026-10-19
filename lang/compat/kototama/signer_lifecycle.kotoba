(ns kototama.signer-lifecycle
  "Monotonic, root-authorized manifest signer lifecycle."
  (:require [kotoba.security.ed25519 :as ed25519]
            [kotoba.security.effect :as effect]))

(defn new-registry
  "Create a live signer registry. ROOT-KEY-ID identifies the offline/control
   root whose verifier authorizes every trust update."
  [root-key-id]
  (atom {:version 1 :epoch 0 :root-key-id root-key-id
         :signers {} :emergency-distrust #{} :history []}))

(defn- update-body [update]
  (dissoc update :signature))

(defn apply-trust-update!
  "Apply a root-signed, strictly monotonic trust update without restart.

   UPDATE contains :epoch, :issued-at-ms, optional :add-signers map,
   :revoke-signers set, :emergency-distrust set, and :signature.
   VERIFY-ROOT receives [root-key-id canonical-body signature]."
  [registry trust-update verify-root]
  (let [current @registry
        body (update-body trust-update)
        epoch (:epoch trust-update)
        signature (:signature trust-update)]
    (when-not (and (integer? epoch) (> epoch (:epoch current)))
      (throw (ex-info "kototama.signer-lifecycle: stale trust epoch"
                      {:kototama.signer/code :stale-epoch
                       :current (:epoch current) :proposed epoch})))
    (when-not (and (ifn? verify-root)
                   (verify-root (:root-key-id current) (pr-str body) signature))
      (throw (ex-info "kototama.signer-lifecycle: invalid root signature"
                      {:kototama.signer/code :invalid-root-signature
                       :epoch epoch})))
    (let [adds (or (:add-signers trust-update) {})
          revokes (set (:revoke-signers trust-update))
          emergency (set (:emergency-distrust trust-update))]
      (when-not (every? (fn [[id signer]]
                          (and id
                               (= id (:key-id signer))
                               (integer? (:not-before-ms signer))
                               (integer? (:expires-at-ms signer))
                               (< (:not-before-ms signer)
                                  (:expires-at-ms signer))))
                        adds)
        (throw (ex-info "kototama.signer-lifecycle: invalid signer record"
                        {:kototama.signer/code :invalid-signer-record})))
      (swap! registry
             (fn [state]
               (-> state
                   (assoc :epoch epoch)
                   (update :signers merge adds)
                   (update :signers
                           (fn [signers]
                             (reduce #(assoc-in %1 [%2 :status] :revoked)
                                     signers revokes)))
                   (update :emergency-distrust into emergency)
                   (update :history conj
                           {:epoch epoch
                            :issued-at-ms (:issued-at-ms trust-update)
                            :added (set (keys adds))
                            :revoked revokes
                            :emergency-distrust emergency})))))
    @registry))

(defn signer-decision
  "Fail-closed authorization decision at manifest verification time."
  [registry key-id now-ms]
  (let [state @registry
        signer (get-in state [:signers key-id])
        reason (cond
                 (contains? (:emergency-distrust state) key-id)
                 :emergency-distrust
                 (nil? signer) :unknown-signer
                 (= :revoked (:status signer)) :revoked
                 (< now-ms (:not-before-ms signer)) :not-yet-valid
                 (>= now-ms (:expires-at-ms signer)) :expired
                 :else nil)]
    {:allowed? (nil? reason)
     :reason reason :epoch (:epoch state)
     :key-id key-id :signer signer}))

(defn authorize-manifest!
  "Authorize and verify a signed manifest against the live registry.
   VERIFY-MANIFEST receives [public-key canonical-manifest signature]."
  [registry manifest now-ms verify-manifest]
  (let [key-id (:manifest/signer-key-id manifest)
        decision (signer-decision registry key-id now-ms)]
    (when-not (:allowed? decision)
      (throw (ex-info "kototama.signer-lifecycle: manifest signer denied"
                      {:kototama.signer/code :signer-denied
                       :kototama.signer/decision decision})))
    (let [signature (:manifest/signature manifest)
          body (dissoc manifest :manifest/signature)]
      (effect/guard!
       {:evaluate
        (fn [_]
          {:allowed?
           (and (ifn? verify-manifest)
                (verify-manifest
                 (get-in decision [:signer :public-key])
                 (pr-str body) signature))})
        :request {:key-id key-id :epoch (:epoch decision)}
        :approved? :allowed?
        :action :manifest/authorize
        :resource key-id
        :digest nil
        :effect
        (fn [_]
          {:authorized? true :key-id key-id :epoch (:epoch decision)})}))))

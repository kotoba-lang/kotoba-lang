(ns kotobase.kotobase
  "KotobaseStore — the IStore that persists to **kotobase.net** (the kotoba PDS).
  It carries NO HTTP client: every op is forwarded through a host-injected `xrpc`
  function `(fn [method params] -> result)`. The injecting host wires `xrpc` to the
  kotoba XRPC endpoint — in a Cloudflare Worker that is a `fetch` to
  `https://kotobase.net/xrpc/<method>` carrying the actor's CACAO; in tests it is a
  mock over a LocalStore. So the same store record runs in a worker, a JVM service,
  or a WASM pod.

  The five methods map onto kotoba's `:db-api` verbs (`{:q :transact! :pull …}`):
  `:put`/`:append` are `transact!`-class writes to the actor's key-derived IPNS
  graph (depth-1 self-mint, no token), `:get`/`:list`/`:read` are `q`/`pull` reads.
  kotobase.net itself backs that graph onto external object storage (git-annex/B2,
  S3) — so 'kotoba external storage' is exactly this record pointed at the PDS."
  (:require [kotoba.security.abac :as abac]
            [kotoba.security.approval :as approval]
            [kotoba.security.capability :as capability]
            [kotoba.security.crypto-policy :as crypto]
            [kotoba.security.hardware :as hardware]
            [kotoba.security.information-flow :as flow]
            [kotoba.security.qualification :as qualification]
            [kotoba.security.resilience :as resilience]
            [kotoba.security.transport :as transport]
            [kotobase.sealed-store :as sealed-store]
            [kotobase.store :as st]))

(def write-methods #{:put :append :transact})

(defn- resource-id [method params]
  (case method
    :put (str (:coll params) "/" (:key params))
    :get (str (:coll params) "/" (:key params))
    :list (str (:coll params) "/*")
    :append (str (:stream params) "/*")
    :read (str (:stream params) "/*")
    :snapshot :kotobase/snapshot
    :transact :kotobase/transaction
    :kotobase/unknown))

(defn authorize-xrpc
  "Wrap the host transport with shared four-axis ABAC. The wrapper evaluates
  immediately before every remote operation and never invokes XRPC on deny."
  [xrpc {:keys [abac-policy abac-attributes audit!
                information-flow-context information-flow-audit!
                transport-profile transport-audit!
                crypto-required? crypto-policy crypto-envelope crypto-audit!
                capability-required? capability-token-fn capability-context
                request-encode-fn request-digest-fn capability-audit!
                approval-required? approvals-fn approval-context approval-audit!
                request-bounds-required? request-size-fn max-request-bytes
                request-bounds-audit!
                hardware-signing-required? hardware-signing-evidence
                hardware-signing-audit!
                remote-telemetry-required? telemetry-events
                telemetry-encode-fn telemetry-digest-fn
                telemetry-append-fn telemetry-verify-ack-fn]
         :or {audit! (fn [_] nil) information-flow-audit! (fn [_] nil)
              transport-audit! (fn [_] nil) crypto-audit! (fn [_] nil)
              capability-audit! (fn [_] nil)
              approval-audit! (fn [_] nil)
              request-bounds-audit! (fn [_] nil)
              hardware-signing-audit! (fn [_] nil)}}]
  (fn [method params]
    (let [decision
          (abac/evaluate
           (-> abac-attributes
               (assoc :resource
                      (merge {:id (resource-id method params)}
                             (:resource abac-attributes)))
               (assoc :action
                      (merge {:id (if (write-methods method)
                                    :kotobase/write :kotobase/read)
                              :capabilities #{(keyword "kotobase" (name method))}}
                             (:action abac-attributes))))
           abac-policy)
          flow-decision (when (and (write-methods method)
                                   information-flow-context)
                          (flow/evaluate-egress information-flow-context))
          encoded-request (when (ifn? request-encode-fn)
                            (request-encode-fn [method params]))
          request-digest (when (and encoded-request request-digest-fn)
                           (request-digest-fn encoded-request))
          request-size (when (and encoded-request (ifn? request-size-fn))
                         (request-size-fn encoded-request))
          request-bounds-decision
          (when request-bounds-required?
            {:request-bounds/allowed?
             (and (nat-int? request-size) (nat-int? max-request-bytes)
                  (<= request-size max-request-bytes))
             :request-bounds/size request-size
             :request-bounds/maximum max-request-bytes})
          capability-decision
          (when capability-required?
            (capability/evaluate
             (when (ifn? capability-token-fn)
               (capability-token-fn method params))
             (merge capability-context
                    {:action (if (write-methods method)
                               :kotobase/write :kotobase/read)
                     :resource (resource-id method params)
                     :request-digest request-digest})))
          approval-decision
          (when approval-required?
            (approval/evaluate
             (when (ifn? approvals-fn) (approvals-fn method params))
             (assoc approval-context :request-digest request-digest)))
          hardware-signing-decision
          (when hardware-signing-required?
            (hardware/evaluate-signing hardware-signing-evidence))
          crypto-decision (when (or crypto-required? crypto-envelope)
                            (crypto/check-production-envelope
                             crypto-policy crypto-envelope))
          transport-decision (when transport-profile
                               (transport/evaluate transport-profile))]
      (when abac-policy (audit! decision))
      (when flow-decision (information-flow-audit! flow-decision))
      (when transport-decision (transport-audit! transport-decision))
      (when crypto-decision (crypto-audit! crypto-decision))
      (when capability-decision (capability-audit! capability-decision))
      (when approval-decision (approval-audit! approval-decision))
      (when request-bounds-decision
        (request-bounds-audit! request-bounds-decision))
      (when hardware-signing-decision
        (hardware-signing-audit! hardware-signing-decision))
      (cond
        (and capability-required?
             (not (:capability/allowed? capability-decision)))
        (throw (ex-info "signed capability denies kotobase XRPC"
                        {:type :kotobase/capability-denied :method method
                         :capability capability-decision}))

        (and hardware-signing-required?
             (not (:hardware-signing/qualified?
                   hardware-signing-decision)))
        (throw (ex-info "hardware signing policy denies kotobase XRPC"
                        {:type :kotobase/hardware-signing-denied :method method
                         :hardware-signing hardware-signing-decision}))

        (and approval-required?
             (not (:approval/allowed? approval-decision)))
        (throw (ex-info "approval quorum denies kotobase XRPC"
                        {:type :kotobase/approval-denied :method method
                         :approval approval-decision}))

        (and request-bounds-required?
             (not (:request-bounds/allowed? request-bounds-decision)))
        (throw (ex-info "request bounds deny kotobase XRPC"
                        {:type :kotobase/request-bounds-denied :method method
                         :request-bounds request-bounds-decision}))

        (not (:abac/allowed? decision))
        (throw (ex-info "ABAC policy denies kotobase operation"
                        {:type :kotobase/abac-denied
                         :method method
                         :abac/policy-id (:abac/policy-id decision)
                         :abac/violations (:abac/violations decision)}))

        (and flow-decision (not (:information-flow/allowed? flow-decision)))
        (throw (ex-info "information-flow policy denies kotobase egress"
                        {:type :kotobase/information-flow-denied
                         :method method
                         :information-flow/violations
                         (:information-flow/violations flow-decision)}))

        (and transport-decision (not (:transport/allowed? transport-decision)))
        (throw (ex-info "transport profile denies kotobase XRPC"
                        {:type :kotobase/transport-denied
                         :method method
                         :transport/violations
                         (:transport/violations transport-decision)}))

        (and crypto-required? (not (:valid? crypto-decision)))
        (throw (ex-info "hybrid PQC policy denies kotobase XRPC"
                        {:type :kotobase/crypto-denied :method method
                         :crypto crypto-decision}))

        :else
        (do
          (when remote-telemetry-required?
            (when-not (and (some? telemetry-events)
                           (ifn? telemetry-append-fn)
                           (ifn? telemetry-verify-ack-fn))
              (throw (ex-info "remote immutable telemetry required"
                              {:type :kotobase/telemetry-denied})))
            (let [result
                  (resilience/append-remote-telemetry!
                   {:events @telemetry-events
                    :event {:event :kotobase/operation-admitted
                            :method method :resource (resource-id method params)
                            :request-digest request-digest}
                    :encode-fn telemetry-encode-fn
                    :digest-fn telemetry-digest-fn
                    :append-fn telemetry-append-fn
                    :verify-ack-fn telemetry-verify-ack-fn})]
              (reset! telemetry-events (:telemetry/events result))))
          (xrpc method params))
        ))))

(defn evaluate-recovery-readiness
  [{:keys [recovery-artifact-digest restore-receipt restore-attestation
           restore-attestation-context]}]
  (let [restore (resilience/evaluate-restore-receipt
                 restore-receipt recovery-artifact-digest)
        attestation (qualification/verify-signed-receipt
                     restore-attestation restore-attestation-context)
        violations (cond-> []
                     (not (:restore-drill/qualified? restore))
                     (conj :restore-drill)
                     (not (:qualification/accepted? attestation))
                     (conj :restore-attestation)
                     (not= recovery-artifact-digest
                           (:qualification/artifact-digest attestation))
                     (conj :artifact-binding))]
    {:recovery/ready? (empty? violations)
     :recovery/violations violations
     :recovery/restore restore
     :recovery/attestation attestation}))

(defn enforce-recovery-readiness! [options]
  (let [result (evaluate-recovery-readiness options)]
    (when-not (:recovery/ready? result)
      (throw (ex-info "recovery readiness denies kotobase store" result)))
    result))

(defn production-profile-violations
  "Return missing fail-closed controls for a Grade-A-candidate remote store.
  This is deliberately structural; each supplied control still performs its
  cryptographic/policy validation at construction or immediately before XRPC."
  [{:keys [sealed-store-options abac-policy abac-attributes
           information-flow-context transport-profile
           crypto-required? crypto-policy crypto-envelope
           capability-required? capability-token-fn capability-context
           request-encode-fn request-digest-fn
           request-bounds-required? request-size-fn max-request-bytes
           approval-required? approvals-fn approval-context
           hardware-signing-required? hardware-signing-evidence
           remote-telemetry-required? telemetry-events telemetry-encode-fn
           telemetry-digest-fn telemetry-append-fn telemetry-verify-ack-fn
           recovery-required?]}]
  (cond-> []
    (nil? sealed-store-options) (conj :sealed-store)
    (nil? abac-policy) (conj :abac-policy)
    (nil? abac-attributes) (conj :abac-attributes)
    (nil? information-flow-context) (conj :information-flow)
    (nil? transport-profile) (conj :transport-profile)
    (not (and crypto-required? crypto-policy crypto-envelope))
    (conj :crypto-policy)
    (not (and capability-required? (ifn? capability-token-fn)
              capability-context (ifn? request-encode-fn)
              (ifn? request-digest-fn)))
    (conj :signed-capability)
    (not (and request-bounds-required? (ifn? request-size-fn)
              (nat-int? max-request-bytes) (pos? max-request-bytes)))
    (conj :request-bounds)
    (not (and approval-required? (ifn? approvals-fn) approval-context))
    (conj :approval)
    (not (and hardware-signing-required? hardware-signing-evidence))
    (conj :hardware-signing)
    (not (and remote-telemetry-required? telemetry-events
              (ifn? telemetry-encode-fn) (ifn? telemetry-digest-fn)
              (ifn? telemetry-append-fn) (ifn? telemetry-verify-ack-fn)))
    (conj :remote-telemetry)
    (not recovery-required?) (conj :recovery-readiness)))

(defn enforce-production-profile! [options]
  (let [violations (production-profile-violations options)]
    (when (seq violations)
      (throw (ex-info "incomplete production kotobase security profile"
                      {:kotobase/problem :incomplete-production-profile
                       :deployment-profile :production
                       :kotobase/violations violations})))
    options))

(deftype KotobaseStore [xrpc]
  st/IStore
  (-put  [_ coll k v] (xrpc :put  {:coll coll :key k :val v}))
  (-get  [_ coll k]   (xrpc :get  {:coll coll :key k}))
  (-list [_ coll]     (xrpc :list {:coll coll}))
  (-append [_ stream event] (xrpc :append {:stream stream :event event}))
  (-read   [_ stream since] (xrpc :read   {:stream stream :since (or since 0)})))

(deftype TransactionalKotobaseStore [xrpc]
  st/IStore
  (-put  [_ coll k v] (xrpc :put  {:coll coll :key k :val v}))
  (-get  [_ coll k]   (xrpc :get  {:coll coll :key k}))
  (-list [_ coll]     (xrpc :list {:coll coll}))
  (-append [_ stream event] (xrpc :append {:stream stream :event event}))
  (-read [_ stream since] (xrpc :read {:stream stream :since (or since 0)}))
  st/ITransactionalStore
  (-snapshot [_ scope] (xrpc :snapshot scope))
  (-transact [_ request] (xrpc :transact request)))

(defn kotobase-store
  "A store backed by kotobase.net through the injected `xrpc` fn
  `(fn [method params] -> result)`.

  `:deployment-profile :production` is fail-closed: a production remote store
  cannot be constructed without `:sealed-store-options`. The one-argument
  form and non-production profiles remain available for local compatibility
  and tests, but they make no production confidentiality claim."
  ([xrpc] (->KotobaseStore xrpc))
  ([xrpc {:keys [transactional? abac-policy information-flow-context
                 transport-profile crypto-required? capability-required?
                 hardware-signing-required? remote-telemetry-required?
                 approval-required? request-bounds-required?
                 recovery-required? sealed-store-options deployment-profile]
          :as options}]
   (let [_ (when (= :production deployment-profile)
             (enforce-production-profile! options))
         _ (when recovery-required? (enforce-recovery-readiness! options))
         xrpc (if sealed-store-options
                (sealed-store/wrap-xrpc xrpc sealed-store-options)
                xrpc)
         xrpc (if (or abac-policy information-flow-context transport-profile
                      crypto-required? capability-required?
                      hardware-signing-required? remote-telemetry-required?
                      approval-required? request-bounds-required?)
                (authorize-xrpc xrpc options)
                xrpc)]
     (if transactional?
       (->TransactionalKotobaseStore xrpc)
       (->KotobaseStore xrpc)))))

(defn xrpc-method->path
  "kotoba XRPC URL path for a store `method` (the worker's `fetch` target under
  https://kotobase.net/xrpc/)."
  [method]
  (str "net.kotobase.store." (name method)))

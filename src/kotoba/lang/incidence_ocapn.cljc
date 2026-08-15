(ns kotoba.lang.incidence-ocapn
  "Narrow OCapN CapTP adapter for the incidence publication port.

  This is not a CapTP implementation. A trusted netlayer/session runtime
  supplies a live send function after authentication and session setup. The
  adapter turns one verified incidence append into the current draft
  op:deliver abstraction without making locator or sturdyref data executable."
  (:require [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-port :as port]))

(def captp-version "1.0")
(def draft-profile "ocapn-captp-1.0-draft-2026-08-15")

(def ^:private reference-fields
  #{:session/id :session/version :session/authenticated?
    :remote/target :send!})

(def ^:private target-fields
  #{:ocapn/descriptor :ocapn/position})

(defprotocol ^:private RemoteReference
  (-reference-info [reference])
  (-deliver! [reference message]))

(deftype ^:private LiveReference [info send!]
  RemoteReference
  (-reference-info [_] info)
  (-deliver! [_ message] (send! message)))

(defn- non-negative-int?
  [x]
  (and (int? x) (not (neg? x))))

(defn- target?
  [target]
  (and (map? target)
       (= target-fields (set (keys target)))
       (= :desc/export (:ocapn/descriptor target))
       (non-negative-int? (:ocapn/position target))))

(defn connection-error
  "Return a fail-closed diagnostic for trusted runtime connection inputs."
  [opts]
  (cond
    (not (map? opts))
    {:problem :ocapn/connection-not-a-map}

    (not= reference-fields (set (keys opts)))
    {:problem :ocapn/connection-fields}

    (not (capabilities/non-empty-string? (:session/id opts)))
    {:problem :ocapn/session-id-invalid}

    (not= captp-version (:session/version opts))
    {:problem :ocapn/version-unsupported
     :expected captp-version
     :actual (:session/version opts)}

    (not (true? (:session/authenticated? opts)))
    {:problem :ocapn/session-not-authenticated}

    (not (target? (:remote/target opts)))
    {:problem :ocapn/target-invalid}

    (not (fn? (:send! opts)))
    {:problem :ocapn/send-port-invalid}))

(defn connected-reference
  "Construct an opaque live reference from an already authenticated CapTP
  session. This host API does not parse or resolve locator/sturdyref data."
  [opts]
  (if-let [error (connection-error opts)]
    (throw (ex-info "invalid OCapN live reference" error))
    (LiveReference.
     {:ocapn/profile draft-profile
      :session/id (:session/id opts)
      :session/version (:session/version opts)
      :remote/target (:remote/target opts)}
     (:send! opts))))

(defn live-reference?
  [x]
  (satisfies? RemoteReference x))

(defn reference-description
  "Return inert audit metadata. The live send authority is never exposed."
  [reference]
  (when (live-reference? reference)
    (-reference-info reference)))

(defn- append-request-error
  [request]
  (let [dataspace (:dataspace request)
        entry (:entry request)
        cap (:capability request)
        verified (when (map? entry) (incidence/verify-addressed entry))]
    (cond
      (not (map? request))
      {:problem :ocapn/append-request-not-a-map}

      (not= #{:dataspace :entry :capability} (set (keys request)))
      {:problem :ocapn/append-request-fields}

      (not (capabilities/non-empty-string? dataspace))
      {:problem :ocapn/dataspace-invalid}

      (not (:ok? verified))
      {:problem :ocapn/incidence-invalid :verification verified}

      (not (capabilities/capability? cap))
      {:problem :ocapn/capability-invalid}

      (not= port/append-kind (:cap/kind cap))
      {:problem :ocapn/capability-kind}

      (not= dataspace (:cap/resource cap))
      {:problem :ocapn/capability-resource})))

(defn deliver-message
  "Build the abstract current-draft CapTP delivery for one incidence.

  Canonical DAG-CBOR bytes, rather than an EDN capability-shaped envelope, are
  sent as passable binary data. A concrete session driver is responsible for
  Syrup encoding and the reliable in-order netlayer."
  [reference request]
  (when-not (live-reference? reference)
    (throw (ex-info "OCapN reference is not live"
                    {:problem :ocapn/not-live-reference})))
  (if-let [error (append-request-error request)]
    (throw (ex-info "invalid OCapN incidence append" error))
    (let [entry (:entry request)
          block (:incidence/block entry)]
      {:ocapn/profile draft-profile
       :ocapn/op :op/deliver
       :ocapn/to (:remote/target (-reference-info reference))
       :ocapn/args ['append-incidence
                    (:dataspace request)
                    (:incidence/cid entry)
                    (incidence/canonical-bytes block)]
       :ocapn/answer-position false
       :ocapn/resolve-me false})))

(defn append-provider
  "Adapt a live OCapN reference to incidence-port's lexical append provider.

  The session driver must return an inert map containing
  :ocapn/accepted? true and a non-empty :ocapn/message-id after accepting the
  frame. This acknowledges local session enqueue only, not remote durability."
  [reference]
  (when-not (live-reference? reference)
    (throw (ex-info "OCapN reference is not live"
                    {:problem :ocapn/not-live-reference})))
  (fn append-over-ocapn! [request]
    (let [message (deliver-message reference request)
          result (-deliver! reference message)]
      (when-not (and (map? result)
                     (true? (:ocapn/accepted? result))
                     (capabilities/non-empty-string? (:ocapn/message-id result)))
        (throw (ex-info "OCapN session did not accept message"
                        {:problem :ocapn/send-unconfirmed})))
      {:ocapn/accepted? true
       :ocapn/message-id (:ocapn/message-id result)
       :ocapn/session-id (:session/id (-reference-info reference))
       :incidence/cid (get-in request [:entry :incidence/cid])})))

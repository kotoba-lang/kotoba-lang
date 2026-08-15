(ns kotoba.lang.incidence-ocapn
  "Narrow OCapN CapTP adapter for the incidence publication port.

  This is not a CapTP implementation. A trusted netlayer/session runtime
  supplies a live send function after authentication and session setup. The
  adapter turns one verified incidence append into the current draft
  op:deliver abstraction without making locator or sturdyref data executable."
  (:require [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.incidence-port :as port]
            [kotoba.lang.trusted-admission :as trusted]))

(def captp-version trusted/captp-version)
(def draft-profile "ocapn-captp-1.0-draft-2026-08-15")
(def durable-receipt-version incidence/append-durable-version)
(def durable-receipt-kind incidence/append-durable-kind)

(def ^:private reference-fields
  #{:session :remote/target})

(def ^:private target-fields
  #{:ocapn/descriptor :ocapn/position})

(defprotocol ^:private RemoteReference
  (-reference-info [reference])
  (-deliver! [reference message]))

(defprotocol ^:private RemoteRequestReference
  (-request! [reference call]))

(deftype ^:private LiveReference [info session]
  RemoteReference
  (-reference-info [_] info)
  (-deliver! [_ message] (trusted/session-send! session message)))

(deftype ^:private LiveRequestReference [info session]
  RemoteReference
  (-reference-info [_] info)
  (-deliver! [_ message] (trusted/session-send! session message))
  RemoteRequestReference
  (-request! [_ call] (trusted/session-request! session call)))

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

    (not (trusted/authenticated-session? (:session opts)))
    {:problem :ocapn/session-not-authenticated}

    (not (target? (:remote/target opts)))
    {:problem :ocapn/target-invalid}))

(defn connected-reference
  "Construct an opaque live reference from an already authenticated CapTP
  session. This host API does not parse or resolve locator/sturdyref data."
  [opts]
  (if-let [error (connection-error opts)]
    (throw (ex-info "invalid OCapN live reference" error))
    (let [session (:session opts)
          info (assoc (trusted/session-description session)
                      :ocapn/profile draft-profile
                      :remote/target (:remote/target opts))]
      (if (trusted/request-capable-session? session)
        (LiveRequestReference. info session)
        (LiveReference. info session)))))

(defn live-reference?
  [x]
  (satisfies? RemoteReference x))

(defn request-capable-reference?
  [x]
  (satisfies? RemoteRequestReference x))

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

(defn durable-receipt
  "Construct the deterministic content-addressed remote durability claim for
  ENTRY in DATASPACE. This is inert evidence, not authority or independent
  proof that storage occurred."
  [dataspace entry]
  (incidence/append-durable-receipt dataspace entry))

(defn durable-receipt-error
  "Return a fail-closed diagnostic unless RECEIPT is the exact deterministic
  receipt for REQUEST's verified CID and dataspace."
  [request receipt]
  (let [verified (when (map? receipt) (incidence/verify-addressed receipt))
        expected (when (and (map? request)
                            (map? (:entry request))
                            (capabilities/non-empty-string? (:dataspace request)))
                   (try
                     (durable-receipt (:dataspace request) (:entry request))
                     (catch #?(:clj Exception :cljs :default) _ nil)))]
    (cond
      (not (map? receipt))
      {:problem :ocapn/durable-receipt-not-a-map}

      (not= #{:incidence/cid :incidence/block} (set (keys receipt)))
      {:problem :ocapn/durable-receipt-fields}

      (not (:ok? verified))
      {:problem :ocapn/durable-receipt-address-invalid
       :verification verified}

      (nil? expected)
      {:problem :ocapn/durable-receipt-request-invalid}

      (not= expected receipt)
      {:problem :ocapn/durable-receipt-mismatch
       :expected-cid (:incidence/cid expected)
       :actual-cid (:incidence/cid receipt)})))

(defn- append-args
  [request]
  (let [entry (:entry request)]
    ['append-incidence
     (:dataspace request)
     (:incidence/cid entry)
     (incidence/canonical-bytes (:incidence/block entry))]))

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
    {:ocapn/profile draft-profile
     :ocapn/op :op/deliver
     :ocapn/to (:remote/target (-reference-info reference))
     :ocapn/args (append-args request)
     :ocapn/answer-position false
     :ocapn/resolve-me false}))

(defn request-call
  "Build a settled-result request for an authenticated session driver.

  The driver, not the language kernel, allocates a resolver descriptor and any
  answer position, sends op:deliver, waits for settlement, and releases CapTP
  answer/import state."
  [reference request]
  (when-not (request-capable-reference? reference)
    (throw (ex-info "OCapN reference cannot request a result"
                    {:problem :ocapn/request-not-supported})))
  (if-let [error (append-request-error request)]
    (throw (ex-info "invalid OCapN incidence append" error))
    {:ocapn/profile draft-profile
     :ocapn/to (:remote/target (-reference-info reference))
     :ocapn/args (append-args request)
     :ocapn/result :settled}))

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

(defn durable-append-provider
  "Adapt a request-capable live reference to a remote durability-claiming
  incidence provider.

  Success requires a fulfilled CapTP result whose value is the exact
  content-addressed receipt for the requested dataspace and incidence CID.
  The claim is authenticated only to the extent of the injected live session;
  it is not an independent proof of physical persistence."
  [reference]
  (when-not (request-capable-reference? reference)
    (throw (ex-info "OCapN reference cannot request a result"
                    {:problem :ocapn/request-not-supported})))
  (fn append-over-ocapn-with-receipt! [request]
    (let [call (request-call reference request)
          settlement (-request! reference call)]
      (cond
        (and (map? settlement)
             (= :broken (:ocapn/status settlement)))
        (throw (ex-info "OCapN remote request broke"
                        {:problem :ocapn/remote-broken}))

        (not (and (map? settlement)
                  (= #{:ocapn/status :ocapn/value} (set (keys settlement)))
                  (= :fulfilled (:ocapn/status settlement))))
        (throw (ex-info "invalid OCapN settlement"
                        {:problem :ocapn/settlement-invalid}))

        :else
        (let [receipt (:ocapn/value settlement)]
          (if-let [error (durable-receipt-error request receipt)]
            (throw (ex-info "invalid OCapN durable receipt" error))
            {:ocapn/remote-durable? true
             :ocapn/session-id (:session/id (-reference-info reference))
             :incidence/cid (get-in request [:entry :incidence/cid])
             :receipt/cid (:incidence/cid receipt)}))))))

(ns kotoba.lang.capability-host
  "Host-call dispatch kernel for capability values (ADR-safe-capability-language,
  S4b; issue #263). Pure CLJC: a host wires its concrete provider handler
  through `guard-call`, which intersects the requested capability with the
  CACAO grants and local policy (kotoba.lang.capability-values/intersect-grants)
  at call time. A denied call NEVER reaches the handler; a granted call is
  invoked with the CONCRETE (post-intersection) capability. Both outcomes —
  and handler errors — leave a receipt, so an audit trail exists for every
  attempted host call. `journal` provides a zero-dependency append-only
  recorder for those receipts."
  (:require [kotoba.lang.capability-values :as values]))

(defn- ->receipt
  [cap now call outcome extra]
  (merge (assoc (values/receipt cap now call) :receipt/outcome outcome)
         extra))

(defn guard-call
  "Guarded host-call dispatch. OPTS:

  {:call         <keyword or symbol naming the host call>
   :requested    <requested capability value>
   :cacao-grants [<grant> ...]
   :local-policy <policy>
   :now          <date string>
   :record!      <fn receipt -> any, optional audit recorder>
   :handler      <fn concrete-cap -> result>}

  Runs kotoba.lang.capability-values/intersect-grants over the requested
  capability. On denial returns
  {:kotoba.host/ok? false :kotoba.host/denied <reason>
   :kotoba.host/receipt <denial receipt>} WITHOUT invoking the handler. On
  grant invokes the handler with the concrete capability and returns
  {:kotoba.host/ok? true :kotoba.host/result <result>
   :kotoba.host/receipt <receipt>}. When the handler throws, a receipt with
  :receipt/outcome :error is recorded and the exception is rethrown. In every
  case the receipt is built via kotoba.lang.capability-values/receipt and
  passed to :record! when provided."
  [{:keys [call requested cacao-grants local-policy now record! handler]}]
  (let [outcome (values/intersect-grants {:requested requested
                                          :cacao-grants cacao-grants
                                          :local-policy local-policy
                                          :now now})]
    (if (values/denied? outcome)
      (let [receipt (->receipt requested now call :denied
                               {:receipt/denied (:denied outcome)})]
        (when record! (record! receipt))
        {:kotoba.host/ok? false
         :kotoba.host/denied (:denied outcome)
         :kotoba.host/receipt receipt})
      (let [concrete outcome
            invoked (try
                      {:value (handler concrete)}
                      (catch #?(:clj Exception :cljs :default) e
                        {:error e}))]
        (if (contains? invoked :error)
          (let [e (:error invoked)
                receipt (->receipt concrete now call :error
                                   {:receipt/error
                                    #?(:clj (or (ex-message e) (str e))
                                       :cljs (str e))})]
            (when record! (record! receipt))
            (throw e))
          (let [receipt (->receipt concrete now call :ok nil)]
            (when record! (record! receipt))
            {:kotoba.host/ok? true
             :kotoba.host/result (:value invoked)
             :kotoba.host/receipt receipt}))))))

(defn journal
  "Atom-backed append-only receipt recorder. Returns
  {:record! <fn receipt -> receipt> :entries <fn -> [receipt ...]>} so a host
  gets an ordered audit trail with zero extra dependencies: pass :record! to
  `guard-call` and read the trail back with :entries."
  []
  (let [state (atom [])]
    {:record! (fn record! [receipt]
                (swap! state conj receipt)
                receipt)
     :entries (fn entries [] @state)}))

;; ---------------------------------------------------------------------------
;; Conformance-case runner (shared by the test suite and the bb gate)

(defn run-case
  "Runs one :host-dispatch conformance fixture. DATA is the guard-call opts
  EDN (minus :record!/:handler, which the runner supplies with probes).
  Returns {:outcome .. :handler-calls [concrete-cap ...] :receipts [..]}."
  [_tc data]
  (let [calls (atom [])
        {:keys [record! entries]} (journal)
        handler (fn [concrete]
                  (swap! calls conj concrete)
                  concrete)
        outcome (guard-call (assoc data :record! record! :handler handler))]
    {:outcome outcome
     :handler-calls @calls
     :receipts (entries)}))

(defn check-case
  "Runs a :host-dispatch conformance case and compares the outcome with the
  expectations declared in TC. Returns {:ok? bool :case id :actual ...}."
  [tc data]
  (let [{:keys [outcome handler-calls receipts]} (run-case tc data)
        receipt (first receipts)
        ok?
        (case (:kind tc)
          :accept
          (and (true? (:kotoba.host/ok? outcome))
               (= 1 (count handler-calls))
               (= 1 (count receipts))
               (true? (:ok? (values/validate-receipt receipt)))
               (= :ok (:receipt/outcome receipt))
               (or (not (contains? tc :expected-resource))
                   (= (:expected-resource tc)
                      (get-in receipt [:receipt/cap :cap/resource]))))

          :expect-denied
          (and (false? (:kotoba.host/ok? outcome))
               (= (:denied tc) (:kotoba.host/denied outcome))
               (empty? handler-calls)
               (= 1 (count receipts))
               (true? (:ok? (values/validate-receipt receipt)))
               (= (:denied tc) (:receipt/denied receipt)))

          false)]
    {:ok? ok? :case (:id tc) :actual outcome}))

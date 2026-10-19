(ns kotoba.cap-table
  "Per-run capability table for the S4b capability-passing slice
  (ADR-safe-capability-language, capability-passing style).

  Capability values flow through guest code as small opaque integer handles
  (i64-safe positive longs, first handle 1; 0 is never issued). The policy ∩
  grants ∩ requested intersection runs ONCE at acquisition
  (kotoba.lang.capability-host/guard-call), and the CONCRETE
  (post-intersection) capability is stored under the handle. A host call that
  receives a handle resolves it back to that stored concrete capability — no
  re-intersection is needed because the stored capability IS the intersected
  one — but expiry is re-checked against the use-time clock and the
  capability kind must match the op, so a handle can go stale or be presented
  to the wrong surface and still fail closed.

  Receipts: acquisition leaves a receipt with :receipt/call :cap/acquire
  (plus :receipt/cap-handle on grant); each use leaves its own receipt via
  the host-call path (kotoba.host-providers/host-call-with)."
  (:require [kotoba.lang.capability-host :as capability-host]
            [kotoba.lang.capability-values :as capability-values]))

(defn make-table
  "Empty per-run capability registry: handle -> concrete capability value."
  []
  (atom {:next-handle 1 :caps {}}))

(defn- store!
  "Store CONCRETE under a fresh handle and return the handle."
  [table concrete]
  (-> (swap-vals! table (fn [{:keys [next-handle caps]}]
                          {:next-handle (inc next-handle)
                           :caps (assoc caps next-handle concrete)}))
      first
      :next-handle))

(defn acquire!
  "Acquire a capability of KIND over RESOURCE against GRANTS/POLICY at NOW.

  Runs the kotoba.lang.capability-host/guard-call intersection ONCE. On grant
  the CONCRETE capability is stored in TABLE and the guard-call outcome map is
  returned with the fresh handle as :kotoba.host/result; the grant receipt
  additionally carries :receipt/cap-handle. On denial no handle is ever
  issued and the outcome carries :kotoba.host/denied plus the denial receipt.

  OPTS: {:kind <capability kind kw> :resource <resource or nil (:any)>
         :holder <optional holder string> :grants [<grant> ...]
         :policy <local policy> :now <date string> :record! <optional fn>}"
  [table {:keys [kind resource holder grants policy now record!]}]
  (let [assigned (atom nil)
        record-with-handle (fn [receipt]
                             (when record!
                               (record! (if-some [handle @assigned]
                                          (assoc receipt :receipt/cap-handle handle)
                                          receipt))))]
    (capability-host/guard-call
     {:call :cap/acquire
      :requested (capability-values/make-cap kind (or resource :any)
                                             (when holder {:holder holder}))
      :cacao-grants grants
      :local-policy policy
      :now now
      :record! record-with-handle
      :handler (fn [concrete]
                 (let [handle (store! table concrete)]
                   (reset! assigned handle)
                   handle))})))

(defn resolve-cap
  "Concrete capability stored under HANDLE, or nil for an unknown handle."
  [table handle]
  (get-in @table [:caps handle]))

(defn- expired-at?
  [expires now]
  (and (some? expires) (some? now) (neg? (compare expires now))))

(defn resolve-use
  "Resolve HANDLE for a host call of capability kind KIND at time NOW.

  The stored capability is already the policy ∩ grants ∩ requested
  intersection, so it is returned as-is — but fail closed when the handle was
  never issued ({:denied :unknown-cap-handle}), the capability kind does not
  match the op ({:denied :cap-kind-mismatch}), or the stored expiry has
  passed at use time ({:denied :expired}). On success returns
  {:ok? true :cap <concrete capability>}."
  [table handle kind now]
  (let [cap (resolve-cap table handle)]
    (cond
      (nil? cap) {:denied :unknown-cap-handle}
      (not= kind (:cap/kind cap)) {:denied :cap-kind-mismatch}
      (expired-at? (:cap/expires cap) now) {:denied :expired}
      :else {:ok? true :cap cap})))

(defn consume-use!
  "S2 runtime affinity: resolve HANDLE like `resolve-use`, then DROP it from
  TABLE on success so the same handle cannot authorize a second host call.
  Static affine checking is the primary gate for well-typed sources; this is
  defense-in-depth against tampered wasm or forged handles. A denial leaves
  the table unchanged."
  [table handle kind now]
  (let [resolved (resolve-use table handle kind now)]
    (if (:ok? resolved)
      (do (swap! table update :caps dissoc handle)
          resolved)
      resolved)))

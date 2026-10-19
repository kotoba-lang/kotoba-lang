(ns shinkansen.adapter
  "Deploy adapters — the deploy-targets increment (maturity axis G).

  A target is a SHAPE, not a plugin system: the app's manifest plus an
  adapter fn that turns emitted documents into whatever the target
  stores. shinkansen defines the shape; Cloudflare, a static bucket, or
  a local directory each implement it in their own repo — the framework
  ships none of them.

  The adapter contract:

    (adapt {:manifest … :documents […] :emit-fn (fn [doc] bytes)})
      → {:ok true :receipts [{:key … :cid … :bytes …} …]}
      | {:ok false :reason … :problems […]}

  Fail-closed: a document failing the manifest gate (viewport contract)
  is refused before any byte is emitted — an adapter cannot publish what
  publish refused. Receipts are content-addressed like everything else:
  a deploy is a set of CIDs, diffable, re-deployable, rollback = point
  the edge at the previous receipt set.

  Pure .cljc. The host owns actual upload; this owns the seam.")

(defn adapt
  "Run the adapter over the documents. `adapt-fn` is the target-specific
  implementation (key building, serialization). Every document is
  gated FIRST — shinkansen.viewport/audit via `audit-fn`, the same gate
  publish uses — then handed to adapt-fn. Returns receipts with the
  content CID for each emitted artifact."
  [{:keys [documents audit-fn adapt-fn]}]
  (let [gated (mapv (fn [d]
                      (let [r (audit-fn d)]
                        (if (:ok r)
                          {:ok true :doc d}
                          {:ok false :doc d :problems (:problems r)})))
                    (vec documents))
        refused (filterv #(not (:ok %)) gated)]
    (if (seq refused)
      {:ok false :reason :manifest-gate-refused :problems refused}
      (try
        (let [receipts (mapv adapt-fn (mapv :doc gated))]
          {:ok true :receipts receipts})
        (catch :default e
          {:ok false :reason :adapter-failed
           :error (or (ex-message e) (str e))})))))

(defn diff-receipts
  "Which receipts changed between two deploys? CID set difference —
  the deploy plan an adapter prints before uploading. :only-new is what
  the target needs to write; :missing is what the previous deploy had
  that this one dropped (rollback inventory)."
  [prev next]
  (let [prev-map (into {} (map (juxt :cid :key)) prev)
        next-map (into {} (map (juxt :cid :key)) next)]
    {:only-new (filterv #(not (contains? prev-map (:cid %))) next)
     :missing (filterv #(not (contains? next-map (:cid %))) prev)}))

(defn rollback-plan
  "Point the edge at the previous deploy: the receipt set IS the deploy.
  Returns the previous set tagged as the new desired state — the host
  applies it; the framework records it."
  [prev-receipts]
  {:ok true :desired prev-receipts})

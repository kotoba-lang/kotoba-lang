(ns shinkansen.load
  "Per-document data loading — the data-loading increment (maturity axis B).

  A document declares a :load-fn; running it produces a DATA VALUE that
  is itself content-addressed (a station, like everything else in
  shinkansen). The document references the data CID; the host inlines the
  data (SSG/SSR) or the edge resolves it — the caller decides, the
  contract is the same.

  Two modes, matching the rendering-modes plan:

    :build  (SSG) — load runs ONCE at publish; the data CID is frozen
                    into the manifest. Fast, but the data is as-of build.
    :edge   (SSR) — load runs per request; the data CID changes with the
                    data. The edge owns when the load re-runs.

  Fail-closed: a load that throws is {:ok false :reason ...} with the
  error named — a failed load is never silently an empty value. A load
  that itself fetches external assets is the publish gate's business,
  not this namespace's.

  Pure .cljc. The :load-fn and :cid-fn are injected; nothing here touches
  the network or the store.")

(defn- text
  "The serialized form whose hash is the data CID. EDN, like the state
  chain — one text boundary across shinkansen."
  [v]
  (binding [*print-readably* true
            *print-namespace-maps* false]
    (pr-str v)))

(defn run-load
  "Run one document's load. Returns
    {:ok true :data <value> :data-cid \"…\" :mode :build|:edge}
  or {:ok false :reason :load-failed :error <message>}.
  `load-fn` receives the params map (path params, query, locale — what
  the edge knows). `cid-fn` is the injected hash."
  [{:keys [mode load-fn params cid-fn]}]
  (let [result (try
                 {:ok true :data (load-fn (or params {}))}
                 (catch :default e
                   {:ok false :reason :load-failed
                    :error (or (ex-message e) (str e))}))]
    (if (:ok result)
      (let [t (text (:data result))]
        (assoc result
               :data-cid (cid-fn t)
               :text t
               :mode (or mode :build)))
      result)))

(defn document-with-data
  "Attach a data station to a document manifest: the returned map carries
  :data-cid so the host can inline `<script>var __DATA_CID = …</script>`
  or resolve the data at the edge. Refuses nothing here — validation
  belongs to publish/manifest; this composes."
  [{:keys [document load-fn params cid-fn mode]}]
  (let [r (run-load {:mode mode :load-fn load-fn :params params :cid-fn cid-fn})]
    (if (:ok r)
      (assoc document :data-cid (:data-cid r) :data (:data r) :load-mode (:mode r))
      (assoc document :load-error (:reason r) :load-error-message (:error r)))))

(defn hydrate-bundle
  "The inline script the host appends to a document so the browser
  hydrates without a second fetch: one line, the data CID + the EDN text
  (readable by the same EDN reader the state chain uses). Self-contained
  by construction — the data rides IN the document, no CDN round-trip."
  [{:keys [data-cid text]}]
  (str "window.__SHINKANSEN_DATA = {cid: " (pr-str (str data-cid))
       ", edn: " (pr-str (str text)) "};"))

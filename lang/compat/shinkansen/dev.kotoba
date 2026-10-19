(ns shinkansen.dev
  "Dev server model — the dev-experience increment (maturity axis F).

  The CID model makes HMR almost trivial, and this namespace says how:
  every save produces NEW content, which is a new station — there is no
  mutation to hot-reload, only a new head to look at. So the dev loop is

    save → compile → new document CID → edge re-points → browser reloads

  The server itself is the HOST's business (HTTP loop, file watching);
  this namespace owns the dev contract the host implements:

    - snapshot: one pass of the dev loop, from sources to a resolvable
      tree + documents, with timing the host can print
    - errors: a failed compile is a NAMED document error rendered into
      the page (error-as-content), never a silent 500 — the browser
      shows what broke and why, the loop continues
    - verify: same shinkansen.viewport/audit and route resolution as
      publish — dev does not relax the contract, so what works in dev
      works at publish. Fail in dev what would fail in publish.

  Pure .cljc. The host injects compile fns; nothing here watches files.")

(defn snapshot
  "One dev-loop pass. `entries` are {:path … :html …} emitted documents;
  `tree` is the declared route tree; `cid-fn` the content-address hash.
  Returns {:ok true :documents [{:path … :cid …} …] :tree tree
           :ms <elapsed>}
  or {:ok false :problems [...]} when any document fails the SAME audit
  publish enforces — dev fails fast, publish never surprises."
  [{:keys [entries tree cid-fn audit-fn ms]}]
  (let [docs (mapv (fn [e]
                     (let [r (audit-fn e)]
                       (if (:ok r)
                         {:ok true :path (:path e) :cid (cid-fn (:html e))}
                         {:ok false :path (:path e)
                          :problems (:problems r)})))
                   (vec entries))
        bad (filterv #(and (contains? % :ok) (not (:ok %))) docs)]
    (if (seq bad)
      {:ok false :problems bad}
      {:ok true :documents docs :tree tree :ms (or ms 0)})))

(defn error-document
  "A failed compile becomes content: a minimal named-error page carrying
  the reason. The dev browser shows WHY it broke; the refresh loop keeps
  running. The error page itself must pass the viewport contract — a
  broken build does not excuse a broken phone layout."
  [{:keys [path reason detail cid-fn]}]
  (let [html (str "<!doctype html><html><head>"
                  "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                  "<title>dev error</title></head><body>"
                  "<h1>dev error</h1><p>path: " (str path) "</p>"
                  "<p>reason: " (str reason) "</p>"
                  (when detail (str "<pre>" (str detail) "</pre>"))
                  "</body></html>")]
    {:path path :html html :cid (cid-fn html)
     :error {:reason reason :detail detail}}))

(defn same-content?
  "Has this document's content changed since the last snapshot? CID
  equality answers it — a save that changes nothing costs nothing."
  [a b]
  (= (:cid a) (:cid b)))

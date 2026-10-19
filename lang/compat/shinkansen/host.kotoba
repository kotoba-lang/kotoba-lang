(ns shinkansen.host
  "The reference host — the one place the contracts are DRIVEN, not
  declared (maturity 2026-09-16: routes / load / render / actions / invoke /
  adapter / dev had tests and zero consumers).

  A request comes in as data and a response goes out as data; the
  transport (`shinkansen.serve` on node:http, or a Worker) parses bytes on
  the way in and writes them on the way out. What happens in between is
  every seam the framework already owns, in order:

    GET  <name>          routes/resolve-path → the artifact the name names
                         → load (a :query station) → render (:ssg verbatim,
                         :ssr = query, :isr = ssg + window) → layouts
                         composed outside-in → bytes with ETag = the CID
    POST <invoke-path>   body → invoke envelope; principal from
                         `principal-fn`, grant from `grant-fn` (headers →
                         opaque; the host's wire, never the framework's)
                         → invoke/dispatch | invoke/query | invoke/authorize
                         (:event) → the chain (host persistence) → JSON

  Fail-closed and named at every branch: an unresolvable name is a 404
  whose body says :no-match and the deepest segment; a leaf the tree names
  but the document set does not carry is a 500 that says so (a
  misconfiguration is not a missing page); an invocation nobody authorized
  is a 403 that says :no-authorizer, never a 200 because the request
  arrived.

  The name host and the content host differ on purpose (SPEC §1.7): a path
  is mutable, so a name answers `Cache-Control: no-cache` + `ETag: <cid>`
  and revalidates; `Link: <ipfs://cid>; rel=canonical` says where the
  identity lives. `{cid}.ipfs.*` is the immutable origin; this host never
  claims to be it.

  ctx:
    :tree          route tree (routes.cljc); a leaf's :document is a KEY
    :documents     {key {:html … :mode … :render-fn … :load-fn … :revalidate-seconds … :status …}}
                   (:status lets a dev rebuild failure serve its error document as a 500)
    :cid-fn        content-address hash (injected, as everywhere)
    :decl          actions declaration
    :app           {:state (atom {:db … :chain []}) :state-fn f}  chain persistence
    :authorize-fn  the seam (absent → every invocation is :no-authorizer)
    :principal-fn  (headers → did | nil)
    :grant-fn      (headers → grant | nil); default reads `Authorization: Bearer <opaque>`
    :invoke-path   default \"/invoke\"
    :dev?          inject the live-reload listener (dev bytes ≠ published bytes)

  Pure .cljc. No IO here."
  (:require [clojure.string :as str]
            [shinkansen.dev :as dev]
            [shinkansen.interaction :as interaction]
            [shinkansen.invoke :as invoke]
            [shinkansen.load :as load]
            [shinkansen.render :as render]
            [shinkansen.routes :as routes]))

;; ── JSON out (pure, nested) ───────────────────────────────────────────────

(defn- json-str [s]
  (str "\"" (-> (str s)
                (str/replace "\\" "\\\\")
                (str/replace "\"" "\\\"")
                (str/replace "\n" "\\n")
                (str/replace "\r" "\\r")
                (str/replace "\t" "\\t"))
       "\""))

(defn- kw-name [k]
  (if (keyword? k)
    (str (when-let [n (namespace k)] (str n "/")) (name k))
    (str k)))

(defn ->json
  "EDN → JSON text. Keywords become their `ns/name` string (the same
  vocabulary `interaction/event-id->action` uses), so `[:todo/add \"milk\"]`
  round-trips through a JSON client as `[\"todo/add\",\"milk\"]`. Sets and
  seqs are arrays; nil is null."
  [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (number? v) (str v)
    (keyword? v) (json-str (kw-name v))
    (string? v) (json-str v)
    (map? v) (str "{" (str/join "," (for [[k x] v] (str (json-str (kw-name k)) ":" (->json x)))) "}")
    (or (sequential? v) (set? v)) (str "[" (str/join "," (map ->json v)) "]")
    :else (json-str v)))

;; ── request pieces ────────────────────────────────────────────────────────

(defn- split-url
  "\"/todos?limit=5&x=1\" → [\"/todos\" {:limit \"5\" :x \"1\"}]. Values stay
  strings — the load-fn / render-fn coerces what it needs."
  [url]
  (let [[path q] (str/split (str url) #"\?" 2)
        params (if (str/blank? q)
                 {}
                 (into {} (for [kv (str/split q #"&")
                                :let [[k v] (str/split kv #"=" 2)]
                                :when (not (str/blank? k))]
                            [(keyword k) (or v "")])))]
    [path params]))

(defn- header [headers k]
  (some (fn [[hk hv]] (when (= (str/lower-case (kw-name hk)) k) hv)) headers))

(defn default-grant-fn
  "`Authorization: Bearer <opaque>` → the opaque string. What the string IS
  (a base64 EDN token model, a protobuf Biscuit, a session id) is the
  authorize-fn's business — this host carries it, it does not read it."
  [headers]
  (when-let [a (header headers "authorization")]
    (let [[scheme v] (str/split (str a) #"\s+" 2)]
      (when (and v (= "bearer" (str/lower-case scheme)))
        v))))

;; ── documents ─────────────────────────────────────────────────────────────

(def ^:const reload-script
  "Dev only: the listener that follows `shinkansen.serve`'s reload stream.
   Appended before </body>; dev bytes are NOT the published bytes."
  "<script>new EventSource('/__shinkansen/reload').onmessage=function(){location.reload();};</script>")

(defn- with-reload [html]
  (if (str/includes? html "</body>")
    (str/replace html "</body>" (str reload-script "</body>"))
    (str html reload-script)))

(defn- compose-layouts
  "Layouts accumulate root-first (routes/resolve-path); the outermost wraps
  last, so fold from the leaf outward."
  [html layouts params]
  (reduce (fn [inner layout] (layout inner params)) html (reverse layouts)))

(defn error-document
  "An error is content with a viewport (dev/error-document's rule holds in
  production: a broken page does not excuse a broken phone layout)."
  [{:keys [path reason detail cid-fn]}]
  (dev/error-document {:path path :reason reason :detail detail
                       :cid-fn (or cid-fn (fn [_] nil))}))

(defn etag-of
  "The strong ETag a CID makes: the quoted CID."
  [cid]
  (when cid (str "\"" cid "\"")))

(defn document-headers
  "The headers a NAME host answers for a document whose bytes have `cid`:
  ETag = the CID, Link rel=canonical at ipfs://, and a Cache-Control that
  revalidates (a name is mutable) unless the document declared :isr. Exposed
  so a host that serves bytes some other way (a Worker's asset binding) still
  answers the same identity headers — cloud-kotoba/app-kotoba-cloud, 2026-09-16."
  [{:keys [cid mode revalidate-seconds]}]
  (cond-> {"cache-control" (case mode
                             :isr (str "public, max-age=" (or revalidate-seconds 0)
                                       ", stale-while-revalidate=" (or revalidate-seconds 0))
                             "no-cache")}
    cid (assoc "etag" (etag-of cid)
               "link" (str "<ipfs://" cid ">; rel=\"canonical\""))))

(defn not-modified?
  "Does the request's If-None-Match name this CID? Then the bytes need not
  travel — a name host can answer 304 from the receipt alone, before it
  touches the bytes.

  A weak tag (`W/\"cid\"`) matches too: a CDN that compresses the response
  weakens the strong ETag it was given (measured 2026-09-17 on kotoba.cloud
  behind Cloudflare: curl saw \"cid\", a fetch with Accept-Encoding saw
  W/\"cid\"), and a browser sends back exactly what it received. Content
  identity is a claim about the decoded bytes, so weak comparison is the
  right comparison here — RFC 9110 §13.1.2 allows it for If-None-Match."
  [if-none-match cid]
  (boolean (and cid if-none-match
                (some #(= (str/replace (str/trim %) #"^W/" "") (etag-of cid))
                      (str/split (str if-none-match) #",")))))

(defn- html-response [status html {:keys [cid mode revalidate-seconds dev? etag-in]}]
  (let [html (if dev? (with-reload html) html)]
    (if (and (= 200 status) (not-modified? etag-in cid))
      {:status 304 :headers {"etag" (etag-of cid)} :body ""}
      {:status status
       :headers (merge {"content-type" "text/html; charset=utf-8"}
                       (document-headers {:cid cid :mode mode :revalidate-seconds revalidate-seconds}))
       :body html})))

(defn- serve-document
  [{:keys [documents cid-fn dev?]} {:keys [document layouts params]} query headers path]
  (let [doc (get documents document)
        params (merge query params)]
    (if (nil? doc)
      (html-response 500 (:html (error-document {:path path :reason :document-not-registered
                                                 :detail (str "route leaf " (pr-str document)
                                                              " is not in :documents")}))
                     {:dev? dev?})
      (let [loaded (when (:load-fn doc)
                     (load/run-load {:mode (if (= :ssr (:mode doc)) :edge :build)
                                     :load-fn (:load-fn doc) :params params :cid-fn cid-fn}))]
        (if (and loaded (not (:ok loaded)))
          (html-response 500 (:html (error-document {:path path :reason (:reason loaded)
                                                     :detail (:error loaded)}))
                         {:dev? dev?})
          (let [r (render/render-document {:mode (:mode doc) :render-fn (:render-fn doc)
                                           :html (:html doc) :params params
                                           :data (:data loaded) :cid-fn cid-fn})]
            (if-not (:ok r)
              (html-response 500 (:html (error-document {:path path :reason (:reason r)
                                                         :detail (:error r)}))
                             {:dev? dev?})
              (let [html (compose-layouts (:html r) layouts params)
                    cid (or (:document-cid r) (when cid-fn (cid-fn html)))]
                (html-response (or (:status doc) 200) html {:cid cid :mode (:mode r)
                                         :revalidate-seconds (:revalidate-seconds doc)
                                         :dev? dev?
                                         :etag-in (header headers "if-none-match")})))))))))

;; ── invoke ────────────────────────────────────────────────────────────────

(defn- normalize-input
  "JSON gives strings where the envelope wants keywords: :kind, and the
  event id at the head of an :action's event vector (`\"todo/add\"` →
  :todo/add, the interaction vocabulary). Everything else is verbatim."
  [input]
  (when (map? input)
    (let [kind (:kind input)
          input (assoc input :kind (if (string? kind) (keyword kind) kind))]
      (if (and (= :action (:kind input)) (vector? (:event input)) (string? (first (:event input))))
        (update input :event #(assoc % 0 (interaction/action->event-id (first %))))
        input))))

(defn envelope-from-request
  "HTTP body + headers → invoke envelope. The body names the artifact and
  the input; the headers name the principal and carry the grant; the URL
  names nothing (it is the transport)."
  [{:keys [body headers]} {:keys [principal-fn grant-fn]}]
  {:artifact (:artifact body)
   :principal (when principal-fn (principal-fn headers))
   :grant ((or grant-fn default-grant-fn) headers)
   :input (normalize-input (:input body))})

(def ^:const status-of
  "Refusal reason → HTTP status. 4xx for the caller's envelope, 403 for
   anything the seam refused (including :no-authorizer — a host with no
   authorizer refuses, it does not error), 422 for a well-formed action the
   declaration refuses, 500 for the host's own failures."
  {:artifact-required 400 :cidv0-refused 400 :artifact-not-a-cid 400 :input-required 400
   :unknown-kind 400 :action-needs-event 400 :query-needs-params 400 :event-needs-source 400
   :principal-not-a-did 400 :not-an-action 400 :not-a-query 400
   :no-authorizer 403 :denied 403 :authorizer-answer-not-a-decision 403
   :undeclared-event 422 :validation-failed 422
   :load-failed 500 :render-failed 500})

(defn- json-response [status v]
  {:status status
   :headers {"content-type" "application/json; charset=utf-8" "cache-control" "no-store"}
   :body (->json (dissoc v :grant))})

(defn- run-action [env {:keys [decl app cid-fn authorize-fn]}]
  (let [{:keys [state state-fn]} app
        {:keys [db chain]} @state
        r (invoke/dispatch decl env {:db db :state-fn state-fn :cid-fn cid-fn
                                     :prev-cid (:db-cid (last chain))
                                     :height (inc (count chain))
                                     :authorize-fn authorize-fn})]
    (when (:ok r)
      (swap! state (fn [s] (-> s (assoc :db (get-in r [:entry :db]))
                               (update :chain conj (dissoc (:entry r) :db))))))
    (if (:ok r)
      (json-response 200 (update r :entry dissoc :text))
      (json-response (get status-of (:reason r) 400) r))))

(defn- run-query [env {:keys [documents cid-fn authorize-fn]}]
  (let [doc (or (get documents (:artifact env))
                (some (fn [[_ d]] (when (= (:artifact d) (:artifact env)) d)) documents))
        load-fn (or (:load-fn doc) (fn [_] nil))
        r (invoke/query env {:authorize-fn authorize-fn :cid-fn cid-fn :load-fn load-fn :mode :edge})]
    (if (:ok r)
      (json-response 200 (dissoc r :text))
      (json-response (get status-of (:reason r) 400) r))))

(defn- run-event [env {:keys [authorize-fn event-fn]}]
  (let [a (invoke/authorize env {:authorize-fn authorize-fn})]
    (if-not (:ok a)
      (json-response (get status-of (:reason a) 400) a)
      (let [applied (if event-fn (event-fn (:envelope a)) {:ok true :applied false})]
        (json-response 200 (assoc applied :effect (:effect a) :receipt (:receipt a)))))))

(defn handle-invoke [req ctx]
  (let [env (envelope-from-request req ctx)]
    (case (get-in env [:input :kind])
      :action (run-action env ctx)
      :query (run-query env ctx)
      :event (run-event env ctx)
      ;; not one of the three: let check-envelope name it
      (let [chk (invoke/check-envelope env)]
        (json-response (get status-of (:reason chk) 400) chk)))))

;; ── the entry ─────────────────────────────────────────────────────────────

(defn handle
  "One request → one response, as data.
     req: {:method \"GET\"|\"POST\"|… :url \"/path?q\" :headers {…} :body <parsed JSON or nil>}
     →    {:status n :headers {…} :body string}"
  [{:keys [method url headers] :as req} {:keys [tree invoke-path dev? cid-fn] :as ctx}]
  (let [[path query] (split-url url)
        method (str/upper-case (str method))
        invoke-path (or invoke-path "/invoke")]
    (cond
      (= path invoke-path)
      (if (= "POST" method)
        (handle-invoke req ctx)
        {:status 405 :headers {"allow" "POST" "content-type" "application/json"}
         :body (->json {:ok false :reason :method-not-allowed :allow ["POST"]})})

      (not (contains? #{"GET" "HEAD"} method))
      {:status 405 :headers {"allow" "GET, HEAD"} :body ""}

      :else
      (let [r (routes/resolve-path tree path)]
        (if-not (:ok r)
          (html-response 404 (:html (error-document {:path path :reason (:reason r)
                                                     :detail (str "deepest matched: " (pr-str (:deepest r)))
                                                     :cid-fn cid-fn}))
                         {:dev? dev?})
          (let [resp (serve-document ctx r query headers path)]
            (if (= "HEAD" method) (assoc resp :body "") resp)))))))

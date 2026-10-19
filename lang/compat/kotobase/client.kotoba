(ns kotobase.client
  "Promise-based ClojureScript client for the kotobase.net tenant Datom plane
  (`ai.gftd.apps.kotobase.datomic.*`). Runs in both the browser SPA and the
  cljs Workers (workerd/node) — global `fetch` + Web Crypto are present in both.

  Reads (`q`/`datoms`/`pull`) mint a read CACAO by default (the operator
  yoro-social db is private); pass `:public? true` to skip auth when the graph
  is registered Public. Writes (`transact`) mint a write CACAO. The single
  client identity IS the operator Ed25519 key, so `canonical-graph` resolves
  the operator's own `kotobase/db/<operator-did>/<db-name>` (the only writable
  namespace).

  ── CACAO auth profiles (`make-client` `:auth-profile`) ─────────────────────
  The kotobase.net APEX (net-kotobase clj-edge, cf-wasm cutover 2026-07-08)
  gates every datomic.* call on a CACAO that (validate-cacao, live-probed
  2026-07-09):
    1. carries the `kotoba://can/kotobase:pin` capability,
    2. carries a `kotoba://graph/` scope equal to the ISSUER DID (a graph-CID
       scope without the issuer DID is REJECTED — \"CACAO graph scope does
       not include issuer DID\"),
    3. arrives with an `x-kotoba-did` header matching the issuer, and
    4. uses a FRESH nonce per request (the edge records nonces in B2 for
       replay protection — reusing one 401s).
  This ns's pre-cutover mint (operation capability + graph-CID scope) fails
  checks 1–2, so every stock caller was getting
  `401 {\"ok\":false,\"error\":\"Unauthorized\"}` against the apex.

  `:auth-profile :apex` (the DEFAULT) mints the edge shape: primary capability
  `kotobase:pin`, the operation capabilities (`datom:read` /
  `datom:transact`+`tx:create`) riding along as extras, graph scope = issuer
  DID — the same shape yoro-ui.studio.genko-store proved live against the
  apex (app-aozora ab21923, 2026-07-09). Request addressing is unchanged: the
  body still names the canonical graph CID; only the CACAO scope moved. Every
  mint (including each retry attempt) gets a fresh nonce.

  `:auth-profile :legacy` keeps the pre-cutover byte shape (operation
  capability primary, `kotoba://graph/<graph-cid>` scope, no kotobase:pin)
  for endpoints that still verify it — e.g. a pod/tenant-worker lineage that
  pre-dates the apex edge. Same fn signatures either way; the profile only
  changes the minted CACAO.

  ── direct transport (`make-client` `:transport :direct-v1`) ────────────────
  Default `:transport` is `:xrpc` (everything above this paragraph — POSTs
  `/xrpc/ai.gftd.apps.kotobase.datomic.<method>`, JSON envelope, apex/legacy
  CACAO). `:direct-v1` instead talks straight to a `kotoba-lang/
  kotobase-storage-d1` deployment's own native surface (`/v1/transact` /
  `/v1/q` / `/v1/pull` / `/v1/datoms` / `/v1/fold` / `/v1/view`) — no XRPC
  edge in the path at all. Same six fn signatures either way; only the wire
  shape and the RESPONSE SHAPE change:

  * ref addressing: XRPC keys reads/writes by a content-addressed `graph`
    CID; direct-v1 sends the LITERAL `kotobase/db/<did>/<db-name>` string in
    an `x-kotobase-ref` header (storage-d1's own `authorize()` derives the
    tenant purely from the ref prefix + the CACAO issuer, ADR-2607260940).
  * CACAO capability: storage-d1's own gate checks for the literal resource
    `kotoba://can/datom:transact` (writes) / `kotoba://can/graph:query`
    (reads) — DIFFERENT strings from the XRPC vocabulary's `datom:read`/
    `datom:transact`+`tx:create` — and has no `kotobase:pin` concept and NO
    anonymous/public-read path (every direct-v1 call needs a `:secret-key`
    client; `:public-reads?` is XRPC-only).
  * body/response: raw `application/edn`, not a JSON envelope — direct-v1
    responses are `kotobase.datomic`'s OWN native shapes, not the XRPC
    handler's (kotobase-server's `do-transact`/`do-fold`/etc. are a SEPARATE
    implementation with different field names — e.g. `transact` returns
    `{:db-before :db-after :tx-data :tempids :novelty-size}`, not XRPC's
    `{:commit :datom_count :novelty_size}`). `datoms`/`view` happen to share
    the `{:e :a :v_edn :added}` row shape either transport uses, so those two
    are wrapped to the SAME `.-datoms`/`.-rows` JS shape callers already
    read; `transact`/`fold`/`pull`/`q` are NOT byte-compatible with XRPC —
    read this ns's `v1-*` fn docstrings for their exact native shape before
    depending on a field."
  (:require ["@noble/curves/ed25519.js" :refer [ed25519]]
            [cljs.reader :as reader]
            [kotoba.lang.text :as str]
            [kotobase.cid :as cid]
            [kotobase.cacao :as cacao]))

(def ^:private datomic-ns "ai.gftd.apps.kotobase.datomic")
(def ^:private store-ns "net.kotobase.store")

(def ^:private user-agent
  "**すべての外向きリクエストに名乗らせる。**

  Cloudflare Workers の `fetch` は User-Agent を既定で付けない。その結果
  kotobase の edge には UA 空のまま届き、実測 2026-08-14、aozora PDS の
  datoms が上流で 429 になった:

    datoms 429: {\"error\":\"Too Many Requests\",
                 \"details\":\"empty User-Agent datoms traffic is rate limited\"}

  PDS は describeRepo 1 回につき 12 本前後の keyed read を投げるので、
  名乗らないクライアントとしては真っ先に絞られる側だった。**429 の理由は
  応答本文に書かれていて、status だけ見ていると分からない。**

  ここで直すのは名乗りであって呼び出し回数ではない —— 増幅そのものは別件。"
  "kotobase-client/1 (+https://github.com/kotoba-lang/kotobase-client)")

(defn make-client
  "opts: :endpoint (e.g. \"https://kotobase.net\"), :operator-did (CACAO
  audience, e.g. \"did:web:kotobase.net\"), and an identity — either
  :secret-key (32-byte Uint8Array seed = operator write identity) or, for a
  read-only AppView against a Public graph, :did (the operator DID, used only
  to derive the graph CID) + :public-reads? true. :fetch-fn optional.
  :auth-profile optional — :apex (default; the kotobase.net edge CACAO shape)
  or :legacy (pre-cutover shape; see ns docstring).
  :transport optional — :xrpc (default) or :direct-v1 (a kotobase-storage-d1
  deployment's own native surface, no XRPC edge; see ns docstring — needs
  :secret-key, :public-reads? is XRPC-only).
  :tenant-id optional — when set, every CACAO this client mints carries
  `kotoba://tenant/<tenant-id>` in its SIGNED resources. `kotobase-server`'s
  `verify-grant` refuses a grant without it under
  `:require-tenant-binding? true`; absent, nothing changes."
  [{:keys [endpoint secret-key operator-did fetch-fn did public-reads? auth-profile
           transport tenant-id biscuit-fn]}]
  (when (and (nil? secret-key) (nil? did) (nil? biscuit-fn))
    (throw (js/Error. "make-client needs :secret-key, :did, or :biscuit-fn")))
  {:endpoint (str/replace endpoint #"/+$" "")
   :secret-key secret-key
   :operator-did operator-did
   :public-reads? (boolean public-reads?)
   :auth-profile (or auth-profile :apex)
   :transport (or transport :xrpc)
   :tenant-id tenant-id
   ;; Biscuit auth-profile (ADR-2608291500): when present, `(biscuit-fn db_name)`
   ;; returns Promise<base64url-token> for the graph kotobase/db/<tenant>/<db_name>
   ;; and the XRPC transport sends `Authorization: Biscuit <t>` instead of
   ;; minting a CACAO. The edge derives the write/read graph from `db_name`
   ;; against the Biscuit's verified tenant, so a Biscuit client needs no
   ;; Ed25519 signing key — the datom plane went Biscuit-required (net-kotobase
   ;; ADR-2608280230) and CACAO no longer authenticates ordinary tenant
   ;; reads/writes there.
   :biscuit-fn biscuit-fn
   :fetch (or fetch-fn js/fetch)
   :did (or did (some-> secret-key (as-> sk (cid/did-key-from-ed25519-pub (.getPublicKey ed25519 sk)))))})

(def ^:private db-name-note
  "Why authenticated XRPC read bodies carry `db_name` beside `graph`.

  The apex used to resolve a read from the content-addressed `graph` CID
  alone. Since ADR-2607279500 it bridges `datomic.*` to
  kotobase-storage-d1, which addresses refs as the literal string
  `kotobase/db/<did>/<name>` and cannot invert a CID back into one. A read
  carrying only the CID therefore answers 404 `UnknownGraphCid` — and this
  client's own `empty-on-404` then turns that into an EMPTY RESULT, which is
  indistinguishable from an empty database. That is how every
  cloud-itonami-marketplace-* actor came back with zero rows against a store
  that had data.

  Measured against kotobase.net on 2026-07-28, same CACAO, four bodies:

    literal ref, no db_name   200
    literal ref + db_name     200
    CID graph, no db_name     404 UnknownGraphCid
    CID graph + db_name       200

  `transact` already sent `db_name`, which is why writes probed clean while
  reads did not. Sending both keeps the CID addressing the pod understands
  and gives the bridge the name it needs — neither side is guessed at."
  :see-adr-2607279500)

(def default-ttl-sec
  "How long a minted CACAO stays valid. 15 minutes, not 5.

  A Worker's `Date.now()` does NOT advance freely — Cloudflare freezes it at
  the time of the last I/O, so a cold isolate can mint with a clock that is
  minutes stale. With a 5-minute window that CACAO can already be expired
  when it arrives, and the apex answers a bare 401 — intermittently, only on
  cold isolates, which reads as a flaky network rather than a bug.

  Invisible until the apex began actually VERIFYING CACAOs
  (ADR-2607279500). The window is the cheap lever: an `iat` too far in the
  FUTURE is still rejected at 300s of skew, and a stale clock errs into the
  past, so widening `exp` costs nothing on that side."
  900)

(defn request-cacao
  "Mint ONE request's CACAO (fresh nonce — never reuse across requests or
  retry attempts; the apex records nonces for replay protection) granting
  `op-caps` (e.g. [\"datom:read\"] / [\"datom:transact\" \"tx:create\"]) over
  `graph`, shaped per the client's :auth-profile (ns docstring). nil when the
  client has no :secret-key. Public so callers with custom methods (or a
  bespoke transport) can mint the same auth the built-in q/datoms/pull/
  transact/fold use.

  opts: `:ttl-sec`, and `:purpose` — a human-readable reason recorded in the
  SIWE `statement` field, which is part of the signed payload. Both profiles
  carry the client's `:tenant-id` when it has one."
  ([client op-caps graph] (request-cacao client op-caps graph nil))
  ([client op-caps graph {:keys [ttl-sec purpose] :or {ttl-sec default-ttl-sec}}]
   ;; Biscuit clients carry their auth in the Authorization header, not a
   ;; minted CACAO — skip signing entirely (they may have no secret-key).
   (when-let [secret-key (and (nil? (:biscuit-fn client)) (:secret-key client))]
     (:cacao-b64
      (if (= :legacy (:auth-profile client))
        (cacao/mint-cacao {:secret-key secret-key
                           :aud (:operator-did client)
                           :capability (first op-caps)
                           :extra-capabilities (vec (rest op-caps))
                           :graph graph
                           :tenant (:tenant-id client)
                           :statement purpose
                           :ttl-sec ttl-sec})
        ;; :apex — kotobase:pin primary, op caps as extras, ISSUER DID scope.
        ;;
        ;; `:sig-encoding :base64`, NOT the base64url default. `v1-cacao`
        ;; already passed this (kotobase-client #12); the apex path did not,
        ;; and it did not matter while the apex merely TRUSTED the
        ;; x-kotoba-did header. Since the edge began verifying and bridging to
        ;; kotobase-storage-d1, a base64url signature decodes to different
        ;; bytes, fails Ed25519 verification, and comes back as the same bare
        ;; 401 as every other cause.
        ;;
        ;; Found by diffing the CACAO this client mints against one a JVM
        ;; client mints from the SAME seed: every payload field identical —
        ;; header, issuer, audience, domain, timestamps, statement, resources
        ;; — and `sig ok?` false on this one alone.
        (cacao/mint-cacao {:secret-key secret-key
                           :aud (:operator-did client)
                           :capability "kotobase:pin"
                           :extra-capabilities (vec op-caps)
                           :graph (:did client)
                           :tenant (:tenant-id client)
                           :statement purpose
                           :ttl-sec ttl-sec
                           :sig-encoding :base64}))))))

(defn- read-cacao
  "A read CACAO for `graph`, or nil when the client reads Public graphs /
  has no signing key (then the request is sent unauthenticated)."
  [client graph]
  (when-not (:public-reads? client)
    ;; BOTH vocabularies. The XRPC edge checks `datom:read`; since
    ;; ADR-2607279500 that edge BRIDGES datomic.* to kotobase-storage-d1,
    ;; whose own gate checks the literal `graph:query` and has no
    ;; `kotobase:pin` concept. A CACAO carrying only the XRPC vocabulary
    ;; reaches D1 and is rejected as `Unauthenticated` — which is what took
    ;; every cloud-itonami-marketplace-* read down on 2026-07-27 the moment
    ;; production set KOTOBASE_D1_*. Extra capabilities ride along harmlessly
    ;; on the path that does not check them, so carrying both is strictly
    ;; safer than guessing which side will answer.
    (request-cacao client ["datom:read" "graph:query"] graph)))

(defn- post
  "POST one datomic method. Returns a Promise of the parsed JSON body, or
  rejects with the HTTP status + text on non-2xx (the edge/pod return
  text/plain for auth/guard rejections, so we surface status before parsing).
  ALSO rejects on a 2xx response whose parsed body carries `\"ok\":false`
  (the handler dispatch shape every kotobase XRPC method uses for a LOGICAL
  failure — e.g. transact's ConcurrentWriteConflict on a lost head-CAS race —
  served over an ordinary HTTP 200, since the request itself was handled
  fine). A caller checking only `res.ok`/HTTP status, as this fn used to,
  treats that as success: confirmed live 2026-07-03, an etzhayyim actor
  mass-identify's createRecord calls kept returning a fabricated 200+uri+cid
  while the underlying transact had actually failed and reported so in its
  own body — every downstream caller (aozora.pds.repo/create-record chief
  among them) built its response from LOCALLY computed values instead of the
  transact result, masking the failure end-to-end. Fixing it here, once,
  covers every caller of transact/datoms/q/pull uniformly."
  [client method body cacao-b64]
  (let [{:keys [endpoint fetch did biscuit-fn]} client
        ;; Resolve auth first: a Biscuit client fetches a fresh (cached) token;
        ;; a CACAO client already minted one. The default (no biscuit-fn) path
        ;; is byte-for-byte unchanged.
        ;; A Biscuit is scoped to exactly one graph = kotobase/db/<tenant>/<db_name>,
        ;; so the fn is told which db_name this call targets and returns a token
        ;; bound to it (mint/cache per db_name). Reads and writes both carry
        ;; :db_name in the body; graph-only calls pass nil.
        auth-promise (if biscuit-fn
                       (-> (biscuit-fn (:db_name body))
                           (.then (fn [tok] {:header (str "Biscuit " tok) :cacao nil})))
                       (js/Promise.resolve {:header (when cacao-b64 (str "CACAO " cacao-b64))
                                            :cacao cacao-b64}))]
    (-> auth-promise
        (.then
         (fn [{:keys [header cacao]}]
           (let [headers #js {"content-type" "application/json"
                              "user-agent" user-agent}
                 full-body (cond-> body cacao (assoc :cacao_b64 cacao))]
             (when header
               (aset headers "authorization" header)
               (when did (aset headers "x-kotoba-did" did)))
             (-> (fetch (str endpoint "/xrpc/" datomic-ns "." method)
                        #js {:method "POST"
                             :headers headers
                             :body (js/JSON.stringify (clj->js full-body))})
                 (.then (fn [^js res]
                          (.then (.text res)
                                 (fn [text]
                                   (if-not (.-ok res)
                                     (let [e (js/Error. (str method " " (.-status res) ": " text))]
                                       (set! (.-status e) (.-status res))
                                       (throw e))
                                     (let [^js parsed (if (seq text) (js/JSON.parse text) #js {})]
                                       (if (false? (.-ok parsed))
                                         (let [e (js/Error. (str method " " (.-status res) " ok:false "
                                                                 (or (.-error parsed) "LogicalFailure")
                                                                 (when (.-message parsed) (str ": " (.-message parsed)))))]
                                           (set! (.-status e) (.-status res))
                                           (set! (.-body e) parsed)
                                           (throw e))
                                         parsed))))))))))))))

(defn- post-store
  "POST one portable IStore method with a freshly minted apex CACAO. Returns
  the parsed wire envelope; public store helpers below project it back to the
  five-method IStore contract while remaining Promise-based."
  [client method body write?]
  (when-not (:secret-key client)
    (throw (js/Error. "IStore access needs a :secret-key client")))
  (let [{:keys [endpoint fetch did]} client
        ;; See `read-cacao`: both vocabularies, because the XRPC edge may
        ;; answer or may bridge to kotobase-storage-d1, which names the same
        ;; permissions differently.
        caps (if write? ["datom:transact" "tx:create"] ["datom:read" "graph:query"])
        cacao-b64 (request-cacao client caps did)
        headers #js {"content-type" "application/json"
                     "user-agent" user-agent
                     "authorization" (str "CACAO " cacao-b64)
                     "x-kotoba-did" did}]
    (-> (fetch (str endpoint "/xrpc/" store-ns "." (name method))
               #js {:method "POST" :headers headers
                    :body (js/JSON.stringify (clj->js body))})
        (.then (fn [^js response]
                 (.then (.text response)
                        (fn [text]
                          (let [^js parsed (if (seq text) (js/JSON.parse text) #js {})]
                            (if (and (.-ok response) (not (false? (.-ok parsed))))
                              parsed
                              (let [error (js/Error.
                                           (str "store." (name method) " "
                                                (.-status response) ": "
                                                (or (.-error parsed) text)))]
                                (set! (.-status error) (.-status response))
                                (set! (.-body error) parsed)
                                (throw error)))))))))))

(defn store-put [client coll key value]
  (-> (post-store client :put {:coll coll :key key :val value} true)
      (.then (fn [^js body] (js->clj (.-val body) :keywordize-keys true)))))

(defn store-get [client coll key]
  (-> (post-store client :get {:coll coll :key key} false)
      (.then (fn [^js body] (js->clj (.-val body) :keywordize-keys true)))))

(defn store-list [client coll]
  (-> (post-store client :list {:coll coll} false)
      (.then (fn [^js body] (js->clj (.-keys body))))))

(defn store-append [client stream event]
  (-> (post-store client :append {:stream stream :event event} true)
      (.then (fn [^js body] (js->clj (.-event body) :keywordize-keys true)))))

(defn store-read [client stream since]
  (-> (post-store client :read {:stream stream :since (or since 0)} false)
      (.then (fn [^js body] (js->clj (.-events body) :keywordize-keys true)))))

(defn store-xrpc
  "Host-injected xrpc function for `kotobase.kotobase/kotobase-store`. Its
  results are Promises of the bare IStore values, suitable for the
  promise-aware code-graph adapter."
  [client]
  (fn [method params]
    (case method
      :put (store-put client (:coll params) (:key params) (:val params))
      :get (store-get client (:coll params) (:key params))
      :list (store-list client (:coll params))
      :append (store-append client (:stream params) (:event params))
      :read (store-read client (:stream params) (:since params))
      (js/Promise.reject (js/Error. (str "unknown IStore method " method))))))

;; ── direct-v1 transport (kotobase-storage-d1's own native surface) ──────────
;; See ns docstring's "direct transport" section for the wire-shape contract.

(def ^:private v1-method->path
  {"transact" "/v1/transact" "q" "/v1/q" "pull" "/v1/pull"
   "datoms" "/v1/datoms" "fold" "/v1/fold" "view" "/v1/view"})

(defn- v1-ref
  "The LITERAL ref direct-v1 addresses by (unlike XRPC's content-addressed
  `graph` CID) — storage-d1's own authorize() derives the tenant from this
  string's `kotobase/db/<did>/` prefix, matched against the CACAO issuer."
  [client db-name]
  (str "kotobase/db/" (:did client) "/" db-name))

(defn- v1-cacao
  "Mint a CACAO shaped for kotobase-storage-d1's own direct authorize() gate
  (kotoba-lang/kotobase-storage-d1 `worker.mjs` TX_CAPABILITY/
  READ_CAPABILITY) — a single literal resource, `kotoba://can/datom:transact`
  or `kotoba://can/graph:query`, NOT the XRPC vocabulary's `datom:read`/
  `datom:transact`+`tx:create`, and no `kotobase:pin`. nil when the client
  has no :secret-key (direct-v1 has no anonymous-read path, so a nil here
  means the request WILL 401 — unlike XRPC's :public-reads? escape hatch)."
  [client capability ref ttl-sec]
  (when-let [secret-key (:secret-key client)]
    (:cacao-b64
     (cacao/mint-cacao {:secret-key secret-key :aud (:operator-did client)
                        :capability capability :graph ref :ttl-sec ttl-sec
                        :sig-encoding :base64}))))

(defn- v1-post
  "POST one method's EDN body straight to a kotobase-storage-d1 deployment.
  Returns a Promise of the parsed EDN VALUE (whatever `kotobase.datomic`
  natively returns for that op — see each `v1-*` caller below for the exact
  shape) — unlike XRPC's `post`, there is no `{:ok ...}` envelope to check:
  storage-d1's own worker.mjs only ever uses a non-2xx status for failure."
  [client method edn-body ref cacao-b64]
  (let [{:keys [endpoint fetch did]} client
        headers #js {"content-type" "application/edn" "x-kotobase-ref" ref
                     "user-agent" user-agent}]
    (when cacao-b64
      (aset headers "authorization" (str "CACAO " cacao-b64)))
    (-> (fetch (str endpoint (get v1-method->path method))
               #js {:method "POST" :headers headers :body (pr-str edn-body)})
        (.then (fn [^js res]
                 (.then (.text res)
                        (fn [text]
                          (if-not (.-ok res)
                            (let [e (js/Error. (str method " " (.-status res) ": " text))]
                              (set! (.-status e) (.-status res))
                              (throw e))
                            (reader/read-string text)))))))))

(defn- v1-write-cacao [client ref ttl-sec] (v1-cacao client "datom:transact" ref ttl-sec))
(defn- v1-read-cacao [client ref] (when-not (:public-reads? client) (v1-cacao client "graph:query" ref 300)))

(defn- v1-datoms-response
  "Wrap a raw `kotobase.datomic/datoms` row vector ({:e :a :v_edn :added}
  maps — the SAME shape XRPC's `do-datoms` uses) into the `.-datoms` JS
  shape every existing `datoms` caller already reads."
  [rows]
  (clj->js {:datoms (vec rows)}))

(defn- v1-q-response
  "Wrap a raw `kotobase.datomic/q` result into `.-rows_edn` (an array of
  arrays, `decode-edn-scalar`-compatible per cell) — ONLY correct for a
  :relation-shaped query (multiple :find vars, the common case and the only
  shape any current caller sends); a :scalar/:tuple/:collection-shaped
  query's raw result is NOT a collection-of-tuples and this will misshape
  it. Callers using a non-relation `:find` spec must inspect the raw value
  themselves instead of relying on this wrapping."
  [raw]
  (clj->js {:rows_edn (mapv (fn [row] (if (sequential? row) (vec row) [row])) raw)}))

;; A graph with no Datomic/IPNS head yet (never written) reads as empty rather
;; than an error — mirrors kotoba.cljc fetchDatoms' 404 handling.
(def ^:private not-empty-404
  "404 bodies that mean MISCONFIGURED, not empty.

  `UnknownGraphCid` is the apex saying it cannot resolve the ref you named —
  since the D1 bridge landed, a content-addressed CID with no `db_name` is
  exactly that. Turning it into an empty result made every
  cloud-itonami-marketplace-* actor report zero rows against a store that had
  data, and an empty database is a perfectly ordinary thing for a caller to
  see, so nothing anywhere raised an eyebrow.

  A store that cannot be read must not look like an empty store."
  #{"UnknownGraphCid" "UnknownRef" "UnknownDatabase"})

(defn- misconfigured-404? [^js err]
  (boolean (some #(str/includes? (str (.-message err)) %) not-empty-404)))

(defn- empty-on-404
  "A 404 means the thing is not there — usually a database with nothing in it
  yet, which is legitimately empty. But see `not-empty-404`: some 404s mean
  the ref could not be resolved at all, and those must propagate."
  [empty-val p]
  (.catch p (fn [^js err]
              (if (and (= 404 (.-status err)) (not (misconfigured-404? err)))
                empty-val
                (throw err)))))

;; The kotoba-wasm tenant worker intermittently 500s with "Invalid array buffer
;; length" while (re)loading a growing graph's blocks from R2 — a transient
;; fault in the WASM db-load allocation, fast-failing (~0.2 s) and uncorrelated
;; across isolates (ADR-2607022330 addendum; net-kotobase kotoba-wasm). Reads are
;; idempotent, so retrying a transient 5xx a few times turns the ~40% flake into
;; effectively 0 without touching the engine. Writes opt in (`:retry?`) and MUST
;; be idempotent (keyed re-assert) to be safe.
(defn- transient-5xx? [^js err]
  (let [s (.-status err)] (and (number? s) (>= s 500))))

(defn- jittered
  "backoff-ms ± up to 40%, so concurrent retriers don't hammer the same warm
  isolate in lockstep."
  [backoff-ms]
  (max 0 (long (* backoff-ms (+ 0.8 (* 0.4 (js/Math.random)))))))

(defn- with-retry
  "Retry a Promise-returning thunk on transient 5xx. Non-5xx (e.g. 404/403/401)
  reject immediately so empty-on-404 and auth handling are unaffected.

  Kept LIGHT on purpose. Measured behaviour: the wasm worker's failures are
  CORRELATED within any feasible synchronous window — spacing 5 retries over
  ~10 s failed at the SAME ~25% rate as a sub-second burst, just far slower. So
  aggressive backoff only adds latency; it can't break the floor (the graph is
  near the isolate memory limit and ~1-in-4 full-loads fail regardless of
  timing). This retry catches the genuinely-independent transients (smoothing
  the 40-90% spikes to the ~25% floor) while failing fast; jitter avoids
  lockstep. The durable server-side fix has since LANDED (verified 2026-07-17,
  ADR-2607167000 addendum 2): backend.kotobase.net is served by the
  kotobase-server cljc handler (hot-datoms — components_edn/limit honoured,
  range-pruned narrow reads), not the old whole-DB-rehydrating wasm build this
  paragraph described; kotobase.aozora.app runs kotobase-cljc-worker (same
  engine + immutable block cache + datomic.view). The retry stays as cheap
  insurance for transient 5xx, no longer as a workaround for O(graph) reads."
  ([thunk] (with-retry thunk 3 250))
  ([thunk tries backoff-ms]
   (-> (thunk)
       (.catch (fn [^js err]
                 (if (and (> tries 1) (transient-5xx? err))
                   (-> (js/Promise. (fn [res] (js/setTimeout res (jittered backoff-ms))))
                       (.then (fn [_] (with-retry thunk (dec tries)
                                        (min 1200 (* 2 backoff-ms))))))
                   (throw err)))))))

;; ── reads ────────────────────────────────────────────────────────────────────

(defn q
  "Query (EDN string) against the operator's `db-name`.

  THE TWO TRANSPORTS TAKE DIFFERENT LANGUAGES. This is not a wart to
  route around; it is what each backend implements, and calling one with
  the other's input FAILS SILENTLY:

    :apex (default)  a triple PATTERN, `[s p o]`, `nil` for a wildcard:
                     \"[nil \\\":mp.order/doc\\\" nil]\". The pod hands it
                     straight to `arrangement.query/query`.
    :direct-v1       real datalog — the string is `read-string`'d and
                     sent as `{:query .. :args []}`.

  Send a `[:find .. :where ..]` datalog query to the apex and it parses
  as a six-element vector, matches nothing, and returns `rows: []` —
  indistinguishable from an empty database. That cost
  `cloud-itonami-marketplace-*` a day and a whole-graph `datoms` scan on
  every request, on the strength of this docstring's previous first line
  (\"Datalog query (EDN string)\"), which was true for one transport and
  read as true for both.

  Rows come back `{s p o}` from the apex — the same positions as a
  datom's `{e a v}`. Under :direct-v1 the response is reshaped to
  `.-rows_edn` for a :relation-shaped query only — see `v1-q-response`.

  Reads are AUTHENTICATED: `:public? true` skips the CACAO mint and the
  apex answers 401. A key-derived graph is still someone's graph."
  ([client db-name query-edn] (q client db-name query-edn nil))
  ([client db-name query-edn {:keys [limit offset public?]}]
   (if (= :direct-v1 (:transport client))
     (let [ref (v1-ref client db-name)]
       (empty-on-404 #js {:rows_edn #js []}
                     (with-retry #(-> (v1-post client "q" {:query (reader/read-string query-edn) :args []}
                                              ref (v1-read-cacao client ref))
                                     (.then v1-q-response)))))
     (let [graph (cid/canonical-graph (:did client) db-name)
           body (cond-> {:graph graph :db_name db-name :query_edn query-edn}
                  limit (assoc :limit limit)
                  offset (assoc :offset offset))]
       ;; mint INSIDE the retry thunk — a fresh nonce per attempt (apex replay
       ;; protection records nonces; reusing one across retries 401s).
       (empty-on-404 #js {:rows_edn #js []}
                     (with-retry #(post client "q" body
                                        (when-not public? (read-cacao client graph)))))))))

(defn datoms
  "Index scan (`:eavt` / `:aevt` / `:avet` / `:vaet`) over the operator db.
  `components` is an optional vector of EDN-string prefix components.
  `.-datoms` (row shape `{e a v_edn added}`) is identical across both
  transports."
  ([client db-name index] (datoms client db-name index nil))
  ([client db-name index {:keys [components limit public?]}]
   (if (= :direct-v1 (:transport client))
     (let [ref (v1-ref client db-name)
           ;; `components` are raw prefix VALUES (e.g. an entity-id string),
           ;; not individually EDN-encoded — matches the XRPC path's own
           ;; `components_edn` convention (kotobase-server's `do-datoms`
           ;; consumes it as `(vec components_edn)`, no per-element read-string).
           options (cond-> {:index (keyword (str/lower (str/replace index #"^:" "")))}
                     (seq components) (assoc :components (vec components))
                     limit (assoc :limit limit))]
       (empty-on-404 #js {:datoms #js []}
                     (with-retry #(-> (v1-post client "datoms" options ref (v1-read-cacao client ref))
                                     (.then v1-datoms-response)))))
     (let [graph (cid/canonical-graph (:did client) db-name)
           ;; Public browser reads are intentionally selected by exact graph
           ;; CID only.  Sending db_name would ask the edge to resolve a
           ;; tenant-owned name without a verified caller and is therefore
           ;; rejected.  Authenticated clients retain db_name for the D1
           ;; bridge, whose storage ref cannot be recovered from a CID.
           body (cond-> {:graph graph :index index}
                  (not (:public-reads? client)) (assoc :db_name db-name)
                  (seq components) (assoc :components_edn (vec components))
                  limit (assoc :limit limit))]
       (empty-on-404 #js {:datoms #js []}
                     (with-retry #(post client "datoms" body
                                        (when-not public? (read-cacao client graph)))))))))

(defn view
  "Rows of a fold-materialized view (ADR-2607166600 IVM): one server-side
  block read + fresh novelty merge, so it stays correct between folds.
  Response is `.-spec` + `.-rows` (rows shaped {e a v_edn added}) — IDENTICAL
  across both transports (XRPC's `do-view` and direct-v1's native `view`
  both merge kotobase-peer's `view-rows` unchanged; NOT `.-datoms` despite
  `datoms`' similar row shape — a different top-level field). The view must
  have been declared via `fold`'s `:views` opt; an undeclared view rejects
  with ViewNotFound (XRPC: post's ok:false path; direct-v1: non-2xx)."
  ([client db-name view-name] (view client db-name view-name nil))
  ([client db-name view-name {:keys [public?]}]
   (if (= :direct-v1 (:transport client))
     (let [ref (v1-ref client db-name)]
       ;; NO empty-on-404 (see XRPC branch's rationale below).
       (with-retry #(-> (v1-post client "view" {:view view-name} ref (v1-read-cacao client ref))
                       (.then clj->js))))
     (let [graph (cid/canonical-graph (:did client) db-name)
           body {:graph graph :view view-name}]
       ;; NO empty-on-404 (unlike datoms): a 404 head or ViewNotFound must
       ;; REJECT so consumers with richer fallbacks (attr scans, full reads)
       ;; actually take them — an empty-success here silently masks "view
       ;; missing" as "graph empty" (observed live: appview served empty
       ;; follower lists instead of falling back, ADR-2607167000 addendum).
       (with-retry #(post client "view" body
                          (when-not public? (read-cacao client graph))))))))

(defn pull
  "Pull `pattern-edn` for `entity` from the operator db. Under :transport
  :direct-v1 the response is `kotobase.datomic/pull`'s own native pull-result
  map (clj->js'd directly) — NOT XRPC's `{:attrs ...}` / `{:result_edn ...}`
  shape (do-pull's two response shapes, chosen by whether pattern_edn was
  sent); no current aozora-engine caller inspects a `pull` response field, so
  this has not needed reconciling with either XRPC shape."
  ([client db-name entity pattern-edn] (pull client db-name entity pattern-edn nil))
  ([client db-name entity pattern-edn {:keys [public?]}]
   (if (= :direct-v1 (:transport client))
     (let [ref (v1-ref client db-name)
           selector (if pattern-edn (reader/read-string pattern-edn) '[*])]
       (empty-on-404 #js {}
                     (with-retry #(-> (v1-post client "pull" {:selector selector :eid entity}
                                              ref (v1-read-cacao client ref))
                                     (.then clj->js)))))
     (let [graph (cid/canonical-graph (:did client) db-name)
           body (cond-> {:graph graph :db_name db-name :entity entity}
                  pattern-edn (assoc :pattern_edn pattern-edn))]
       (empty-on-404 #js {}
                     (with-retry #(post client "pull" body
                                        (when-not public? (read-cacao client graph)))))))))

;; ── writes ───────────────────────────────────────────────────────────────────

(defn transact
  "Transact an EDN tx-data string into the operator's `db-name`. The edge binds
  the write to `kotobase/db/<operator-did>/<db-name>` regardless of any
  client-supplied graph.

  `:retry?` retries a transient 5xx (the kotoba-wasm db-load flake). Set it ONLY
  when the tx is idempotent — a keyed re-assert (cardinality-one upsert) applied
  twice is a no-op, but an append with a fresh monotonic key (e.g. firehose seq)
  would duplicate. Default off.

  Under :transport :direct-v1, the response is `kotobase.datomic/transact`'s
  own native tx-report — `{:db-before :db-after :tx-data :tempids
  :novelty-size}` (dash-cased keys: use `(aget resp \"db-after\")`, not
  `.-db-after`) — NOT XRPC's `do-transact` shape (`{:commit :previous_commit
  :datom_count :novelty_size}`; a DIFFERENT implementation, not just a
  rename — do-transact never resolves tempids). A `novelty_size` (snake_case)
  ALIAS of `:novelty-size` is added so `aozora.pds.per-actor`'s best-effort
  `novelty_size` read still finds a value either way."
  ([client db-name tx-edn] (transact client db-name tx-edn nil))
  ([client db-name tx-edn {:keys [ttl-sec retry?] :or {ttl-sec default-ttl-sec}}]
   (when-not (or (:secret-key client) (:biscuit-fn client))
     (throw (js/Error. "transact needs a :secret-key or :biscuit-fn (write) client")))
   (if (= :direct-v1 (:transport client))
     (let [ref (v1-ref client db-name)
           ;; mint inside the thunk — fresh nonce per retry attempt (see q).
           do-post #(-> (v1-post client "transact" {:tx-data (reader/read-string tx-edn)}
                                 ref (v1-write-cacao client ref ttl-sec))
                       (.then (fn [report] (clj->js (assoc report :novelty_size (:novelty-size report))))))]
       (if retry? (with-retry do-post) (do-post)))
     (let [graph (cid/canonical-graph (:did client) db-name)
           ;; mint inside the thunk — fresh nonce per retry attempt (see q).
           do-post #(post client "transact" {:db_name db-name :tx_edn tx-edn}
                          (request-cacao client ["datom:transact" "tx:create"] graph
                                         {:ttl-sec ttl-sec}))]
       (if retry? (with-retry do-post) (do-post))))))

(defn fold
  "Maintenance fold: compact `db-name`'s accumulated novelty into a fresh indexed
  snapshot (ADR-2607032430 D1). A cron/ops op — the D1 write path appends O(tx)
  novelty blocks that every read merges, so an un-folded graph's reads slow as
  novelty grows; a periodic fold keeps them fast. Head-mutating, so gated like a
  transact (mints a `datom:transact` CACAO); names the graph directly
  (`kotobase/db/<operator-did>/<db-name>`), unlike transact's server-derived
  graph. Idempotent/no-op when there is nothing to fold, so safe on a schedule.
  `:max-novelty` (optional): bound the fold to the oldest that-many
  not-yet-folded tx blocks (kotobase-peer#16 / gftdcojp/app-aozora#78) instead
  of the unbounded default — this call still runs SERVER-SIDE inside the same
  Worker's CPU/wall-time budget (it is a plain HTTP POST, not a local/offline
  computation), so a backlog too large to fold in one unbounded pass needs
  repeated bounded calls (a cron/ops driver), not a single unbounded one.
  `:views` (optional, ADR-2607166600): a map of view-name →
  {\"attrs\" [attr …]} (nil spec removes that view) declaring the graph's
  materialized views — sent as `views_edn`, re-derived by every subsequent
  fold (stored specs carry forward when the opt is omitted), served by
  `view`.
  → the worker's `{:ok :graph :folded [:commit :novelty_folded
  :novelty_remaining]}` response (XRPC). Under :transport :direct-v1, the
  response is instead `kotobase.datomic/fold`'s own native shape —
  `{:committed? :chain-cid-before :chain-cid-after :novelty-before
  :novelty-after :attempts}` (dash-cased keys, `aget` not `.-`) — a
  DIFFERENT implementation, not a rename; no current caller inspects a
  `fold` response field."
  ([client db-name] (fold client db-name nil))
  ([client db-name {:keys [ttl-sec max-novelty views] :or {ttl-sec default-ttl-sec}}]
   (when-not (or (:secret-key client) (:biscuit-fn client))
     (throw (js/Error. "fold needs a :secret-key or :biscuit-fn (write) client")))
   (if (= :direct-v1 (:transport client))
     (let [ref (v1-ref client db-name)
           opts (cond-> {}
                  max-novelty (assoc :max-novelty max-novelty)
                  views (assoc :views views))]
       (-> (v1-post client "fold" opts ref (v1-write-cacao client ref ttl-sec))
           (.then clj->js)))
     (let [graph (cid/canonical-graph (:did client) db-name)
           body (cond-> {:graph graph}
                  max-novelty (assoc :max_novelty max-novelty)
                  views (assoc :views_edn (pr-str views)))]
       (post client "fold" body
             (request-cacao client ["datom:transact" "tx:create"] graph
                            {:ttl-sec ttl-sec}))))))

;; ── EDN scalar decode (rows_edn / v_edn cells → cljs values) ─────────────────

(defn decode-edn-scalar
  "Parse one EDN scalar cell from a datomic.q/datoms result into a cljs value:
  \"string\" -> string, 42 -> number, true/false -> bool, nil -> nil,
  :kw -> \":kw\" (kept as-is). Mirrors the SDK's decodeEdnScalar."
  [cell]
  (if-not (string? cell)
    cell
    (let [s (str/trim cell)]
      (cond
        (zero? (count s)) s
        (= (first s) \") (try (js/JSON.parse s) (catch :default _ s))
        (= s "true") true
        (= s "false") false
        (= s "nil") nil
        (re-matches #"[+-]?\d+" s) (let [n (js/Number s)]
                                     (if (js/Number.isSafeInteger n) n s))
        (re-matches #"[+-]?\d*\.\d+([eE][+-]?\d+)?" s) (js/Number s)
        :else s))))

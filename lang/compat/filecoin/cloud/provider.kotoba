(ns filecoin.cloud.provider
  "The storage provider's PDP HTTP API — the transfer surface the rest of this
  repo was missing.

  Everything else in `cloud-filecoin` is on-chain: PieceCID, PDP calldata,
  Filecoin Pay, Warm Storage. None of it moves a byte. A provider does, over
  an ordinary HTTP API rooted at its `serviceURL`:

      GET  pdp/ping                      is it alive
      GET  pdp/piece?pieceCid=<cid>      is this piece here
      POST pdp/piece                     begin an upload
      PUT  pdp/piece/upload/<uuid>       send the bytes
      GET  piece/<cid>                   retrieve

  ## The upload is two requests, and the first one may be the last

  `POST pdp/piece` announces the PieceCID. **`200` means the provider already
  has it** — deduplication is content-addressing doing its job, and there is
  nothing left to send. Any other success carries a `Location` header whose
  final path segment is an upload id, and the bytes go to
  `PUT pdp/piece/upload/<that id>`. Treating `200` as \"proceed to upload\"
  wastes the transfer; treating a missing `Location` as success loses the data
  silently.

  ## The upload is not authenticated, and that is not an oversight

  There is no key, token or signature on either request. Authorisation is the
  **on-chain `addPieces`**, which the client signs: a provider will happily
  park bytes, but only a signed transaction makes them part of a data set
  anyone is paid to prove. So an upload alone stores nothing durably, and this
  namespace cannot pretend otherwise — see `filecoin.cloud.pdp` for the half
  that needs a key.

  ## Retrieval has two URL shapes and they are not interchangeable

      provider   <serviceURL>/piece/<cid>
      FilBeam    https://<client-address>.<retrieval-domain>/<cid>

  The CDN form is a **per-client subdomain** with the CID at the path root —
  not `<domain>/piece/<cid>`. Getting that wrong yields a URL that resolves,
  404s, and looks like a missing piece rather than a malformed request. (It
  was wrong in the first cut of `cloud-itonami-app`'s read-through for exactly
  that reason: the failure was a silent nil.)

  ## No I/O here

  Same contract as `filecoin.rpc`: these functions build request maps and
  interpret responses. `filecoin.protocols/IHttp` does the sending, so this
  works on a JVM, under nbb, or in a Worker unchanged."
  (:require [clojure.string :as str]
            [filecoin.cloud.piece :as piece]))

(def ^:const min-upload-size
  "`SIZE_CONSTANTS.MIN_UPLOAD_SIZE`. Also the FR32 quad, which is why it is
  127 and not a round number."
  127)

(def ^:const max-upload-size
  "`SIZE_CONSTANTS.MAX_UPLOAD_SIZE` — 1 GiB × 127/128, i.e. the payload whose
  FR32 expansion is exactly 1 GiB."
  1065353216)

(defn- join
  "`serviceURL` + path, tolerating a trailing slash on either side."
  [service-url path]
  (str (str/replace (str service-url) #"/+$" "") "/" (str/replace path #"^/+" "")))

;; ── liveness ─────────────────────────────────────────────────────────────────

(defn ping-request [service-url]
  {:method :get :url (join service-url "pdp/ping") :headers {}})

(defn ping-response
  "A provider is up only on 2xx. Worth being strict: mainnet has providers
  answering 502 right now, and a client that treats any response as alive
  will pick one of them."
  [{:keys [status]}]
  {:alive? (boolean (and status (<= 200 status 299))) :status status})

;; ── is the piece there ───────────────────────────────────────────────────────

(defn find-piece-request [service-url piece-cid]
  {:method :get
   :url (str (join service-url "pdp/piece") "?pieceCid=" piece-cid)
   :headers {}})

(defn find-piece-response
  "Three outcomes, and collapsing them loses the one that matters:

    200  present
    202  the provider has it and is still processing — retry, do not re-upload
    404  absent"
  [{:keys [status body]}]
  (cond
    (= 202 status) {:state :processing}
    (and status (<= 200 status 299)) {:state :present :body body}
    (= 404 status) {:state :absent}
    :else {:state :error :status status :body body}))

;; ── upload ───────────────────────────────────────────────────────────────────

(defn check-size!
  "Refuse before transferring. A provider rejects an out-of-range piece after
  the bytes are on the wire."
  [n]
  (when (or (< n min-upload-size) (> n max-upload-size))
    (throw (ex-info "provider: payload outside the accepted upload range"
                    {:bytes n :min min-upload-size :max max-upload-size})))
  n)

(defn begin-upload-request
  "`POST pdp/piece` — announce the PieceCID. The body is JSON, built here as
  a string so this namespace needs no JSON encoder for one field."
  [service-url piece-cid]
  {:method :post
   :url (join service-url "pdp/piece")
   :headers {"content-type" "application/json"}
   :body (str "{\"pieceCid\":\"" piece-cid "\"}")})

(defn upload-id
  "The upload id from a `Location` header — its final path segment.

  Header lookup is case-insensitive because `Location` and `location` are the
  same header and only one of them is what a given server sends."
  [headers]
  (let [loc (some (fn [[k v]] (when (= "location" (str/lower-case (name k))) v)) headers)]
    (when-let [seg (some-> loc (str/split #"/") last not-empty)]
      seg)))

(defn begin-upload-response
  "What to do next.

  `:already-present` on 200 — the provider deduplicated by PieceCID and there
  is nothing to send. Anything else needs the upload id, and its absence is an
  error rather than a shrug."
  [{:keys [status headers body]}]
  (cond
    (= 200 status) {:next :already-present}
    (and status (<= 200 status 299))
    (if-let [id (upload-id headers)]
      {:next :upload :upload-id id}
      {:state :error :reason "no usable Location header on the upload ticket"
       :status status :headers headers})
    :else {:state :error :status status :body body}))

(defn send-bytes-request
  "`PUT pdp/piece/upload/<id>` — the transfer itself."
  [service-url upload-id* bytes]
  (let [n (count (vec (seq bytes)))]
    {:method :put
     :url (join service-url (str "pdp/piece/upload/" upload-id*))
     :headers {"content-type" "application/octet-stream"
               "content-length" (str n)}
     :body bytes}))

;; ── retrieval ────────────────────────────────────────────────────────────────

(defn piece-url
  "`<serviceURL>/piece/<cid>` — retrieval straight from the provider."
  [service-url piece-cid]
  (join service-url (str "piece/" piece-cid)))

(defn cdn-url
  "`https://<client-address>.<retrieval-domain>/<cid>`.

  A **per-client subdomain**, with the CID at the path root. Not
  `<domain>/piece/<cid>`, which resolves and 404s and so reads as a missing
  piece rather than a wrong URL."
  [client-address retrieval-domain piece-cid]
  (str "https://" (str/lower-case (str client-address)) "." retrieval-domain
       "/" piece-cid))

(defn get-piece-request [url]
  {:method :get :url url :headers {}})

;; ── the check a caller should not skip ───────────────────────────────────────

(defn verify-bytes
  "Do these bytes actually hash to the PieceCID they were fetched under?

  Content addressing is only worth something if somebody checks. A provider
  serving the wrong bytes, a truncated transfer and a CDN cache collision all
  look identical until this is computed."
  [piece-cid bytes]
  (let [computed (:cid (piece/calculate bytes))]
    {:ok? (= computed (str piece-cid))
     :expected (str piece-cid)
     :computed computed}))

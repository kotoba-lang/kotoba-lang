(ns murakumo.overlay.witness-http-transport
  "witness-quorum attestation production backed by cloud-murakumo's
   HTTP witness RPC (cloud_murakumo.verify.witness-rpc, ADR-2607110300
   Phase 3) -- the 'deployment glue' both witness_rpc.clj's and
   verify/compute.cljc's docstrings explicitly deferred: 'a caller WITH
   a real witness-quorum dependency (deployment glue, not this repo)
   composes the two.' murakumo is that caller -- it already depends on
   witness-quorum (Phase 2, for the QUIC-based
   murakumo.overlay.witness-transport), so this is the natural home:

     - NOT in cloud-murakumo itself, which deliberately stays
       witness-quorum-dependency-free (single-checkout, no-west-workspace
       deploy constraint -- see cloud-murakumo's pay/core.cljc vendoring
       note).
     - NOT in witness-quorum itself, which stays cloud-murakumo-agnostic
       (a generic quorum library with no business knowing what a GPU
       recompute job looks like).

   The HTTP wire format below (request/response envelope, /witness path,
   EDN body) DUPLICATES cloud_murakumo.verify.witness-rpc's shape rather
   than requiring that repo -- this is a wire CONTRACT two independent
   services agree on, not shared code (murakumo has no dependency edge to
   cloud-murakumo, and shouldn't grow one just to speak its RPC format,
   any more than an HTTP client library needs to depend on every server
   it talks to)."
  (:require [kotoba.lang.edn :as edn]
            [kotoba.lang.witness-quorum.attestation :as attestation])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]
           [java.time Duration]))

(defn- request-envelope [payload]
  {:type "cloud-murakumo.witness-rpc-request" :version 1 :payload payload})

(defn http-dial!
  "POST an EDN-encoded request-envelope to `url` (a cloud-murakumo
   witness-rpc endpoint, e.g. http://<node>:<port>/witness), read back
   an EDN-encoded response-envelope's :payload. Same wire contract as
   cloud_murakumo.verify.witness-rpc/dial!, reimplemented here (see ns
   docstring) so this module has no cross-repo dependency edge."
  [url payload timeout-ms]
  (try
    (let [client (-> (HttpClient/newBuilder)
                     (.connectTimeout (Duration/ofMillis (long timeout-ms)))
                     .build)
          body (pr-str (request-envelope payload))
          req (-> (HttpRequest/newBuilder (URI/create url))
                 (.timeout (Duration/ofMillis (long timeout-ms)))
                 (.header "content-type" "application/edn")
                 (.POST (HttpRequest$BodyPublishers/ofString body))
                 .build)
          resp (.send client req (HttpResponse$BodyHandlers/ofString))
          status (.statusCode resp)]
      (if (<= 200 status 299)
        {:ok? true :response (:payload (edn/read-string (.body resp)))}
        {:ok? false :reason :http-error :status status :body (.body resp)}))
    (catch Exception e
      {:ok? false :reason :connect-error :message (.getMessage e)})))

(defn http-deterministic-validator
  "A witness-quorum :deterministic validator
   (`(fn [record rule] -> {:layer :deterministic :verdict ... :reason ...})`,
   per kotoba.lang.witness-quorum.attestation/validate-against-membrane's
   contract) that dials a cloud-murakumo witness-rpc endpoint and
   interprets the response. `record` must carry `:inv`/`:claimed-cids`
   matching cloud_murakumo.verify.witness-rpc's witness-compute-handler
   payload contract. An unreachable/erroring endpoint fails CLOSED
   (:reject), not open -- a witness that can't be reached never silently
   counts as agreeing."
  [url timeout-ms]
  (fn [record _rule]
    (let [{:keys [ok? response] :as result}
          (http-dial! url {:inv (:inv record) :claimed-cids (:claimed-cids record)} timeout-ms)]
      (if (and ok? (map? response) (contains? #{:accept :reject} (:verdict response)))
        response
        {:layer :deterministic :verdict :reject
         :reason (str "witness-rpc unreachable or malformed response: " (pr-str result))}))))

(defn produce-http-witnessed-attestation
  "End-to-end: dial a cloud-murakumo witness node's HTTP RPC endpoint for
   a proof-of-compute recompute check, wrap its verdict in a real
   Ed25519-signed witness-quorum attestation. This is the composition
   both cloud-murakumo's and witness-quorum's docstrings pointed at as
   the missing piece.

   `opts`:
     :record-uri/:record-cid/:record/:cell/:rule/:signer  same as
       kotoba.lang.witness-quorum.attestation/produce-attestation.
       `:record` must carry `:inv`/`:claimed-cids`.
     :witness-url   the cloud-murakumo witness-rpc endpoint to dial.
     :timeout-ms    per-request HTTP timeout. Default 5000."
  [{:keys [record-uri record-cid record cell rule signer witness-url timeout-ms]}]
  (attestation/produce-attestation
   {:record-uri record-uri :record-cid record-cid :record record :cell cell :rule rule :signer signer
    :validators {:deterministic (http-deterministic-validator witness-url (or timeout-ms 5000))}}))

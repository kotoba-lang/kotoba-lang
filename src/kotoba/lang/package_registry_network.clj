(ns kotoba.lang.package-registry-network
  "Network/IPFS-backed package registry resolver (ADR-2607180900 follow-up
  item 'network/IPFS package registry backend', scoped and implemented per
  com-junkawasaki/root ADR-2607182700's companion audit).

  `kotoba.lang.package-registry` stays I/O-free by design ('callers supply
  an already-parsed registry EDN map. Network/HTTP registry backends can
  project into the same shape.'). This namespace is that projection: the
  thin JVM-only I/O adapter that fetches a registry snapshot's bytes from
  an IPFS HTTP gateway by CID, verifies the fetched bytes hash to that
  exact CID (never trusts gateway-returned bytes on say-so -- a
  compromised or misconfigured gateway returning wrong bytes for a CID is
  the entire threat model this check exists for), parses them as EDN, and
  hands the result to `kotoba.lang.package-registry`'s existing pure
  functions unchanged. No resolution logic is duplicated here.

  `:clj`-only (java.net.http.HttpClient, part of the JDK -- no new
  dependency added to this repo's deps.edn). No JVM-compatible CID-fetching
  IPFS client exists anywhere else in this fleet to reuse (checked
  kotobase-protocols/ipfs.cljc: a server-side gateway *handler*, not a
  client; kotobase-client/ipns.cljs and net-kotobase-ipfs/gateway.cljs:
  both `js/fetch`-based, browser/Cloudflare-Worker only) -- this is a
  fresh, minimal implementation, not a rewiring of existing code."
  (:require [clojure.edn :as edn]
            [multiformats.core :as mf]
            [kotoba.lang.package-registry :as registry])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
           (java.time Duration)))

(def default-gateway-base
  "Local Kubo gateway convention (`ipfs daemon`'s default HTTP gateway).
  Override via opts for a public gateway or a different local port."
  "http://127.0.0.1:8080/ipfs/")

(def default-timeout-ms 10000)

(defn fetch-bytes
  "Fetch raw bytes for CID from an IPFS HTTP gateway. Returns
  {:ok? true :bytes <byte-array>} or {:ok? false :problems [...]} --
  never throws; every HttpClient/IOException/timeout is caught and
  reported as a fail-closed problem map like every other resolver path
  in this namespace family."
  ([cid] (fetch-bytes cid {}))
  ([cid {:keys [gateway-base timeout-ms]
         :or {gateway-base default-gateway-base timeout-ms default-timeout-ms}}]
   (try
     (let [client (HttpClient/newHttpClient)
           req (-> (HttpRequest/newBuilder)
                   (.uri (URI/create (str gateway-base cid)))
                   (.timeout (Duration/ofMillis timeout-ms))
                   (.GET)
                   (.build))
           resp (.send client req (HttpResponse$BodyHandlers/ofByteArray))]
       (if (= 200 (.statusCode resp))
         {:ok? true :bytes (.body resp)}
         {:ok? false :problems [{:problem :registry/fetch-http-status
                                  :status (.statusCode resp)
                                  :cid cid}]}))
     (catch Exception e
       {:ok? false :problems [{:problem :registry/fetch-failed
                                :message (ex-message e)
                                :cid cid}]}))))

(defn verify-cid
  "True only when BYTES hash to CID exactly (CIDv1 raw codec, sha2-256,
  via `multiformats.core/cidv1-raw` -- byte-identical to `ipfs add
  --cid-version=1 --raw-leaves`). This is the actual safety-relevant
  check: a gateway can serve any bytes it wants for any request path, so
  resolving a package registry over the network is only as safe as this
  verification, never the transport."
  [cid bytes]
  (= cid (mf/cidv1-raw bytes)))

(defn fetch-and-parse-registry
  "Fetch a registry snapshot by CID, verify its content hash, parse EDN,
  and normalize it via `kotoba.lang.package-registry/normalize`. Returns
  {:ok? true :registry <normalized>} or a fail-closed {:ok? false
  :problems [...]} -- never returns a registry whose bytes were not
  verified against CID, and never returns a partially-parsed registry."
  ([cid] (fetch-and-parse-registry cid {}))
  ([cid opts]
   (let [fetched (fetch-bytes cid opts)]
     (cond
       (not (:ok? fetched))
       fetched

       (not (verify-cid cid (:bytes fetched)))
       {:ok? false :problems [{:problem :registry/cid-mismatch :cid cid}]}

       :else
       (try
         (let [parsed (edn/read-string (String. ^bytes (:bytes fetched) "UTF-8"))]
           {:ok? true :registry (registry/normalize parsed)})
         (catch Exception e
           {:ok? false :problems [{:problem :registry/edn-parse-failed
                                    :message (ex-message e)
                                    :cid cid}]}))))))

(defn resolve-record-network
  "Network-backed equivalent of `kotoba.lang.package-registry/resolve-record`:
  fetch+verify+parse the registry snapshot at CID, then resolve NAME/VERSION
  against it via the existing pure resolver. No duplicated resolution logic."
  ([cid name version] (resolve-record-network cid name version {}))
  ([cid name version opts]
   (let [fetched (fetch-and-parse-registry cid opts)]
     (if-not (:ok? fetched)
       fetched
       (registry/resolve-record (:registry fetched) name version)))))

(defn lock-from-requests-network
  "Network-backed equivalent of
  `kotoba.lang.package-registry/lock-from-requests`."
  ([cid requests] (lock-from-requests-network cid requests {}))
  ([cid requests opts]
   (let [fetched (fetch-and-parse-registry cid opts)]
     (if-not (:ok? fetched)
       fetched
       (registry/lock-from-requests (:registry fetched) requests)))))

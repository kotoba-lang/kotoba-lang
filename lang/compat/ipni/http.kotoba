(ns ipni.http
  "HTTP path contracts. This namespace does not fetch.

  Publisher (the process that *holds* advertisement bytes):

      GET /ipni/v1/ad/{cid}
      GET /ipni/v1/head

  Indexer query:

      GET {indexer}/cid/{cid}
      GET {indexer}/routing/v1/providers/{cid}

  Indexer ingest (announce). The spec name is `/announce`. Production
  cid.contact serves ingest at `/ingest/announce`. Both are paths, not
  identities. Default announce URL is the production ingest endpoint so
  a first call is not a 404 dressed as a pass."
  (:require [clojure.string :as str]))

(def ^:const default-indexer "https://cid.contact")
(def ^:const default-routing-v1 "https://cid.contact/routing/v1")
(def ^:const default-announce-url "https://cid.contact/ingest/announce")

(defn- trim-slash [s]
  (if (and (string? s) (str/ends-with? s "/"))
    (subs s 0 (dec (count s)))
    s))

(defn ad-path
  "Publisher path for one advertisement CID. `cid` is the advertisement
  CID, not the content CID."
  [cid]
  (str "/ipni/v1/ad/" cid))

(defn head-path
  []
  "/ipni/v1/head")

(defn ad-url
  [publisher-origin cid]
  (str (trim-slash publisher-origin) (ad-path cid)))

(defn head-url
  [publisher-origin]
  (str (trim-slash publisher-origin) (head-path)))

(defn cid-url
  "IPNI-native find. Use when ContextID / Metadata are needed.
  Gateway fetch only needs routing v1."
  [indexer cid]
  (str (trim-slash (or indexer default-indexer)) "/cid/" cid))

(defn providers-url
  "Delegated Routing V1 find. Same client API kad.routing uses.
  `router` is a routing-v1 base (`…/routing/v1`)."
  [router cid]
  (str (trim-slash (or router default-routing-v1)) "/providers/" cid))

(defn announce-url
  ([indexer] (announce-url indexer {}))
  ([indexer {:keys [path] :or {path "/ingest/announce"}}]
   (if (re-find #"^https?://" (str indexer))
     (if (re-find #"/announce" (str indexer))
       (trim-slash indexer)
       (str (trim-slash indexer) path))
     default-announce-url)))

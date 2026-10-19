(ns content-address.core
  "Content addressing, as pure functions over octets.

  A document's identity is its bytes. This namespace turns bytes into a
  CIDv1 string, reads a CIDv1 string back into a digest, and answers the
  three questions an application manifest has to answer:

    identity  — `:kotoba.app/bundle-cid`   the raw CIDv1 of the document
    naming    — `:kotoba.app/latest`       the key-derived IPNS name (k51…)
    location  — `:published`/`:graph`      where a copy answered a GET

  Location is a retrieval hint. It is never the identity, so a manifest that
  carries only an https URL is NOT addressed (`addressed?` says so).

  No hashing here — hashing is platform work (`content-address.digest`), and
  no I/O — that is `content-address.archive`. Everything below is a pure
  function of octet vectors, so both runtimes run the same code and the
  tests do not need a network.

  ⚠ IPLD paths (`/ipld/{cid}/a/b`) are resolution paths through a DAG, not
  locations. \"Not path-based\" constrains identity, not traversal."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------- octets

(def ^:private alphabet "abcdefghijklmnopqrstuvwxyz234567")

(def ^:private alphabet-index
  (into {} (map-indexed (fn [i c] [c i]) alphabet)))

(defn base32-encode
  "RFC 4648 base32, lower case, no padding — the multibase `b` body."
  [octets]
  (let [v (vec octets)]
    (loop [i 0 acc 0 bits 0 out []]
      (if (< i (count v))
        (let [acc (+ (* acc 256) (nth v i))
              bits (+ bits 8)
              [acc bits out] (loop [a acc b bits o out]
                               (if (>= b 5)
                                 (recur a (- b 5)
                                        (conj o (nth alphabet
                                                     (bit-and (bit-shift-right a (- b 5)) 31))))
                                 [(bit-and a (dec (bit-shift-left 1 b))) b o]))]
          (recur (inc i) acc bits out))
        (apply str (if (pos? bits)
                     (conj out (nth alphabet (bit-and (bit-shift-left acc (- 5 bits)) 31)))
                     out))))))

(defn base32-decode
  "Inverse of `base32-encode`. Returns a vector of octets, or nil if the
  string carries a character the alphabet does not contain."
  [s]
  (when (string? s)
    (loop [cs (seq s) acc 0 bits 0 out []]
      (if-let [c (first cs)]
        (if-let [idx (alphabet-index c)]
          (let [acc (+ (* acc 32) idx)
                bits (+ bits 5)]
            (if (>= bits 8)
              (recur (next cs)
                     (bit-and acc (dec (bit-shift-left 1 (- bits 8))))
                     (- bits 8)
                     (conj out (bit-and (bit-shift-right acc (- bits 8)) 255)))
              (recur (next cs) acc bits out)))
          nil)
        out))))

;; ------------------------------------------------------------------ CID

(def codecs
  "The two multicodecs this workspace addresses documents with.

  `:raw` is the only one `PUT https://kotobase.net/ipfs/{cid}` accepts —
  a dag-cbor identity has to be archived under the raw CID of the same
  bytes (ADR-2608148200). They are one object, not two identities."
  {:raw 0x55
   :dag-cbor 0x71})

(def ^:private codec-name (into {} (map (fn [[k v]] [v k]) codecs)))

(def sha2-256 0x12)
(def digest-length 32)

(defn cid-string
  "CIDv1 string for a sha2-256 digest under `codec` (default `:raw`)."
  ([digest] (cid-string :raw digest))
  ([codec digest]
   (let [code (get codecs codec)
         d (vec digest)]
     (when-not code
       (throw (ex-info "unknown codec" {:codec codec :known (keys codecs)})))
     (when-not (= digest-length (count d))
       (throw (ex-info "digest is not sha2-256"
                       {:length (count d) :expected digest-length})))
     (str "b" (base32-encode (into [0x01 code sha2-256 digest-length] d))))))

(defn parse-cid
  "Parse a CIDv1 string into `{:codec :digest}`, or nil if it is not one.

  Returns nil rather than throwing: callers are usually deciding whether a
  manifest field IS a content address, and \"no\" is an answer, not a fault."
  [s]
  (when (and (string? s) (str/starts-with? s "b"))
    (let [octets (base32-decode (subs s 1))]
      (when (and octets
                 (= (+ 4 digest-length) (count octets))
                 (= 0x01 (nth octets 0))
                 (= sha2-256 (nth octets 2))
                 (= digest-length (nth octets 3))
                 (codec-name (nth octets 1)))
        {:codec (codec-name (nth octets 1))
         :digest (vec (drop 4 octets))}))))

(defn raw-cid?
  "True when `s` is a raw (codec 0x55) CIDv1 — what the archive accepts."
  [s]
  (= :raw (:codec (parse-cid s))))

(defn same-object?
  "True when two CID strings address the same bytes under different codecs.

  This is the check that keeps `:kotoba.graph/cid` (dag-cbor) and the raw
  Location CID from being read as two identities."
  [a b]
  (let [pa (parse-cid a) pb (parse-cid b)]
    (boolean (and pa pb (= (:digest pa) (:digest pb))))))

;; ----------------------------------------------------------------- links

(defn embed-url
  "The location-independent link to a document: `ipfs://{cid}`."
  [cid]
  (when (parse-cid cid) (str "ipfs://" cid)))

(defn ipns-url
  "The location-independent link to whatever the name currently points at."
  [name]
  (when (and (string? name) (str/starts-with? name "k51")) (str "ipns://" name)))

(defn archive-url
  "A Location — one host that answered a GET for these bytes. Not identity."
  [origin cid]
  (str (str/replace origin #"/+$" "") "/ipfs/" cid))

;; -------------------------------------------------------------- manifest

(def address-attrs
  "Manifest attributes that carry a content address, most specific first."
  [:kotoba.app/bundle-cid :kotoba.graph/cid])

(defn entities
  "The application entities inside a parsed `kotoba.app.edn`.

  Two shapes are both in use and both correct: a bare manifest map, and the
  tx-data vector `[{:db/id -1 …}]` the EDN-only docs convention writes.
  Measured 2026-08-15: 105 files are maps and 50 are tx-data vectors. A
  reader that understands one shape reports the other as unreadable — which
  is the same silence-counted-as-a-verdict this namespace exists to stop."
  [parsed]
  (cond
    (map? parsed) [parsed]
    (sequential? parsed) (filterv map? parsed)
    :else []))

(defn address-of
  "The content address a `kotoba.app.edn` manifest carries, if any."
  [manifest]
  (let [bundle (:kotoba.app/bundle-cid manifest)
        graph (:kotoba.graph/cid manifest)
        latest (:kotoba.app/latest manifest)
        embed (:kotoba.app/embed-url manifest)]
    (when (or (parse-cid bundle) (parse-cid graph))
      (cond-> {}
        (parse-cid bundle) (assoc :bundle-cid bundle)
        (parse-cid graph) (assoc :graph-cid graph)
        (ipns-url latest) (assoc :latest latest)
        (string? embed) (assoc :embed-url embed)))))

(defn addressed?
  "Does this manifest identify its document by content?

  The measurement predicate. An https URL, a hostname, or a path is not an
  answer — those are locations, and a location can move without the bytes
  changing."
  [manifest]
  (some? (address-of manifest)))

(defn file-addressed?
  "Does a parsed `kotoba.app.edn` — either shape — carry a content address?"
  [parsed]
  (boolean (some addressed? (entities parsed))))

(defn file-address-of
  "The first content address in a parsed `kotoba.app.edn`, either shape."
  [parsed]
  (some address-of (entities parsed)))

(defn problems
  "Everything wrong with the addressing in a manifest. Empty vector = fine.

  Reports rather than throws, and reports the *absence* of an address as a
  finding, so a manifest that was never published cannot read as clean."
  [manifest]
  (let [bundle (:kotoba.app/bundle-cid manifest)
        graph (:kotoba.graph/cid manifest)
        latest (:kotoba.app/latest manifest)
        embed (:kotoba.app/embed-url manifest)]
    (cond-> []
      (not (addressed? manifest))
      (conj {:problem :no-content-address
             :detail "manifest identifies its document by location only"})

      (and (some? bundle) (not (raw-cid? bundle)))
      (conj {:problem :bundle-cid-not-raw :value bundle})

      (and (some? graph) (nil? (parse-cid graph)))
      (conj {:problem :graph-cid-not-a-cid :value graph})

      (and (some? embed) (some? bundle)
           (str/starts-with? (str embed) "ipfs://")
           (not= embed (embed-url bundle)))
      (conj {:problem :embed-url-disagrees-with-bundle-cid
             :embed-url embed :bundle-cid bundle})

      (and (some? embed) (str/starts-with? (str embed) "http"))
      (conj {:problem :embed-url-is-a-location
             :detail "ipfs:// or ipns:// — an https URL names a host, not the bytes"
             :value embed})

      (and (some? latest) (nil? (ipns-url latest)))
      (conj {:problem :latest-not-an-ipns-name :value latest}))))

(defn record-address
  "Return `manifest` with this publication's address recorded.

  Protocol attributes (`:kotoba.*`) carry identity and naming. The plain
  `:published` key carries Location — the host that answered, the status
  codes, the size. Keeping Location out of the `:kotoba.*` namespace is the
  point: a reader that only understands the protocol sees no host at all."
  [manifest {:keys [bundle-cid graph-cid latest origin size put-status get-status at]}]
  (cond-> manifest
    bundle-cid (assoc :kotoba.app/bundle-cid bundle-cid
                      :kotoba.app/embed-url (embed-url bundle-cid)
                      :published (cond-> {:archive (archive-url (or origin "https://kotobase.net")
                                                                bundle-cid)}
                                   size (assoc :size size)
                                   put-status (assoc :put-status put-status)
                                   get-status (assoc :get-status get-status)
                                   at (assoc :at at)))
    graph-cid (assoc :kotoba.graph/cid graph-cid)
    latest (assoc :kotoba.app/latest latest)))

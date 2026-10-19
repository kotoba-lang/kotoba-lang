(ns ipni.announce
  "Announce message: advertisement CID + publisher addresses.

  The indexer fetches `GET /ipni/v1/ad/{ad-cid}` from those addresses.
  They are not retrieval multiaddrs. Mixing the two is how a gateway
  URL gets announced as a publisher and the indexer 404s the chain.

  `:ad-cid` is the advertisement CID. `:cid` on a discover record is the
  content CID. This map uses `:ad-cid` so they cannot be swapped by
  accident. The JSON field the indexer reads is still `cid`.

  ## The wire form is not the obvious one

  Measured against production cid.contact on 2026-08-20, by sending each
  shape and reading what came back. All three of these were wrong here
  before, and every one of them fails as an HTTP 400 at ingest -- which a
  publisher that only logs status codes would record as `:rejected` with no
  idea why:

  | sent | cid.contact answered |
  | --- | --- |
  | `{\"cid\": \"bafk…\"}` | `json: cannot unmarshal string into Go value of type struct { CidTarget string \"json:\\\"/\\\"\" }` |
  | `{\"addrs\": [\"/dns4/host/tcp/443/https\"]}` | `illegal base64 data at input byte 10` |
  | `/dns4/host/tcp/443/https` (no peer) | `invalid p2p multiaddr` |

  So: `cid` is a **dag-json link** `{\"/\": \"bafy…\"}`; `addrs` are
  **base64 of the BINARY multiaddr**, not its text form; and each address
  must carry a `/p2p/{peer-id}` component, because the indexer identifies
  the publisher by peer ID and not by host.

  Parsing a multiaddr needs a multiformats table, and this library holds no
  dependencies, so `addr-encode-fn` is injected: `(fn [multiaddr-string]
  octets)`. `multiformats.multiaddr/->octets` is one. Base64 IS this
  namespace's job -- it is the wire, and the wire is what this namespace
  owns.")

(def ^:private b64-alphabet
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")

(defn- b64
  "Standard base64 with padding, over a seq of octets. Pure so this
  namespace keeps `:deps {}` -- see the ns docstring."
  [octets]
  (let [v (vec octets)
        n (count v)]
    (loop [i 0 out ""]
      (if (>= i n)
        out
        (let [b0 (nth v i)
              b1 (when (< (+ i 1) n) (nth v (+ i 1)))
              b2 (when (< (+ i 2) n) (nth v (+ i 2)))
              c0 (bit-shift-right b0 2)
              c1 (bit-or (bit-shift-left (bit-and b0 0x03) 4)
                         (if b1 (bit-shift-right b1 4) 0))
              c2 (when b1 (bit-or (bit-shift-left (bit-and b1 0x0F) 2)
                                  (if b2 (bit-shift-right b2 6) 0)))
              c3 (when b2 (bit-and b2 0x3F))]
          (recur (+ i 3)
                 (str out
                      (nth b64-alphabet c0)
                      (nth b64-alphabet c1)
                      (if c2 (nth b64-alphabet c2) "=")
                      (if c3 (nth b64-alphabet c3) "="))))))))

(defn p2p-component?
  "True when a multiaddr string names a peer.

  cid.contact answers `invalid p2p multiaddr` without one: it identifies a
  publisher by peer ID, not by host, so `/dns4/host/tcp/443/https` alone is
  not an address it can attribute to anybody."
  [s]
  (boolean (and (string? s) (re-find #"/p2p/[A-Za-z0-9]+" s))))

(defn message
  "Build the announce message.

  `addrs` are publisher multiaddr STRINGS, each carrying `/p2p/{peer-id}`.
  `addr-encode-fn` turns one into octets. Both are required: a message
  missing either is returned as an `:error` rather than as a message the
  indexer will 400."
  [{:keys [ad-cid addrs cid addr-encode-fn]}]
  (cond
    (and (string? cid) (not (string? ad-cid)))
    {:error :content-cid-is-not-an-advertisement
     :hint "pass :ad-cid (advertisement CID), not the content CID"
     :value cid}
    (not (string? ad-cid)) {:error :invalid-ad-cid :value ad-cid}
    (not (sequential? addrs)) {:error :invalid-addrs :value addrs}
    (empty? addrs) {:error :empty-publisher-addrs}
    (not (every? string? addrs)) {:error :invalid-addrs :value addrs}
    (not (every? p2p-component? addrs))
    {:error :missing-p2p-component
     :hint "cid.contact answers `invalid p2p multiaddr` for an address with no /p2p/{peer-id}"
     :value (vec (remove p2p-component? addrs))}
    (not (ifn? addr-encode-fn))
    {:error :addr-encode-fn-required
     :hint "addrs go on the wire as base64 of the BINARY multiaddr; pass multiformats.multiaddr/->octets"}
    :else
    (let [encoded (mapv (fn [a] (b64 (addr-encode-fn a))) addrs)]
      {:ad-cid ad-cid
       :addrs (vec addrs)
       ;; dag-json link, not a bare string -- see the ns docstring.
       :wire {:cid {"/" ad-cid} :addrs encoded}})))

(ns kotobase.blocks
  "CID-verified block reads over `GET /ipld/<cid>` — the transport a client
  uses when the server is only a byte store.

  ## Why this exists

  Every other read in this library is an RPC: `kotobase.client/q` POSTs
  `/xrpc/ai.gftd.apps.kotobase.datomic.q` and the Worker executes the query
  inside its own CPU budget. `kotobase.datom-source` moved the *algebra* to
  the caller but still reads through `datomic.datoms`, so the index descent
  and the novelty merge stay on the server.

  This namespace is the rung below both: the server hands back immutable
  bytes named by their own hash, and the caller does the rest. The route it
  speaks to states the same split — *\"Reads are public and verified by CID\"*
  (`kotobase-graph-database`'s `handle-ipld-block`) — and serves them
  `cache-control: public, max-age=31536000, immutable`, which is only sound
  because the name IS the content.

  ## The verification is the point, and it happens HERE

  \"Verified by CID\" is a claim about what the origin stores, not about what
  arrived. A cache, a proxy, a compromised bucket, or a bit flip all produce
  bytes that the origin's own check never saw. So this namespace re-derives
  the address from the bytes it actually received and **refuses** anything
  that does not match. `get-block` never returns unverified bytes:

  | outcome                          | result                             |
  |----------------------------------|------------------------------------|
  | 200 and digest matches           | `Uint8Array`                       |
  | 404                              | `nil` — absent, not an error       |
  | 200 and digest does NOT match    | rejects `:kotobase.blocks/corrupt` |
  | CID this code cannot hash-check  | rejects `:unsupported-multihash`   |
  | any other status                 | rejects `:kotobase.blocks/http`    |

  The fourth row is deliberate. A multihash we cannot recompute (anything but
  sha2-256) leaves us unable to *answer*, and an unanswerable check must not
  return the same value as a check that passed — so it refuses rather than
  handing back bytes it did not verify. Superproject CLAUDE.md calls this
  class out by name: an inspection that could not run must not look like an
  inspection that found nothing wrong.

  ## Scope, and where the credential is NOT

  Reading needs no credential at all, which is what makes this usable from a
  page that holds no key. Contributing a block does — `PUT /ipld/<cid>`
  requires a CACAO carrying `kotoba://can/kotobase:pin` — but this namespace
  still holds none: `put-block!` takes an already-minted `Authorization`
  value from its caller. Minting lives in `kotobase.cacao`, so a reader can
  see at a glance that nothing here can sign anything.

  The write exists because a client that can only read cannot publish the
  index it computed. Superproject ADR-2608085000 is about exactly that
  boundary: the client does the work, and what it may write back is bytes
  named by their own hash — which no amount of malice can turn into somebody
  else's block."
  (:require [kotoba.lang.text :as str]
            [sha2.core :as sha2]
            [kotobase.cid :as cid]))

;; ── multibase ───────────────────────────────────────────────────────────────

(def ^:private b32-alphabet "abcdefghijklmnopqrstuvwxyz234567")

(defn base32-lower-no-pad-decode
  "Inverse of `kotobase.cid/base32-lower-no-pad`: the payload of a multibase
   'b' string → Uint8Array. Throws on a character outside the alphabet.

   Trailing bits that do not complete a byte are DISCARDED, which is what the
   encoder produced them as (it pads the final group with zeros). Rejecting a
   non-zero remainder would reject valid CIDs whose length is not a multiple
   of 5 bits, and 32 bytes of digest plus a 4-byte prefix is exactly such a
   length."
  [^string s]
  (let [idx (into {} (map-indexed (fn [i c] [c i]) b32-alphabet))
        out (array)]
    (loop [chars (seq s) bits 0 value 0]
      (if-let [c (first chars)]
        (let [v (or (get idx c)
                    (throw (js/Error. (str "base32: bad char " c))))
              value (bit-or (bit-shift-left value 5) v)
              bits (+ bits 5)]
          (if (>= bits 8)
            (do (.push out (bit-and (unsigned-bit-shift-right value (- bits 8)) 0xff))
                (recur (rest chars) (- bits 8) value))
            (recur (rest chars) bits value)))
        nil))
    (js/Uint8Array.from out)))

;; ── CID → the digest a reader must reproduce ────────────────────────────────

(def ^:private sha2-256-code 0x12)

(defn- read-uvarint
  "→ [value next-index], or nil if the bytes run out. Bounded to 5 groups: a
   CID prefix field wider than 35 bits is not something this code should try
   to interpret."
  [^js bytes i]
  (loop [i i shift 0 acc 0 groups 0]
    (when (and (< i (.-length bytes)) (< groups 5))
      (let [b (aget bytes i)
            acc (+ acc (* (bit-and b 0x7f) (js/Math.pow 2 shift)))]
        (if (zero? (bit-and b 0x80))
          [acc (inc i)]
          (recur (inc i) (+ shift 7) acc (inc groups)))))))

(defn parse-cid
  "Parse a CID string into the parts a block reader needs.

   → `{:version :codec :multihash-code :digest}` where `:digest` is a
   `Uint8Array`, or throws. CIDv0 (`Qm…`, base58btc, dag-pb, sha2-256) and
   CIDv1 in multibase 'b' (base32-lower) are understood; every other
   multibase prefix throws rather than guessing, because guessing a base
   silently produces a digest that will never match and would be reported as
   corruption rather than as the unsupported encoding it is."
  [^string s]
  (cond
    (str/blank? s)
    (throw (ex-info "empty CID" {:type ::invalid-cid :cid s}))

    ;; CIDv0: base58btc multihash, implicitly dag-pb + sha2-256.
    (and (str/starts-with? s "Qm") (= 46 (count s)))
    (let [mh (cid/base58btc-decode s)]
      (when-not (and (= sha2-256-code (aget mh 0)) (= 32 (aget mh 1)))
        (throw (ex-info "CIDv0 is not sha2-256/32" {:type ::unsupported-multihash :cid s})))
      {:version 0 :codec 0x70 :multihash-code sha2-256-code
       :digest (.slice mh 2)})

    (str/starts-with? s "b")
    (let [bs (base32-lower-no-pad-decode (subs s 1))]
      (if-let [[version i] (read-uvarint bs 0)]
        (if-not (= 1 version)
          (throw (ex-info (str "unsupported CID version " version)
                          {:type ::invalid-cid :cid s :version version}))
          (let [[codec i] (or (read-uvarint bs i)
                              (throw (ex-info "truncated CID codec"
                                              {:type ::invalid-cid :cid s})))
                [mh-code i] (or (read-uvarint bs i)
                                (throw (ex-info "truncated CID multihash code"
                                                {:type ::invalid-cid :cid s})))
                [mh-len i] (or (read-uvarint bs i)
                               (throw (ex-info "truncated CID digest length"
                                               {:type ::invalid-cid :cid s})))]
            (when-not (= mh-len (- (.-length bs) i))
              (throw (ex-info "CID digest length does not match the payload"
                              {:type ::invalid-cid :cid s
                               :declared mh-len :actual (- (.-length bs) i)})))
            {:version 1 :codec codec :multihash-code mh-code
             :digest (.slice bs i)}))
        (throw (ex-info "truncated CID" {:type ::invalid-cid :cid s}))))

    :else
    (throw (ex-info (str "unsupported multibase prefix " (subs s 0 1))
                    {:type ::unsupported-multibase :cid s}))))

(defn- ->byte-vec
  "`Uint8Array` (or anything seqable) → a vector of unsigned bytes, which is
   what `sha2.core` both consumes and produces."
  [bytes]
  (if (vector? bytes) bytes (vec (js/Array.from bytes))))

(defn- same-bytes?
  "Constant-time-in-content comparison of two byte sequences. Length is
   allowed to leak — a digest length is public — but a mismatching byte must
   not be locatable by timing the compare."
  [a b]
  (let [a (->byte-vec a) b (->byte-vec b)]
    (and (= (count a) (count b))
         (zero? (reduce bit-or 0 (map bit-xor a b))))))

(defn verify-block
  "Does `bytes` hash to the address `cid` names?

   → true / false. Throws `:unsupported-multihash` when the CID names a hash
   this code cannot compute: that is not the same answer as `false`, and
   collapsing the two would let an unhashable CID read as a clean check.

   The multihash covers the block bytes whatever the codec is, so this is as
   true of a dag-cbor commit as of a raw leaf."
  [^string cid-str ^js bytes]
  (let [{:keys [multihash-code digest]} (parse-cid cid-str)]
    (when-not (= sha2-256-code multihash-code)
      (throw (ex-info (str "cannot verify multihash 0x"
                           (.toString multihash-code 16))
                      {:type ::unsupported-multihash
                       :cid cid-str :multihash-code multihash-code})))
    (same-bytes? digest (sha2/sha256 (->byte-vec bytes)))))

;; ── the transport ───────────────────────────────────────────────────────────

(def ^:private default-endpoint "https://kotobase.net")
(def ^:private default-path-prefix "/ipld/")

(defn- block-url [endpoint path-prefix cid]
  (str (str/replace endpoint #"/+$" "") path-prefix cid))

(defn client
  "→ a read-only block client for the `/ipld/<cid>` surface.

   `:endpoint`     origin to read from (default `https://kotobase.net`).
   `:path-prefix`  surface on that origin (default `/ipld/`, the datom
                   plane). `/ipfs/` on `https://ipfs.kotobase.net` is the
                   archive plane and answers the same way; the verification
                   here does not care which, because it re-derives the
                   address from the bytes rather than trusting the route.
   `:fetch-fn`     injected `fetch` (default the global one) — the seam the
                   tests drive, and the seam a caller uses to add a cache.
   `:authorization` the `Authorization` value `put-block!` sends, or a
                   0-arg function returning one. A function is the useful
                   form: a CACAO expires and its nonce is single-use, so a
                   client that outlives either must mint the next one.
                   Reads ignore this entirely.

   The returned map is deliberately the shape
   `kotoba-lang/kotobase-storage-ipfs` asks for
   (`{:client {:get-block …}}`), so composing it into a real `IBlockStore`
   needs no adapter:

       (ipfs/open {:client (blocks/client {:endpoint \"https://kotobase.net\"})})

   `:put-block!` is absent on purpose. Writing needs a CACAO and belongs to
   `kotobase.client`; a store built from this one is read-only and will say
   so by failing to satisfy a write, rather than by pretending."
  [{:keys [endpoint path-prefix fetch-fn authorization]}]
  (let [endpoint (or endpoint default-endpoint)
        path-prefix (or path-prefix default-path-prefix)
        f (or fetch-fn (.-fetch js/globalThis))
        ;; A thunk, not a captured string: a CACAO carries a short TTL and a
        ;; single-use nonce, so a client that outlives one of them must be
        ;; able to mint the next. Holding the value would quietly turn this
        ;; into a client that stops working after five minutes.
        resolve-auth (fn [] (if (fn? authorization) (authorization) authorization))]
    {:endpoint endpoint
     :path-prefix path-prefix
     :get-block
     (fn get-block [cid]
       ;; Parse before the request: an unreadable CID is the caller's bug and
       ;; should not become a round trip, still less a 404 that reads as
       ;; "this block does not exist".
       (let [{:keys [multihash-code]} (parse-cid cid)]
         (when-not (= sha2-256-code multihash-code)
           (throw (ex-info (str "refusing to fetch a block this client cannot verify: "
                                "multihash 0x" (.toString multihash-code 16))
                           {:type ::unsupported-multihash :cid cid}))))
       (-> (f (block-url endpoint path-prefix cid)
              #js {:method "GET"
                   :headers #js {"accept" "application/vnd.ipld.raw"}})
           (.then
            (fn [resp]
              (let [status (.-status resp)]
                (cond
                  (= 404 status) (js/Promise.resolve nil)
                  (not (.-ok resp))
                  (js/Promise.reject
                   (ex-info (str "block read failed: HTTP " status)
                            {:type ::http :cid cid :status status}))
                  :else
                  (-> (.arrayBuffer resp)
                      (.then (fn [buf]
                               (let [bytes (js/Uint8Array. buf)]
                                 (if (verify-block cid bytes)
                                   bytes
                                   (throw (ex-info
                                           "block bytes do not hash to the CID they were fetched under"
                                           {:type ::corrupt :cid cid
                                            :bytes (.-length bytes)})))))))))))))

     ;; Two arities on purpose. `kotobase-storage-ipfs`'s injected-client
     ;; contract is `(put-block! cid bytes)` and validates with `ifn?`, which
     ;; a 3-arity-only function would satisfy and then fail at the first
     ;; call — green wiring, broken write. The 3-arity form stays for a
     ;; caller that mints per request without configuring the client.
     :put-block!
     (fn put-block!
       ([cid bytes] (put-block! cid bytes (resolve-auth)))
       ([cid ^js bytes authorization]
       ;; Verify before the network, not after. The origin checks too, but a
       ;; caller that mis-addressed a block should learn that from its own
       ;; code rather than from a 400 that looks like a service problem.
       (when-not (verify-block cid bytes)
         (throw (ex-info "refusing to contribute bytes under an address they do not hash to"
                         {:type ::corrupt :cid cid :bytes (.-length bytes)})))
       (when (str/blank? (str authorization))
         (throw (ex-info "put-block! needs an Authorization value; this namespace mints none"
                         {:type ::missing-authorization :cid cid})))
       (-> (f (block-url endpoint path-prefix cid)
              #js {:method "PUT"
                   :headers #js {"authorization" authorization
                                 "content-type" "application/vnd.ipld.raw"}
                   :body bytes})
           (.then
            (fn [resp]
              (let [status (.-status resp)]
                (cond
                  (or (= 204 status) (= 201 status) (= 200 status)) cid
                  (= 401 status)
                  (throw (ex-info "block contribution was not authorized"
                                  {:type ::unauthorized :cid cid :status status}))
                  (= 413 status)
                  (throw (ex-info "block is larger than this surface accepts"
                                  {:type ::too-large :cid cid :status status
                                   :bytes (.-length bytes)}))
                  (= 400 status)
                  (throw (ex-info "origin refused the block as not matching its CID"
                                  {:type ::rejected :cid cid :status status}))
                  :else
                  (throw (ex-info (str "block contribution failed: HTTP " status)
                                  {:type ::http :cid cid :status status})))))))))}))

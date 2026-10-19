(ns kotobase-peer.cache
  "The host side of the block cache — the effects `merkle-lsm` has always
  declared and nothing has ever interpreted.

  `merkle-lsm.core` emits `{:effect/type :cache/get :cid ...}` and
  `{:effect/type :cache/put ...}`. Grep the repo before this namespace existed
  and those two effect types appear exactly twice: at their own definitions.
  The design left a hole for a block cache and nobody filled it, so every read
  went to the object store and every block was re-hashed on arrival.

  What makes this safe is the same thing that makes it small: **a CID-keyed
  cache needs no invalidation.** A content-addressed block cannot change under
  its key, so there is no TTL, no coherence protocol, and no write path to
  keep in step. See `kotoba-lang/block-cache`.

  Two seams, because kotobase-peer has two:

  - `cached-get-fn` wraps the synchronous `(fn [cid] bytes)` that `datoms`,
    `cold-datoms` and `hydrate-db` take. Nothing in this repo changes; the
    HOST composes it.
  - `handle-effect` interprets the merkle-lsm cache effects for a host that
    executes an effect list."
  (:require [block.cache :as bc]
            [ipld.core :as ipld]))

(defn verify-cid!
  "Throw unless `bytes` hash to `cid`. Same check `core/verified-node` does,
  extracted so the cache can run it EXACTLY ONCE — on the read that fetched
  the block, not on every read that finds it."
  [cid bytes]
  (let [actual (ipld/cid bytes)]
    (when-not (= cid actual)
      (throw (ex-info "kotobase-peer: block CID mismatch"
                      {:type :ipld/cid-mismatch :expected-cid cid :actual-cid actual})))))

(defn default-cache
  "A segmented cache sized for a Cloudflare Worker.

  Segmented rather than one budget because snapshot nodes are small and hot
  while data blocks are 16-128 KB (`kotobase-peer.block-sizing`) and cold: one
  wide scan through a shared LRU evicts the metadata to hold blocks it will
  never read again. The 8 KB threshold sits under the smallest data-block
  class, so nodes land in the protected segment and blocks do not.

  Defaults total 20 MB against a 128 MB isolate — deliberately conservative,
  since the working set has to share that budget with the rows the query is
  building."
  ([] (default-cache {}))
  ([{:keys [small-max-bytes large-max-bytes]
     :or {small-max-bytes (* 4 1024 1024) large-max-bytes (* 16 1024 1024)}}]
   (bc/segmented {:small-max-bytes small-max-bytes
                  :large-max-bytes large-max-bytes
                  :threshold-bytes 8192})))

(defn cached-get-fn
  "Wrap a host `get-fn` so repeated block reads are served from `cache` and
  each block is CID-verified once.

  Pass the result anywhere this repo takes a `get-fn`. Nothing else changes —
  which is the point: the cache is composed in by the host rather than built
  into the read path, so it can be sized, shared, swapped for `block.cache/null`,
  or measured without touching the engine."
  ([get-fn] (cached-get-fn (default-cache) get-fn))
  ([cache get-fn] (bc/wrap-get-fn cache get-fn {:verify! verify-cid!})))

(defn handle-effect
  "Interpret one merkle-lsm cache effect against `cache`.

  `:cache/get` -> bytes or nil. `:cache/put` -> nil. Anything else -> `::pass`,
  so a host can thread this in front of its own effect handler without having
  to know which types belong here."
  [cache {:effect/keys [type] :keys [cid bytes] :as _effect}]
  (case type
    :cache/get (bc/lookup cache cid)
    :cache/put (do (bc/store! cache cid bytes) nil)
    ::pass))

(defn stats
  "Cache counters. Read the MISS rate, not the hit count — a cache with a
  million hits and a 40% miss rate is still the thing making reads slow."
  [cache]
  (bc/stats cache))

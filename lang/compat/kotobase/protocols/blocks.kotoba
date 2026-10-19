(ns kotobase.protocols.blocks
  "The shared content-addressed block space every surface reads from.
  The IPFS gateway serves it, AT-Protocol `sync.getBlob` serves it, and
  the pinning API decides `pinned`/`failed` on it (ADR-2607171700,
  KRP §3.3 Content identity).

  ## Bytes do not belong on the datom plane (ADR-2608039970)

  This used to be one thing only: an `IStore` document collection keyed
  by CID. That works, and it is still what a deploy shell with nothing
  else gets — but it means a block's bytes travel as a document value,
  and in `kotobase-protocols-worker` a document value is encoded as a
  datom (`\"doc/val\" (pr-str v)`) on the prolly-tree chain. A 5 MB object
  body becomes datoms. The measured cost of that route is the 4 MiB
  ceiling on `PUT /ipfs/:cid` (superproject ADR-2607175000,
  ADR-2608012600 D4).

  So `ctx` may now carry a **block port**, and the bytes go there instead:

      {:store  <IStore>          ; documents, metadata, audit — unchanged
       :blocks {:get  (fn [cid] -> block | nil)
                :put! (fn [cid block] -> any)
                :list (fn [] -> [cid ...])}
       :cid-of (fn [block] -> cid-string)}   ; optional, see below

  A `block` is `{:bytes <string> :content-type <string> :encoding
  <optional \"base64\">}`, unchanged. `:encoding \"base64\"` marks bytes that
  are a base64 transport encoding of binary content — surfaces serve
  those with `:body-encoding :base64` so the deploy shell decodes them
  back to raw bytes.

  **Without `:blocks`, behaviour is exactly what it was**: the document
  collection below. That is deliberate — `kotobase-protocols-worker` is
  live, and a port it has not adopted yet must not change what it does.

  ### Why this port is not `kotobase.storage/IBlockStore`

  It is the same idea, and a shell holding an `IBlockStore` should be able
  to adapt it to this in a few lines. It is not that protocol because
  these handlers are **synchronous by contract** while `IBlockStore` is
  batch and Promise-returning on cljs — the same wall
  `kotobase-protocols-worker`'s `kotobase-store` namespace documents when
  it explains why it hydrates before the router runs rather than making
  handlers async. A shell needing async reads prefetches, exactly as it
  already does for documents.

  ## Verification is the plane's job, not each surface's

  When `ctx` carries `:cid-of` — `(fn [block] -> cid-string)` — every read
  and every write checks that the block hashes to the key it is stored
  under, and a mismatch **throws** `:kotobase.protocols/cid-mismatch`.

  `:cid-of` takes the whole block rather than its `:bytes` because only
  the shell knows what the CID was computed over: `POST /ipfs` hashes the
  RAW body and stores `:encoding \"base64\"`, so hashing `:bytes` directly
  would compare a digest of base64 text against a digest of the bytes
  that text encodes. The shell owns the encoding convention; this
  namespace owns the invariant.

  A mismatch throws rather than reading as absent, for the reason
  `kotobase.storage.verify` gives: a missing block is indistinguishable
  from a subtree that does not exist, so omitting a tampered one turns a
  corrupt store into a *shorter answer* — bytes quietly gone, looking
  exactly like a cache miss. A CID is only ever asked for because
  something pointed at it, so a mismatch is not \"nothing here\", it is
  proof of bytes that are not the bytes.

  **Without `:cid-of` nothing is verified**, which is what this namespace
  did unconditionally before (\"trusts the caller-supplied CID\"). It is an
  injected capability rather than a dependency because hashing is a host
  capability here — `kotobase.protocols.cid` is digest-agnostic on
  purpose (ADR-2607176000) — and naming it in `ctx` is what makes it
  possible to ask a deployment whether it has one."
  (:require [kotobase.store :as st]))

(def coll
  "The legacy document collection: one `IStore` collection keyed by CID.
  Used when `ctx` carries no `:blocks` port."
  :kotobase.protocols/blocks)

;; ------------------------------------------------------------------- the port

(defn- doc-port
  "The legacy plane: blocks as documents in one `IStore` collection."
  [store]
  {:get (fn [cid] (st/-get store coll cid))
   :put! (fn [cid block] (st/-put store coll cid block))
   :list (fn [] (st/-list store coll))})

(defn- store-arity?
  "True when the caller passed a bare `IStore` rather than a ctx map --
  the arity `kotobase-protocols-worker` still calls."
  [target]
  (satisfies? st/IStore target))

(defn- port [target]
  (if (store-arity? target)
    (doc-port target)
    (or (:blocks target) (doc-port (:store target)))))

(defn- cid-fn [target]
  (when-not (store-arity? target) (:cid-of target)))

(defn- audit!
  "Audit stays on the document plane: it is metadata about the write, not
  the bytes. A ctx with a port and no `:store` has nowhere to put it, and
  says so by having no store rather than by failing."
  [target event]
  (when-let [store (if (store-arity? target) target (:store target))]
    (st/-append store :kotobase.protocols/audit event)))

(defn- verify!
  [op cid-of cid block]
  (when (and cid-of block)
    (let [actual (cid-of block)]
      (when-not (= actual cid)
        (throw (ex-info (str "block does not hash to the CID it is keyed by: " cid)
                        {:type :kotobase.protocols/cid-mismatch
                         :op op :cid cid :actual actual}))))))

;; ------------------------------------------------------------------- reads

(defn get-block
  "The block stored under `cid`, or nil. `target` is a ctx map (see the ns
  docstring) or, for compatibility, a bare `IStore`.

  Throws `:kotobase.protocols/cid-mismatch` when `ctx` carries `:cid-of`
  and the stored block does not hash to `cid`: a miss is nil, a tampered
  block is an error. The ns docstring says why those differ."
  [target cid]
  (let [block ((:get (port target)) cid)]
    (verify! :get (cid-fn target) cid block)
    block))

(defn list-cids
  "Every CID in the block plane."
  [target]
  ((:list (port target))))

;; ------------------------------------------------------------------ writes

(defn put-block!
  "Store `block` under caller-computed `cid`. Idempotent -- content
  addressed, so the same cid means the same bytes. Returns the block as
  stored (with `:content-type` defaulted).

  Throws `:kotobase.protocols/cid-mismatch` before writing anything when
  `ctx` carries `:cid-of` and the block does not hash to `cid`. Bytes are
  never stored under a key they do not hash to."
  [target cid {:keys [bytes content-type encoding]}]
  {:pre [(string? cid) (string? bytes)]}
  (let [block (cond-> {:bytes bytes
                       :content-type (or content-type "application/octet-stream")}
                encoding (assoc :encoding encoding))]
    (verify! :put (cid-fn target) cid block)
    ((:put! (port target)) cid block)
    (audit! target {:surface :blocks :op :put :cid cid :size (count bytes)})
    block))

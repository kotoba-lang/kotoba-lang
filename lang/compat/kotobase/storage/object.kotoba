(ns kotobase.storage.object
  "The large-object plane: bytes too big to move as blocks.

  `IBlockStore` already holds immutable CID-addressed bytes, so the obvious
  question is why a second port exists. Same answer as the block/ref split in
  `kotobase.storage.core`: the operations a caller can rely on are different,
  and the failure mode of guessing is silent.

  - A block is small, fetched in batches, and travels through the process
    that asked for it. A large object is a GB, fetched once, and must NOT
    travel through the process — proxying it is what put a 4 MiB ceiling on
    `PUT /ipfs/:cid` (superproject ADR-2607175000, ADR-2608012600 D4).
  - A block store never deletes; deletion is meaningless for a content
    address. A large-object plane has to answer `git annex drop --from` and
    an LFS quota, which are per-holder lifecycle questions.

  So a provider declares exactly one TRANSFER PROFILE:

  - `:presigned-transfer` — it can hand the client a URL and let the bytes go
    directly to storage. B2/R2 via SigV4.
  - `:proxied-transfer` — the bytes must pass through this process. Embedded
    stores, and anything without a signing key.

  and separately declares whether it can delete at all. A store that cannot
  must say so, because the alternative is a tombstone that reports success
  while the bytes stay readable — which is what `GET /ipfs/:cid` does today."
  (:require [kotobase.storage.object-id :as oid]))

(defprotocol IObjectStore
  (-stat-object [store cid]
    "nil, or {:size-bytes n} (providers may add fields).")
  (-delete-object! [store cid]
    "Return {:deleted? boolean}. A store without `:object-delete` must return
     `{:deleted? false :reason :not-supported}` rather than pretend."))

(defprotocol IPresignedTransfer
  (-presign-put [store cid opts]
    "A grant the client can PUT to. `opts` carries at least `:size-bytes`.")
  (-presign-get [store cid opts]
    "A grant the client can GET from."))

(defprotocol IProxiedTransfer
  (-put-object! [store cid bytes])
  (-get-object [store cid] "bytes, or nil."))

(defprotocol IRangeRead
  (-get-object-range [store cid start end]
    "The bytes of `[start, end)` — HALF-OPEN, like `subs`.

     HTTP `Range` is inclusive at both ends, so a provider sends
     `bytes=<start>-<end - 1>`. Getting that conversion wrong costs or gains
     exactly one byte, which for a CAR frame means a varint that parses and a
     block that does not — so the boundary is stated here rather than left to
     each provider.

     Returns nil when the object does not exist. When the object is shorter
     than `end` it returns the overlap, exactly as HTTP 206 does; a caller
     that needs the full width must check the length it got. Returning fewer
     bytes than asked WITHOUT the caller being able to tell is the failure
     this signature is shaped to avoid — hence bytes or nil, never a short
     read dressed as a complete one."))

(defprotocol IObjectCapabilities
  (-object-capabilities [store] "A set of capability keywords."))

(def transfer-profiles
  "Exactly one of these is mandatory, for the same reason `ref-profiles` is:
  a caller cannot discover by trying. Asking a proxied store to presign
  fails loudly here instead of at deploy time."
  #{:presigned-transfer :proxied-transfer})

(def required-capabilities #{:large-objects})

(def optional-capabilities
  "Two of these are about ranges and they are NOT the same claim.

  `:range-grant` is a property of the URL this store hands out: the caller
  fetches the range itself, directly from the endpoint, and the bytes never
  enter this process. That is the meaning `:range-read` carried when this
  set was first written, and it is the one a presigned store can make.

  `:range-read` is a property of this store: it will return `[start, end)`
  itself, through `IRangeRead`. A packed block store needs THIS one — it has
  to hold the bytes to parse a CAR frame out of them — and a presigned store
  that can only hand out URLs cannot make the claim by having a signing key.

  They were conflated on 2026-08-16 when the pack plane gave `:range-read`
  an operation, which silently redefined a word the S3 adapter was already
  declaring correctly under the old meaning. Splitting them is the fix; the
  old word keeps the new, narrower meaning because that is the one with a
  protocol behind it, and the grant property gets the name that says grant."
  #{:object-delete   ; -delete-object! actually removes the bytes
    :range-read      ; this store implements IRangeRead
    :range-grant     ; a get grant it hands out honours an HTTP Range
    :multipart-put}) ; objects larger than one part can be uploaded

(defn object-store? [value]
  (and (satisfies? IObjectStore value) (satisfies? IObjectCapabilities value)))

(defn transfer-profile
  "Which of `transfer-profiles` this store declares, or nil if it declares
  none or more than one."
  [store]
  (let [declared (filter (-object-capabilities store) transfer-profiles)]
    (when (= 1 (count declared)) (first declared))))

(defn presigned? [store] (= :presigned-transfer (transfer-profile store)))
(defn deletes? [store] (contains? (-object-capabilities store) :object-delete))

(defn range-read?
  "True when the store both declares `:range-read` and implements it.

  Both halves are required because they fail differently: a declaration
  without an implementation crashes the first pack read, and an
  implementation without a declaration is invisible to every caller that
  branches on capabilities. `validate-object-store!` refuses the first."
  [store]
  (and (contains? (-object-capabilities store) :range-read)
       (satisfies? IRangeRead store)))

(defn range-grant?
  "True when a GET grant this store hands out honours an HTTP `Range`.

  Nothing here can verify that — it is a statement about the endpoint the
  URL points at, and the fetch happens somewhere else entirely. Which is
  exactly why it is a different word from `:range-read`: one is checkable
  here and one is not, and a caller deciding whether it can read part of an
  object needs to know which kind of promise it is holding."
  [store]
  (contains? (-object-capabilities store) :range-grant))

(defn validate-object-store!
  "Validate a large-object provider, or throw.

  Beyond the capability check this enforces that the declared transfer
  profile and the implemented transfer protocol agree — a store that claims
  `:presigned-transfer` without `IPresignedTransfer` would otherwise fail on
  the first upload of the first customer."
  ([store] (validate-object-store! store required-capabilities))
  ([store required]
   (when-not (object-store? store)
     (throw (ex-info "Kotobase large-object store does not implement the contract"
                     {:type :kotobase.storage/invalid-object-store})))
   (let [caps (-object-capabilities store)
         missing (remove caps required)]
     (when (seq missing)
       (throw (ex-info "Kotobase large-object store lacks required capabilities"
                       {:type :kotobase.storage/missing-capabilities
                        :missing (set missing)})))
     (let [profile (transfer-profile store)]
       (when-not profile
         (throw (ex-info "Kotobase large-object store must declare exactly one transfer profile"
                         {:type :kotobase.storage/undeclared-transfer-profile
                          :expected transfer-profiles
                          :declared (set (filter caps transfer-profiles))})))
       ;; The branch is on the RESULT of two `satisfies?` calls, not on which
       ;; protocol gets passed to one. In ClojureScript `satisfies?` is a
       ;; macro that resolves its protocol argument at compile time, so
       ;; `(satisfies? (if ... A B) store)` is not a slightly awkward spelling
       ;; of the same thing — it fails to macroexpand, and the whole namespace
       ;; stops compiling. It reads as correct because on the JVM `satisfies?`
       ;; is an ordinary function and the computed form works there, as it
       ;; does under nbb's interpreter; the toolchain that rejects it is
       ;; shadow-cljs, which is the one that builds the Workers. Keep the two
       ;; calls literal.
       (when-not (if (= :presigned-transfer profile)
                   (satisfies? IPresignedTransfer store)
                   (satisfies? IProxiedTransfer store))
         (throw (ex-info "Kotobase large-object store declares a transfer profile it does not implement"
                         {:type :kotobase.storage/transfer-profile-mismatch
                          :profile profile})))
       ;; Same rule, one capability lower down: `:range-read` used to be a
       ;; word with no operation behind it, so nothing could contradict it.
       ;; A pack reader is the first caller that depends on it, and it must
       ;; not discover the gap on the first fetch.
       (when (and (caps :range-read) (not (satisfies? IRangeRead store)))
         (throw (ex-info "Kotobase large-object store declares :range-read without IRangeRead"
                         {:type :kotobase.storage/range-read-capability-mismatch})))))
   store))

;; ── grants ──────────────────────────────────────────────────────────────────

(def ^:private required-grant-keys #{:href :method :expires-at})

(defn valid-grant?
  "Shape check for what `-presign-put`/`-presign-get` return."
  [grant]
  (and (map? grant)
       (every? #(contains? grant %) required-grant-keys)
       (string? (:href grant))
       (keyword? (:method grant))
       (map? (or (:headers grant) {}))))

(defn bound-put-grant?
  "True when a PUT grant constrains what may be written under it.

  An unbound presigned PUT is a blank cheque: whoever holds the URL may store
  any number of bytes under a CID whose digest they never had to know. The
  binding must be part of the SIGNATURE — listing `content-length` in
  `:signed-headers` while signing only `host` binds nothing — so providers
  report which headers they actually signed."
  [grant]
  (and (valid-grant? grant)
       (contains? (set (:signed-headers grant)) "content-length")))

(defn grant
  "Construct and validate a grant. Providers should build grants through this
  rather than returning a bare map, so a missing expiry is caught where it is
  written."
  [{:keys [href method headers expires-at signed-headers] :as g}]
  (let [g (cond-> (assoc g :href href :method method :expires-at expires-at)
            (nil? headers) (assoc :headers {})
            (nil? signed-headers) (assoc :signed-headers []))]
    (when-not (valid-grant? g)
      (throw (ex-info "invalid large-object transfer grant"
                      {:type :kotobase.storage/invalid-grant :grant g})))
    g))

;; ── helpers ─────────────────────────────────────────────────────────────────

(defn assert-object-cid!
  "Large objects are addressed by CIDv1(raw, sha2-256) and nothing else.

  Providers call this before touching storage so a caller who passed an LFS
  oid or an annex key straight through gets told, instead of creating an
  object under a key nobody can resolve."
  [cid]
  (when-not (oid/cid->digest-hex cid)
    (throw (ex-info "large-object id must be a CIDv1(raw, sha2-256)"
                    {:type :kotobase.storage/invalid-object-cid :cid cid})))
  cid)

(defn present?
  "True when THIS store holds the object.

  Deliberately not 'can these bytes be fetched from somewhere'. The annex
  adapter's `CHECKPRESENT` answers from this, and git-annex decides whether
  it is safe to drop its last local copy from the answer: a store that says
  yes because a public gateway happens to serve the CID makes git-annex
  destroy the only copy it controls (ADR-2608012600, Context 6)."
  [store cid]
  (some? (-stat-object store cid)))

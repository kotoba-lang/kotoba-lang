(ns drive.object
  "Where the bytes are, and who may move them.

  `drive.workspace` models the tree, the permissions, the quota and the
  versions, and deliberately stops short of content: an item carries a
  `:drive/object-ref` and nothing here interprets it. That left a hole, and
  every consumer filling it privately fills it slightly differently — which
  for a permission boundary means each one gets a different answer to *may
  this principal read this file*.

  So the seam is here, next to the rules it has to obey.

  ## Bytes are a vector of unsigned ints

  Across `IObjectStore`, in both directions. The protocol did not say so at
  first, and within hours of it existing there were two implementations that
  disagreed: `drive.store.memory` and `drive.store.fs` pass vectors, and a
  Filecoin-backed store in `cloud-itonami-app` passes `byte[]`. Both are
  reasonable and neither is wrong — what was wrong was the protocol not
  saying.

  Unstated, the mismatch does not surface at the seam. It surfaces in whoever
  called `read-item`, as bytes that are a vector on one deployment and an
  array on another, and the code that works on both is code someone had to
  find out they needed.

  **What a byte is, though, is not drive's to decide.** `kotoba.bytes` exists
  for that, says the same thing in its own README, and was already a
  dependency of the protocol libraries around this one. Stating it a second
  time here — as this namespace did for a day, with a private `->bytes` — is
  how a workspace ends up with two definitions that can drift, which is the
  failure this section is about. So the definition is `kotoba.bytes/->bytes`
  and drive is one of its callers.

  It is applied at two places, which is not redundancy. `store-of` is a
  convenience; the contract is on `-get-object`, and a store written as a
  bare `reify` never passes through the convenience. Normalising only in
  `store-of` would mean the protocol held for stores built one particular
  way — which is not what a protocol is.

  ## What a store is not allowed to decide

  An `IObjectStore` is four operations over opaque references. It does not
  know about principals, roles, share links, trash or quota, and it must not:
  a store that could be asked directly for a reference is a store that can be
  asked for someone else's file. Everything in this namespace exists so that
  the store is only ever reached *after* the answer is yes.

  ## Four rules that are not obvious

  - **Trashed items are not readable.** `can-read?` says nothing about trash —
    `visible-items` filters it separately — so a caller composing `can-read?`
    with a store hands out deleted content. The check belongs with the fetch.
  - **Quota is checked before the bytes move, not after.** `add-version`
    throws when the total would exceed, which is correct and too late: the
    upload has already happened and the bandwidth is already spent.
  - **A new version must have a new object reference.** Reusing one silently
    replaces the bytes of an earlier version, and the history that says
    otherwise is still sitting in `:drive/versions`.
  - **A share link may read and never write.** `create-share-link` already
    refuses any role but `:viewer` and `:commenter`; this refuses the write
    rather than trusting that.

  ## Ordering, and what is left behind when something fails

  A write checks permission and quota first — both pure — then stores the
  bytes, then records the version. A store failure leaves the workspace
  untouched, which is the safe direction: the model never claims bytes that
  are not there. The other order can strand an object nothing references, and
  that is the direction this deliberately does not take."
  (:require [kotoba.bytes :as b]
            [drive.workspace :as ws]))

(defprotocol IObjectStore
  "Bytes by opaque reference. Four operations, no opinions.

  A reference is whatever the caller chose when it wrote — a content hash, a
  uuid, a path. Nothing here parses one, because a store that understood
  references would be a store that could be asked to enumerate them."
  (-get-object [this ref])
  (-put-object [this ref bytes])
  (-delete-object [this ref])
  (-object-exists? [this ref]))

(defn- refuse [reason data]
  (merge {:ok? false :reason reason} data))

;; ── reading ─────────────────────────────────────────────────────────────────

(defn readable?
  "Whether `principal-id` may read the bytes of `item-id` right now.

  Both halves matter. `can-read?` is the ACL and `:drive/trashed?` is whether
  the item still exists as far as its owner is concerned; a caller that checks
  only the first hands out deleted content."
  [workspace item-id principal-id]
  (let [item (ws/item workspace item-id)]
    (boolean (and item
                  (not (:drive/trashed? item))
                  (ws/can-read? workspace item-id principal-id)))))

(defn read-item
  "The bytes of `item-id`, if this principal may have them.

  Returns `{:ok? true :bytes b :object-ref r}` or `{:ok? false :reason kw}`.
  `:no-content` is not an error condition to route around: a file that has
  never had a version has no object reference, and that is different from a
  file whose bytes are missing from the store."
  [workspace store item-id principal-id]
  (let [item (ws/item workspace item-id)]
    (cond
      (nil? item)                      (refuse :no-such-item {:item-id item-id})
      (:drive/trashed? item)           (refuse :item-is-trashed {:item-id item-id})
      (not (ws/can-read? workspace item-id principal-id))
      (refuse :not-permitted {:item-id item-id :principal principal-id})

      (= :folder (:drive/kind item))   (refuse :folders-have-no-bytes {:item-id item-id})

      :else
      (if-let [ref (:drive/object-ref item)]
        (if-let [bytes (-get-object store ref)]
          {:ok? true :bytes (b/->bytes bytes) :object-ref ref}
          ;; the model says these bytes exist and the store disagrees. Worth
          ;; its own reason: it is a broken node, not a permission answer.
          (refuse :object-missing-from-store {:item-id item-id :object-ref ref}))
        (refuse :no-content {:item-id item-id})))))

(defn read-via-share-link
  "The bytes behind a share link.

  The link carries its own role and its own expiry, so this does not consult
  the ACL — it consults the link. An expired or revoked token is
  indistinguishable from one that never existed, which is the point of a
  token."
  [workspace store token now]
  (if-let [link (ws/resolve-share-link workspace token now)]
    (let [item-id (:drive.share/item-id link)
          item    (ws/item workspace item-id)]
      (cond
        (nil? item)            (refuse :no-such-item {:item-id item-id})
        (:drive/trashed? item) (refuse :item-is-trashed {:item-id item-id})
        (= :folder (:drive/kind item)) (refuse :folders-have-no-bytes {:item-id item-id})
        :else
        (if-let [ref (:drive/object-ref item)]
          (if-let [bytes (-get-object store ref)]
            {:ok? true :bytes (b/->bytes bytes) :object-ref ref
             :role (:drive.share/role link)}
            (refuse :object-missing-from-store {:item-id item-id :object-ref ref}))
          (refuse :no-content {:item-id item-id}))))
    (refuse :no-such-link {})))

;; ── writing ─────────────────────────────────────────────────────────────────

(defn- used-refs
  "Every object reference this workspace has ever recorded.

  Versions rather than current items: an earlier version's bytes are still
  addressed by their reference, and reusing one replaces them."
  [workspace]
  (into #{}
        (comp (mapcat (fn [item]
                        (cons (:drive/object-ref item)
                              (map :drive.version/object-ref (:drive/versions item)))))
              (filter some?))
        (vals (:drive.workspace/items workspace))))

(defn would-exceed-quota?
  [workspace n]
  (> (+ (:drive.workspace/used-bytes workspace) n)
     (:drive.workspace/quota-bytes workspace)))

(defn write-item
  "Store `bytes` as a new version of `item-id`.

  Returns `{:ok? true :workspace ws' :object-ref r}` or a refusal. `opts`
  needs `:object-ref` — the caller names it, because a reference that meant
  something to this library would be a reference this library had to be able
  to generate, and that means randomness or hashing in a namespace that has
  neither.

  The version records `principal-id` as `:drive.version/author`. Not as an
  option, because an author the caller passes is a history the caller can
  write — and the value is already here, having just been checked against
  the ACL. Before an item could be shared this was redundant; the moment two
  principals can write one item, a history that cannot say which of them did
  is a history of nothing.

  Nothing is written until permission, quota and reference are all settled."
  [workspace store item-id principal-id bytes
   {:keys [object-ref created-at] :as _opts}]
  ;; Normalised before it is measured, not just before it is stored. `count`
  ;; on a string is characters, so a caller writing "日本語" would be charged 3
  ;; against quota and store 9 — the quota would drift from the bytes it is
  ;; supposed to be counting, in the direction that lets a workspace exceed it.
  (let [bytes (b/->bytes bytes)
        item  (ws/item workspace item-id)
        size  (count bytes)]
    (cond
      (nil? item)                      (refuse :no-such-item {:item-id item-id})
      (:drive/trashed? item)           (refuse :item-is-trashed {:item-id item-id})
      (= :folder (:drive/kind item))   (refuse :folders-have-no-bytes {:item-id item-id})

      (not (ws/can-write? workspace item-id principal-id))
      (refuse :not-permitted {:item-id item-id :principal principal-id})

      (nil? object-ref)                (refuse :no-object-ref {})

      (contains? (used-refs workspace) object-ref)
      ;; silently replacing an earlier version's bytes, with the history that
      ;; says otherwise still sitting in :drive/versions
      (refuse :object-ref-already-used {:object-ref object-ref})

      (would-exceed-quota? workspace size)
      ;; before the upload rather than after it. `add-version` throws on this
      ;; too, and by then the bandwidth is spent.
      (refuse :quota-exceeded {:size size
                               :used (:drive.workspace/used-bytes workspace)
                               :quota (:drive.workspace/quota-bytes workspace)})

      :else
      (do
        (-put-object store object-ref bytes)
        {:ok? true
         :object-ref object-ref
         ;; The author is the principal this write was permitted for, not a
         ;; value the caller may name. A version that recorded whoever the
         ;; caller said wrote it would be a history that can be written to
         ;; say anything, and the permission answer above is the only thing
         ;; here that knows who this actually was.
         :workspace (ws/add-version workspace item-id
                                    (cond-> {:drive.version/object-ref object-ref
                                             :drive.version/size-bytes size
                                             :drive.version/author principal-id}
                                      created-at (assoc :drive.version/created-at created-at)))}))))

(defn forget-item
  "Delete the bytes of every version of `item-id` and drop its reference.

  Not called on trash — trashing is reversible and this is not. A caller that
  wires this to the trash button has made deletion silent and permanent.

  Freed bytes are returned to the quota, because a workspace that counts bytes
  nobody can reach fills up and stops accepting writes for no reason anyone
  can see."
  [workspace store item-id principal-id]
  (let [item (ws/item workspace item-id)]
    (cond
      (nil? item) (refuse :no-such-item {:item-id item-id})

      (not (ws/can-write? workspace item-id principal-id))
      (refuse :not-permitted {:item-id item-id :principal principal-id})

      :else
      (let [refs  (into #{} (comp (map :drive.version/object-ref) (filter some?))
                        (:drive/versions item))
            refs  (cond-> refs (:drive/object-ref item) (conj (:drive/object-ref item)))
            freed (reduce + 0 (keep :drive.version/size-bytes (:drive/versions item)))]
        (doseq [ref refs] (-delete-object store ref))
        {:ok? true
         :deleted (count refs)
         :freed-bytes freed
         :workspace (-> workspace
                        (assoc-in [:drive.workspace/items item-id :drive/object-ref] nil)
                        (assoc-in [:drive.workspace/items item-id :drive/versions] [])
                        (update :drive.workspace/used-bytes #(max 0 (- % freed))))}))))

;; ── an adapter with no dependency ───────────────────────────────────────────

(defn store-of
  "An `IObjectStore` from four functions.

  This is how a backend is attached without either library depending on the
  other. `kotoba-lang/io-storj` has `get-object`, `put-object`,
  `delete-object` and `head-object`; wrapping them here means `drive` does not
  require it and it does not require `drive` — whoever builds the store
  depends on both, and that is the application."
  [{:keys [get-object put-object delete-object exists? bytes-out] :as fns
    :or   {bytes-out identity}}]
  ;; A misspelled key is otherwise a store that answers `nil` to every read —
  ;; indistinguishable, at the call site, from a backend that is simply empty,
  ;; and it stays that way until someone wonders why nothing is ever there.
  ;; Construction is the moment this is still cheap to say.
  (when-let [missing (seq (remove #(ifn? (get fns %))
                                  [:get-object :put-object :delete-object]))]
    (throw (ex-info "store-of needs a function for each operation"
                    {:missing (vec missing) :given (vec (keys fns))})))
  (when-let [unknown (seq (remove #{:get-object :put-object :delete-object
                                    :exists? :bytes-out}
                                  (keys fns)))]
    (throw (ex-info "store-of given a key it does not use"
                    {:unknown (vec unknown)})))
  (reify IObjectStore
    (-get-object [_ ref] (b/->bytes (get-object ref)))
    ;; `:bytes-out` is how a backend that wants arrays says so. Default
    ;; identity, because the protocol's shape is the vector — a backend
    ;; needing something else converts, rather than every consumer.
    (-put-object [_ ref bytes] (put-object ref (bytes-out bytes)))
    (-delete-object [_ ref] (delete-object ref))
    (-object-exists? [_ ref]
      (if exists? (exists? ref) (some? (get-object ref))))))

(ns kotoba.codebase.backend.kotobase
  "A codebase backed by any `kotobase.storage` provider.

  `kotobase-storage` is the provider-neutral contract the rest of this fleet
  already speaks — PostgreSQL, S3/R2, IPFS/IPNS, git, kura and an in-memory
  oracle all implement it, and it carries zero runtime dependencies. Wiring the
  codebase to it means the definition plane inherits every one of those hosts
  without this repository learning any of them.

  Two protocols, and the mapping is not arbitrary:

  - `IBlockStore` takes `:block` and `:artifact` unchanged, because in both
    spaces the key already IS the hash of the value. A dag-cbor block and a raw
    artifact cannot collide even in one keyspace: the codec is inside the CID.
  - `IRefStore` takes the namespace head, which is the whole reason a mutable
    plane exists here at all.

  The build cache is the interesting case. Its key is the hash of the
  DESCRIPTOR, not of the entry, so it is not a block and cannot be stored as
  one. It becomes what it actually is — a pointer: the entry goes in the block
  plane under its own raw CID, and a ref named for the descriptor points at it.
  That is one more round trip than a directory write, and it is the honest
  shape rather than filing bytes under a name they do not hash to.

  ## What this backend cannot do, and why it says so

  `-list-keys` throws. `kotobase.storage`'s contract has no enumeration: a
  block store answers `-get-blocks` for CIDs you already know, which is what
  lets it sit on hosts that genuinely cannot list (a content-addressed network
  has no directory to read). Enumeration is a property of the local filesystem
  layout, not of content addressing, and pretending otherwise would make
  `block-cids` silently return nothing on a remote store — a wrong answer being
  worse than a refused one."
  (:require [kotoba.codebase.backend :as backend]
            [kotobase.storage.core :as storage]
            [multiformats.core :as mf]))

(def ^:private marker-schema "kotoba.semantic-codebase-store.v1")

(defn- ref-name [space key]
  (case space
    :head (str "codebase/head/" key)
    :cache (str "codebase/cache/" key)
    :marker "codebase/store"))

(defn- block-present [store cid]
  (get (storage/-get-blocks store [cid]) cid))

(defn- put-block-bytes! [store ^bytes value]
  (let [cid (mf/cidv1-raw value)]
    (if (block-present store cid)
      cid
      (do (storage/-put-blocks! store [{:cid cid :bytes value}]) cid))))

(defn- current-ref-cid [store name]
  (:cid (storage/-read-ref store name)))

(defrecord KotobaseStore [storage]
  backend/ICodebaseStore
  (-initialize! [_]
    (let [marker (.getBytes ^String marker-schema "UTF-8")
          cid (put-block-bytes! storage marker)
          name (ref-name :marker nil)]
      (when-not (current-ref-cid storage name)
        (storage/-compare-and-set-ref! storage name nil cid))
      {:root :kotobase.storage :schema marker-schema}))

  (-initialized? [_]
    (some? (current-ref-cid storage (ref-name :marker nil))))

  (-put-bytes! [_ space key value]
    (case space
      (:block :artifact)
      (if-let [existing (block-present storage key)]
        (if (= (seq existing) (seq value)) :identical :conflict)
        (do (storage/-put-blocks! storage [{:cid key :bytes value}]) :written))

      :cache
      (let [name (ref-name :cache key)]
        (if-let [existing-cid (current-ref-cid storage name)]
          (if (= (seq (block-present storage existing-cid)) (seq value))
            :identical
            :conflict)
          (let [cid (put-block-bytes! storage value)]
            (if (:published? (storage/-compare-and-set-ref! storage name nil cid))
              :written
              ;; another writer got there first; believe the store, not us
              (if (= (seq (block-present storage (current-ref-cid storage name)))
                     (seq value))
                :identical
                :conflict)))))))

  (-get-bytes [_ space key]
    (case space
      (:block :artifact) (block-present storage key)
      :cache (when-let [cid (current-ref-cid storage (ref-name :cache key))]
               (block-present storage cid))))

  (-list-keys [_ space]
    (throw (ex-info "this backend cannot enumerate a keyspace"
                    {:problem :codebase/enumeration-unsupported
                     :space space
                     :backend :kotobase.storage})))

  (-read-head [_ namespace]
    (when-let [cid (current-ref-cid storage (ref-name :head namespace))]
      (when-let [bytes (block-present storage cid)]
        (String. ^bytes bytes "UTF-8"))))

  (-swap-head! [this namespace expected next-cid]
    (let [name (ref-name :head namespace)
          actual (backend/-read-head this namespace)]
      (if-not (= expected actual)
        {:ok? false :actual actual}
        (let [pointer (put-block-bytes! storage (.getBytes ^String next-cid "UTF-8"))
              result (storage/-compare-and-set-ref!
                      storage name (current-ref-cid storage name) pointer)]
          (if (:published? result)
            {:ok? true}
            ;; the ref moved under us: report what is actually there, and let
            ;; `store` raise the same head-conflict a filesystem race raises
            {:ok? false :actual (backend/-read-head this namespace)}))))))

(defn open
  "A codebase over STORAGE, any `kotobase.storage.core` backend."
  [storage]
  (->KotobaseStore storage))

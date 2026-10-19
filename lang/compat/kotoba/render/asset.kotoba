(ns kotoba.render.asset
  "Asset cache for loaded GPU meshes and materials, keyed by string
   identifier (e.g. blob_key). Ported from `kami-render/src/asset.rs`.

   `MeshHandle`/`MaterialHandle` are opaque GPU resource handles in the Rust
   source (`u32` newtypes minted by the host renderer) — here they're just
   opaque values the caller supplies; the cache itself is pure data (a map),
   consistent with `AssetCache` never touching the GPU directly.")

(defn new-cache
  "Empty asset cache: `{:meshes {} :materials {}}`."
  []
  {:meshes {} :materials {}})

(defn insert-mesh
  "Associate `key` with `[handle index-count]`."
  [cache key handle index-count]
  (assoc-in cache [:meshes key] [handle index-count]))

(defn insert-material
  "Associate `key` with `handle`."
  [cache key handle]
  (assoc-in cache [:materials key] handle))

(defn get-mesh
  "`[handle index-count]` for `key`, or nil."
  [cache key]
  (get-in cache [:meshes key]))

(defn get-material
  "Handle for `key`, or nil."
  [cache key]
  (get-in cache [:materials key]))

(defn has-mesh?
  [cache key]
  (contains? (:meshes cache) key))

(defn mesh-count [cache] (count (:meshes cache)))
(defn material-count [cache] (count (:materials cache)))

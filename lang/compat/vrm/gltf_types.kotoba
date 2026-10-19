(ns vrm.gltf-types
  "glTF 2.0 JSON schema — component-type/target constants + document
  constructors. Restored from `kami-vrm/src/gltf_types.rs`
  (kotoba-lang/kami-engine, deleted PR #82) as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  Unlike the Rust original (which used `serde` structs with `#[serde(rename_all
  = \"camelCase\")]` field renaming), a glTF document here is just the raw
  parsed JSON EDN tree from `vrm.json/parse` — object keys are exact-wire-name
  keywords (`:bufferView`, `:componentType`, `:byteOffset`, `:extensionsUsed`,
  ...), so no translation layer is needed between the wire format and the
  in-memory shape. This namespace only supplies the constants and default
  document/asset builders the other modules need.")

;; glTF component type constants.
(def component-type-byte 5120)
(def component-type-unsigned-byte 5121)
(def component-type-short 5122)
(def component-type-unsigned-short 5123)
(def component-type-unsigned-int 5125)
(def component-type-float 5126)

;; glTF buffer view target constants.
(def buffer-target-array-buffer 34962)
(def buffer-target-element-array-buffer 34963)

(defn asset
  ([] (asset {}))
  ([{:keys [version generator]}]
   {:version (or version "2.0") :generator generator}))

(defn gltf-document
  "Build a glTF document map with defaults for all optional/collection fields."
  [m]
  (merge
   {:asset (asset)
    :scene nil
    :scenes []
    :nodes []
    :meshes []
    :accessors []
    :bufferViews []
    :buffers []
    :materials []
    :textures []
    :images []
    :samplers []
    :skins []
    :animations []
    :extensionsUsed []
    :extensionsRequired []
    :extensions nil}
   m))

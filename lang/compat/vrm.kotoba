(ns vrm
  "Zero-dep portable CLJC. Restored from the legacy kami-engine/kami-vrm Rust
  crate (deleted in kotoba-lang/kami-engine PR #82 'Remove Rust workspace
  from kami-engine') as part of the clj-wgsl migration (ADR-2607010930,
  com-junkawasaki/root).

  VRM (Virtual Reality Model, a glTF 2.0 extension) avatar part composition:
  parse, decompose, compose, export — plus the runtime systems the original
  crate defined data for but didn't apply (spring-bone physics, node
  constraints, expression resolution, first-person visibility).

  Pipeline:
  ```
  GLB bytes  -> vrm.parse/parse-vrm    -> VrmDocument
  VrmDocument -> vrm.part/decompose     -> [VrmPart ...]
  [VrmPart ...] -> vrm.compose/compose  -> VrmDocument
  VrmDocument -> vrm.export/export-glb  -> GLB bytes
  ```

  Modules: `vrm.glb` (GLB binary container), `vrm.gltf-types` (glTF 2.0
  constants/defaults), `vrm.vrm-types` (VRM data shapes), `vrm.parse` (VRM
  1.0 parser), `vrm.compat` (VRM 0.x -> 1.0 conversion), `vrm.humanoid`
  (bone mapping <-> skeleton), `vrm.part` (decomposition), `vrm.compose`
  (part merging), `vrm.export` (GLB writer), `vrm.convert` (accessor ->
  vertex/index buffers), `vrm.spring` (spring-bone physics), `vrm.constraint`
  (node constraint solver), `vrm.expression` (expression weight resolver),
  `vrm.firstperson` (first-person visibility resolver). Plus supporting
  infrastructure not present in the Rust original (which used external
  crates): `vrm.json` (dependency-free JSON parser/serializer) and
  `vrm.math` (glam-equivalent Vec3/Quat/Mat4, matching
  `kotoba-lang/skeleton`'s `skeleton/math.cljc` conventions).

  Errors are raised via `ex-info` with a `:vrm/error` key (see `error-info`)
  in place of the original `VrmError` enum — callers can `ex-data` to
  inspect `{:vrm/error <keyword> ...}`. Native execution (wgpu / wasmtime /
  wasmi) stays substrate; this namespace owns the CLJC contracts / data
  interpreters / EDN IR for the domain."
  (:require [vrm.glb :as glb]
            [vrm.gltf-types :as gltf-types]
            [vrm.vrm-types :as vrm-types]
            [vrm.parse :as parse]
            [vrm.compat :as compat]
            [vrm.humanoid :as humanoid]
            [vrm.part :as part]
            [vrm.compose :as compose]
            [vrm.export :as export]
            [vrm.convert :as convert]
            [vrm.spring :as spring]
            [vrm.constraint :as constraint]
            [vrm.expression :as expression]
            [vrm.firstperson :as firstperson]))

;; ── Re-exports for convenience (mirrors the original `pub use` set) ──
;;
;; `compose-parts` (not `compose`, /loop maturity pass bug fix): a def named
;; `compose` in this namespace collides with the required `vrm.compose`
;; namespace itself under ClojureScript `:simple`/`:whitespace` optimization
;; (Google Closure does not rename/munge namespace-qualified JS paths at
;; those levels the way `:advanced` does, so `vrm.compose` — the sub-
;; namespace's own compiled JS object, nested under `vrm` — and `vrm/compose`
;; — this def, also compiling to the `compose` property on the `vrm` JS
;; object — fight over the exact same property). The compiler surfaces this
;; itself: `WARNING: Namespace vrm.compose clashes with var vrm/compose`.
;; At runtime the def's assignment (a plain function) clobbers the whole
;; `vrm.compose` namespace object, so any code that later reaches into
;; `vrm.compose.<anything else>` (e.g. `vrm.compose/decompose` — no,
;; decompose lives in `vrm.part` — but any other member of `vrm.compose`
;; itself, or Closure's own module-init bookkeeping expecting a namespace
;; object there) crashes with something like "Cannot set/read properties of
;; undefined." A `kami-app-character-creator` build hit exactly this
;; (requiring `vrm.parse` directly instead of root `vrm` was that app's own
;; workaround). No other re-export here collides this way (`parse-vrm`/
;; `decompose`/`export-glb` are all named differently from their source
;; namespace's last segment — `vrm.parse`/`vrm.part`/`vrm.export` — so only
;; `compose` had this exact name-for-name collision).
(def parse-vrm parse/parse-vrm)
(def decompose part/decompose)
(def compose-parts compose/compose)
(def export-glb export/export-glb)

(def new-expression-manager expression/new-manager)
(def resolve-expression expression/resolve-expression)

(def new-first-person-resolver firstperson/new-resolver)
(def first-person-node-visible? firstperson/node-visible?)

(def to-kami-skeleton humanoid/to-kami-skeleton)

;; ── Error convention ──

(defn error-info
  "Build an `ex-info` matching the original `VrmError` enum's shape:
  `(error-info :invalid-glb \"reason\")` etc. `kind` is one of
  `:invalid-glb :json :missing-extension :accessor-out-of-range
  :buffer-view-out-of-range :incompatible-skeleton :unsupported-version
  :part`."
  ([kind msg] (error-info kind msg {}))
  ([kind msg data]
   (ex-info msg (assoc data :vrm/error kind))))

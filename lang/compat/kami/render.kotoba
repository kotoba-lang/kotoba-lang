(ns kami.render
  "L2 — render-IR builder. Queries the in-memory ECS and produces one frame of
  render-IR: a plain-data, retained-by-id / immediate-by-frame draw description
  (ARCHITECTURE.md §7). The renderer (`kami-render`, Rust/wgpu) is a dumb executor
  of this IR; instancing is the default, not an optimization."
  (:require [kami.ecs  :as ecs]
            [kami.math :as m]
            [kami.render.authority :as authority]))

(def nintendo-cream
  "Default clear color #f0ead6 (KAMI Engine prohibits dark themes; see §14)."
  authority/default-clear)

(def ^:private default-rot [0.0 0.0 0.0 1.0])
(def ^:private default-scale [1.0 1.0 1.0])

(defn- model-of
  "Column-major model matrix from an entity's TRS components (with defaults)."
  [e]
  (m/from-trs (:transform/translation e [0.0 0.0 0.0])
              (:transform/rotation e default-rot)
              (:transform/scale e default-scale)))

(defn- asset-id
  "Resolve a mesh/material/shader ref to its string/uuid id."
  [r]
  (cond (map? r) (:asset/id r) (vector? r) (second r) :else r))

(defn camera-ir
  "Build {:view <f32×16> :proj <f32×16>} from the active camera entity in `world`
  (the one with :camera/active? true). View = inverse of the camera's world
  transform; proj from fov/near/far + `aspect`."
  [world aspect]
  (let [[_ cam] (first (ecs/query world #{:camera/active?}))]
    (when-not cam
      (throw (ex-info "camera-ir: no entity with :camera/active? true" {})))
    {:view (m/invert-rigid (model-of cam))
     :proj (if (= :ortho (:camera/projection cam))
             ;; orthographic screen-space (2D boards, e.g. freeboard ADR-2606280200)
             (m/ortho 0.0 (double (:camera/ortho-w cam 1280.0)) (double (:camera/ortho-h cam 720.0)) 0.0
                      (:camera/near cam -1.0) (:camera/far cam 1.0))
             (m/perspective (:camera/fov cam 60.0) aspect
                            (:camera/near cam 0.1) (:camera/far cam 1000.0)))}))

(defn- pipeline-of
  "Choose the pipeline for an entity: explicit :shader/asset id → that registered
  pipeline; otherwise the default built-in :pbr."
  [e]
  (if-let [s (:shader/asset e)] (asset-id s) :pbr))

(defn- texture-of
  "Texture asset id for an entity (a sampled image / glyph atlas), or nil."
  [e]
  (when-let [t (:texture/asset e)] (asset-id t)))

(defn merge-instances
  "Group renderable entities (those carrying a :mesh/asset) sharing
  (pipeline, mesh, material) into a single instanced draw. Returns a seq of
  :draw maps whose :draw/instances carries one flattened model-matrix array
  (→ a KAMI Dtype/Mat4 column in `kami.ipc`) plus a flattened tint array."
  [world]
  ;; Sort renderables by eid so both the group order (sorted by key below) AND the
  ;; per-instance order within a group are deterministic — `ecs/query` walks a set
  ;; intersection whose iteration order is unspecified. Determinism makes
  ;; `kami.ipc/pack` output byte-reproducible (the record/replay / golden surface).
  (let [renderable (sort-by #(str (:kami/eid %))
                            (map second (ecs/query world #{:mesh/asset})))
        groups (group-by (juxt pipeline-of
                               #(asset-id (:mesh/asset %))
                               #(asset-id (:material/asset %))
                               texture-of)
                         renderable)]
    (for [[[pipeline mesh material texture] ents] (sort-by (comp str first) groups)
          :let [models (vec (mapcat model-of ents))
                tints  (vec (mapcat #(get-in % [:material/params :tint] [1.0 1.0 1.0 1.0]) ents))]]
      (cond-> {:draw/pipeline pipeline
               :draw/mesh     mesh
               :draw/material material
               :draw/instances {:count (count ents)
                                :model models
                                :tint  tints}}
        texture (assoc :draw/texture texture)))))

(defn draws-for
  "Build the draw-list for one render pass. Currently the single :main pass holds
  every instanced draw; multi-pass routing (shadow, postfx) is future work."
  [world pass-id]
  (case pass-id
    :main (vec (merge-instances world))
    []))

(defn frame
  "Assemble one full render-IR frame map (§7):
     {:frame/n n :frame/clear [...] :frame/camera {...} :frame/passes [...]}
  Pure given the ECS world. Serializable — the golden-test / record-replay
  surface. Hand to `kami.ipc/pack` then `kami.gpu/submit!`."
  [world {:keys [n aspect clear]
          :or   {n 0 aspect 1.7777778 clear nintendo-cream}}]
  (let [{:keys [:pass/id :pass/target]} authority/default-pass]
    {:frame/n      n
     :frame/clear  clear
     :frame/camera (camera-ir world aspect)
     :frame/passes [{:pass/id     id
                     :pass/target target
                     :pass/draws  (draws-for world id)}]}))

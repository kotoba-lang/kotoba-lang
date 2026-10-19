(ns vrm.constraint
  "VRM node constraint solver (VRMC_node_constraint spec). Supports three
  constraint types: `:rotation` (destination inherits source's local
  rotation delta, slerped by weight), `:aim` (destination rotates so its
  local aim axis points toward the source position, world space), `:roll`
  (destination inherits the component of source's rotation around a
  specified roll axis). Restored from `kami-vrm/src/constraint.rs`
  (kotoba-lang/kami-engine, deleted PR #82) as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  Reference: <https://github.com/vrm-c/vrm-specification/blob/master/specifications/VRMC_node_constraint-1.0/README.md>"
  (:require [vrm.math :as m]))

(defn- rest-local-rot [gltf node-idx]
  (or (:rotation (get (:nodes gltf) node-idx)) m/quat-identity))

(defn new-solver
  "Build a constraint solver from the parsed VrmDocument. Returns
  `{:entries [ConstraintEntry ...]}`."
  [doc]
  (let [gltf (:gltf doc)]
    {:entries
     (mapv
      (fn [c]
        (let [dest-initial-local-rot (rest-local-rot gltf (:node c))
              constraint (:constraint c)
              [source-node kind]
              (case (:type constraint)
                :rotation [(:source constraint) {:kind :rotation :weight (:weight constraint)}]
                :aim [(:source constraint) {:kind :aim :aim-axis (:aim-axis constraint) :weight (:weight constraint)}]
                :roll [(:source constraint) {:kind :roll :roll-axis (:roll-axis constraint) :weight (:weight constraint)}])
              source-initial-local-rot (rest-local-rot gltf source-node)]
          {:dest-node (:node c) :source-node source-node :kind kind
           :dest-initial-local-rot dest-initial-local-rot
           :source-initial-local-rot source-initial-local-rot}))
      (:node-constraints doc))}))

(defn- twist-angle-about
  "Extract the twist angle of quaternion `q` around unit `axis` (swing-twist
  decomposition)."
  [[qx qy qz qw] [ax ay az]]
  (let [dot (+ (* qx ax) (* qy ay) (* qz az))
        twist (m/quat-normalize [(* ax dot) (* ay dot) (* az dot) qw])
        [tx ty tz tw] twist
        w-clamped (max -1.0 (min 1.0 tw))
        sign (let [d (+ (* tx ax) (* ty ay) (* tz az))] (cond (pos? d) 1.0 (neg? d) -1.0 :else 0.0))]
    (* 2.0 (Math/acos w-clamped) sign)))

(defn apply-constraints
  "Apply all constraints in `solver`. `source-local-rot` is a fn `node-idx ->
  (nilable Quat)` returning the current local rotation of the source node
  (includes upstream pose overrides and spring). `source-world` / `dest-head-world`
  are fns `node-idx -> (nilable Mat4)` for the current world matrices (used
  by `:aim`). Returns `[[dest-node-idx [x y z w]] ...]`."
  [solver source-local-rot source-world dest-head-world]
  (vec
   (keep
    (fn [e]
      (let [{:keys [kind]} e]
        (case (:kind kind)
          :rotation
          (when-let [cur (source-local-rot (:source-node e))]
            (let [delta (m/quat-mul cur (m/quat-conjugate (:source-initial-local-rot e)))
                  blended (m/quat-slerp m/quat-identity delta (:weight kind))]
              [(:dest-node e) (m/quat-mul blended (:dest-initial-local-rot e))]))

          :roll
          (when-let [cur (source-local-rot (:source-node e))]
            (let [delta (m/quat-mul cur (m/quat-conjugate (:source-initial-local-rot e)))
                  axis (m/vec3-normalize-or-zero (:roll-axis kind))]
              (when (>= (m/vec3-length-squared axis) 1e-8)
                (let [twist-angle (twist-angle-about delta axis)
                      twist-rot (m/quat-from-axis-angle axis (* twist-angle (:weight kind)))]
                  [(:dest-node e) (m/quat-mul twist-rot (:dest-initial-local-rot e))]))))

          :aim
          (when-let [src-w (source-world (:source-node e))]
            (when-let [dst-head (dest-head-world (:dest-node e))]
              (let [[_ dst-head-rot dst-head-pos] (m/mat4-to-scale-rotation-translation dst-head)
                    [_ _src-rot src-pos] (m/mat4-to-scale-rotation-translation src-w)
                    to-target (m/vec3-normalize-or-zero (m/vec3- src-pos dst-head-pos))]
                (when (>= (m/vec3-length-squared to-target) 1e-8)
                  (let [current-aim-world (m/vec3-normalize-or-zero (m/quat-rotate-vec3 dst-head-rot (:aim-axis kind)))]
                    (when (>= (m/vec3-length-squared current-aim-world) 1e-8)
                      (let [delta-world (m/quat-from-rotation-arc current-aim-world to-target)
                            blended (m/quat-slerp m/quat-identity delta-world (:weight kind))]
                        [(:dest-node e)
                         (m/quat-mul
                          (m/quat-mul (m/quat-mul (m/quat-conjugate dst-head-rot) blended) dst-head-rot)
                          (:dest-initial-local-rot e))]))))))))))
    (:entries solver))))

(defn count-entries [solver] (count (:entries solver)))

(ns vrm.spring
  "VRM spring bone simulator (VRMC_springBone spec). Per-joint verlet chain:
  gravity + stiffness toward rest pose + drag. Output is per-spring-joint
  local rotation overrides, merged into the caller's pose state before
  palette computation. Restored from `kami-vrm/src/spring.rs`
  (kotoba-lang/kami-engine, deleted PR #82) as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  Reference: <https://github.com/vrm-c/vrm-specification/blob/master/specifications/VRMC_springBone-1.0/README.md>

  A `SpringSimulator` here is a plain map: `{:chains [ChainStatic ...]
  :runtime [[JointRuntime ...] ...] :colliders [ColliderEntry ...]}`. `step`
  is a pure function `simulator, dt, node-world-fn -> [new-simulator
  overrides]` (no mutation; the caller threads the returned simulator to the
  next frame, matching Rust's `&mut self` via explicit state passing)."
  (:require [vrm.math :as m]))

;; ── Build ────────────────────────────────────────

(defn- resolve-collider-shape [shape]
  (case (:type shape)
    :sphere {:type :sphere :offset (:offset shape) :radius (:radius shape)}
    :capsule {:type :capsule :offset (:offset shape) :tail (:tail shape) :radius (:radius shape)}))

(defn- build-chain [doc chain]
  (let [joints (:joints chain)
        n (count joints)
        gltf (:gltf doc)
        statics
        (vec
         (map-indexed
          (fn [i joint]
            (let [node (get (:nodes gltf) (:node joint))
                  initial-local-rot (or (:rotation node) m/quat-identity)
                  next-joint (get joints (inc i))
                  [bone-axis-local bone-length has-tail]
                  (if next-joint
                    (let [next-pos (or (:translation (get (:nodes gltf) (:node next-joint))) (m/vec3-scale m/vec3-y 0.07))
                          len (m/vec3-length next-pos)]
                      (if (> len 1e-6)
                        [(m/vec3-scale next-pos (/ 1.0 len)) len true]
                        [m/vec3-y 0.07 false]))
                    [m/vec3-y 0.07 false])]
              {:node (:node joint)
               :initial-local-rot initial-local-rot
               :bone-axis-local bone-axis-local
               :bone-length bone-length
               :stiffness (max 0.0 (min 1.0 (:stiffness joint)))
               :drag (max 0.0 (min 1.0 (:drag-force joint)))
               :gravity (m/vec3-scale (:gravity-dir joint) (:gravity-power joint))
               :hit-radius (max 0.0 (:hit-radius joint))
               :has-tail has-tail}))
          joints))
        chain-colliders
        (vec (mapcat (fn [group-idx]
                        (:colliders (get (:spring-bone-collider-groups doc) group-idx)))
                      (:collider-groups chain)))]
    {:static {:joints statics :colliders chain-colliders}
     :runtime (vec (repeat n {:prev-tail-world m/vec3-zero :current-tail-world m/vec3-zero :initialized false}))}))

(defn new-simulator
  "Build a simulator from the parsed VrmDocument."
  [doc]
  (let [colliders (mapv (fn [c] {:node (:node c) :shape (resolve-collider-shape (:shape c))})
                         (:spring-bone-colliders doc))
        built (mapv (fn [chain] (build-chain doc chain)) (:spring-bones doc))]
    {:chains (mapv :static built)
     :runtime (mapv :runtime built)
     :colliders colliders}))

;; ── Step ─────────────────────────────────────────

(defn- resolve-colliders [sim chain-st node-world]
  (vec (keep (fn [ci]
               (when-let [entry (get (:colliders sim) ci)]
                 (when-let [world (node-world (:node entry))]
                   [(:shape entry) world])))
             (:colliders chain-st))))

(defn- push-out-of-colliders [next-tail hit-radius resolved-colliders]
  (reduce
   (fn [next-tail [shape world]]
     (case (:type shape)
       :sphere
       (let [c-world (m/mat4-transform-point3 world (:offset shape))
             min-dist (+ hit-radius (:radius shape))
             d (m/vec3- next-tail c-world)
             dl (m/vec3-length d)]
         (if (and (> dl 1e-6) (< dl min-dist))
           (m/vec3+ c-world (m/vec3-scale d (/ min-dist dl)))
           next-tail))
       :capsule
       (let [a (m/mat4-transform-point3 world (:offset shape))
             b (m/mat4-transform-point3 world (:tail shape))
             ab (m/vec3- b a)
             ab-len-sq (m/vec3-length-squared ab)
             t (if (> ab-len-sq 1e-8)
                 (max 0.0 (min 1.0 (/ (m/vec3-dot (m/vec3- next-tail a) ab) ab-len-sq)))
                 0.0)
             closest (m/vec3+ a (m/vec3-scale ab t))
             min-dist (+ hit-radius (:radius shape))
             d (m/vec3- next-tail closest)
             dl (m/vec3-length d)]
         (if (and (> dl 1e-6) (< dl min-dist))
           (m/vec3+ closest (m/vec3-scale d (/ min-dist dl)))
           next-tail))))
   next-tail
   resolved-colliders))

(defn step
  "Advance simulation by `dt` seconds. `node-world` is a fn `node-idx ->
  Mat4` returning the current pose-applied world matrix of the given glTF
  node, BEFORE spring overrides are applied this frame (the previous frame's
  spring output cascades implicitly through this world matrix — one frame of
  latency on intra-chain cascades, matching the Rust original).

  Returns `[new-simulator overrides]` where `overrides` is
  `[[node-idx [x y z w]] ...]`."
  [sim dt node-world]
  (let [dt (max 0.0 (min (/ 1.0 30.0) dt))]
    (reduce
     (fn [[sim overrides] [chain-idx chain-st]]
       (let [resolved-colliders (resolve-colliders sim chain-st node-world)
             statics (:joints chain-st)
             rts (get (:runtime sim) chain-idx)
             [new-rts overrides]
             (reduce
              (fn [[rts overrides] [i js]]
                (if-not (:has-tail js)
                  [rts overrides]
                  (if-let [world-m (node-world (:node js))]
                    (let [[_ world-rot head-world] (m/mat4-to-scale-rotation-translation world-m)
                          rest-dir-world (m/vec3-normalize-or-zero (m/quat-rotate-vec3 world-rot (:bone-axis-local js)))
                          rest-tail-world (m/vec3+ head-world (m/vec3-scale rest-dir-world (:bone-length js)))
                          rt (get rts i)]
                      (if-not (:initialized rt)
                        [(assoc rts i {:prev-tail-world rest-tail-world :current-tail-world rest-tail-world :initialized true})
                         overrides]
                        (let [inertia (m/vec3-scale (m/vec3- (:current-tail-world rt) (:prev-tail-world rt)) (- 1.0 (:drag js)))
                              stiffness-force (m/vec3-scale rest-dir-world (* (:stiffness js) dt))
                              ext (m/vec3-scale (:gravity js) dt)
                              next-tail (m/vec3+ (m/vec3+ (m/vec3+ (:current-tail-world rt) inertia) stiffness-force) ext)
                              delta (m/vec3- next-tail head-world)
                              len (m/vec3-length delta)
                              next-tail (if (> len 1e-6)
                                          (m/vec3+ head-world (m/vec3-scale delta (/ (:bone-length js) len)))
                                          (m/vec3+ head-world (m/vec3-scale rest-dir-world (:bone-length js))))
                              next-tail (push-out-of-colliders next-tail (:hit-radius js) resolved-colliders)
                              delta2 (m/vec3- next-tail head-world)
                              len2 (m/vec3-length delta2)
                              next-tail (if (> len2 1e-6)
                                          (m/vec3+ head-world (m/vec3-scale delta2 (/ (:bone-length js) len2)))
                                          next-tail)
                              new-rt {:prev-tail-world (:current-tail-world rt) :current-tail-world next-tail :initialized true}
                              rts (assoc rts i new-rt)]
                          (let [desired-dir-world (m/vec3-normalize-or-zero (m/vec3- next-tail head-world))]
                            (if (or (< (m/vec3-length-squared rest-dir-world) 1e-8)
                                    (< (m/vec3-length-squared desired-dir-world) 1e-8))
                              [rts overrides]
                              (let [delta-q-world (m/quat-from-rotation-arc rest-dir-world desired-dir-world)
                                    new-local-rot (m/quat-mul
                                                   (m/quat-mul (m/quat-mul (m/quat-conjugate world-rot) delta-q-world) world-rot)
                                                   (:initial-local-rot js))]
                                [rts (conj overrides [(:node js) new-local-rot])]))))))
                    [rts overrides])))
              [rts overrides]
              (map-indexed vector statics))]
         [(assoc-in sim [:runtime chain-idx] new-rts) overrides]))
     [sim []]
     (map-indexed vector (:chains sim)))))

;; ── Telemetry ────────────────────────────────────

(defn chain-count [sim] (count (:chains sim)))
(defn joint-count [sim] (reduce + (map (fn [c] (count (filter :has-tail (:joints c)))) (:chains sim))))
(defn collider-count [sim] (count (:colliders sim)))

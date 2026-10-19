(ns vrm.parse
  "VRM document parser: GLB bytes -> VrmDocument (see `vrm.vrm-types`).
  Restored from `kami-vrm/src/parse.rs` (kotoba-lang/kami-engine, deleted PR
  #82) as part of the clj-wgsl migration (ADR-2607010930,
  com-junkawasaki/root)."
  (:require [vrm.glb :as glb]
            [vrm.json :as json]
            [vrm.gltf-types :as gt]
            [vrm.vrm-types :as vt]
            [vrm.compat :as compat]))

;; ── Helpers ──────────────────────────────────────

(defn parse-f32-3
  "`{:x :y :z}` or `[x y z]` -> `[x y z]`, or nil."
  [v]
  (cond
    (nil? v) nil
    (map? v) [(double (or (:x v) 0.0)) (double (or (:y v) 0.0)) (double (or (:z v) 0.0))]
    (sequential? v) (when (>= (count v) 3) [(double (nth v 0)) (double (nth v 1)) (double (nth v 2))])
    :else nil))

(defn parse-f32-2
  [v]
  (when (and (sequential? v) (>= (count v) 2))
    [(double (nth v 0)) (double (nth v 1))]))

(defn- parse-range-map [v]
  (vt/range-map
   (double (or (:inputMaxValue v) 90.0))
   (double (or (:outputScale v) 10.0))))

;; ── VRM 1.0 parsers ──────────────────────────────

(defn- parse-meta-v1 [vrmc]
  (let [meta (:meta vrmc)]
    (when-not meta (throw (ex-info "missing required extension: VRMC_vrm.meta" {})))
    (vt/vrm-meta
     {:name (or (:name meta) "")
      :version (:version meta)
      :authors (vec (:authors meta))
      :license-url (:licenseUrl meta)
      :allow-redistribution (some-> (:allowRedistribution meta) (= "allow"))
      :thumbnail-image (:thumbnailImage meta)
      :avatar-permission (:avatarPermission meta)
      :commercial-usage (:commercialUsage meta)})))

(defn- parse-humanoid-v1 [vrmc]
  (let [humanoid (:humanoid vrmc)]
    (when-not humanoid (throw (ex-info "missing required extension: VRMC_vrm.humanoid" {})))
    (let [human-bones-obj (:humanBones humanoid)]
      (when-not human-bones-obj (throw (ex-info "missing required extension: VRMC_vrm.humanoid.humanBones" {})))
      (vt/vrm-humanoid
       (keep (fn [[name val]]
               (when-let [bone-name (vt/str->human-bone-name (clojure.core/name name))]
                 (when-let [node (:node val)]
                   (vt/vrm-human-bone bone-name node))))
             human-bones-obj)))))

(defn- node->mesh-index
  "glTF node index -> the mesh index that node references, or nil. VRM 1.0's
  `morphTargetBinds[].node` is a NODE index (a node has its own optional
  `:mesh`), not a mesh index directly -- this differs from VRM 0.x's
  `blendShapeMaster` binds, whose `mesh` field genuinely IS a mesh index per
  its own schema (`vrm.blendshape.bind.schema.json`), so `vrm.compat`'s
  parallel `(:mesh b)` read is correct as-is and untouched by this fix."
  [gltf node-idx]
  (get-in gltf [:nodes node-idx :mesh]))

(defn- parse-single-expression [gltf nm val preset]
  (let [morph-target-binds (keep (fn [b]
                                    ;; Real bug fix: VRM 1.0's morphTargetBind schema is
                                    ;; `{node index weight}` (see vrm-specification/
                                    ;; VRMC_vrm-1.0/expressions.md) -- this used to read
                                    ;; `(:mesh b)`, a field that does not exist in real VRM
                                    ;; 1.0 JSON, so morph-target-binds was silently empty for
                                    ;; every real-world VRM 1.0 file (confirmed against a real
                                    ;; production VRM during this session's /loop maturity
                                    ;; pass; this repo's own test fixtures happened to use the
                                    ;; same wrong key consistently, so they never caught it).
                                    (when-let [node-idx (:node b)]
                                      (when-let [mesh-idx (node->mesh-index gltf node-idx)]
                                        (when (:index b)
                                          (vt/morph-target-bind mesh-idx (:index b) (double (or (:weight b) 1.0)))))))
                                  (:morphTargetBinds val))
        material-color-binds (keep (fn [b]
                                      (when-let [tv (:targetValue b)]
                                        (when (:material b)
                                          (vt/material-color-bind
                                           (:material b) (:type b)
                                           [(double (or (nth tv 0 nil) 0.0))
                                            (double (or (nth tv 1 nil) 0.0))
                                            (double (or (nth tv 2 nil) 0.0))
                                            (double (or (nth tv 3 nil) 1.0))]))))
                                    (:materialColorBinds val))
        texture-transform-binds (keep (fn [b]
                                         (when (:material b)
                                           (vt/texture-transform-bind
                                            (:material b)
                                            (or (parse-f32-2 (:offset b)) [0.0 0.0])
                                            (or (parse-f32-2 (:scale b)) [1.0 1.0]))))
                                       (:textureTransformBinds val))
        parse-override (fn [k] (case (get val k)
                                  "block" :block
                                  "blend" :blend
                                  "none" :none
                                  nil))]
    (vt/vrm-expression
     {:name (clojure.core/name nm)
      :preset preset
      :is-binary (boolean (:isBinary val))
      :morph-target-binds (vec morph-target-binds)
      :material-color-binds (vec material-color-binds)
      :texture-transform-binds (vec texture-transform-binds)
      :override-blink (parse-override :overrideBlink)
      :override-look-at (parse-override :overrideLookAt)
      :override-mouth (parse-override :overrideMouth)})))

(defn- parse-expressions-v1 [gltf vrmc]
  (if-let [expressions (:expressions vrmc)]
    (vec (concat
          (map (fn [[nm val]] (parse-single-expression gltf nm val (vt/str->expression-preset (clojure.core/name nm))))
               (:preset expressions))
          (map (fn [[nm val]] (parse-single-expression gltf nm val nil))
               (:custom expressions))))
    []))

(defn- parse-look-at-v1 [vrmc]
  (when-let [la (:lookAt vrmc)]
    (vt/vrm-look-at
     {:look-at-type (if (= (:type la) "bone") :bone :expression)
      :offset-from-head-bone (or (parse-f32-3 (:offsetFromHeadBone la)) [0.0 0.0 0.0])
      :range-map-horizontal-inner (parse-range-map (:rangeMapHorizontalInner la))
      :range-map-horizontal-outer (parse-range-map (:rangeMapHorizontalOuter la))
      :range-map-vertical-down (parse-range-map (:rangeMapVerticalDown la))
      :range-map-vertical-up (parse-range-map (:rangeMapVerticalUp la))})))

(defn- parse-first-person-v1 [vrmc]
  (when-let [fp (:firstPerson vrmc)]
    (when-let [annotations (:meshAnnotations fp)]
      (vt/vrm-first-person
       (keep (fn [a]
               (when (:node a)
                 (vt/mesh-annotation
                  (:node a)
                  (case (:type a)
                    "both" :both
                    "thirdPersonOnly" :third-person-only
                    "firstPersonOnly" :first-person-only
                    :auto))))
             annotations)))))

(defn- parse-spring-bone-v1 [sb-ext]
  (let [colliders (keep (fn [c]
                           (when (:node c)
                             (let [shape (:shape c)]
                               (cond
                                 (:sphere shape)
                                 (vt/vrm-collider (:node c)
                                                   (vt/collider-shape-sphere
                                                    (or (parse-f32-3 (:offset (:sphere shape))) [0.0 0.0 0.0])
                                                    (double (:radius (:sphere shape)))))
                                 (:capsule shape)
                                 (vt/vrm-collider (:node c)
                                                   (vt/collider-shape-capsule
                                                    (or (parse-f32-3 (:offset (:capsule shape))) [0.0 0.0 0.0])
                                                    (or (parse-f32-3 (:tail (:capsule shape))) [0.0 0.0 0.0])
                                                    (double (:radius (:capsule shape)))))
                                 :else nil))))
                         (:colliders sb-ext))
        collider-groups (mapv (fn [g]
                                 (vt/vrm-collider-group (:name g) (vec (:colliders g))))
                               (:colliderGroups sb-ext))
        springs (mapv (fn [s]
                         (vt/vrm-spring-bone-chain
                          {:name (:name s)
                           :joints (keep (fn [j]
                                           (when (:node j)
                                             (vt/spring-joint
                                              {:node (:node j)
                                               :hit-radius (double (or (:hitRadius j) 0.0))
                                               :stiffness (double (or (:stiffness j) 1.0))
                                               :gravity-power (double (or (:gravityPower j) 0.0))
                                               :gravity-dir (or (parse-f32-3 (:gravityDir j)) [0.0 -1.0 0.0])
                                               :drag-force (double (or (:dragForce j) 0.4))})))
                                         (:joints s))
                           :collider-groups (vec (:colliderGroups s))
                           :center (:center s)}))
                       (:springs sb-ext))]
    [springs (vec colliders) collider-groups]))

(defn- parse-mtoon-materials [gltf]
  (vec
   (keep-indexed
    (fn [i mat]
      (when-let [ext (get-in mat [:extensions :VRMC_materials_mtoon])]
        (vt/vrm-mtoon-material
         {:material-index i
          :shade-color-factor (or (parse-f32-3 (:shadeColorFactor ext)) [0.0 0.0 0.0])
          :shade-multiply-texture (get-in ext [:shadeMultiplyTexture :index])
          :shading-shift-factor (double (or (:shadingShiftFactor ext) 0.0))
          :shading-toony-factor (double (or (:shadingToonyFactor ext) 0.9))
          :gi-equalization-factor (double (or (:giEqualizationFactor ext) 0.9))
          :rim-color-factor (or (parse-f32-3 (:parametricRimColorFactor ext)) [0.0 0.0 0.0])
          :rim-lighting-mix-factor (double (or (:rimLightingMixFactor ext) 1.0))
          :rim-fresnel-power-factor (double (or (:parametricRimFresnelPowerFactor ext) 5.0))
          :rim-lift-factor (double (or (:parametricRimLiftFactor ext) 0.0))
          :rim-multiply-texture (get-in ext [:rimMultiplyTexture :index])
          :outline-width-mode (case (:outlineWidthMode ext)
                                 "worldCoordinates" :world-coordinates
                                 "screenCoordinates" :screen-coordinates
                                 :none)
          :outline-width-factor (double (or (:outlineWidthFactor ext) 0.0))
          :outline-color-factor (or (parse-f32-3 (:outlineColorFactor ext)) [0.0 0.0 0.0])
          :outline-lighting-mix-factor (double (or (:outlineLightingMixFactor ext) 1.0))
          :matcap-texture (get-in ext [:matcapTexture :index])
          :parametric-rim-color-factor (or (parse-f32-3 (:parametricRimColorFactor ext)) [0.0 0.0 0.0])
          :uv-animation-scroll-x (double (or (:uvAnimationScrollXSpeedFactor ext) 0.0))
          :uv-animation-scroll-y (double (or (:uvAnimationScrollYSpeedFactor ext) 0.0))
          :uv-animation-rotation (double (or (:uvAnimationRotationSpeedFactor ext) 0.0))
          :render-queue-offset (long (or (:renderQueueOffsetNumber ext) 0))
          :transparent-with-z-write (boolean (:transparentWithZWrite ext))})))
    (:materials gltf))))

(defn- parse-node-constraints [gltf]
  (vec
   (keep-indexed
    (fn [i node]
      (when-let [ext (get-in node [:extensions :VRMC_node_constraint])]
        (when-let [constraint (:constraint ext)]
          (let [ct (cond
                     (:aim constraint)
                     (vt/constraint-aim (get-in constraint [:aim :source])
                                         (or (parse-f32-3 (get-in constraint [:aim :aimAxis])) [0.0 0.0 1.0])
                                         (double (or (get-in constraint [:aim :weight]) 1.0)))
                     (:rotation constraint)
                     (vt/constraint-rotation (get-in constraint [:rotation :source])
                                              (double (or (get-in constraint [:rotation :weight]) 1.0)))
                     (:roll constraint)
                     (vt/constraint-roll (get-in constraint [:roll :source])
                                          (or (parse-f32-3 (get-in constraint [:roll :rollAxis])) [0.0 1.0 0.0])
                                          (double (or (get-in constraint [:roll :weight]) 1.0)))
                     :else nil)]
            (when ct (vt/vrm-node-constraint i ct))))))
    (:nodes gltf))))

(defn- parse-vrm-1-0 [gltf bin]
  (let [root-ext (:extensions gltf)]
    (when-not root-ext (throw (ex-info "missing required extension: VRMC_vrm" {})))
    (let [vrmc-vrm (:VRMC_vrm root-ext)]
      (when-not vrmc-vrm (throw (ex-info "missing required extension: VRMC_vrm" {})))
      (let [meta (parse-meta-v1 vrmc-vrm)
            humanoid (parse-humanoid-v1 vrmc-vrm)
            expressions (parse-expressions-v1 gltf vrmc-vrm)
            look-at (parse-look-at-v1 vrmc-vrm)
            first-person (parse-first-person-v1 vrmc-vrm)
            [spring-bones spring-bone-colliders spring-bone-collider-groups]
            (if-let [sb-ext (:VRMC_springBone root-ext)]
              (parse-spring-bone-v1 sb-ext)
              [[] [] []])
            mtoon-materials (parse-mtoon-materials gltf)
            node-constraints (parse-node-constraints gltf)]
        (vt/vrm-document
         {:gltf gltf :bin bin :version :v1-0 :meta meta :humanoid humanoid
          :expressions expressions :spring-bones spring-bones
          :spring-bone-colliders spring-bone-colliders
          :spring-bone-collider-groups spring-bone-collider-groups
          :mtoon-materials mtoon-materials :look-at look-at :first-person first-person
          :node-constraints node-constraints})))))

(defn- parse-vrm-0x [gltf bin]
  (let [vrm-ext (get-in gltf [:extensions :VRM])]
    (when-not vrm-ext (throw (ex-info "missing required extension: VRM" {})))
    (let [{:keys [meta humanoid expressions spring-bones colliders collider-groups
                  mtoon-materials look-at first-person]}
          (compat/convert-v0x-to-v1 vrm-ext gltf)]
      (vt/vrm-document
       {:gltf gltf :bin bin :version :v0x :meta meta :humanoid humanoid
        :expressions expressions :spring-bones spring-bones
        :spring-bone-colliders colliders :spring-bone-collider-groups collider-groups
        :mtoon-materials mtoon-materials :look-at look-at :first-person first-person
        :node-constraints []}))))

(defn parse-vrm
  "Parse VRM GLB bytes (byte-int vector) into a VrmDocument. Detects VRM
  version automatically: `extensionsUsed` contains `VRMC_vrm` -> VRM 1.0;
  root `extensions` has a `VRM` key -> VRM 0.x (converted to 1.0 types)."
  [data]
  (let [chunks (glb/parse-glb data)
        gltf (gt/gltf-document (json/parse (glb/byte-seq->string (:json chunks))))
        bin (vec (or (:bin chunks) []))
        is-v1 (some #(= % "VRMC_vrm") (:extensionsUsed gltf))
        is-v0 (and (not is-v1) (some? (get-in gltf [:extensions :VRM])))]
    (cond
      is-v1 (parse-vrm-1-0 gltf bin)
      is-v0 (parse-vrm-0x gltf bin)
      :else (throw (ex-info "missing required extension: VRMC_vrm or VRM" {})))))

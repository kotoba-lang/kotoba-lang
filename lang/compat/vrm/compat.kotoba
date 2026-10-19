(ns vrm.compat
  "VRM 0.x -> 1.0 conversion layer. Restored from `kami-vrm/src/compat.rs`
  (kotoba-lang/kami-engine, deleted PR #82) as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root)."
  (:require [clojure.string]
            [vrm.vrm-types :as vt]))

(defn- parse-f32-3-obj
  "0.x `{x y z}` object -> `[x y z]`, or `default` if absent/incomplete."
  [v default]
  (if (and v (:x v) (:y v) (:z v))
    [(double (:x v)) (double (:y v)) (double (:z v))]
    default))

(defn- convert-meta-v0x [vrm-ext]
  (let [meta (:meta vrm-ext)]
    (when-not meta (throw (ex-info "missing required extension: VRM.meta" {})))
    (vt/vrm-meta
     {:name (or (:title meta) "")
      :version (:version meta)
      :authors (if-let [a (:author meta)] [a] [])
      :license-url (:otherLicenseUrl meta)
      :allow-redistribution (some-> (:allowedUserName meta) (= "Everyone"))
      :thumbnail-image (:texture meta)
      :avatar-permission (:allowedUserName meta)
      :commercial-usage (:commercialUssageName meta)})))

(defn- convert-humanoid-v0x [vrm-ext]
  (let [humanoid (:humanoid vrm-ext)]
    (when-not humanoid (throw (ex-info "missing required extension: VRM.humanoid" {})))
    (let [bones (:humanBones humanoid)]
      (when-not bones (throw (ex-info "missing required extension: VRM.humanoid.humanBones" {})))
      (vt/vrm-humanoid
       (keep (fn [b]
               (when-let [name (:bone b)]
                 (when-let [node (:node b)]
                   (when-let [bone-name (vt/str->human-bone-name name)]
                     (vt/vrm-human-bone bone-name node)))))
             bones)))))

(def ^:private preset-name->kw
  {"joy" :happy "happy" :happy
   "angry" :angry
   "sorrow" :sad "sad" :sad
   "fun" :relaxed "relaxed" :relaxed
   "surprised" :surprised
   "a" :aa "aa" :aa
   "i" :ih "ih" :ih
   "u" :ou "ou" :ou
   "e" :ee "ee" :ee
   "o" :oh "oh" :oh
   "blink" :blink
   "blink_l" :blink-left "blinkleft" :blink-left
   "blink_r" :blink-right "blinkright" :blink-right
   "lookup" :look-up
   "lookdown" :look-down
   "lookleft" :look-left
   "lookright" :look-right
   "neutral" :neutral})

(defn- convert-expressions-v0x [vrm-ext]
  (let [groups (get-in vrm-ext [:blendShapeMaster :blendShapeGroups])]
    (if-not groups
      []
      (vec
       (keep
        (fn [g]
          (when-let [name (:name g)]
            (let [preset-name (clojure.string/lower-case (or (:presetName g) ""))
                  preset (get preset-name->kw preset-name)
                  morph-target-binds (keep (fn [b]
                                              (when (and (:mesh b) (:index b))
                                                (vt/morph-target-bind
                                                 (:mesh b) (:index b)
                                                 (/ (double (or (:weight b) 100.0)) 100.0))))
                                            (:binds g))
                  material-color-binds (keep (fn [b]
                                                (when-let [tv (:targetValue b)]
                                                  (when (:propertyName b)
                                                    (vt/material-color-bind
                                                     0 (:propertyName b)
                                                     [(double (or (nth tv 0 nil) 0.0))
                                                      (double (or (nth tv 1 nil) 0.0))
                                                      (double (or (nth tv 2 nil) 0.0))
                                                      (double (or (nth tv 3 nil) 1.0))]))))
                                              (:materialValues g))]
              (vt/vrm-expression
               {:name name
                :preset preset
                :is-binary (boolean (:isBinary g))
                :morph-target-binds (vec morph-target-binds)
                :material-color-binds (vec material-color-binds)
                :texture-transform-binds []
                :override-blink nil
                :override-look-at nil
                :override-mouth nil}))))
        groups)))))

(defn- convert-spring-bones-v0x [vrm-ext]
  (let [sa (:secondaryAnimation vrm-ext)]
    (if-not sa
      [[] [] []]
      (let [all-colliders (atom [])
            collider-groups
            (mapv
             (fn [g]
               (let [node (or (:node g) 0)
                     colliders-in-group
                     (vec
                      (keep
                       (fn [c]
                         (when-let [offset (:offset c)]
                           (let [ox (double (or (:x offset) 0.0))
                                 oy (double (or (:y offset) 0.0))
                                 oz (double (or (:z offset) 0.0))
                                 radius (double (or (:radius c) 0.0))
                                 idx (count @all-colliders)]
                             (swap! all-colliders conj
                                    (vt/vrm-collider node (vt/collider-shape-sphere [ox oy oz] radius)))
                             idx)))
                       (:colliders g)))]
                 (vt/vrm-collider-group nil colliders-in-group)))
             (:colliderGroups sa))
            springs
            (vec
             (keep
              (fn [bg]
                (let [stiffness (double (or (:stiffiness bg) 1.0))
                      gravity-power (double (or (:gravityPower bg) 0.0))
                      gravity-dir (parse-f32-3-obj (:gravityDir bg) [0.0 -1.0 0.0])
                      drag-force (double (or (:dragForce bg) 0.4))
                      hit-radius (double (or (:hitRadius bg) 0.02))
                      bones (:bones bg)]
                  (when bones
                    (let [joints (keep (fn [b]
                                          (when b
                                            (vt/spring-joint
                                             {:node b :hit-radius hit-radius :stiffness stiffness
                                              :gravity-power gravity-power :gravity-dir gravity-dir
                                              :drag-force drag-force})))
                                        bones)]
                      (vt/vrm-spring-bone-chain
                       {:name (:comment bg)
                        :joints (vec joints)
                        :collider-groups (vec (:colliderGroups bg))
                        :center (:center bg)})))))
              (:boneGroups sa)))]
        [springs @all-colliders collider-groups]))))

(defn- convert-mtoon-v0x [vrm-ext]
  (let [props (:materialProperties vrm-ext)]
    (if-not props
      []
      (vec
       (keep-indexed
        (fn [i p]
          (let [shader (:shader p)]
            (when (and shader (clojure.string/includes? shader "MToon"))
              (let [fv (fn [k] (double (or (get-in p [:floatProperties (keyword k)]) 0.0)))
                    cv (fn [k]
                         (if-let [a (get-in p [:vectorProperties (keyword k)])]
                           [(double (or (nth a 0 nil) 0.0)) (double (or (nth a 1 nil) 0.0)) (double (or (nth a 2 nil) 0.0))]
                           [0.0 0.0 0.0]))]
                (vt/vrm-mtoon-material
                 {:material-index i
                  :shade-color-factor (cv "_ShadeColor")
                  :shade-multiply-texture nil
                  :shading-shift-factor (fv "_ShadeShift")
                  :shading-toony-factor (fv "_ShadeToony")
                  :gi-equalization-factor (fv "_IndirectLightIntensity")
                  :rim-color-factor (cv "_RimColor")
                  :rim-lighting-mix-factor (fv "_RimLightingMix")
                  :rim-fresnel-power-factor (fv "_RimFresnelPower")
                  :rim-lift-factor (fv "_RimLift")
                  :rim-multiply-texture nil
                  :outline-width-mode (case (long (fv "_OutlineWidthMode"))
                                        1 :world-coordinates
                                        2 :screen-coordinates
                                        :none)
                  :outline-width-factor (fv "_OutlineWidth")
                  :outline-color-factor (cv "_OutlineColor")
                  :outline-lighting-mix-factor (fv "_OutlineLightingMix")
                  :matcap-texture nil
                  :parametric-rim-color-factor (cv "_RimColor")
                  :uv-animation-scroll-x (fv "_UvAnimScrollX")
                  :uv-animation-scroll-y (fv "_UvAnimScrollY")
                  :uv-animation-rotation (fv "_UvAnimRotation")
                  :render-queue-offset (long (or (:renderQueue p) 0))
                  :transparent-with-z-write (> (fv "_ZWrite") 0.5)})))))
        props)))))

(defn- convert-range-map-v0x [v]
  (if-not v
    (vt/range-map 90.0 10.0)
    (vt/range-map (double (or (:xRange v) 90.0)) (double (or (:yRange v) 10.0)))))

(defn- convert-look-at-v0x [vrm-ext]
  (when-let [fp (:firstPerson vrm-ext)]
    (when-let [lat (:lookAtTypeName fp)]
      (vt/vrm-look-at
       {:look-at-type (if (= lat "Bone") :bone :expression)
        :offset-from-head-bone (parse-f32-3-obj (:firstPersonBoneOffset fp) [0.0 0.0 0.0])
        :range-map-horizontal-inner (convert-range-map-v0x (:lookAtHorizontalInner fp))
        :range-map-horizontal-outer (convert-range-map-v0x (:lookAtHorizontalOuter fp))
        :range-map-vertical-down (convert-range-map-v0x (:lookAtVerticalDown fp))
        :range-map-vertical-up (convert-range-map-v0x (:lookAtVerticalUp fp))}))))

(defn- convert-first-person-v0x [vrm-ext]
  (when-let [fp (:firstPerson vrm-ext)]
    (when-let [annotations (:meshAnnotations fp)]
      (vt/vrm-first-person
       (keep (fn [a]
               (when (:mesh a)
                 (vt/mesh-annotation
                  (:mesh a)
                  (case (:firstPersonFlag a)
                    "Both" :both
                    "ThirdPersonOnly" :third-person-only
                    "FirstPersonOnly" :first-person-only
                    :auto))))
             annotations)))))

(defn convert-v0x-to-v1
  "Convert VRM 0.x extension JSON (already-parsed EDN) to VRM 1.0 types.
  Key differences handled: 0.x single `VRM` extension -> 1.0 separate
  `VRMC_*` extensions; 0.x `blendShapeMaster.blendShapeGroups` -> 1.0
  expressions; 0.x `secondaryAnimation` -> 1.0 `VRMC_springBone`; 0.x
  `materialProperties` -> 1.0 `VRMC_materials_mtoon`."
  [vrm-ext _gltf]
  (let [meta (convert-meta-v0x vrm-ext)
        humanoid (convert-humanoid-v0x vrm-ext)
        expressions (convert-expressions-v0x vrm-ext)
        [spring-bones colliders collider-groups] (convert-spring-bones-v0x vrm-ext)
        mtoon-materials (convert-mtoon-v0x vrm-ext)
        look-at (convert-look-at-v0x vrm-ext)
        first-person (convert-first-person-v0x vrm-ext)]
    {:meta meta :humanoid humanoid :expressions expressions :spring-bones spring-bones
     :colliders colliders :collider-groups collider-groups :mtoon-materials mtoon-materials
     :look-at look-at :first-person first-person}))

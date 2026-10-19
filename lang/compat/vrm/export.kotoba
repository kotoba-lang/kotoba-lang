(ns vrm.export
  "VRM GLB export: VrmDocument -> GLB bytes. Restored from
  `kami-vrm/src/export.rs` (kotoba-lang/kami-engine, deleted PR #82) as part
  of the clj-wgsl migration (ADR-2607010930, com-junkawasaki/root)."
  (:require [vrm.glb :as glb]
            [vrm.json :as json]
            [vrm.vrm-types :as vt]))

(defn- build-expression [expr]
  (cond-> {}
    (:is-binary expr) (assoc :isBinary true)
    (seq (:morph-target-binds expr))
    (assoc :morphTargetBinds
           (mapv (fn [b] {:mesh (:mesh-index b) :index (:morph-index b) :weight (:weight b)})
                 (:morph-target-binds expr)))
    (seq (:material-color-binds expr))
    (assoc :materialColorBinds
           (mapv (fn [b] {:material (:material-index b) :type (:property b) :targetValue (:target-value b)})
                 (:material-color-binds expr)))
    (seq (:texture-transform-binds expr))
    (assoc :textureTransformBinds
           (mapv (fn [b] {:material (:material-index b) :offset (:offset b) :scale (:scale b)})
                 (:texture-transform-binds expr)))
    (:override-blink expr) (assoc :overrideBlink (name (:override-blink expr)))
    (:override-look-at expr) (assoc :overrideLookAt (name (:override-look-at expr)))
    (:override-mouth expr) (assoc :overrideMouth (name (:override-mouth expr)))))

(defn- build-vrmc-vrm [doc]
  (let [meta (:meta doc)
        meta-obj (cond-> {:name (:name meta)}
                   (:version meta) (assoc :version (:version meta))
                   (seq (:authors meta)) (assoc :authors (vec (:authors meta)))
                   (:license-url meta) (assoc :licenseUrl (:license-url meta))
                   (:avatar-permission meta) (assoc :avatarPermission (:avatar-permission meta))
                   (:commercial-usage meta) (assoc :commercialUsage (:commercial-usage meta)))
        human-bones (into {} (map (fn [hb] [(vt/human-bone-name->str (:bone hb)) {:node (:node hb)}]))
                          (:human-bones (:humanoid doc)))
        exprs (:expressions doc)
        preset-map (into {} (keep (fn [e] (when (:preset e) [(:name e) (build-expression e)]))) exprs)
        custom-map (into {} (keep (fn [e] (when-not (:preset e) [(:name e) (build-expression e)]))) exprs)
        la (:look-at doc)
        fp (:first-person doc)]
    (cond-> {:specVersion "1.0"
             :meta meta-obj
             :humanoid {:humanBones human-bones}}
      (seq exprs)
      (assoc :expressions
             (cond-> {}
               (seq preset-map) (assoc :preset preset-map)
               (seq custom-map) (assoc :custom custom-map)))
      la
      (assoc :lookAt
             {:type (case (:look-at-type la) :bone "bone" "expression")
              :offsetFromHeadBone (:offset-from-head-bone la)
              :rangeMapHorizontalInner {:inputMaxValue (:input-max-value (:range-map-horizontal-inner la))
                                         :outputScale (:output-scale (:range-map-horizontal-inner la))}
              :rangeMapHorizontalOuter {:inputMaxValue (:input-max-value (:range-map-horizontal-outer la))
                                         :outputScale (:output-scale (:range-map-horizontal-outer la))}
              :rangeMapVerticalDown {:inputMaxValue (:input-max-value (:range-map-vertical-down la))
                                      :outputScale (:output-scale (:range-map-vertical-down la))}
              :rangeMapVerticalUp {:inputMaxValue (:input-max-value (:range-map-vertical-up la))
                                    :outputScale (:output-scale (:range-map-vertical-up la))}})
      fp
      (assoc :firstPerson
             {:meshAnnotations
              (mapv (fn [a] {:node (:node a)
                             :type (case (:annotation-type a)
                                     :both "both"
                                     :third-person-only "thirdPersonOnly"
                                     :first-person-only "firstPersonOnly"
                                     "auto")})
                    (:mesh-annotations fp))}))))

(defn- build-vrmc-spring-bone [doc]
  (cond-> {}
    (seq (:spring-bone-colliders doc))
    (assoc :colliders
           (mapv (fn [c]
                   (let [shape (:shape c)]
                     {:node (:node c)
                      :shape (case (:type shape)
                               :sphere {:sphere {:offset (:offset shape) :radius (:radius shape)}}
                               :capsule {:capsule {:offset (:offset shape) :tail (:tail shape) :radius (:radius shape)}})}))
                 (:spring-bone-colliders doc)))
    (seq (:spring-bone-collider-groups doc))
    (assoc :colliderGroups
           (mapv (fn [g] (cond-> {:colliders (vec (:colliders g))}
                           (:name g) (assoc :name (:name g))))
                 (:spring-bone-collider-groups doc)))
    (seq (:spring-bones doc))
    (assoc :springs
           (mapv (fn [chain]
                   (cond-> {:joints
                            (mapv (fn [j] {:node (:node j) :hitRadius (:hit-radius j) :stiffness (:stiffness j)
                                           :gravityPower (:gravity-power j)
                                           :gravityDir {:x (nth (:gravity-dir j) 0) :y (nth (:gravity-dir j) 1) :z (nth (:gravity-dir j) 2)}
                                           :dragForce (:drag-force j)})
                                  (:joints chain))}
                     (:name chain) (assoc :name (:name chain))
                     (seq (:collider-groups chain)) (assoc :colliderGroups (vec (:collider-groups chain)))
                     (:center chain) (assoc :center (:center chain))))
                 (:spring-bones doc)))))

(defn- build-mtoon-extension [mtoon]
  (cond-> {:specVersion "1.0"
           :shadeColorFactor (:shade-color-factor mtoon)
           :shadingShiftFactor (:shading-shift-factor mtoon)
           :shadingToonyFactor (:shading-toony-factor mtoon)
           :giEqualizationFactor (:gi-equalization-factor mtoon)
           :parametricRimColorFactor (:parametric-rim-color-factor mtoon)
           :rimLightingMixFactor (:rim-lighting-mix-factor mtoon)
           :parametricRimFresnelPowerFactor (:rim-fresnel-power-factor mtoon)
           :parametricRimLiftFactor (:rim-lift-factor mtoon)
           :outlineWidthMode (case (:outline-width-mode mtoon)
                               :world-coordinates "worldCoordinates"
                               :screen-coordinates "screenCoordinates"
                               "none")
           :outlineWidthFactor (:outline-width-factor mtoon)
           :outlineColorFactor (:outline-color-factor mtoon)
           :outlineLightingMixFactor (:outline-lighting-mix-factor mtoon)
           :renderQueueOffsetNumber (:render-queue-offset mtoon)
           :transparentWithZWrite (boolean (:transparent-with-z-write mtoon))}
    (:shade-multiply-texture mtoon) (assoc :shadeMultiplyTexture {:index (:shade-multiply-texture mtoon)})
    (:rim-multiply-texture mtoon) (assoc :rimMultiplyTexture {:index (:rim-multiply-texture mtoon)})
    (:matcap-texture mtoon) (assoc :matcapTexture {:index (:matcap-texture mtoon)})))

(defn- build-node-constraint [nc]
  (let [c (:constraint nc)
        constraint (case (:type c)
                     :aim {:aim {:source (:source c) :aimAxis (:aim-axis c) :weight (:weight c)}}
                     :rotation {:rotation {:source (:source c) :weight (:weight c)}}
                     :roll {:roll {:source (:source c) :rollAxis (:roll-axis c) :weight (:weight c)}})]
    {:constraint constraint}))

(defn export-glb
  "Export a VrmDocument to GLB bytes (byte-int vector) with VRM 1.0
  extensions."
  [doc]
  (let [gltf (:gltf doc)
        required-exts ["VRMC_vrm" "VRMC_springBone" "VRMC_materials_mtoon"]
        gltf (update gltf :extensionsUsed
                     (fn [exts] (reduce (fn [exts ext] (if (some #{ext} exts) exts (conj (vec exts) ext)))
                                        (vec exts) required-exts)))
        root-ext (cond-> {:VRMC_vrm (build-vrmc-vrm doc)}
                   (or (seq (:spring-bones doc)) (seq (:spring-bone-colliders doc)))
                   (assoc :VRMC_springBone (build-vrmc-spring-bone doc)))
        gltf (assoc gltf :extensions root-ext)
        gltf (reduce (fn [gltf mtoon]
                       (update-in gltf [:materials (:material-index mtoon)]
                                  (fn [mat]
                                    (assoc-in mat [:extensions :VRMC_materials_mtoon] (build-mtoon-extension mtoon)))))
                     gltf
                     (:mtoon-materials doc))
        gltf (reduce (fn [gltf nc]
                       (update-in gltf [:nodes (:node nc)]
                                  (fn [node]
                                    (assoc-in node [:extensions :VRMC_node_constraint] (build-node-constraint nc)))))
                     gltf
                     (:node-constraints doc))
        json-str (json/->json gltf)]
    (glb/write-glb (glb/string->byte-seq json-str) (:bin doc))))

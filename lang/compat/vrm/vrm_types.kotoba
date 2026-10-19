(ns vrm.vrm-types
  "VRM extension data types (VRM 1.0 primary, 0.x via `vrm.compat`). Restored
  from `kami-vrm/src/vrm_types.rs` (kotoba-lang/kami-engine, deleted PR #82
  'Remove Rust workspace from kami-engine') as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  Every Rust struct becomes a plain CLJC map with kebab-case keyword keys;
  every Rust enum becomes a CLJC keyword (payload-carrying enums, e.g.
  `ColliderShape`/`ConstraintType`, become `{:type <kw> ...fields}` maps).
  `VrmDocument` shape:
  `{:gltf GltfDocument :bin [byte-ints] :version (:v0x|:v1-0) :meta VrmMeta
    :humanoid VrmHumanoid :expressions [VrmExpression] :spring-bones
    [VrmSpringBoneChain] :spring-bone-colliders [VrmCollider]
    :spring-bone-collider-groups [VrmColliderGroup] :mtoon-materials
    [VrmMtoonMaterial] :look-at (nilable VrmLookAt) :first-person (nilable
    VrmFirstPerson) :node-constraints [VrmNodeConstraint]}`.")

;; ── Humanoid bone names ──────────────────────────
;; `[keyword camelCase-wire-string]` pairs in VRM 1.0 spec order (55 bones).

(def human-bone-table
  [[:hips "hips"] [:spine "spine"] [:chest "chest"] [:upper-chest "upperChest"]
   [:neck "neck"] [:head "head"]
   [:left-eye "leftEye"] [:right-eye "rightEye"] [:jaw "jaw"]
   [:left-shoulder "leftShoulder"] [:left-upper-arm "leftUpperArm"]
   [:left-lower-arm "leftLowerArm"] [:left-hand "leftHand"]
   [:right-shoulder "rightShoulder"] [:right-upper-arm "rightUpperArm"]
   [:right-lower-arm "rightLowerArm"] [:right-hand "rightHand"]
   [:left-upper-leg "leftUpperLeg"] [:left-lower-leg "leftLowerLeg"]
   [:left-foot "leftFoot"] [:left-toes "leftToes"]
   [:right-upper-leg "rightUpperLeg"] [:right-lower-leg "rightLowerLeg"]
   [:right-foot "rightFoot"] [:right-toes "rightToes"]
   [:left-thumb-metacarpal "leftThumbMetacarpal"] [:left-thumb-proximal "leftThumbProximal"]
   [:left-thumb-distal "leftThumbDistal"]
   [:left-index-proximal "leftIndexProximal"] [:left-index-intermediate "leftIndexIntermediate"]
   [:left-index-distal "leftIndexDistal"]
   [:left-middle-proximal "leftMiddleProximal"] [:left-middle-intermediate "leftMiddleIntermediate"]
   [:left-middle-distal "leftMiddleDistal"]
   [:left-ring-proximal "leftRingProximal"] [:left-ring-intermediate "leftRingIntermediate"]
   [:left-ring-distal "leftRingDistal"]
   [:left-little-proximal "leftLittleProximal"] [:left-little-intermediate "leftLittleIntermediate"]
   [:left-little-distal "leftLittleDistal"]
   [:right-thumb-metacarpal "rightThumbMetacarpal"] [:right-thumb-proximal "rightThumbProximal"]
   [:right-thumb-distal "rightThumbDistal"]
   [:right-index-proximal "rightIndexProximal"] [:right-index-intermediate "rightIndexIntermediate"]
   [:right-index-distal "rightIndexDistal"]
   [:right-middle-proximal "rightMiddleProximal"] [:right-middle-intermediate "rightMiddleIntermediate"]
   [:right-middle-distal "rightMiddleDistal"]
   [:right-ring-proximal "rightRingProximal"] [:right-ring-intermediate "rightRingIntermediate"]
   [:right-ring-distal "rightRingDistal"]
   [:right-little-proximal "rightLittleProximal"] [:right-little-intermediate "rightLittleIntermediate"]
   [:right-little-distal "rightLittleDistal"]])

(def human-bone-names
  "All 55 bone keywords in specification order (`HumanBoneName::ALL`)."
  (mapv first human-bone-table))

(def ^:private human-bone-kw->str (into {} human-bone-table))
(def ^:private human-bone-str->kw (into {} (map (fn [[k s]] [s k])) human-bone-table))

(defn human-bone-name->str
  "Convert a bone keyword to its camelCase VRM 1.0 wire string."
  [kw]
  (get human-bone-kw->str kw))

(defn str->human-bone-name
  "Parse a camelCase string (VRM 1.0 spec naming) into a bone keyword, or nil."
  [s]
  (get human-bone-str->kw s))

;; ── Expressions ──────────────────────────────────

(def expression-preset-table
  [[:happy "happy"] [:angry "angry"] [:sad "sad"] [:relaxed "relaxed"] [:surprised "surprised"]
   [:aa "aa"] [:ih "ih"] [:ou "ou"] [:ee "ee"] [:oh "oh"]
   [:blink "blink"] [:blink-left "blinkLeft"] [:blink-right "blinkRight"]
   [:look-up "lookUp"] [:look-down "lookDown"] [:look-left "lookLeft"] [:look-right "lookRight"]
   [:neutral "neutral"]])

(def ^:private expression-preset-str->kw (into {} (map (fn [[k s]] [s k])) expression-preset-table))
(def ^:private expression-preset-kw->str (into {} expression-preset-table))

(defn str->expression-preset
  "Parse from string (VRM 1.0 preset naming)."
  [s]
  (get expression-preset-str->kw s))

(defn expression-preset->str [kw] (get expression-preset-kw->str kw))

;; ── Constructors (plain maps; mirror the original struct field sets 1:1) ──

(defn vrm-meta
  [{:keys [name version authors license-url allow-redistribution thumbnail-image
           avatar-permission commercial-usage]}]
  {:name (or name "")
   :version version
   :authors (or authors [])
   :license-url license-url
   :allow-redistribution allow-redistribution
   :thumbnail-image thumbnail-image
   :avatar-permission avatar-permission
   :commercial-usage commercial-usage})

(defn vrm-human-bone [bone node] {:bone bone :node node})
(defn vrm-humanoid [human-bones] {:human-bones (vec human-bones)})

(defn morph-target-bind [mesh-index morph-index weight]
  {:mesh-index mesh-index :morph-index morph-index :weight weight})

(defn material-color-bind [material-index property target-value]
  {:material-index material-index :property property :target-value target-value})

(defn texture-transform-bind [material-index offset scale]
  {:material-index material-index :offset offset :scale scale})

(defn vrm-expression
  [{:keys [name preset is-binary morph-target-binds material-color-binds
           texture-transform-binds override-blink override-look-at override-mouth]}]
  {:name name
   :preset preset
   :is-binary (boolean is-binary)
   :morph-target-binds (or morph-target-binds [])
   :material-color-binds (or material-color-binds [])
   :texture-transform-binds (or texture-transform-binds [])
   :override-blink override-blink
   :override-look-at override-look-at
   :override-mouth override-mouth})

(defn spring-joint
  [{:keys [node hit-radius stiffness gravity-power gravity-dir drag-force]}]
  {:node node
   :hit-radius (or hit-radius 0.0)
   :stiffness (or stiffness 1.0)
   :gravity-power (or gravity-power 0.0)
   :gravity-dir (or gravity-dir [0.0 -1.0 0.0])
   :drag-force (or drag-force 0.4)})

(defn vrm-spring-bone-chain
  [{:keys [name joints collider-groups center]}]
  {:name name :joints (vec joints) :collider-groups (or collider-groups []) :center center})

;; ColliderShape: `{:type :sphere :offset [x y z] :radius r}` or
;; `{:type :capsule :offset [..] :tail [..] :radius r}`.
(defn collider-shape-sphere [offset radius] {:type :sphere :offset offset :radius radius})
(defn collider-shape-capsule [offset tail radius] {:type :capsule :offset offset :tail tail :radius radius})

(defn vrm-collider [node shape] {:node node :shape shape})
(defn vrm-collider-group [name colliders] {:name name :colliders (vec colliders)})

(defn vrm-mtoon-material
  [m]
  (merge
   {:material-index 0
    :shade-color-factor [0.0 0.0 0.0]
    :shade-multiply-texture nil
    :shading-shift-factor 0.0
    :shading-toony-factor 0.9
    :gi-equalization-factor 0.9
    :rim-color-factor [0.0 0.0 0.0]
    :rim-lighting-mix-factor 1.0
    :rim-fresnel-power-factor 5.0
    :rim-lift-factor 0.0
    :rim-multiply-texture nil
    :outline-width-mode :none
    :outline-width-factor 0.0
    :outline-color-factor [0.0 0.0 0.0]
    :outline-lighting-mix-factor 1.0
    :matcap-texture nil
    :parametric-rim-color-factor [0.0 0.0 0.0]
    :uv-animation-scroll-x 0.0
    :uv-animation-scroll-y 0.0
    :uv-animation-rotation 0.0
    :render-queue-offset 0
    :transparent-with-z-write false}
   m))

(defn range-map [input-max-value output-scale] {:input-max-value input-max-value :output-scale output-scale})

(defn vrm-look-at
  [{:keys [look-at-type offset-from-head-bone range-map-horizontal-inner
           range-map-horizontal-outer range-map-vertical-down range-map-vertical-up]}]
  {:look-at-type look-at-type
   :offset-from-head-bone (or offset-from-head-bone [0.0 0.0 0.0])
   :range-map-horizontal-inner range-map-horizontal-inner
   :range-map-horizontal-outer range-map-horizontal-outer
   :range-map-vertical-down range-map-vertical-down
   :range-map-vertical-up range-map-vertical-up})

(defn mesh-annotation [node annotation-type] {:node node :annotation-type annotation-type})
(defn vrm-first-person [mesh-annotations] {:mesh-annotations (vec mesh-annotations)})

;; ConstraintType: `{:type :aim :source i :aim-axis [x y z] :weight w}`
;;               | `{:type :rotation :source i :weight w}`
;;               | `{:type :roll :source i :roll-axis [x y z] :weight w}`
(defn constraint-aim [source aim-axis weight] {:type :aim :source source :aim-axis aim-axis :weight weight})
(defn constraint-rotation [source weight] {:type :rotation :source source :weight weight})
(defn constraint-roll [source roll-axis weight] {:type :roll :source source :roll-axis roll-axis :weight weight})
(defn vrm-node-constraint [node constraint] {:node node :constraint constraint})

(defn vrm-document
  [m]
  (merge
   {:bin []
    :expressions []
    :spring-bones []
    :spring-bone-colliders []
    :spring-bone-collider-groups []
    :mtoon-materials []
    :look-at nil
    :first-person nil
    :node-constraints []}
   m))

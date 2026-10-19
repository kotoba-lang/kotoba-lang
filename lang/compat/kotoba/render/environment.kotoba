(ns kotoba.render.environment
  "Portable image-based-lighting assets shared by browser and native hosts.

   The contract deliberately stores already-convolved irradiance and already-
   prefiltered specular cube levels. Runtime adapters upload these bytes; they
   do not perform an expensive, backend-specific bake during game startup."
  (:require [kotoba.render.texture :as texture]))

(def cube-faces [:+x :-x :+y :-y :+z :-z])

(defn cube-level
  "Validate one RGBA8 cube mip level. `faces` must contain six row-major face
   byte vectors in WebGPU cube order: +X, -X, +Y, -Y, +Z, -Z."
  [size faces]
  (when-not (pos-int? size)
    (throw (ex-info "cube level size must be a positive integer" {:size size})))
  (when-not (= (set cube-faces) (set (keys faces)))
    (throw (ex-info "cube level must contain exactly six faces"
                    {:expected cube-faces :actual (keys faces)})))
  (doseq [[face data] faces]
    (when-not (= (* size size 4) (count data))
      (throw (ex-info "cube face RGBA8 byte count does not match size"
                      {:face face :size size :expected (* size size 4)
                       :actual (count data)}))))
  {:size size :faces (into {} (map (fn [[face data]] [face (vec data)])) faces)})

(defn cube-rgba8
  "Create an ordered-mip RGBA8 cube descriptor. Level zero is required first;
   subsequent sizes must halve down toward 1. `color-space` is explicit."
  [levels color-space]
  (when-not (seq levels)
    (throw (ex-info "cube texture requires at least one level" {})))
  (when-not (#{:srgb :linear} color-space)
    (throw (ex-info "cube color-space must be :srgb or :linear"
                    {:color-space color-space})))
  (doseq [[[a b] level] (map vector (partition 2 1 levels) (range 1 (count levels)))]
    (when-not (= (:size b) (max 1 (quot (:size a) 2)))
      (throw (ex-info "cube mip sizes must form a complete halving chain"
                      {:level level :previous (:size a) :actual (:size b)}))))
  {:schema :kotoba.render/cube-rgba8-v1
   :color-space color-space
   :levels (vec levels)})

(defn pbr-environment
  "Create the split-sum IBL asset contract used by Cook-Torrance GGX:
   diffuse irradiance cube, roughness-prefiltered specular cube, and a linear
   2D BRDF integration LUT. The specular cube must include a full mip chain."
  [{:keys [irradiance prefiltered-specular brdf-lut] :as environment}]
  (doseq [[kind cube] [[:irradiance irradiance]
                       [:prefiltered-specular prefiltered-specular]]]
    (when-not (= :kotoba.render/cube-rgba8-v1 (:schema cube))
      (throw (ex-info "IBL cube has an unsupported schema" {:kind kind :cube cube})))
    (when-not (= :linear (:color-space cube))
      (throw (ex-info "IBL lighting cubes must use linear data" {:kind kind}))))
  (when-not (= :kotoba.render/texture-rgba8-v1 (:schema brdf-lut))
    (throw (ex-info "IBL BRDF LUT has an unsupported schema" {:brdf-lut brdf-lut})))
  (when-not (= :linear (:color-space brdf-lut))
    (throw (ex-info "IBL BRDF LUT must use linear data" {})))
  (let [spec-levels (:levels prefiltered-specular)
        expected (texture/mip-level-count (:size (first spec-levels))
                                          (:size (first spec-levels)))]
    (when-not (= expected (count spec-levels))
      (throw (ex-info "prefiltered specular cube requires a full roughness mip chain"
                      {:expected expected :actual (count spec-levels)}))))
  (assoc (select-keys environment [:irradiance :prefiltered-specular :brdf-lut])
         :schema :kotoba.render/pbr-environment-v1))

(defn- solid-faces [pixel]
  (into {} (for [face cube-faces] [face (vec pixel)])))

(def neutral-pbr-environment
  "Valid 1x1 split-sum resources used only when a scene omits authored IBL.
   Hosts always bind these, keeping the shader layout stable. The BRDF value is
   a conservative neutral scale/bias approximation, not an offline-quality bake."
  (pbr-environment
   {:irradiance (cube-rgba8
                 [(cube-level 1 (solid-faces [96 104 120 255]))] :linear)
    :prefiltered-specular (cube-rgba8
                           [(cube-level 1 (solid-faces [80 88 104 255]))] :linear)
    :brdf-lut (texture/rgba8 1 1 [160 24 0 255] :linear)}))

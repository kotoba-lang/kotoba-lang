(ns kami.shaders
  "Game shaders as data — the kami.wgsl EDN AST for the lit instanced renderer's fragment lighting.
   The lighting model (hemisphere ambient, Lambert, Blinn-Phong spec, Fresnel rim, PCF shadow,
   ACES filmic tonemap, gamma) is authored as data, so it reads and forks like the scene, and ONE
   source generates the WGSL the web (kami.webgpu) runs — and, in time, the native kami-webgpu-rs
   path (parity by source). The struct/bindings/shadow/vertex preamble stays a template in
   kami.webgpu; this is the fragment a designer actually tunes. `.cljc` → browser + bb/JVM."
  (:require [clojure.walk :as walk]
            [kami.wgsl :as w]))

;; lighting inputs come from the G uniform (g.light_a..d pack the tunables) and the VO varying `i`.
(def lit-fs-body
  [[:let :N [:normalize :i.n]]
   [:let :L [:normalize [:- :g.sun-dir.xyz]]]
   [:let :eye [:vec3 :g.sun-dir.w :g.sun-col.w :g.sky.w]]
   [:let :V [:normalize [:- :eye :i.wpos]]]
   [:let :H [:normalize [:+ :L :V]]]
   [:let :ndl [:max [:dot :N :L] 0.0]]
   [:let :metallic [:clamp :i.mat.x 0.0 1.0]]
   [:let :rough [:clamp :i.mat.y 0.04 1.0]]
   [:let :emissive :i.mat.z]
   [:let :amb [:mix :g.light-a.rgb [:* :g.sky.rgb :g.light-a.w] [:+ [:* :N.y 0.5] 0.5]]]
   [:let :shininess [:mix :g.light-c.x :g.light-c.y [:- 1.0 :rough]]]
   [:let :specStr [:mix :g.light-b.x :g.light-b.y :metallic]]
   [:let :specTint [:mix [:vec3 1.0] :i.col :metallic]]
   [:let :spec [:* [:pow [:max [:dot :N :H] 0.0] :shininess] :specStr]]
   [:let :rim [:* [:pow [:- 1.0 [:max [:dot :N :V] 0.0]] :g.light-b.w] :g.light-b.z]]
   [:let :sh [:shadow :i.wpos :ndl]]
   [:var :c [:+ [:* :i.col [:+ :amb [:* :ndl :g.sun-col.rgb :g.light-c.z [:- 1.0 [:* :metallic :g.light-c.w]] :sh]]]
                [:* :specTint :g.sun-col.rgb :spec :sh]
                [:* :g.sky.rgb :rim]
                [:* :i.col :emissive]]]
   ;; Narkowicz ACES filmic fit: preserves highlight colour and rolls off
   ;; overbright emissive/sun contributions more naturally than Reinhard.
   [:set :c [:clamp
             [:/ [:* :c [:+ [:* 2.51 :c] [:vec3 0.03]]]
              [:+ [:* :c [:+ [:* 2.43 :c] [:vec3 0.59]]] [:vec3 0.14]]]
             [:vec3 0.0] [:vec3 1.0]]]
   [:set :c [:pow :c [:vec3 [:/ 1.0 :g.light-d.x]]]] ;; gamma
   [:return [:vec4 :c 1.0]]])

(defn lit-fs
  "The lit instanced renderer's fragment shader, generated from data."
  []
  (apply w/func :fs {:stage :fragment :params [[:i :VO]] :ret [:loc 0 [:vec4 :f32]]} lit-fs-body))

;; ── the full shader as data: uniforms, shadow-map bindings, PCF shadow fn, varyings, vertex ──────

(def G-fields
  [[:vp :mat4] [:sun-dir :vec4] [:sun-col :vec4] [:sky :vec4] [:light-vp :mat4]
   [:light-a :vec4] [:light-b :vec4] [:light-c :vec4] [:light-d :vec4]])

;; 3×3 PCF percentage-closer shadow lookup (clamps outside the light frustum to lit).
(def shadow-fn-body
  [[:let :lc [:* :g.light-vp [:vec4 :wpos 1.0]]]
   [:let :ndc [:/ :lc.xyz :lc.w]]
   [:let :uv [:vec2 [:+ [:* :ndc.x 0.5] 0.5] [:- 0.5 [:* :ndc.y 0.5]]]]
   [:if [:|| [:< :uv.x 0.0] [:> :uv.x 1.0] [:< :uv.y 0.0] [:> :uv.y 1.0] [:> :ndc.z 1.0]]
    [[:return 1.0]]]
   [:let :bias [:max [:* :g.light-d.y [:- 1.0 :ndl]] :g.light-d.z]]
   [:let :texel :g.light-d.w]
   [:var :lit 0.0]
   [:for [:var :dx [:i -1]] [:<= :dx [:i 1]] [:++ :dx]
    [:for [:var :dy [:i -1]] [:<= :dy [:i 1]] [:++ :dy]
     [:+= :lit [:textureSampleCompareLevel :shadowMap :shadowSamp
                [:+ :uv [:* [:vec2 [:f32 :dx] [:f32 :dy]] :texel]]
                [:- :ndc.z :bias]]]]]
   [:return [:/ :lit 9.0]]])

(def VO-fields
  [[:clip [:vec4 :f32] {:builtin :position}] [:n [:vec3 :f32] {:location 0}]
   [:col [:vec3 :f32] {:location 1}] [:wpos [:vec3 :f32] {:location 2}] [:mat [:vec3 :f32] {:location 3}]])

;; per-instance model matrix (m0..m3) + color + material → clip-space + world varyings.
(def vs-body
  [[:let :model [:mat4 :m0 :m1 :m2 :m3]]
   [:let :world [:* :model [:vec4 :pos 1.0]]]
   [:decl :o :VO]
   [:set :o.clip [:* :g.vp :world]]
   [:set :o.n [:normalize [:. [:* :model [:vec4 :normal 0.0]] :xyz]]]
   [:set :o.col :color.rgb]
   [:set :o.wpos :world.xyz]
   [:set :o.mat :material.xyz]
   [:return :o]])

;; ── the depth-only shadow pass: instances from the sun's POV into the shadow map ──────────────────
;; only needs vp..light_vp (the first 5 G fields), not the lighting tunables.
(def shadow-vs-body
  [[:let :model [:mat4 :m0 :m1 :m2 :m3]]
   [:return [:* :g.light-vp :model [:vec4 :pos 1.0]]]])

(defn shadow-shader
  "The depth-only shadow-map vertex pass, generated from data."
  []
  (w/shader
   (w/struct* :G (vec (take 5 G-fields)))
   (w/binding* {:group 0 :binding 0 :space :uniform} :g :G)
   (apply w/func :vs {:stage :vertex
                      :params [[:pos [:vec3 :f32] {:location 0}] [:normal [:vec3 :f32] {:location 1}]
                               [:m0 [:vec4 :f32] {:location 2}] [:m1 [:vec4 :f32] {:location 3}]
                               [:m2 [:vec4 :f32] {:location 4}] [:m3 [:vec4 :f32] {:location 5}]
                               [:color [:vec4 :f32] {:location 6}] [:material [:vec4 :f32] {:location 7}]]
                      :ret [:builtin :position [:vec4 :f32]]} shadow-vs-body)))

(defn lit-shader
  "The complete lit instanced renderer WGSL — generated entirely from data (struct/bindings/shadow/
   vertex/fragment). One source; the web (kami.webgpu) and, in time, native run the same shader."
  []
  (w/shader
   (w/struct* :G G-fields)
   (w/binding* {:group 0 :binding 0 :space :uniform} :g :G)
   (w/binding* {:group 0 :binding 1} :shadowMap "texture_depth_2d")
   (w/binding* {:group 0 :binding 2} :shadowSamp "sampler_comparison")
   (apply w/func :shadow {:params [[:wpos [:vec3 :f32]] [:ndl :f32]] :ret :f32} shadow-fn-body)
   (w/struct* :VO VO-fields)
   (apply w/func :vs {:stage :vertex
                      :params [[:pos [:vec3 :f32] {:location 0}] [:normal [:vec3 :f32] {:location 1}]
                               [:m0 [:vec4 :f32] {:location 2}] [:m1 [:vec4 :f32] {:location 3}]
                               [:m2 [:vec4 :f32] {:location 4}] [:m3 [:vec4 :f32] {:location 5}]
                               [:color [:vec4 :f32] {:location 6}] [:material [:vec4 :f32] {:location 7}]]
                      :ret :VO} vs-body)
   (lit-fs)))

;; ── four-cascade directional shadows -----------------------------------------

(def cascaded-G-fields
  [[:vp :mat4] [:sun-dir :vec4] [:sun-col :vec4] [:sky :vec4]
   [:light-vp0 :mat4] [:light-vp1 :mat4] [:light-vp2 :mat4] [:light-vp3 :mat4]
   [:shadow-splits :vec4]
   [:light-a :vec4] [:light-b :vec4] [:light-c :vec4] [:light-d :vec4]])

(def foliage-G-fields
  "Textured foliage extension. Keeping this separate preserves the established
   448-byte cascaded uniform ABI for native and untextured adapters."
  (conj cascaded-G-fields
        ;; wind = normalized world X/Z direction, deterministic scene time, speed.
        [:wind :vec4]))

(def cascaded-shadow-fn-body
  [[:let :eye [:vec3 :g.sun-dir.w :g.sun-col.w :g.sky.w]]
   [:let :view-distance [:length [:- :eye :wpos]]]
   [:var :layer :i32 [:i 0]]
   [:var :light-vp :mat4 :g.light-vp0]
   [:if [:> :view-distance :g.shadow-splits.x]
    [[:set :layer [:i 1]] [:set :light-vp :g.light-vp1]]]
   [:if [:> :view-distance :g.shadow-splits.y]
    [[:set :layer [:i 2]] [:set :light-vp :g.light-vp2]]]
   [:if [:> :view-distance :g.shadow-splits.z]
    [[:set :layer [:i 3]] [:set :light-vp :g.light-vp3]]]
   [:let :lc [:* :light-vp [:vec4 :wpos 1.0]]]
   [:let :ndc [:/ :lc.xyz :lc.w]]
   [:let :uv [:vec2 [:+ [:* :ndc.x 0.5] 0.5] [:- 0.5 [:* :ndc.y 0.5]]]]
   [:if [:|| [:< :uv.x 0.0] [:> :uv.x 1.0] [:< :uv.y 0.0]
          [:> :uv.y 1.0] [:> :ndc.z 1.0]]
    [[:return 1.0]]]
   [:let :bias [:max [:* :g.light-d.y [:- 1.0 :ndl]] :g.light-d.z]]
   [:let :texel :g.light-d.w]
   [:var :lit 0.0]
   [:for [:var :dx [:i -1]] [:<= :dx [:i 1]] [:++ :dx]
    [:for [:var :dy [:i -1]] [:<= :dy [:i 1]] [:++ :dy]
     [:+= :lit [:textureSampleCompareLevel :shadowMap :shadowSamp
                [:+ :uv [:* [:vec2 [:f32 :dx] [:f32 :dy]] :texel]]
                :layer [:- :ndc.z :bias]]]]]
   [:return [:/ :lit 9.0]]])

(defn cascaded-shadow-shader
  "Depth-only shader for one layer of the shared four-layer shadow texture."
  [cascade-index]
  {:pre [(<= 0 cascade-index 3)]}
  (let [matrix-field (keyword (str "g.light-vp" cascade-index))
        body [[:let :model [:mat4 :m0 :m1 :m2 :m3]]
              [:return [:* matrix-field :model [:vec4 :pos 1.0]]]]]
    (w/shader
     (w/struct* :G cascaded-G-fields)
     (w/binding* {:group 0 :binding 0 :space :uniform} :g :G)
     (apply w/func :vs {:stage :vertex
                        :params [[:pos [:vec3 :f32] {:location 0}]
                                 [:normal [:vec3 :f32] {:location 1}]
                                 [:m0 [:vec4 :f32] {:location 2}]
                                 [:m1 [:vec4 :f32] {:location 3}]
                                 [:m2 [:vec4 :f32] {:location 4}]
                                 [:m3 [:vec4 :f32] {:location 5}]
                                 [:color [:vec4 :f32] {:location 6}]
                                 [:material [:vec4 :f32] {:location 7}]]
                        :ret [:builtin :position [:vec4 :f32]]} body))))

(defn cascaded-lit-shader
  "Lit shader selecting a cascade by camera distance and sampling a depth array."
  []
  (let [fs-body (walk/postwalk-replace {:shadow :cascaded-shadow} lit-fs-body)]
    (w/shader
     (w/struct* :G cascaded-G-fields)
     (w/binding* {:group 0 :binding 0 :space :uniform} :g :G)
     (w/binding* {:group 0 :binding 1} :shadowMap "texture_depth_2d_array")
     (w/binding* {:group 0 :binding 2} :shadowSamp "sampler_comparison")
     (apply w/func :cascaded-shadow
            {:params [[:wpos [:vec3 :f32]] [:ndl :f32]] :ret :f32}
            cascaded-shadow-fn-body)
     (w/struct* :VO VO-fields)
     (apply w/func :vs {:stage :vertex
                        :params [[:pos [:vec3 :f32] {:location 0}]
                                 [:normal [:vec3 :f32] {:location 1}]
                                 [:m0 [:vec4 :f32] {:location 2}]
                                 [:m1 [:vec4 :f32] {:location 3}]
                                 [:m2 [:vec4 :f32] {:location 4}]
                                 [:m3 [:vec4 :f32] {:location 5}]
                                 [:color [:vec4 :f32] {:location 6}]
                                 [:material [:vec4 :f32] {:location 7}]]
                        :ret :VO} vs-body)
     (apply w/func :fs {:stage :fragment :params [[:i :VO]]
                        :ret [:loc 0 [:vec4 :f32]]} fs-body))))

(defn cascaded-hdr-shader
  "Cascaded lit shader returning linear HDR. ACES/gamma are deferred to the
   final composite pass so bloom operates on values above display white."
  []
  (let [linear-body (conj (vec (drop-last 3 lit-fs-body))
                          [:return [:vec4 :c 1.0]])
        fs-body (walk/postwalk-replace {:shadow :cascaded-shadow} linear-body)]
    (w/shader
     (w/struct* :G cascaded-G-fields)
     (w/binding* {:group 0 :binding 0 :space :uniform} :g :G)
     (w/binding* {:group 0 :binding 1} :shadowMap "texture_depth_2d_array")
     (w/binding* {:group 0 :binding 2} :shadowSamp "sampler_comparison")
     (apply w/func :cascaded-shadow
            {:params [[:wpos [:vec3 :f32]] [:ndl :f32]] :ret :f32}
            cascaded-shadow-fn-body)
     (w/struct* :VO VO-fields)
     (apply w/func :vs {:stage :vertex
                        :params [[:pos [:vec3 :f32] {:location 0}]
                                 [:normal [:vec3 :f32] {:location 1}]
                                 [:m0 [:vec4 :f32] {:location 2}]
                                 [:m1 [:vec4 :f32] {:location 3}]
                                 [:m2 [:vec4 :f32] {:location 4}]
                                 [:m3 [:vec4 :f32] {:location 5}]
                                 [:color [:vec4 :f32] {:location 6}]
                                 [:material [:vec4 :f32] {:location 7}]]
                        :ret :VO} vs-body)
     (apply w/func :fs {:stage :fragment :params [[:i :VO]]
                        :ret [:loc 0 [:vec4 :f32]]} fs-body))))

;; ── textured metallic-roughness PBR -----------------------------------------

(def textured-VO-fields
  (conj (mapv (fn [field]
                (if (= :mat (first field))
                  [:mat [:vec4 :f32] {:location 3}]
                  field))
              VO-fields)
        [:uv [:vec2 :f32] {:location 4}]
        [:tangent [:vec4 :f32] {:location 5}]
        [:biome [:vec3 :f32] {:location 6}]
        [:biomeLayers [:vec3 :f32] {:location 7}]
        [:foliage [:vec4 :f32] {:location 8}]))

(def textured-vs-body
  [[:let :model [:mat4 :m0 :m1 :m2 :m3]]
   [:var :world [:* :model [:vec4 :pos 1.0]]]
   ;; UV.v is the portable base→tip coordinate for vegetation cards. Opaque
   ;; instances carry strength=0 and follow the exact pre-wind path.
   [:let :windWeight [:clamp [:- 1.0 :uv.y] 0.0 1.0]]
   [:let :windWave [:sin [:+ [:* :g.wind.z :g.wind.w :foliage.w] :foliage.z]]]
   [:let :windAmount [:* :foliage.y :windWeight :windWeight :windWave]]
   [:set :world.x [:+ :world.x [:* :g.wind.x :windAmount]]]
   [:set :world.z [:+ :world.z [:* :g.wind.y :windAmount]]]
   ;; The instance model contains rotation * non-uniform scale. Transforming a
   ;; normal with model directly bends lighting on tall/thin geometry; dividing
   ;; each basis column by its squared length is inverse-transpose(model3x3)
   ;; for this affine form, without carrying another per-instance matrix.
   [:let :normalWorld
    [:normalize
     [:+ [:/ [:* :m0.xyz :normal.x] [:max [:dot :m0.xyz :m0.xyz] 0.000001]]
         [:/ [:* :m1.xyz :normal.y] [:max [:dot :m1.xyz :m1.xyz] 0.000001]]
         [:/ [:* :m2.xyz :normal.z] [:max [:dot :m2.xyz :m2.xyz] 0.000001]]]]]
   [:decl :o :VO]
   [:set :o.clip [:* :g.vp :world]]
   [:set :o.n :normalWorld]
   [:set :o.col :color.rgb]
   [:set :o.wpos :world.xyz]
   [:set :o.mat :material]
   [:set :o.uv [:+ [:* :uv :uvTransform.xy] :uvTransform.zw]]
   [:set :o.tangent [:vec4 [:normalize [:. [:* :model [:vec4 :tangent.xyz 0.0]] :xyz]] :tangent.w]]
   [:set :o.biome :biomeWeights]
   [:set :o.biomeLayers :biomeLayerIndices]
   [:set :o.foliage :foliage]
   [:return :o]])

(def textured-pbr-fs-body
  [[:let :useTex [:clamp :i.mat.w 0.0 1.0]]
   ;; material.w = 0 selects the untextured fallback; positive values are the
   ;; one-based PBR texture-array layer carried by each instance.
   [:let :materialLayer [:i32 [:max [:- :i.mat.w 1.0] 0.0]]]
   [:let :baseN [:normalize :i.n]]
   [:let :T [:normalize [:- :i.tangent.xyz [:* :baseN [:dot :baseN :i.tangent.xyz]]]]]
   [:let :B [:* [:normalize [:cross :baseN :T]] :i.tangent.w]]
   [:let :biomeSum [:+ :i.biome.x :i.biome.y :i.biome.z]]
   [:let :biomeUse [:clamp :biomeSum 0.0 1.0]]
   [:let :bw [:/ :i.biome [:max :biomeSum 0.0001]]]
   [:let :grassLayer [:i32 [:max :i.biomeLayers.x 0.0]]]
   [:let :soilLayer [:i32 [:max :i.biomeLayers.y 0.0]]]
   [:let :rockLayer [:i32 [:max :i.biomeLayers.z 0.0]]]
   ;; CPU contract supplies both slope/height/macro weights and the material
   ;; library's zero-based array indices; no scene-specific order is assumed.
   [:let :biomeAlbedo
    [:+ [:* [:. [:textureSample :albedoTex :materialSamp :i.uv :grassLayer] :rgb] :bw.x]
        [:* [:. [:textureSample :albedoTex :materialSamp :i.uv :soilLayer] :rgb] :bw.y]
        [:* [:. [:textureSample :albedoTex :materialSamp :i.uv :rockLayer] :rgb] :bw.z]]]
   [:let :biomeNormal
    [:+ [:* [:textureSample :normalTex :materialSamp :i.uv :grassLayer] :bw.x]
        [:* [:textureSample :normalTex :materialSamp :i.uv :soilLayer] :bw.y]
        [:* [:textureSample :normalTex :materialSamp :i.uv :rockLayer] :bw.z]]]
   [:let :biomeMr
    [:+ [:* [:textureSample :metallicRoughnessTex :materialSamp :i.uv :grassLayer] :bw.x]
        [:* [:textureSample :metallicRoughnessTex :materialSamp :i.uv :soilLayer] :bw.y]
        [:* [:textureSample :metallicRoughnessTex :materialSamp :i.uv :rockLayer] :bw.z]]]
   [:let :singleNormal [:textureSample :normalTex :materialSamp :i.uv :materialLayer]]
   [:let :mapN [:- [:* [:mix :singleNormal :biomeNormal :biomeUse] 2.0] [:vec4 1.0]]]
   [:let :mappedN [:normalize [:+ [:* :T :mapN.x] [:* :B :mapN.y] [:* :baseN :mapN.z]]]]
   [:let :N [:normalize [:mix :baseN :mappedN :useTex]]]
   [:let :albedoSample [:textureSample :albedoTex :materialSamp :i.uv :materialLayer]]
   [:if [:&& [:>= :i.foliage.x 0.0] [:< :albedoSample.a :i.foliage.x]]
    [[:discard]]]
   [:let :resolvedAlbedo [:mix :albedoSample.rgb :biomeAlbedo :biomeUse]]
   [:let :baseColor [:* :i.col [:mix [:vec3 1.0] :resolvedAlbedo [:max :useTex :biomeUse]]]]
   [:let :mr [:textureSample :metallicRoughnessTex :materialSamp :i.uv :materialLayer]]
   [:let :resolvedMr [:mix :mr :biomeMr :biomeUse]]
   [:let :metallic [:clamp [:* :i.mat.x [:mix 1.0 :resolvedMr.b [:max :useTex :biomeUse]]] 0.0 1.0]]
   [:let :rough [:clamp [:* :i.mat.y [:mix 1.0 :resolvedMr.g [:max :useTex :biomeUse]]] 0.04 1.0]]
   [:let :emissive :i.mat.z]
   [:let :L [:normalize [:- :g.sun-dir.xyz]]]
   [:let :eye [:vec3 :g.sun-dir.w :g.sun-col.w :g.sky.w]]
   [:let :V [:normalize [:- :eye :i.wpos]]]
   [:let :H [:normalize [:+ :L :V]]]
   [:let :ndl [:max [:dot :N :L] 0.0]]
   [:let :ndv [:max [:dot :N :V] 0.001]]
   [:let :ndh [:max [:dot :N :H] 0.0]]
   [:let :vdh [:max [:dot :V :H] 0.0]]
   ;; Cook-Torrance GGX, matching the glTF metallic-roughness BRDF.
   [:let :alpha [:* :rough :rough]]
   [:let :alpha2 [:* :alpha :alpha]]
   [:let :dDenom [:+ [:* :ndh :ndh [:- :alpha2 1.0]] 1.0]]
   [:let :D [:/ :alpha2 [:* 3.14159265 :dDenom :dDenom]]]
   [:let :k [:/ [:* [:+ :rough 1.0] [:+ :rough 1.0]] 8.0]]
   [:let :Gv [:/ :ndv [:+ [:* :ndv [:- 1.0 :k]] :k]]]
   [:let :Gl [:/ :ndl [:+ [:* :ndl [:- 1.0 :k]] :k]]]
   [:let :G [:* :Gv :Gl]]
   [:let :F0 [:mix [:vec3 0.04] :baseColor :metallic]]
   [:let :F [:+ :F0 [:* [:- [:vec3 1.0] :F0] [:pow [:- 1.0 :vdh] 5.0]]]]
   [:let :specular [:/ [:* :F :D :G] [:+ [:* 4.0 :ndv :ndl] 0.001]]]
   [:let :diffuseWeight [:* [:- [:vec3 1.0] :F] [:- 1.0 :metallic]]]
   [:let :brdf [:+ [:/ [:* :diffuseWeight :baseColor] 3.14159265] :specular]]
   ;; Split-sum image-based lighting. The specular cube's mip axis is the
   ;; prefiltered roughness axis; the BRDF LUT stores the scale/bias integral.
   [:let :Fibl [:+ :F0 [:* [:- [:vec3 1.0] :F0]
                           [:pow [:- 1.0 :ndv] 5.0]]]]
   [:let :iblDiffuseWeight [:* [:- [:vec3 1.0] :Fibl] [:- 1.0 :metallic]]]
   [:let :irradiance [:. [:textureSample :irradianceTex :materialSamp :N] :rgb]]
   [:let :R [:reflect [:- :V] :N]]
   [:let :maxEnvironmentLod
    [:- [:f32 [:textureNumLevels :prefilteredSpecularTex]] 1.0]]
   [:let :prefiltered [:. [:textureSampleLevel :prefilteredSpecularTex
                            :materialSamp :R [:* :rough :maxEnvironmentLod]] :rgb]]
   [:let :environmentBrdf [:. [:textureSample :brdfLut :materialSamp
                                [:vec2 :ndv :rough]] :rg]]
   [:let :iblSpecular [:* :prefiltered
                        [:+ [:* :Fibl :environmentBrdf.x] :environmentBrdf.y]]]
   [:let :ibl [:* [:+ [:* :iblDiffuseWeight :irradiance :baseColor]
                       :iblSpecular]
                :g.light-a.w]]
   [:let :sh [:cascaded-shadow :i.wpos :ndl]]
   [:var :c [:+ :ibl
                [:* :brdf :g.sun-col.rgb :g.light-c.z :ndl :sh]
                [:* :baseColor :emissive]]]
   [:return [:vec4 :c 1.0]]])

(defn- cascaded-textured-shader* [fs-body]
  (w/shader
   (w/struct* :G foliage-G-fields)
   (w/binding* {:group 0 :binding 0 :space :uniform} :g :G)
   (w/binding* {:group 0 :binding 1} :shadowMap "texture_depth_2d_array")
   (w/binding* {:group 0 :binding 2} :shadowSamp "sampler_comparison")
   (w/binding* {:group 0 :binding 3} :albedoTex "texture_2d_array<f32>")
   (w/binding* {:group 0 :binding 4} :normalTex "texture_2d_array<f32>")
   (w/binding* {:group 0 :binding 5} :metallicRoughnessTex "texture_2d_array<f32>")
   (w/binding* {:group 0 :binding 6} :materialSamp "sampler")
   (w/binding* {:group 0 :binding 7} :irradianceTex "texture_cube<f32>")
   (w/binding* {:group 0 :binding 8} :prefilteredSpecularTex "texture_cube<f32>")
   (w/binding* {:group 0 :binding 9} :brdfLut "texture_2d<f32>")
   (apply w/func :cascaded-shadow
          {:params [[:wpos [:vec3 :f32]] [:ndl :f32]] :ret :f32}
          cascaded-shadow-fn-body)
   (w/struct* :VO textured-VO-fields)
   (apply w/func :vs {:stage :vertex
                      :params [[:pos [:vec3 :f32] {:location 0}]
                               [:normal [:vec3 :f32] {:location 1}]
                               [:m0 [:vec4 :f32] {:location 2}]
                               [:m1 [:vec4 :f32] {:location 3}]
                               [:m2 [:vec4 :f32] {:location 4}]
                               [:m3 [:vec4 :f32] {:location 5}]
                               [:color [:vec4 :f32] {:location 6}]
                               [:material [:vec4 :f32] {:location 7}]
                               [:uv [:vec2 :f32] {:location 8}]
                               [:tangent [:vec4 :f32] {:location 9}]
                               [:uvTransform [:vec4 :f32] {:location 10}]
                               [:biomeWeights [:vec3 :f32] {:location 11}]
                               [:biomeLayerIndices [:vec3 :f32] {:location 12}]
                               [:foliage [:vec4 :f32] {:location 13}]]
                      :ret :VO} textured-vs-body)
     (apply w/func :fs {:stage :fragment :params [[:i :VO]]
                      :ret [:loc 0 [:vec4 :f32]]} fs-body)))

(defn cascaded-foliage-shadow-shader
  "Depth-only cascade shader with the same wind deformation and alpha-mask
   decision as the color pass, preventing detached or rectangular card shadows."
  [cascade-index]
  {:pre [(<= 0 cascade-index 3)]}
  (let [matrix-field (keyword (str "g.light-vp" cascade-index))]
    (w/shader
     (w/struct* :G foliage-G-fields)
     (w/binding* {:group 0 :binding 0 :space :uniform} :g :G)
     (w/binding* {:group 0 :binding 1} :albedoTex "texture_2d_array<f32>")
     (w/binding* {:group 0 :binding 2} :materialSamp "sampler")
     (w/struct* :ShadowOut
                [[:clip [:vec4 :f32] {:builtin :position}]
                 [:uv [:vec2 :f32] {:location 0}]
                 [:cutoffLayer [:vec2 :f32] {:location 1}]])
     (apply w/func :vs {:stage :vertex
                        :params [[:pos [:vec3 :f32] {:location 0}]
                                 [:normal [:vec3 :f32] {:location 1}]
                                 [:m0 [:vec4 :f32] {:location 2}]
                                 [:m1 [:vec4 :f32] {:location 3}]
                                 [:m2 [:vec4 :f32] {:location 4}]
                                 [:m3 [:vec4 :f32] {:location 5}]
                                 [:color [:vec4 :f32] {:location 6}]
                                 [:material [:vec4 :f32] {:location 7}]
                                 [:uv [:vec2 :f32] {:location 8}]
                                 [:tangent [:vec4 :f32] {:location 9}]
                                 [:uvTransform [:vec4 :f32] {:location 10}]
                                 [:biomeWeights [:vec3 :f32] {:location 11}]
                                 [:biomeLayerIndices [:vec3 :f32] {:location 12}]
                                 [:foliage [:vec4 :f32] {:location 13}]]
                        :ret :ShadowOut}
            [[:let :model [:mat4 :m0 :m1 :m2 :m3]]
             [:var :world [:* :model [:vec4 :pos 1.0]]]
             [:let :windWeight [:clamp [:- 1.0 :uv.y] 0.0 1.0]]
             [:let :windWave [:sin [:+ [:* :g.wind.z :g.wind.w :foliage.w] :foliage.z]]]
             [:let :windAmount [:* :foliage.y :windWeight :windWeight :windWave]]
             [:set :world.x [:+ :world.x [:* :g.wind.x :windAmount]]]
             [:set :world.z [:+ :world.z [:* :g.wind.y :windAmount]]]
             [:decl :o :ShadowOut]
             [:set :o.clip [:* matrix-field :world]
             ]
             [:set :o.uv [:+ [:* :uv :uvTransform.xy] :uvTransform.zw]]
             [:set :o.cutoffLayer [:vec2 :foliage.x [:max [:- :material.w 1.0] 0.0]]]
             [:return :o]])
     (apply w/func :fs {:stage :fragment :params [[:i :ShadowOut]]}
            [[:let :alpha [:. [:textureSample :albedoTex :materialSamp :i.uv
                                [:i32 :i.cutoffLayer.y]] :a]]
             [:if [:&& [:>= :i.cutoffLayer.x 0.0] [:< :alpha :i.cutoffLayer.x]]
              [[:discard]]]]))))

(defn cascaded-textured-hdr-shader
  "Linear-HDR PBR shader with sRGB base color, tangent-space normal, and
   glTF metallic-roughness bindings. material.w enables texture sampling per
   instance. Positive material.w values select a one-based texture-array layer,
   so many materials and untextured objects share one instanced pipeline."
  []
  (cascaded-textured-shader* textured-pbr-fs-body))

(defn cascaded-textured-lit-shader
  "Display-ready sibling of cascaded-textured-hdr-shader. Applies ACES+gamma
   in-fragment for the adaptive direct-to-swapchain saturation tier."
  []
  (let [display-body
        (into (vec (drop-last textured-pbr-fs-body))
              [[:set :c [:clamp
                         [:/ [:* :c [:+ [:* 2.51 :c] [:vec3 0.03]]]
                          [:+ [:* :c [:+ [:* 2.43 :c] [:vec3 0.59]]] [:vec3 0.14]]]
                         [:vec3 0.0] [:vec3 1.0]]]
               [:set :c [:pow :c [:vec3 [:/ 1.0 :g.light-d.x]]]]
               [:return [:vec4 :c 1.0]]])]
    (cascaded-textured-shader* display-body)))

(def fullscreen-vertex-wgsl
  "struct FullscreenOut { @builtin(position) position: vec4<f32>, @location(0) uv: vec2<f32> };
@vertex fn vs(@builtin(vertex_index) i: u32) -> FullscreenOut {
  var p = array<vec2<f32>, 3>(vec2<f32>(-1.0,-1.0), vec2<f32>(3.0,-1.0), vec2<f32>(-1.0,3.0));
  var out: FullscreenOut;
  out.position = vec4<f32>(p[i], 0.0, 1.0);
  out.uv = p[i] * vec2<f32>(0.5,-0.5) + vec2<f32>(0.5,0.5);
  return out;
}")

(defn atmosphere-cloud-shader
  "Deterministic HDR atmosphere with an analytic two-octave cloud layer."
  []
  (w/shader
   fullscreen-vertex-wgsl
   "struct Atmosphere {
  zenith_enabled: vec4<f32>, horizon_rayleigh: vec4<f32>,
  sun_mie: vec4<f32>, sun_dir_disc: vec4<f32>,
  cloud_shape: vec4<f32>, cloud_color_softness: vec4<f32>,
  cloud_seed_pad: vec4<f32>, viewport_pad: vec4<f32>,
};
@group(0) @binding(0) var<uniform> atmosphere: Atmosphere;
fn hash21(p: vec2<f32>) -> f32 {
  let q = dot(p, vec2<f32>(127.1, 311.7)) + atmosphere.cloud_seed_pad.x;
  return fract(sin(q) * 43758.5453123);
}
fn valueNoise(p: vec2<f32>) -> f32 {
  let i = floor(p); let f = fract(p); let u = f*f*(3.0-2.0*f);
  return mix(mix(hash21(i), hash21(i+vec2<f32>(1.0,0.0)), u.x),
             mix(hash21(i+vec2<f32>(0.0,1.0)), hash21(i+vec2<f32>(1.0,1.0)), u.x), u.y);
}
fn fbm(p0: vec2<f32>) -> f32 {
  var p = p0; var sum = 0.0; var amplitude = 0.5;
  for (var octave = 0; octave < 4; octave++) {
    sum += valueNoise(p) * amplitude;
    p = p*2.07 + vec2<f32>(5.3,1.7);
    amplitude *= 0.5;
  }
  return sum / 0.9375;
}
@fragment fn fs(in: FullscreenOut) -> @location(0) vec4<f32> {
  let v = clamp(1.0-in.uv.y, 0.0, 1.0);
  let airMass = pow(v, 0.42 + atmosphere.horizon_rayleigh.w*0.08);
  var sky = mix(atmosphere.horizon_rayleigh.rgb, atmosphere.zenith_enabled.rgb, airMass);
  let sunUv = vec2<f32>(0.5 + atmosphere.sun_dir_disc.x*0.38,
                        0.52 + atmosphere.sun_dir_disc.y*0.34);
  let sunDistance = distance(in.uv, sunUv);
  let disc = 1.0-smoothstep(atmosphere.sun_dir_disc.w, atmosphere.sun_dir_disc.w*2.8, sunDistance);
  let mieHalo = exp(-sunDistance * (8.0 + 24.0*(1.0-atmosphere.sun_mie.w)));
  sky += atmosphere.sun_mie.rgb * (disc*5.0 + mieHalo*atmosphere.sun_mie.w);
  let scale = atmosphere.cloud_shape.z;
  let domain = vec2<f32>(in.uv.x*scale*1.78, (in.uv.y-atmosphere.cloud_shape.w)*scale*1.16);
  let body = fbm(domain);
  let erosion = fbm(domain*2.73+11.4);
  let cloudNoise = body*0.82 + erosion*0.18;
  let cloud = smoothstep(atmosphere.cloud_shape.x,
                         atmosphere.cloud_shape.x+atmosphere.cloud_color_softness.w,
                         cloudNoise) * atmosphere.cloud_shape.y;
  let horizonMask = smoothstep(0.04,0.24,in.uv.y) * (1.0-smoothstep(0.48,0.96,in.uv.y));
  let silver = pow(max(0.0,1.0-sunDistance*1.8),4.0);
  let cloudColor = atmosphere.cloud_color_softness.rgb * (0.72+silver*0.48);
  sky = mix(sky, cloudColor, clamp(cloud*horizonMask,0.0,0.92));
  return vec4<f32>(mix(atmosphere.horizon_rayleigh.rgb,sky,atmosphere.zenith_enabled.w),1.0);
}"))

(defn bloom-shader
  "Half-resolution bright-pass with a single 3x3 Gaussian filter."
  []
  (w/shader
   fullscreen-vertex-wgsl
   "@group(0) @binding(0) var hdrTex: texture_2d<f32>;
@group(0) @binding(1) var linearSampler: sampler;
@fragment fn fs(in: FullscreenOut) -> @location(0) vec4<f32> {
  let dims = vec2<f32>(textureDimensions(hdrTex));
  let texel = 1.0 / dims;
  var sum = vec3<f32>(0.0);
  for (var y = -1; y <= 1; y++) {
    for (var x = -1; x <= 1; x++) {
      let weight = select(select(1.0, 2.0, x == 0), select(2.0, 4.0, x == 0), y == 0);
      let c = textureSample(hdrTex, linearSampler, in.uv + vec2<f32>(f32(x),f32(y))*texel).rgb;
      let brightness = max(max(c.r,c.g),c.b);
      sum += c * smoothstep(0.8, 1.3, brightness) * weight;
    }
  }
  return vec4<f32>(sum / 16.0, 1.0);
}") )

(defn ssao-shader
  "Deterministic depth-derived screen-space contact occlusion.  The compact
   eight-float contract is radius/intensity/bias/power + near/far/fade range;
   no frame-varying noise texture is required, so visual regression captures
   are stable."
  []
  (w/shader
   fullscreen-vertex-wgsl
   "struct SsaoParams {
  shape: vec4<f32>, // radiusPx, intensity, bias, power
  range: vec4<f32>, // near, far, fadeStart, fadeEnd
  control: vec4<f32>, // sampleCount (1..12), remaining lanes reserved
};
@group(0) @binding(0) var depthTex: texture_depth_2d;
@group(0) @binding(1) var<uniform> params: SsaoParams;
fn linearDepth(d: f32) -> f32 {
  return (params.range.x * params.range.y) /
         max(params.range.y - d * (params.range.y - params.range.x), 0.0001);
}
@fragment fn fs(in: FullscreenOut) -> @location(0) vec4<f32> {
  let dims = vec2<i32>(textureDimensions(depthTex));
  let pixel = clamp(vec2<i32>(in.uv * vec2<f32>(dims)), vec2<i32>(0), dims-vec2<i32>(1));
  let rawCenter = textureLoad(depthTex, pixel, 0);
  if (rawCenter >= 0.999999) { return vec4<f32>(1.0); }
  let center = linearDepth(rawCenter);
  let left = linearDepth(textureLoad(depthTex, max(pixel-vec2<i32>(1,0),vec2<i32>(0)), 0));
  let right = linearDepth(textureLoad(depthTex, min(pixel+vec2<i32>(1,0),dims-vec2<i32>(1)), 0));
  let up = linearDepth(textureLoad(depthTex, max(pixel-vec2<i32>(0,1),vec2<i32>(0)), 0));
  let down = linearDepth(textureLoad(depthTex, min(pixel+vec2<i32>(0,1),dims-vec2<i32>(1)), 0));
  let depthGradient = vec2<f32>((right-left)*0.5,(down-up)*0.5);
  let golden = 2.39996323;
  var occlusion = 0.0;
  var weightSum = 0.0;
  for (var i = 0; i < 12; i++) {
    if (i >= i32(clamp(params.control.x, 1.0, 12.0))) { break; }
    let fi = f32(i);
    let ring = (0.35 + 0.65 * (fi + 0.5) / clamp(params.control.x, 1.0, 12.0)) * params.shape.x;
    let dir = vec2<f32>(cos(fi*golden), sin(fi*golden));
    let offset = vec2<i32>(round(dir*ring));
    let q = clamp(pixel + offset, vec2<i32>(0), dims-vec2<i32>(1));
    let sampleDepth = linearDepth(textureLoad(depthTex, q, 0));
    let expectedDepth = center + dot(vec2<f32>(offset),depthGradient);
    let delta = expectedDepth - sampleDepth;
    let rangeWeight = 1.0 - smoothstep(params.shape.x*0.05, params.shape.x*0.75, abs(delta));
    occlusion += select(0.0, rangeWeight, delta > params.shape.z);
    weightSum += rangeWeight;
  }
  let distanceFade = 1.0-smoothstep(params.range.z, params.range.w, center);
  let ao = pow(clamp(1.0-(occlusion/max(weightSum,1.0))*params.shape.y*distanceFade,0.38,1.0), params.shape.w);
  return vec4<f32>(ao,ao,ao,1.0);
}"))

(defn hdr-composite-shader
  "Combine linear scene+bloom+AO, apply ACES filmic mapping, then output gamma."
  []
  (w/shader
   fullscreen-vertex-wgsl
   "@group(0) @binding(0) var hdrTex: texture_2d<f32>;
@group(0) @binding(1) var bloomTex: texture_2d<f32>;
@group(0) @binding(2) var aoTex: texture_2d<f32>;
@group(0) @binding(3) var linearSampler: sampler;
@fragment fn fs(in: FullscreenOut) -> @location(0) vec4<f32> {
  var c = textureSample(hdrTex, linearSampler, in.uv).rgb;
  c += textureSample(bloomTex, linearSampler, in.uv).rgb * 0.12;
  c *= mix(1.0, textureSample(aoTex, linearSampler, in.uv).r, 0.58);
  c = clamp((c*(2.51*c+vec3<f32>(0.03))) / (c*(2.43*c+vec3<f32>(0.59))+vec3<f32>(0.14)), vec3<f32>(0.0), vec3<f32>(1.0));
  c = pow(c, vec3<f32>(1.0/2.2));
  return vec4<f32>(c,1.0);
}") )

(defn hdr-ao-composite-shader
  "Adaptive-tier ACES composite retaining contact AO while shedding bloom."
  []
  (w/shader
   fullscreen-vertex-wgsl
   "@group(0) @binding(0) var hdrTex: texture_2d<f32>;
@group(0) @binding(1) var aoTex: texture_2d<f32>;
@group(0) @binding(2) var linearSampler: sampler;
@fragment fn fs(in: FullscreenOut) -> @location(0) vec4<f32> {
  var c = textureSample(hdrTex, linearSampler, in.uv).rgb;
  c *= mix(1.0, textureSample(aoTex, linearSampler, in.uv).r, 0.58);
  c = clamp((c*(2.51*c+vec3<f32>(0.03))) / (c*(2.43*c+vec3<f32>(0.59))+vec3<f32>(0.14)), vec3<f32>(0.0), vec3<f32>(1.0));
  c = pow(c, vec3<f32>(1.0/2.2));
  return vec4<f32>(c,1.0);
}"))

(ns kotoba.render.texture
  "Pure CPU-side texture helpers ported from `kami-render/src/texture.rs`:
   mip-level-count math and the box-filter CPU mipmap downsampler (both were
   plain byte-array transforms in the Rust source, unconditionally compiled
   whether or not you're on the GPU path).

   NOT ported: `GpuTexture`, `create_texture`, `default_white_texture`,
   `default_normal_texture`, `default_mr_texture` — actual `wgpu::Device`
   texture/sampler creation and `queue.write_texture` upload. Those are
   host-adapter GPU calls; only their **pixel data** is portable, and is
   provided below as [[white-pixel]] / [[normal-pixel]] / [[mr-pixel]]."
  )

(defn mip-level-count
  "Number of mip levels for a `width` x `height` texture (full chain).
   `(1 + floor(log2(max(width, height))))`. Matches the Rust
   `(width.max(height) as f32).log2().floor() as u32 + 1`."
  [width height]
  (inc (long (Math/floor (/ (Math/log (double (max width height))) (Math/log 2.0))))))

(defn generate-mipmaps-cpu
  "Generate the full CPU-side mip chain via 2x2 box-filter downsampling.
   `base-data` is a flat RGBA8 pixel vector (unsigned ints 0..255, 4 per
   pixel, row-major). Returns a vector of `{:level :width :height :data}`
   maps for levels `1..mip-levels-1` (level 0 is the input, not repeated).
   Faithful port of `generate_mipmaps_cpu`'s box-filter downsample loop
   (clamped edge sampling — the last row/col repeats, matching
   `.min(w-1)`/`.min(h-1)` in the Rust source)."
  [base-data base-width base-height mip-levels]
  (loop [level 1 w base-width h base-height prev base-data out []]
    (if (>= level mip-levels)
      out
      (let [new-w (max 1 (quot w 2))
            new-h (max 1 (quot h 2))
            stride (* w 4)
            new-data
            (vec
             (for [y (range new-h) x (range new-w) c (range 4)]
               (let [sx (* x 2) sy (* y 2)
                     sum (reduce +
                                 (for [dy (range 2) dx (range 2)]
                                   (let [px (min (+ sx dx) (dec w))
                                         py (min (+ sy dy) (dec h))]
                                     (nth prev (+ (* py stride) (* px 4) c)))))]
                 (quot sum 4))))]
        (recur (inc level) new-w new-h new-data
               (conj out {:level level :width new-w :height new-h :data new-data}))))))

(def white-pixel
  "1x1 white pixel (fallback for untextured albedo)."
  [255 255 255 255])

(def normal-pixel
  "1x1 flat normal map (0.5, 0.5, 1.0 = up in tangent space)."
  [128 128 255 255])

(def mr-pixel
  "1x1 default metallic-roughness (metallic=0, roughness=0.5).
   glTF convention: G=roughness, B=metallic."
  [0 128 0 255])

(defn rgba8
  "Portable RGBA8 texture descriptor consumed by WebGPU/native host adapters.
   `data` is row-major unorm bytes. `color-space` is :srgb for base color and
   :linear for normal/metallic-roughness data textures."
  [width height data color-space]
  (when-not (and (pos-int? width) (pos-int? height))
    (throw (ex-info "texture dimensions must be positive integers"
                    {:width width :height height})))
  (when-not (= (* width height 4) (count data))
    (throw (ex-info "RGBA8 byte count does not match dimensions"
                    {:width width :height height :expected (* width height 4)
                     :actual (count data)})))
  (when-not (#{:srgb :linear} color-space)
    (throw (ex-info "texture color-space must be :srgb or :linear"
                    {:color-space color-space})))
  {:schema :kotoba.render/texture-rgba8-v1
   :width width :height height :data (vec data) :color-space color-space})

(def fallback-textures
  "Spec-correct 1x1 PBR bindings. Hosts bind these even when an asset omits a
   texture, so shader layouts never branch or claim an unavailable resource."
  {:albedo (rgba8 1 1 white-pixel :srgb)
   :normal (rgba8 1 1 normal-pixel :linear)
   :metallic-roughness (rgba8 1 1 mr-pixel :linear)})

(defn pbr-texture-set
  "Complete a partial {:albedo :normal :metallic-roughness} texture set with
   portable fallbacks. Unknown keys are retained for forward-compatible hosts."
  [textures]
  (merge fallback-textures (or textures {})))

(defn- solid-rgba8
  [width height pixel color-space]
  (rgba8 width height (vec (mapcat identity (repeat (* width height) pixel)))
         color-space))

(def ^:private fallback-spec
  {:albedo {:pixel white-pixel :color-space :srgb}
   :normal {:pixel normal-pixel :color-space :linear}
   :metallic-roughness {:pixel mr-pixel :color-space :linear}})

(defn pbr-texture-library
  "Build a texture-array-ready vector of PBR texture sets.

   Every layer has all three glTF metallic-roughness channels. Missing maps are
   expanded from spec-correct solid fallbacks to the authored dimensions, and
   each channel is required to have identical dimensions across layers. This
   explicit constraint lets WebGPU/native hosts preserve one instanced draw and
   select a material with a texture-array layer instead of changing bind groups.

   `sets` may be nil/empty (one fallback layer), one legacy texture-set map, or a
   vector of texture-set maps."
  [sets]
  (let [sets (cond
               (or (nil? sets) (and (sequential? sets) (empty? sets))) [{}]
               (map? sets) [sets]
               (sequential? sets) (vec sets)
               :else (throw (ex-info "PBR texture library must be a map or sequence"
                                     {:value sets})))
        kinds (keys fallback-spec)
        dimensions
        (into {}
              (for [kind kinds
                    :let [authored (keep #(get % kind) sets)
                          dims (set (map (juxt :width :height) authored))]]
                (do
                  (when (> (count dims) 1)
                    (throw (ex-info "texture-array layers must share dimensions"
                                    {:kind kind :dimensions dims})))
                  [kind (or (first dims) [1 1])])))]
    (mapv
     (fn [texture-set]
       (reduce
        (fn [result kind]
          (let [[width height] (get dimensions kind)
                descriptor (get texture-set kind)
                {:keys [pixel color-space]} (get fallback-spec kind)]
            (assoc result kind
                   (or descriptor (solid-rgba8 width height pixel color-space)))))
        (or texture-set {}) kinds))
     sets)))

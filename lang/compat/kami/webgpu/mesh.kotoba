(ns kami.webgpu.mesh
  "A second, additive WebGPU executor for arbitrary skinned/morphable triangle
   meshes — new for ADR-2607031200 Phase 2 (com-junkawasaki/root), which asked
   for `kami.webgpu.cljs`'s `draw!` to consume `parse-render-ir`'s `:meshes`
   (with `:skin`/`:joints`/`:morphs`).

   Two real findings changed the plan (both confirmed by reading the actual
   current source, not assumed):

   1. `kotoba.webgpu-rs.render-ir/parse-mesh` (`kotoba-lang/webgpu`) does NOT
      carry vertex geometry — a `:meshes` entry is `{:id :url :pos :rot :scale
      :material :skin :joints :morphs}`, a transform+material+skin *binding*
      whose `:url` a host loader is meant to resolve. No such geometry loader
      exists anywhere in `kotoba-lang` yet.
   2. `kami.webgpu.cljs`'s `draw!` is built entirely around ONE fixed vertex
      model: a small library of *procedural, instanced* primitives (box/
      sphere/... from `kami.webgpu.ir/default-geometry`), one shared
      pos+normal vertex buffer per geometry KIND, drawn many times via a
      per-instance model-matrix buffer. There is no per-mesh arbitrary vertex
      buffer, no skin/morph binding, and no room for one without changing that
      buffer/pipeline model — extending it in place would risk the existing
      golden/visual behaviour ADR-0044's own migration note was careful to
      preserve.

   So instead of editing `kami.webgpu.cljs`, this is a NEW sibling executor,
   `kami.webgpu.mesh`, for the one case that file's model can't express: a
   single arbitrary mesh with real vertex/index data, optional glTF-style
   morph targets (`POSITION` deltas blended by weight), and optional glTF-style
   skinning (4 joint indices + weights per vertex, blended against a joint
   matrix palette). It shares the device/canvas bootstrap style of
   `kami.webgpu/init!` but is a distinct pipeline, distinct vertex layout,
   distinct shader — additive, `kami.webgpu.cljs` is untouched.

   Geometry source: `character-creator.gpu-adapter` (kotoba-lang/
   kami-app-character-creator) decodes real vertex/morph data straight off an
   in-memory `vrm.vrm-types/vrm-document` via `vrm.convert/read-accessor-f32`
   (restored 1:1 from `kami-vrm`, ADR-2607010930) — this namespace only knows
   about plain `{:positions :normals :indices :morph-target-deltas}`/`{:joints
   :weights :joint-count}` maps, not VRM/character-creator specifics.

   Skinning is NOT demoed against a real character-creator avatar: Phase 1's
   generated mesh has no JOINTS_0/WEIGHTS_0 (`character.body`'s mesh
   generators don't attach vertex weights — a real, separate limitation, not
   introduced here). `preview-demo.html` (kami-app-character-creator) instead
   drives this shader's skinning path with a small synthetic 2-bone bending
   quad-strip fixture, and its morph path with the real character-creator head
   mesh — see that file's comments.

   Added later (/loop maturity pass, ADR-2607031200): procedural, computed-
   in-shader PATTERNS as a second, backward-compatible draw-call option
   alongside the original flat `color`. This is explicitly NOT a texture/UV-
   decal system — no image loading, no sampler, no UV vertex attribute (an
   actual bitmap-texture pipeline is a separate, much larger effort). It's a
   `color_b` + `pattern_kind` + `pattern_params` uniform, evaluated per-
   fragment from each vertex's un-morphed, un-skinned LOCAL position
   (`VertexOut.local_pos`, carried through unchanged from the vertex shader's
   `position` input) — local space so a pattern (e.g. a scar's edge fade)
   stays anchored to the mesh's own geometry regardless of pose/animation,
   not the world-space result of skinning. `pattern_kind` 0 (the default,
   byte-for-byte what every pre-existing call site already produces since
   the new uniform fields default to zero) reproduces the exact old flat-
   color behaviour — `t` is always `0.0`, so `mix(color, color_b, 0.0) =
   color`.

   Added later still (/loop maturity pass, real-VRM-base spike follow-up):
   an actual bitmap-TEXTURE path + basic 2-tone TOON shading, closing the
   'no texture pipeline' gap the pattern work above explicitly deferred.
   Real VRM/VRoid faces are painted via a baseColorTexture, not vertex
   colour — a real production VRM (Seed-san.vrm, tested locally, never
   committed — see `character-creator.gpu-adapter/mesh-base-color-texture`
   + `vrm.convert/read-base-color-texture` for the loader side) rendered
   through the pre-texture version of this shader had a blank face.

   Texture: a UV vertex attribute (`TEXCOORD_0` convention) + one
   `texture_2d<f32>`/`sampler` pair per draw call, sampled and multiplied
   into the pattern-resolved base colour. Backward compatibility trick: the
   shader ALWAYS samples the texture (no `has_texture` branch) — every
   pre-existing/untextured call binds a shared 1x1 opaque-white texture
   (`init!`'s `:default-texture`), so `textureSample(...).rgb = (1,1,1)`
   and `base_color * (1,1,1) = base_color`, exactly the old output. UVs
   default to `(0,0)` when a mesh has none (same default-texture makes the
   sample value irrelevant either way).

   Toon shading: `shade_kind` 0 (default) is the original continuous
   `ambient + ndl*(1-ambient)` lighting, byte-identical to every
   pre-existing call. `shade_kind` 1 is a 2-tone step (`toon_threshold` +
   `toon_smooth` transition band) between a fixed dim/lit factor — the
   anime-style hard light/shadow boundary. This is deliberately NOT full
   MToon (no rim light, no matcap, no outline pass, no per-material shade
   COLOR — just a brightness step; a real shade-colour uniform would grow
   this struct further and isn't needed to prove the texture pipeline
   works). Texture loading itself is async (`createImageBitmap`) —
   `upload-texture!` returns a Promise of a GPUTexture; callers `.then`
   before passing it to `draw!`."
  (:require [clojure.string :as str]
            [kami.webgpu.render-style :as render-style]
            [kotoba.webgl :as webgl]
            [w3.webgpu :as w3]))

;; --- minimal camera math (duplicated, not shared, from kami.webgpu — kept
;; private there; a handful of pure functions, not worth coupling two
;; independent executors over) --------------------------------------------

(defn- m4 [] (js/Float32Array. 16))
(defn- m4-mul [a b]
  (let [o (m4)]
    (dotimes [c 4]
      (dotimes [r 4]
        (aset o (+ (* c 4) r)
              (+ (* (aget a r)        (aget b (+ (* c 4) 0)))
                 (* (aget a (+ r 4))  (aget b (+ (* c 4) 1)))
                 (* (aget a (+ r 8))  (aget b (+ (* c 4) 2)))
                 (* (aget a (+ r 12)) (aget b (+ (* c 4) 3)))))))
    o))

(defn- perspective [fovy aspect near far]
  (let [f (/ 1.0 (js/Math.tan (/ fovy 2.0))) nf (/ 1.0 (- near far)) o (m4)]
    (aset o 0 (/ f aspect)) (aset o 5 f)
    (aset o 10 (* far nf)) (aset o 11 -1.0) (aset o 14 (* far near nf)) o))

(defn- v-sub [a b]
  [(- (nth a 0) (nth b 0)) (- (nth a 1) (nth b 1)) (- (nth a 2) (nth b 2))])
(defn- v-cross [a b]
  [(- (* (nth a 1) (nth b 2)) (* (nth a 2) (nth b 1)))
   (- (* (nth a 2) (nth b 0)) (* (nth a 0) (nth b 2)))
   (- (* (nth a 0) (nth b 1)) (* (nth a 1) (nth b 0)))])
(defn- v-norm [a]
  (let [l (js/Math.hypot (nth a 0) (nth a 1) (nth a 2))
        l (if (< l 1e-9) 1.0 l)]
    [(/ (nth a 0) l) (/ (nth a 1) l) (/ (nth a 2) l)]))
(defn- v-dot [a b]
  (+ (* (nth a 0) (nth b 0)) (* (nth a 1) (nth b 1)) (* (nth a 2) (nth b 2))))

(defn- look-at [eye center up]
  (let [f (v-norm (v-sub center eye))
        s (v-norm (v-cross f up))
        u (v-cross s f)
        o (m4)]
    (aset o 0 (nth s 0)) (aset o 4 (nth s 1)) (aset o 8 (nth s 2))
    (aset o 1 (nth u 0)) (aset o 5 (nth u 1)) (aset o 9 (nth u 2))
    (aset o 2 (- (nth f 0))) (aset o 6 (- (nth f 1))) (aset o 10 (- (nth f 2)))
    (aset o 12 (- (v-dot s eye))) (aset o 13 (- (v-dot u eye))) (aset o 14 (v-dot f eye))
    (aset o 15 1.0) o))

(defn view-projection
  "eye/target [x y z], aspect ratio -> a column-major mat4 Float32Array (wgpu convention)."
  [eye target aspect]
  (m4-mul (perspective (/ js/Math.PI 3.0) aspect 0.05 100.0) (look-at eye target [0 1 0])))

(defn- translation-matrix [[x y z]]
  (let [o (m4)]
    (aset o 0 1) (aset o 5 1) (aset o 10 1) (aset o 15 1)
    (aset o 12 x) (aset o 13 y) (aset o 14 z)
    o))

(defn- scale-matrix [[x y z]]
  (let [o (m4)]
    (aset o 0 x) (aset o 5 y) (aset o 10 z) (aset o 15 1) o))

(defn- rotation-x-matrix [angle]
  (let [o (m4) c (js/Math.cos angle) s (js/Math.sin angle)]
    (aset o 0 1) (aset o 5 c) (aset o 6 s) (aset o 9 (- s)) (aset o 10 c) (aset o 15 1) o))
(defn- rotation-y-matrix [angle]
  (let [o (m4) c (js/Math.cos angle) s (js/Math.sin angle)]
    (aset o 0 c) (aset o 2 (- s)) (aset o 5 1) (aset o 8 s) (aset o 10 c) (aset o 15 1) o))
(defn- rotation-z-matrix [angle]
  (let [o (m4) c (js/Math.cos angle) s (js/Math.sin angle)]
    (aset o 0 c) (aset o 1 s) (aset o 4 (- s)) (aset o 5 c) (aset o 10 1) (aset o 15 1) o))

(defn model-matrix
  "Column-major TRS model matrix. Rotation is XYZ Euler radians and scale
  defaults to identity. Shared here so applications never own GPU matrices."
  [{:keys [matrix translation rotation scale]
    :or {translation [0 0 0] rotation [0 0 0] scale [1 1 1]}}]
  (if matrix
    (js/Float32Array. (clj->js matrix))
    (let [[rx ry rz] rotation]
      (m4-mul (translation-matrix translation)
              (m4-mul (rotation-z-matrix rz)
                      (m4-mul (rotation-y-matrix ry)
                              (m4-mul (rotation-x-matrix rx) (scale-matrix scale))))))))

;; --- the shader: morph blend (glTF POSITION-delta convention) then optional
;; 4-joint linear-blend skinning, in one vertex stage; flat single-color
;; N.L-lit fragment (this is a correctness demo, not a material system) ------

(def SHADER
  "struct Uniforms {
  mvp: mat4x4<f32>,
  color: vec4<f32>,
  color_b: vec4<f32>,
  vertex_count: u32,
  morph_count: u32,
  joint_count: u32,
  pattern_kind: u32,
  pattern_params: vec4<f32>,
  shade_kind: u32,
  toon_threshold: f32,
  toon_smooth: f32,
  _pad2: u32,
  material_params: vec4<f32>,
  uv_transform: vec4<f32>,
  shade_color: vec4<f32>,
  rim_color: vec4<f32>,
  stylized_params: vec4<f32>,
  highlight_params: vec4<f32>,
};

@group(0) @binding(0) var<uniform> u: Uniforms;
@group(0) @binding(1) var<storage, read> morph_deltas: array<vec4<f32>>;
@group(0) @binding(2) var<storage, read> morph_weights: array<f32>;
@group(0) @binding(3) var<storage, read> joint_matrices: array<mat4x4<f32>>;
@group(0) @binding(4) var tex: texture_2d<f32>;
@group(0) @binding(5) var samp: sampler;

struct VertexOut {
  @builtin(position) clip: vec4<f32>,
  @location(0) n: vec3<f32>,
  @location(1) local_pos: vec3<f32>,
  @location(2) uv: vec2<f32>,
};

@vertex
fn vs(
  @location(0) position: vec3<f32>,
  @location(1) normal: vec3<f32>,
  @location(2) uv_in: vec2<f32>,
  @location(3) joints: vec4<u32>,
  @location(4) weights: vec4<f32>,
  @builtin(vertex_index) vidx: u32
) -> VertexOut {
  var pos = position;
  for (var t: u32 = 0u; t < u.morph_count; t = t + 1u) {
    let w = morph_weights[t];
    if (w != 0.0) {
      let d = morph_deltas[t * u.vertex_count + vidx];
      pos = pos + d.xyz * w;
    }
  }
  var world = vec4<f32>(pos, 1.0);
  if (u.joint_count > 0u) {
    let base = vec4<f32>(pos, 1.0);
    var acc = vec4<f32>(0.0, 0.0, 0.0, 0.0);
    for (var j: u32 = 0u; j < 4u; j = j + 1u) {
      let jm = joint_matrices[joints[j]];
      acc = acc + (jm * base) * weights[j];
    }
    world = acc;
  }
  var out: VertexOut;
  out.clip = u.mvp * world;
  out.n = normal;
  out.local_pos = position;
  out.uv = uv_in * u.uv_transform.zw + u.uv_transform.xy;
  return out;
}

// pattern_kind: 0 = flat (color only, color_b unused — the pre-existing
// behaviour, byte-identical). 1 = linear gradient (color->color_b along
// pattern_params.xyz, a local-space axis; .w is the half-range that maps to
// t=0..1). 2 = radial gradient (color at pattern_params.xyz, a local-space
// center point, fading to color_b at pattern_params.w, the radius — the
// scar/tattoo edge-fade case). 3 = stripes (hard color/color_b bands along
// pattern_params.xyz at frequency pattern_params.w).
fn pattern_t(local_pos: vec3<f32>) -> f32 {
  if (u.pattern_kind == 1u) {
    let axis = normalize(u.pattern_params.xyz);
    let range = max(u.pattern_params.w, 1e-4);
    return clamp(dot(local_pos, axis) / range * 0.5 + 0.5, 0.0, 1.0);
  } else if (u.pattern_kind == 2u) {
    let center = u.pattern_params.xyz;
    let radius = max(u.pattern_params.w, 1e-4);
    return clamp(length(local_pos - center) / radius, 0.0, 1.0);
  } else if (u.pattern_kind == 3u) {
    let axis = normalize(u.pattern_params.xyz);
    let freq = u.pattern_params.w;
    return step(0.0, sin(dot(local_pos, axis) * freq));
  }
  return 0.0;
}

// shade_kind: 0 = continuous N.L lighting (pre-existing, byte-identical).
// 1 = 2-tone toon step — a smoothstep transition band (width toon_smooth)
// centred on toon_threshold, between a fixed dim factor and full lit. Not
// full MToon (no shade COLOR, no rim/matcap/outline) — a brightness step
// only, sufficient to prove the texture path and show a visibly harder
// light/shadow boundary than the continuous mode.
fn toon_lit(ndl: f32) -> f32 {
  if (u.shade_kind == 1u) {
    let smooth_w = max(u.toon_smooth, 1e-4);
    let edge = smoothstep(u.toon_threshold - smooth_w, u.toon_threshold + smooth_w, ndl);
    return mix(0.35, 1.0, edge);
  }
  let ambient = 0.35;
  return ambient + ndl * (1.0 - ambient);
}

@fragment
fn fs(in: VertexOut) -> @location(0) vec4<f32> {
  let light_dir = normalize(vec3<f32>(0.4, 0.8, 0.5));
  let n = normalize(in.n);
  let ndl = max(dot(n, light_dir), 0.0);
  let t = pattern_t(in.local_pos);
  let base_color = mix(u.color.rgb, u.color_b.rgb, t);
  let tex_sample = textureSample(tex, samp, in.uv);
  let albedo = base_color * tex_sample.rgb;
  let metallic = clamp(u.material_params.x, 0.0, 1.0);
  let roughness = clamp(u.material_params.y, 0.04, 1.0);
  let half_dir = normalize(light_dir + vec3<f32>(0.0, 0.0, 1.0));
  let specular = pow(max(dot(n, half_dir), 0.0), mix(128.0, 2.0, roughness)) * ndl;
  let f0 = mix(vec3<f32>(0.04), albedo, metallic);
  if (u.shade_kind == 1u) {
    let smooth_w = max(u.toon_smooth, 1e-4);
    let shade_edge = smoothstep(u.toon_threshold - smooth_w, u.toon_threshold + smooth_w, ndl);
    let diffuse = mix(albedo * u.shade_color.rgb, albedo, shade_edge);
    let rim_base = clamp(1.0 - max(dot(n, vec3<f32>(0.0, 0.0, 1.0)), 0.0) + u.stylized_params.z, 0.0, 1.0);
    let rim = pow(rim_base, max(u.stylized_params.y, 1e-4)) * u.stylized_params.x;
    let bands = max(u.stylized_params.w, 1.0);
    let spec_edge = smoothstep(u.highlight_params.y - max(u.highlight_params.w, 1e-4),
                               u.highlight_params.y + max(u.highlight_params.w, 1e-4), specular);
    let quantized_spec = floor(spec_edge * bands + 0.5) / bands;
    return vec4<f32>(diffuse * (1.0 - metallic * 0.45)
                     + u.rim_color.rgb * rim
                     + f0 * quantized_spec * u.highlight_params.x, 1.0);
  }
  let ambient = 0.35;
  let lit = ambient + ndl * (1.0 - ambient);
  return vec4<f32>(albedo * lit * (1.0 - metallic * 0.45) + f0 * specular, 1.0);
}
")

(def ^:private VERTEX-STRIDE 64) ;; pos(12) + normal(12) + uv(8) + joints(16, uint32x4) + weights(16)

(defn- default-texture!
  "1x1 opaque-white texture — the shared fallback every untextured `draw!`
  call binds, so the shader's unconditional `textureSample` is a no-op
  (`base_color * (1,1,1) = base_color`), achieving backward compatibility
  without a `has_texture` branch in WGSL."
  [device]
  (let [tex (w3/create-texture! device #js {:size #js {:width 1 :height 1}
                                         :format "rgba8unorm"
                                         :usage (bit-or (w3/texture-usage :texture-binding)
                                                        (w3/texture-usage :copy-dst))})]
    (w3/write-texture! (w3/device-queue device) #js {:texture tex}
                       (js/Uint8Array. #js [255 255 255 255])
                       #js {:bytesPerRow 4} #js {:width 1 :height 1})
    tex))

(defn init!
  "Build the skin/morph mesh pipeline once (canvas already configured for
  WebGPU by the caller — reuses the same `device`/`ctx`/`fmt` a `kami.webgpu/
  init!` context already produced, so both executors can draw into the same
  canvas/frame if desired). Returns the pipeline context (now also carrying
  `:default-texture`/`:default-sampler`, the backward-compat fallback every
  `draw!` call without its own real texture binds)."
  [device fmt]
  (let [mod (w3/create-shader-module! device #js {:code SHADER})
        pipe (w3/create-render-pipeline! device
               #js {:layout "auto"
                    :vertex #js {:module mod :entryPoint "vs"
                                 :buffers #js [#js {:arrayStride VERTEX-STRIDE
                                                     :attributes
                                                     #js [#js {:format "float32x3" :offset 0 :shaderLocation 0}
                                                          #js {:format "float32x3" :offset 12 :shaderLocation 1}
                                                          #js {:format "float32x2" :offset 24 :shaderLocation 2}
                                                          #js {:format "uint32x4" :offset 32 :shaderLocation 3}
                                                          #js {:format "float32x4" :offset 48 :shaderLocation 4}]}]}
                    :fragment #js {:module mod :entryPoint "fs" :targets #js [#js {:format fmt}]}
                    :primitive #js {:cullMode "none"}
                    :depthStencil #js {:format "depth24plus" :depthWriteEnabled true :depthCompare "less"}})]
    {:device device :pipe pipe
     :default-texture (default-texture! device)
     :default-sampler (w3/create-sampler! device #js {:magFilter "linear" :minFilter "linear"})}))

(defn upload-texture!
  "Decode `{:bytes :mime-type}` (`vrm.convert/read-base-color-texture`'s
  output shape) into a real `GPUTexture`. Async (image decode via
  `createImageBitmap`) — returns a `Promise` resolving to the texture;
  callers `.then` before passing it to `draw!`'s `:texture`."
  [{:keys [device]} {:keys [bytes mime-type]}]
  (-> (js/createImageBitmap (js/Blob. #js [(js/Uint8Array. (clj->js (vec bytes)))] #js {:type mime-type}))
      (.then (fn [bitmap]
               (let [w (.-width bitmap) h (.-height bitmap)
                     tex (w3/create-texture! device #js {:size #js {:width w :height h}
                                                      :format "rgba8unorm"
                                                      :usage (bit-or (w3/texture-usage :texture-binding)
                                                                     (w3/texture-usage :copy-dst)
                                                                     (w3/texture-usage :render-attachment))})]
                 (w3/copy-external-image-to-texture! (w3/device-queue device)
                   #js {:source bitmap} #js {:texture tex} #js {:width w :height h})
                 tex)))))

(defn- f32 [xs] (js/Float32Array. (clj->js (vec xs))))

(defn upload-mesh!
  "`{:positions :normals :indices :uvs :morph-target-deltas :joints :weights}`
  (all optional except `:positions`/`:normals`/`:indices`; `:uvs` is a
  per-vertex `[u v]`, defaults to `[0 0]` when omitted — irrelevant either
  way against the default white texture; `:joints`/`:weights` are per-vertex
  `[j0 j1 j2 j3]`/`[w0 w1 w2 w3]`, omit both for an unskinned mesh;
  `:morph-target-deltas` is `[[[dx dy dz] ...] ...]`, one seq of per-vertex
  deltas per target, omit for no morphs) -> GPU buffer handles + counts.
  Storage buffers are always allocated (min size, if the mesh has none) since
  WebGPU rejects zero-byte buffers."
  [{:keys [device] :as context} {:keys [positions normals indices uvs morph-target-deltas joints weights] :as geometry}]
  (if (= :webgl2 (:backend context))
    (webgl/upload-mesh! context geometry)
  (let [vcount (count positions)
        has-skin? (and (seq joints) (seq weights))
        has-uv? (seq uvs)
        interleaved (js/Float32Array. (* vcount (/ VERTEX-STRIDE 4)))
        joints-view (js/Uint32Array. (.-buffer interleaved))]
    (dotimes [i vcount]
      (let [base (* i 16) ;; 16 = 64 bytes / 4
            p (nth positions i) n (nth normals i)
            uv (if has-uv? (nth uvs i) [0.0 0.0])
            j (if has-skin? (nth joints i) [0 0 0 0])
            w (if has-skin? (nth weights i) [1.0 0.0 0.0 0.0])]
        (.set interleaved (f32 p) base)
        (.set interleaved (f32 n) (+ base 3))
        (.set interleaved (f32 uv) (+ base 6))
        (.set joints-view (js/Uint32Array. (clj->js (vec j))) (+ base 8))
        (.set interleaved (f32 w) (+ base 12))))
    (let [mkbuf (fn [data usage]
                  (let [b (w3/create-buffer! device #js {:size (.-byteLength data)
                                                        :usage (bit-or usage (w3/buffer-usage :copy-dst))})]
                    (w3/write-buffer! (w3/device-queue device) b data) b))
          vbuf (mkbuf interleaved (w3/buffer-usage :vertex))
          ibuf (mkbuf (js/Uint32Array. (clj->js (vec indices))) (w3/buffer-usage :index))
          morph-count (count morph-target-deltas)
          morph-flat (if (pos? morph-count)
                       (f32 (mapcat (fn [target] (mapcat (fn [d] (conj (vec d) 0.0)) target)) morph-target-deltas))
                       (js/Float32Array. 4))
          morph-deltas-buf (mkbuf morph-flat (w3/buffer-usage :storage))
          morph-weights-buf (w3/create-buffer! device #js {:size (max 4 (* 4 (max 1 morph-count)))
                                                        :usage (bit-or (w3/buffer-usage :storage) (w3/buffer-usage :copy-dst))})
          joint-count (if has-skin? (inc (apply max 0 (mapcat identity joints))) 0)
          joint-matrices-buf (w3/create-buffer! device #js {:size (max 64 (* 64 (max 1 joint-count)))
                                                         :usage (bit-or (w3/buffer-usage :storage) (w3/buffer-usage :copy-dst))})
          ;; 240 bytes: historical 176-byte contract plus four aligned stylized vec4s.
          ;; pattern_kind(16) + pattern_params(16) + shade_kind/toon_threshold/
          ;; toon_smooth/_pad2(16) — see `draw!`'s `gdata`.
          gbuf (w3/create-buffer! device #js {:size 240 :usage (bit-or (w3/buffer-usage :uniform)
                                                                       (w3/buffer-usage :copy-dst))})]
      {:vbuf vbuf :ibuf ibuf :idx-count (count indices) :vertex-count vcount
       :submission-id (random-uuid)
       :morph-count morph-count :morph-deltas-buf morph-deltas-buf :morph-weights-buf morph-weights-buf
       :joint-count joint-count :joint-matrices-buf joint-matrices-buf
       :gbuf gbuf}))))

(defn draw!
  "Draw one mesh into `pass` (an already-begun GPURenderPassEncoder, so the
  caller controls the color/depth attachments and clear — this fn only sets
  pipeline/bind-group/buffers and issues the indexed draw). `mvp`: Float32Array
  (16, column-major). `color`: `[r g b]`. `morph-weights`: seq of f32, length
  `:morph-count` (ignored if 0). `joint-matrices`: seq of 16-float column-major
  mat4s, length `:joint-count` (ignored if 0).

  Optional trailing `opts` map:
  - `:color-b`/`:kind` (0|1|2|3)/`:params` `[x y z w]` — procedural fragment
    pattern (see the shader's own `pattern_t` doc comment). `:kind` defaults
    `0` (flat `color`, `color_b`/`params` unused) — the pre-pattern-work
    behaviour.
  - `:texture` — a real `GPUTexture` (from `upload-texture!`, already
    resolved via its Promise) to sample instead of `ctx`'s shared
    `:default-texture` (1x1 white — a no-op multiply, so omitting `:texture`
    draws exactly as before texture support existed).
  - `:shade-kind` (0|1)/`:toon-threshold`/`:toon-smooth` — 2-tone toon
    shading (see the shader's `toon_lit` doc comment). `:shade-kind`
    defaults `0` (the original continuous N.L lighting). Prefer the stable
    `:render-style` (`:photoreal`, `:stylized`, or the complete style-v1
    envelope) plus optional stylized profile fields; legacy `:shade-kind`
    remains supported. A canonical screen-space outline request fails closed:
    this mesh pipeline does not yet own an outline pass.
  Omitting `opts` entirely (every pre-`/loop`-maturity-pass call site) draws
  byte-identically to before any of this."
  ([ctx pass buffers mvp color morph-weights joint-matrices]
   (draw! ctx pass buffers mvp color morph-weights joint-matrices nil))
  ([{:keys [device pipe default-texture default-sampler]} pass
    {:keys [vbuf ibuf idx-count vertex-count morph-count morph-deltas-buf morph-weights-buf
            joint-count joint-matrices-buf gbuf]}
    mvp color morph-weights joint-matrices
    {:keys [color-b kind params texture sampler render-style shade-kind toon-threshold toon-smooth
            shade-color rim-color rim-intensity rim-power rim-lift specular-bands specular-threshold
            specular-smooth highlight-intensity metallic roughness uv-offset uv-scale
            submission-gdata submission-joint-buffer submission-bind]
     :or {color-b color kind 0 params [0.0 0.0 0.0 0.0]
          metallic 0.0 roughness 0.5
          uv-offset [0.0 0.0] uv-scale [1.0 1.0]}}]
   (let [profile-input (cond-> (cond
                                  (map? render-style) render-style
                                  (keyword? render-style) {:render-style render-style}
                                  :else {})
                         (some? toon-threshold) (assoc :toon-threshold toon-threshold)
                         (some? toon-smooth) (assoc :toon-smooth toon-smooth)
                         shade-color (assoc :shade-color shade-color)
                         rim-color (assoc :rim-color rim-color)
                         (some? rim-intensity) (assoc :rim-intensity rim-intensity)
                         (some? rim-power) (assoc :rim-power rim-power)
                         (some? rim-lift) (assoc :rim-lift rim-lift)
                         (some? specular-bands) (assoc :specular-bands specular-bands)
                         (some? specular-threshold) (assoc :specular-threshold specular-threshold)
                         (some? specular-smooth) (assoc :specular-smooth specular-smooth)
                         (some? highlight-intensity) (assoc :highlight-intensity highlight-intensity))
         style-uniforms (render-style/shader-uniforms profile-input)
         shade-kind (if (some? render-style) (:shade-kind style-uniforms) (or shade-kind 0))
         toon-threshold (or toon-threshold (:toon-threshold style-uniforms) 0.4)
         toon-smooth (or toon-smooth (:toon-smooth style-uniforms) 0.08)
         shade-color (or shade-color (:shade-color style-uniforms) [0.35 0.35 0.38])
         rim-color (or rim-color (:rim-color style-uniforms) [1.0 1.0 1.0])
         rim-intensity (or rim-intensity (:rim-intensity style-uniforms) 0.25)
         rim-power (or rim-power (:rim-power style-uniforms) 3.0)
         rim-lift (or rim-lift (:rim-lift style-uniforms) 0.0)
         specular-bands (or specular-bands (:specular-bands style-uniforms) 3)
         specular-threshold (or specular-threshold (:specular-threshold style-uniforms) 0.45)
         specular-smooth (or specular-smooth (:specular-smooth style-uniforms) 0.04)
         highlight-intensity (or highlight-intensity (:highlight-intensity style-uniforms) 0.35)
         q (w3/device-queue device)
         ;; 240 bytes / 4 = 60 floats: historical 44 plus four stylized vec4s.
         ;; counts+kind(4, u32 view) + params(4) + shade_kind/toon_threshold/
         ;; toon_smooth/_pad2(4, mixed u32/f32 view) + material(4) + uv_transform(4).
         gdata (or submission-gdata (js/Float32Array. 60))]
     (.set gdata mvp 0)
     (.set gdata (f32 (conj (vec color) 1.0)) 16)
     (.set gdata (f32 (conj (vec color-b) 1.0)) 20)
     (let [gview (js/Uint32Array. (.-buffer gdata))]
       (aset gview 24 vertex-count) (aset gview 25 morph-count) (aset gview 26 joint-count) (aset gview 27 kind)
       (aset gview 32 shade-kind))
     (.set gdata (f32 params) 28)
     (aset gdata 33 toon-threshold)
     (aset gdata 34 toon-smooth)
     (aset gdata 36 metallic)
     (aset gdata 37 roughness)
     (.set gdata (f32 (concat uv-offset uv-scale)) 40)
     (.set gdata (f32 (conj (vec shade-color) 1.0)) 44)
     (.set gdata (f32 (conj (vec rim-color) 1.0)) 48)
     (.set gdata (f32 [rim-intensity rim-power rim-lift specular-bands]) 52)
     (.set gdata (f32 [highlight-intensity specular-threshold 0.0 specular-smooth]) 56)
     (w3/write-buffer! q gbuf gdata)
    (when (pos? morph-count)
      (w3/write-buffer! q morph-weights-buf (f32 (take morph-count (concat morph-weights (repeat 0.0))))))
    (when (and (pos? joint-count) (nil? submission-joint-buffer))
      (w3/write-buffer! q joint-matrices-buf (js/Float32Array. (clj->js (vec (mapcat vec (take joint-count joint-matrices)))))))
    (let [joint-buffer (or submission-joint-buffer joint-matrices-buf)
          texture-view-source (or texture default-texture)
          bind (or submission-bind
                   (w3/create-bind-group! device
                 #js {:layout (w3/get-bind-group-layout pipe 0)
                      :entries #js [#js {:binding 0 :resource #js {:buffer gbuf}}
                                    #js {:binding 1 :resource #js {:buffer morph-deltas-buf}}
                                    #js {:binding 2 :resource #js {:buffer morph-weights-buf}}
                                    #js {:binding 3 :resource #js {:buffer joint-buffer}}
                                    #js {:binding 4 :resource (w3/create-view texture-view-source)}
                                    #js {:binding 5 :resource (or sampler default-sampler)}]}))]
      (w3/set-pipeline! pass pipe)
      (w3/set-bind-group! pass 0 bind)
      (w3/set-vertex-buffer! pass 0 vbuf)
      (w3/set-index-buffer! pass ibuf "uint32")
      (w3/draw-indexed! pass idx-count)))))

(defn init-canvas!
  "Initialize the canonical WebGPU mesh viewport. The app supplies only an
  HTML canvas; adapter/device/context/raw API ownership stays inside
  webgpu -> org-w3-webgpu. WebGL2 is selected only when WebGPU is absent or
  adapter/device initialization fails. Returns Promise<context>."
  [canvas]
  (let [fallback (fn []
                   (if-let [viewport (webgl/init-mesh-viewport! canvas)]
                     (js/Promise.resolve (assoc viewport :mesh-context viewport))
                     (js/Promise.reject (js/Error. "Neither WebGPU nor WebGL2 is available"))))]
    (if-not (w3/supported?)
      (fallback)
      (-> (w3/request-adapter!)
          (.then (fn [adapter]
                   (if adapter
                     (w3/request-device! adapter)
                     (js/Promise.reject (js/Error. "No WebGPU adapter available")))))
          (.then (fn [device]
                   (let [w (max 1 (.-clientWidth canvas)) h (max 1 (.-clientHeight canvas))
                         _ (set! (.-width canvas) w) _ (set! (.-height canvas) h)
                         ctx (w3/get-context canvas) fmt (w3/preferred-canvas-format)
                         depth (w3/create-texture! device #js {:size #js [w h] :format "depth24plus"
                                                               :usage (w3/texture-usage :render-attachment)})]
                     (w3/configure-context! ctx #js {:device device :format fmt :alphaMode "opaque"})
                     {:backend :webgpu :device device :queue (w3/device-queue device) :ctx ctx
                      :depth depth :mesh-context (init! device fmt) :width w :height h})))
          (.catch (fn [_] (fallback)))))))

(defn sync-canvas-size!
  "Match the drawing buffer to the canvas' CSS layout size and return the
  (possibly updated) viewport. init-canvas! samples clientWidth/clientHeight
  exactly once, so a canvas that is hidden at init time (e.g. a stage behind
  a non-default route: clientWidth 0 → 1×1 buffer) stays one stretched pixel
  forever without this. Call it whenever the canvas may have changed size —
  it is cheap when nothing changed and a no-op while the canvas is hidden.
  WebGPU recreates the depth attachment at the new size (the swap-chain
  texture follows canvas width/height automatically); WebGL2's default
  framebuffer resizes with the canvas, and every kami.webgl render pass sets
  gl.viewport from the viewport map's :width/:height each frame."
  [viewport canvas]
  (let [cw (.-clientWidth canvas) ch (.-clientHeight canvas)]
    (if (or (zero? cw) (zero? ch)
            (and (= cw (:width viewport)) (= ch (:height viewport))))
      viewport
      (do (set! (.-width canvas) cw)
          (set! (.-height canvas) ch)
          (if (= :webgpu (:backend viewport))
            (let [device (:device viewport)
                  depth (w3/create-texture! device
                          #js {:size #js [cw ch] :format "depth24plus"
                               :usage (w3/texture-usage :render-attachment)})]
              (when-let [old (:depth viewport)] (w3/destroy-texture! old))
              (assoc viewport :width cw :height ch :depth depth))
            (assoc viewport :width cw :height ch))))))

(defn render-frame!
  "Render one arbitrary mesh frame through the canonical W3C binding.
  `viewport` is from `init-canvas!`; `buffers` is from `upload-mesh!`."
  ([viewport buffers eye target color]
   (render-frame! viewport buffers eye target color nil))
  ([viewport buffers eye target color transform]
  (let [{:keys [device queue ctx depth mesh-context width height]} viewport
        vp (m4-mul (view-projection eye target (/ width height)) (model-matrix (or transform {})))]
    (if (= :webgl2 (:backend viewport))
      (webgl/render-mesh-frame! viewport buffers vp color)
  (let [
        encoder (w3/create-command-encoder! device #js {:label "kami.mesh.upload"})
        pass (w3/begin-render-pass!
              encoder
              #js {:colorAttachments
                   #js [#js {:view (w3/create-view (w3/current-texture ctx))
                             :loadOp "clear" :storeOp "store"
                             :clearValue #js {:r 0.035 :g 0.055 :b 0.10 :a 1}}]
                   :depthStencilAttachment
                   #js {:view (w3/create-view depth)
                        :depthLoadOp "clear" :depthStoreOp "store"
                        :depthClearValue 1}})]
    (draw! mesh-context pass buffers vp color [] [])
    (w3/end-pass! pass)
    (w3/submit! queue [(w3/finish! encoder)]))))))

(defn render-scene!
  "Render multiple arbitrary meshes in one frame and one render pass.
  Each draw is {:buffers upload-result :color [r g b] :transform optional-TRS}.
  This preserves depth between objects and is shared by WebGPU/WebGL2."
  [viewport draws eye target]
  (let [{:keys [device queue ctx depth mesh-context width height]} viewport
        projection (view-projection eye target (/ width height))
        prepared (mapv (fn [{:keys [buffers color transform material]}]
                         {:buffers buffers :color color :material material
                          :mvp (m4-mul projection (model-matrix (or transform {})))}) draws)]
    (if (= :webgl2 (:backend viewport))
      (webgl/render-mesh-scene! viewport prepared)
      (let [encoder (w3/create-command-encoder! device #js {:label "kami.mesh.upload"})
            pass (w3/begin-render-pass!
                  encoder
                  #js {:colorAttachments
                       #js [#js {:view (w3/create-view (w3/current-texture ctx))
                                 :loadOp "clear" :storeOp "store"
                                 :clearValue #js {:r 0.035 :g 0.055 :b 0.10 :a 1}}]
                       :depthStencilAttachment
                       #js {:view (w3/create-view depth) :depthLoadOp "clear"
                            :depthStoreOp "store" :depthClearValue 1}})]
        (doseq [{:keys [buffers color mvp material]} prepared]
          (draw! mesh-context pass buffers mvp color [] [] material))
        (w3/end-pass! pass)
        (w3/submit! queue [(w3/finish! encoder)])))))

(defn render-skinned-frame!
  "Render one skinned mesh with an ordered joint matrix palette. WebGPU uses
  the shader storage palette; WebGL2 delegates to its compatible skin path."
  ([viewport buffers eye target color joint-matrices]
   (render-skinned-frame! viewport buffers eye target color joint-matrices nil))
  ([viewport buffers eye target color joint-matrices transform]
  (let [{:keys [device queue ctx depth mesh-context width height]} viewport
        vp (m4-mul (view-projection eye target (/ width height)) (model-matrix (or transform {})))]
    (if (= :webgl2 (:backend viewport))
      (webgl/render-skinned-mesh-frame! viewport buffers vp color joint-matrices)
      (let [encoder (w3/create-command-encoder! device #js {:label "kami.mesh.upload"})
            pass (w3/begin-render-pass!
                  encoder #js {:colorAttachments #js [#js {:view (w3/create-view (w3/current-texture ctx))
                                                            :loadOp "clear" :storeOp "store"
                                                            :clearValue #js {:r 0.035 :g 0.055 :b 0.10 :a 1}}]
                               :depthStencilAttachment #js {:view (w3/create-view depth) :depthLoadOp "clear"
                                                           :depthStoreOp "store" :depthClearValue 1}})]
        (draw! mesh-context pass buffers vp color [] joint-matrices)
        (w3/end-pass! pass)
        (w3/submit! queue [(w3/finish! encoder)]))))))

(defn create-skinned-submission-cache
  "Create reusable GPU resource state for cached skinned overlay packets."
  ([] (create-skinned-submission-cache nil))
  ([device]
   (atom {:device device :destroyed? false :entities {} :draw-packets {}
          :lifecycle {:entity-evictions 0 :reset-count 0 :destroy-count 0
                      :destroyed-joint-buffers 0 :evicted-draw-packets 0
                      :device-mismatches 0}
          :evidence {:schema :kotoba.webgpu/skinned-submission-evidence-v1}})))

(defn- destroy-entity-state! [state]
  (when-let [buffer (:buffer state)] (w3/destroy-buffer! buffer))
  (boolean (:buffer state)))

(defn evict-skinned-entity!
  "Destroy one entity palette buffer and forget every bind packet referencing it."
  [cache entity-id]
  (let [{:keys [entities draw-packets]} @cache
        state (get entities entity-id)
        retained (into {} (remove (fn [[[id _ _ _] _]] (= id entity-id)) draw-packets))
        packet-count (- (count draw-packets) (count retained))]
    (when state (destroy-entity-state! state))
    (swap! cache (fn [c]
                   (-> c
                       (update :entities dissoc entity-id)
                       (assoc :draw-packets retained)
                       (update-in [:lifecycle :entity-evictions] (fnil inc 0))
                       (update-in [:lifecycle :destroyed-joint-buffers]
                                  (fnil + 0) (if state 1 0))
                       (update-in [:lifecycle :evicted-draw-packets] (fnil + 0) packet-count))))
    cache))

(defn reset-skinned-submission-cache!
  "Destroy all entity GPU buffers and packet references, retaining device ownership."
  [cache]
  (let [{:keys [entities draw-packets]} @cache]
    (doseq [[_ state] entities] (destroy-entity-state! state))
    (swap! cache (fn [c]
                   (-> c
                       (assoc :entities {} :draw-packets {})
                       (update-in [:lifecycle :reset-count] (fnil inc 0))
                       (update-in [:lifecycle :destroyed-joint-buffers]
                                  (fnil + 0) (count entities))
                       (update-in [:lifecycle :evicted-draw-packets]
                                  (fnil + 0) (count draw-packets))))))
  cache)

(defn destroy-skinned-submission-cache!
  "Release all owned GPU buffers and permanently invalidate this cache instance."
  [cache]
  (reset-skinned-submission-cache! cache)
  (swap! cache (fn [c] (-> c (assoc :device nil :destroyed? true)
                             (update-in [:lifecycle :destroy-count] (fnil inc 0)))))
  cache)

(defn- validate-cache-device! [cache device]
  (let [{owned :device destroyed? :destroyed?} @cache]
    (when destroyed?
      (throw (js/Error. "skinned submission cache has been destroyed")))
    (cond
      (nil? owned) (swap! cache assoc :device device)
      (not (identical? owned device))
      (do
        (reset-skinned-submission-cache! cache)
        (swap! cache (fn [c] (-> c (assoc :device nil)
                                   (update-in [:lifecycle :device-mismatches] (fnil inc 0)))))
        (throw (js/Error. "skinned submission cache device changed; resources cleared"))))
    cache))

(defn- fill-joint-data! [data palette joint-count]
  (dotimes [joint joint-count]
    (let [matrix (nth palette joint)]
      (dotimes [component 16]
        (aset data (+ (* joint 16) component) (nth matrix component)))))
  data)

(defn- ensure-entity-palette! [device cache entity-id joint-count]
  (let [required (max 1 joint-count)
        existing (get-in @cache [:entities entity-id])]
    (if (and existing (>= (:capacity existing) required))
      existing
      (let [buffer (w3/create-buffer! device #js {:size (* 64 required)
                                                   :usage (bit-or (w3/buffer-usage :storage)
                                                                  (w3/buffer-usage :copy-dst))})
            state {:buffer buffer :capacity required
                   :data (js/Float32Array. (* 16 required))}]
        (when-let [old (:buffer existing)] (w3/destroy-buffer! old))
        (swap! cache (fn [c]
                       (-> c
                           (assoc-in [:entities entity-id] state)
                           (update :draw-packets
                                   #(into {} (remove (fn [[[id _ _ _] _]] (= id entity-id)) %))))))
        state))))

(defn- cached-bind! [{:keys [device pipe default-texture default-sampler]} cache
                     entity-id buffers material joint-buffer]
  (let [{:keys [texture sampler]} material
        texture-source (or texture default-texture)
        sampler-source (or sampler default-sampler)
        key [entity-id (:submission-id buffers) texture-source sampler-source]
        cached (get-in @cache [:draw-packets key])]
    (if cached
      (do (swap! cache update-in [:evidence :bind-groups-reused] (fnil inc 0)) cached)
      (let [view (w3/create-view texture-source)
            bind (w3/create-bind-group!
                  device
                  #js {:layout (w3/get-bind-group-layout pipe 0)
                       :entries #js [#js {:binding 0 :resource #js {:buffer (:gbuf buffers)}}
                                     #js {:binding 1 :resource #js {:buffer (:morph-deltas-buf buffers)}}
                                     #js {:binding 2 :resource #js {:buffer (:morph-weights-buf buffers)}}
                                     #js {:binding 3 :resource #js {:buffer joint-buffer}}
                                     #js {:binding 4 :resource view}
                                     #js {:binding 5 :resource sampler-source}]})
            packet {:bind bind :gdata (js/Float32Array. 60) :texture-view view}]
        (swap! cache (fn [c] (-> c (assoc-in [:draw-packets key] packet)
                                  (update-in [:evidence :bind-groups-created] (fnil inc 0)))))
        packet))))

(defn prepare-skinned-submission-packets!
  "Upload one joint palette per entity for this frame and attach cached typed
   globals/bind groups to every compatible material-range draw. Matrices are
   always rewritten per call; changing animation is never cached across frames."
  [{:keys [device] :as mesh-context} cache draws]
  (validate-cache-device! cache device)
  (let [groups (group-by :entity-id draws)]
    (when (contains? groups nil)
      (throw (js/Error. "cached skinned draws require :entity-id provenance")))
    (swap! cache assoc :evidence {:schema :kotoba.webgpu/skinned-submission-evidence-v1
                                  :source-draw-count (count draws)
                                  :entity-count (count groups)
                                  :joint-palette-uploads 0
                                  :bind-groups-created 0 :bind-groups-reused 0})
    (let [entity-states
          (into {}
                (for [[entity-id entity-draws] groups
                      :let [palettes (mapv :joint-matrices entity-draws)
                            _ (when-not (apply = palettes)
                                (throw (js/Error. "one entity has conflicting joint palettes")))
                            palette (first palettes)
                            joint-count (count palette)
                            state (ensure-entity-palette! device cache entity-id joint-count)
                            _ (fill-joint-data! (:data state) palette joint-count)
                            _ (w3/write-buffer! (w3/device-queue device) (:buffer state) (:data state))
                            _ (swap! cache update-in [:evidence :joint-palette-uploads] inc)]]
                  [entity-id state]))
          prepared
          (mapv (fn [{:keys [entity-id buffers material] :as draw}]
                  (let [state (entity-states entity-id)
                        packet (cached-bind! mesh-context cache entity-id buffers (or material {}) (:buffer state))]
                    (update draw :material merge
                            {:submission-gdata (:gdata packet)
                             :submission-joint-buffer (:buffer state)
                             :submission-bind (:bind packet)})))
                draws)]
      (swap! cache update :evidence assoc
             :prepared-draw-count (count prepared)
             :joint-upload-reduction (- (count draws) (count groups))
             :provenance-preserved? (= (mapv :entity-id draws) (mapv :entity-id prepared)))
      prepared)))

(defn skinned-submission-evidence [cache]
  (let [{:keys [device destroyed? entities draw-packets lifecycle evidence]} @cache]
    (assoc evidence
           :cache-device-bound? (some? device)
           :cache-destroyed? destroyed?
           :resident-entity-count (count entities)
           :resident-draw-packet-count (count draw-packets)
           :lifecycle lifecycle)))

(declare encode-skinned-overlay!)

(defn encode-skinned-overlay-cached!
  "Cached overlay path: one palette upload per entity/frame, persistent texture
   views/bind groups, and persistent 60-float globals arrays per draw packet."
  [gpu-ctx mesh-context cache encoder attachments draws camera]
  (assoc (encode-skinned-overlay! gpu-ctx mesh-context encoder attachments
                                  (prepare-skinned-submission-packets! mesh-context cache draws)
                                  camera)
         :cache (skinned-submission-evidence cache)))

(defn encode-skinned-overlay!
  "Encode skinned meshes into the main renderer's graph-local world target.
   Called by `kami.webgpu/set-world-overlay!` before post-processing, therefore
   color, depth, camera, and resolution are exactly shared with world geometry."
  [gpu-ctx mesh-context encoder {:keys [color-view depth-view]} draws
   {:keys [eye target fov near far] :or {fov 60.0 near 0.5 far 4000.0}}]
  (when (seq draws)
    (when-not (and (= :webgpu (:backend gpu-ctx))
                   (identical? (:device gpu-ctx) (:device mesh-context)))
      (throw (js/Error. "skinned overlay requires the main WebGPU device")))
    (let [device (:device gpu-ctx)
          projection (m4-mul
                      (perspective (* fov (/ js/Math.PI 180.0))
                                   (/ (:w gpu-ctx) (:h gpu-ctx)) near far)
                      (look-at eye target [0 1 0]))
          pass (w3/begin-render-pass!
                encoder
                #js {:colorAttachments
                     #js [#js {:view color-view
                               :loadOp "load" :storeOp "store"}]
                     :depthStencilAttachment
                     #js {:view depth-view
                          :depthLoadOp "load" :depthStoreOp "store"}})]
      (doseq [{:keys [buffers transform color joint-matrices morph-weights material]} draws]
        (draw! mesh-context pass buffers
               (m4-mul projection (model-matrix (or transform {})))
               color (or morph-weights [])
               joint-matrices material))
      (w3/end-pass! pass)
      {:kind :skinned-overlay
       :draw-count (count draws)
       :entity-ids (vec (distinct (keep :entity-id draws)))
       :submitted-roles (vec (distinct (keep :role draws)))
       :projected-screen-bounds (vec (keep :projected-screen-bounds draws))
       :projected-screen-bounds-count (count (keep :projected-screen-bounds draws))
       :projected-screen-bounds-provenance :consumer-supplied
       :device-match? true})))

(defn render-skinned-scene!
  "Render multiple skinned parts in one depth-preserving frame on either
  backend. Each draw is {:buffers :joint-matrices :color :transform
  :morph-weights :material}. On WebGPU this is one clear + one render pass;
  on the WebGL2 fallback every part is CPU-skinned into its vertex buffer
  first (same math as webgl/render-skinned-mesh-frame!), then all parts go
  through one webgl/render-mesh-scene! call so depth is preserved across
  parts instead of the last part clearing the previous ones."
  [viewport draws eye target]
  (let [{:keys [device queue ctx depth mesh-context width height]} viewport
        projection (view-projection eye target (/ width height))
        prepared (mapv (fn [{:keys [transform screen-space?] :as draw}]
                         ;; :screen-space? draws carry clip-space vertices
                         ;; already (e.g. a full-viewport scene backdrop quad
                         ;; at far depth) — identity MVP, no camera transform.
                         (assoc draw :mvp (if screen-space?
                                            (model-matrix {})
                                            (m4-mul projection (model-matrix (or transform {}))))))
                       draws)]
    (if (= :webgl2 (:backend viewport))
      ;; The WebGL2 fallback's mesh path has no texture support on this
      ;; published surface; screen-space (textured backdrop) draws are
      ;; deliberately dropped there rather than drawn as flat rectangles.
      (let [gl (:gl viewport)
            prepared (vec (remove :screen-space? prepared))]
        (doseq [{:keys [buffers joint-matrices]} prepared
                :when (seq (:joints buffers))]
          (let [{:keys [positions normals joints weights vertex-buffer]} buffers
                skinned-positions (mapv #(webgl/skin-vector %1 %2 %3 joint-matrices false)
                                        positions joints weights)
                skinned-normals (mapv #(webgl/skin-vector %1 %2 %3 joint-matrices true)
                                      normals joints weights)
                vertices (js/Float32Array. (clj->js (mapcat concat skinned-positions skinned-normals)))]
            (.bindBuffer gl (.-ARRAY_BUFFER gl) vertex-buffer)
            (.bufferSubData gl (.-ARRAY_BUFFER gl) 0 vertices)))
        (webgl/render-mesh-scene! viewport prepared))
      (let [encoder (w3/create-command-encoder! device #js {:label "kami.mesh.upload"})
            pass (w3/begin-render-pass!
                  encoder #js {:colorAttachments #js [#js {:view (w3/create-view (w3/current-texture ctx))
                                                           :loadOp "clear" :storeOp "store"
                                                           :clearValue #js {:r 0.035 :g 0.055 :b 0.10 :a 1}}]
                               :depthStencilAttachment #js {:view (w3/create-view depth)
                                                            :depthLoadOp "clear" :depthStoreOp "store"
                                                            :depthClearValue 1}})]
        (doseq [{:keys [buffers color joint-matrices material mvp morph-weights]} prepared]
          (draw! mesh-context pass buffers mvp color (or morph-weights []) joint-matrices material))
        (w3/end-pass! pass)
        (w3/submit! queue [(w3/finish! encoder)])))))

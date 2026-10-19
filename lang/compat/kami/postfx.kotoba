(ns kami.postfx
  "L2 — post-processing shaders authored as Clojure data → WGSL source string.

  Phase 2.3 of the Rust→CLJ/WGSL migration (ADR-2607010930): moves the postfx
  passes (bloom/outline/CRT/vignette/pixelate) out of hand-authored Rust
  `include_str!` WGSL (`kami-webgpu-rs/src/post.wgsl`) into CLJC-authored WGSL
  data, so postfx chains are EDN-described and CLJC-authored (the 'what to
  record → EDN' principle; the wgpu recorder stays Rust).

  Every postfx pass is a fullscreen fragment pass: one oversized-triangle vertex
  shader feeds a UV, and the fragment shader samples an input texture and writes
  one RGBA color. The shader-as-data maps here go straight through `kami.wgsl-emit/emit`
  (which already supports `:vertex` + `:fragment` stages). No `@compute` is needed
  for postfx; this ns deliberately does NOT touch the emitter core (Phase 2.1).

  Each shader shares one bind group (group 0):
    @binding(0) texture_2d<f32>  tex   — the input HDR/LDR color target
    @binding(1) sampler          samp
    @binding(2) uniform<Params>  p     — a small vec4 pair (8 floats) of params

  The params uniform is a fixed-shape pair of vec4<f32> (matches the Rust-side
  `struct P { p0, p1 }` postfx params layout in post.wgsl) so the host can drive
  any postfx pass with the same bind-group layout. Per-shader semantics of p0/p1
  are documented on each shader below.

  The body sub-language understood by `kami.wgsl-emit/emit` is a small s-expression
  subset: `set!`, `let`, `vec2..4`/`mat3..4` constructors, binops (+,-,*,/),
  generic WGSL function calls (`(dot a b)` → `dot(a, b)`), and dotted symbols
  (`in.uv`, `p.p0.x` preserved). Binops lower to WGSL infix, so arithmetic is
  authored as s-expressions. The raw-string escape hatch (a string arg passes
  through `emit-expr` verbatim) is used only for constructs the subset can't
  model: u32 bit-ops + `@builtin(vertex_index)` math in the vertex stage, and
  `.rgb` swizzles on sampled textures in the fragment stage. This keeps the bulk
  of authoring in composable EDN while not pretending the subset is bigger than
  it is."
  (:require [kami.wgsl-emit :as wgsl]))

;; --- shared fullscreen-triangle vertex stage --------------------------------
;;
;; One oversized triangle covering the viewport, fed by `@builtin(vertex_index)`.
;; This is the same vs_full used by every pass in post.wgsl; authored once here
;; as data and reused by every postfx shader so the bind-group layout stays
;; identical. The bit math `(vi << 1u) & 2u` uses u32 literals, which the
;; s-expression subset doesn't model, so those lines use the raw-WGSL escape
;; hatch (strings pass through `emit-expr` verbatim).

(def ^:private fullscreen-vertex
  "Vertex stage shared by all postfx passes. `vi` is `@builtin(vertex_index)`; UV
  in [0,1] is derived from the triangle position; `clip` is the framebuffer
  coordinate. Returns the `:vertex` sub-map consumed by `kami.wgsl-emit/emit`."
  {:in  [[:vi :u32 {:builtin :vertex_index}]]
   :out [[:clip :vec4<f32> :builtin/position]
         [:uv   :vec2<f32> {:location 0}]]
   :body '[(let [x "(f32(((in.vi << 1u) & 2u)))"
                 y "(f32((in.vi & 2u)))"]
             (set! out.uv   (vec2 x y))
             (set! out.clip (vec4 (- (* x 2.0) 1.0)
                                  (- 1.0 (* y 2.0))
                                  0.0 1.0)))]})

(defn- postfx-bindings
  "The standard postfx bind group (group 0): sampled texture + sampler + a
  uniform params struct named `p`. Matches `struct P { p0, p1 }` in post.wgsl
  so the host can use one bind-group layout for every postfx pass."
  []
  [{:group 0 :binding 0 :var :uniform :name "tex"  :type :texture_2d<f32>}
   {:group 0 :binding 1 :var :uniform :name "samp" :type :sampler}
   {:group 0 :binding 2 :var :uniform :name "p"    :type :P}])

(def ^:private postfx-params-struct
  "Fixed-shape params uniform: two vec4<f32> slots (8 floats). The Rust side
  drives this with the same layout as `struct P` in post.wgsl."
  {:P [[:p0 :vec4<f32>]
       [:p1 :vec4<f32>]]})

;; --- per-shader specs --------------------------------------------------------

(defn vignette-shader
  "Vignette postfx: darken the image toward the corners.

  Params (p0):
    p0.x = strength  — 0 = no darkening, ~1 = strong corner falloff
    p0.y, p0.z, p0.w = (unused)

  Math: vig = clamp(1 - strength * 2 * dot(uv-0.5, uv-0.5), 0, 1); color *= vig.
  Returns a shader-as-data map suitable for `kami.wgsl-emit/emit`."
  []
  {:wgsl/name "postfx_vignette"
   :wgsl/bindings (postfx-bindings)
   :wgsl/structs postfx-params-struct
   :wgsl/vertex fullscreen-vertex
   :wgsl/fragment
   {:in  [[:uv :vec2<f32> {:location 0}]]
    :out [[:color :vec4<f32> {:location 0}]]
    :body '[(let [c   "textureSample(tex, samp, in.uv).rgb"   ; raw: .rgb swizzle
                  d   (- in.uv (vec2 0.5 0.5))
                  vig (clamp (- 1.0 (* p.p0.x (* 2.0 (dot d d))))
                             0.0 1.0)]
              (set! out.color (vec4 (* c vig) 1.0)))]}})

(defn pixelate-shader
  "Pixelate postfx: quantize UVs into a block grid and sample at the block
  center, producing a chunky low-res look.

  Params (p0):
    p0.x = block — block size in texels (e.g. 8.0 → 8×8 px blocks)
    p0.y = (unused)

  Math: snap uv to the block grid, sample at the block center in UV space.
  Returns a shader-as-data map suitable for `kami.wgsl-emit/emit`."
  []
  {:wgsl/name "postfx_pixelate"
   :wgsl/bindings (postfx-bindings)
   :wgsl/structs postfx-params-struct
   :wgsl/vertex fullscreen-vertex
   :wgsl/fragment
   {:in  [[:uv :vec2<f32> {:location 0}]]
    :out [[:color :vec4<f32> {:location 0}]]
    :body '[(let [block  p.p0.x
                  size   (vec2 "textureDimensions(tex, 0)")   ; raw: i32 mip level
                  pix    (/ size block)
                  blkIdx (floor (/ (* in.uv pix) block))
                  quv    (/ (* (+ blkIdx 0.5) block) pix)]
              (set! out.color (vec4 "textureSample(tex, samp, quv).rgb" 1.0)))]}}) ; raw: .rgb

(defn outline-shader
  "Outline postfx: simple edge detection via per-channel neighbor difference
  (a Sobel-ish variant that samples the 4-neighbors and sums absolute deltas).
  Brightens edges over the original color.

  Params (p0):
    p0.x = texel.x  — horizontal texel step (1/width)
    p0.y = texel.y  — vertical texel step   (1/height)
    p0.z = strength — edge gain multiplier

  Math: sample 4-neighbors, sum |center - neighbor|, add strength*edge to color.
  Returns a shader-as-data map suitable for `kami.wgsl-emit/emit`."
  []
  {:wgsl/name "postfx_outline"
   :wgsl/bindings (postfx-bindings)
   :wgsl/structs postfx-params-struct
   :wgsl/vertex fullscreen-vertex
   :wgsl/fragment
   {:in  [[:uv :vec2<f32> {:location 0}]]
    :out [[:color :vec4<f32> {:location 0}]]
    :body '[(let [texel p.p0.xy
                  oL    (+ in.uv (vec2 (* -1.0 texel.x) 0.0))
                  oR    (+ in.uv (vec2 texel.x 0.0))
                  oU    (+ in.uv (vec2 0.0 (* -1.0 texel.y)))
                  oD    (+ in.uv (vec2 0.0 texel.y))
                  c     "textureSample(tex, samp, in.uv).rgb"   ; raw: .rgb swizzle
                  cL    "textureSample(tex, samp, oL).rgb"
                  cR    "textureSample(tex, samp, oR).rgb"
                  cU    "textureSample(tex, samp, oU).rgb"
                  cD    "textureSample(tex, samp, oD).rgb"
                  edge  (* p.p0.z (+ (+ (abs (- c cL)) (abs (- c cR)))
                                     (+ (abs (- c cU)) (abs (- c cD)))))]
              (set! out.color (vec4 (+ c edge) 1.0)))]}})

;; --- public emit API ---------------------------------------------------------

(defn postfx-shader
  "Return the shader-as-data map for postfx `kind` (a keyword: `:vignette`,
  `:pixelate`, `:outline`). The map is suitable for `kami.wgsl-emit/emit`. Pure."
  [kind]
  (case kind
    :vignette (vignette-shader)
    :pixelate (pixelate-shader)
    :outline  (outline-shader)
    (throw (ex-info "kami.postfx/postfx-shader: unknown postfx kind"
                    {:kind kind
                     :known #{:vignette :pixelate :outline}}))))

(defn emit-postfx
  "Emit a postfx shader to a WGSL source string. `kind` is a keyword naming one of
  the built-in postfx passes; `opts` is reserved for future per-shader tuning
  (currently unused — params are runtime uniform, not compile-time). Pure."
  ([kind]
   (emit-postfx kind nil))
  ([kind _opts]
   (wgsl/emit (postfx-shader kind))))

(def postfx-kinds
  "The set of postfx kinds this namespace can author. Exposed for chain
  description / EDN validation by the host."
  #{:vignette :pixelate :outline})

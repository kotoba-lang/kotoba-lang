(ns kami.webgl
  "kami-webgl — a WebGL2 renderer driven by the SAME EDN as kami.webgpu, for browsers without
   WebGPU. It consumes the same render-IR (globals + instances) and the same render-graph (resolved
   through kami.gpu for the :webgl2 tier, which drops compute passes), running the GLSL ES 3.00
   shaders that `bb gen-glsl` transpiles from the one EDN shader (kami.wgsl → naga → GLSL).

   The naga GLSL convention this binds to: per-instance vertex inputs at `layout(location=0..)`,
   uniforms in a std140 block, the full-quad corners from gl_VertexID. So `(pick-backend)` selects
   WebGPU when navigator.gpu exists and falls back here otherwise — one EDN, two GPU runtimes."
  (:require [kami.gpu :as gpu]
            [kami.sprite-gpu :as sg]
            [kami.webgl.glsl :as glsl]))

;; ── backend selection ────────────────────────────────────────────────────────────────────────────
(defn webgpu-available? [] (boolean (and js/navigator (.-gpu js/navigator))))

(defn webgl2-context
  "A WebGL2 rendering context for the canvas (premultiplied alpha, antialias), or nil."
  [canvas]
  (.getContext canvas "webgl2" #js {:antialias true :premultipliedAlpha true}))

(defn pick-backend
  "The best available GPU backend for this browser: :webgpu if WebGPU is present, else :webgl2.
   Both consume the same render-IR; the caller routes to kami.webgpu or kami.webgl accordingly."
  []
  (if (webgpu-available?) :webgpu :webgl2))

(defn caps
  "The kami.gpu capability tier for a running WebGL2 context (no compute / no storage, instancing
   via ANGLE_instanced_arrays core in WebGL2)."
  [_gl]
  (gpu/caps-from-device :webgl2 {:compute false :storage false :instancing true}))

;; ── GLSL program compilation ───────────────────────────────────────────────────────────────────
(defn- compile-shader [gl kind src]
  (let [s (.createShader gl kind)]
    (.shaderSource gl s src)
    (.compileShader gl s)
    (when-not (.getShaderParameter gl s (.-COMPILE_STATUS gl))
      (throw (ex-info (str "GLSL compile error:\n" (.getShaderInfoLog gl s)) {:src src})))
    s))

(defn program
  "Compile + link a GLSL ES 3.00 program from vertex/fragment source (as produced by bb gen-glsl).
   Throws with the info log on failure."
  [gl vsrc fsrc]
  (let [p  (.createProgram gl)
        vs (compile-shader gl (.-VERTEX_SHADER gl) vsrc)
        fs (compile-shader gl (.-FRAGMENT_SHADER gl) fsrc)]
    (.attachShader gl p vs) (.attachShader gl p fs) (.linkProgram gl p)
    (when-not (.getProgramParameter gl p (.-LINK_STATUS gl))
      (throw (ex-info (str "GLSL link error:\n" (.getProgramInfoLog gl p)) {})))
    p))

;; ── 2D sprite pass: instanced SDF quads (the GPU-2D path, identical output to WebGPU) ───────────
;; instance layout = kami.sprite-gpu/pack-instances: 12 floats — ipos(2) isize(2) irot(1) ishape(1)
;; icolor(4) pad(2); 48-byte stride. Quad corners come from gl_VertexID (6 verts), no corner buffer.
(def ^:private F4 4)
(defn sprite-renderer
  "Build a 2D-sprite draw fn for this WebGL2 context from the generated GLSL (sprite.vert/.frag).
   The returned `(draw! quad-instances [w h])` packs + uploads the instances and issues one
   instanced draw — the whole 2D frame in a single call, rendering the SDF shapes on the GPU."
  [gl & [{:keys [vert frag] :or {vert glsl/sprite-vert frag glsl/sprite-frag}}]]
  (let [prog (program gl vert frag)
        vao  (.createVertexArray gl)
        ibuf (.createBuffer gl)
        ublk (.getUniformBlockIndex gl prog "U_block_0Vertex")
        ubuf (.createBuffer gl)]
    (.bindVertexArray gl vao)
    (.bindBuffer gl (.-ARRAY_BUFFER gl) ibuf)
    ;; per-instance attributes at locations 0..4 (naga _p2vs_locationN), divisor 1, stride 48
    (let [stride 48
          attrs [[0 2 0] [1 2 8] [2 1 16] [3 1 20] [4 4 24]]]   ;; [loc size byte-offset]
      (doseq [[loc n off] attrs]
        (.enableVertexAttribArray gl loc)
        (.vertexAttribPointer gl loc n (.-FLOAT gl) false stride off)
        (.vertexAttribDivisor gl loc 1)))
    (when (not= ublk (.-INVALID_INDEX gl)) (.uniformBlockBinding gl prog ublk 0))
    (.bindVertexArray gl nil)
    (fn draw! [quad-instances [w h]]
      (let [data (js/Float32Array. (clj->js (sg/pack-instances quad-instances)))
            n    (count quad-instances)]
        (.useProgram gl prog)
        (.bindVertexArray gl vao)
        (.bindBuffer gl (.-ARRAY_BUFFER gl) ibuf)
        (.bufferData gl (.-ARRAY_BUFFER gl) data (.-DYNAMIC_DRAW gl))
        ;; uniform block U = { viewport: vec2, _p0: vec2 }
        (.bindBuffer gl (.-UNIFORM_BUFFER gl) ubuf)
        (.bufferData gl (.-UNIFORM_BUFFER gl) (js/Float32Array. #js [w h 0 0]) (.-DYNAMIC_DRAW gl))
        (.bindBufferBase gl (.-UNIFORM_BUFFER gl) 0 ubuf)
        (.enable gl (.-BLEND gl))
        (.blendFunc gl (.-ONE gl) (.-ONE_MINUS_SRC_ALPHA gl))
        (.drawArraysInstanced gl (.-TRIANGLES gl) 0 6 n)   ;; 6 corner verts × n instances
        (.bindVertexArray gl nil)))))

;; ── frame entry: resolve the graph for WebGL2, run the runnable passes ─────────────────────────
(defn render-2d!
  "Render a 2D sprite frame on WebGL2: clear, then draw the quad instances (from
   kami.sprite-gpu/draw-ops->quads) via the sprite pass. The :sprites pass has no kami.gpu
   :requires, so it runs on this tier; compute passes in a richer graph are dropped by resolve."
  [gl {:keys [draw-sprites! clear]} quad-instances [w h]]
  (.viewport gl 0 0 w h)
  (let [[r g b a] (or clear [0.04 0.05 0.08 1.0])]
    (.clearColor gl r g b a) (.clear gl (.-COLOR_BUFFER_BIT gl)))
  (draw-sprites! quad-instances [w h]))

;; ── 3D lit + shadow pass (instanced meshes, depth-FBO shadow map) ──────────────────────────────
;; Mirrors kami.webgpu's two-pass lit path on the WebGL2 API. The matrix math (camera→vp, sun→
;; light_vp) is shared with WebGPU and computed by the caller, who hands us the packed G uniform
;; (60 f32: vp[16] sun_dir[4] sun_col[4] sky[4] light_vp[16] light_a..d[16]) — identical to the
;; native `gf` array, so the render is the same. We own the GLSL plumbing: programs, the depth
;; framebuffer, the mesh + instance buffers, and the shadow→main draw order.
;; mesh attrs: pos(loc0,vec3) normal(loc1,vec3), divisor 0. instance attrs: m0..m3(loc2-5,vec4)
;; color(loc6,vec4) material(loc7,vec4), divisor 1 — 24 f32 / instance, the kami.webgpu layout.
(def ^:private SHADOW-FS "#version 300 es\nprecision highp float;\nvoid main() {}")   ;; depth-only

(defn scene-renderer
  "Build a whole-2D-frame draw fn from the embedded GLSL: a sky gradient pass (fullscreen triangle)
   then the instanced sprite/text quad pass. (render! {:sky {:zenith :ground} :quads [...]} [w h])
   draws the full kami.scene2d frame on the GPU — the Canvas2D draw-2d! replacement."
  [gl]
  (let [sky-prog (program gl glsl/sky-vert glsl/sky-frag)
        sky-ub   (.createBuffer gl)
        sky-blk  (.getUniformBlockIndex gl sky-prog "SU_block_0Fragment")
        draw!    (sprite-renderer gl)]
    (when (not= sky-blk (.-INVALID_INDEX gl)) (.uniformBlockBinding gl sky-prog sky-blk 0))
    (fn render-frame! [{:keys [sky quads]} [w h]]
      (.viewport gl 0 0 w h)
      ;; sky gradient pass
      (.useProgram gl sky-prog)
      (.bindBuffer gl (.-UNIFORM_BUFFER gl) sky-ub)
      (.bufferData gl (.-UNIFORM_BUFFER gl)
                   (js/Float32Array. (clj->js (concat (:zenith sky) (:ground sky)))) (.-DYNAMIC_DRAW gl))
      (.bindBufferBase gl (.-UNIFORM_BUFFER gl) 0 sky-ub)
      (.disable gl (.-BLEND gl))
      (.drawArrays gl (.-TRIANGLES gl) 0 3)
      ;; sprites + text, blended over the sky
      (draw! quads [w h]))))

(defn- mesh-vao [gl vbuf ibuf inst]
  (let [vao (.createVertexArray gl)]
    (.bindVertexArray gl vao)
    (.bindBuffer gl (.-ARRAY_BUFFER gl) vbuf)                       ;; interleaved pos(3)+normal(3) = 24B
    (doseq [[loc off] [[0 0] [1 12]]]
      (.enableVertexAttribArray gl loc) (.vertexAttribPointer gl loc 3 (.-FLOAT gl) false 24 off))
    (.bindBuffer gl (.-ELEMENT_ARRAY_BUFFER gl) ibuf)
    (.bindBuffer gl (.-ARRAY_BUFFER gl) inst)                       ;; 24 f32 / instance, stride 96
    (doseq [[loc off] [[2 0] [3 16] [4 32] [5 48] [6 64] [7 80]]]
      (.enableVertexAttribArray gl loc)
      (.vertexAttribPointer gl loc 4 (.-FLOAT gl) false 96 off)
      (.vertexAttribDivisor gl loc 1))
    (.bindVertexArray gl nil) vao))

(defn- depth-fbo [gl size]
  (let [tex (.createTexture gl) fbo (.createFramebuffer gl)]
    (.bindTexture gl (.-TEXTURE_2D gl) tex)
    (.texImage2D gl (.-TEXTURE_2D gl) 0 (.-DEPTH_COMPONENT32F gl) size size 0
                 (.-DEPTH_COMPONENT gl) (.-FLOAT gl) nil)
    (doseq [[k v] [[(.-TEXTURE_MIN_FILTER gl) (.-LINEAR gl)] [(.-TEXTURE_MAG_FILTER gl) (.-LINEAR gl)]
                   [(.-TEXTURE_COMPARE_MODE gl) (.-COMPARE_REF_TO_TEXTURE gl)]
                   [(.-TEXTURE_COMPARE_FUNC gl) (.-LEQUAL gl)]]]
      (.texParameteri gl (.-TEXTURE_2D gl) k v))
    (.bindFramebuffer gl (.-FRAMEBUFFER gl) fbo)
    (.framebufferTexture2D gl (.-FRAMEBUFFER gl) (.-DEPTH_ATTACHMENT gl) (.-TEXTURE_2D gl) tex 0)
    (.bindFramebuffer gl (.-FRAMEBUFFER gl) nil)
    {:tex tex :fbo fbo :size size}))

(defn lit-renderer
  "Build the 3D lit+shadow draw for this WebGL2 context. `shaders` {:lit {:vert :frag} :shadow {:vert}}
   are the GLSL ES 3.00 from bb gen-glsl. Returns (draw! packed-G mesh instances [w h]) where mesh is
   {:vbuf :ibuf :count}, instances a Float32Array (24 f32/instance) with metadata :count on the map
   passed as the 3rd-arg wrapper {:buf :count}."
  [gl shaders & [{:keys [shadow-size] :or {shadow-size 2048}}]]
  (let [lit-p (program gl (get-in shaders [:lit :vert]) (get-in shaders [:lit :frag]))
        shd-p (program gl (get-in shaders [:shadow :vert]) SHADOW-FS)
        sm    (depth-fbo gl shadow-size)
        gbuf  (.createBuffer gl)
        ibuf  (.createBuffer gl)
        bind-g (fn [prog n] (let [i (.getUniformBlockIndex gl prog n)]
                              (when (not= i (.-INVALID_INDEX gl)) (.uniformBlockBinding gl prog i 0))))]
    (bind-g lit-p "G_block_0Vertex") (bind-g lit-p "G_block_0Fragment") (bind-g shd-p "G_block_0Vertex")
    (fn draw! [packed-G mesh instances [w h]]
      (.bindBuffer gl (.-UNIFORM_BUFFER gl) gbuf)
      (.bufferData gl (.-UNIFORM_BUFFER gl) packed-G (.-DYNAMIC_DRAW gl))
      (.bindBufferBase gl (.-UNIFORM_BUFFER gl) 0 gbuf)
      (.bindBuffer gl (.-ARRAY_BUFFER gl) ibuf)
      (.bufferData gl (.-ARRAY_BUFFER gl) (:buf instances) (.-DYNAMIC_DRAW gl))
      (let [vao (mesh-vao gl (:vbuf mesh) (:ibuf mesh) ibuf)
            n   (:count instances)]
        (.enable gl (.-DEPTH_TEST gl))
        ;; pass 1 — depth into the shadow map from the sun's POV
        (.bindFramebuffer gl (.-FRAMEBUFFER gl) (:fbo sm))
        (.viewport gl 0 0 (:size sm) (:size sm)) (.clear gl (.-DEPTH_BUFFER_BIT gl))
        (.useProgram gl shd-p) (.bindVertexArray gl vao)
        (.drawElementsInstanced gl (.-TRIANGLES gl) (:count mesh) (.-UNSIGNED_SHORT gl) 0 n)
        ;; pass 2 — main, sampling the shadow map
        (.bindFramebuffer gl (.-FRAMEBUFFER gl) nil)
        (.viewport gl 0 0 w h) (.clear gl (bit-or (.-COLOR_BUFFER_BIT gl) (.-DEPTH_BUFFER_BIT gl)))
        (.useProgram gl lit-p)
        (.activeTexture gl (.-TEXTURE0 gl)) (.bindTexture gl (.-TEXTURE_2D gl) (:tex sm))
        (.uniform1i gl (.getUniformLocation gl lit-p "_group_0_binding_1_fs") 0)
        (.drawElementsInstanced gl (.-TRIANGLES gl) (:count mesh) (.-UNSIGNED_SHORT gl) 0 n)
        (.bindVertexArray gl nil)))))

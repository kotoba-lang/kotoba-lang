(ns kami.rt
  "L2 — ray tracing authored as EDN data → a backend-neutral ray-tracing IR.
  Same split as the rest of the SDK (ARCHITECTURE.md §7): clj is the brain that
  *describes* the trace (acceleration structure, integrator, sampler, camera,
  output), and a per-platform *arm* executes it. `emit` lowers the IR to the
  target's ray-tracing API — WebGPU ray-query WGSL, Metal RT (MPSRayIntersector),
  Vulkan VK_KHR_ray_tracing, DXR, or a console/engine bridge — none of which is
  re-implemented here. The same EDN recipe drives every backend; the executor is
  chosen by the host, exactly like the raster render-IR is consumed by kami-render.

  EDN recipe shape (everything optional except :rt/name):

    {:rt/name       \"gi\"
     :rt/accel      {:kind :bvh :builder :sah :refit :per-frame}
     :rt/integrator {:kind :path :max-bounces 4 :rr-start 2 :spp 8 :clamp 10.0}
     :rt/sampler    {:kind :sobol :seed 0}
     :rt/camera     {:fov 60.0 :aperture 0.0 :focus-dist 5.0}
     :rt/output     {:width 1280 :height 720 :format :rgba16f :denoise :svgf}}

  `pipeline` normalizes a recipe into a canonical IR (defaults filled, passes
  derived, deterministic) — the golden-test / record-replay surface. A tiny pure
  CPU intersector (`cpu-trace`) is included so the IR's *semantics* are testable
  without a GPU; it is a reference oracle, never the shipping path."
  (:require [kami.math :as m]
            [kami.wgsl-emit :as wgsl]))

;; --- helpers ----------------------------------------------------------------

(defn- fnum
  "Format a number as a WGSL f32 literal (integers get a trailing `.0`)."
  [n]
  (let [d (double n)]
    (if (== d (Math/floor d)) (str (long d) ".0") (str d))))

;; --- vocabulary -------------------------------------------------------------

(def accel-kinds      #{:bvh :lbvh :kd-tree})
(def integrator-kinds #{:path :ao :whitted :primary})
(def sampler-kinds    #{:sobol :halton :random :stratified})
(def output-formats   #{:rgba8 :rgba16f :rgba32f})
(def denoisers        #{:none :svgf :atrous :oidn})

(def default-accel      {:kind :bvh :builder :sah :refit :per-frame})
(def default-integrator {:kind :path :max-bounces 4 :rr-start 2 :spp 8 :clamp 10.0})
(def default-sampler    {:kind :sobol :seed 0})
(def default-camera     {:fov 60.0 :aperture 0.0 :focus-dist 5.0})
(def default-output     {:width 1280 :height 720 :format :rgba16f :denoise :svgf})

;; --- validation -------------------------------------------------------------

(defn valid?
  "Throw with a precise reason if `recipe` is malformed; return true otherwise."
  [recipe]
  ;; A recipe may be partial (defaults are merged later in `pipeline`); only
  ;; reject a field that is *present* and wrong, never a missing one.
  (let [{:keys [rt/name rt/accel rt/integrator rt/sampler rt/output]} recipe]
    (when-not (string? name)
      (throw (ex-info "rt: :rt/name must be a string" {:recipe recipe})))
    (when (and (:kind accel) (not (accel-kinds (:kind accel))))
      (throw (ex-info "rt: unknown accel kind" {:kind (:kind accel) :known accel-kinds})))
    (when (and (:kind integrator) (not (integrator-kinds (:kind integrator))))
      (throw (ex-info "rt: unknown integrator kind" {:kind (:kind integrator) :known integrator-kinds})))
    (when (neg? (:max-bounces integrator 0))
      (throw (ex-info "rt: :max-bounces must be >= 0" {:integrator integrator})))
    (when (< (:spp integrator 1) 1)
      (throw (ex-info "rt: :spp must be >= 1" {:integrator integrator})))
    (when (and (:kind sampler) (not (sampler-kinds (:kind sampler))))
      (throw (ex-info "rt: unknown sampler kind" {:kind (:kind sampler) :known sampler-kinds})))
    (when (and (:format output) (not (output-formats (:format output))))
      (throw (ex-info "rt: unknown output format" {:format (:format output) :known output-formats})))
    (when (and (:denoise output) (not (denoisers (:denoise output))))
      (throw (ex-info "rt: unknown denoiser" {:denoise (:denoise output) :known denoisers})))
    true))

;; --- normalization → canonical IR ------------------------------------------

(defn- derive-passes
  "Derive the ordered pass list from the integrator + output. Deterministic.
  primary → trace (path bounces) → [denoise] → present."
  [integrator output]
  (let [trace-pass {:pass/id :trace :pass/kind (:kind integrator)
                    :pass/bounces (:max-bounces integrator)
                    :pass/spp (:spp integrator)}
        denoise (:denoise output)]
    (cond-> [{:pass/id :primary :pass/kind :gbuffer}
             trace-pass]
      (and denoise (not= denoise :none)) (conj {:pass/id :denoise :pass/kind denoise})
      :always (conj {:pass/id :present :pass/kind :present}))))

(defn pipeline
  "Normalize an EDN recipe into the canonical ray-tracing IR (defaults filled,
  passes derived). Pure and serializable."
  [recipe]
  (valid? recipe)
  (let [accel      (merge default-accel      (:rt/accel recipe))
        integrator (merge default-integrator (:rt/integrator recipe))
        sampler    (merge default-sampler    (:rt/sampler recipe))
        camera     (merge default-camera     (:rt/camera recipe))
        output     (merge default-output     (:rt/output recipe))]
    {:rt/name       (:rt/name recipe)
     :rt/accel      accel
     :rt/integrator integrator
     :rt/sampler    sampler
     :rt/camera     camera
     :rt/output     output
     :rt/passes     (derive-passes integrator output)}))

;; --- backend capability matrix ---------------------------------------------

(def targets
  "Per-platform ray-tracing executors. :status is how kami reaches each:
     :emit     — clj emits the shader/source here (WebGPU ray-query WGSL)
     :delegate — clj emits an IR plan; the native backend owns the RT API
     :nda      — console RT API is under NDA; plan only, impl out of tree."
  {:wgsl   {:api :webgpu-ray-query :status :emit     :notes "WebGPU ray-query (EXT) + WGSL LBVH fallback"}
   :metal  {:api :metal-rt         :status :delegate :notes "MPSRayIntersector / Metal ray tracing (iOS/macOS)"}
   :vulkan {:api :khr-ray-tracing  :status :delegate :notes "VK_KHR_ray_tracing_pipeline (Android/desktop)"}
   :dx12   {:api :dxr              :status :delegate :notes "DirectX Raytracing (Windows)"}
   :ps5    {:api :agc-rt           :status :nda      :notes "PS5 AGC ray tracing — NDA, plan only"}
   :switch {:api :nvn-rt           :status :nda      :notes "Switch NVN — NDA, plan only"}
   :unity  {:api :hdrp-dxr         :status :delegate :notes "Unity HDRP ray tracing bridge"}
   :unreal {:api :lumen            :status :delegate :notes "Unreal Lumen / hardware RT bridge"}})

;; --- WGSL ray-query emission (the one :emit backend) ------------------------

(defn- bindings
  "Bind-group layout for the trace compute pass: scene TLAS, camera/sampler
  uniforms, and the storage output image."
  []
  [{:group 0 :binding 0 :var :acceleration-structure :name "tlas"   :type :acceleration_structure}
   {:group 0 :binding 1 :var :uniform                 :name "u"      :type :RtGlobals}
   {:group 0 :binding 2 :var :storage-rw              :name "out"    :type "array<vec4<f32>>"}])

(defn emit-wgsl
  "Emit a WGSL ray-query compute shader from the IR. Integrator parameters
  (bounces / spp / clamp / seed) are baked as override constants so the same IR
  recompiles per quality preset. Uses kami.wgsl-emit for struct emission and a raw
  WGSL body (the escape hatch kami.wgsl-emit documents for forms outside its subset)."
  [ir]
  (let [{:keys [rt/integrator rt/sampler]} ir
        bounces (:max-bounces integrator)
        spp     (:spp integrator)
        clamp   (:clamp integrator)
        seed    (:seed sampler)
        structs (wgsl/emit-struct "RtGlobals"
                                  [[:inv_view_proj :mat4x4<f32>]
                                   [:cam_pos :vec3<f32>]
                                   [:frame :u32]
                                   [:width :u32]
                                   [:height :u32]])]
    (str "// kami.rt — generated WGSL ray-query trace for recipe \"" (:rt/name ir) "\"\n"
         "// integrator=" (name (:kind integrator)) " bounces=" bounces
         " spp=" spp " sampler=" (name (:kind sampler)) "\n"
         "enable chromium_experimental_ray_query;\n\n"
         structs "\n\n"
         "override RT_MAX_BOUNCES: u32 = " bounces "u;\n"
         "override RT_SPP: u32 = " spp "u;\n"
         "override RT_CLAMP: f32 = " (fnum clamp) ";\n"
         "override RT_SEED: u32 = " seed "u;\n\n"
         "@group(0) @binding(0) var tlas: acceleration_structure;\n"
         "@group(0) @binding(1) var<uniform> u: RtGlobals;\n"
         "@group(0) @binding(2) var<storage, read_write> out_color: array<vec4<f32>>;\n\n"
         "@compute @workgroup_size(8, 8, 1)\n"
         "fn trace(@builtin(global_invocation_id) gid: vec3<u32>) {\n"
         "  if (gid.x >= u.width || gid.y >= u.height) { return; }\n"
         "  let idx = gid.y * u.width + gid.x;\n"
         "  var radiance = vec3<f32>(0.0);\n"
         "  for (var s: u32 = 0u; s < RT_SPP; s = s + 1u) {\n"
         "    var ray = primary_ray(gid.xy, s);\n"
         "    var throughput = vec3<f32>(1.0);\n"
         "    for (var b: u32 = 0u; b <= RT_MAX_BOUNCES; b = b + 1u) {\n"
         "      var rq: ray_query;\n"
         "      rayQueryInitialize(&rq, tlas, ray);\n"
         "      rayQueryProceed(&rq);\n"
         "      let hit = rayQueryGetCommittedIntersection(&rq);\n"
         "      if (hit.kind == RAY_QUERY_INTERSECTION_NONE) {\n"
         "        radiance = radiance + throughput * sky(ray.dir);\n"
         "        break;\n"
         "      }\n"
         "      radiance = radiance + throughput * emission(hit);\n"
         "      throughput = throughput * bsdf_sample(hit, &ray, s, b);\n"
         "      if (max(throughput.x, max(throughput.y, throughput.z)) < 1e-4) { break; }\n"
         "    }\n"
         "  }\n"
         "  radiance = min(radiance / f32(RT_SPP), vec3<f32>(RT_CLAMP));\n"
         "  out_color[idx] = vec4<f32>(radiance, 1.0);\n"
         "}\n")))

;; --- backend lowering -------------------------------------------------------

(defmulti emit
  "Lower the ray-tracing IR (from `pipeline`) to a backend descriptor. Dispatch
  on backend keyword (see `targets`)."
  (fn [backend _ir & _opts] backend))

(defmethod emit :wgsl [_ ir & _]
  {:backend :wgsl
   :api :webgpu-ray-query
   :bindings (bindings)
   :entry "trace"
   :workgroup [8 8 1]
   :wgsl (emit-wgsl ir)
   :passes (:rt/passes ir)})

(defmethod emit :default [backend ir & _]
  (let [tgt (get targets backend)]
    (when-not tgt
      (throw (ex-info "rt/emit: unknown backend" {:backend backend :known (keys targets)})))
    ;; delegate / nda backends: a structured plan the native side executes.
    {:backend backend
     :api (:api tgt)
     :status (:status tgt)
     :notes (:notes tgt)
     :delegate true
     :recipe (:rt/name ir)
     :accel (:rt/accel ir)
     :integrator (:rt/integrator ir)
     :passes (:rt/passes ir)}))

;; --- CPU reference intersector (test oracle, not the ship path) -------------

(defn intersect-sphere
  "Analytic ray↔sphere. Ray {:o [..] :d [..]} (d unit), sphere {:c [..] :r r}.
  Returns nearest positive t (front hit) or nil."
  [{:keys [o d]} {:keys [c r]}]
  (let [oc (m/v- o c)
        b  (m/dot oc d)
        cc (- (m/dot oc oc) (* r r))
        disc (- (* b b) cc)]
    (when (>= disc 0.0)
      (let [sq (Math/sqrt disc)
            t  (- (- b) sq)]
        (cond
          (> t 1e-4) t
          (> (+ (- b) sq) 1e-4) (+ (- b) sq)
          :else nil)))))

(defn cpu-trace
  "Reference oracle: nearest hit of `ray` against `spheres`
  ([{:c [..] :r r :id ...} ...]). Returns {:t :id :point} or nil. Deterministic;
  validates that an IR description corresponds to executable trace semantics."
  [ray spheres]
  (reduce
   (fn [best s]
     (if-let [t (intersect-sphere ray s)]
       (if (or (nil? best) (< t (:t best)))
         {:t t :id (:id s) :point (m/v+ (:o ray) (m/v* t (:d ray)))}
         best)
       best))
   nil
   spheres))

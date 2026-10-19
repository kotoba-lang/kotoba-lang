(ns kotoba.render.raytrace
  "RT compute dispatch uniform data. Ported from `kami-render/src/raytrace.rs`.

   `RayTracePipeline` (wgpu compute pipeline + BVH storage-buffer upload +
   `rt_bvh_compute.wgsl` dispatch) is host-adapter GPU code and is NOT
   ported — only the pure `rt-globals` uniform constructor (the CPU-side
   data the pipeline uploads) is portable.")

(def hit-stride
  "Bytes per output hit record: vec4<f32> = (t, tri_id, bary_u, bary_v)."
  16)

(defn rt-globals
  "Camera + framebuffer dimensions uniform (matches WGSL `RtGlobals`).
   `inv-view-proj` is a flat 16-float column-major matrix; `cam-pos` is
   `[x y z]`."
  [inv-view-proj cam-pos width height]
  {:inv-view-proj inv-view-proj
   :cam-pos (conj (vec cam-pos) 1.0)
   :dims [width height 0 0]})

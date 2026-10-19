(ns kami.sky
  "Sky / background as a GPU gradient pass — the vertical zenith→ground wash that draw-2d! drew with a
   Canvas2D linear gradient, as a fullscreen kami.wgsl shader. One EDN source → WGSL (WebGPU/native) +
   GLSL (WebGL2 via naga). A fullscreen triangle, the fragment lerps the two colours by screen y. .cljc"
  (:require [kami.wgsl :as w]))

(defn gradient-shader []
  (w/shader
   (w/struct* :SU [[:zenith [:vec4 :f32]] [:ground [:vec4 :f32]]])
   (w/binding* {:group 0 :binding 0 :space :uniform} :u :SU)
   (w/struct* :VO [[:clip [:vec4 :f32] {:builtin :position}]
                   [:uv [:vec2 :f32] {:location 0}]])
   (w/func :vs {:stage :vertex
                :params [[:vid :u32 {:builtin :vertex-index}]]
                :ret :VO}
           "var p = array<vec2<f32>, 3>(vec2<f32>(-1.0, -3.0), vec2<f32>(-1.0, 1.0), vec2<f32>(3.0, 1.0))"
           [:let :q "p[vid]"]
           [:decl :o :VO]
           [:set :o.clip [:vec4 :q.x :q.y 0.0 1.0]]
           [:set :o.uv [:* [:+ :q 1.0] 0.5]]   ;; clip → 0..1
           [:return :o])
   (w/func :fs {:stage :fragment :params [[:i :VO]] :ret [:loc 0 [:vec4 :f32]]}
           ;; uv.y 0 = top of clip → zenith; 1 = bottom → ground
           [:return [:mix :u.zenith :u.ground :i.uv.y]])))

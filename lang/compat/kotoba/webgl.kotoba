(ns kotoba.webgl
  "Facade re-exporting `kami.webgl` (SSoT in this package, ADR-2607102200 addendum 6).

   Historical consumers require `kotoba.webgl`; the WebGL2 executor lives in
   `kami.webgl` so apps and webgpu share one fallback path."
  (:require [kami.webgl :as impl]))

(def webgpu-available? impl/webgpu-available?)
(def webgl2-context     impl/webgl2-context)
(def pick-backend       impl/pick-backend)
(def caps               impl/caps)
(def program            impl/program)
(def sprite-renderer    impl/sprite-renderer)
(def render-2d!         impl/render-2d!)
(def scene-renderer     impl/scene-renderer)
(def lit-renderer       impl/lit-renderer)
(def init-mesh-viewport! impl/init-mesh-viewport!)
(def upload-mesh!        impl/upload-mesh!)
(def render-mesh-scene!  impl/render-mesh-scene!)
(def render-mesh-frame!  impl/render-mesh-frame!)
(def render-skinned-mesh-frame! impl/render-skinned-mesh-frame!)
(def skin-vector impl/skin-vector)

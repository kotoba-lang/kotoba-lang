(ns kotoba.wgsl
  "Facade over `kami.wgsl` (SSoT in this package, ADR-2607102200).

   Historical consumers require `kotoba.wgsl`; the implementation lives in
   `kami.wgsl` so webgpu and the rest of the render stack share one compiler."
  (:require [kami.wgsl :as k]))

;; Re-export the public surface used by sprite-gpu / shaders / sky / render-shaders.
(def expr k/expr)
(def stmt k/stmt)
(def func k/func)
(def struct* k/struct*)
(def binding* k/binding*)
(def shader k/shader)

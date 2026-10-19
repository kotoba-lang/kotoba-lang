(ns kotoba.render.quality (:require [kotoba.render.post-process :as post] [kotoba.render.shadow :as shadow]))
(def profiles {:mobile {:shadow :low :post-process :low :lod-bias 0.65 :max-visible-instances 20000 :max-visible-triangles 3000000} :balanced {:shadow :medium :post-process :medium :lod-bias 1.0 :max-visible-instances 60000 :max-visible-triangles 10000000} :high {:shadow :high :post-process :high :lod-bias 1.25 :max-visible-instances 120000 :max-visible-triangles 25000000} :cinematic {:shadow :ultra :post-process :ultra :lod-bias 1.5 :max-visible-instances 250000 :max-visible-triangles 60000000}})
(defn render-plan ([profile] (render-plan profile {})) ([profile overrides]
  (let [base (get profiles profile)]
    (when-not base (throw (ex-info "unknown render quality profile" {:profile profile})))
    (let [settings (merge base overrides)] {:schema :kotoba.render/quality-v1 :profile profile :settings settings :shadow (shadow/plan {:quality (:shadow settings)}) :post-process (post/plan {:quality (:post-process settings)}) :lod {:bias (:lod-bias settings) :max-visible-instances (:max-visible-instances settings) :max-visible-triangles (:max-visible-triangles settings)}}))))

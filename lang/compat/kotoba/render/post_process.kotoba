(ns kotoba.render.post-process "Portable HDR post-processing frame graphs.")
(def quality-presets {:off [] :low [:tone-map :fxaa] :medium [:bloom :tone-map :fxaa] :high [:ssao :bloom :tone-map :taa :color-grade] :ultra [:ssao :ssr :bloom :depth-of-field :motion-blur :tone-map :taa :color-grade]})
(def pass-defaults {:ssao {:radius 0.5 :bias 0.025 :samples 16 :scale 0.5} :ssr {:max-steps 48 :thickness 0.2 :scale 0.5} :bloom {:threshold 1.0 :knee 0.5 :levels 5 :intensity 0.08} :depth-of-field {:focus-distance 10.0 :aperture 2.8 :max-blur 8.0} :motion-blur {:samples 8 :shutter-angle 180.0} :tone-map {:operator :aces :exposure 1.0} :taa {:feedback 0.9 :jitter :halton-2-3} :fxaa {:quality :medium} :color-grade {:lut-size 32 :intensity 1.0}})
(defn plan [{:keys [quality overrides] :or {quality :high overrides {}}}]
  (let [passes (get quality-presets quality)]
    (when-not passes (throw (ex-info "unknown post-process quality" {:quality quality})))
    {:quality quality :input-format (if (= quality :off) :swapchain :rgba16float) :output :swapchain
     :passes (mapv (fn [index pass] {:index index :kind pass :params (merge (get pass-defaults pass) (get overrides pass))}) (range) passes)}))

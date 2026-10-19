(ns kotoba.cad.viewport
  "Kami's canonical browser rendering path: CLJS authoring data → kami.webgpu
   render-IR → generated Kami WGSL → browser WebGPU. No third-party renderer."
  (:require [reagent.core :as r]
            [kami.webgpu :as gpu]
            [kami.webgpu.ir :as ir]))

(defn- scene-pos [{:keys [x y]}] [(- (/ x 5.0) 10.0) 0.0 (- 10.0 (/ y 5.0))])

(defn- node->instance [{:keys [kind color] :as node}]
  (let [[x _ z] (scene-pos node)
        material (case kind
                   :water {:metallic 0.15 :roughness 0.12}
                   :light {:emissive 1.8 :roughness 0.2}
                   :building {:roughness 0.72}
                   {:roughness 0.88})]
    (merge (ir/instance [x 0.0 z] color
                        (case kind :building [4.4 3.1] :vegetation [1.4 3.8]
                                   :water [5.4 0.08] :landscape [7.5 0.12] :light [0.35 0.35] [1 1])
                        :yaw (case kind :landscape -0.22 :building 0.2 0.0))
           material
           {:geo (case kind :vegetation :sphere :water :plane :landscape :box :box)})))

(defn- camera [mode]
  (case mode
    :top {:eye [0 42 0.1] :target [0 0 0]}
    :walk {:eye [8 2.2 15] :target [0 1 0]}
    {:eye [20 16 24] :target [0 1 0]}))

(defn scene->render-ir [{:keys [scene time weather] :as props}]
  (let [camera-mode (:camera props)
        {:keys [eye target]} (camera camera-mode)
        sky (case weather
              :night (ir/sky [0.015 0.03 0.08] [-0.4 -0.8 -0.3] [0.25 0.32 0.5])
              :rain (ir/sky [0.22 0.30 0.36] [-0.5 -0.7 -0.2] [0.56 0.62 0.68])
              :overcast (ir/sky [0.46 0.57 0.61] [-0.4 -0.8 -0.3] [0.72 0.74 0.72])
              (ir/sky [0.48 0.72 0.82] [-0.4 -0.86 -0.3] [1.0 0.83 0.56]))]
    (-> (ir/render-ir sky (mapv node->instance scene) eye target)
        (assoc-in [:globals :fov] (if (= camera-mode :walk) 70 52))
        (assoc-in [:globals :lighting] {:ambient [0.18 0.22 0.16]
                                        :sun-diffuse (+ 0.45 (/ time 48.0))})
        (assoc-in [:globals :shadow] {:extent 75.0 :distance 120.0}))))

(def scene-viewport
  (let [canvas (atom nil)
        context (atom nil)]
    (r/create-class
     {:display-name "kami-wgsl-viewport"
      :component-did-mount
      (fn [this]
        (let [props (second (r/argv this))]
          (-> (gpu/init! @canvas {:geometry {:plane {:type :plane :w 1 :d 1}}})
              (.then (fn [ctx] (reset! context ctx) (gpu/draw! ctx (scene->render-ir props))))
              (.catch (fn [error] (aset (.-dataset @canvas) "kamiError" (str error)))))))
      :component-did-update
      (fn [this _]
        (when-let [ctx @context]
          (gpu/draw! ctx (scene->render-ir (second (r/argv this))))))
      :reagent-render
      (fn [_]
        [:canvas.viewport-canvas {:ref #(reset! canvas %)
                                 :aria-label "Kami WGSL WebGPU scene viewport"}])})))

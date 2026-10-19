(ns kotoba.render.atmosphere
  "Portable, data-driven atmosphere and analytic cloud contract.")

(def defaults
  {:schema :kotoba.render/atmosphere-v1
   :enabled? true
   :zenith [0.035 0.09 0.20]
   :horizon [0.43 0.58 0.72]
   :sun-color [1.0 0.78 0.53]
   :sun-direction [-0.52 -0.72 -0.34]
   :rayleigh 1.15
   :mie 0.32
   :sun-disc 0.018
   :clouds {:coverage 0.42 :density 0.68 :scale 3.2
            :altitude 0.58 :softness 0.16 :seed 41.0
            :color [1.0 0.96 0.90]}})

(defn- clamp [x lo hi] (max lo (min hi x)))
(defn- color3 [x fallback]
  (if (and (sequential? x) (= 3 (count x)) (every? number? x))
    (mapv #(clamp (double %) 0.0 16.0) x)
    fallback))
(defn- direction3 [x fallback]
  (if (and (sequential? x) (= 3 (count x)) (every? number? x))
    (let [v (mapv double x)
          l (Math/sqrt (reduce + (map #(* % %) v)))]
      (if (> l 1.0e-6) (mapv #(/ % l) v) fallback))
    fallback))

(defn atmosphere
  "Normalize an authored atmosphere map into the stable v1 GPU contract.
   Unknown keys are discarded so serialized evidence remains deterministic."
  [authored]
  (let [a (merge defaults (or authored {}))
        c (merge (:clouds defaults) (:clouds authored))]
    {:schema :kotoba.render/atmosphere-v1
     :enabled? (not= false (:enabled? a))
     :zenith (color3 (:zenith a) (:zenith defaults))
     :horizon (color3 (:horizon a) (:horizon defaults))
     :sun-color (color3 (:sun-color a) (:sun-color defaults))
     :sun-direction (direction3 (:sun-direction a) (:sun-direction defaults))
     :rayleigh (clamp (double (:rayleigh a)) 0.0 8.0)
     :mie (clamp (double (:mie a)) 0.0 4.0)
     :sun-disc (clamp (double (:sun-disc a)) 0.001 0.2)
     :clouds {:coverage (clamp (double (:coverage c)) 0.0 1.0)
              :density (clamp (double (:density c)) 0.0 2.0)
              :scale (clamp (double (:scale c)) 0.25 32.0)
              :altitude (clamp (double (:altitude c)) 0.0 1.0)
              :softness (clamp (double (:softness c)) 0.01 0.5)
              :seed (double (:seed c))
              :color (color3 (:color c) (get-in defaults [:clouds :color]))}}))

(defn uniform-values
  "The exact 32-float/std140-compatible order consumed by the fullscreen shader."
  [authored]
  (let [{:keys [enabled? zenith horizon sun-color sun-direction rayleigh mie sun-disc clouds]}
        (atmosphere authored)]
    (vec (concat zenith [(if enabled? 1.0 0.0)]
                 horizon [rayleigh]
                 sun-color [mie]
                 sun-direction [sun-disc]
                 [(:coverage clouds) (:density clouds) (:scale clouds) (:altitude clouds)]
                 (:color clouds) [(:softness clouds)]
                 [(:seed clouds) 0.0 0.0 0.0]
                 [0.0 0.0 0.0 0.0]))))

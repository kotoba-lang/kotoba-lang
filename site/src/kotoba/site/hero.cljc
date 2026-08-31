(ns kotoba.site.hero
  "Build-time geometry for the kotoba-lang.org hero canvas.

  Pure functions and one deterministic byte table. The same inputs produce the
  same point table on every machine and every run, so the committed page bytes
  never drift. The runtime engine is a small inline script in site/generate.cljs
  (WebGPU -> WebGL2 -> static SVG fallback); nothing here runs in the browser
  and no third-party runtime dependency is introduced.

  Geometry: one Kotoba-K stroke arc (720 samples with a radius and density
  envelope), a 12-claw crown fanned across the top gap, and a 24-point core.
  Coordinates are unit-space [0,1]; each renderer maps them to its viewport.
  Camera drift (slow sway), point size scaling, and the alpha envelope live in
  the inline engine in site/generate.cljs; their amplitudes are defined here
  so engine and geometry stay one unit.")

(def pi 3.141592653589793)

(def stroke-count 720)
(def claw-count 12)
(def claw-samples 8)
(def core-count 24)
(def point-count (+ stroke-count (* claw-count claw-samples) core-count))

(def arc-gap-radians 0.8)
(def ring-radius 0.36)
(def stroke-radius-min 0.028)
(def stroke-radius-max 0.060)
(def claw-base-radius 0.295)
(def claw-tip-radius 0.455)
(def claw-shear-radians 0.16)
(def claw-radius 0.022)
(def claw-density 0.55)
(def core-radius-max 0.075)
(def core-point-radius 0.020)
(def hero-fps 45)
(def hero-duration-seconds 46.0)
(def drift-amplitude-x 0.022)
(def drift-amplitude-y 0.016)
(def drift-freq-x 0.42)
(def drift-freq-y 0.31)
(def alpha-max 0.30)

(defn- sin* [x] #?(:clj (Math/sin (double x)) :cljs (Math/sin x)))
(defn- cos* [x] #?(:clj (Math/cos (double x)) :cljs (Math/cos x)))
(defn- sqrt* [x] #?(:clj (Math/sqrt (double x)) :cljs (Math/sqrt x)))

(defn- q8 [v]
  (int (Math/round (* 255.0 (max 0.0 (min 1.0 (double v)))))))

(defn arc-angle
  "Stroke parameter t in [0,1] -> angle in radians. The arc starts just
  clockwise of the top gap and sweeps a full turn to just counter-clockwise
  of it, leaving a gap of arc-gap-radians centered at the top."
  [t]
  (+ (* 0.5 pi) (/ arc-gap-radians 2)
     (* t (- (* 2 pi) arc-gap-radians))))

(defn stroke-point
  "One sample of the K stroke arc. Radius and density taper at both ends and
  peak mid-arc."
  [i]
  (let [t (/ i (dec stroke-count))
        a (arc-angle t)
        env (sin* (* pi t))
        r (+ stroke-radius-min (* (- stroke-radius-max stroke-radius-min) env))
        density (let [d (+ 0.35 (* 0.65 env))] (* d d))]
    {:x (+ 0.5 (* ring-radius (cos* a)))
     :y (+ 0.5 (* ring-radius (sin* a)))
     :r r
     :density density
     :t t}))

(defn claw-point
  "Claw j (of claw-count) sampled at k (of claw-samples). Claws fan across
  the top gap; the base sits at claw-base-radius sheared by claw-shear-radians
  and the tip reaches claw-tip-radius."
  [j k]
  (let [u (/ j (dec claw-count))
        a (- (+ (* 0.5 pi) (/ arc-gap-radians 2)) (* u arc-gap-radians))
        s (/ k (dec claw-samples))
        radius (+ claw-base-radius (* (- claw-tip-radius claw-base-radius) s))]
    {:x (+ 0.5 (* radius (cos* a)))
     :y (+ 0.5 (* radius (sin* a)))
     :r claw-radius
     :density (* claw-density (- 1.0 (* 0.35 s)))
     :t nil}))

(defn core-point
  "Core sample k: a sunflower fill of the inner disc."
  [k]
  (let [a (* 2 pi (/ (+ k 0.5) core-count))
        radius (* core-radius-max (sqrt* (/ (+ k 0.5) core-count)))]
    {:x (+ 0.5 (* radius (cos* a)))
     :y (+ 0.5 (* radius (sin* a)))
     :r core-point-radius
     :density 1.0
     :t nil}))

(defn all-points
  "The full table in committed order: stroke arc, then claws, then core.
  Renderers branch on the index ranges, so this order is part of the
  contract with the inline engine."
  []
  (concat (map stroke-point (range stroke-count))
          (for [j (range claw-count) k (range claw-samples)] (claw-point j k))
          (map core-point (range core-count))))

(defn stroke-samples
  "n evenly-indexed stroke points for the static SVG decoration."
  [n]
  (let [step (max 1 (quot stroke-count n))]
    (into [] (comp (filter #(zero? (mod % step))) (map stroke-point))
          (range stroke-count))))

(defn- quantized-vec [{:keys [x y r density]}]
  [(q8 x) (q8 y) (q8 r) (q8 density)])

(defn quantized-bytes
  "point-count x 4 bytes: x, y, point radius, density, each quantized to 8
  bits. This is the exact byte payload the inline engine decodes; hashing it
  pins the page's hero geometry."
  []
  (let [vs (vec (mapcat quantized-vec (all-points)))]
    #?(:clj (byte-array vs)
       :cljs (let [arr (js/Uint8Array. (count vs))]
               (doseq [[i b] (map-indexed vector vs)]
                 (aset arr i b))
               arr))))

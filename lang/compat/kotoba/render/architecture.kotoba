(ns kotoba.render.architecture
  "Portable, asset-free architectural detail kits.

   Parts are ordinary render-IR primitives with normalized PBR material roles.  A
   host only has to translate `:offset` by a terrain-grounded origin and merge its
   scene palette.  The three details are deliberately bounded so samples can spend
   geometry on hero landmarks without multiplying every background building.")

(def details #{:high :medium :low})
(def variants #{:depot :habitat})

(def default-palette
  {:wall    {:color [0.34 0.29 0.24] :metallic 0.03 :roughness 0.84}
   :roof    {:color [0.12 0.16 0.21] :metallic 0.58 :roughness 0.34}
   :trim    {:color [0.52 0.45 0.34] :metallic 0.18 :roughness 0.54}
   :window  {:color [0.10 0.42 0.58] :metallic 0.12 :roughness 0.16 :emissive 0.34}
   :door    {:color [0.16 0.19 0.22] :metallic 0.42 :roughness 0.42}
   :utility {:color [0.18 0.22 0.25] :metallic 0.64 :roughness 0.31}})

(defn- part [role offset size]
  {:role role :offset offset :size size :geo :box :triangles 12})

(defn- windows
  [width depth height columns rows sides]
  (let [body-height (* height 0.82)
        ww (min (* width 0.19) 1.35)
        wh (min (* body-height 0.17) 1.35)
        x-step (/ (* width 0.72) (max 1 (dec columns)))
        y-step (/ (* body-height 0.52) (max 1 (dec rows)))
        xs (if (= columns 1) [0.0]
               (mapv #(+ (* -0.36 width) (* % x-step)) (range columns)))
        ys (if (= rows 1) [(* body-height 0.42)]
               (mapv #(+ (* body-height 0.22) (* % y-step)) (range rows)))
        front-back (for [z [(- (+ (/ depth 2.0) 0.035)) (+ (/ depth 2.0) 0.035)]
                         x xs y ys]
                     (part :window [x y z] [ww wh 0.07]))
        side-count (max 1 (dec columns))
        zs (if (= side-count 1) [0.0]
               (mapv #(+ (* -0.30 depth)
                         (* % (/ (* depth 0.60) (dec side-count))))
                     (range side-count)))
        left-right (when sides
                     (for [x [(- (+ (/ width 2.0) 0.035)) (+ (/ width 2.0) 0.035)]
                           z zs y ys]
                       (part :window [x y z] [0.07 wh ww])))]
    (vec (concat front-back left-right))))

(defn building-parts
  "Expand one authored building into renderer-neutral detail parts.

   `:offset` uses a ground-based Y coordinate (not a centre coordinate), matching
   the common instance contract. `:role` selects a palette entry; `:material` is
   included as a portable default and may be replaced by the consuming scene."
  [{:keys [variant width depth height palette]
    :or {variant :depot width 10.0 depth 7.0 height 6.0 palette {}}}
   detail]
  (when-not (variants variant)
    (throw (ex-info "unsupported architecture variant"
                    {:variant variant :supported variants})))
  (when-not (details detail)
    (throw (ex-info "unsupported architecture detail"
                    {:detail detail :supported details})))
  (when-not (and (number? width) (pos? width)
                 (number? depth) (pos? depth)
                 (number? height) (pos? height))
    (throw (ex-info "architecture dimensions must be positive"
                    {:width width :depth depth :height height})))
  (let [body-height (* height (if (= variant :depot) 0.78 0.86))
        roof-height (- height body-height)
        roof-overhang (if (= variant :depot) 1.08 1.04)
        shell [(part :wall [0.0 0.0 0.0] [width body-height depth])
               (part :roof [0.0 body-height 0.0]
                     [(* width roof-overhang) roof-height (* depth roof-overhang)])]
        medium (concat
                shell
                [(part :door [0.0 0.0 (+ (/ depth 2.0) 0.045)]
                       [(* width 0.18) (* body-height 0.48) 0.09])
                 (part :trim [0.0 (* body-height 0.62) (+ (/ depth 2.0) 0.055)]
                       [(* width 0.90) (* height 0.055) 0.11])]
                (windows width depth height (if (= variant :depot) 3 2) 1 false))
        high (concat
              shell
              [(part :door [0.0 0.0 (+ (/ depth 2.0) 0.055)]
                     [(* width 0.18) (* body-height 0.48) 0.11])
               (part :trim [0.0 (* body-height 0.62) (+ (/ depth 2.0) 0.065)]
                     [(* width 0.92) (* height 0.052) 0.13])
               (part :trim [0.0 (- body-height (* height 0.08)) 0.0]
                     [(* width 1.04) (* height 0.045) (* depth 1.04)])
               (part :utility [(* width -0.27) height 0.0]
                     [(* width 0.10) (* height 0.14) (* depth 0.12)])
               (part :utility [(* width 0.24) height (* depth -0.18)]
                     [(* width 0.16) (* height 0.09) (* depth 0.18)])]
              (windows width depth height (if (= variant :depot) 3 2) 2 true))
        selected (case detail :low shell :medium medium :high high)
        materials (merge default-palette palette)]
    (mapv (fn [index p]
            (assoc p
                   :part/index index
                   :material (get materials (:role p))))
          (range) selected)))

(defn budget
  "Stable draw/triangle accounting for a generated kit."
  [parts]
  {:draws (count parts)
   :triangles (reduce + (map #(or (:triangles %) 12) parts))
   :roles (frequencies (map :role parts))})

(ns kotoba.cam.toolpath
  "Toolpath generation: CAM operations, segment types, and job execution.
   Ported from kami-cam (Rust) `src/toolpath.rs`.

   A `CamOperation` is a plain tagged map `{:op :pocket|:drill|... ...}`:
     {:op :face-mill  :tool-id :depth-of-cut :stepover :feed-rate :spindle-rpm}
     {:op :pocket     :tool-id :depth :stepover :strategy :feed-rate :spindle-rpm
                       :pocket-min :pocket-max}     ; corners are vec3
     {:op :contour    :tool-id :depth :side :feed-rate :spindle-rpm}
     {:op :drill      :tool-id :depth :peck-depth :feed-rate :spindle-rpm :holes}
     {:op :surface-3d :tool-id :stepover :strategy :feed-rate :spindle-rpm}
     {:op :turn       :tool-id :depth-of-cut :feed-rate :spindle-rpm}

   `:pocket` (zigzag), `:drill` (peck cycles), `:face-mill` (single-pass
   raster over the stock's top face) and `:contour` (offset following of a
   caller-supplied CONVEX polygon `:profile`, `:side` :inside/:outside/
   :on-line) are actually simulated. `:surface-3d` and `:turn` still
   generate a placeholder rapid move to the tool-change point — real 3D
   surface finishing needs mesh height-field sampling and turning is a
   different (2-axis polar) paradigm; both are future work, not rushed
   here. `:contour` on a concave/self-intersecting profile is also not
   attempted (general polygon offsetting needs self-intersection
   resolution this doesn't implement) — see convex-ccw?."
  (:require [kotoba.cam.vec3 :as vec3]
            [kotoba.cam.stock :as stock]
            [kotoba.cam.util :as util]))

(def pocket-strategies #{:zigzag :spiral :trochoidal-peel})
(def surface-strategies #{:raster :spiral :waterline :pencil})
(def contour-sides #{:inside :outside :on-line})
(def segment-types #{:rapid :linear :arc-cw :arc-ccw})

(defn segment
  "One segment of a generated toolpath."
  [{:keys [segment-type start end feed-rate center tool-id]}]
  {:segment-type segment-type
   :start start
   :end end
   :feed-rate (double (or feed-rate 0.0))
   :center center
   :tool-id tool-id})

(defn- last-end
  "The end position of the last segment, or origin if empty."
  [segments]
  (if (seq segments) (:end (peek segments)) vec3/zero))

;; ---------------------------------------------------------------------
;; :pocket — zigzag pocket clearing
;; ---------------------------------------------------------------------

(defn- gen-pocket
  [job {:keys [tool-id depth stepover feed-rate pocket-min pocket-max]} segments0]
  (let [tool (get (:tool-library job) tool-id)
        tool-radius (if tool (/ (:diameter tool) 2.0) 0.0)
        effective-stepover (if (> stepover 0.0) stepover tool-radius)
        x-min (+ (:x pocket-min) tool-radius)
        x-max (- (:x pocket-max) tool-radius)
        y-min (+ (:y pocket-min) tool-radius)
        y-max (- (:y pocket-max) tool-radius)
        z-top (:z pocket-min)
        z-bottom (- z-top depth)
        safe-z (+ z-top (:safe-height job))
        ;; Simple constant depth-of-cut = stepover for now; a real
        ;; implementation would use axial DOC (ported verbatim from Rust).
        layer-doc (min effective-stepover depth)
        num-layers (long (util/ceil (/ depth layer-doc)))]
    (loop [layer 0 segments segments0]
      (if (>= layer num-layers)
        segments
        (let [z (max (- z-top (* (+ layer 1.0) layer-doc)) z-bottom)
              first-start (vec3/v3 x-min y-min safe-z)
              segments (if (seq segments)
                         (conj segments
                               (segment {:segment-type :rapid
                                         :start (last-end segments)
                                         :end (vec3/v3 x-min y-min safe-z)
                                         :tool-id tool-id}))
                         segments)
              segments (conj segments
                             (segment {:segment-type :rapid
                                       :start first-start
                                       :end (vec3/v3 x-min y-min z)
                                       :tool-id tool-id}))
              segments (loop [y y-min forward true segments segments]
                         (if (> y y-max)
                           segments
                           (let [[sx ex] (if forward [x-min x-max] [x-max x-min])
                                 start (vec3/v3 sx y z)
                                 end-pt (vec3/v3 ex y z)
                                 prev (last-end segments)
                                 segments (if (> (vec3/length (vec3/sub prev start)) 1e-6)
                                            (conj segments
                                                  (segment {:segment-type :rapid
                                                            :start prev :end start
                                                            :tool-id tool-id}))
                                            segments)
                                 segments (conj segments
                                                (segment {:segment-type :linear
                                                          :start start :end end-pt
                                                          :feed-rate feed-rate
                                                          :tool-id tool-id}))]
                             (recur (+ y effective-stepover) (not forward) segments))))
              prev (last-end segments)
              segments (conj segments
                             (segment {:segment-type :rapid
                                       :start prev
                                       :end (vec3/v3 (:x prev) (:y prev) safe-z)
                                       :tool-id tool-id}))]
          (recur (inc layer) segments))))))

;; ---------------------------------------------------------------------
;; :drill — peck-drill cycles
;; ---------------------------------------------------------------------

(defn- gen-drill
  [job {:keys [tool-id depth peck-depth feed-rate holes]} segments0]
  (let [safe-z (:safe-height job)]
    (reduce
     (fn [segments hole]
       (let [top (vec3/v3 (:x hole) (:y hole) safe-z)
             prev (last-end segments)
             segments (conj segments
                            (segment {:segment-type :rapid :start prev :end top
                                      :tool-id tool-id}))
             z-bottom (- (:z hole) depth)]
         (loop [z (:z hole) segments segments]
           (if (<= z z-bottom)
             segments
             (let [target-z (max (- z peck-depth) z-bottom)
                   segments (conj segments
                                  (segment {:segment-type :linear
                                            :start (vec3/v3 (:x hole) (:y hole) z)
                                            :end (vec3/v3 (:x hole) (:y hole) target-z)
                                            :feed-rate feed-rate
                                            :tool-id tool-id}))
                   segments (conj segments
                                  (segment {:segment-type :rapid
                                            :start (vec3/v3 (:x hole) (:y hole) target-z)
                                            :end top
                                            :tool-id tool-id}))]
               (recur target-z segments))))))
     segments0
     holes)))

;; ---------------------------------------------------------------------
;; :face-mill — single-pass raster over the stock's top face
;; ---------------------------------------------------------------------

(defn- gen-face-mill
  "Rasters the whole stock's top XY footprint at one Z depth
  (`:depth-of-cut` below the stock's top face, derived from
  `(:stock job)` — face-milling covers the workpiece, not an arbitrary
  caller-specified region like :pocket does). Same zigzag/retract
  structure as gen-pocket, single layer."
  [job {:keys [tool-id depth-of-cut stepover feed-rate]} segments0]
  (let [tool (get (:tool-library job) tool-id)
        tool-radius (if tool (/ (:diameter tool) 2.0) 0.0)
        effective-stepover (if (and stepover (pos? stepover)) stepover (max tool-radius 1.0))
        {:keys [origin] :as job-stock} (:stock job)
        dims (stock/dimensions job-stock)
        x-min (- (:x origin) tool-radius)
        x-max (+ (:x origin) (:x dims) tool-radius)
        y-min (:y origin)
        y-max (+ (:y origin) (:y dims))
        z-top (+ (:z origin) (:z dims))
        z (- z-top depth-of-cut)
        safe-z (+ z-top (:safe-height job))
        first-start (vec3/v3 x-min y-min safe-z)
        segments (if (seq segments0)
                   (conj segments0 (segment {:segment-type :rapid :start (last-end segments0)
                                             :end first-start :tool-id tool-id}))
                   segments0)
        segments (conj segments (segment {:segment-type :rapid :start first-start
                                          :end (vec3/v3 x-min y-min z) :tool-id tool-id}))
        segments (loop [y y-min forward true segments segments]
                   (if (> y y-max)
                     segments
                     (let [[sx ex] (if forward [x-min x-max] [x-max x-min])
                           start (vec3/v3 sx y z) end-pt (vec3/v3 ex y z)
                           prev (last-end segments)
                           segments (if (> (vec3/length (vec3/sub prev start)) 1e-6)
                                      (conj segments (segment {:segment-type :rapid :start prev
                                                               :end start :tool-id tool-id}))
                                      segments)
                           segments (conj segments (segment {:segment-type :linear :start start :end end-pt
                                                             :feed-rate feed-rate :tool-id tool-id}))]
                       (recur (+ y effective-stepover) (not forward) segments))))
        prev (last-end segments)]
    (conj segments (segment {:segment-type :rapid :start prev
                             :end (vec3/v3 (:x prev) (:y prev) safe-z) :tool-id tool-id}))))

;; ---------------------------------------------------------------------
;; :contour — offset following of a convex polygon profile
;; ---------------------------------------------------------------------

(defn convex-ccw?
  "True if `pts` (vec3, Z ignored) form a convex, counter-clockwise
  polygon — the only profile shape gen-contour offsets correctly (a
  concave polygon's straight per-edge offset can self-intersect, which
  this namespace doesn't attempt to resolve)."
  [pts]
  (let [n (count pts)
        cross (fn [a b c]
                (- (* (- (:x b) (:x a)) (- (:y c) (:y a)))
                   (* (- (:y b) (:y a)) (- (:x c) (:x a)))))]
    (and (>= n 3)
         (every? pos? (for [i (range n)]
                        (cross (nth pts i) (nth pts (mod (inc i) n)) (nth pts (mod (+ i 2) n))))))))

(defn- edge-outward-normal-2d
  "Outward unit normal (as [nx ny]) of edge a->b of a CCW polygon —
  rotate the edge direction -90° (clockwise)."
  [a b]
  (let [dx (- (:x b) (:x a)) dy (- (:y b) (:y a))
        len (vec3/length (vec3/v3 dx dy 0.0))]
    (if (< len 1e-9) [0.0 0.0] [(/ dy len) (/ (- dx) len)])))

(defn- line-intersect-2d
  "Intersection point of infinite lines through (p1,p2) and (p3,p4), XY
  only (z carried from p1). nil if parallel."
  [p1 p2 p3 p4]
  (let [x1 (:x p1) y1 (:y p1) x2 (:x p2) y2 (:y p2)
        x3 (:x p3) y3 (:y p3) x4 (:x p4) y4 (:y p4)
        denom (- (* (- x1 x2) (- y3 y4)) (* (- y1 y2) (- x3 x4)))]
    (when (> (Math/abs denom) 1e-9)
      (let [t (/ (- (* (- x1 x3) (- y3 y4)) (* (- y1 y3) (- x3 x4))) denom)]
        (vec3/v3 (+ x1 (* t (- x2 x1))) (+ y1 (* t (- y2 y1))) (:z p1))))))

(defn offset-convex-polygon
  "Offset convex CCW polygon `pts` outward by `dist` (negative = inward):
  each edge shifts along its outward normal by `dist`, then each new
  vertex is the intersection of its two adjacent shifted edges."
  [pts dist]
  (let [n (count pts)
        shifted-edges
        (mapv (fn [i]
                (let [a (nth pts i) b (nth pts (mod (inc i) n))
                      [nx ny] (edge-outward-normal-2d a b)]
                  [(vec3/v3 (+ (:x a) (* nx dist)) (+ (:y a) (* ny dist)) (:z a))
                   (vec3/v3 (+ (:x b) (* nx dist)) (+ (:y b) (* ny dist)) (:z b))]))
              (range n))]
    (mapv (fn [i]
            (let [[a1 b1] (nth shifted-edges (mod (dec i) n))
                  [a2 b2] (nth shifted-edges i)]
              (or (line-intersect-2d a1 b1 a2 b2) a2)))
          (range n))))

(defn- gen-contour
  "Follows `:profile` (a convex CCW polygon, vec3 points, `:side`
  :inside/:outside/:on-line relative to it) offset by the tool radius,
  at one Z depth (`:depth` below the profile's own Z). Not attempted
  (returns `segments0` unchanged) for fewer than 3 points or a
  non-convex/non-CCW profile — see convex-ccw?."
  [job {:keys [tool-id depth side feed-rate profile]} segments0]
  (if (or (< (count (or profile [])) 3) (not (convex-ccw? profile)))
    segments0
    (let [tool (get (:tool-library job) tool-id)
          tool-radius (if tool (/ (:diameter tool) 2.0) 0.0)
          offset (case side :outside tool-radius :inside (- tool-radius) 0.0)
          offset-pts (offset-convex-polygon profile offset)
          z-top (:z (first profile))
          z (- z-top depth)
          safe-z (+ z-top (:safe-height job))
          path (mapv #(vec3/v3 (:x %) (:y %) z) offset-pts)
          closed (conj path (first path))
          start-xy (first path)
          segments (conj segments0
                         (segment {:segment-type :rapid :start (last-end segments0)
                                   :end (vec3/v3 (:x start-xy) (:y start-xy) safe-z) :tool-id tool-id}))
          segments (conj segments
                         (segment {:segment-type :rapid
                                   :start (vec3/v3 (:x start-xy) (:y start-xy) safe-z)
                                   :end start-xy :tool-id tool-id}))
          segments (reduce (fn [segs [a b]]
                              (conj segs (segment {:segment-type :linear :start a :end b
                                                   :feed-rate feed-rate :tool-id tool-id})))
                            segments
                            (map vector closed (rest closed)))
          prev (last-end segments)]
      (conj segments (segment {:segment-type :rapid :start prev
                               :end (vec3/v3 (:x prev) (:y prev) safe-z) :tool-id tool-id})))))

;; ---------------------------------------------------------------------
;; placeholder ops — surface-3d / turn
;; ---------------------------------------------------------------------

(defn- gen-placeholder
  [job {:keys [tool-id]} segments]
  (let [prev (last-end segments)]
    (conj segments
          (segment {:segment-type :rapid :start prev
                    :end (vec3/v3 0.0 0.0 (:safe-height job))
                    :tool-id tool-id}))))

;; ---------------------------------------------------------------------
;; CamJob
;; ---------------------------------------------------------------------

(defn new-job
  "A complete CAM job combining stock, tool library, and ordered operations."
  [stock tool-library]
  {:stock stock :operations [] :tool-library tool-library :safe-height 5.0})

(defn add-operation [job op] (update job :operations conj op))

(defn generate-toolpath
  "Generate toolpath segments for all operations in order. Implements
   zigzag pocket, peck drill, single-pass raster face-mill, and convex-
   polygon-offset contour following; :surface-3d/:turn (and :contour on a
   profile gen-contour can't offset correctly) still produce a
   placeholder rapid move to the tool-change point so the G-code
   structure stays valid."
  [job]
  (reduce
   (fn [segments op]
     (case (:op op)
       :pocket (gen-pocket job op segments)
       :drill (gen-drill job op segments)
       :face-mill (gen-face-mill job op segments)
       :contour (let [result (gen-contour job op segments)]
                  (if (= result segments) (gen-placeholder job op segments) result))
       (:surface-3d :turn) (gen-placeholder job op segments)))
   []
   (:operations job)))

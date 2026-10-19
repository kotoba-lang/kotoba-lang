(ns physics-2d.stairs
  "Diagonal traversable stair zones for 2D side-scrolling platformers.

  New capability (not a Rust port): added for `network-isekai`'s Castlevania-
  style staircases, where a player overlapping a diagonal strip and holding
  up/down + a direction walks along the slope instead of falling under
  gravity, then resumes normal platforming physics on leaving the zone.

  Zero-dep, portable `.cljc`, plain data + pure functions, matching the rest
  of `physics-2d`. A stair zone is NOT a `physics-2d` collider/body — it does
  not participate in `world-step`'s broad/narrowphase or impulse resolution.
  It is a separate, coarser rectangular region (min-corner `:x`/`:y` + extents
  `:width`/`:height`, NOT the centered half-extent shape `make-aabb-collider`
  uses) that a game loop queries directly: check overlap, then call
  `resolve-stair-movement` instead of the normal gravity-integration step
  while the entity is on the zone.

  Coordinate convention: matches `physics-2d`'s own (see
  `physics-2d.backend/scene->world`'s default gravity `[0.0 -9.81]`) —
  standard math axes, +y is UP, not screen-down. `:x`/`:y` on a zone or AABB
  is therefore its bottom-left (min-x, min-y) corner.

  `:dir` describes which horizontal direction climbs the slope:
    :up-right — walking in +x (right) goes UP the slope (y rises with x).
    :up-left  — walking in +x (right) goes DOWN the slope, i.e. walking in
                -x (left) goes UP (y falls as x rises).")

(def dirs
  "Valid `:dir` values for a stair zone."
  #{:up-right :up-left})

;; ---------------------------------------------------------------------------
;; Zone construction
;; ---------------------------------------------------------------------------

(defn make-stair-zone
  "Builds a stair-zone map. `x`/`y` is the zone's bottom-left (min) corner,
  `width`/`height` its extents (`height` is the total rise of the slope over
  the zone), `dir` is `:up-right` or `:up-left` (see namespace docstring)."
  [{:keys [x y width height dir]}]
  (when-not (contains? dirs dir)
    (throw (ex-info "stair zone :dir must be :up-right or :up-left" {:dir dir})))
  {:x x :y y :width width :height height :dir dir})

;; ---------------------------------------------------------------------------
;; Overlap predicates
;; ---------------------------------------------------------------------------

(defn point-in-zone?
  "True if world point `[x y]` falls within `zone`'s rectangular bounds
  (inclusive of the boundary)."
  [zone x y]
  (and (<= (:x zone) x (+ (:x zone) (:width zone)))
       (<= (:y zone) y (+ (:y zone) (:height zone)))))

(defn aabb-overlaps-zone?
  "True if axis-aligned box `aabb` (a map with min-corner `:x`/`:y` and
  extents `:width`/`:height`, same shape as a stair zone) overlaps `zone`'s
  rectangular bounds."
  [zone aabb]
  (and (< (:x zone) (+ (:x aabb) (:width aabb)))
       (< (:x aabb) (+ (:x zone) (:width zone)))
       (< (:y zone) (+ (:y aabb) (:height aabb)))
       (< (:y aabb) (+ (:y zone) (:height zone)))))

;; ---------------------------------------------------------------------------
;; Slope surface
;; ---------------------------------------------------------------------------

(defn slope-y-at
  "Returns the y coordinate of the stair surface at world x `x`, clamping `x`
  into `zone`'s `[x0, x0+width]` range first. At the zone's low-x edge the
  surface is at `:y`; at the high-x edge it is at `:y + :height`; `:dir`
  decides which edge is which end of the climb (see namespace docstring) —
  `slope-y-at` itself only needs low-x-edge-height vs. high-x-edge-height, so
  `:up-right` rises left-to-right and `:up-left` falls left-to-right."
  [zone x]
  (let [x0 (:x zone) w (:width zone) h (:height zone)
        y0 (:y zone)
        cx (-> x (max x0) (min (+ x0 w)))
        frac (if (zero? w) 0.0 (/ (- cx x0) w))]
    (case (:dir zone)
      :up-right (+ y0 (* frac h))
      :up-left  (+ y0 (* (- 1.0 frac) h)))))

;; ---------------------------------------------------------------------------
;; Movement resolution
;; ---------------------------------------------------------------------------

(defn resolve-stair-movement
  "Advances an entity's `aabb` (map with min-corner `:x`/`:y` and extents
  `:width`/`:height`) horizontally by `vx * dt` while it is on `zone`,
  suspending gravity and snapping its `:y` (feet, since `:y` is the bottom
  edge in this +y-up convention) to the slope surface at its new horizontal
  center.

  Returns `{:aabb aabb' :on-stairs? bool}`. `:on-stairs?` is true when the
  entity's new horizontal center still falls within the zone's x-range (the
  normal case while walking along the strip); once it walks past either end
  it is false and `:aabb`'s `:x` still reflects the requested horizontal
  move but `:y` is left unchanged (untouched by the slope) so the caller can
  hand off to normal gravity/platforming physics from an unmodified height.

  Callers should stop invoking this fn (and resume normal `world-step`
  gravity integration) once `:on-stairs?` is false or the entity is no
  longer overlapping `zone` per `aabb-overlaps-zone?`."
  [zone aabb vx dt]
  (let [new-x (+ (:x aabb) (* vx dt))
        cx (+ new-x (/ (:width aabb) 2.0))
        on-x? (<= (:x zone) cx (+ (:x zone) (:width zone)))]
    (if on-x?
      {:aabb (assoc aabb :x new-x :y (slope-y-at zone cx))
       :on-stairs? true}
      {:aabb (assoc aabb :x new-x)
       :on-stairs? false})))

(ns brep.feature
  "Parametric feature tree: sketch, extrude, revolve, fillet, chamfer,
  boolean, etc. Restored from kami-cad's `feature` module (deleted
  PR #82). A `FeatureId` is a plain number (the original's newtype
  `FeatureId(u64)` wrapper adds no behavior). Sketch constraint kinds use
  the same keyword vocabulary as `kotoba-lang/engineer`'s
  `engineer.constraint/kinds` (:coincident/:parallel/etc.) — the original
  duplicated `ConstraintKind` as `SketchConstraintKind` for serde
  independence rather than a hard crate dependency; this mirrors that by
  using the same keywords without an explicit inter-repo dependency."
  (:require [brep.kernel :as k]))

(def boolean-ops #{:new :add :cut :intersect})

(def sketch-constraint-kinds
  "Mirrors engineer.constraint/kinds — see namespace docstring."
  #{:coincident :parallel :perpendicular :tangent :equal :horizontal :vertical
    :fixed :symmetric :concentric :midpoint :collinear :distance :angle
    :radius :diameter})

;; SketchPlane
(defn sketch-plane-xy [] {:kind :xy})
(defn sketch-plane-xz [] {:kind :xz})
(defn sketch-plane-yz [] {:kind :yz})
(defn sketch-plane-custom [origin normal] {:kind :custom :origin origin :normal normal})

(defn sketch-plane-normal [plane]
  (case (:kind plane)
    :xy [0.0 0.0 1.0]
    :xz [0.0 1.0 0.0]
    :yz [1.0 0.0 0.0]
    :custom (k/v-normalize (:normal plane))))

(defn sketch-plane-origin [plane]
  (case (:kind plane)
    (:xy :xz :yz) [0.0 0.0 0.0]
    :custom (:origin plane)))

;; SketchEntity variants
(defn sketch-line [start end] {:kind :line :start start :end end})
(defn sketch-arc [center radius start-angle end-angle]
  {:kind :arc :center center :radius radius :start-angle start-angle :end-angle end-angle})
(defn sketch-circle [center radius] {:kind :circle :center center :radius radius})
(defn sketch-spline [control-points] {:kind :spline :control-points control-points})
(defn sketch-dimension [entity-ref value] {:kind :dimension :entity-ref entity-ref :value value})
(defn sketch-constraint [kind entity-refs] {:kind :constraint :constraint-kind kind :entity-refs entity-refs})

;; Feature variants — each a map with :kind + :id + variant-specific fields
(defn sketch-feature [id plane entities] {:kind :sketch :id id :plane plane :entities entities})
(defn extrude-feature [id sketch-ref direction distance operation]
  {:kind :extrude :id id :sketch-ref sketch-ref :direction direction :distance distance :operation operation})
(defn revolve-feature [id sketch-ref axis angle operation]
  {:kind :revolve :id id :sketch-ref sketch-ref :axis axis :angle angle :operation operation})
(defn fillet-feature [id edges radius] {:kind :fillet :id id :edges edges :radius radius})
(defn chamfer-feature [id edges distance] {:kind :chamfer :id id :edges edges :distance distance})
(defn sweep-feature [id profile-ref path-ref operation]
  {:kind :sweep :id id :profile-ref profile-ref :path-ref path-ref :operation operation})
(defn loft-feature [id profiles operation] {:kind :loft :id id :profiles profiles :operation operation})
(defn shell-feature [id removed-faces thickness] {:kind :shell :id id :removed-faces removed-faces :thickness thickness})
(defn pattern-feature [id source-features direction count spacing]
  {:kind :pattern :id id :source-features source-features :direction direction :count count :spacing spacing})
(defn boolean-feature [id operation tool-body] {:kind :boolean :id id :operation operation :tool-body tool-body})

(defn feature-id [feature] (:id feature))

;; FeatureTree
(defn feature-tree
  "A fresh, empty parametric feature tree."
  []
  {:entries []})

(defn add-feature [tree feature]
  (update tree :entries conj {:feature feature :suppressed false}))

(defn suppress [tree id]
  (update tree :entries
          (fn [entries] (mapv (fn [e] (if (= (feature-id (:feature e)) id) (assoc e :suppressed true) e)) entries))))

(defn unsuppress [tree id]
  (update tree :entries
          (fn [entries] (mapv (fn [e] (if (= (feature-id (:feature e)) id) (assoc e :suppressed false) e)) entries))))

(defn reorder
  "Move the feature with `id` to `new-index`."
  [tree id new-index]
  (let [entries (:entries tree)
        pos (first (keep-indexed (fn [i e] (when (= (feature-id (:feature e)) id) i)) entries))]
    (if-not pos
      tree
      (let [entry (nth entries pos)
            without (vec (concat (subvec entries 0 pos) (subvec entries (inc pos))))
            idx (min new-index (count without))]
        (assoc tree :entries (vec (concat (subvec without 0 idx) [entry] (subvec without idx))))))))

(defn tree-len [tree] (count (:entries tree)))
(defn tree-empty? [tree] (empty? (:entries tree)))

(defn- sketch-by-id
  "Non-suppressed :sketch features in `entries`, keyed by :id — revolve
  looks its profile up by :sketch-ref, and (unlike extrude) actually
  needs it."
  [entries]
  (into {}
        (keep (fn [{:keys [feature suppressed]}]
                (when (and (not suppressed) (= :sketch (:kind feature)))
                  [(:id feature) feature])))
        entries))

(defn- revolve-profile-cylinder
  "The only revolve profile this evaluator supports today: `sketch`'s
  :entities contains exactly one sketch-line, and both its 2D [x y]
  endpoints share the same x (an axis-parallel profile — x is radius, y
  is axial offset from the sketch plane's origin). Returns
  {:radius :axial-min :axial-max}, or nil if the sketch doesn't match
  that shape (an angled line — a cone/frustum profile — or any other
  entity count/kind is not yet implemented; no cone tessellation exists
  in brep.tessellate yet to render one correctly)."
  [sketch]
  (let [lines (filter #(= :line (:kind %)) (:entities sketch))]
    (when (= 1 (count lines))
      (let [{:keys [start end]} (first lines)
            [x0 y0] start [x1 y1] end]
        (when (< (Math/abs (- x0 x1)) 1e-9)
          {:radius x0 :axial-min (min y0 y1) :axial-max (max y0 y1)})))))

(defn evaluate
  "Evaluate the feature tree, producing a BREP solid. Handles two base-
  feature cases:

  - `:extrude` with `:operation :new` generates a box-like prism from a
    unit-square cross-section along the extrusion direction (matches the
    original kami-cad Rust restoration — sketch entities are not yet
    consumed here, only :direction/:distance).
  - `:revolve` generates a real solid of revolution (brep.kernel/
    make-cylinder) ONLY for a full 2π turn of a single axis-parallel
    sketch-line profile (see revolve-profile-cylinder) — a cone/frustum
    profile (an angled line) or a partial-angle revolve returns
    `:error`, not a silently wrong shape; no cone tessellation exists
    yet to render either correctly.

  boolean/fillet/chamfer/sweep/loft/shell/pattern evaluation remain
  future work, tracked as TODOs in the source (a general boolean CSG
  kernel and arbitrary-profile revolve/sweep both need real geometry
  algorithms — polygon/polyhedron clipping, cone/freeform tessellation —
  that are correctness-critical enough not to rush).

  Returns `[:ok [solid edges verts]]` or `[:error msg]`."
  [tree]
  (let [sketches (sketch-by-id (:entries tree))]
    (loop [entries (:entries tree)
           result nil]
      (if (empty? entries)
        (if result [:ok result] [:error "feature tree produced no solid"])
        (let [{:keys [feature suppressed]} (first entries)]
          (if suppressed
            (recur (rest entries) result)
            (case (:kind feature)
              :extrude
              (case (:operation feature)
                :new
                (let [half 0.5
                      ext (k/v-scale (k/v-normalize (:direction feature)) (:distance feature))
                      vmin [(- half) (- half) 0.0]
                      vmax (k/v+ [half half 0.0] ext)]
                  (recur (rest entries) (k/make-box 1 vmin vmax)))

                (:add :cut :intersect)
                (if (nil? result)
                  [:error "no base solid to apply boolean to"]
                  (recur (rest entries) result)))

              :revolve
              (let [full-turn? (>= (:angle feature) (- (* 2.0 Math/PI) 1e-6))
                    sketch (get sketches (:sketch-ref feature))
                    profile (when sketch (revolve-profile-cylinder sketch))]
                (cond
                  (not full-turn?)
                  [:error "revolve: only a full 2π turn is implemented (partial-angle pie-slice revolve is not yet supported)"]
                  (nil? sketch)
                  [:error (str "revolve: sketch-ref " (:sketch-ref feature) " not found")]
                  (nil? profile)
                  [:error "revolve: only a single axis-parallel sketch-line profile is implemented (an angled line -> cone/frustum, or any other profile shape, is not yet supported)"]
                  :else
                  (let [axis (k/v-normalize (:axis feature))
                        base (k/v+ (sketch-plane-origin (:plane sketch))
                                   (k/v-scale axis (:axial-min profile)))
                        height (- (:axial-max profile) (:axial-min profile))]
                    (recur (rest entries) (k/make-cylinder 1 base axis (:radius profile) height)))))

              :sketch (recur (rest entries) result)

              (recur (rest entries) result))))))))

(ns kotoba.render.camera
  "Camera math (perspective / orthographic / map-view projection), light
   uniform, and PBR material-preset data. Ported from `kami-render/src/camera.rs`.

   All matrices are plain Clojure vectors of 16 floats, **column-major**
   (same layout `glam::Mat4::to_cols_array` produces — i.e. `m[0..4]` is
   column 0, ready to hand to a `mat4x4<f32>` uniform buffer as-is).")

;; ---------------------------------------------------------------------------
;; Vec3 / Mat4 minimal pure-data helpers (right-handed, matches `glam`)
;; ---------------------------------------------------------------------------

(defn v3-sub [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
(defn v3-add [[ax ay az] [bx by bz]] [(+ ax bx) (+ ay by) (+ az bz)])
(defn v3-scale [[x y z] s] [(* x s) (* y s) (* z s)])
(defn v3-dot [[ax ay az] [bx by bz]] (+ (* ax bx) (* ay by) (* az bz)))
(defn v3-cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])
(defn v3-len [v] (Math/sqrt (double (v3-dot v v))))
(defn v3-normalize [v]
  (let [l (v3-len v)]
    (if (zero? l) v (v3-scale v (/ 1.0 l)))))

(defn deg->rad [d] (* d (/ Math/PI 180.0)))

(defn look-at-rh
  "Right-handed view matrix, column-major 16-vector. Matches `glam::Mat4::look_at_rh`."
  [eye target up]
  (let [f (v3-normalize (v3-sub target eye))
        s (v3-normalize (v3-cross f up))
        u (v3-cross s f)]
    [(nth s 0) (nth u 0) (- (nth f 0)) 0.0
     (nth s 1) (nth u 1) (- (nth f 1)) 0.0
     (nth s 2) (nth u 2) (- (nth f 2)) 0.0
     (- (v3-dot s eye)) (- (v3-dot u eye)) (v3-dot f eye) 1.0]))

(defn perspective-rh
  "Right-handed perspective projection, depth range [0,1] (wgpu convention).
   Matches `glam::Mat4::perspective_rh`."
  [fov-y-rad aspect near far]
  (let [f (/ 1.0 (Math/tan (/ fov-y-rad 2.0)))
        range-inv (/ 1.0 (- near far))]
    [(/ f aspect) 0.0 0.0 0.0
     0.0 f 0.0 0.0
     0.0 0.0 (* far range-inv) -1.0
     0.0 0.0 (* far near range-inv) 0.0]))

(defn orthographic-rh
  "Right-handed orthographic projection, depth range [0,1] (wgpu convention).
   Matches `glam::Mat4::orthographic_rh`."
  [left right bottom top near far]
  [(/ 2.0 (- right left)) 0.0 0.0 0.0
   0.0 (/ 2.0 (- top bottom)) 0.0 0.0
   0.0 0.0 (/ -1.0 (- far near)) 0.0
   (/ (- (+ right left)) (- right left))
   (/ (- (+ top bottom)) (- top bottom))
   (/ (- near) (- far near))
   1.0])

(defn mat4-mul
  "Column-major 4x4 matrix multiply: `(mat4-mul a b)` = a*b (b applied first)."
  [a b]
  (vec
   (for [col (range 4) row (range 4)]
     (reduce +
             (for [k (range 4)]
               (* (nth a (+ row (* k 4))) (nth b (+ (* col 4) k))))))))

;; ---------------------------------------------------------------------------
;; ortho-matrix — direct port of `camera::ortho_matrix`
;; ---------------------------------------------------------------------------

(defn ortho-matrix
  "Build a column-major orthographic projection matrix (right-handed, depth [0,1])."
  [width height near far]
  (let [hw (* width 0.5)
        hh (* height 0.5)]
    (orthographic-rh (- hw) hw (- hh) hh near far)))

;; ---------------------------------------------------------------------------
;; Camera state + projection modes
;; ---------------------------------------------------------------------------

(defn camera
  "New camera at the kami-render default pose/aspect."
  [aspect]
  {:position [0.0 10.0 20.0]
   :target [0.0 0.0 0.0]
   :up [0.0 1.0 0.0]
   :fov-y (deg->rad 60.0)
   :aspect aspect
   :near 0.5
   :far 256.0
   :mode {:kind :perspective}})

(defn- map-view-altitude [zoom]
  (* 256.0 (Math/pow 2.0 (- 16.0 zoom))))

(defn camera-uniform
  "Build the `{:view :projection :position}` uniform map for `cam`, matching
   `Camera::uniform` in the Rust source (`CameraUniform` layout minus padding,
   which is a GPU-buffer-alignment concern, not data)."
  [{:keys [position target up fov-y aspect near far mode]}]
  (let [view (look-at-rh position target up)
        projection
        (case (:kind mode)
          :perspective (perspective-rh fov-y aspect near far)
          :orthographic-side
          (let [oh 16.0 ow (* oh aspect) hw (* ow 0.5) hh (* oh 0.5)]
            (orthographic-rh (- hw) hw (- hh) hh near far))
          :orthographic-top
          (let [oh (max (Math/abs (double (nth position 1))) 1.0)
                ow (* oh aspect) hw (* ow 0.5) hh (* oh 0.5)]
            (orthographic-rh (- hw) hw (- hh) hh near far))
          :map-view
          (let [{:keys [zoom pitch]} mode
                altitude (map-view-altitude zoom)]
            (if (< pitch 0.01)
              (let [half-h altitude half-w (* half-h aspect)]
                (orthographic-rh (- half-w) half-w (- half-h) half-h near far))
              (let [fov (+ 0.6 (* pitch 0.4))]
                (perspective-rh fov aspect near far)))))]
    {:view view
     :projection projection
     :position position}))

(defn orbit
  "Orbit around target. Returns updated camera."
  [cam yaw pitch distance]
  (let [x (* distance (Math/cos pitch) (Math/sin yaw))
        y (* distance (Math/sin pitch))
        z (* distance (Math/cos pitch) (Math/cos yaw))]
    (assoc cam :position (v3-add (:target cam) [x y z]))))

(defn set-position
  "Set camera position directly, looking toward -Z."
  [cam pos]
  (assoc cam :position pos :target (v3-add pos [0.0 0.0 -1.0])))

(defn move-fps
  "First-person camera: move by delta and look in yaw/pitch direction."
  [cam delta yaw pitch]
  (let [position (v3-add (:position cam) delta)
        forward [(* (Math/sin yaw) (Math/cos pitch))
                  (Math/sin pitch)
                  (- (* (Math/cos yaw) (Math/cos pitch)))]]
    (assoc cam :position position :target (v3-add position forward))))

(defn map-view-update
  "Map-view camera: position above center, looking down with optional tilt."
  [cam center-x center-z zoom bearing pitch]
  (let [mode {:kind :map-view :zoom zoom :bearing bearing :pitch pitch}
        altitude (map-view-altitude zoom)
        cos-p (Math/cos pitch) sin-p (Math/sin pitch)
        cos-b (Math/cos bearing) sin-b (Math/sin bearing)
        back-dist (* altitude sin-p)
        up-dist (* altitude cos-p)
        offset-x (- (* back-dist sin-b))
        offset-z (- (* back-dist cos-b))
        position [(+ center-x offset-x) up-dist (+ center-z offset-z)]
        target [center-x 0.0 center-z]
        up (if (< pitch 0.01) [(- sin-b) 0.0 (- cos-b)] [0.0 1.0 0.0])]
    (assoc cam :mode mode :position position :target target :up up)))

(defn side-scroll-update
  "Side-scroll camera: follow a player on the XY plane, looking along -Z."
  [cam player-x player-y]
  (assoc cam
         :position [player-x (+ player-y 2.0) 20.0]
         :target [player-x (+ player-y 2.0) 0.0]
         :up [0.0 1.0 0.0]))

;; ---------------------------------------------------------------------------
;; Light uniform
;; ---------------------------------------------------------------------------

(defn light-uniform
  "Directional light uniform: direction/color/intensity + a shadow-map
   view_proj matrix (30-unit ortho box, matching `LightUniform::directional`)."
  [direction color intensity]
  (let [dir (v3-normalize direction)
        light-pos (v3-scale dir -50.0)
        view (look-at-rh light-pos [0.0 0.0 0.0] [0.0 1.0 0.0])
        projection (orthographic-rh -30.0 30.0 -30.0 30.0 0.1 100.0)]
    {:direction dir
     :color color
     :intensity intensity
     :view-proj (mat4-mul projection view)}))

;; ---------------------------------------------------------------------------
;; Material uniform — PBR + SSS + hair + eye/clearcoat/emission presets
;; ---------------------------------------------------------------------------

(def default-material
  "Matches `MaterialUniform::default()`."
  {:albedo [0.8 0.8 0.8 1.0]
   :metallic 0.0
   :roughness 0.5
   :has-albedo-tex 0
   :has-normal-tex 0
   :subsurface-color [0.0 0.0 0.0 0.0]
   :subsurface-radius [0.0 0.0 0.0]
   :sss-model 0
   :aniso-tangent [0.0 1.0 0.0]
   :aniso-strength 0.0
   :hair-scatter [0.0 0.0 0.0 0.0]
   :clearcoat 0.0
   :clearcoat-roughness 0.5
   :emission [0.0 0.0 0.0]
   :tex-flags 0
   :parallax-depth 0.0})

(defn material-skin
  "Skin material preset: Burley SSS diffusion profile. `tone` 0.0(dark)..1.0(fair)."
  [tone]
  (let [base (+ 0.4 (* tone 0.5))]
    (merge default-material
           {:albedo [base (* base 0.82) (* base 0.72) 1.0]
            :metallic 0.0
            :roughness 0.35
            :subsurface-color [0.85 0.25 0.15 0.65]
            :subsurface-radius [1.2 0.4 0.2]
            :sss-model 1})))

(defn material-hair
  "Hair material preset: anisotropic Marschner specular + fiber scatter.
   `hue` 0.0=red .. 0.7=black, `lightness` overall brightness."
  [hue lightness]
  (let [r (* (- 1.0 (min 1.0 (Math/abs (- (* hue 6.0) 0.0)))) lightness)
        g (* (- 1.0 (min 1.0 (Math/abs (- (* hue 6.0) 2.0)))) lightness)
        b (* (- 1.0 (min 1.0 (Math/abs (- (* hue 6.0) 4.0)))) lightness)]
    (merge default-material
           {:albedo [(max r 0.05) (max g 0.05) (max b 0.05) 1.0]
            :metallic 0.0
            :roughness 0.28
            :aniso-tangent [0.0 1.0 0.0]
            :aniso-strength 0.85
            :hair-scatter [(* r 0.6) (* g 0.6) (* b 0.6) 0.15]})))

(defn material-eye
  "Eye material preset: clearcoat cornea + parallax iris refraction."
  [[r g b]]
  (merge default-material
         {:albedo [r g b 1.0]
          :metallic 0.0
          :roughness 0.05
          :clearcoat 0.95
          :clearcoat-roughness 0.02
          :parallax-depth 0.03}))

(defn material-lip
  "Lip material preset: subtle SSS + glossy clearcoat."
  [[r g b]]
  (merge default-material
         {:albedo [r g b 1.0]
          :metallic 0.0
          :roughness 0.25
          :clearcoat 0.4
          :clearcoat-roughness 0.15
          :subsurface-color [0.9 0.2 0.15 0.3]
          :subsurface-radius [0.5 0.15 0.1]
          :sss-model 1}))

(defn material-fabric
  "Fabric material preset: diffuse with subtle roughness variation."
  [color roughness]
  (merge default-material
         {:albedo color
          :metallic 0.0
          :roughness roughness}))

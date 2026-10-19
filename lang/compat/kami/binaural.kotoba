(ns kami.binaural
  "L2 — binaural / spatial audio authored as EDN data → a backend-neutral
  spatialization IR. The clj layer is the *brain*: it turns a listener + a set of
  positioned sound sources into per-source binaural parameters (ITD / ILD / gains
  / delays). The *arm* is per-platform — `emit` lowers the IR to whatever the
  runtime can execute: a Web Audio node graph (browser), the `kami-audio` Rust
  mixer (native: iOS/Metal · Android · desktop), or a console mixer. Same EDN,
  any executor — the kami `audio.cljs` cue-bank pattern extended to 3D.

  This deliberately upgrades the old simplified dot-product pan (kami-audio's
  `spatialize`) to a physically-grounded *spherical-head* model, all in data:

    • ITD — Woodworth's spherical-head formula  itd = (a/c)(θ + sin θ),
      where θ is the lateral angle, a the head radius, c the speed of sound.
      Front/back symmetric and elevation-aware (θ shrinks as a source rises).
    • ILD — frequency-independent head-shadow approximation: the contralateral
      ear is attenuated proportionally to the lateral angle.
    • distance — OpenAL-style inverse / linear / exponential rolloff.

  EDN scene shape (everything optional except :sources):

    {:binaural/listener {:pos [0 0 0] :forward [0 0 -1] :up [0 1 0]}
     :binaural/hrtf     {:model :spherical-head :head-radius 0.0875 :max-ild-db 12.0}
     :binaural/rolloff  {:kind :inverse :ref 1.0 :max 100.0 :factor 1.0}
     :binaural/sources  [{:id :foot :cue :step :pos [3 0 -1] :gain 0.8}
                         {:id :bell :cue :ring :pos [0 1  4] :gain 1.0}]}

  `mix` is pure and serializable — the golden-test / record-replay surface.

  Phase 2.2 (ADR-CLJ-2607010930) adds an `emit :wgsl` backend: the per-source
  spatialization (the SAME ITD/ILD/gain/delay math as `spatialize`) lowers to a
  WGSL @compute kernel that reads a source buffer (positions/gains) + a mono
  source-PCM buffer and writes a stereo output buffer. This is the OFFLINE
  bounce / render-to-file path — the per-sample hot loop runs on GPU. The
  real-time, latency-critical path stays `emit :native` (the kami-audio Rust
  mixer): CLJ authors + emits; native stays the sample mixer. Do NOT route
  live playback through `:wgsl`."
  (:require [kami.math :as m]
            [kami.wgsl-emit :as wgsl]))

;; --- physical + default constants ------------------------------------------

(def ^:const speed-of-sound 343.0)          ; m/s, dry air ~20°C
(def ^:const default-head-radius 0.0875)    ; m, ~standard adult (KEMAR)

(def default-listener {:pos [0.0 0.0 0.0] :forward [0.0 0.0 -1.0] :up [0.0 1.0 0.0]})
(def default-hrtf     {:model :spherical-head :head-radius default-head-radius :max-ild-db 12.0})
(def default-rolloff  {:kind :inverse :ref 1.0 :max 100.0 :factor 1.0})

(def rolloff-kinds #{:inverse :linear :exponential :none})
(def hrtf-models   #{:spherical-head :pan-law})

;; --- validation -------------------------------------------------------------

(defn valid?
  "Throw with a precise reason if `scene` is malformed; return true otherwise.
  Mirrors kami.scene/valid? — fail fast at author time, not on the GPU/mixer."
  [scene]
  (let [{:keys [binaural/rolloff binaural/hrtf binaural/sources]} scene]
    (when (and rolloff (not (rolloff-kinds (:kind rolloff))))
      (throw (ex-info "binaural: unknown rolloff kind"
                      {:kind (:kind rolloff) :known rolloff-kinds})))
    (when (and hrtf (not (hrtf-models (:model hrtf))))
      (throw (ex-info "binaural: unknown hrtf model"
                      {:model (:model hrtf) :known hrtf-models})))
    (when-not (sequential? (or sources []))
      (throw (ex-info "binaural: :binaural/sources must be a sequence" {:sources sources})))
    (doseq [s sources]
      (when-not (and (vector? (:pos s)) (= 3 (count (:pos s))))
        (throw (ex-info "binaural: source needs a 3-vector :pos" {:source s}))))
    true))

;; --- listener basis ---------------------------------------------------------

(defn- listener-basis
  "Orthonormal {right up forward} for the listener (right-handed, robust to a
  non-orthogonal authored up vector)."
  [{:keys [forward up]}]
  (let [f  (m/normalize forward)
        r  (m/normalize (m/cross f up))
        u' (m/cross r f)]
    {:right r :up u' :forward f}))

;; --- distance rolloff -------------------------------------------------------

(defn distance-gain
  "Linear gain ∈ [0,1] for `dist` under a rolloff spec (OpenAL semantics)."
  [{:keys [kind ref max factor] :or {kind :inverse ref 1.0 max 100.0 factor 1.0}} dist]
  (let [d (m/clamp dist ref max)]
    (case kind
      :none        1.0
      :inverse     (/ ref (+ ref (* factor (- d ref))))
      :linear      (m/clamp (- 1.0 (* factor (/ (- d ref) (Math/max 1e-6 (- max ref))))) 0.0 1.0)
      :exponential (Math/pow (/ d ref) (- factor)))))

;; --- core spatialization (one source) --------------------------------------

(defn spatialize
  "Pure: listener + hrtf + rolloff + one source → binaural params.
  Returns {:distance :azimuth :elevation :lateral :itd-s :ild-db
           :gain-l :gain-r :delay-l-s :delay-r-s}. Angles in radians; the leading
  ear has delay 0 and the contralateral ear carries the ITD. gains fold in
  distance attenuation × source :gain."
  [listener hrtf rolloff source]
  (let [{:keys [right up forward]} (listener-basis listener)
        a        (:head-radius hrtf default-head-radius)
        max-ild  (:max-ild-db hrtf 12.0)
        rel      (m/v- (:pos source) (:pos listener))
        dist     (m/length rel)
        dir      (m/normalize rel)
        lateral  (m/clamp (m/dot dir right) -1.0 1.0)   ; +right, sin of lateral angle
        front    (m/dot dir forward)
        vert     (m/clamp (m/dot dir up) -1.0 1.0)
        azimuth  (Math/atan2 lateral front)
        elev     (Math/asin vert)
        theta    (Math/asin lateral)                    ; lateral angle, front/back symmetric
        itd      (* (/ a speed-of-sound) (+ theta (Math/sin theta)))  ; +→right leads
        ild-db   (* max-ild lateral)                    ; +→right louder
        dgain    (* (distance-gain rolloff dist) (:gain source 1.0))
        ;; attenuate only the contralateral (far) ear by |ILD|
        shadow   (Math/pow 10.0 (/ (- (Math/abs ild-db)) 20.0))
        right?   (>= lateral 0.0)
        gain-l   (* dgain (if right? shadow 1.0))
        gain-r   (* dgain (if right? 1.0 shadow))
        delay-l  (if (>= itd 0.0) itd 0.0)              ; right leads → delay left
        delay-r  (if (< itd 0.0) (- itd) 0.0)]
    {:distance dist :azimuth azimuth :elevation elev :lateral lateral
     :itd-s itd :ild-db ild-db
     :gain-l gain-l :gain-r gain-r :delay-l-s delay-l :delay-r-s delay-r}))

;; --- scene mix (the IR) -----------------------------------------------------

(defn mix
  "Pure: an EDN binaural scene → spatialization IR
     {:binaural/listener {...}
      :binaural/sources [{:source/id :source/cue :spatial {...}} ...]}
  Defaults are filled deterministically; source order is preserved."
  [scene]
  (valid? scene)
  (let [listener (merge default-listener (:binaural/listener scene))
        hrtf     (merge default-hrtf     (:binaural/hrtf scene))
        rolloff  (merge default-rolloff  (:binaural/rolloff scene))]
    {:binaural/listener listener
     :binaural/sources
     (vec (for [s (:binaural/sources scene)]
            {:source/id  (:id s)
             :source/cue (:cue s)
             :spatial    (spatialize listener hrtf rolloff s)}))}))

;; --- backend lowering (execution delegated per platform) --------------------

;; Phase 2.2 — WGSL @compute kernel (offline bounce). The spatialization math
;; below mirrors `spatialize` (and thus the kami-audio Rust native arm)
;; formula-for-formula: Woodworth spherical-head ITD, head-shadow ILD, inverse
;; distance rolloff, per-ear gain + integer-sample ITD delay. The body uses the
;; `:wgsl/body` raw-WGSL escape hatch (storage-buffer indexing / `if/return`
;; early-out exceed the s-expr subset), exactly like the Phase 2.1 cartpole
;; kernel. The surrounding @compute scaffolding, structs, and storage bindings
;; remain fully data-driven. One workgroup lane = one output sample; the kernel
;; spatializes `sources[0]` (single-source bounce — multi-source is a sequence
;; of bounces + a mixdown, kept for a later kernel).

(def ^:private binaural-source-struct
  {:Source [[:pos  :vec3<f32>]
            [:gain :f32]]})

(def ^:private binaural-cfg-struct
  {:Cfg [[:listener_pos   :vec3<f32>]
         [:right           :vec3<f32>]
         [:up              :vec3<f32>]
         [:forward         :vec3<f32>]
         [:head_radius     :f32]
         [:max_ild_db      :f32]
         [:speed_of_sound  :f32]
         [:sample_rate     :f32]
         [:ref             :f32]
         [:max_dist        :f32]
         [:factor          :f32]
         [:num_samples     :u32]
         [:num_sources     :u32]]})

(def ^:private binaural-wgsl-bindings
  [{:group 0 :binding 0 :var :storage :access :read
    :name "sources" :type :array<Source>}
   {:group 0 :binding 1 :var :storage :access :read
    :name "pcm" :type :array<f32>}
   {:group 0 :binding 2 :var :storage :access :read_write
    :name "stereo" :type :array<f32>}
   {:group 0 :binding 3 :var :uniform
    :name "cfg" :type :Cfg}])

(def ^:private binaural-wgsl-body
  "
  let i = gid.x;
  if (i >= cfg.num_samples) {
    return;
  }

  // spatialize source[0] — mirrors kami.binaural/spatialize formula-for-formula.
  let src = sources[0u];
  let rel = src.pos - cfg.listener_pos;
  let dist = length(rel);
  let dir = normalize(rel);
  let lateral = clamp(dot(dir, cfg.right), -1.0, 1.0);
  let theta = asin(lateral);

  // Woodworth spherical-head ITD:  itd = (a / c) * (theta + sin theta)
  let itd_s = (cfg.head_radius / cfg.speed_of_sound) * (theta + sin(theta));

  // ILD — frequency-independent head shadow: contralateral ear attenuated by
  // |ILD| dB.  shadow = 10^(-|ild_db| / 20)
  let ild_db = cfg.max_ild_db * lateral;
  let shadow = pow(10.0, (-abs(ild_db)) / 20.0);

  // distance attenuation (inverse rolloff, OpenAL semantics)
  let d = clamp(dist, cfg.ref, cfg.max_dist);
  let dgain = cfg.ref / (cfg.ref + cfg.factor * (d - cfg.ref));
  let g = dgain * src.gain;

  // fold ILD into per-ear gain (contralateral ear x shadow; ipsilateral x 1.0)
  let right_leads = lateral >= 0.0;
  let gain_l = g * select(1.0, shadow, right_leads);
  let gain_r = g * select(shadow, 1.0, right_leads);

  // ITD -> integer sample delay per ear (leading ear delay = 0)
  let dl_f = select(0.0, itd_s, itd_s >= 0.0);
  let dr_f = select(0.0, -itd_s, itd_s < 0.0);
  let dl = u32(clamp(round(dl_f * cfg.sample_rate), 0.0, f32(cfg.num_samples)));
  let dr = u32(clamp(round(dr_f * cfg.sample_rate), 0.0, f32(cfg.num_samples)));

  // per-sample mix: read delayed source PCM, apply per-ear gain -> stereo out
  var sl: f32 = 0.0;
  var sr: f32 = 0.0;
  if (i >= dl) {
    sl = pcm[i - dl];
  }
  if (i >= dr) {
    sr = pcm[i - dr];
  }
  stereo[2u * i]      = gain_l * sl;
  stereo[2u * i + 1u] = gain_r * sr;")

(defn binaural-wgsl-shader
  "Return the binaural offline-bounce @compute kernel as a `kami.wgsl-emit` data map.
  One workgroup invocation = one output sample; 64 samples per workgroup,
  dispatch `ceil(num_samples / 64, 1, 1)` groups. The kernel reads `sources[0]`
  (single-source bounce), spatializes it (ITD/ILD/gain/delay matching
  `spatialize`), and writes the stereo pair `stereo[2*i], stereo[2*i+1]`."
  []
  {:wgsl/name    "binaural_bounce"
   :wgsl/structs (merge binaural-source-struct binaural-cfg-struct)
   :wgsl/bindings binaural-wgsl-bindings
   :wgsl/compute {:workgroup-size [64 1 1]
                  :entry          "binaural_main"
                  :builtin        :global_invocation_id
                  :builtin-name   "gid"
                  :wgsl/body      binaural-wgsl-body}})

(defn binaural-wgsl-emit
  "Return the binaural bounce WGSL source string via `kami.wgsl-emit/emit`. Pure."
  []
  (wgsl/emit (binaural-wgsl-shader)))

(defmulti emit
  "Lower the spatialization IR (from `mix`) to a backend-specific, executable
  descriptor. Dispatch on backend keyword. Execution itself happens in the
  runtime (Web Audio graph, kami-audio Rust mixer, console mixer) — this only
  produces the data that runtime consumes."
  (fn [backend _ir & _opts] backend))

(defmethod emit :web-audio
  ;; A node-graph recipe the cljs runtime builds: per source a DelayNode pair
  ;; (ITD) + GainNode pair (ILD + distance) + equal-power StereoPanner fallback.
  [_ ir & _]
  {:backend :web-audio
   :nodes (vec (for [{:source/keys [id cue] :keys [spatial]} (:binaural/sources ir)]
                 {:id id :cue cue
                  :delay-l (:delay-l-s spatial) :delay-r (:delay-r-s spatial)
                  :gain-l  (:gain-l spatial)    :gain-r  (:gain-r spatial)
                  :pan     (Math/sin (:azimuth spatial))}))})

(defmethod emit :native
  ;; Matches the kami-audio (Rust) AudioMixer::spatialize voice fields. ITD is
  ;; carried as an integer sample delay at the mixer's sample rate.
  [_ ir & [{:keys [sample-rate] :or {sample-rate 48000}}]]
  {:backend :native :sample-rate sample-rate
   :voices (vec (for [{:source/keys [id cue] :keys [spatial]} (:binaural/sources ir)]
                  {:id id :cue cue
                   :left-vol  (:gain-l spatial) :right-vol (:gain-r spatial)
                   :pan       (Math/sin (:azimuth spatial))
                   :delay-l-samples (long (Math/round (* (:delay-l-s spatial) sample-rate)))
                   :delay-r-samples (long (Math/round (* (:delay-r-s spatial) sample-rate)))}))})

(defmethod emit :wgsl
  ;; Offline-bounce lowering: emit the binaural @compute kernel (WGSL source) +
  ;; the bind-group config the host uploads. The shader is generic (cached,
  ;; reusable); the per-scene listener/HRTF/rolloff/sample-rate go into the `cfg`
  ;; uniform descriptor returned here, and the host fills the `sources` storage
  ;; buffer (positions/gains) + `pcm` mono buffer + `stereo` output buffer at
  ;; dispatch time. The per-source spatialization math runs IN the kernel
  ;; (mirrors `spatialize`); CLJ only authors + dispatches. See ns docstring:
  ;; this is the offline bounce path, NOT real-time playback.
  [_ ir & [{:keys [sample-rate num-samples] :or {sample-rate 48000 num-samples 0}}]]
  (let [{:keys [binaural/listener binaural/hrtf binaural/rolloff binaural/sources]} ir
        hrtf    (merge default-hrtf hrtf)
        rolloff (merge default-rolloff rolloff)
        {:keys [right up forward]} (listener-basis listener)]
    {:backend      :wgsl
     :src          (binaural-wgsl-emit)
     :sample-rate  sample-rate
     :num-samples  num-samples
     :layout       {:bindings
                    [{:group 0 :binding 0 :var :storage :access :read
                      :name "sources" :type :array<Source>}
                     {:group 0 :binding 1 :var :storage :access :read
                      :name "pcm" :type :array<f32>}
                     {:group 0 :binding 2 :var :storage :access :read_write
                      :name "stereo" :type :array<f32>}
                     {:group 0 :binding 3 :var :uniform
                      :name "cfg" :type :Cfg}]}
     :cfg          {:listener-pos  (:pos listener)
                    :right         right :up up :forward forward
                    :head-radius   (:head-radius hrtf default-head-radius)
                    :max-ild-db    (:max-ild-db hrtf 12.0)
                    :speed-of-sound speed-of-sound
                    :sample-rate   (double sample-rate)
                    :ref           (:ref rolloff 1.0)
                    :max-dist      (:max rolloff 100.0)
                    :factor        (:factor rolloff 1.0)
                    :num-samples   (long num-samples)
                    :num-sources   (count (or sources []))}}))

(defmethod emit :default [backend ir & _]
  ;; Unknown backend: hand back the neutral IR so the caller can lower it itself.
  {:backend backend :ir ir})

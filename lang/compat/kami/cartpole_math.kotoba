(ns kami.cartpole-math
  "Phase 3.1 (ADR-2607010930, re-scoped to kami-webgpu) — the pure-CLJC cartpole step
  that the compute-golden pins. This is a faithful, dependency-free mirror of the
  WGSL body emitted by `kami.physics-compute/cartpole-step-emit` (semi-implicit Euler,
  Sutton & Barto 1983 cartpole equations), so the golden fixture can be regenerated
  AND asserted from CLJC data alone — no GPU, no Rust, no wgpu execution.

  Keeping the math here (rather than reaching across to `kami.physics-compute` from
  the test) sidesteps the two-repo `kami.wgsl` divergence: kami-webgpu ships a
  low-level `kami.wgsl` (expr/stmt/func), the kami-engine SDK ships a high-level
  `kami.wgsl` (emit/emit-compute-stage). Requiring the SDK's `kami.physics-compute`
  from `bb test` would pull the SDK's `kami.wgsl` onto the JVM classpath and shadow
  kami-webgpu's, breaking `wgsl-test`. So the *math* lives here (shared by the gen
  script and the test), and only the *WGSL emission* (a separate `bb gen-compute-golden`
  process with the SDK first on its classpath) touches `kami.physics-compute`.

  The formula is byte-for-byte the same as the `:wgsl/body` string in
  `kami.physics-compute/cartpole-step-shader` — update both together.")
(defn clamp
  "Mirror WGSL `clamp(x, lo, hi)`."
  [x lo hi] (max lo (min hi x)))

(defn step
  "One semi-implicit Euler integration step for a single cartpole environment.

  State  = [x x_dot theta theta_dot] (doubles).
  Action = raw force (clamped to ±force_mag, mirroring the WGSL `clamp`).
  Cfg    = map with :cart_mass :pole_mass :pole_half_length :gravity :force_mag :dt.

  Returns the next state `[x' x_dot' theta' theta_dot']`. Pure; deterministic.
  The update order is semi-implicit: x_dot/theta_dot are advanced first, then
  x/theta use the NEW velocities — exactly as the WGSL body does."
  [{:keys [cart-mass pole-mass pole-half-length gravity force-mag dt]}
   [x x-dot theta theta-dot]
   action]
  (let [force    (clamp action (- force-mag) force-mag)
        sin-t    (Math/sin theta)
        cos-t    (Math/cos theta)
        total    (+ cart-mass pole-mass)
        pml      (* pole-mass pole-half-length)
        temp     (/ (+ force (* pml theta-dot theta-dot sin-t)) total)
        theta-acc (/ (- (* gravity sin-t) (* cos-t temp))
                     (* pole-half-length
                        (- (/ 4.0 3.0) (/ (* pole-mass cos-t cos-t) total))))
        x-acc    (- temp (/ (* pml theta-acc cos-t) total))
        x-dot'   (+ x-dot (* dt x-acc))
        x'       (+ x (* dt x-dot'))
        theta-dot' (+ theta-dot (* dt theta-acc))
        theta'   (+ theta (* dt theta-dot'))]
    [x' x-dot' theta' theta-dot']))

(def ^:private canonical-cfg
  "The fixed cartpole config the golden pins. OpenAI Gym cartpole-v1 defaults where
  they exist (masses, half-length, gravity, force_mag, dt)."
  {:cart-mass        1.0
   :pole-mass        0.1
   :pole-half-length 0.5
   :gravity          9.8
   :force-mag        10.0
   :dt               0.02})

(def ^:private canonical-state
  "Slightly tilted (theta=0.2 rad), zero velocity — a non-trivial step that exercises
  every term (sin/cos, the temp/theta_acc/x_acc chain, both semi-implicit updates)."
  [0.0 0.0 0.2 0.0])

(def ^:private canonical-action
  "Push right at unit force (well within force_mag=10)."
  1.0)

(defn canonical-input
  "The fixed input the compute-golden regenerates from: state, action, and cfg.
  Both `bb gen-compute-golden` and the golden test read this so they can never drift
  apart on what 'the input' is."
  []
  {:state  canonical-state
   :action canonical-action
   :cfg    canonical-cfg})

(defn canonical-step
  "Run `step` on `canonical-input`. Pure; deterministic."
  []
  (let [{:keys [state action cfg]} (canonical-input)]
    (step cfg state action)))

(defn output-bytes
  "Serialize a 4-vector state to stable bytes for hashing. Each component is formatted
  with `%.12g` (12 significant digits — matching the 1e-12 tolerance the geometry
  goldens already use) so sub-ULP cross-architecture differences in `Math/sin`/`cos`
  don't perturb the hash, then joined and UTF-8 encoded."
  [[x x-dot theta theta-dot]]
  (.getBytes
    (str (format "%.12g" (double x))    " "
         (format "%.12g" (double x-dot)) " "
         (format "%.12g" (double theta)) " "
         (format "%.12g" (double theta-dot)))
    "UTF-8"))

(defn output-hash
  "SHA-256 hex of `output-bytes`. Deterministic across regenerations on the same
  input — the value the golden fixture pins."
  [state]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        bs (.digest md (output-bytes state))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xFF)) bs))))

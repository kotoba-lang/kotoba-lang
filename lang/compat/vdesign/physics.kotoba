(ns vdesign.physics
  "PhysicsGovernor — the INDEPENDENT censor of the design actor.

  Robotaxi seals AR1 behind a SafetyGovernor that can reject any
  trajectory. The mirror image here: the DesignProposer is sealed behind
  this governor, which enforces the conservation laws the proposer is
  free to ignore. It answers one question authoritatively:

      does this concept CLOSE? — i.e. is there a self-consistent curb
      mass at which the energy store sized for the required range, plus
      the glider and motor, equals that same mass, AND fits the package
      envelope, AND stays under the gross-mass limit?

  The hard part is the MASS SPIRAL: a heavier car needs a bigger store,
  which makes it heavier, which needs a bigger store… For feasible
  designs this converges to a fixed point; for over-reach (e.g. a 1500 km
  city BEV on today's cells) it DIVERGES — and divergence is the
  governor's reject signal, the design-side equivalent of an MRC.

  The whole spiral is a BOUNDED fixed-point iteration encapsulated in ONE
  node call, so — exactly like robotaxi — one graph run = one design pass,
  not an unbounded inner loop."
  (:require [vdesign.powertrain :as pt]))

(def ^:const max-iterations 60)
(def ^:const tolerance-kg 1.0)
(def ^:const occupant-load-kg 150.0)   ; representative operating load for road-load

(defn- operating-mass
  "Mass the road load actually sees: curb + a representative occupant load."
  [curb-kg]
  (+ curb-kg occupant-load-kg))

(defn close-mass
  "Iterate the mass spiral to a fixed point. Returns
  {:converged? :curb-mass-kg :store :iterations :history}.

  Each step: size the energy store at the current operating mass, recompute
  curb = glider + store(+motor); stop when curb stops moving (converged) or
  it runs past the gross limit / iteration budget (diverged).

  opts (optional map):
    :store-fn — optional (fn [glider concept mass-kg] -> store). When
    supplied it REPLACES the pt/size dispatch for every spiral step (the
    cartridge-fed path uses this); when absent the behavior is exactly
    the pre-existing tank/battery dispatch. The returned store must
    carry :mass-kg and :store-mass-kg numbers like pt/size results."
  [powertrain glider concept & [{:keys [store-fn]}]]
  (let [glider-mass (:glider-mass concept)
        gross       (:gross-limit concept)]
    (loop [curb (double (:target-curb-kg concept))   ; seed from the proposer's guess
           n    0
           hist []]
      (let [store (if store-fn
                    (store-fn glider concept (operating-mass curb))
                    (pt/size powertrain glider concept (operating-mass curb)))
            curb' (+ glider-mass (:mass-kg store))
            hist' (conj hist (Math/round curb'))]
        (cond
          (< (Math/abs (- curb' curb)) tolerance-kg)
          {:converged? true  :curb-mass-kg curb' :store store
           :iterations n :history hist'}

          (or (>= n max-iterations) (> curb' (* 1.05 gross)))
          {:converged? false :curb-mass-kg curb' :store store
           :iterations n :history hist'
           :reason (if (> curb' (* 1.05 gross))
                     :mass-spiral-runaway :no-fixed-point)}

          :else (recur curb' (inc n) hist'))))))

(defn check
  "Run the full feasibility censor on a proposer concept. Returns a verdict
  {:closes? :violations [..] :curb-mass-kg :store :margins {..} :iterations}.

  Violations are independent gates — a design must pass ALL of them. This
  is the design-actor invariant: nothing the governor hasn't closed and
  cleared on every gate may leave the actor as a spec.

  opts — optional map passed through to `close-mass` (:store-fn). A store
  reporting :unmeasured :volume-L (cartridge path) makes the packaging
  gate UNVERIFIABLE: it is listed in :unverified-gates and never reads as
  a silent pass or fail."
  [powertrain glider concept & [{:keys [store-fn] :as opts}]]
  (let [{:keys [converged? curb-mass-kg store iterations reason history]}
        (close-mass powertrain glider concept opts)
        gross    (:gross-limit concept)
        avail-v  (:avail-volume-L concept)
        store-v  (:volume-L store)
        store-v-num? (number? store-v)
        store-frac (/ (:store-mass-kg store) curb-mass-kg)
        viol (cond-> []
               (not converged?)
               (conj {:gate :mass-closure :reason reason
                      :detail (str "curb mass does not converge ("
                                   (Math/round curb-mass-kg) " kg and climbing); "
                                   "store technology cannot hold "
                                   (:range-km concept) " km at this class.")})

               (and converged? (> curb-mass-kg gross))
               (conj {:gate :gross-mass
                      :detail (str (Math/round curb-mass-kg) " kg curb exceeds "
                                   gross " kg gross-mass limit.")})

               (and converged? store-v-num? (> store-v avail-v))
               (conj {:gate :packaging
                      :detail (str (Math/round (double store-v)) " L energy store "
                                   "exceeds " avail-v " L package envelope ("
                                   (name powertrain) ").")})

               (and converged? (> store-frac 0.45))
               (conj {:gate :store-fraction
                      :detail (str (Math/round (* 100 store-frac))
                                   "% of curb mass is energy store (>45% — "
                                   "structurally/commercially unviable).")}))]
    {:closes?      (and converged? (empty? viol))
     :violations   viol
     :unverified-gates (if (and converged? (not store-v-num?)) [:packaging] [])
     :curb-mass-kg curb-mass-kg
     :store        store
     :iterations   iterations
     :history      history
     :margins      {:gross-kg     (- gross curb-mass-kg)
                    :volume-L     (if store-v-num? (- avail-v store-v) :unmeasured)
                    :store-frac   store-frac}}))

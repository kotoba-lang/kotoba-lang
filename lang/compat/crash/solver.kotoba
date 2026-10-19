(ns crash.solver
  "Reduced-order frontal-crash solver (:rom-crash) — energy-balance crush model.
  The kinetic energy at impact is absorbed over the front crush length by the
  rails at a mean crush force; that force sets the cabin deceleration and the
  rail stress, hence the structural safety factor. Replaces the closed-form SF
  proxy in vehicle-design-actor/simverify with a mechanistic estimate.

  A validated kami-cae explicit FEA registers :explicit-fea on the same
  cae-solver contract; callers swap backends via [:solver :kind]."
  (:require [vphysics.core :as phys]
            [datom.core :as d]
            [cae.solver :as cae]))

;; Material yield (MPa) library — automotive structural alloys/steels.
(def materials
  {:AA6082-T6 250 :AA6061-T6 276 :DP600 350 :DP980 700 :boron-PHS 1400})

(defn- crush
  "Energy-balance crush. KE = ½ m v²; absorbed = F_mean · crush-len.
  F_mean = KE / crush-len; decel a = F_mean/m; rail stress σ = F_mean/A_rails."
  [{:keys [mass-kg impact-kmh crush-len-m rail-area-mm2 material]
    :or {impact-kmh 56 crush-len-m 0.6 rail-area-mm2 1600 material :DP600}}]
  (let [v   (/ impact-kmh 3.6)
        ke  (* 0.5 mass-kg v v)
        fm  (/ ke crush-len-m)               ; N
        a-g (/ (/ fm mass-kg) phys/g)        ; cabin decel in g
        sig (/ fm (* rail-area-mm2 1e-6) 1e6) ; MPa
        yld (get materials material 350)
        sf  (/ yld sig)]
    {:ke-J ke :crush-force-N fm :decel-g a-g
     :rail-stress-MPa sig :yield-MPa yld :SF sf
     :material material}))

(defn solve [case]
  (let [r (crush case)]
    (assoc r :solver :rom-crash :pass? (and (>= (:SF r) 1.5) (<= (:decel-g r) 40.0)))))

(defmethod cae/solve :rom-crash [case] (solve case))

(defn run
  "Solve + datafy a crash case. Returns the result with :datoms."
  [case]
  (let [r   (solve case)
        cid (or (:case/id case) "crash-0")
        ent (d/entity "crash" :CrashRun cid
                      {:material (name (:material r))
                       :decelG   (Math/round (* 10.0 (:decel-g r)))
                       :stressMPa (Math/round (double (:rail-stress-MPa r)))
                       :SF       (Math/round (* 100.0 (:SF r)))
                       :pass     (:pass? r)})
        led (d/log [ent])]
    (assoc r :datoms (:datoms led) :datom-count (:count led))))

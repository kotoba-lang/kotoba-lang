(ns ao.lineage
  "Finite organism identity, relational wellbecoming, and governed succession.

  Extracted from `kotoba.tamaki.lineage` (kotoba-lang/tamaki ADR-0002,
  'finite relational lineage and wellbecoming') and made portable. Two
  tamaki-specific things were removed on the way out: the `\"Tamaki\"`
  family-name default, and the assumption that 30 days is everyone's cap.
  Both are now parameters — the *shape* of the decision is shared, the
  values belong to whoever runs the fleet.

  The lease belongs to an **incarnation**, not to the AO. tamaki ADR-0001 is
  explicit that a maximum-30-day named life is an incarnation stewarding an
  AO, and is not the repository-bound AO itself. That AO persists —
  archiving makes it dormant, never gone (see `ao.identity`). What expires
  is the named individual currently stewarding it.

  Why only this layer has a lease at all: an AO rewrites its own definition,
  so no bound it could edit is a bound. A temporal one is the only kind that
  survives self-modification. See `ao.evolution`."
  (:require [clojure.string :as str]))

(def day-ms 86400000)

(def default-max-lifetime-ms
  "tamaki ADR-0002's 30-day external lease, kept as the default so an
  unconfigured fleet inherits the reviewed bound rather than none."
  (* 30 day-ms))

(def wellbeing-dimensions
  [:human-agency :relational-trust :inheritable-learning
   :future-optionality :succession-integrity])

(defn clamp [value]
  (-> (double (or value 0.0)) (max 0.0) (min 1.0)))

(defn organism
  "Create one finite individual. Callers may request a shorter lifetime but
  never more than `max-lifetime-ms` — a lease you can extend from inside is
  not a lease."
  ([spec now-ms] (organism spec now-ms default-max-lifetime-ms))
  ([{:keys [id family-name given-name generation parent born-at lifetime-ms]
     :or {generation 1}}
    now-ms max-lifetime-ms]
   (let [born-at (or born-at now-ms)
         lifetime-ms (or lifetime-ms max-lifetime-ms)]
     (when (str/blank? (str family-name))
       (throw (ex-info "Organism requires a family name"
                       {:field :organism/family-name})))
     (when (str/blank? (str given-name))
       (throw (ex-info "Organism requires a given name"
                       {:field :organism/given-name})))
     (when (or (not (pos-int? lifetime-ms))
               (> lifetime-ms max-lifetime-ms))
       (throw (ex-info "Organism lifetime must be positive and within the fleet maximum"
                       {:lifetime-ms lifetime-ms :maximum-ms max-lifetime-ms})))
     {:organism/version 1
      :organism/id (or id
                       (str (str/lower-case family-name) "-"
                            (str/lower-case given-name) "-"
                            generation))
      :organism/family-name family-name
      :organism/given-name given-name
      :organism/generation generation
      :organism/parent parent
      :organism/born-at born-at
      :organism/expires-at (+ born-at lifetime-ms)
      :organism/lifetime-ms lifetime-ms})))

(defn life-phase
  "Where in its lease an individual is. The bands exist so succession starts
  while the parent is still healthy enough to hand over well, rather than at
  the moment it stops."
  [individual now-ms]
  (let [born (:organism/born-at individual)
        expires (:organism/expires-at individual)
        age (- now-ms born)
        lifetime (- expires born)
        ratio (if (pos? lifetime) (/ age (double lifetime)) 1.0)]
    (cond
      (< now-ms born) :not-born
      (>= now-ms expires) :expired
      (< ratio 0.70) :active-life
      (< ratio 0.90) :succession-planning
      :else :handover)))

(defn expired? [individual now-ms]
  (= :expired (life-phase individual now-ms)))

(defn lineage-vitality
  "Geometric mean across the wellbeing dimensions.

  Geometric, not arithmetic, on purpose: no dimension can be compensated
  away by maximizing another. An AO with perfect throughput and zero human
  agency scores near zero, which is the intended reading."
  [observation]
  (let [values (map #(clamp (get observation % 0.0)) wellbeing-dimensions)]
    (Math/pow (reduce * values) (/ 1.0 (count wellbeing-dimensions)))))

(defn action-gate
  "The organism-level gate for an action.

  Relational existence does not erase human boundaries: low agency pauses
  work, and reproduction always needs explicit signed human consent — there
  is no vitality score high enough to substitute for it."
  [{:keys [individual now-ms wellbecoming action human-consent? consent-signature]}]
  (cond
    (expired? individual now-ms) :expired
    (< (clamp (:human-agency wellbecoming)) 0.5) :repair-relationship
    (and (= action :reproduce)
         (not (and human-consent? (seq consent-signature))))
    :approval-required
    (< (lineage-vitality wellbecoming) 0.35) :rest-and-repair
    :else :allowed))

(defn inheritable-memes
  "Only explicitly inheritable memes with provenance and consent scope cross
  a generation boundary. Everything else dies with the individual, which is
  the point of a finite lease."
  [memes]
  (->> memes
       (filter #(and (:meme/inheritable? %)
                     (seq (:meme/provenance %))
                     (:meme/consent %)))
       vec))

(defn succession-plan
  [{:keys [parent child-name now-ms wellbecoming memes
           human-consent? consent-signature max-lifetime-ms]
    :or {max-lifetime-ms default-max-lifetime-ms}}]
  (let [gate (action-gate {:individual parent :now-ms now-ms
                           :wellbecoming wellbecoming :action :reproduce
                           :human-consent? human-consent?
                           :consent-signature consent-signature})]
    (cond-> {:succession/status (if (= :allowed gate) :approved :blocked)
             :succession/gate gate
             :succession/parent (:organism/id parent)
             :succession/proposed-name child-name
             :succession/memes (inheritable-memes memes)}
      (= :allowed gate)
      (assoc :succession/child
             (organism {:family-name (:organism/family-name parent)
                        :given-name child-name
                        :generation (inc (:organism/generation parent))
                        :parent (:organism/id parent)}
                       now-ms max-lifetime-ms)
             :succession/consent-signature consent-signature))))

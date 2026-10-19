(ns valueflows.datom
  "Project Valueflows records onto this workspace's datom plane.

   The plane is the one `manifest/edn-query.cljs` reads (ADR-2607171600 /
   ADR-2607252000): entity maps tagged with `:source/dataset`, queried with
   DataScript, joined across datasets by shared keys. Valueflows records enter
   it as `:source/dataset \"valueflows\"`.

   Two join keys are asserted, both because they already exist on the plane:

     :company/lei  an Agent that is a legal entity -> market-intel financials,
                   cloud-itonami-lei blueprints, cloud-itonami-lei-tos
     :repo/path    the repository whose actor recorded the flow ->
                   repo-taxonomy, repo-maturity

   No other join is claimed. An agent with no LEI simply has no LEI here; it
   is not given a synthesised one (a fabricated key joins to the wrong row,
   which is worse than not joining).

   ONE REF, ON PURPOSE: kotobase reaches exactly one ref for a Datalog join
   (ADR-260726-kotobase-query-plane-is-one-ref), so events, resources,
   processes and agents are projected into ONE dataset. Splitting them by
   volume would make 'who produced the resource this event consumed'
   unanswerable — the exact question the plane exists for."
  (:require [valueflows.vocabulary :as vocab]))

(def dataset "valueflows")

(defn- base [i] {:db/id (- (inc i)) :source/dataset dataset})

(defn- measure->attrs [prefix m]
  (when (map? m)
    (cond-> {}
      (some? (:has-numerical-value m))
      (assoc (keyword (str prefix "-value")) (:has-numerical-value m))
      (some? (:has-unit m))
      (assoc (keyword (str prefix "-unit")) (name (:has-unit m))))))

(defn- agent-attrs [prefix a]
  (cond
    (nil? a) {}
    (map? a) (cond-> {(keyword (str prefix "-id")) (str (or (:id a) (:name a)))}
               (:company/lei a) (assoc :company/lei (:company/lei a))
               (:name a) (assoc (keyword (str prefix "-name")) (:name a)))
    :else {(keyword (str prefix "-id")) (str a)}))

(defn event->entity
  "One EconomicEvent -> one entity map. The behaviour cells are denormalised
   onto the event so a query can ask 'which events incremented an inventory'
   without also loading the vocabulary."
  [i event]
  (let [a (:action event)]
    (merge
     (base i)
     {:vf.event/action (some-> a name)}
     (when (vocab/action? a)
       {:vf.event/input-output (some-> (vocab/input-output a) name)
        :vf.event/accounting-effect (some-> (vocab/effect a :accounting-effect) name)
        :vf.event/onhand-effect (some-> (vocab/effect a :onhand-effect) name)
        :vf.event/effort-based? (vocab/effort-based? a)})
     (agent-attrs "vf.event/provider" (:provider event))
     (agent-attrs "vf.event/receiver" (:receiver event))
     (measure->attrs "vf.event/resource-quantity" (:resource-quantity event))
     (measure->attrs "vf.event/effort-quantity" (:effort-quantity event))
     (cond-> {}
       (:resource-inventoried-as event)
       (assoc :vf.event/resource-inventoried-as (str (:resource-inventoried-as event)))
       (:to-resource-inventoried-as event)
       (assoc :vf.event/to-resource-inventoried-as (str (:to-resource-inventoried-as event)))
       (:input-of event) (assoc :vf.event/input-of (str (:input-of event)))
       (:output-of event) (assoc :vf.event/output-of (str (:output-of event)))
       (:has-point-in-time event) (assoc :vf.event/has-point-in-time (str (:has-point-in-time event)))
       (:fulfills event) (assoc :vf.event/fulfills (str (:fulfills event)))
       (:satisfies event) (assoc :vf.event/satisfies (str (:satisfies event)))
       (:note event) (assoc :vf.event/note (:note event))
       (:repo/path event) (assoc :repo/path (:repo/path event))))))

(defn resource->entity [i [res-id r]]
  (merge
   (base i)
   {:vf.resource/id (str res-id)}
   (measure->attrs "vf.resource/accounting-quantity" (:accounting-quantity r))
   (measure->attrs "vf.resource/onhand-quantity" (:onhand-quantity r))
   (agent-attrs "vf.resource/primary-accountable" (:primary-accountable r))
   (cond-> {}
     (:current-location r) (assoc :vf.resource/current-location (str (:current-location r)))
     (:contained-in r) (assoc :vf.resource/contained-in (str (:contained-in r)))
     (:stage r) (assoc :vf.resource/stage (str (:stage r)))
     (:state r) (assoc :vf.resource/state (str (:state r)))
     (:conforms-to r) (assoc :vf.resource/conforms-to (str (:conforms-to r))))))

(defn commitment->entity
  "A Commitment or Intent. Same dataset as events and resources on purpose:
   'which promise did this event settle' is a join, and a separate ref would
   put the two halves out of reach of one another."
  [i c]
  (let [a (:action c)]
    (merge
     (base i)
     {:vf.commitment/id (str (:id c))
      :vf.commitment/action (some-> a name)}
     (when (vocab/action? a)
       {:vf.commitment/effort-based? (vocab/effort-based? a)})
     (agent-attrs "vf.commitment/provider" (:provider c))
     (agent-attrs "vf.commitment/receiver" (:receiver c))
     (measure->attrs "vf.commitment/resource-quantity" (:resource-quantity c))
     (measure->attrs "vf.commitment/effort-quantity" (:effort-quantity c))
     (measure->attrs "vf.commitment/minimum-quantity" (:minimum-quantity c))
     (measure->attrs "vf.commitment/available-quantity" (:available-quantity c))
     (cond-> {}
       (:resource-conforms-to c) (assoc :vf.commitment/resource-conforms-to (str (:resource-conforms-to c)))
       (:due c) (assoc :vf.commitment/due (str (:due c)))
       ;; `finished` is recorded as given, including absent. Upstream defaults
       ;; it to false, but "recorded false" and "never stated" are different
       ;; facts and only one of them was measured.
       (contains? c :finished) (assoc :vf.commitment/finished (boolean (:finished c)))
       (:satisfies c) (assoc :vf.commitment/satisfies (str (:satisfies c)))
       (:clause-of c) (assoc :vf.commitment/clause-of (str (:clause-of c)))
       (:independent-demand-of c) (assoc :vf.commitment/independent-demand-of
                                         (str (:independent-demand-of c)))
       (:planned-within c) (assoc :vf.commitment/planned-within (str (:planned-within c)))
       (:repo/path c) (assoc :repo/path (:repo/path c))))))

(defn coverage->entity
  "What was NOT projected. Declared as an entity so a count query cannot read
   as complete coverage — the same device `:concept/coverage` uses."
  [i {:keys [events-in events-out resources-in resources-out
             commitments-in commitments-out skipped-reasons]}]
  (merge (base i)
         {:vf.coverage/events-offered events-in
          :vf.coverage/events-projected events-out
          :vf.coverage/resources-offered resources-in
          :vf.coverage/resources-projected resources-out
          :vf.coverage/commitments-offered (or commitments-in 0)
          :vf.coverage/commitments-projected (or commitments-out 0)
          :vf.coverage/complete? (and (= events-in events-out)
                                      (= resources-in resources-out)
                                      (= (or commitments-in 0) (or commitments-out 0)))}
         (when (seq skipped-reasons)
           {:vf.coverage/skipped-reasons (pr-str skipped-reasons)})))

(defn project
  "events + inventory (+ commitments) -> tx-data ready for `d/transact`,
   ending with one coverage entity. Anything that is not a map is skipped and
   counted, never silently dropped.

   Commitments and intents go in the SAME dataset as the events that settle
   them. `vf.event/fulfills` -> `vf.commitment/id` is the join that answers
   'was this promise kept', and splitting the two would put the halves out of
   reach of one another."
  ([events inventory] (project events inventory nil))
  ([events inventory commitments]
   (let [evs (vec (filter map? events))
         res (vec (filter (fn [[_ v]] (map? v)) inventory))
         cms (vec (filter map? commitments))
         n-e (count evs)
         n-r (count res)
         ents (concat (map-indexed event->entity evs)
                      (map-indexed (fn [i r] (resource->entity (+ i n-e) r)) res)
                      (map-indexed (fn [i c] (commitment->entity (+ i n-e n-r) c)) cms))
         skipped (cond-> []
                   (< n-e (count events)) (conj :non-map-event)
                   (< n-r (count inventory)) (conj :non-map-resource)
                   (< (count cms) (count commitments)) (conj :non-map-commitment))]
     (conj (vec ents)
           (coverage->entity (+ n-e n-r (count cms))
                             {:events-in (count events) :events-out n-e
                              :resources-in (count inventory) :resources-out n-r
                              :commitments-in (count commitments)
                              :commitments-out (count cms)
                              :skipped-reasons skipped})))))

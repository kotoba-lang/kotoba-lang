(ns authentication.datom
  "Model values as Datomic transaction data.

  Each entity carries its identity as a real attribute (`:authn.factor/id`,
  `:authn.decision/request-id`), declared `:db.unique/identity` in
  `authentication.schema`. `:db/id` holds the same string as a tempid, which
  is what makes the two read together -- but the *attribute* is what survives
  the transaction. A string `:db/id` on its own is scoped to one transaction,
  so it could never have made two transactions about the same decision refer
  to one entity.

  Attributes with no value are dropped rather than asserted as nil, so what
  is emitted here is tx-data a real transactor would accept.")

(defn- prune [m]
  (reduce-kv (fn [acc a v] (if (nil? v) acc (assoc acc a v))) {} m))

(defn factor-datoms [factor]
  [(prune {:db/id (:authn.factor/id factor)
           :authn.factor/id (:authn.factor/id factor)
           :authn.factor/type (:authn.factor/type factor)
           :authn.factor/ok? (:authn.factor/ok? factor)
           :authn.factor/subject (:authn.factor/subject factor)
           :authn.factor/evidence-ref (:authn.factor/evidence-ref factor)
           :authn.factor/assurance (:authn.factor/assurance factor)
           :authn.factor/at (:authn.factor/at factor)})])

(defn factor-request-datoms [factor-request]
  [(prune {:db/id (:authn.factor-request/id factor-request)
           :authn.factor-request/id (:authn.factor-request/id factor-request)
           :authn.factor-request/type (:authn.factor-request/type factor-request)
           :authn.factor-request/subject (:authn.factor-request/subject factor-request)
           :authn.factor-request/challenge-ref (:authn.factor-request/challenge-ref factor-request)
           :authn.factor-request/purpose (:authn.factor-request/purpose factor-request)
           :authn.factor-request/created-at (:authn.factor-request/created-at factor-request)})])

(defn decision-datoms
  "The decision entity. `:authn.decision/factors` references the factor
  entities by the tempid their datoms are emitted under, so the two have to
  be transacted together -- which is what `persist-decision!` does. A tempid
  reference (rather than a lookup ref) is also what a real Datomic
  transactor accepts for an entity created in the same transaction."
  [decision]
  [(prune {:db/id (:authn.decision/request-id decision)
           :authn.decision/request-id (:authn.decision/request-id decision)
           :authn.decision/subject (:authn.decision/subject decision)
           :authn.decision/decision (:authn.decision/decision decision)
           :authn.decision/level (:authn.decision/level decision)
           :authn.decision/factors (mapv :authn.factor/id (:authn.decision/factors decision))
           :authn.decision/issued-at (:authn.decision/issued-at decision)})])

(defn decision-tx
  "A decision together with the factors it aggregated -- the transaction unit
  that keeps `:authn.decision/factors` resolvable."
  [decision]
  (into (vec (mapcat factor-datoms (:authn.decision/factors decision)))
        (decision-datoms decision)))

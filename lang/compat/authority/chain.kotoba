(ns authority.chain
  "Folding a delegation into the authority it actually confers.

  ## What this does not do

  It does not authenticate anything. No signature is verified here, no key is
  read, and no linkage between a parent's audience and a child's issuer is
  checked. Those are `cacao.core/verify-chain`'s, and a fold of unverified
  links is a fold of whatever the presenter wrote.

  Saying that here rather than in a note further down is the point: this
  namespace answers `how much authority does this chain carry`, and a reader
  who mistakes it for `is this chain genuine` has built the exact gap the
  superproject's own review found in `kotobase.guarded` — a gate that looks
  like the check and is not on the path where the check happens.

  ## Why the fold is a meet

  `verify-chain` today verifies each parent→child pair and then reports the
  *leaf's* resource set as effective. That is correct while every pair
  verified, and it is one forgotten branch away from not being.

      effective = meet(link₀, link₁, … linkₙ)

  Because `meet` is a greatest lower bound, the result is covered by every
  link by construction. A link that claims more than its parent does not
  need to be rejected for the fold to be safe — it simply contributes
  nothing, because there is no value the fold could produce that some link
  did not already grant. Escalation is not defended against; it is
  arithmetically absent.

  `attempts` still reports every claim a parent did not confer. Safety comes
  from the algebra, evidence comes from the report, and neither stands in for
  the other."
  (:require [authority.grant :as grant]))

(defn fold
  "GRANTS root-first, leaf-last → what the chain confers and what it tried to.

  An empty chain confers `grant/nothing`, which is the same answer as a chain
  whose links share no scope — one code path, so `no links` cannot be read as
  `no restrictions`."
  [grants]
  (let [links (vec (filter map? grants))]
    {:chain/effective (if (seq links)
                        (reduce grant/meet links)
                        grant/nothing)
     :chain/attempts (into []
                           (keep-indexed
                            (fn [i child]
                              (when (pos? i)
                                (let [e (grant/escalation (nth links (dec i)) child)]
                                  (when (seq e) (assoc e :index i))))))
                           links)
     :chain/depth (count links)}))

(defn authorize
  "The single entry point a caller should reach for: fold the chain, then ask.

  Returns the decision *and* what it was made from, because a decision whose
  basis is not recorded cannot be audited later, and the caller is the only
  one holding both at this moment.

  `:strict?` refuses a chain that attempted escalation even though the fold
  already neutralised it. Off by default — a presenter who over-claims is
  usually a stale client, and denying the whole request turns a harmless
  drafting error into an outage. On, for anything where an over-claim should
  be treated as an attack."
  [{:keys [chain requested now holder strict?]}]
  (let [{:chain/keys [effective attempts depth]} (fold chain)
        blocked? (and strict? (seq attempts))
        ok? (and (not blocked?)
                 (grant/authorized? effective requested {:now now :holder holder}))]
    {:authority/allowed? ok?
     :authority/reason (cond ok? :granted
                             blocked? :escalation-attempted
                             (zero? depth) :empty-chain
                             (not (grant/live? effective now)) :expired-or-no-trusted-time
                             (and (:grant/holder effective)
                                  (not= holder (:grant/holder effective))) :wrong-holder
                             :else :out-of-scope)
     :authority/effective effective
     :authority/attempts attempts
     :authority/depth depth}))

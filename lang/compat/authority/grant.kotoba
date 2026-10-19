(ns authority.grant
  "A scope set with a holder and a bound — what a verified token carries once
  the signature is off it.

  This namespace is deliberately crypto-free. Whether a CACAO verified, whose
  key signed it and whether its nonce was replayed are questions for
  `cacao.core`; whether the authority it carries reaches what is being asked
  is this question, and keeping them apart is what lets the second one be
  checked exhaustively on both platforms with no key material in the test.

  ## `nil` expiry is +∞, and that is why `meet` closes a hole

  `cacao.core/link-problems` compares expiries only `(when (and (:exp parent)
  (:exp child)))`, so a child link carrying *no* `exp` under a parent that has
  one is not reported. The chain fold there still takes the minimum, so the
  effective bound is right — but the check and the fold disagree, and only one
  of them is looked at during review.

  Here there is one rule: an absent bound is no bound, `earlier` treats it as
  +∞, and `meet` therefore returns the parent's. Unbounded children cannot
  extend a bounded parent because the arithmetic will not let them, and
  `escalation` reports the attempt so it is still visible."
  (:require [authority.scope :as scope]))

(defn grant
  "Normalise a grant. SCOPES may be scopes or resource strings; anything
  unparseable lands in `:grant/rejected` instead of quietly narrowing the
  result to something the caller cannot account for."
  [{:keys [scopes holder expires]}]
  (let [strings (filter string? scopes)
        parsed (scope/parse-all strings)
        direct (filter vector? scopes)]
    {:grant/scopes (scope/normalize (concat direct (:scopes parsed)))
     :grant/holder holder
     :grant/expires expires
     :grant/rejected (:rejected parsed)}))

(def nothing
  "The grant that reaches nothing — the empty antichain, not a sentinel.
  Every failure in this library returns this, and it is safe for the same
  reason an ordinary empty grant is: `covered?` on it is false."
  {:grant/scopes #{} :grant/holder nil :grant/expires nil :grant/rejected []})

(defn- earlier
  "The tighter of two bounds, where nil means unbounded.

  Instants are compared as strings, the same convention `cacao.core` uses.
  That is correct for ISO-8601 UTC and wrong for anything else, so callers
  normalise before they get here — mixing offsets would make this silently
  order two valid instants backwards."
  [a b]
  (cond (nil? a) b
        (nil? b) a
        (neg? (compare a b)) a
        :else b))

(defn meet
  "The greatest authority both grants confer.

  The holder is the child's: a delegation ends at whoever holds the leaf.
  Scopes meet as antichains, bounds meet as the earlier of the two."
  [parent child]
  {:grant/scopes (scope/meet-sets (:grant/scopes parent) (:grant/scopes child))
   :grant/holder (:grant/holder child)
   :grant/expires (earlier (:grant/expires parent) (:grant/expires child))
   :grant/rejected (into (vec (:grant/rejected parent)) (:grant/rejected child))})

(defn escalation
  "What CHILD claimed that PARENT does not confer — evidence, never the
  safety mechanism.

  `meet` already makes escalation impossible to *achieve*; this makes an
  attempt impossible to *miss*. Both matter: the first keeps a forged link
  from working, the second keeps it from being invisible."
  [parent child]
  (let [uncovered (into #{} (remove #(scope/covered? (:grant/scopes parent) %))
                        (:grant/scopes child))
        pe (:grant/expires parent)
        ce (:grant/expires child)
        extended? (and (some? pe) (or (nil? ce) (pos? (compare ce pe))))]
    (cond-> {}
      (seq uncovered) (assoc :escalated-scopes uncovered)
      extended? (assoc :extended-expiry {:parent pe :child ce}))))

(defn covers?
  "Does PARENT confer at least everything CHILD claims, for at least as long?"
  [parent child]
  (empty? (escalation parent child)))

(defn live?
  "Is the grant unexpired at NOW? An absent bound is unbounded; an absent NOW
  means the caller did not supply trusted time, and a decision that needs a
  clock and has none is a denial, not a pass."
  [{:grant/keys [expires]} now]
  (cond (nil? expires) (string? now)
        (not (string? now)) false
        :else (neg? (compare now expires))))

(defn authorized?
  "The whole question, in one place: does GRANT reach REQUESTED, right now,
  in the hands of HOLDER?

  Every conjunct is required. There is no arity that omits the clock, and no
  arity that omits the holder — the two things a hurried caller drops first."
  [grant requested {:keys [now holder]}]
  (boolean
   (and (map? grant)
        (live? grant now)
        (or (nil? (:grant/holder grant)) (= holder (:grant/holder grant)))
        (scope/covered? (:grant/scopes grant)
                        (if (string? requested) (scope/parse requested) requested)))))

(ns biscuit.authority
  "A verified biscuit becomes an inert `authority` grant — the same one-way
  door `macaroon.authority` is, for the same reason.

  Two wires, one decider. The workspace has measured what a second decider
  costs: `authority.scope` exists because `covers?` had been written once per
  URI scheme, and one of those copies compared strings with `starts-with?`.
  Adding biscuits must therefore add **no** new answer to *does this cover
  that*.

  So the mapping is deliberately narrow. A biscuit's Datalog can express far
  more than a scope lattice can, and the surplus does **not** become
  authority: only facts of the shape `[scope \"kotoba://…\"]`, `[before
  \"…\"]` and `[holder \"did:…\"]` are read, everything else is ignored **for
  the purpose of granting** while still being available to the token's own
  checks and the authorizer's policies. Ignoring is safe in exactly one
  direction, and this is that direction: an unread fact cannot widen a
  grant."
  (:require [authority.grant :as grant]))

(defn- earlier [a b]
  (cond (nil? a) b (nil? b) a (neg? (compare a b)) a :else b))

(defn ->grant
  "Fold the token's scope/before/holder facts, optionally onto `base`.

  One argument reads what the token grants ON ITS OWN, which is what a root
  verifier wants. Two folds it onto a prior grant, which is what a delegate
  wants. `base` must be a real prior authority -- passing `{}` means the empty
  antichain, and meeting anything with it yields nothing.

  **Within a block, scope facts are alternatives; across blocks they
  narrow.** That asymmetry is the whole mapping: a block saying
  `scope(\"a\")` and `scope(\"b\")` is offering two places it may be used,
  while a *later* block adding `scope(\"a\")` is restricting the token to
  one of them. Meeting every fact separately — the shape this was first
  written as — makes a block with two scopes reach nothing, which is safe
  and wrong.

  Blocks fold in order, so a later block can only narrow: the same
  monotonicity `biscuit.token/scope` gives checks, applied to authority."
  ([t] (->grant t nil))
  ([t base]
  (let [blocks (:biscuit/blocks t)
        per-block (keep (fn [b]
                          (let [ss (keep (fn [[p v]] (when (= 'scope p) v))
                                         (:block/facts b))]
                            (when (seq ss) (grant/grant {:scopes (vec ss)}))))
                        blocks)
        all-facts (mapcat :block/facts blocks)
        befores (keep (fn [[p v]] (when (= 'before p) v)) all-facts)
        holder (some (fn [[p v]] (when (= 'holder p) v)) all-facts)
        ;; With a base, fold the token onto the delegator's prior authority.
        ;; WITHOUT one, fold the token against itself.
        ;;
        ;; There is no top element in this lattice -- `scope/meet-sets` is a
        ;; cross product, so meeting anything with the empty antichain gives
        ;; the empty antichain. A verifier holding no prior grant therefore
        ;; could not express "what does this token grant on its own": passing
        ;; `{}` reads like an unconstrained base and silently yields NOTHING.
        ;;
        ;; That is not hypothetical. `cloud-itonami/actor-hanmoto` called
        ;; `(->grant m {})` to read a billing scope and got an empty set for
        ;; every token, which surfaced as `:no-scope-in-token` -- a token that
        ;; declared its scope perfectly well, reported as declaring none. It
        ;; went unnoticed because no token had ever reached that line.
        narrowed (if (nil? base)
                   (if (seq per-block)
                     (reduce grant/meet (first per-block) (rest per-block))
                     grant/nothing)
                   (reduce grant/meet (grant/grant base) per-block))]
    (cond-> (assoc narrowed :grant/expires
                   (reduce earlier (:grant/expires narrowed) befores))
      holder (assoc :grant/holder holder)))))

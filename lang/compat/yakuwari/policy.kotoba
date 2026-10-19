(ns yakuwari.policy
  "Human-in-the-loop policy: for a given capability, who decides.

  Lives with the role rather than with the AO. tamaki ADR-0001 lists HIL
  policy under what an AO owns, and that stays true — but the AO owns the
  *population* of roles and their bounds, while the decision for one
  capability is a property of the role holding it. An AO's own policy is
  simply the policy of the role it acts as.

  Complements `kotoba-lang/hil` rather than duplicating it. This namespace
  answers “does this capability need a human at all, and how much of one”;
  `hil.core` is the transaction that asks and returns :approved / :rejected /
  :dismissed. A `:approval-required` decision here is what routes a call into
  `hil/request!`; :autonomous never reaches it.

  The vocabulary is `kotoba.tamaki.actor/hil-decisions`, adopted rather than
  re-invented. A fourth spelling of the same four ideas was drafted while
  scaffolding an AO (`:self-executing` / `:propose` / `:forbidden`); it is
  kept here only as `legacy-aliases`, mapping onto the real names, because
  the failure mode of parallel vocabularies is that a policy reads as
  approved under one and blocked under the other."
  (:require [clojure.string :as str]))

(def decisions
  "Ordered from most to least autonomy. The order is meaningful:
  `strictest-of` resolves conflicts by taking the later one."
  [:autonomous :voice-required :approval-required :blocked])

(def decision-set (set decisions))

(def decision-rank (zipmap decisions (range)))

(def legacy-aliases
  {:self-executing :autonomous
   :propose :approval-required
   :forbidden :blocked})

(defn normalize-decision
  "Accept a legacy spelling, return the real one. Unknown values fail closed
  to `:blocked` — a policy nobody can parse must not read as permission."
  [d]
  (cond
    (contains? decision-set d) d
    (contains? legacy-aliases d) (get legacy-aliases d)
    :else :blocked))

(defn strictest-of
  "The least autonomous of several decisions. Used when a capability is
  reachable by more than one rule: the strict one wins, always."
  [ds]
  (let [normalized (map normalize-decision ds)]
    (if (seq normalized)
      (apply max-key decision-rank normalized)
      :blocked)))

(defn decide
  "What may this actor do with `capability`?

  `policy` is `{capability decision}`. An unlisted capability is `:blocked`,
  not `:autonomous`: a capability nobody wrote a rule for is one nobody
  reviewed, and defaulting those open is how an AO acquires powers by
  omission."
  [policy capability]
  (normalize-decision (get policy capability :blocked)))

(defn may-execute?
  "True only for `:autonomous`. `:voice-required` still executes, but the
  caller owes a notification first — which is why it is not folded in here;
  a caller that treats 'tell someone' as optional would silently downgrade
  it to `:autonomous`."
  [policy capability]
  (= :autonomous (decide policy capability)))

(defn needs-human? [policy capability]
  (contains? #{:approval-required :blocked} (decide policy capability)))

(defn validate-policy
  "Report every problem at once rather than one per round trip.
  Returns `{:ok? bool :problems [...]}`."
  [policy]
  (let [problems
        (cond-> []
          (not (map? policy))
          (conj {:problem :not-a-map})

          :always
          (into (for [[cap d] (when (map? policy) policy)
                      :when (not (or (contains? decision-set d)
                                     (contains? legacy-aliases d)))]
                  {:problem :unknown-decision :capability cap :decision d}))

          :always
          (into (for [[cap _] (when (map? policy) policy)
                      :when (str/blank? (str (if (keyword? cap) (name cap) cap)))]
                  {:problem :blank-capability :capability cap})))]
    {:ok? (empty? problems) :problems (vec problems)}))

(defn deprecated-spellings
  "Which entries still use a legacy alias. Not an error — the aliases
  resolve correctly — but a fleet that never reports them keeps two
  vocabularies alive forever."
  [policy]
  (vec (for [[cap d] (when (map? policy) policy)
             :when (contains? legacy-aliases d)]
         {:capability cap :was d :now (get legacy-aliases d)})))

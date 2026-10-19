(ns ao.evolution
  "The self-evolution gate, and why an AO is the only layer that needs a
  lease.

  An AO rewrites its own definition and holds commit/push/merge authority
  over its own repository. Nothing above it is fixed, so no structural bound
  applies: a policy it could rewrite is not a bound, and a review of code it
  can replace expires the moment it does.

  That leaves exactly one bound that survives self-modification — a
  **temporal** one. The incarnation lease is not a biological flourish; it
  is the only point at which a human is *required* to re-consent to what the
  AO has become.

  A yakuwari needs none of this: it decides, but cannot rewrite its own
  spec, so the human who approved that spec stays correct. An agent needs
  even less: its bound arrives with the request."
  (:require [ao.lineage :as lineage]))

(def write-scopes
  "What an AO may write, in increasing order of authority. `:merge` is
  separate from `:push` on purpose — merging is the act that makes a change
  the repository's official state, and an AO that can merge its own work has
  no reviewer left."
  [:none :commit :push :merge])

(def scope-rank (zipmap write-scopes (range)))

(defn may-write?
  [ao scope]
  (>= (get scope-rank (:ao/write-scope ao) 0)
      (get scope-rank scope 99)))

(defn gate
  "May this AO apply a self-modifying change right now?

  Returns `:allowed` or the reason it is not. The order is deliberate:
  expiry is checked before everything, because an expired incarnation must
  not be able to argue its way past the lease with a good vitality score."
  [{:keys [ao incarnation now-ms wellbecoming change human-consent? consent-signature]}]
  (let [scope (:change/scope change :merge)]
    (cond
      (nil? incarnation) :no-incarnation

      (lineage/expired? incarnation now-ms) :expired

      (not (may-write? ao scope)) :insufficient-write-scope

      ;; Reuse the organism gate for relational health rather than inventing
      ;; a second set of thresholds that could disagree with it.
      (not= :allowed (lineage/action-gate {:individual incarnation
                                           :now-ms now-ms
                                           :wellbecoming wellbecoming
                                           :action :self-modify}))
      (lineage/action-gate {:individual incarnation :now-ms now-ms
                            :wellbecoming wellbecoming :action :self-modify})

      ;; Changing what the AO is allowed to be — its policy, its capabilities,
      ;; its objective, its write scope — is not ordinary work. It is the AO
      ;; editing the terms of its own supervision, and it always needs a
      ;; signed human.
      (and (:change/governing? change)
           (not (and human-consent? (seq consent-signature))))
      :approval-required

      :else :allowed)))

(defn governing-change?
  "Does this change touch the terms of the AO's own supervision?"
  [paths]
  (boolean (some #(re-find #"(?i)actor\.edn|policy|capabilit|objective|write-scope|governance"
                           (str %))
                 paths)))

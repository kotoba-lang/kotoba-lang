(ns aiueos.update
  "Update planning and step admission — the decision half of \"update it without
  taking the customer down\".

  Contract: `resources/aiueos/update_contract.edn`. Pure decisions only; the
  fetching, writing, rebooting and draining are provider mechanism.

  ## \"Zero downtime\" is three different claims, and only two are free

  What can be swapped without stopping the service depends on what changed:

  | changed | class | service downtime |
  |---|---|---|
  | a component (WASM) | `:live` | none — admission already re-decides per component |
  | a guest (the inference appliance) | `:blue-green` | none — start the new one, health-gate it, drain the old |
  | kernel or loader | `:ab-reboot` | one reboot |

  The A/B partition flow this repository already proves (primary ESP + a
  byte-identical recovery partition, an update receipt, and a rollback the
  release smoke executes in both directions) is the `:ab-reboot` mechanism. It
  is atomic and reversible, but it is not free of a reboot, and claiming
  otherwise would be the kind of thing this repository writes ADRs about.

  What *is* achievable for a fleet member is continuity of the **service**
  rather than of the process: fleet work drains to other nodes before the
  reboot, so the customer's own agents are the only thing that can be
  interrupted — which is why `:ab-reboot` requires either an idle machine, a
  maintenance window, or explicit consent, and never proceeds on \"probably
  fine\".

  ## Every step is gated by evidence that can fail

  `admit-step` refuses to advance on a missing health answer the same way
  `aiueos.enroll/claim` refuses to decide without a possession proof. A health
  probe that cannot fail is not a gate, and an update flow whose gate cannot
  fail is a flow that installs anything."
  (:require [clojure.set :as set]))

;; ── what changed decides the class ─────────────────────────────────────────

(def artifact-kinds
  "Kinds an update payload can carry, ordered by how disruptive they are."
  [:component :guest :kernel :loader])

(def kind->class
  {:component :live
   :guest     :blue-green
   :kernel    :ab-reboot
   :loader    :ab-reboot})

(def class-rank
  "Higher wins when an update carries several kinds — an update that touches a
  component *and* the kernel is a reboot update, not a live one."
  {:live 0 :blue-green 1 :ab-reboot 2})

(defn update-class
  "The class of an update carrying `kinds`. nil when there is nothing to do —
  callers must treat nil as \"no update\", not as \"the cheap class\"."
  [kinds]
  (let [known (filter kind->class kinds)]
    (when (seq known)
      (->> known (map kind->class) (sort-by class-rank) last))))

(defn unknown-kinds
  "Kinds in `kinds` that this version does not know how to apply. A payload
  carrying one is refused whole: a partially-understood update is the case where
  applying the understood half is worse than applying nothing."
  [kinds]
  (set/difference (set kinds) (set (keys kind->class))))

;; ── the step machine ───────────────────────────────────────────────────────

(def steps
  "The sequence every class walks. `:staged` means the new version is written
  and the previous one is still intact; nothing after `:staged` may destroy the
  previous version until `:committed`."
  [:idle :fetched :staged :probing :committed])

(def step-order (into {} (map-indexed (fn [i s] [s i]) steps)))

(def terminal
  "States the machine can end in besides `:committed`."
  #{:rolled-back :refused})

(defn next-step
  [step]
  (let [i (get step-order step)]
    (when (and i (< (inc i) (count steps)))
      (nth steps (inc i)))))

;; ── admission ──────────────────────────────────────────────────────────────

(def deny-reasons
  #{:unknown-artifact-kind :not-admitted-by-publisher :no-previous-version-preserved
    :health-unknown :health-failed :machine-busy :outside-maintenance-window
    :consent-required :peer-updating :rollback-window-too-small :bad-step})

(defn- deny [reason extra]
  (merge {:aiueos/decision :deny :aiueos.update/reason reason} extra))

(defn- allow [step extra]
  (merge {:aiueos/decision :grant :aiueos.update/step step} extra))

(def default-policy
  "`:min-rollback-window` is 1 because the mechanism keeps exactly one previous
  version; a fleet that wants staged rollout raises it and the store bound has
  to move with it. `:max-concurrent-peers` is 1 so two machines belonging to the
  same owner never reboot together — the customer keeps a working machine even
  when a release is bad."
  {:min-rollback-window 1
   :max-concurrent-peers 1
   :require-consent-for #{:ab-reboot}
   :health-probe-timeout-ms 120000})

(defn admit-step
  "Decide whether the update may advance from `(:step state)` to the next step.

  `state`  — `{:step :class :previous-preserved? :health :owner-peers-updating
               :machine-busy? :in-maintenance-window? :consent? :rollback-window}`
  `policy` — see `default-policy`.

  `:health` is `:pass`, `:fail`, or `:unknown`; `:unknown` denies. That is the
  whole point of the gate — an update that cannot be told apart from a healthy
  one by the probe is not allowed to become the running system."
  ([state] (admit-step state default-policy))
  ([state policy]
   (let [{:keys [step class previous-preserved? health owner-peers-updating
                 machine-busy? in-maintenance-window? consent? rollback-window
                 publisher-admitted? kinds]} state
         policy (merge default-policy policy)
         to (next-step step)
         unknown (unknown-kinds (or kinds []))]
     (cond
       (nil? to) (deny :bad-step {:aiueos.update/step step})
       (seq unknown) (deny :unknown-artifact-kind {:aiueos.update/kinds unknown})

       ;; nothing is fetched without the publisher having admitted it first —
       ;; aiueos.publisher owns that decision, this namespace only requires it.
       (and (= to :fetched) (not (true? publisher-admitted?)))
       (deny :not-admitted-by-publisher {})

       (and (= to :staged)
            (< (or rollback-window 0) (:min-rollback-window policy)))
       (deny :rollback-window-too-small
             {:aiueos.update/rollback-window rollback-window
              :aiueos.update/minimum (:min-rollback-window policy)})

       ;; from :staged onward the previous version must still be there.
       (and (#{:probing :committed} to) (not (true? previous-preserved?)))
       (deny :no-previous-version-preserved {})

       (and (= to :committed) (= :fail health)) (deny :health-failed {})
       (and (= to :committed) (not= :pass health))
       (deny :health-unknown {:aiueos.update/health health})

       ;; disruption gates apply where the class actually disrupts.
       (and (= :ab-reboot class) (#{:staged :probing :committed} to)
            (pos? (or owner-peers-updating 0))
            (> (inc (or owner-peers-updating 0)) (inc (:max-concurrent-peers policy))))
       (deny :peer-updating {:aiueos.update/owner-peers-updating owner-peers-updating})

       (and (= :ab-reboot class) (= to :committed) (true? machine-busy?)
            (not (true? in-maintenance-window?)))
       (deny :machine-busy {})

       (and (= to :committed)
            (contains? (:require-consent-for policy) class)
            (not (true? in-maintenance-window?))
            (not (true? consent?)))
       (deny :consent-required {:aiueos.update/class class})

       :else (allow to {:aiueos.update/class class})))))

(defn rollback-required?
  "True when a staged-or-later update must be reverted: the probe said `:fail`,
  or it never answered within the timeout. A probe that never answered is
  treated as a failure, not as a pending state — otherwise a hung probe leaves
  an unverified system running."
  ([state] (rollback-required? state default-policy))
  ([{:keys [step health probe-elapsed-ms]} policy]
   (let [policy (merge default-policy policy)]
     (boolean
      (and (>= (get step-order step -1) (get step-order :staged))
           (or (= :fail health)
               (and (not= :pass health)
                    (some-> probe-elapsed-ms
                            (> (:health-probe-timeout-ms policy))))))))))

(defn plan
  "The ordered steps for an update carrying `kinds`, with its class. Returns nil
  when there is nothing to apply."
  [kinds]
  (when-let [class (update-class kinds)]
    {:aiueos.update/class class
     :aiueos.update/steps (vec (rest steps))
     :aiueos.update/reboot? (= :ab-reboot class)
     :aiueos.update/drains-fleet-work? (= :ab-reboot class)}))

(defn granted? [verdict] (= :grant (:aiueos/decision verdict)))

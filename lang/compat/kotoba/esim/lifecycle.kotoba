(ns kotoba.esim.lifecycle
  "eSIM profile lifecycle as a pure state machine.

  This namespace exists so that the question -- is this profile operation even
  reachable from the state we have on record? -- is answered by a total,
  deterministic function with no I/O, no model and no policy. It is the
  intended callee of a consent surface's pre-check (ADR-2607300300 D2), which
  must reject an unreachable operation before any human approval is requested,
  exactly as kotoba-lang/card's amount checks reject before approval.

  What this namespace decides: structural reachability, the single-enabled-
  profile invariant, and terminality. What it deliberately does NOT decide:
  whether an operator is licensed, whether a subject consented, whether a
  transfer is fraudulent. Those are the governor's and the consent surface's
  and are not expressible as a state transition.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]))

(def states
  "Profile states. :disabled, :enabled and :deleted are the states an eUICC
  reports. :absent is this library's modeling of a profile not yet installed --
  it is a convenience for expressing download as a transition, not a state the
  specification defines."
  #{:absent :disabled :enabled :deleted})

(def terminal-states
  "States no operation leads out of."
  #{:deleted})

(def operations
  "Profile management operations, each declaring the states it is reachable
  from and the state it lands in.

  On :delete -- this table admits delete only from :disabled. Deleting an
  enabled profile is a deliberately conservative refusal, not a quoted
  requirement: a device may well disable-then-delete in one user gesture, but a
  gate that has to justify itself to an operator is better off requiring the
  disable to be an explicit, separately consented step, because the failure
  mode of getting it wrong is a subject losing connectivity with no profile
  enabled. A caller that wants the combined gesture composes :disable then
  :delete and gets two decisions on the record instead of one."
  {:download {:from #{:absent}   :to :disabled}
   :enable   {:from #{:disabled} :to :enabled}
   :disable  {:from #{:enabled}  :to :disabled}
   :delete   {:from #{:disabled} :to :deleted}})

(defn terminal?
  "True when state admits no further operation."
  [state]
  (contains? terminal-states state))

(defn reachable?
  "True when operation is admissible from state. False for any unknown state or
  operation -- an unrecognized input is never treated as permissive."
  [state operation]
  (boolean
   (when-let [{:keys [from]} (get operations operation)]
     (and (contains? states state)
          (contains? from state)))))

(defn next-state
  "The state operation lands in when applied to state, or nil when the
  operation is not reachable from it."
  [state operation]
  (when (reachable? state operation)
    (get-in operations [operation :to])))

(defn transition-issues
  "Return a seq of reasons operation cannot be applied to state, empty when it
  can. Reasons are data so a governor can cite one rather than re-derive it."
  [state operation]
  (cond
    (not (contains? operations operation))
    [{:esim/issue :operation/unknown :esim/operation operation}]

    (not (contains? states state))
    [{:esim/issue :state/unknown :esim/state state}]

    (terminal? state)
    [{:esim/issue :state/terminal :esim/state state :esim/operation operation}]

    (not (reachable? state operation))
    [{:esim/issue     :transition/unreachable
      :esim/state     state
      :esim/operation operation
      :esim/from      (get-in operations [operation :from])}]

    :else []))

;; ---------------------------------------------------------------------------
;; The single-enabled-profile invariant
;; ---------------------------------------------------------------------------

(defn enabled-iccids
  "The ICCIDs of profiles currently in :enabled state. profiles is a collection
  of kotoba.esim/profile records."
  [profiles]
  (into #{} (comp (filter #(= :enabled (:esim/state %)))
                  (map :esim/iccid))
        profiles))

(defn enabled-conflict
  "Return the ICCID of the profile that blocks enabling iccid, or nil when
  nothing does.

  An eUICC has at most one enabled profile: enabling a second one implicitly
  disables the first, which means a caller that has not accounted for the
  incumbent is about to take a subject's working line away as a side effect.
  This function surfaces the incumbent so the decision is explicit rather than
  a consequence."
  [profiles iccid]
  (first (disj (enabled-iccids profiles) iccid)))

(defn apply-operation
  "Apply operation to the profile identified by iccid within profiles, returning
  either

    {:esim/ok? true  :esim/profiles <updated> :esim/from s :esim/to s'}

  or

    {:esim/ok? false :esim/issues [...]}

  and never throwing. An :enable that would displace an incumbent enabled
  profile is refused with :enable/would-displace rather than silently disabling
  the incumbent -- displacing it is a separate decision the caller must make by
  issuing the :disable itself.

  This is the whole surface a pre-check needs: it is total, it returns data, and
  its refusals carry a reason."
  [profiles iccid operation]
  (let [target (first (filter #(= iccid (:esim/iccid %)) profiles))]
    (cond
      (nil? target)
      {:esim/ok? false
       :esim/issues [{:esim/issue :profile/not-found :esim/iccid iccid}]}

      (seq (transition-issues (:esim/state target) operation))
      {:esim/ok? false
       :esim/issues (vec (transition-issues (:esim/state target) operation))}

      (and (= :enable operation) (enabled-conflict profiles iccid))
      {:esim/ok? false
       :esim/issues [{:esim/issue    :enable/would-displace
                      :esim/iccid    iccid
                      :esim/incumbent (enabled-conflict profiles iccid)}]}

      :else
      (let [from (:esim/state target)
            to   (next-state from operation)]
        {:esim/ok?      true
         :esim/from     from
         :esim/to       to
         :esim/profiles (mapv #(if (= iccid (:esim/iccid %))
                                 (assoc % :esim/state to)
                                 %)
                              profiles)}))))

(defn describe
  "A one-line human-readable rendering of a transition, for an operator log.
  Pure string building; no formatting library."
  [state operation]
  (if-let [to (next-state state operation)]
    (str (name operation) ": " (name state) " -> " (name to))
    (str (name operation) ": refused from " (name state)
         " (reachable from "
         (str/join ", " (sort (map name (get-in operations [operation :from] #{}))))
         ")")))

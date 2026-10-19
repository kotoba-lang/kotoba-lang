(ns kotoba.compiler.plan
  "Build the portable, policy-admissible plan descriptor from checked KIR.

  Content addressing is deliberately supplied by the codebase/artifact layer:
  this namespace never invents a CID from a mutable source path."
  (:require [kotoba.abi.contract :as abi]))

(def required-input-keys
  #{:plan-cid :code-closure-cid :artifact-cid :compiler-contract :input-cid
    :requested-resources :budget})

(defn inferred-effects
  "The transitive KIR effect summary is the only authority for plan effects.
  An empty or missing summary is a pure plan, not an invitation to add effects."
  [compiled]
  (set (get-in compiled [:kir :effects] #{})))

(defn build!
  "Create a closed `kotoba.plan/v1` descriptor. INPUT must supply immutable
  identities and requested resource scopes; effects are recomputed from
  COMPILED and cannot be supplied or widened by the caller."
  [compiled input]
  (when-not (and (map? input) (= required-input-keys (set (keys input))))
    (throw (ex-info "plan input is not exact"
                    {:phase :plan :reason :invalid-input})))
  (let [plan (assoc input
                    :format :kotoba.plan/v1
                    :requested-effects (inferred-effects compiled))]
    (when-not (abi/valid-plan? plan)
      (throw (ex-info "portable plan rejected by ABI"
                      {:phase :plan :reason :invalid-descriptor})))
    plan))

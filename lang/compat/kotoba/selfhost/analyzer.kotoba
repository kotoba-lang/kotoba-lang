(ns kotoba.selfhost.analyzer
  "The safe analyzer: a host traversal that decides nothing.

  This is the other half of the split the decisions were written for. Walking a
  program's ops is a fold over a collection — mechanism — and lives here. Every
  question with a safety answer is asked of the shipped Kotoba artifact:

      classify        -> safe-analyzer       (which class an op is in)
      infer-effects   -> capability-admission (effect-bit, effect-union)
      check           -> capability-admission (declaration, unused grants, minimal policy)
      admit           -> capability-admission (deny ladder, scope, sufficiency)

  Read the arrows as the invariant, not as documentation. There is no host copy
  of any rule below — no set of effect ops, no bit assignment, no deny order. If
  you find yourself needing one here, the decision belongs in the .kotoba.

  This namespace exists because a decision nobody calls is the same failure as a
  fact table nobody reads, which is what this repo was found in (ADR-2608110100)."
  (:require [kotoba.selfhost.oracle :as oracle]))

(def ^:private analyzer :safe-analyzer)
(def ^:private admission :capability-admission)

;; ── classification ───────────────────────────────────────────────────

(def op-classes
  "Class -> the export that answers it. Membership is never decided here."
  {:non-executable-form "non-executable-form?"
   :numeric-result      "numeric-result-op?"
   :effect              "effect-op?"
   :user-call-excluded  "user-call-excluded-op?"})

(defn classify
  "The set of classes `op` belongs to. Classes overlap by design — `has-capability?`
   is in three — so this is a set, not a tag."
  [op]
  (into #{} (keep (fn [[class export]]
                    (when (oracle/call analyzer export [op]) class)))
        op-classes))

(defn user-call?
  "Is `op` a call into user code? Excluded ops and non-executable forms are not."
  [op]
  (let [c (classify op)]
    (not (or (contains? c :user-call-excluded)
             (contains? c :non-executable-form)))))

;; ── effect inference (the traversal) ─────────────────────────────────

(defn effect-bit [op]
  (oracle/call admission "effect-bit" [op]))

(defn infer-effects
  "Fold the ops a program reaches into an effect mask.

   What is host here is `reduce` and nothing else. The transition is
   `infer-step` in the artifact, so this cannot mis-classify an op, assign it
   the wrong bit, or union incorrectly — it does none of those.

   It used to call `effect-bit` and `effect-union` separately, which left the
   ORDER of those two host-authored: a fold that classified and dropped the
   result, or unioned before classifying, would still have type-checked here.
   One step call has no such seam.

   Not a collection crossing the boundary, deliberately. Native vector and
   string-index handles are private and may not cross a kexe export, so a
   sequence parameter would forfeit native qualification to buy back something
   this shape already has."
  [ops]
  (reduce (fn [mask op] (oracle/call admission "infer-step" [mask op])) 0 ops))

(defn known-effect-mask [] (oracle/call admission "known-effect-mask" []))

;; ── checks ───────────────────────────────────────────────────────────

(def denial-codes
  "Code -> reason. Read from the artifact so the numbers cannot drift apart."
  (delay
    (into {} (map (fn [[reason export]] [(oracle/call admission export []) reason]))
          {:admit             "code-admit"
           :unknown-kind      "code-unknown-kind"
           :missing-grant     "code-missing-grant"
           :expired-grant     "code-expired-grant"
           :empty-intersection "code-empty-intersection"})))

(defn check
  "Static check of a program against its declared effects.

   `ops` is what the caller's walk found; `declared` is the effect mask the
   program claims. Returns inferred/undeclared/unused/minimal-policy."
  [ops declared]
  (let [inferred (infer-effects ops)]
    {:inferred   inferred
     :declared   declared
     :satisfied? (oracle/call admission "declaration-satisfied?" [inferred declared])
     :undeclared (oracle/call admission "undeclared-effects" [inferred declared])
     :unused     (oracle/call admission "unused-grants" [inferred declared])
     :minimal-policy (oracle/call admission "minimal-policy" [inferred])}))

(defn admit
  "Admission decision for a program that needs `inferred`.

   `:outcome` is the deny ladder's answer. `:sufficient?` is a different
   question and both are returned on purpose: a partial policy is ADMITTED with
   a narrowed scope, so `:outcome :admit` alone does not mean the program has
   what it needs."
  [inferred {:keys [delegated policy now expires]}]
  (let [code (oracle/call admission "admission-code"
                          [inferred delegated policy now expires])]
    {:outcome     (get @denial-codes code)
     :code        code
     :effective   (oracle/call admission "effective-scope" [inferred delegated policy])
     :sufficient? (oracle/call admission "authority-sufficient?" [inferred delegated policy])
     :shortfall   (oracle/call admission "authority-shortfall" [inferred delegated policy])}))

(defn attenuation-violation?
  "Does delegating `child` from `parent` widen authority?"
  [parent child]
  (oracle/call admission "attenuation-violation?" [parent child]))

(defn production-policy-ok?
  "Is `policy` well-formed for production? (:policy/forbid-wildcard)"
  [policy]
  (oracle/call admission "production-policy-ok?" [policy]))

(defn analyze
  "One pass: walk `ops`, check against `declared`, decide admission under `grant`.

   The shape a caller actually wants, and the shape that shows the split — every
   value below came from the artifact; this function contributed the fold and
   the map keys."
  [ops declared grant]
  (let [checked (check ops declared)]
    (assoc checked :admission (admit (:inferred checked) grant))))

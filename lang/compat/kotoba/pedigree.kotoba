(ns kotoba.pedigree
  "Material pedigree / certificate-of-conformance -- pure data
  contracts for cross-actor supply-chain linkage (ADR-2607999950).

  Every `cloud-itonami-isic-*` open business in this fleet is built on
  a strict 'pure data + pure functions -- no I/O, no network'
  discipline (see `kotoba.robotics`'s own docstring: 'policy, not
  control', the same boundary this library extends). Until this
  library existed, that discipline meant every vertical actor was a
  complete silo: an upstream actor's real, physics-simulation-derived
  quality telemetry (e.g. `cloud-itonami-isic-2410`'s steel-coupon
  tensile-test reading) had no way to reach a downstream actor's
  governor (e.g. `cloud-itonami-isic-2930`'s weld/fastener
  acceptance check) except as an invented number or an unverifiable
  self-report -- even though the two verticals are conceptually a
  supply chain.

  This library is the shared EDN interchange schema that closes that
  gap WITHOUT a live network call between actors: an upstream actor's
  `export` namespace packages a `pedigree` record from data it
  already has on file (never inventing a claim), and a downstream
  actor's `propose`/intake operation accepts that record as a plain
  data argument (a test/demo/orchestration script passes the EDN --
  never an HTTP fetch). The downstream governor then independently
  re-verifies the pedigree's own shape and claims before trusting it
  -- the SAME 'ground truth, not self-report' discipline every
  governor in this fleet already applies within a single actor, now
  extended ACROSS actors.

  Schema (a `pedigree` record):

    {:pedigree/id             \"PEDIGREE-heat-1\"   ; issuing lot/certificate id, string
     :pedigree/subject-lot-id \"heat-1\"            ; the specific lot/batch this pedigree is about
     :pedigree/issuing-actor  \"cloud-itonami-isic-2410\" ; business-id of the issuing actor
     :pedigree/claims         {:tensile-test-load-n 5000.0} ; verified, NUMERIC claims only -- never a self-reported string
     :pedigree/evidence-basis [\"steelworks.robotics/run-tensile-test ...\"] ; non-empty vector of source citations
     :pedigree/issued-at      \"2026-07-15\"       ; ISO date string
     :pedigree/upstream       nil}                 ; OPTIONAL, see below

  This library does not know what any particular claim key means
  (e.g. whether `:tensile-test-load-n` clears some acceptance floor)
  -- that acceptance-threshold judgment belongs entirely to the
  DOWNSTREAM actor's own governor (mirrors `kotoba.robotics`'s own
  `gate` not knowing whether any particular safety class is allowed;
  that judgment is the caller's `allowed-safety-classes` argument).
  This library only guarantees the record's SHAPE is honest: required
  fields present, claims numeric, evidence non-empty.

  `:pedigree/upstream` (ADR-2607999960, the second applied link of
  this pattern -- isic-2930 autoparts issuing its OWN pedigree for a
  part-lot that in turn cites an isic-2410 steel-heat pedigree) is an
  OPTIONAL nested `pedigree` record: when a downstream actor's own
  export builds a pedigree for a lot that itself already carries an
  upstream pedigree, it may embed that upstream record here, turning
  a single hop into a genuine multi-hop provenance chain (steel heat
  -> part lot -> ... ) representable as plain, recursively-shaped
  EDN -- never a second network fetch, never a bare id the receiver
  has to go look up. This field is deliberately NOT a new top-level
  concept: it is just another `pedigree` record, so `valid?` below
  validates it with the exact same rule, recursively, and a chain of
  any depth is 'shaped' by applying `claim`/`valid?` once per hop.
  Existing single-hop pedigrees (e.g. every isic-2410-issued heat
  pedigree from the ADR-2607999950 pilot) never set this key and
  remain fully valid with no upstream -- this extension is strictly
  additive and backward compatible.

  No network, no I/O. Portable `.cljc` across JVM / ClojureScript /
  SCI / GraalVM, structurally modeled on `kotoba-lang/robotics`.")

;; ---------------------------------------------------------------------------
;; Construction
;; ---------------------------------------------------------------------------

(defn claim
  "Construct a pedigree record. `id` is the issuing lot/certificate id,
  `subject-lot-id` the upstream lot/batch the pedigree is about,
  `issuing-actor` the issuing business-id (e.g.
  \"cloud-itonami-isic-2410\"), `claims` a map of verified, numeric
  readings (never a self-reported string). `:evidence-basis` (a seq of
  source citations) and `:issued-at` (an ISO date string) are supplied
  by the caller -- this fn takes no wall-clock reading itself, keeping
  it pure/deterministic like every other constructor in this fleet.
  `:upstream` (OPTIONAL, ADR-2607999960) is another `pedigree` record
  (or nil) this one cites as ITS OWN upstream provenance -- e.g. an
  isic-2930 part-lot pedigree embedding the isic-2410 steel-heat
  pedigree it was built from. Omitted/nil `:upstream` means no
  `:pedigree/upstream` key is set at all (not even `nil`), so a
  single-hop pedigree's shape is byte-for-byte identical to what this
  fn produced before this option existed.

  Returns nil (never a partially-built record) when `id`/
  `subject-lot-id`/`issuing-actor` are not non-empty strings or
  `claims` is not a map -- mirrors `kotoba.robotics/action`'s
  minimal-membership-check-then-build shape. This is a SHALLOW
  presence check only; use `valid?` for the full shape/type
  validation a downstream governor needs before trusting the record."
  [id subject-lot-id issuing-actor claims & {:keys [evidence-basis issued-at upstream]}]
  (when (and (string? id) (seq id)
             (string? subject-lot-id) (seq subject-lot-id)
             (string? issuing-actor) (seq issuing-actor)
             (map? claims))
    (cond-> {:pedigree/id             id
             :pedigree/subject-lot-id subject-lot-id
             :pedigree/issuing-actor  issuing-actor
             :pedigree/claims         claims
             :pedigree/evidence-basis (vec evidence-basis)
             :pedigree/issued-at      issued-at}
      (some? upstream) (assoc :pedigree/upstream upstream))))

;; ---------------------------------------------------------------------------
;; Validation -- what a downstream governor independently re-checks
;; ---------------------------------------------------------------------------

(defn valid?
  "Full shape/type validation of a pedigree record -- what a downstream
  governor calls to independently verify an upstream actor's claim
  BEFORE trusting any value inside it (never trust `claim`'s return
  value just because it is non-nil; a hand-assembled or transmitted
  map must still pass this). True only when:

    - `:pedigree/id`/`:pedigree/subject-lot-id`/`:pedigree/issuing-
      actor` are all non-empty strings
    - `:pedigree/claims` is a non-empty map whose values are ALL
      numbers (never a self-reported string, keyword, or nil)
    - `:pedigree/evidence-basis` is a non-empty vector of strings
    - `:pedigree/issued-at` is a non-empty string
    - `:pedigree/upstream` (ADR-2607999960), when the key is PRESENT
      and non-nil, is itself a valid `pedigree` record -- checked
      recursively via this SAME fn, so a chain of any depth is only
      as trustworthy as every link in it. When the key is absent (the
      single-hop case every pre-ADR-2607999960 pedigree still uses)
      this condition is vacuously true -- never a reason to reject a
      pedigree that never claimed to have an upstream.

  Non-map input (or any other shape mismatch) is simply false, never
  an exception -- a downstream governor must be able to call this on
  arbitrary/untrusted input."
  [p]
  (boolean
   (and (map? p)
        (string? (:pedigree/id p)) (seq (:pedigree/id p))
        (string? (:pedigree/subject-lot-id p)) (seq (:pedigree/subject-lot-id p))
        (string? (:pedigree/issuing-actor p)) (seq (:pedigree/issuing-actor p))
        (map? (:pedigree/claims p))
        (seq (:pedigree/claims p))
        (every? number? (vals (:pedigree/claims p)))
        (vector? (:pedigree/evidence-basis p))
        (seq (:pedigree/evidence-basis p))
        (every? string? (:pedigree/evidence-basis p))
        (string? (:pedigree/issued-at p))
        (seq (:pedigree/issued-at p))
        (let [up (:pedigree/upstream p)]
          (or (nil? up) (valid? up))))))

(defn claim-value
  "Read one claim value off a pedigree's `:pedigree/claims` map, or nil
  when absent -- the accessor a downstream governor uses to compare a
  specific claim (e.g. `:tensile-test-load-n`) against its own
  acceptance threshold, without reaching into `:pedigree/claims`
  directly at every call site."
  [p k]
  (get-in p [:pedigree/claims k]))

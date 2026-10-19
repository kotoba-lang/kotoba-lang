(ns kotoba.compiler.effect-row
  "What an inferred effect row holds, and which of its members a capability
  policy decides on.

  Until kotoba-sema e42b74ef a row had one kind of member, `[:cap/call <id>]`:
  an authority the function exercises, which a `--policy` must grant. The
  typed abort ability (kotoba-lang `lang/abort-ability.edn`, slice 1) adds a
  second kind, the bare keyword `:abort`: a TRACKED CONTROL EFFECT. It says the
  function may leave its caller's scope through a typed abort, which the
  frontend has already lowered to a `[:result T E]` value before any consumer
  here sees the body. It names no authority, so there is nothing for a policy
  to grant, and `kotoba.kir.admission/check` -- which decides by
  `(set/difference required allowed)` and only admits `[:cap/call <int>]`
  into `:allow` -- can neither be told to permit it nor asked to.

  Measured 2026-09-02 on this CLI at the pin bump: a module whose helper
  throws and whose `main` catches has the module row `#{:abort}` (the union
  of function rows, one of which aborts), and `amu compile --target wasm32`
  answered `capability policy denies required effects` for a program that
  exercises no capability at all. Every `admission/check` call in this
  repository was handed the raw row.

  This namespace is the one place that knows the difference, so that the
  sites which destructure a row -- `(first effect)`, `[[effect id]]`,
  `(second effect)` -- do not each rediscover that a keyword has no `first`.

  Narrowing is by KNOWN control effect, never by shape. A member this
  namespace does not recognise is kept and reaches admission, where it is
  refused as a grant nobody made. Dropping every non-vector would turn a
  misspelt or newly invented row member into a silent pass -- the shape of
  failure where an unmeasured check returns what a passing check returns.

  Provenance, the interface report, the logic manifest and `check`'s printed
  `:effects` all keep the row as the frontend inferred it, `:abort` included:
  a reader of those is entitled to know the function aborts. Only the
  admission decision is narrowed."
  (:require [kotoba.kir.admission :as admission]))

(def control-effects
  "Row members that describe how a function may LEAVE its caller's scope, not
  what authority it exercises. Closed: kotoba-hir's `valid-effect?` admits
  exactly these keywords into a row, so this set and that predicate move
  together."
  #{:abort})

(defn control-effect?
  "True for a tracked control effect (`:abort`). No grant is needed for one."
  [member]
  (contains? control-effects member))

(defn grant?
  "True for `[:cap/call <id>]`, the wire form of an authority a function
  exercises. The id's representation is not examined here: under nbb it is a
  BigInt, on the JVM a long, and `kotoba.compiler.capability-names` owns
  that translation."
  [member]
  (and (vector? member) (= 2 (count member)) (= :cap/call (first member))))

(defn grants
  "The members of ROW a capability policy grants or denies: ROW without its
  control effects. Everything that is not a known control effect stays,
  including a member this namespace does not recognise -- see the namespace
  docstring for why that is the fail-closed direction."
  [row]
  (into #{} (remove control-effect?) row))

(defn admissible
  "HIR with `:effects` narrowed to `grants`. Nothing else about HIR changes;
  the caller keeps reporting the full row from the original."
  [hir]
  (update hir :effects grants))

(defn check
  "`kotoba.kir.admission/check` over the grants of HIR's row. Same policy
  shape, same result, same refusals -- except that a control effect is no
  longer a required grant nobody can write."
  [hir policy]
  (admission/check (admissible hir) policy))

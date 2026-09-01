# ADR — `eval` means CID-addressed checked-KIR evaluation

- **Status**: Accepted
- **Date**: 2026-09-01
- **Artifacts**: `lang/typed-eval.edn`, `lang/guest-grammar.edn`,
  `lang/capability-catalog.edn`, `lang/surface-status.edn`
- **Related**: `ADR-kotoba-code-identity-and-abilities.md`,
  `ADR-kotoba-content-addressed-codebase.md`,
  `ADR-safe-capability-language.md`

## Context

Kotoba originally rejected `eval` with the other ambient Clojure escape
hatches. That was correct while `eval` meant handing a source form to a host
runtime whose classpath, namespace, interop, and authority were not part of the
checked program. It is no longer the only meaning available to the language.

The codebase now stores definitions whose identity is the compiler's checked
KIR and whose dependencies are definition CIDs. The typed evaluator hydrates
that exact closure without consulting source files or mutable names. Kotoba
also has typed capability calls, effect rows, finite fuel, provider admission,
and canonical document values. Together these permit Lisp's `eval` without
restoring host evaluation.

A result hash alone is insufficient. An effect can delete, transmit, or spend
before an output exists; hashing that output only identifies evidence after
the action. Code identity, authority to run it, and result evidence must remain
separate.

## Decision

Admit `(eval request)` as syntax for the typed `:code/eval` capability. The
request is a bounded `:document` containing a checked definition CID and
canonical arguments. The result descriptor is inferred from the surrounding
typed context. The compiler lowers the form to the ordinary closed
`typed-cap-call` ABI; no backend gains a second evaluator.

The host provider must perform these steps before the first nested effect:

1. parse and bound the canonical request document;
2. fetch and hash-verify the definition and complete dependency closure by CID;
3. reassemble and recheck the typed KIR interface and effect row;
4. require the transitive effect row to be a subset of the current allowance;
5. bind the definition CID, interface, allowance, fuel, and decreasing nested
   depth into an `admission-cid`;
6. execute only that admission capsule; and
7. bind the typed output to a `value-cid` and emit a receipt.

```text
DefCID + interface/effects + current allowance + limits
                         |
                         v
                    AdmissionCID
                         |
                  admitted execution
                         |
                         v
                      ValueCID
```

`eval` never accepts source text, reader forms, namespace names, host objects,
or capability values. Names may be used by a human-facing CLI only as a lookup
that resolves to a CID before admission; the capsule and receipt contain the
CID, never the name.

`apply` remains the ordinary bounded application of a checked closure. It does
not require `:code/eval`, because it cannot introduce code outside the closed
module. `eval` is different precisely because it selects another checked
definition closure and therefore crosses a host authority boundary.

## Safety rules

- host `eval`, `load-string`, reader evaluation, ambient `require`/`resolve`,
  reflection, and runtime classpath lookup remain forbidden;
- the definition CID proves identity, never authorization;
- nested evaluation inherits only a subset of the current effect grant and
  consumes one unit of the admitted depth budget;
- missing providers, dependencies, effect grants, result descriptors, or
  receipts fail closed;
- pure evaluation may be cached by admission and input value CIDs;
- an effectful result must never be substituted for performing the effect; and
- providers still revalidate concrete resource scope, quota, deadline, and
  revocation at use time.

## Consequences

Kotoba regains a central Lisp operation while preserving the property that all
executable code has checked, content-addressed meaning and explicit authority.
Programs can store, transfer, inspect, and evaluate definitions as data without
turning data identity into a capability.

The word `eval` is intentionally unavailable as a compatibility alias for host
Clojure evaluation. Existing negative tests for reader escapes and ambient
loading remain normative. Implementations that cannot install an admitted
`:code/eval` provider reject the form at instantiation or dispatch.

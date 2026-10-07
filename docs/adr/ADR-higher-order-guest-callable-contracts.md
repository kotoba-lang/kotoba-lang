# Higher-order guest callable contracts

Status: accepted source contract; normal consumer qualification pending.

A callable clause may take or return another guest callable. A logical `[:fn ...]`
contract is represented by a refined i64 closure word, with parameter/result
contracts carried separately on generated helpers and dispatchers. Dispatcher
families use the logical clause signature, including nested results. This amends
the first-order exclusion in ADR-typed-closure-parameters without admitting host
function invocation, linear resources, or ambient dynamic dispatch. Existing
integer-only programs retain their HIR/KIR representation. Type depth 12, type
nodes 64, five unique arity clauses, arity four, capture and resource limits stay.

Sema PR100 has source candidate evidence and direct checked KIR/restricted ESM
opaque identity/allocation qualification. That does not prove native/Wasm support,
normal Amu consumers, unbounded or escaping host callbacks, reentrancy, async,
public JS callables, or package migration. The grammar records these distinctions.
The synchronized Sema copy, published pin and lock, and actual normal consumer
qualification must follow before any claim of those states.

The same resync reconciles the already published Sema 9734753 bounded-definition
admission contract: aggregate input remains at most 8 MiB and every canonical
UTF-8 top-level form remains at most 1 MiB. Existing definition_admission_test
admits 600 bounded definitions over 1 MiB, refuses one oversized definition, and
refuses aggregate input over 8 MiB. This does not raise System One's independent
snapshot/model input budget, nor claim a whole-component Q9 source migration.

Vendor deferrals name the copies awaiting this wave. They close only when the
consumer's main matches the new bytes; an absent sibling is not a comparison.

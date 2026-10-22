# Native static vector profile proposal

Tracking: JUN-241 / JUN-200 / JUN-201 / JUN-202, Kotoba Labs. Source proposal
only; no native profile activation or runtime qualification is established.

The native source candidate implements the entire eight-operation vector
arena: new-empty, conj, count, at, assoc, alloc, assoc-in-place and drop.
The profile adds these eight names to static-elf admitted-slots. The existing
eight names retain their order. Unknown/unimplemented declared slots still
refuse; none is replaced by an empty stub.

Native defaults remain 4096 vector handles and 65536 item words. Add the
existing address-space maxima to the independently consumed native profile:
cells/vectors/max=4194304, cells/vector-items/max=134217728. The existing
language vector bound is projected as cells/vector-items/per-vector-max=
16777216. Source literal comparison confirms agreement with canonical C1
constants 15/16/17 and language/value/vector-items; no C/header generation or
C-loader identity is required. Pair/string/fuel budgets and capability
authority are unchanged in this step. The hash/cwd declarations remain the
separate paired source proposal already present on this branch.

Native #205 supplies the actual aligned table/item layout, checked arithmetic
and spans, public context pointers, private used counters/capacities, complete
both-ISA typed IR mechanisms, distinct table/item exhaustion messages,
prelude composition and image budget metadata. Assoc always copies; tail
conj shares only a prefix and writes a fresh word; drop is a suffix view.
In-place update still requires compiler ownership proof and runtime bounds.
F64 aliases transport complete word patterns. Per-vector limits and per-run
arena budgets remain separate.

Do not activate declarations ahead of exact admitted implementation and
reviewed owning pins. The complete bytes helper/capability/raw-file/hash/I/O
route, other required ABI/runtime support, complete callers and compiler
self-rebuild are still open. Static source inspection is not generated code,
native admission, positive/refusal/resource behavior or CDCI qualification.
No compiler/generator/assembler/implementation/runtime/test was executed;
no tests were added. No workflow/protection/service/production change.

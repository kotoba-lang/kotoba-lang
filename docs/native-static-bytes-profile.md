# Native static bytes primitive profile proposal

Tracking: AWAI-25 / AWAI-66 / AWAI-48 / AWAI-43 within Kotoba Labs, preserving
former JUN-241/200/201/202 UUIDs. The owner approved progress writes to these
current issues within Kotoba Labs; no old workspace/account is used.

Append the complete four existing byte primitives to static-elf admitted
slots: bytes-from-vector, bytes-slice, bytes-concat and string-from-utf8.
The sixteen preceding slot names retain their order. Existing native source
now supplies all twenty routines; source declarations are not a native
qualification receipt or permission to activate an unmatched profile.

Project existing language limits into the independently consumed closed
native profile: bytes/value-max=65536 and bytes/string-value-max=65536.
These agree with guest-grammar.edn bytes-value/limits, the language string
bound and canonical C1 constants 19/18. They are per-value bounds, separate
from the unchanged per-run string-pool budget and its overrides.

bytes-value-traps carries the complete ordered existing names at C1 indices
1..11: seven UTF8 categories, string/too-large, bytes/item-out-of-range,
bytes/too-large and bytes/slice-bounds. The native mechanism uses these named
terminal value paths; no replacement decoding or successful empty fallback.
The UTF8 walk checks second-byte categories before looking for later bytes,
retaining the reference's first-failure order. Byte operations preserve
non-UTF8/embedded-zero contents without decoding them as strings.

Native #205 supplies real copied conversions/concat, slice views, checked
arena spans, canonical UTF8 validation, shared validated-string pair
construction, private byte/string limit words, planner/refusal data checks,
twenty-slot prelude composition, actual value messages and image metadata.
Existing vector bodies, defaults, capability mappings/authority, exit codes
and other profiles are unchanged in this step. No C loader or generated C
header is required by the new mechanism.

Capability byte kinds/lowering/providers/results, raw-file/hash/I/O, full
shared-read refusal naming and its owning lowering/ABI,
callers and remaining runtime/ABI are still open, as are native artifacts,
positive/refusal/resource evidence, owning main/pins and Amu self-rebuild.
Do not activate this proposal before the exact complete implementation and
matching reviewed pins are qualified. No implementation/compiler/generator/
assembler/admission/artifact/runtime/test was executed; no tests added.
No workflow/protection, live service or production route changed.

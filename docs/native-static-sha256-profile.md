# Native static SHA-256 profile candidate

Tracking: [JUN-201](https://linear.app/kotobalabs/issue/JUN-201),
[JUN-200](https://linear.app/kotobalabs/issue/JUN-200), Kotoba Labs.

This candidate adds wire 3 / `:hash/sha256` to the owning native static-ELF
handler declaration. It preserves all ten existing entries, slots, budgets,
refusal/trap names and exit codes. It does not broaden any guest grant: the
packager still requires declared effects, a grant subset and real sealed
ISA-matched decision artifacts.

The former omission prevented the canonical artifact seal verifier's hash
effect from reaching a native implementation. Its implementation is the
complete private both-ISA SHA-256 kernel, whole-input traversal, hexadecimal
result, checked workspace and C1/C3 pool/result binding in
[kotoba-native #205](https://github.com/kotoba-lang/kotoba-native/pull/205),
head `dcac4f11e051988b34f248a39c53d305052083f6`. The matching owning Amu
`linux_static_handlers.kotoba` candidate routes provider 9 / wire 3 to handler
11; unrelated wires still refuse. C1 already supplies provider 9 for kind 1 /
wire 3. Merely adding this table entry cannot supply or qualify those bodies.

Keep this as a draft dependency proposal until the complete native packager,
Amu decision artifact, owning ELF64 encoder and exact reviewed source/lock
pins are admitted together. Current owning native pins still select the old
packager. Merging this entry ahead of that closure would advertise a handler
the selected implementation does not provide. The active main profile must
not be treated as qualified by this candidate or by empty workflow statuses.

Required order: owning object #8 → complete native #205 source admission and
byte/refusal/resource qualification → matching Amu decision artifact plus this
owning profile and exact reviewed source/lock pins → whole compiler/image
generations and self-rebuild → actual Kotoba / Amu selfhost CDCI. There is no
C-loader pin, generated C-header equality or Node/NBB oracle condition.

No implementation, hash/generator/encoder, compiler build, native admission,
artifact/digest/runtime or test was executed. Static declaration/source
comparison is the only evidence here. This draft does not change live
services, workflow/protection settings or an app route. Qualification and
general runtime-helper/required-ABI closure remain open under JUN-200/201/202.

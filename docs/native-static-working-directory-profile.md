# Native working-directory profile declaration

Tracking: [JUN-236](https://linear.app/kotobalabs/issue/JUN-236),
[JUN-201](https://linear.app/kotobalabs/issue/JUN-201), Kotoba Labs.

This draft adds wire 40 / `:sys/cwd` to the static ELF handler declaration,
alongside the prepared native hash entry. All ten existing handler entries,
slots, budgets, traps, exit codes and other limits data are unchanged. The
catalog already assigns wire 40 to the empty-request operation that returns
the absolute directory the process started in; no catalog/effect change is
needed. An environment read cannot supply this fact.

The matching [Amu #1440](https://github.com/kotoba-lang/amu/pull/1440) route is
C1 provider 0 / wire 40 -> handler 12. Its canonical working-directory module
uses that wire directly. [Native #205](https://github.com/kotoba-lang/kotoba-native/pull/205)
provides the full operation: actual `getcwd` capture at startup, nonempty
absolute physical path, empty-request refusal rule, checked pool/pair
allocation and shared UTF-8 result validation. It does not consult `PWD`,
spawn `pwd` or add a hosted/C helper. Existing entropy/spawn/hash handlers
retain their identities and wire 40 still requires a sealed guest grant.

Owning main still lacks the new route/declaration. Keep the profile a draft
until the native implementation and real Amu-produced decision artifacts
are admitted and qualified with exact reviewed source/lock pins. This table
does not prove provider availability, native lock replay, whole compiler
self-rebuild or native CDCI. C-loader pinning and hosted comparison are not
acceptance conditions.

Only static profile/source comparison was performed; no implementation,
compiler/generator, native artifact/admission/syscall/runtime or test was
executed. No workflow, protection, live service or app-origin route changes.

# ADR: Reliability T5.4b — raise max-parameters from 5 to 64 (stack-passed arguments)

- Status: Accepted
- Date: 2026-09-23
- WBS: T5.4
- Supersedes: `ADR-reliability-t54-max-parameters.md` (decision 1, and decision 4's
  pinned arity)
- Implementation (all on default branches, 2026-09-23): kotoba-gmir `cf39f7c`,
  kotoba-sema `bf42c45` (frontend) + `8571681` (vendored grammar resync),
  kotoba-codegen `9a54c24`, kotoba-mir `6d92aea`, kotoba-native `85a068a`,
  kotoba-verifier `1a3abd1`, kotoba-lang `f9c659e` (#704), amu `4af8c67`
  (#1051, pins + parameter pins 6 -> 65) and `a6f1d90` (#1053, digest
  history). Superproject west pins advanced for all nine.

## Context

T5.4 kept `max-parameters = 5` and required that raising it come with a new
security ADR, evidence from two backends, and a threat review. This ADR is that
record.

Five was the number of registers the native call convention passes arguments
in. It was never a property of the language. What it did do was block real
programs: the torihiki book holds 26 arrays live, and a loop could thread four
of them (`lang/surface-status.edn` `:binds-first`, wall `max-parameters 5`).

## Decision

1. **`max-parameters = 64`**, one bound shared by every layer: kotoba-sema
   `frontend/max-parameters`, kotoba-verifier `max-parameters`, and
   kotoba-gmir `max-arity`, which kotoba-mir and kotoba-native read.
2. **Native calls pass arguments 0–4 in registers and argument i ≥ 5 on the
   stack**, at `[sp + 8(i − 5)]` at the bottom of the caller's frame. The callee
   reads them from its incoming area, above its own frame. A tail call writes
   the callee's stack arguments into its **own** incoming area. This is safe
   because every incoming argument was loaded at entry, before any body code
   could run.
3. kotoba-mir inserts these as three indexed instructions before allocation:
   `incoming-load`, `outgoing-argument`, and `incoming-argument-store`. The
   encoder alone turns an index into an offset, because only the encoder knows
   the frame sizes.
4. The T5.1 / T4.4 policy is unchanged. Multi-field public APIs still prefer
   records and options. The raise removes a wall; it does not recommend wide
   arities.
5. The T2.4 negative corpus (amu ADR 0166) now pins **65** as the rejected
   arity (`:kotoba.error/max-parameters`), one past the bound.

## Threat review

- **Admission stays finite.** 65 or more is refused with
  `:kotoba.error/max-parameters` at the frontend, the verifier, and the gmir and
  mir validators. Erased slices still count their machine words against the same
  bound (`:kotoba.error/slice-erased-max-parameters`).
- **Stack writes are confined to the function's own frame.** The mir validator
  requires three things of every stack argument a physical call needs. It is
  stored exactly once. It is stored between the previous call and this one. It
  is stored into the area its kind reads: the outgoing area for a call, the
  incoming area for a tail call or direct recur. Otherwise the validator refuses
  with `:stack-argument-profile-violation`. An index outside `[5, 64)` is
  refused with `:stack-argument-index-invalid`.
- **The outgoing area is bounded**: at most 59 slots (472 bytes) per frame. On
  aarch64 the encoder refuses any displacement that the scaled imm12 cannot
  reach (`:stack-argument-out-of-range`) rather than emitting a wrapped
  offset.
- **Code with five or fewer arguments is unchanged.** A function with no stack
  arguments comes back from the rewrite unchanged, so its allocation and bytes
  are what they were. See the evidence below.
- **Non-claim:** the native artifact verifier gained only the new bound. It has
  no new structural check that recognizes stack-argument stores in emitted
  code. The checks above run in kotoba-mir, before encoding.

## Evidence (reproduction, not constants)

Every item below was measured with the `params/beyond-five` branches of all
seven repositories on the classpath. Each "before" comparison used a detached
worktree of each repository's base commit with the same classpath recipe, so
the only thing that differed was the source roots.

1. **Native, both ISAs:** `amu compile --target aarch64 | x86_64 --jvm-free`,
   then `amu extract-native`, then `tools/kexe_loader.c` (x86_64 under
   Rosetta). The differential corpus covers a 7-argument call looped 10^6
   times, a 64-argument call, tail calls in both directions (wide → narrow and
   narrow → wide), spill pressure alongside stack arguments, and vector
   arguments. It matches the expected values on both ISAs.
2. **js (`--target js`):** the same corpus matches.
3. **wasm32-browser:** the corpus matches except for the two deep tail-call
   cases. Both overflow because wasm32-browser has no tail-call elimination,
   and the same programs overflow at arity 4 on the base commit. This is the
   pre-existing `no tail-call elimination` wall, not this change.
4. **Byte identity:** the existing programs with five or fewer arguments
   compile to byte-identical artifacts before and after.
5. **Suites:** failure *sets* are compared, not counts, between the base
   commit and this change.
   - kotoba-native: `run-tests.cljk`
   - kotoba-verifier: `run-tests.cljk`
   - kotoba-mir: `mir_test`, which runs only after rewriting
     `clojure.lang.ExceptionInfo` to `ExceptionInfo`
   - kotoba-sema: 564 tests, with the boundary tests at 64 and 65

   The sets are identical. New tests were run in both directions:
   - mir `stack-argument` tests: the base mir refuses the 7-argument module
     with `non-canonical-function`.
   - native `stack-arguments-test`: the base native refuses with
     `unknown-encoding`.
   - amu h10 and corpus pins: the old 6-argument pins fail on the new code,
     because 6 arguments now compile.

## Residual gaps (resume here)

In priority order. None blocks the bound itself.

1. **The next wall for the torihiki book is the native item budget, not
   arity.** Seven fields of 16,384 need `KEXE_VECTOR_ITEMS=131072`; the
   loader default is 65,536 (`tools/kexe_loader.c`
   `KEXE_VECTOR_ITEM_CAPACITY`). Deciding that number is the next ADR, with
   `lang/surface-status.edn` `:binds-first` as its evidence.
2. **`tools/kexe_loader.c` still takes at most 5 entry arguments** on its
   command line. A loader CLI limit, not the language's; raise it when a
   host needs a wide entry.
3. **Suites the kbb engine cannot load.** kotoba-mir `mir_test` and amu
   `project_test` use `clojure.lang.ExceptionInfo` / `bytes?`; since the JVM
   route was removed they are run by nothing. This change ran them on
   scratch copies with `ExceptionInfo`; porting them is its own change.
4. **Measurement traps, recorded so they are not re-diagnosed:** the loader
   starts with 512 fuel (`KEXE_FUEL`; `amu compile --fuel` does not reach
   it), and a zero-argument `main` is evaluated at compile time on a small
   budget (a bit-or accumulator there is refused as `oracle evaluation
   rejected` on the pre-change amu too).
5. **wasm32-browser has no tail-call elimination** (pre-existing, unchanged).

## Related

- `ADR-reliability-t54-max-parameters.md` (superseded by this ADR)
- `ADR-reliability-t51-structural-args.md` (unchanged: records first)
- amu ADR 0166 (T2.4 corpus; the pinned arity moves from 6 to 65)

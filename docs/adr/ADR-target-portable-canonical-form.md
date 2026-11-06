# ADR — The canonical Kotoba form is the portable program; each target is a qualified profile

- **Status**: Proposed. This records an owner-decision request. Nothing here is
  implemented, qualified or a change to an accepted contract until the owner
  accepts it; decisions D1–D7 below are open.
- **Date**: 2026-11-07
- **Tracking**: Linear JUN-949 (this ADR), JUN-947 (investigation)
- **Evidence**: [`docs/evidence/target-portable-core-20261107/`](../evidence/target-portable-core-20261107/README.md)
- **Builds on**: [`ADR-kotoba-pure-s-expression-core-and-cljk-surface.md`](ADR-kotoba-pure-s-expression-core-and-cljk-surface.md),
  [`stack-architecture-target-neutral.ja.md`](../stack-architecture-target-neutral.ja.md),
  amu ADR 0397 (ES modules over SIR), amu ADR 0410 (what coverage means)
- **Would need amending if accepted in full**:
  [`ADR-kotoba-amu-selfhost-only.md`](ADR-kotoba-amu-selfhost-only.md) (D3, D4)

## Context

The owner asked whether Kotoba can be "write once, work and safe anywhere":
compilable or transpilable to Rust, C, TypeScript, Python, Java, Wasm and others
from the language alone, with Amu as one build route.

Kotoba is not a Clojure dialect that happens to want more hosts. The accepted
language is the canonical pure S-expression form: `.kotoba` is the core, while
`.cljk` and `.edn` are input sugar that must reach the same form and Definition
CID. So the question is about the canonical form, not about the Clojure-shaped
surface.

What was measured on 2026-11-07 (details and limits in the evidence directory):

| Question | Result |
|---|---|
| Is S-expression syntax an obstacle? | No. A throwaway i64-subset emitter sharing no code with Amu agreed with Amu native on 14/15 programs in C and 13/15 in Java, TypeScript and Python |
| Does the emitter need rules beyond the syntax? | Yes. The same programs with each language's own operators agree on 9 (C), 11 (Java), 5 (TS) and 4 (Python) of 15 |
| Where do those rules live? | Only in `amu/seed/SIR`, spelled with aarch64 instruction names. [`semantics-ssot.md`](../lang/semantics-ssot.md) has no integer overflow, shift or `MIN quot -1` rule |
| Does the selfhost compiler accept the canonical core? | `perform`, `handle`, `let`, `if`, `do`: yes. `lam`, `app`, `ref`, `rel`, `query`: refused E2101 |
| Do targets accept the same programs? | Of 86 positive conformance files, aarch64-macos compiles 48; wasm32-browser and js-browser compile 29 each. All 26 negative files are refused on all three |
| Does a shared IR help? | Yes. js-browser and wasm32-browser gave the same verdict on 112/112 files, and the JS emitter is 267 lines over SIR |
| What does an empty program cost? | js-browser emits 129 functions and about 83 KB for a constant `main` |

The compile counts are admission only; they do not compare execution. Rust was
not measured (no toolchain on the measuring host). The independent emitter
covers only `i64`, `loop`, calls and arithmetic, and is written against the
surface spelling Amu accepts today.

The two self-hosted admission gaps conflict with an accepted specification, so
under the workspace's code-first rule they are reported as the code violating
the specification, not as the specification being wrong. The surface-equivalence
corpus recorded the same `app`/`ref` refusal on 2026-10-29.

## Decision

### 1. The promise

Replace "work and safe anywhere" with a promise that can be tested:

> The same admitted canonical form gives the same observable result, the same
> named trap, or the same named refusal on every target qualified for its
> profile.

A target that cannot honor the meaning refuses before producing an artifact.
Silent fallback stays non-conforming.

### 2. What the portable program is

The portable unit is the **canonical program form**, after reading, desugaring
and checking. The three meanings of "form" in the
[Amu course](../learn/amu/02-forms.md) stay distinct:

| Word | What it is | Portable? |
|---|---|---|
| Source form | What an author writes, in `.kotoba` or sugar | Only through normalization |
| Frontend Form value | `kotoba.form` or the seed `NODE` table | No, compiler-internal |
| Canonical program form | The checked meaning behind the Definition CID | Yes |

Consequences:

- Backends never see surface spellings. `->`, `cond`, destructuring and other
  sugar end at normalization.
- Language compatibility is judged on the canonical form. Compatibility with
  Clojure or EDN readers is a property of the sugar surfaces only.
- There are no per-target reader tags. `.kotoba` gains no `:rust`, `:py` or
  `:js` branches. Target differences belong in capability providers, which keeps
  one source meaning one program.

### 3. Meaning is specified at the form, without naming an ISA

Add a target-independent normative contract, proposed as
`lang/core-semantics.edn` with prose in `semantics-ssot.md`. SIR then implements
it rather than defining it. It starts from what the compiler already does:

| Topic | Rule | Source today |
|---|---|---|
| `:i64` `+ - *` | Two's complement, wraps at 64 bits | `seed/SIR` |
| `quot` | Truncates toward zero; traps on divisor 0 and on `MIN quot -1` | `seed/SIR`, measured |
| Shifts | Count is taken mod 64 | `seed/SIR`, measured |
| `:string` | UTF-8; length in bytes unless a code-point operation is named | SSoT |
| `:f32` / `:f64` | IEEE-754, ties-to-even, no contraction or fast-math | SSoT |
| Evaluation | Left to right, call by value | SSoT |
| Tail calls | Proper: a call in tail position does not grow the stack | Proposed; emitters already do it, including the JS trampoline |
| Traps | A closed, named set with the same name on every target | Proposed |
| Resource exhaustion | See D5 | Open |

### 4. Three profiles

| Profile | Contains | Who must implement it |
|---|---|---|
| `kotoba-core` | Pure canonical computation: scalars, strings, bytes, records, variants, options, results, vectors, documents, closures, abort handling | Every qualified target |
| `kotoba-cap` | `perform` and capability imports through a host ABI | A target plus a host that provides the capability; absent means refused |
| `kotoba-sys` | `kernel-*`, memory windows, GPU and privileged actions | Named native targets only |

Legacy emitter builtins (`"i64+"`, `"alloc"`, `"str-ptr"`, `"mem-i32-at"`) and
host-shaped values such as `js-bool-value` leave the portable vocabulary. A
program's profile is computed by the checker and recorded with its artifact.

### 5. Conformance is the contract

- One form-level corpus states result, trap, effect trace and refusal.
- Each target runs the corpus and publishes a generated per-target table. That
  table replaces hand-maintained `:backends` sets in `surface-status.edn`, which
  have drifted from the self-hosted route.
- Every comparison keeps a negative control.
- Compiling a case is not passing it; execution results are compared.

### 6. Routes

- Amu selfhost stays the reference route and, until D3 is decided, the only
  required route.
- A conforming route takes canonical forms and passes the corpus. It may claim
  Definition CID identity only if it reproduces the CID; otherwise it may claim
  behavior only.
- Routes do not fall back to one another.

### 7. How source targets are emitted

In order:

1. **SIR machine output.** All values are `i64`, memory is linear, and each
   function is a basic-block switch, as in the JS emitter. It runs the same
   runtime text as the other targets, so meaning cannot drift per target. The
   output is not idiomatic. Interop is the five-function host ABI.
2. **Wasm-derived routes** such as wasm2c or Chicory where they satisfy the same
   corpus. They add an external dependency, which falls under D4.
3. **Typed, idiomatic output** (record to struct, option to enum), only for
   targets where the output must be consumed as a library. `kotoba-script` is the
   precedent, at about 3,300 lines for one language. This is the costly option
   and must justify itself per target.

A linkable runtime comes before new source emitters, in the spirit of Fennel's
no-runtime output: a program carries only the runtime functions it reaches.

### 8. What "safe" may claim

| Layer | Property | Holds where |
|---|---|---|
| Admission | No interop, host eval or guest macros; effects declared and checked | On any route that runs the checker; a second frontend must reimplement it and pass the negative corpus |
| Emitted code | Bounds, traps, fuel | Only where the emitter produces them and the corpus proves it |
| Isolation | Guest cannot reach the host except through granted capabilities | Provided by the engine or OS, not by translation |

Claims are made per target. As an expectation to be tested, not a result:

| Target | Expected claim | Main risk |
|---|---|---|
| Wasm | Engine sandbox plus emitted checks | Engine bugs remain in the TCB |
| Rust | Compiler-checked memory safety of a safe-subset emission | Not measured here |
| JS / TS | Restricted module and forbidden-token scan; TS types add nothing at run time | Shares a realm with its host unless a Worker or similar separates them |
| Java / Python | Memory-safe host; no isolation from code in the same process | Reflection and host code can reach guest state |
| C | Weakest: every UB path must be closed by emitted helpers and fixed flags, and the C compiler joins the TCB | At `-O2` the naive translation produced garbage for `1 quot 0` and `1 << 64`, and contracted FMA changed a float result |

### 9. Sequence

Each stage has its own exit evidence, and none implies the next.

1. **S0**: Owner decisions D1–D7.
2. **S1**: The self-hosted frontend admits the full canonical core, or the
   canonical core is corrected by decision D2. Exit: core-head table all admitted,
   surface-equivalence pairs measured with their controls.
3. **S2**: `core-semantics` and the three profiles land; the checker reports a
   program's profile. Exit: every conformance file classified.
4. **S3**: Close `kotoba-core` gaps on the existing targets: floats on Wasm and
   JS (E7101), and the export-clause refusal (E2004) that stops 35 positive files
   on browser targets, 9 of which compile on aarch64-macos. Exit: wasm and JS match
   native on `kotoba-core`, by execution.
5. **S4**: Linkable runtime. Exit: a constant `main` reaches only the runtime it
   uses, with fuel and trap results unchanged.
6. **S5**: First new source target by D7, from SIR. Exit: the full `kotoba-core`
   corpus by execution, with controls.
7. **S6**: Further targets, each by the same exit. Coverage by device stays with
   JUN-889 and ADR 0410, which are a different question.

## Owner decisions

| # | Decision | Recommendation |
|---|---|---|
| D1 | Adopt the §1 promise in place of "safe anywhere"? | Yes |
| D2 | What is the canonical spelling? The 2026-09-04 ADR names `lam`, `app`, `ref`, `perform`, `handle`, `rel`, `query`, `let`, `if`, `do` and `(def name (fn ...))`, but it has no form for type annotations and `(def f (fn ...))` is refused today (E2124), while every core file uses `defn` with types | Write down the canonical top level and type syntax before S1, since this blocks a grammar-only implementation |
| D3 | May routes other than Amu be official? Today's contract requires Amu selfhost for every target | Yes for generated-target routes that pass the corpus; keep the self-hosted compiler as the only required route |
| D4 | May gates run target toolchains (JVM, `rustc`, a C compiler, Python) to execute generated output? JUN-290 and the selfhost-only decision forbid them in CDCI closures, but those rules were written about hosted compilers | Allow in a separate, non-required target-qualification tier that cannot feed the compiler or its gates |
| D5 | What must deep non-tail recursion do? Native currently dies with signal exit 139, Java/JS/Python raise their own errors, and POSIX native fuel is off by default | Require a named trap on every target, including native |
| D6 | Do `rel` and `query` belong in `kotoba-core`? They bind the core to one store and are refused by selfhost today | Move to `kotoba-cap` until they are portable |
| D7 | First new source target? | Rust if D4 admits its toolchain, because it adds independently checked memory-safety evidence |

## Alternatives considered

- **Be a family of hosted dialects** (ClojureScript, Basilisp, Hy, squint,
  jank). Each exists to use its host's semantics and interop. ClojureScript takes
  JS numbers, and squint takes JS data structures, which gives small output but a
  meaning that changes with the host. This contradicts §1.
- **Leave overflow and null to each target** (Haxe, and Gleam's `Int`). Cheaper,
  but programs then differ by target. Kotoba's fixed `:i64` is the property to
  keep.
- **Transpile the surface directly.** Fast to demonstrate, as the spike did, but
  it skips the checker and makes the surface the language.
- **Per-target reader conditionals.** Familiar from `.cljc`, but each branch is a
  second program with its own meaning and identity.
- **Wasm as the only target.** Strongest isolation and already works, but it does
  not give inspectable or embeddable source, and it is not the foundation since
  KOTOBA-174.
- **A Janet-style portable interpreter per host.** Simple to embed and useful
  where generating code at run time is restricted, but slow. Kept as a later
  option for those environments, unmeasured.

## Consequences

- "Grammar alone" becomes achievable for `kotoba-core`, because the spec no
  longer needs Amu to be read. Type checking and elaboration remain real work: a
  second frontend is not small (`seed/21-check.kotoba` is about 7,900 lines at
  amu origin/main `1754ff26f`).
- Some documented heads will be reclassified or fall out of `kotoba-core`; that
  is a visible change for authors.
- More targets mean more qualification cost, and D4 determines where it runs.
- Generated C, Java or Python does not inherit Wasm's isolation. Marketing must
  use the per-target claim.

## Documentation found to disagree with the code

Reported, not changed by this ADR:

| Document | States | Code or measurement |
|---|---|---|
| `lang/guest-grammar.edn` `:pure-s-expression-core` and the pure-core ADR addendum | All seven pure heads admitted | Selfhost refuses `lam`, `app`, `ref`, `rel`, `query` (E2101). This addendum was measured on the retired hosted route |
| `lang/surface-status.edn:274` | `defdesugar` is the admitted alternative to macros | Selfhost refuses it (E2003), so no authoring extension exists on the required route |
| `lang/surface-status.edn` | `some->`, `map-indexed`, `lazy-cons` and others are implemented-partial | Selfhost refuses them (E2101) |
| `semantics-ssot.md` §3.4 | Wasm and JS have full float support | `wasm32-browser` and `js-browser` refuse floats (E7101); §3.4 names the older `wasm32-kotoba-v1` and `js-kotoba-v1` targets |
| `semantics-ssot.md` §7 | Adversarial depth fails closed | 1e8 non-tail recursion ends with exit 139 on `bin/amu` |
| `lang/safety-claims.edn` | Cites negative-evidence tests | Five cited Amu paths checked are absent from the local checkout |
| `form_pipeline.kotoba` comment | `.kotoba` skips surface lowering | Selfhost accepts `#?(...)`, `->` and `cond` in `.kotoba`. The pure-core ADR permits this until its step 3, but it should be stated |

## Not established

- Execution parity across the conformance corpus.
- Rust output, and strings, collections, closures, fuel or capabilities in any
  independent emitter.
- Definition CID reproduction by any route other than Amu.
- Cost of a linkable runtime, or performance of SIR machine output.
- Current state of the other Lisp projects beyond their documentation; several
  are early or were read through secondary sources.

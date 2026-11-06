# ADR — The canonical Kotoba form is the portable program; each target is a qualified profile

- **Status**: Accepted, owner decision 2026-11-07 (D1–D7 below). This decides the
  direction. No stage is implemented or qualified, and the exact spelling in §2.1
  is reviewed at S1.
- **Date**: 2026-11-07
- **Tracking**: Linear JUN-949 (this ADR), JUN-947 (investigation)
- **Evidence**: [`docs/evidence/target-portable-core-20261107/`](../evidence/target-portable-core-20261107/README.md)
- **Builds on**: [`ADR-kotoba-pure-s-expression-core-and-cljk-surface.md`](ADR-kotoba-pure-s-expression-core-and-cljk-surface.md),
  [`stack-architecture-target-neutral.ja.md`](../stack-architecture-target-neutral.ja.md),
  amu ADR 0397 (ES modules over SIR), amu ADR 0410 (what coverage means)
- **Amends**: [`ADR-kotoba-amu-selfhost-only.md`](ADR-kotoba-amu-selfhost-only.md)
  (D3, D4), [`ADR-kotoba-pure-s-expression-core-and-cljk-surface.md`](ADR-kotoba-pure-s-expression-core-and-cljk-surface.md)
  (D2, D6)

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

### 2.1 Kotoba stays pure: IPLD data with Unison-style identity (D2)

The owner's decision: the Kotoba language is kept pure so that it stays IPLD and
Unison-like. Rules:

1. **One spelling per meaning.** `.kotoba` carries no sugar. `->`, `cond`,
   destructuring, `#?(...)` and `defdesugar` belong to the `.cljk` and `.edn`
   surfaces, which must normalize to the same Definition CID.
2. **Source is inert data on the IPLD Data Model.** Every pure form has one
   representation, pinned by the IPLD Schema
   [`lang/pure-form.ipldsch`](../../lang/pure-form.ipldsch) (§2.2).
3. **No second identity.** The Definition CID stays where
   [`lang/code-identity.edn`](../../lang/code-identity.edn) puts it: DAG-CBOR over
   checked typed KIR, effect row, interface and dependencies. The pure form is how
   that program is written, not another hash. This keeps the refusal of a second
   canonical S-expression in
   [`ADR-kotoba-syntax-layers-and-quote-position.md`](ADR-kotoba-syntax-layers-and-quote-position.md).
4. **Names are metadata.** Binders are alpha-normalized and callees are sealed by
   CID, as already implemented, so renaming changes no identity. A name is a bare
   symbol; `(ref "bafy...")` reads a definition by CID and is an IPLD Link.
5. **Effects are visible and typed.** Effects occur only through `perform` and are
   sealed in the effect row. A CID never grants authority.

The spelling is settled in [`lang/pure-form.edn`](../../lang/pure-form.edn). The
owner set the direction and asked for the spelling to be settled, and these choices
were made under that instruction. It is decided but not implemented.

```clojure
(ns example (:export [run]))
(def inc1 (lam [n :i64] :i64 (+ n 1)))
(def run (lam [] :i64 (inc1 41)))
```

A spelling counts as sugar when it has the same Definition CID as a pure spelling.
That test was measured, and it decided the less obvious cases:

| Choice | Decision | Reason |
|---|---|---|
| Parameter types | Required at the top level, absent on an inner `lam` | Position decides, so there is one spelling, and today's checker already refuses annotations on `fn` literals (E2139) |
| Calls | Prefix for a named head; `app` only for a computed operator | Prefix is what the source already uses, and one spelling per case |
| `defn-` | None; visibility is `:export` | `defn` and `defn-` measure the same CID |
| `loop` / `recur` | None; a tail-recursive `def` | Measured the same CID as the lifted helper, and proper tail calls are in §3 |
| `and` / `or` | Stay core | No other spelling measured has their CID |
| Variadic `(+ a b c)` | Binary only in `.kotoba` | It matches neither nesting, so its desugaring needs its own decision |
| Records, variants | Existing descriptor forms such as `record-new` | They already reach KIR; `defrecord` and `->T` are sugar |
| Protocols, multi-arity | No pure spelling; one `def` per arity | Both resolve statically |
| Module header | `(ns NAME (:export ...) ...)` only | `{:kotoba/export ...}` attribute maps are sugar |

**Migration cost.** In the compiler's own seed (169,268 forms), the sugar to convert
includes 6,883 `defn`/`defn-`, 3,757 `inc`, 2,513 `->`, 1,648 `not=`, 1,619 `cond`,
1,606 `loop` and 468 `case`. JUN-579 keeps `.cljk` out of the compiler's own
dependencies, so these sites become pure source that people then edit. S1 has to
weigh that readability cost before `.kotoba` refuses sugar.

**Order.** Selfhost admits the pure forms; sources convert mechanically with
Definition CID equality and a negative control per pair; only then does `.kotoba`
refuse sugar. Until then, today's Clojure-shaped `.kotoba` stays admitted, as the
2026-09-04 ADR permits.

### 2.2 The three IPLD specifications

Each pure form follows the [Data Model](https://ipld.io/docs/data-model/kinds/)
kinds, and its shape is pinned with an
[IPLD Schema](https://ipld.io/docs/schemas/). [ADLs](https://ipld.io/docs/advanced-data-layouts/)
stay outside the grammar.

| Layer | Pure form | Why |
|---|---|---|
| Data Model | Integer, String, Boolean, Bytes, Link and Null are their own kinds; a call is a List: `(+ 1 2)` is `[{"sym": "+"}, 1, 2]` | Tools see real kinds, and paths and selectors work on them |
| Data Model, no kind | Symbol, keyword, vector, set, map and `f64` are single-entry maps | The kinds do not include them. Float excludes NaN and infinities and is advised against. Map keys are strings in DAG-CBOR, while a form's key can be any form |
| Schema | A `kinded` union over kinds, with a `keyed` union for the maps | Every node is discriminated by the schema itself, which the KIR schema cannot do |
| ADL | Not in the grammar. A schema declares `representation advanced`, and a module pinned by CID implements it | An ADL is a lens over data, and it holds code, so it stays behind the existing boundary |

The schema pins shape, not checkedness: passing it is not being valid Kotoba (root
`adr-2609076000`, decision 4). Evidence: 16 positive forms unify and 8 negative forms
are refused, through `ipld.schema`.

**What is not spec-based today.** The pure form is new; the code that exists differs:

| Part | State on 2026-11-07 |
|---|---|
| `io-ipld` library (`ipld.schema`, `ipld.schema-dsl`, ADL view, FBL) | Implements the specs as a hosted library. Its own coverage table shows 24 of 26 DSL constructs round-tripping, and `copy` and `unit representation empty` refused; that table states it is not conformance |
| Amu selfhost | Contains none of it: no IPLD module in `seed/FULL-MANIFEST`. The `.kotoba` ports stop at E6002 or E1005, and `document-link` is refused (E2101) |
| Definition CID (payload v2) | Valid DAG-CBOR, but a private model: every node is a tagged list such as `["int", "5"]`, with integers as decimal text. Its schema admits it cannot pin the discriminator |
| `kotoba.value.v1` | Also tagged pairs such as `[2, 5]`, disagreeing with payload v2 on every byte |
| ``(:schemas ...)`` | Kotoba's own `[:record ...]` vocabulary, not IPLD Schema |

So the pure form does not change the Definition CID. Moving identity onto native
Data Model kinds is payload v3, which root `adr-2609076000` measured as moving every
Definition CID, module lock and cache key, and left to the owner.

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
| Tail calls | Proper: a call in tail position does not grow the stack | New in this contract; emitters already do it, including the JS trampoline |
| Traps | A closed, named set with the same name on every target | New in this contract |
| Resource exhaustion | A named trap on every target, including deep non-tail recursion on native (D5) | Decided; native exits 139 today |

### 4. Three profiles

| Profile | Contains | Who must implement it |
|---|---|---|
| `kotoba-core` | Pure canonical computation: scalars, strings, bytes, records, variants, options, results, vectors, documents, closures, abort handling | Every qualified target |
| `kotoba-cap` | `perform`, capability imports through a host ABI, and `rel` / `query` until they are portable (D6) | A target plus a host that provides the capability; absent means refused |
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

- Amu selfhost stays the reference route and the only required route (D3).
- Other routes are official for generated targets when they take canonical forms
  and pass the corpus. A route may claim Definition CID identity only if it
  reproduces the CID; otherwise it may claim behavior only.
- Routes do not fall back to one another.
- Target toolchains (JVM, `rustc`, a C compiler, Python) may run generated output
  in a separate, non-required target-qualification tier. That tier cannot feed the
  compiler, its build or the required gates (D4).

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

1. **S0**: Owner decisions D1–D7. Done 2026-11-07.
2. **S1**: The spelling and IPLD mapping are written down (done, §2.1 and §2.2), and
   the self-hosted frontend admits them. Conversion and the `.kotoba` sugar refusal
   follow in that order. Exit: every pure head admitted, surface-equivalence pairs
   measured with their controls.
3. **S2**: `core-semantics` and the three profiles land, including the named trap
   for resource exhaustion; the checker reports a program's profile. Exit: every
   conformance file classified, and deep recursion gives the named trap natively.
4. **S3**: Close `kotoba-core` gaps on the existing targets: floats on Wasm and
   JS (E7101), and the header refusal (E2004) that stops 35 positive files on browser
   targets, 9 of which compile on aarch64-macos. Of all 39 E2004 files, 31 use the
   `{:kotoba/export ...}` attribute map, which §2.1 makes sugar. Exit: wasm and JS
   match native on `kotoba-core`, by execution.
5. **S4**: Linkable runtime. Exit: a constant `main` reaches only the runtime it
   uses, with fuel and trap results unchanged.
6. **S5**: Rust from SIR (D7), in the D4 tier. Exit: the full `kotoba-core` corpus
   by execution, with controls.
7. **S6**: Further targets, each by the same exit. Coverage by device stays with
   JUN-889 and ADR 0410, which are a different question.

## Owner decisions

Decided by the owner on 2026-11-07.

| # | Question | Decision |
|---|---|---|
| D1 | Adopt the §1 promise in place of "safe anywhere"? | Yes |
| D2 | What is the canonical spelling? The 2026-09-04 ADR has no form for type annotations, and `(def f (fn ...))` is refused today (E2124) | Keep Kotoba pure so that it stays IPLD and Unison-like. §2.1 applies this; its exact spelling is reviewed at S1 |
| D3 | May routes other than Amu be official? | Yes for generated-target routes that pass the corpus; the self-hosted compiler stays the only required route |
| D4 | May target toolchains (JVM, `rustc`, a C compiler, Python) run generated output? | Yes, in a separate, non-required target-qualification tier that cannot feed the compiler or its gates |
| D5 | What must deep non-tail recursion do? Native exits 139, and POSIX native fuel is off by default | A named trap on every target, including native |
| D6 | Do `rel` and `query` belong in `kotoba-core`? | No. They stay in `kotoba-cap` until they are portable |
| D7 | First new source target? | Rust, because it adds independently checked memory-safety evidence |

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

- Converting existing `.kotoba` sources, including the compiler seed, is a large
  migration that S1 has to size. Sugar stays admitted in `.kotoba` until it is done.
- The required CDCI closure does not change. The workspace rule on JVM and nbb in
  CI still has to name the D4 tier before that tier is built; see S5.
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
| `lang/surface-status.edn:274` | `defdesugar` is the admitted alternative to macros | Selfhost refuses it in `.kotoba` (E2003). Under §2.1 that refusal is correct, so the entry should name `.cljk` |
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

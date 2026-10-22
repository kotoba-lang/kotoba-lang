# `kotoba` as an Amu selfhost-built native binary

## Current contract — 2026-10-22

Canonical source is `.kotoba`; compiler, CLI, build orchestration, packaging
and execution use Amu selfhost-produced native artifacts. Remove
CLJK/CLJC/CLJS, NBB, KBB, Clojure, Java/JVM, GraalVM, Node and C
compiler/loader/runtime dependencies. Retaining CLJK syntax sugar and pinning
a C loader are superseded conditions. `--jvm-free` alone does not qualify a
selfhost route. Missing native functionality is blocked, without a hosted
fallback. See [the current decision](adr/ADR-kotoba-amu-selfhost-only.md) and
the machine contract in `lang/q9-migration.edn`.

The former hosted source-scan, differential and red-probe drivers are retired
from `scripts/`. Their exact source text and full corpus are historical data;
see [the retirement record](native-selfhost-harness-retirement.md). Native
compiler/runner availability and full execution qualification remain open.

## Historical measurements and earlier contracts

The commands, goals and implementation statements below record their dated
observations. They are not current execution instructions or acceptance
exceptions, and do not prove the native build closure required above.

The released `kotoba` is a native executable. It is not a native executable
that Kotoba produced.

```sh
# scripts/build-native.sh, in kotoba-lang/kotoba
kbb -T:build uber                                       # Clojure -> uberjar
native-image -jar target/kotoba-standalone.jar target/native/kotoba
```

GraalVM folds a JVM-hosted Clojure program into one file. It has nothing to do
with Kotoba's own code generation. Two claims that read alike are different:
**Kotoba is a native compiler** is about `kotoba-native` emitting machine code,
and **the Kotoba compiler is a native binary** is about GraalVM.

The goal of this document is the sentence that removes the second one:
**`kotoba` is a native executable produced by Kotoba, and building it needs no
JVM and no GraalVM.**

## The half that already works: a native binary with no source tree

Content addressing is not a plan here. Measured 2026-09-08, `amu 75f0e6bd`,
no JDK on `PATH`:

```
$ amu module-lock demo.cljk --source-path src --blocks blocks --jvm-free
{:ok true, :lock-cid "bafkreifsgrc2naqc6jufvq4cdwowwjgpje5i2fzp2s4la4nqy5auh7dtdq",
 :modules 1}

$ amu compile --module-lock kotoba.modules.edn --blocks blocks \
      --target aarch64-macos --jvm-free --output demo.kexe
{:ok true, :target :aarch64-macos-kotoba-v1}        # blocks only — no source path

$ amu extract-native demo.kexe --symbol main --output main.bin
{:ok true, :symbol main, :offset 48, :length 36, :arity 0}

$ kexe_loader main.bin 48 0 aarch64 -
42
```

Built from content-addressed blocks, **and executed** — because a build that
was never run is not evidence that it works.

The definitions underneath are real too:

```
helper  bafyreifo74hyerguwp6yec4n34tow3ncz7okz63wxrb6tjeyqmhldqjr4i  deps []
main    bafyreibpeivq6vgclgpn4gr6kgo37fu2xjszxs5ft4a4dfg2ingub6o5ja  deps [<helper's CID>]
```

`main` depends on `helper`'s **CID**, not on its name.

## What identity is, and what it is not

This matters because it decides what "distance to self-hosting" even means.
`lang/code-identity.edn`:

| identity | status | proves |
|---|---|---|
| `:source-tree-cid` | **`:not-implemented`** | `:authored-input` only — explicitly **not** `:typed-semantics`, `:authority`, `:component-equivalence` |
| `:definition-cid` | **`:implemented`** | `[:typed-kir :profile-version :desugar-contract-version :effect-row :interface :direct-definition-dependencies]`, `:excludes [:source-formatting :package-name :git-ref …]` |

And `lang/definition-patch.edn` says what travels between codebases:

```clojure
:unit {:interchange :definition-cid-ops-and-name-mappings
       :not #{:source-file-copy :source-tree-bytes :source-tree-cid
              :typed-code-cid :wasm-artifact-cid}}
```

The unit is a set of definition-CID operations and name bindings. A source
file is **named as what it is not**.

Around it: `lang/value-codec.edn` fixes the canonical value codec
(`kotoba.value.v1`, `ValueCID = cidv1-dag-cbor-sha2-256`); `io-ipld` carries
the data model, DAG-CBOR/DAG-JSON/DAG-PB, `ipld.schema` (Schema-Schema DMT
validation with metered ADL capabilities), `ipld.selector` over ADL Nodes, and
`ipld.fbl`, a Flexible Byte Layout ADL.

So the missing half of the goal is **not** "teach the compiler to parse its own
files". It is: *can the compiler's own behaviour exist as typed definitions —
KIR, interface, effect row, dependencies by CID — in that plane?*

## The measurement in `lang/selfhost-distance.edn`, and what it is not

The historical `measure-selfhost-distance.cljk` scanned the 137 `.cljc` files
on the compiler's then-current classpath (85,106 lines) and ran the two gates
that the **authored-input** surface had. Its retired source is preserved as
text under `docs/evidence/retired-selfhost-host-drivers-20261022/`; these old
counts are not a current source graph or a native acceptance result:

| gate | passes |
|---|---:|
| the reader | **82 / 137** |
| `amu check` | **0 / 137** |

⚠ **This is a measurement of the authoring surface, not of distance to
self-hosting**, and the file records that in `:plane` and `:does-not-measure`.
An earlier draft of this document called it the ladder to self-hosting. That
was wrong, on the authority quoted above: `:source-tree-cid` is not
implemented and proves nothing about typed semantics.

It is still worth having, because text authoring is today the only **entry**
into the definition plane — `module-lock` is fed by source before it produces
blocks. It says how far the compiler's own text is from the language's text
surface. It does not say how far the compiler is from being a set of
definitions.

### What the reader is handed

| form | files | occurrences |
|---|---:|---:|
| `#{…}` set literal | 96 | 1,715 |
| `#?(…)` reader conditional | 78 | 954 |
| `#"…"` regex literal | 36 | 106 |
| `#?@(…)` splicing conditional | 18 | 23 |
| `#js` | 2 | 8 |
| `#'var` | 1 | 2 |

`#{}` and `#?(…)` already read, which is why 82 files get through.

### The one defect the measurement found

One of the 55 refusals was not a missing feature.
`#{8 10 11 12 13 14 17 21 29 30}` in `kotoba-native/interrupt_abi.cljc` threw

```
TypeError: Cannot create property 'closure_uid_795744384' on bigint '8'
```

An exact i64 literal reads as a JavaScript BigInt, `cljs.core/set` switches to
a hashed representation above eight elements, and nbb cannot hash a BigInt.
**This is reachable from an ordinary `.kotoba` program**, not only from the
compiler's own source:

```
#{1 2 3 4 5 6 7 8}              reads
#{1 2 3 4 5 6 7 8 9}            source reader rejected input
#{:a :b :c :d :e :f :g :h :i}   reads     ← the barrier is the BigInt, not the count
```

`max-typed-set-items` is **32**; for exact integers the real ceiling is **8**.

Fixing the reader alone was tried and measured: building the set without
hashing (`sorted-set-by` over printed forms, the remedy `reader-map` already
uses for map keys) clears the reader and hits the same BigInt hash inside
frontend elaboration, so a clean refusal becomes `internal compiler error`.
kotoba-sema `6e0b4703` therefore **names** the refusal instead of leaking a
`TypeError`, and says the limit stands. Lifting it is a frontend change.

## The next slice, in the right plane

Not "make the reader take `#?@`" — 18 files, and the wrong direction:
`#?@` exists to select *between hosts*, which is the thing self-hosting
removes, and `lang/q9-migration.edn` already forbids the neighbouring
shortcuts (`:bulk-extension-rename-forbidden`,
`:decision-only-extraction-forbidden`).

The open question is the definition plane — but it **cannot be surveyed
independently today**, and the obvious way to try is vacuous. Measured
2026-09-08:

```
$ amu definition-cids .../kotoba/object/pe32plus.cljc
{:ok false, :error :subset, :code :kotoba.error/namespace-require-needs-project}
```

`definition-cids` sits downstream of the same gates `check` does, because the
only way a definition enters the plane is by being authored as text the
compiler admits. So "how many of the compiler's definitions exist" has the
same answer as "how much of its text is admitted", and asking it separately
adds nothing.

That collapses the two into one slice: **migrating a whole component
(`:migration-unit :whole-component`) IS how its definitions come to exist.**
What changes is the success criterion. Not "the files parse" — that is the
authored-input surface again — but:

- every definition in the component has a CID, an interface and an effect row;
- the same component authored on either host produces the **same** definition
  CIDs (`:excludes [:source-formatting …]` is what makes that a real test);
- the component builds through `module-lock` → blocks → `--target
  aarch64-macos --jvm-free`, and the artifact is executed, not just built.

`kotoba-object` is the current candidate for it: 3 files, all three read
clean, `pe32plus.cljc` is 229 lines with no host-library call in it, and the
gates it hits are mechanical — an `:export` clause, a folded constant, dropped
`def` metadata.

## What this does not establish

- **The native half proves one program, not the compiler.** `demo.cljk` is
  two definitions. Nothing here compiled a compiler pass.
- **A file that reads is not a file that compiles**, and a file `check` admits
  is not one whose semantics survive lowering.
- **`kexe` is not an OS executable.** It is a sealed container that a C loader
  maps and runs. "A native binary you can hand someone" is a further step, and
  `amu package-ios` / `extract-native` are where that thread starts.
- **The 137 files are one day's classpath**, at the pins recorded in
  `lang/selfhost-distance.edn`. Not a stable denominator.

## The trust half of the native flow

*(Superseded below: measured again 2026-10-19, the trust commands are
JVM-free now. The 2026-09-08 table is kept as the record of what was true
then.)*

Measured 2026-09-08 by putting refusing stubs for `java`, `javac`, `clojure`
and `clj` first on `PATH` with `JAVA_HOME=/nonexistent`, and running each step
of the documented native flow:

| step | JVM-free |
|---|---|
| `amu module-lock` (source → CID blocks) | **yes** |
| `amu compile --module-lock --blocks --target aarch64-macos` | **yes** |
| `amu extract-native` | **yes** |
| `amu keygen` | no — `REFUSED: clojure was invoked` |
| `amu sign` | no |
| `amu verify` | no |
| `amu measure-runtime` (builds the `kotoba-loader` Mach-O) | no |
| `amu run` (verify signature, trust, runtime, then execute) | no |

So a native artifact can be **produced** without a JVM and cannot be
**signed, verified, measured or run** without one. The JVM-free story stops
exactly where the artifact becomes trustworthy.

The only JVM-free way to execute one today is `amu extract-native` plus
invoking `kexe_loader` by hand with the symbol's offset — which is how the
`42` above was obtained, and which **bypasses the signature, the trust set and
the runtime measurement entirely**. That is a demonstration, not a way to run
software.

### Re-measured 2026-10-19 (`amu c4064497`): the trust commands are JVM-free

Re-run 2026-10-19 with the same method — refusing stubs for `java`, `javac`,
`clojure` and `clj` first on `PATH`, `JAVA_HOME=/nonexistent` — but against a
clean worktree of `origin/main` (`c4064497`) rather than a development
checkout:

| step | JVM-free |
|---|---|
| `amu compile … --jvm-free --target aarch64-macos` | **yes** |
| `amu keygen` | **yes** |
| `amu public-key` / `amu trust-key` | **yes** |
| `amu sign` | **yes** |
| `amu verify-signed --trust` | **yes** (`:verified? true`) |
| `amu measure-runtime` (builds the `kotoba-loader` Mach-O) | **yes** |
| `amu run <signed.kexe> --trust --policy --input --executor-key --runtime --loader` | **yes** — `:status :ok`, `:result 42` |
| `amu verify-receipt` | **yes** (`:verified? true`) |

The gap this section was written to name is closed: the whole trust chain —
produce, sign, verify, measure the runtime, execute under a policy and an
input, mint a receipt, verify the receipt — runs with no JVM on `PATH`. The
execute step is real: `amu run` drove the signed `aiueos-probe.kotoba` artifact
under the measured runtime (`:kotoba.native-runtime/v6`) with `{:allow #{}}`
and `{:args []}`, answered `:result 42`, and wrote a receipt
(`receipt-sha256 2126b78d…`).

Two usage details the old table could not have known. `verify-receipt` rejects
the receipt until the *executor* key's signer is in the trust set's
`:trusted-signers` — `executor receipt attestation rejected`, the artifact
signer alone is not enough — and it needs an explicit `--now`. And the command
that accepts a signed envelope is `verify-signed`; `amu verify <signed.kexe>`
answers `native artifact schema rejected`.

What this run proves is that one host's route is JVM-free. The cross-host
question raised below was closed the same day, in the next section.

### There are two signing surfaces, and one of them is already portable

`bin/amu` routes `sign-output-set` and `verify-output-set` to nbb, and
`src/kotoba/compiler/nbb/output_attestation.cljs` signs with `node:crypto`
Ed25519 — no JVM anywhere. The kexe flow goes through
`kotoba-verifier/src/kotoba/verifier/signing.cljk`, 220 lines that carry both
hosts: `java.security` `KeyFactory`/`Signature` and `java.util.Base64` under
`:clj`, `node:crypto` under `:cljs`. (When the table above was measured it was
`signing.clj`, 171 lines of `java.security` only.) `bin/amu` routes the whole
`trustCommands` set — `keygen`, `public-key`, `inspect`, `trust-key`,
`trust-runtime`, `sign`, `verify-signed`, `verify`, `receipt`, … — through
the nbb route, and `run` / `measure-runtime` ride the same route plus the
executor's alias closure.

Same algorithm, same purpose, two implementations, both now portable. That is
the shape `lang/definition-patch.edn` already names for the hasher
(`:parallel-hasher {:status :second-implementation-to-be-migrated}`), and the
signer is the worked example of it.

### The next rung, and what would make it real

The port this section asked for is done: `kotoba.verifier.signing` is
dual-host and the kexe trust commands are on the nbb-eligible route. What
remains is the check that would make it evidence.

It is a **cross-host parity test**, not a green suite: a key generated on one
host verifies on the other, in both directions, and a signature made on one
host is accepted by the other. Signing code that has only ever been run one
way is signing code whose portability nobody has measured — and this
document's own ladder exists because the JVM-free claim had never been run
against a `PATH` with no `java` on it. The 2026-10-19 run above closes the
`PATH` question on one host.

**The cross-host question is closed too (2026-10-19).** kotoba-verifier PR
#72 (merged `deb2d554`) added a live-exchange parity test — whichever host
runs it spawns the other as the worker — and measured 18/18 cases: keys
generated on either host accepted on the other in both directions,
signatures made on either host verified on the other, Ed25519 determinism
(byte-identical signatures for the same key and value), and three negatives
(corrupted signature, wrong-key signature, tampered value) refused on **both**
hosts. The merged tip was re-run from both drivers afterwards
(`nbb: 9 tests, 18 passed` and `jvm: 9 tests, 18 passed`, both exit 0),
because the first version of that PR's entry carried its invocation text in
the ns docstring and a doc-only commit broke `clojure.core/ns` on the JVM
(`Extra input spec: :clojure.core.specs.alpha/ns-form`) and the SCI entry
(`$CP is not ISeqable`) — a case not run is not a case passed, and neither is
an entry that no longer loads.

Two upstream defects the parity work found are open, and they belong to the
language/engine side rather than to signing: `kotoba.kir` resolves to
`kotoba-kir`'s `kir.cljc` (which exports `default-fuel`, not `max-fuel`)
ahead of the pinned `osaho`, so the repo's own `run-tests.cljk` fails at
`origin/main` `231997a` with `Unable to resolve symbol: ir/max-fuel`; and
`clojure.core/ns` evaluates `{:kotoba/export [...]}` attr-maps on a real JVM
(`Unable to resolve symbol`) where SCI treats the map as data — the vectors
want quoting.

## The order the hosts can leave in

Knowing which namespaces are host-bound is not a work order. A namespace
cannot be ported before the namespaces it requires are, so "what first" is a
topological sort over the require graph, restricted to the host-bound nodes.
`scripts/measure-host-port-order.cljk` computes it; `lang/host-port-order.edn`
is the result.

*(Was not regenerable; matcher and predicate repaired 2026-10-19.)* The
script matched source files with `\.clj[cs]?$`, which the 2026-09-11 `.cljk`
rename (ADR-2609111500) left matching nothing: `kotoba-verifier/src` holds 4
`.cljk` files and amu `src` holds 96, none of them `.clj`/`.cljc`/`.cljs`, so
a re-run answered `reachable: 3, blockers: 0, waves: 0` with `:escapes {}` —
an empty sweep, not a clean bill of health. It now reads `.cljk`, fails
closed (`REFUSED: ... an empty graph is not a measurement`) when nothing
matches, and defines `:clj-only` by what survives the rename: no `.cljs` twin
and no `:cljs` branch in any `#?(...)`/`#?@(...)` form (deliberately loose,
like the script's `requires`). On the two-tree scope (kotoba-verifier/src +
amu/src at `c4064497`) that re-run reads: reachable 101, JVM-removal
blockers 17 in 4 waves, Kotoba-only 48 in 9 waves — and
`kotoba.verifier.signing` drops out of the JVM blockers
(`[:java-interop :js-interop :node-require]`, no `:clj-only`), which is the
fact the wave tables below predate. The waves below are still the pre-rename
snapshot; regenerating them over the full classpath (gitlibs pins included)
is the remaining step.

**Two hosts leave, not one.** `node:crypto` is not a smaller problem than
`java.security` — it is the same problem with a different owner, and
Kotoba-only means neither. Four escape kinds are read off the source:
`:clj-only` (no `.cljs` twin and no `:cljs` branch in a reader conditional,
so it loads on no other host), `:java-interop`, `:node-require`,
`:js-interop`.

⚠ **The first version of this section asked one question where there are
two, and got the answer wrong.** It counted any host escape as a JVM blocker,
which put `kotoba.hir`, `kotoba.gmir` and five others in the first wave —
namespaces that are `.cljc`, that the JVM-free nbb route lowers through
today, and that block the JVM not at all. Their `js/` sits in a `:cljs`
branch, which is what portable code looks like. Two predicates, two orders:

| question | blocker | reachable | blockers | waves |
|---|---|---:|---:|---|
| **stop depending on the JVM** | `:clj-only` — no `.cljs` twin and no `:cljs` reader-conditional branch | 125 | **34** | **4, no cycle** |
| **be Kotoba only** | any host escape, including a `:cljs` branch naming `js/` | 125 | **86** | 6 + a cycle of 10 |

Re-measured 2026-10-19 over 60 source trees — `kotoba-verifier/src`, amu
`src` at `c4064497`, and the 58 `:pins` gitlibs checkouts named in
`lang/selfhost-distance.edn` — 125 reachable namespaces, up from 100 at the
pre-rename measurement the first table recorded.

### To stop depending on the JVM

34 namespaces in 4 waves (0–3), no cycle. `lang/host-port-order.edn` carries
the full wave list. `kotoba.verifier.signing` is **not** among them any more:
the port to `.cljk` with a `:cljs` branch moved it out of `:clj-only` (its
escapes now read `[:java-interop :js-interop :node-require]`). In the
pre-rename snapshot it sat in wave 0, and "it can be started now" was the
right read then — that rung is climbed, and the trust-chain measurement
above is what came of it.

### To be Kotoba only

From the same three roots — `kotoba.verifier.signing`,
`kotoba.compiler.cli`, `kotoba.compiler.nbb.output-attestation`: **86
host-bound namespaces, 6 waves and one cycle of 10.** Full wave lists are in
`lang/host-port-order.edn`; the shape to know is that waves 0–2 are the face
that can be started (leaves and near-leaves: the cbor/json/multiformats
shims, the mechanism layer, `component.wit`, `verifier.signing`), and the
names after that wait on what they require.

Two things to read from it.

**Wave 0 is the only face that can be started.** Everything after it waits on
what it requires. It is what can begin, not what is easy.

**The tail is a cycle, and ten namespaces sit in it:** `ipld.core`,
`ipld.dag-json`, `kotoba.compiler.backend.kototama`, `cli`, `core`,
`manifest-native`, `module-lock`, `test-cli`, `test-profile` and
`multiformats.core` require each other, so no one of them comes out alone.
(Compared to the pre-rename snapshot's nine: `backend.kototama`,
`manifest-native`, `test-cli`, `ipld.core` and `ipld.dag-json` joined; `cache`,
`definition-identity`, `provenance` and `kir.definition-identity` left.)
Whether to break the cycle or move all ten
at once is a design decision; it is not an ordering question, and the sort
cannot answer it.

`kotoba.artifact.core` carries `:java-interop`, `:node-require` **and**
`:js-interop` at once — the case where removing the JVM leaves Node behind,
and which only counts as one item of work once the target is Kotoba-only.

## The `.kotoba` files in this closure are not Kotoba

The compiler's own classpath carries **82 `.kotoba` files**. Measured
2026-09-08:

| | count |
|---|---:|
| carry a **double extension** — `X.cljc.kotoba`, `X.clj.kotoba`, `X.cljs.kotoba` | **71** |
| plain `.kotoba` | 11 |
| of those 11, read by the Kotoba reader | 5 |
| **admitted by `amu check`, out of all 82** | **0** |

The double extension is the finding, and it needs no interpretation: the file
is the Clojure source with `.kotoba` appended. `ed25519/core.cljc.kotoba`
opens `;; ed25519.core — pure-Clojure Ed25519 …` and the reader refuses it.

`lang/q9-migration.edn` sets `:bulk-extension-rename-forbidden true`. These
71 files are what that rule exists to prevent, and they are inside the
compiler's own dependency closure.

The consequence for measurement is the point: **any progress metric that
counts `.kotoba` files would score these 71 as done.** The only honest counter
is one that compiles them, which is why both files in this directory report a
gate that executes rather than a file that exists.

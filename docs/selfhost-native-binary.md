# `kotoba` as a native binary, without GraalVM

The released `kotoba` is a native executable. It is not a native executable
that Kotoba produced.

```sh
# scripts/build-native.sh, in kotoba-lang/kotoba
clojure -T:build uber                                       # Clojure -> uberjar
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

`scripts/measure-selfhost-distance.cljk` walks the 137 `.cljc` files on the
compiler's own classpath (85,106 lines) and runs the two gates that the
**authored-input** surface has:

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

## The trust half of the native flow is not JVM-free

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

### There are two signing surfaces, and one of them is already portable

`bin/amu` routes `sign-output-set` and `verify-output-set` to nbb, and
`src/kotoba/compiler/nbb/output_attestation.cljs` signs with `node:crypto`
Ed25519 — no JVM anywhere. The kexe flow instead goes through
`kotoba-verifier/src/kotoba/verifier/signing.clj`, 171 lines of
`java.security` `KeyFactory`/`Signature` and `java.util.Base64`, which is
`.clj` and therefore JVM-only.

Same algorithm, same purpose, two implementations, one portable. That is the
shape `lang/definition-patch.edn` already names for the hasher
(`:parallel-hasher {:status :second-implementation-to-be-migrated}`), and it
is worth naming here for the signer too.

### The next rung, and what would make it real

Port `kotoba.verifier.signing` to `.cljc` — `java.security` under `:clj`,
`node:crypto` under `:cljs` — and add the four kexe trust commands to
`bin/amu`'s nbb-eligible list.

The check that would make it landable is a **cross-host parity test**, not a
green suite: a key generated on one host verifies on the other, in both
directions, and a signature made on one host is accepted by the other. Signing
code that has only ever been run one way is signing code whose portability
nobody has measured — and this document's own ladder exists because the
JVM-free claim had never been run against a `PATH` with no `java` on it.

## The order the hosts can leave in

Knowing that the trust half needs a JVM is not a work order. A namespace
cannot be ported before the namespaces it requires are, so "what first" is a
topological sort over the require graph, restricted to the host-bound nodes.
`scripts/measure-host-port-order.cljk` computes it; `lang/host-port-order.edn`
is the result.

**Two hosts leave, not one.** `node:crypto` is not a smaller problem than
`java.security` — it is the same problem with a different owner, and
Kotoba-only means neither. Four escape kinds are read off the source:
`:clj-only` (a `.clj` with no `.cljc`/`.cljs` twin), `:java-interop`,
`:node-require`, `:js-interop`.

⚠ **The first version of this section asked one question where there are
two, and got the answer wrong.** It counted any host escape as a JVM blocker,
which put `kotoba.hir`, `kotoba.gmir` and five others in the first wave —
namespaces that are `.cljc`, that the JVM-free nbb route lowers through
today, and that block the JVM not at all. Their `js/` sits in a `:cljs`
branch, which is what portable code looks like. Two predicates, two orders:

| question | blocker | reachable | blockers | waves |
|---|---|---:|---:|---|
| **stop depending on the JVM** | `:clj-only` — a `.clj` with no `.cljc`/`.cljs` twin, so it runs on no other host | 100 | **18** | **6, no cycle** |
| **be Kotoba only** | any host escape, including a `:cljs` branch naming `js/` | 100 | **52** | 8 + a cycle of 9 |

### To stop depending on the JVM

```
wave 0  compiler.atomic-output, bounded-edn, coverage, ipld-adl-source,
        project-files, component.admission, verifier.signing, wasm.tools
wave 1  compiler.cache, coverage-evidence, module-lock, receipt, release,
        component.core
wave 2  component.artifact
wave 3  compiler.core
wave 4  compiler.test-profile
wave 5  compiler.cli
```

**`kotoba.verifier.signing` is in wave 0.** Everything it requires is already
`.cljc`; the only thing binding it to the JVM is that it is a `.clj` file.
The trust gap measured above is therefore not blocked behind four waves of
other work, which is what the conflated ordering implied — it can be started
now.

### To be Kotoba only

From the same three roots — `kotoba.verifier.signing`,
`kotoba.compiler.cli`, `kotoba.compiler.nbb.output-attestation`: **52
host-bound namespaces, 8 waves and one cycle.**

| wave | namespaces |
|---:|---|
| 0 | `cbor.core`, `json.core`, `compiler.atomic-output`, `bounded-edn`, `coverage`, `ipld-adl-source`, `kotoba-reader`, `project-files`, `component.admission`, `component.wit`, `gmir`, `hir`, `kir.cljs-i64`, `kir.descriptor`, `native.interrupt-abi`, `script`, `wasm.tools` |
| 1 | `artifact.core`, `capability-names`, `kir.value`, `mir`, `wasm.typed` |
| 2 | `backend.evm`, `nbb.output-attestation`, `kir.decimal`, `kir.xml`, `native.elf64`, `native.machine-ir`, `wasm.core` |
| 3 | `component.core`, `kir`, `native.aarch64`, `native.x86-64` |
| 4 | `compiler.frontend`, `reference-runtime`, `component.artifact`, `verifier` |
| 5 | `backend.cljs`, `ios-aot`, `verifier.signing` |
| 6 | `coverage-evidence`, `receipt`, `release` |
| **cycle** | `cli`, `core`, `cache`, `definition-identity`, `module-lock`, `provenance`, `test-profile`, `kir.definition-identity`, `multiformats.core` |

Two things to read from it.

**Wave 0 is the only face that can be started.** Everything after it waits on
what it requires. It is what can begin, not what is easy.

**The tail is a cycle, and nine namespaces sit in it.** `cli`, `core`,
`cache`, `definition-identity`, `module-lock`, `provenance`, `test-profile`,
`kir.definition-identity` and `multiformats.core` require each other, so no
one of them comes out alone. Whether to break the cycle or move all nine at
once is a design decision; it is not an ordering question, and the sort
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

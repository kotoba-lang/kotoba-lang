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

`scripts/measure-selfhost-distance.cljs` walks the 137 `.cljc` files on the
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

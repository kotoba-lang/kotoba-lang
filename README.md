# Kotoba

> **AI writes freely. Kotoba draws the boundary.**

Kotoba is an intuitive, declarative, security-first language and computing
stack for AI agents—and for humans who vibe-code with them.

> **Post-quantum cryptography is the default admission floor for every new
> Kotoba cryptographic boundary—not an optional compatibility mode.**

New encrypted objects require hybrid X25519 + ML-KEM-768. New package and
publication authority requires ML-DSA-65 alongside the current classical
proof. Missing PQ material and classical-only downgrade are rejection cases.
Development-only legacy paths are not compatibility requirements.

The machine-readable coverage authority is
[`security/cryptographic-boundaries.edn`](security/cryptographic-boundaries.edn).
The deployed projection is
[`/.well-known/kotoba-cryptographic-boundaries.edn`](https://kotoba-lang.org/.well-known/kotoba-cryptographic-boundaries.edn);
the repository file remains the authority.
An admitted boundary must name its PQ suite, downgrade behavior,
implementation, and negative-test evidence. Incomplete storage, compute, or
key-recovery boundaries remain explicitly blocked instead of inheriting the
repository-wide claim.

**Existing software adds security around the program. Kotoba makes security a
property of the whole computation.**

[kotoba-lang.org](https://kotoba-lang.org) · implementation and CLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[getting started](docs/getting-started.md)

The domains name different product responsibilities. `kotoba-lang.org` is this
language's specification and documentation surface. The operational entrance
is [`kotoba.cloud`](https://kotoba.cloud): Passkey identity and CLI deploy
discovery. It carries Kotoba's admitted-computation boundary into operation;
it does not replace language admission, artifact verification, or host
enforcement. Durable storage remains
[`kotobase.net`](https://kotobase.net), CPU/GPU execution remains
[`murakumo.cloud`](https://murakumo.cloud), and agent work remains
[`itonami.cloud`](https://itonami.cloud). The separation is an authority
boundary, not merely navigation or branding.

Library discovery belongs here at
[`kotoba-lang.org/libraries/`](https://kotoba-lang.org/libraries/): GitHub
provenance, exact CID dependency graphs, compatibility evidence, and bounded
comparisons are language/ecosystem facts. Publication starts in Kotoba CLI.
`kotoba library inspect` resolves names or `#hash` abbreviations to immutable
definition CIDs; `kotoba library publish` plans by default and explicitly
reuses the existing locally signed namespace-head/IPNS path when applied.
Kotoba Cloud remains the publication-control entrance, Kotobase remains block
and receipt storage. Hosted publication requires both a live Passkey session
and an ML-DSA-65 signature over the exact request; the ML-DSA key is pinned to
the Stable Principal on first valid use. This is an application-layer
post-quantum co-approval, not a claim that the authenticator's WebAuthn
credential itself is post-quantum.
The pinned ML-DSA key can be rotated through
`https://kotoba.cloud/v1/pq-keys/rotate` only with signatures from both the
current and next keys plus Passkey confirmation; revocation similarly requires
the current key plus Passkey. Independent recovery without the current key and
external transparency witnessing remain blocked boundaries.

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

That expression follows the same admitted compilation path as a file. It is
compile-and-run convenience, not unrestricted runtime `eval`.

## Why Kotoba

AI can produce useful software faster than a human can review every line. The
problem is no longer only whether generated code is correct. It is whether the
code can reach files, networks, secrets, processes, models, or money that the
request never intended to expose.

Most systems begin with a general-purpose program and add sandboxing, IAM,
containers, policy engines, signing, and deployment controls around it. Those
layers still matter, but they are asked to reconstruct intent after the program
already has broad semantics.

Kotoba starts from the opposite direction:

- authority is explicit and deny-by-default;
- effects are inferred and checked before an artifact is emitted;
- code and dependencies have content-derived identities;
- the host binds only the capabilities that survived admission; and
- successful and denied actions can produce receipts.

The goal is not to decide that AI-written code is trustworthy. The goal is to
make the boundary deterministic even when the author is an agent.

## What Kotoba feels like

**Where Lisp's mind and GP 2's graph rewriting meet Rust's discipline.**

Kotoba keeps a small, data-oriented, Clojure-shaped language. Its design draws
on Lisp's code-as-data tradition and
[GP 2's rule-based graph rewriting](https://uoycs-plasma.github.io/GP2/):
immutable values, ordinary functions, explicit data, and a composable syntax
that is easy for humans and models to generate. It adds static discipline around
authority, effects, resources, packages, and artifact identity.

It is deliberately narrower than Clojure. Ambient interop, runtime code
loading, unrestricted mutation, guest-defined macros, and unbounded concurrency
are outside the admitted component surface. The restriction is the product:
programs remain readable while the dangerous degrees of freedom stay visible
at the boundary.

**A language AI agents can use, not abuse.** This is an engineering direction,
not a claim that software can never be exploited.

## Product defaults and engineering directions

The machine-readable authority is
[`lang/product-defaults.edn`](lang/product-defaults.edn). It separates active
defaults, bounded-ready paths, partial implementations, and directions:

- **Build faster. Run faster. Keep the boundary.** Four benchmark families
  publish exact-result evidence; universal speed ranks remain withheld until
  their qualification gates pass.
- **Storage without a language ceiling.** Kotobase supplies content identity,
  range reads, immutable history, and provider-neutral storage. Physical
  capacity, tenancy, retention, replication, cost, and budgets stay explicit.
- **Post-quantum cryptography by default.** New cryptographic boundaries reject
  classical-only downgrade; Passkeys, transport, implementations, and custody
  remain separately qualified.
- **Authentication present; authority denied by default.** Identity never
  silently grants a host effect.
- **Flexible delegation that can only narrow.** Requested, delegated, local,
  resource, and target scopes intersect.
- **Web3 ready, chain-neutral at the root.** Wallets are linked accounts, not
  the principal or an ambient execution grant.
- **Zero-copy where ownership permits; one copy where a boundary requires it.**
  Borrowed Arrow buffers now retain CPU backing through the authorized
  Kotobase lake path. Network ingress, decompression, GPU upload, and immutable
  persistent updates remain explicit copy boundaries.
- **Arrow-shaped data; explicit CPU SIMD and device-native GPU kernels.** On
  Apple M4, a bounded float32 path executes an explicit Wasm `v128`/`f32x4`
  kernel over Arrow values in the same linear-memory backing, with zero
  Arrow-to-SIMD copies and a scalar tail. Across three qualified runs of the
  same 262,147-element scale workload and artifact, it completed 3.66–3.72×
  faster than scalar Wasm. This is a kernel-and-host result, not a general
  runtime claim. The GPU path retains one `ArrayBuffer`, then performs one
  measured WebGPU upload to Num on Metal. Nullable data, other dtypes, broader
  kernels, unified-memory upload removal, and other hosts remain separately
  qualified.
- **AI first; AGI-ready boundaries, not an AGI claim.** Stronger models still
  operate inside explicit effects, finite resources, receipts, and host checks.

## Hello, world

Create `hello.kotoba`:

```clojure
(ns hello (:export [main]))

(defn main [] :i64
  (+ 40 2))
```

Compile it to the primary portable target:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

Or compile a restricted web artifact:

```sh
kotoba compile hello.kotoba --target web --output hello.mjs --json
```

An empty policy grants no host effects:

```clojure
{:policy/allow #{}
 :policy/forbid-wildcard true}
```

Network, storage, model access, secrets, and other external actions require a
resource-scoped capability and a host/provider qualified to enforce it.

## The technical model

```text
intent + source
      ↓
parse, elaborate, and type-check
      ↓
checked KIR
      ↓
capability / effect / resource admission
      ↓
content-addressed, target-specific artifact
      ↓
host verifies, binds, and enforces the admitted grant
      ↓
result + execution receipt
```

### 1. Checked KIR

Kotoba source is lowered into a typed, effect-aware intermediate
representation: checked KIR. Backends consume that checked representation
rather than each inventing their own interpretation of source authority.
Unsupported types, effects, resources, or target surfaces fail closed.

Checked KIR is also a trust boundary. Artifact verification treats embedded IR
as hostile input and rechecks the properties required by the selected backend.

### 2. Capability and effect admission

A program does not receive ambient access to the host. Its transitive effects
must be representable, inferred, and admitted. Requested authority, delegated
authority, local policy, and target support intersect; none of those steps can
silently widen the grant.

```text
requested ∩ delegated ∩ local policy ∩ target support = effective grant
```

An ungranted capability is absent or unbound. Providers must independently
validate concrete resource scope at the moment of use.

### 3. Content-addressed artifacts

Source definitions, dependency closures, policies, locks, compiler contracts,
and target ABIs can participate in artifact identity. The build therefore says
which admitted computation it represents, not merely which mutable filename
was compiled.

Content addressing proves byte identity, not authorization. Signatures,
trusted-signer policy, validity, revocation, and host admission remain separate
checks.

### 4. Typed `eval`, bounded `apply`

Kotoba keeps Lisp's two central dynamic operations without reopening ambient
host evaluation. `apply` invokes an already checked closure with at most four
arguments. `(eval request)` is a typed `:code/eval` ability: its bounded
document names a checked-KIR definition by CID and carries canonical argument
documents; its result type comes from the surrounding function contract.

```clojure
(ns app (:capabilities #{:code/eval}))

(defn run [request :document] :i64
  (eval request))
```

Before execution, the host binds the DefCID, exact interface and effect row,
current effect allowance, fuel, and decreasing nested-eval depth into an
AdmissionCID. The typed output is persisted as a ValueCID. The three identities
are deliberately distinct: a definition hash proves what code was selected,
not that it may run, and a result hash is evidence after execution rather than
authority for an effect. Host `eval`, source strings, reader evaluation, and
ambient namespace lookup remain forbidden. See
[`lang/typed-eval.edn`](lang/typed-eval.edn) and the
[`typed eval ADR`](docs/adr/ADR-kotoba-typed-eval.md).

### 5. Host enforcement

The runtime or tender verifies the artifact and binds only the admitted imports.
The provider or native handler rechecks the resource scope before performing an
effect. Fuel, memory, time, output, and other budgets remain finite. Decisions
and actions can be recorded in signed receipts.

WebAssembly Components are the primary portable application profile. Bounded
native AOT for x86-64 and AArch64 is an explicitly selected backend with a
separate verifier and OS-isolation requirement. Ambient native processes are
not the ordinary application model.

## Security model

Kotoba's claim is confinement through explicit authority, not perfection.

What the bounded, qualified model is designed to establish:

- every transitive component effect is declared and admitted before emission;
- ungranted capability imports do not reach a provider or native handler;
- inputs and execution have explicit finite bounds;
- artifact identity and release evidence can be bound to trusted signers; and
- concrete host effects are re-authorized at the resource boundary and
  receipted.

What Kotoba does **not** claim:

- that the stack is unhackable;
- a general Rust ownership, borrowing, or lifetime system for every value;
- that every source file or runtime value is automatically encrypted;
- that native code needs no OS isolation;
- that every backend has feature parity; or
- universal performance superiority over LLVM, Rust, Wasm, or another runtime.

The trusted computing base still includes the compiler admission path, artifact
verifier, runtime engine or native loader, policy roots, provider
implementations, key custody, and operating-system isolation. The current
machine-readable claims and their residual risks live in
[`lang/safety-claims.edn`](lang/safety-claims.edn).

## Architecture

The repositories are separated so that a language claim can be reviewed
independently from the code that implements or operates it.

| Layer | Responsibility | Authority |
|---|---|---|
| Language | Grammar, semantics, effects, CLI contract, conformance, safety claims | this repository, `kotoba-lang/kotoba-lang` |
| Compiler | Elaboration, type/effect checking, checked KIR, qualified target emission | [`kotoba-lang/amu`](https://github.com/kotoba-lang/amu) |
| Implementation | Installable CLI, host integrations, providers, and integration evidence | [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) |
| Tender | Artifact admission, capability binding, runtime limits, and receipts | [`kotoba-lang/kototama`](https://github.com/kotoba-lang/kototama) |
| Native path | Machine-code backends, KEXE verification, supervised loading | [`kotoba-lang/kotoba-native`](https://github.com/kotoba-lang/kotoba-native) and `tender-native` |
| Data plane | Persistent Datalog and content-addressed application state | [`kotoba-lang/kotobase`](https://github.com/kotoba-lang/kotobase) |
| Fleet | Hosting, placement, deployment, and operational control | [`kotoba-lang/murakumo`](https://github.com/kotoba-lang/murakumo) |

These names describe ownership boundaries, not a requirement that an
application developer understand every repository before writing Kotoba.

## Proof, with the boundary attached

- The language grammar, surface status, capability catalog, safety claims, CLI,
  and conformance suite are machine-readable in this repository.
- Positive and negative fixtures exercise both what the profile accepts and
  what it must reject.
- Checked KIR is shared by qualified portable and native target paths; backend
  coverage is reported per target rather than assumed.
- The wider Kotoba stack currently runs **33 inference cores for internal
  production dogfooding**. This is evidence that the team operates its own
  stack; it is **not customer traction, paid adoption, or revenue**.

See [`docs/maturity.md`](docs/maturity.md) for maturity by axis and
[`docs/README.md`](docs/README.md) for the documentation routes. Contract-stage
labels do not imply ecosystem, adoption, release, or production-SLO maturity.

## Install

### Homebrew (macOS and Linux)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 users may need to trust the tap once:

```sh
brew trust kotoba-lang/kotoba
```

### Verified shell installer

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

The installer verifies the published archive checksum. Native release
availability is platform-specific; use the source launcher when no qualified
archive exists for the current platform.

### From source

```sh
git clone https://github.com/kotoba-lang/kotoba.git
cd kotoba
bin/kotoba-clj check --kind cli-contract --json
```

### Web3-first identity

The public identity command starts with a chain-neutral Kotoba principal and a
Passkey/WebAuthn controller. An EVM smart account is an explicit CAIP-10 link,
verified with ERC-1271 or ERC-6492 when counterfactual; it is not the principal.
The CLI never asks for or prints a private key, and a verified login still needs
an explicitly scoped capability before an agent can act.

```sh
kotoba id new --rp-id itonami.cloud
# urn:kotoba:principal:<random id> + Passkey registration plan

kotoba id new --rp-id itonami.cloud \
  --account eip155:8453:0xA00366234D29d4F882088048c0B2fa0dB7302D4E \
  --account eip155:1:0xA00366234D29d4F882088048c0B2fa0dB7302D4E
```

Base is useful for Murakumo settlement, but is never selected implicitly. A
legacy EVM address can still be described as a linked account by supplying its
chain explicitly: `kotoba id account --address 0x… --chain-id 8453`.

## Source contract

- `.kotoba` is canonical Kotoba component source.
- `.cljc` is portable Clojure-family source and may use a `:kotoba` reader
  branch.
- `.cljk` is CLJ Kotoba source; it is not a JVM target.
- `.clj` and `.cljs` keep their normal single-target meaning.

Kotoba is not a promise that arbitrary JVM Clojure or ClojureScript programs
will run. The admitted grammar is
[`lang/guest-grammar.edn`](lang/guest-grammar.edn), current implementation and
intentional exclusions are in
[`lang/surface-status.edn`](lang/surface-status.edn), and fixtures live under
[`lang/conformance/`](lang/conformance/).

## Roadmap

The direction is intentionally incremental and fail-closed.

**Now**

- keep grammar, effect inference, checked KIR, target adapters, and public docs
  on one versioned contract;
- preserve deny-by-default host behavior and concrete resource scope;
- publish target-specific qualification rather than collapsing it into one
  maturity number; and
- improve the first-run path without widening the language surface.

**Next**

- close typed request/result and provider-conformance gaps for HTTP, storage,
  model, secret, and database capabilities;
- expand differential and adversarial conformance across qualified backends;
- strengthen signed artifact, revocation, receipt, and reproducible-release
  operations; and
- continue bounded native coverage only where the verifier and loader fail
  closed outside the supported slice.

**Later**

- widen production deployment only after independent provider, host-isolation,
  rollback, and soak evidence exists;
- grow the ecosystem through small declarative libraries whose effects remain
  inspectable; and
- keep unsupported ambient authority a non-goal rather than treating it as a
  compatibility backlog.

Roadmap items are not promises of shipped capability. Current status remains
authoritative in machine-readable contracts and qualification evidence.

## Documentation

- [Getting started](docs/getting-started.md)
- [Documentation map](docs/README.md)
- [Language surface status](lang/surface-status.edn)
- [Safety claims and residual risks](lang/safety-claims.edn)
- [Capability semantics](lang/capability-semantics.edn)
- [Component role model](lang/component-role-model.edn)
- [Library publication authority](lang/library-publication.edn)
- [Public library catalog](https://kotoba-lang.org/libraries/)
- [Maturity by axis](docs/maturity.md)
- [Architecture decisions](docs/adr/)

## Verify this repository

```sh
clojure -M:test
nbb scripts/check-docs.cljs
bb scripts/check-cli-contract.bb lang/cli.edn
bb scripts/check-capability-values.bb
bb scripts/check-legacy-runtime-absence.bb
```

This repository is the CLJC/EDN language authority. Native, JVM, Node, or other
launchers are adapters and must not define independent language or CLI
semantics.

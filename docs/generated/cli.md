# Generated Kotoba CLI reference

> Generated from [`lang/cli.edn`](../../lang/cli.edn). Do not edit by hand.

Contract version: `1`. Host executables are adapters; unsupported commands must fail explicitly.

## `kotoba id`

Create a chain-neutral Kotoba principal enrollment plan controlled by a passkey. Smart accounts are explicit CAIP-10 links; no chain or provider is the identity root.

Maturity tier: `m2`.

Positionals:

- **`action`** — new (default) for Passkey enrollment, or account to describe a compatibility EVM account link. Type: `string`.

Options:

- **`--principal`** — Existing DID or urn:kotoba:principal:* to enroll. When omitted, the host creates a random chain-neutral principal id. Type: `string`.
- **`--rp-id`** — WebAuthn relying-party id that will perform the real, single-use Passkey registration ceremony. Type: `string`.
- **`--account`** — Optional CAIP-10 account to link after proof. EVM links use ERC-4337/1271/6492; Base is explicit eip155:8453, never a default. Type: `string`; repeatable.
- **`--address`** — Compatibility public 0x EVM account address. Produces a linked account descriptor, not a principal. Type: `string`.
- **`--chain-id`** — Required with --address. No EIP-155 chain is selected implicitly. Type: `int`.
- **`--json`** — Emit the identity result as JSON. Type: `boolean`.

## `kotoba run`

Compile and run a Kotoba entry point.

Maturity tier: `m1`.

Positionals:

- **`entry`** — Kotoba, CLJ, CLJC, or CLJS source file. Optional when :expr is supplied. Type: `path`.

Options:

- **`-e`, `--expr`** — Inline expression compiled as exported main; compile-and-run sugar, not runtime eval. Type: `string`.
- **`-S`, `--source-path`** — Namespace source root. KOTOBA_CLJ_PATH remains a compatibility alias outside this contract. Type: `path`; repeatable.
- **`--target`** — Reader target for portable source. Type: `enum`; values: `kotoba`, `clj`, `cljs`; default: `kotoba`.
- **`--function`** — Exported function to invoke. Type: `string`; default: `main`.
- **`--arg`** — EDN argument passed to the entry function. Type: `edn`; repeatable.
- **`--json`** — Emit machine-readable JSON result. Type: `boolean`.

## `kotoba compile`

Compile Kotoba-family source to a target artifact. Web .kotoba uses checked KIR and the restricted kotoba-script backend; .cljs remains ClojureScript.

Maturity tier: `m1`.

Positionals:

- **`entry`** — .kotoba, .cljc, or .cljk source file. Type: `path`; required.

Options:

- **`--target`** — Compilation target. Type: `enum`; values: `web`, `wasm`; default: `wasm`.
- **`--prelude`** — Explicit portable source prelude prepended before the entry source. No prelude is loaded implicitly. Type: `path`.
- **`-o`, `--output`** — Output artifact path. Type: `path`.
- **`--json`** — Emit machine-readable diagnostics. Type: `boolean`.

## `kotoba check`

Validate Kotoba source, contracts, or package metadata without running it. Compiler adapter: frontend admit + --profile pure-product (T9.2).

Maturity tier: `m2`.

Positionals:

- **`input`** — Source file, package manifest, lock file, or contract document. Type: `path`.

Options:

- **`--kind`** — Validation domain. Type: `enum`; values: `source`, `package`, `lock`, `cli-contract`, `auto`; default: `auto`.
- **`-S`, `--source-path`** — Namespace source root for source checks. Type: `path`; repeatable.
- **`--target`** — Reader target for portable source checks. Type: `enum`; values: `kotoba`, `clj`, `cljs`; default: `kotoba`.
- **`--safe`** — Apply the capability-safe Kotoba profile. Type: `boolean`; default: `true`.
- **`--json`** — Emit machine-readable diagnostics. Type: `boolean`.

## `kotoba graph`

Query and transact the language graph store (kgraph) with Datomic-shaped operations.

Maturity tier: `m1`.

Subcommands: `connect`, `query`, `transact`, `pull`, `status`.

Options:

- **`--graph`, `--db`** — Graph handle: mem:<alias>, file:<path>, or a filesystem path. --db remains a compatibility alias. Type: `string`; required.
- **`--op`** — Data operation. Mirrors the supported subcommands for adapters without nested command support. Type: `enum`; values: `connect`, `query`, `transact`, `pull`, `status`.
- **`-f`, `--file`** — EDN query, transaction, or pull request file. Type: `path`.
- **`--param`** — EDN parameter binding for query-like operations. Type: `edn`; repeatable.
- **`--json`** — Emit machine-readable result. Type: `boolean`.

## `kotoba git`

Expose Kotoba repository operations as data, not shell-specific behavior.

Maturity tier: `m1`.

Subcommands: `init`, `status`, `commit`, `sync`.

Options:

- **`--repo`** — Repository root. Type: `path`; default: `.`.
- **`--op`** — Repository operation. Mirrors subcommands for simple host adapters. Type: `enum`; values: `init`, `status`, `commit`, `sync`.
- **`-m`, `--message`** — Commit message for commit-like operations. Type: `string`.
- **`--ref`** — Branch, tag, or revision reference. Type: `string`.
- **`--json`** — Emit machine-readable repository status. Type: `boolean`.

## `kotoba rad`

Run rapid application development workflows over Kotoba packages.

Maturity tier: `m1`.

Subcommands: `new`, `build`, `test`, `export`.

Options:

- **`--project`** — Project root. Type: `path`; default: `.`.
- **`--op`** — RAD operation. Mirrors subcommands for simple host adapters. Type: `enum`; values: `new`, `build`, `test`, `export`.
- **`--template`** — Project or artifact template. Type: `string`.
- **`--profile`** — Build or test profile. Type: `enum`; values: `dev`, `test`, `release`; default: `dev`.
- **`-o`, `--output`** — Output path for build/export artifacts. Type: `path`.
- **`--json`** — Emit machine-readable workflow result. Type: `boolean`.

## `kotoba deploy`

Plan and apply package desired-state to a local receipt or a murakumo fleet reside target.

Maturity tier: `m1`.

Subcommands: `plan`, `apply`, `status`, `rollback`.

Options:

- **`--manifest`** — Package or deployment manifest. Type: `path`; default: `package-manifest.edn`.
- **`--op`** — Deployment operation. Mirrors subcommands for simple host adapters. Type: `enum`; values: `plan`, `apply`, `status`, `rollback`.
- **`--target`** — Local name (dev), file: URI, absolute path, or murakumo:<node> / fleet:<node>. Compute reside is the murakumo Mac mini fleet; Deno Deploy and Cloudflare are not targets of this command. Type: `string`; required.
- **`--dry-run`** — Plan without applying host-side changes. Type: `boolean`; default: `true`.
- **`--revision`** — Revision to deploy or roll back to. Type: `string`.
- **`--json`** — Emit machine-readable deployment status. Type: `boolean`.

## `kotoba hinshitsu`

Run software-quality checks (evidence, gates, coverage, visual regression) as data.

Maturity tier: `m1`.

Subcommands: `evidence`, `gate`, `coverage`, `mokushi`.

Options:

- **`-f`, `--file`** — EDN evidence document, gate spec, or coverage spec. Type: `path`.
- **`--op`** — Quality operation. Mirrors subcommands for simple host adapters. Type: `enum`; values: `evidence`, `gate`, `coverage`, `mokushi`.
- **`--baseline`** — Baseline artifact path for mokushi visual comparisons. Type: `path`.
- **`--candidate`** — Candidate artifact path for mokushi visual comparisons. Type: `path`.
- **`--threshold`** — Maximum allowed RMSE distortion for mokushi visual comparisons. Type: `string`; default: `0.02`.
- **`--required-check`** — Check name that must be present and passed for a gate to succeed. Type: `string`; repeatable.
- **`--json`** — Emit machine-readable quality-check result. Type: `boolean`.


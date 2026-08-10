# Agent handoff: Kotoba refactor (`.kotoba` migration)

**Authoritative superproject ADR:**  
`com-junkawasaki/root` → `90-docs/adr/2607289500-kotoba-refactor-agent-handoff-and-capability-repo-cid.edn`  
(policy root: `ADR-2607279200` Clojure-shaped + safety elaboration)

**Living plan:** [`docs/kotoba-centered-migration-plan.md`](./kotoba-centered-migration-plan.md)

This file is the **runbook** so a fresh agent can continue without chat history.

---

## 0a. The compiler repo is named `amu` (2026-08-10)

`kotoba-lang/compiler` was **renamed to `kotoba-lang/amu`**. Everything below that
says "compiler" means that same repository:

- west path is **`orgs/kotoba-lang/amu`** (not `orgs/kotoba-lang/compiler`), CLI is
  **`bin/amu`**; `bin/kotoba-compiler` and the `kotoba.compiler.*` namespaces remain
  as compatibility APIs.
- `compiler#412`, `compiler ADR 0191`, … still resolve — GitHub keeps PR and issue
  numbers across a rename, so those references were **not** rewritten here.
- The old name still redirects on GitHub, which is exactly why this bites: a stale
  clone at `orgs/kotoba-lang/compiler` keeps fetching fine while the **west-managed
  path is empty**, so `bin/amu` looks missing. Materialise it with
  `west update --fetch smart amu` before concluding the toolchain is absent.
  (Same failure mode as `kotoba-fleet-vcs` → `kagi`; see superproject CLAUDE.md.)

The 2026-08-10 `chore: migrate Amu paths across language tooling` sweep updated the
plan, the ADRs and `lang/*.edn`, but **skipped this runbook** — hence this section
rather than a rewrite.

---

## 0. Read order (do not skip)

1. Superproject `ADR-2607279200` — source stays Clojure-shaped; no new app DSL
2. Superproject `ADR-2607289500` — handoff + capability CID boundary
3. This runbook + `docs/kotoba-centered-migration-plan.md` (especially **Next**)
4. Relevant inventory EDN under `lang/w6-*.edn`
5. If touching capabilities: `kotoba-core-contracts` README + `capability_repository.cljc`

---

## 0b. What is left in the toolchain repos is not backlog (2026-08-10)

"Move the remaining `.cljc` to `.kotoba`" is a reasonable-sounding task that,
across `kotoba`, `aiueos`, `kotoba-native` and `amu`, is mostly already answered
— in accepted decisions rather than in the file counts. Measured on `main`:

| repo | `.kotoba` | `.clj`+`.cljc`+`.cljs` | what the non-Kotoba mass is |
|---|---|---|---|
| `aiueos` | 5,093 L / 65 | 15,903 L | 59 kernel objects **are** the Kotoba; the rest is the host plane |
| `kotoba` | 4,060 L / 103 | 24,748 L | `runtime.clj` 5,820 + `launcher.clj` 1,918 + `wasm_exec.clj` 862 — host mechanism |
| `amu` | 1,053 L / 121 | 35,103 L | conformance fixtures and examples; **no product `.kotoba`** |
| `kotoba-native` | **0** | 7,042 L | the x86-64/AArch64 backend itself |

The two that look worst are settled:

- **`amu` stays CLJ.** `ADR-reliability-t63-tool-vs-runtime` decision 3 —
  *"Compiler remains CLJ as the build/analysis tool — no requirement to
  self-host the compiler before R3."* Its 121 `.kotoba` files are
  `resources/kotoba/lang-conformance/*`, `test/nbb/fixtures` and `examples`;
  that is what a compiler's `.kotoba` is supposed to be.
- **`kotoba-native` has no `.kotoba` by construction.** It is the machine-code
  backend, and `ADR-2607072000` puts what would otherwise be Rust into `.cljc`.

`aiueos` and `kotoba` are host planes, which this plan's own completion criteria
keep: *"cljs/cljc remain only as bounded compatibility or implementation
layers"*, and the per-slice checklist ends at *"Host mechanism contains no
product policy."*

**So the open question is not how much moves. It is whether the boundary is
honest** — whether any decision is sitting in host mechanism. Two findings that
say it is worth asking:

- `aiueos/src/broker.cljc` names itself "the capability broker's decision
  logic", and `decide.cljc` exists so that a native host adapter *"shells out
  to"* it rather than deciding itself. The decision is real and it is in a
  CLJ subprocess. It does **not** move as it stands: the native gate
  (`only-native-word-typed-features?`) rejects escaping records, maps,
  variants, typed sets and heterogeneous vectors, and `verify-system` folds
  vectors of violation maps. Only its scalar core (`trust-rank`,
  `below-verified?`, the signature ladder) is native-shaped, and extracting
  that alone does not remove the subprocess — so do not sell it as removing a
  JVM dependency.
- Nothing here has been audited for the checklist's last line. That audit, not
  a file-count reduction, is the next real slice.

---

## 1. Where we are (2026-08-01)

### Done (high level)

| Area | Status |
|---|---|
| W0–W1 grammar / named-op elaboration | done |
| Design-system form-A + Delivery-6 document cutover | **5/5** (css→…→kotoba-ui) |
| W5 kits dual-runtime + production transports | broadly done |
| W5 get-stream, linear resources, CM packaging | compiler ADR **0120–0136** |
| W5 guest move typing | **0137–0139** |
| W5 product verticals (put/get/CAS/conditional) | **0140–0142** |
| W6 inventories | murakumo / cloudflare / kbb gap |
| W6 murakumo pure-planners **high** | **6/6** (#37–#42) |
| W6 murakumo pure-planners **medium+** | **complete through #113** (full KIR catalog + all portable pure hosts dual-source; Product Value ABI v1 token) |
| W6 cloudflare pure-request / deploy oracles | client / stream / analytics / deploy / pages (com-cloudflare) |
| W6 kbb ability gaps | **COMPLETE** (process / scoped-fs / secret / git / entropy / cloud-deploy; SSH host-forever) |
| W6 process / scoped-fs / secret / git / entropy kits | provider ADR **0143–0151** (ids 19–23; cljs OS transports landed) |
| W6 ops kit EDN packages + signing honesty | provider **#34** / ADR **0152** (wasm-aot/signed package pending) |
| residual ops config inject (exact-name getenv) | murakumo#137+#146+#147 |

| kit ready checklist T8.1/T8.2 + unsigned package fingerprint | provider#35 / ADR 0153 |
| T8.3 first slice: signed kit EDN package receipts | provider#36 / ADR 0154 |
| T8.3 remainder first slice: signed Wasm provider receipts (fixture digest API) | provider#37 / ADR 0155 |
| provision.plan pure oracle expand (launch/peer/watchdog) | murakumo#139 |
| reconcile.plan pure oracle expand (CLI flags + action gates) | murakumo#140 |
| cloud.plan pure oracle expand (webtransport + generic endpoints) | murakumo#142 |
| persist pure oracle expand (envelope operation + curl headers) | murakumo#141 |
| task.plan pure oracle expand (unschedulable-detail) | murakumo#143 |
| tunnel pick-exit/trim-err + secret kit-reply pure | murakumo#144 |
| kekkai.gate pure expand (cli-argv fragments) | murakumo#145 |
| package-manifest + production blockers | provider#40 / ADR 0158 |
| T8.3 real non-fixture Wasm package pilot (hash-sha256) | provider#41 / ADR 0159 |
| Reliability T1.2 required-backends matrix (manifest v2) | kotoba-lang (this PR) / ADR-reliability-t12 |
| Reliability T1.3 dual-backend runner pilot (5 fixtures) | compiler#412 / ADR 0161 |
| Reliability T1.3 pilot expand (7 cases, string kit) | compiler#414 / ADR 0163 |
| Reliability T1.3 string kit surface (12 cases dual-green) | compiler#415 / ADR 0164 |
| Reliability T1.3 record kit (13 cases) + T4.4 | compiler#416 / ADR 0165 |
| Reliability T2.2 surface-matrix generator | kotoba-lang (this PR) / ADR-reliability-t22 |
| Reliability T4.4 record cookbook | kotoba-lang (this PR) / ADR-reliability-t44 |
| pure allowlist wasm set (8) + host-grant digest binding | provider#42 / ADR 0160 |
| kekkai ledger/dir/HOME via murakumo.config | murakumo#146 |
| overlay cert MURAKUMO_KAGI_DIR via murakumo.config | murakumo#147 |
| overlay.cert MURAKUMO_KAGI_DIR via murakumo.config | murakumo#147 |
| Ed25519 identity-signer inject proven (test dep) | provider#39 / ADR 0157 |
| identity.sign inject adapter + empty-module fixture | provider#38 / ADR 0156 |
| T8.4 host-parity L5 critical conformance fixtures + resources sync | kotoba-lang (this PR) |
| deploy.plan pure oracle expand (execution probe + pin paths) | murakumo#138 |
| dash.state probe parse pure oracle expand | murakumo#136 |
| Pure capability allowlist reference-impl | **COMPLETE** (sin / cos / sha256 / cbor / json / clock / random / now-days) |
| **T8.2 ops/object/storage checklist (2026-08-01)** | **COMPLETE** except object/storage `:signed-wasm :pending` (no production-signed wasm claim). Ops audit `:ready` (provider ADR 0269); object/storage audit+deny (0271–0273); inject parity (0270) |
| **T8.3 ops guest host plane (2026-08-01)** | **COMPLETE**: W4 recursive EDN 0246–0255; codec AOT 0256–0259; guest host surfaces HTTP/secret/process/git/entropy/fs 0260–0268; inject parity 0270 |
| **Ops wire ids 19–23 registration** | **COMPLETE**: catalog (kotoba-lang#358) + compiler vendoring (compiler#470 ADR 0198) + component-model inventory (component#120 ADR 0120) + compiler pin (compiler#471 ADR 0199) |
| Network / secret capability packages | kits + readiness + signed receipts + package-manifest (provider#34–#40); pure allowlist **8 real wasm packages + grant-binding** (provider#41–#42 / ADR 0159–0160); **ops/network production AOT + readiness `:signed-wasm :ready` still pending** |

### Language reliability parity (CLJ/CLJS-class trust)

Authoritative work breakdown (repos, tasks, sequencing, agent guide):

- [`docs/kotoba-reliability-parity-wbs.md`](./kotoba-reliability-parity-wbs.md)
- **Semantics SSoT (T1.1):** [`docs/lang/semantics-ssot.md`](./lang/semantics-ssot.md)

Tiers R1–R4: semantic conformance → stdlib/records → standalone run → toolchain/LTS.
Product dual-source remains separate (this handoff §2); do not invent language features in product PRs.

**R1 progress:** #295 semantics-ssot, #411 pure-product + error codes, #309 T1.2 matrix, compiler#412–#437 T1.3×**52** (2-source map + list-rest + when-let/u64 + reduce-named/pair/string + named-hof/thread/option + …) + T1.4/T1.5 + T3.1–T3.4 + **T7.1** zero-charge loop + **T7.4** + **T4.4** + **T4.5** vector-i64 map/filter/reduce + named HOF + 2-source map + T9.1–T9.3 + T2.2/T2.4 + T5.4 + T6.1/T6.3 + T7.2/T7.3 + **T10.1–T10.3**.

Next language work: T1.3 full matrix; T7.1 residual mutual-recursion TCO; T4.2 full string-split→collection optional; T4.5 3+ source map / stored closures / nested `do` wasm-typed; T9.1 remaining public adapters (db/git/…). **T8.3 ops guest host + wire 19–23 registration landed 2026-08-01** (see Plan Next — host I/O wasm-aot remains partial by design). **Profile 5 bool-typed predicates landed** (compiler ADR 0191 + composed-surface kit #461; release 0.5.0 authority). **T5.3 packs→records complete** murakumo#193–#206. **T5.2 product host bridge largely complete** (call-record close-out murakumo#261–#276 + com-cloudflare#18 + com-cloudflare-compat#6; native guest record pilot murakumo#277 schedule/task eligibility). **T6.4 oracle-required** murakumo fleet + com-cloudflare#19 + compat#6.
**T7.2 fuel model:** [`docs/lang/fuel-model.md`](./lang/fuel-model.md) (1 unit/function entry, default 512).
**T1.5 goldens:** compiler#418 / ADR 0167 — `clojure -M:conformance --check-golden`.
**T2.2 surface matrix:** [`docs/lang/surface-matrix.md`](./lang/surface-matrix.md) (`clojure -M -m kotoba.lang.surface-matrix --check`).
**T2.4 ambient corpus:** compiler#417 / ADR 0166 + [`grade-a-malicious-source-corpus.md`](./grade-a-malicious-source-corpus.md).
**T4.4 records:** [`docs/lang/record-cookbook.md`](./lang/record-cookbook.md) + compiler#416.
**T4.5 collections costs:** [`docs/lang/collections-costs.md`](./lang/collections-costs.md).
**T5.4 max-parameters:** keep **5** ([ADR-reliability-t54](./adr/ADR-reliability-t54-max-parameters.md)).
**T6.3 tool vs runtime:** [ADR-reliability-t63](./adr/ADR-reliability-t63-tool-vs-runtime.md).
**T6.1 standalone run:** [`docs/lang/standalone-run.md`](./lang/standalone-run.md) (wasmtime primary).
**T10 compatibility:** `clojure -M:compatibility` (profile 4 / release 0.4.0).
**T1.4 pure-native pilot:** compiler#419 / ADR 0168 — `clojure -M:native-conformance`.
**T7.3 fuel-estimate:** compiler#419 / ADR 0169 — `clojure -M:fuel-estimate <file>`.
**T9.2/T3.4 check CLI:** compiler#420 / ADR 0170 — `clojure -M:run check <file> --profile pure-product`.
**T1.3 pilot 20 dual-green:** compiler#421 / ADR 0171 — `clojure -M:conformance` / `--check-golden`.
**T3.2 capability deny:** compiler#421 — missing grants named in message + error code.
**T9.3 test harness:** compiler#421 — `clojure -M:run test <file.kotoba>` (export `test-*` → i64 1).
**T3.1 error codes:** compiler#422 / ADR 0172 — every `reject!` has a code (default `:subset-reject`).
**T9.1 CLI adapters:** `lang/cli-adapter-matrix.edn` + `clojure -M:cli-adapter-matrix` (check is M2).
**T7.1 loop pilot:** compiler#423 — loop/recur dual-green (helper desugar; not machine TCO).
**T3.3 fuel traps:** kotoba-kir#20 + compiler#423 pin — `:function` + `:call-stack` on fuel-exhausted.
**T7.4 deep loop 10k:** compiler#424 / ADR 0174 + kotoba-kir#21+#22 — `:loop-deep-kit` (fuel 12000); loop-helper trampoline; zero-charge still open.
**T1.3 pilot 28:** compiler#425 / ADR 0175 — if-some + string-byte-length + when/cond/if-let + case + bitops dual-green.
**T4.4 typed-map pilot 29:** compiler#426 / ADR 0176 — `[:map :i64 :i64]` new/count/get/contains/assoc/equal dual-green.
**T4.2 string-split-count 30:** compiler#427 / ADR 0177 + kir#23 + wasm#34 — segment count dual-green; full split deferred.
**T7.1 zero-charge loop:** compiler#428 / ADR 0178 + kir#24 + wasm#35+#36 — `__kotoba_loop_N` free after first entry; deep kit fuel 16.
**T4.5 vector-i64 pilot 31:** compiler#429 / ADR 0179 + wasm#37 — count/at/conj dual-green.
**T1.3 pilot 34:** compiler#430 / ADR 0180 — vector-assoc/drop/get + typed-map-dissoc + quot/bit-not dual-green.
**T1.3 pilot 38:** compiler#431 / ADR 0181 — pred + when-ext + if-some-string + shift dual-green.
**T1.3 pilot 41:** compiler#432 / ADR 0182 — inc/dec desugar + vector-sum loop + shift-right dual-green.
**T4.5 reduce-vector pilot 42:** compiler#433 / ADR 0183 — `(reduce + init v)` / `(reduce (fn [a x] e) init v)` dual-green.
**T4.5 map/filter-vector pilot 43:** compiler#434 / ADR 0184 — `(map (fn [x] e) v)` / `(map inc|dec v)` / `(filter (fn [x] pred) v)` dual-green (loop helpers may return `:vector-i64`).
**T1.3/T4.5 pilot 46:** compiler#435 / ADR 0185 — named unary HOF for map/filter + `->`/`->>`/`as->`/`cond->` + option/result/`not` dual-green.
**T1.3/T4.5 pilot 49:** compiler#436 / ADR 0186 — named binary reduce + pair/list + string-length/concat dual-green.
**T4.5/T1.3 pilot 52:** compiler#437 / ADR 0187 — 2-source map (shortest stop) + second/rest + when-let/u64/cmp dual-green.

### Plan Next (priority order)

1. **Host I/O wasm-aot honesty** — ops kits keep `:wasm-aot :partial` while host authority (network/spawn/store/git/CSPRNG) remains host-injected. **Do not** flip `:wasm-aot :implemented` or production `:signed-wasm` without production-admissible signed packages. Guest host surfaces + inject/deny/roundtrip plane is **landed** (provider 0260–0273). Prefer new product pure oracles over re-proposing packing walks / W4 / host inject already on main.
2. **object/storage signed-wasm** — still `:pending` (no production-signed object/storage wasm). Optional future: fixed-depth pure EDN codecs for storage (like secret 0236) as codec-only packages — does **not** by itself justify production signed-wasm claims.
3. **Host parity L5 residual** — T8.4 **partial**: critical fixtures + Node inject honesty/live corpus (kototama#122–#125); remaining production qualification for SCRAM/TLS success paths + browser gaps
4. **T5.2 native guest record wire expansion** — pilot landed murakumo#277. Optional: fold other multi-scalar pure exports that are one conceptual record (not CLI token lines)
5. **Identity inject** — adapter (#38) + Ed25519 proof (#39 / ADR 0157); hosts wire kagi/CACAO for production keys; HMAC doubles stay tests-only
6. **Language reliability residual** — T1.3 full matrix progressive; T7.1 mutual-recursion TCO; T4.2 full string-split→collection optional; T4.5 3+ source map / stored closures; T9.1 remaining public adapters (db/git/…)

**Landed profile 5 (2026-07-30→31):** compiler ADR 0191 A/B/C + composed-surface kit (compiler#461); kotoba-lang release 0.5.0 authority; pure-product includes `:bool` / `[:option :bool]`. Do **not** re-open frontend-only spikes.

**Landed T5.2 product host bridge (2026-07-31):** murakumo call-record waves #155+#261–#276 (positional close-out); com-cloudflare#18; com-cloudflare-compat#6. **T5.2 native record pilot** murakumo#277. Residual: optional more native-record fold-ins.

**Landed T6.4 oracle-required (2026-07-31):** murakumo mirror-delete trail; com-cloudflare#19; com-cloudflare-compat#6. cljs/nbb must `preload!` / `register-kir!` before requiring product shells.

**Landed T5.3 packs→records + flag cutovers (2026-07-29→30):** murakumo#193–#206 — seats, schedule/task eligibility, plan, schedule-assign, credits shares, reconcile targets+name-order, task assign, rebalance demand/order. Pure-product `:value-types` includes `:record` + `:record-ops`. **No base-N packing remains in murakumo pure-planner oracles.**

**Landed 2026-07-28:** full murakumo product-shell dual-source — bulk KIR catalog (#99) + host wire through overlay-driver/runtime (#113) + Product Value ABI v1 #112–#121 + cljs/nbb oracle load (#122); live HMAC/AES (#87).

**Landed T8.2/T8.3 ops + readiness wave (2026-08-01):** provider W4/codecs/guest-host/inject 0246–0270; T8.2 audit/deny 0269–0273; catalog wire 19–23 (this repo#358); component-model 19–23 (component#120); compiler pin (compiler#470–#471). **Do not re-open** ops packing walks, W4 recursive EDN, or guest host inject as greenfield.

### Do not

- Invent a new application DSL
- Use legacy `kotoba wasm emit` as the language ceiling (use `kotoba compile`)
- Hardcode numeric capability IDs in app source
- Treat GitHub repo name or Radicle RID as import **identity**
- Ambient PATH / CWD / home for process/fs transports
- Secret/list/enumerate/keychain dump

---

## 2. How to cut over product pure cores (oracle pattern)

```text
inventory → kotoba/*_core.kotoba → cljc parity tests → leave host in cljc
         → update lang/w6-*-inventory.edn + migration plan Next
```

1. Classify path as `portable-pure` vs `host-mechanism` (inventory).
2. Port **only** pure string/integer/bitmask helpers to `.kotoba`.
3. Keep HTTP/SSH/spawn/DOM/crypto host on cljc/nbb.
4. Prove parity with deterministic fixtures (counts go in the PR).
5. Work in a **sibling worktree** outside the superproject; pin one west entry at a time.

Examples already landed:

- murakumo: `kotoba/kekkai_gate_core.kotoba`, `infer_plan_core`, `dash_state_core`, …
- com-cloudflare: `kotoba/stream_core.kotoba`, `analytics_core`, `workers_path_core`

---

## 3. Capability repos + semantic CID (remote, merged)

### Pins

| Repo | PR | Merge SHA |
|---|---|---|
| [kotoba-core-contracts](https://github.com/kotoba-lang/kotoba-core-contracts) | [#21](https://github.com/kotoba-lang/kotoba-core-contracts/pull/21) | `2e615d17406ef4b6311401167294becceb5af2a1` |
| [tamaki](https://github.com/kotoba-lang/tamaki) | [#10](https://github.com/kotoba-lang/tamaki/pull/10) | `660e70bfbb08423d1f498616a68d4d01ae2a39e5` |
| [kototama](https://github.com/kotoba-lang/kototama) | [#95](https://github.com/kotoba-lang/kototama/pull/95) | `74146a896dfae72b8eb407acf6f5717e0d2fd2e1` |

### Contract (do not weaken)

| Rule | Detail |
|---|---|
| 1 cap = 1 public repo | `capability-<id-with-dashes>` (e.g. `capability-http-fetch`) |
| Import identity | `:capability/definition-cid` = CIDv1 over canonical DAG-CBOR definition block |
| Discovery only | GitHub name + Radicle RID are **aliases** |
| Hash rules pinned | `:capability/hash-contract-cid` |
| No ambient authority | Knowing a CID ≠ grant; Tamaki request + Kototama admit + policy |
| Scaffold | `clojure -M -m scaffold-capability-repos /abs/out [--update]` from core-contracts |
| Generated status | `contract-only` until signed content-addressed Wasm component exists |

Definition block **includes:** schema, version, ABI ns/version, sorted imports/effects, defaultPolicy, artifactFormat, hashContract link.  
Definition block **excludes:** human name, repo path, RID, provider availability.

Tamaki emits repository refs with definition + hash-contract CIDs on the execution envelope.  
Kototama **rejects** definition-CID drift / substitution before HostCaps admission.

### Three layers (do not conflate)

| Layer | What | Example |
|---|---|---|
| A. Semantic definition CID | Meaning identity | `bafyrei…` on capability package |
| B. actor:host wire numeric ids | Host import table in `capability_contract.edn` | 201+ |
| C. Compiler/provider kit ids | Application capability kits | 1–23 (scoped-fs=19, process=20, secret=21, git=22, entropy=23) |

App source should use **named operations / semantic abilities**, not raw numbers.

### Adding or changing a capability

1. Edit **kotoba-core-contracts** catalog (imports, effects, host surface).
2. Recompute definition CIDs; run scaffold (`--update` if regenerating).
3. Bump Tamaki + Kototama pins to the new contracts SHA.
4. Fix admit fixtures if CIDs changed (expected: meaning change ⇒ new CID).
5. Do **not** rename a repo and keep the old CID while changing effects.

---

## 4. Provider kit frontier (W6 kbb-related)

| Kit | id | Status |
|---|---|---|
| scoped-fs | 19 | pure resolve-path + mem-store + **os-store** (`:roots` required); cljs/nbb OS transport landed (provider#28 / ADR 0147) |
| process | 20 | pure validate-spawn + echo-transport + **os-spawn** (`:binaries` required); cljs/nbb OS transport landed (provider#28 / ADR 0147) |
| secret | 21 | get-only allowlist + map-fetch / env-fetch (no dump); ops first-cutover (murakumo#48/#50) |
| git | 22 | pure validate-run + echo-transport + **os-run** dual-runtime (ADR 0148–0150; provider#29+#31+#32); murakumo#55 deploy pin |
| entropy | 23 | CSPRNG draw + dual-runtime **os-draw** (provider#33 / ADR 0151); clock-and-random gap closed |

cljs OS transports for process / scoped-fs / git are **done**. SSH is **host-forever** (no `provider.ssh` kit; decision recorded). Residual: remaining ops CLI ambient sites beyond first secret/git cutovers.

---

## 5. Suggested first commands for a new agent

```bash
# Plan + inventories
gh api repos/kotoba-lang/kotoba-lang/contents/docs/kotoba-centered-migration-plan.md --jq .content | base64 -d | tail -n 40
gh api repos/kotoba-lang/kotoba-lang/contents/lang/w6-kbb-ability-gap.edn --jq .content | base64 -d | head -80

# Capability contract pin
gh api repos/kotoba-lang/kotoba-core-contracts/commits/main --jq .sha
# expect 2e615d17… or newer if advanced deliberately

# Example atomic capability package
gh api repos/kotoba-lang/capability-http-fetch/contents/README.md --jq .content | base64 -d | head -30
```

Pick **one** Next item from the plan; finish with tests + inventory/plan update; open minimal PRs; merge only after green.

---

## 6. Related ADRs / docs

| Doc | Role |
|---|---|
| `docs/adr/ADR-capability-repository-semantic-cid-v1.md` | Capability CID + 1-repo policy (this repo) |
| `docs/adr/ADR-w6-migration-inventory-v1.md` | W6 cohort inventory |
| `docs/w6-*.md` + `lang/w6-*.edn` | Path-level inventories |
| compiler `docs/adr/0120`–`0145` (approx.) | W5 deepen trail |
| Superproject `ADR-2607289500` | Workspace-authoritative handoff |

---

## 7. Capability package implementation (2026-07-28)

Contract only → **reference-implemented** (pure allowlist):

| Package | PR | Status |
|---|---|---|
| core-contracts status rules | [#22](https://github.com/kotoba-lang/kotoba-core-contracts/pull/22) | `:reference-implemented` allowlist + sha256/exports rules |
| capability-math-sin | [#1](https://github.com/kotoba-lang/capability-math-sin/pull/1) | wasm core `sin` + JVM `Math/sin` |
| capability-math-cos | [#1](https://github.com/kotoba-lang/capability-math-cos/pull/1) | wasm core `cos` + JVM `Math/cos` |
| capability-hash-sha256 | [#1](https://github.com/kotoba-lang/capability-hash-sha256/pull/1) | wasm core `sha256_hex` + JVM `MessageDigest` |
| capability-clock-monotonic | [#1](https://github.com/kotoba-lang/capability-clock-monotonic/pull/1) | wasm counter + JVM `System/nanoTime` |
| capability-time-now-days | [#1](https://github.com/kotoba-lang/capability-time-now-days/pull/1) | wasm stub + JVM UTC day counter |
| capability-random-bytes | [#1](https://github.com/kotoba-lang/capability-random-bytes/pull/1) | wasm xorshift + JVM `SecureRandom` |
| capability-data-cbor | [#1](https://github.com/kotoba-lang/capability-data-cbor/pull/1) | wasm `cbor_encode` + JVM flat-pair encoder |
| capability-data-json | [#1](https://github.com/kotoba-lang/capability-data-json/pull/1) | wasm `json_encode`/`json_extract_field` + JVM |

**Allowlist pure reference-implemented set is complete** (sin/cos/sha256/cbor/json/clock/random/now-days).  
Network/secret caps stay contract-only until signed production providers exist.

## 8. 2026-07-28 follow-through

| Item | PR |
|---|---|
| QUIC cert path-ref under scoped roots | murakumo#52 |
| kagi-fetch wire | murakumo#52 |
| git kit id 22 (ADR 0148–0150) | provider#29+#31+#32 |
| entropy kit id 23 (ADR 0151) | provider#33 |
| secret name/policy pure oracle | murakumo#60 |
| rebalance + plan + engine + report pure path | murakumo#61–#91 |
| identity JWT + overlay crypto packaging oracle | murakumo#85 |
| product-shell oracle authority (kekkai.gate dual-source) | murakumo#86 |
| live HMAC/AES host adapters | murakumo#87 |
| product-shell oracle authority (token) | murakumo#88 |
| product-shell oracle authority (report pad/header/help) | murakumo#89 |
| product-shell oracle authority (infer.plan) | murakumo#91 |
| product-shell oracle authority (dash.state) | murakumo#93 |
| product-shell oracle authority (infer.schedule) | murakumo#94 |
| product-shell oracle authority (task.plan) | murakumo#95 |
| product-shell oracle authority (infer.engine) | murakumo#96 |
| Product Value ABI v1 token | murakumo#112 |
| product-shell oracle authority (overlay driver + runtime) | murakumo#113 |
| Product Value ABI v1 fleet + provision ports | murakumo#114 |
| Product Value ABI v1 sealed fields + reconcile optionals | murakumo#115 |
| Product Value ABI v1 connect plane + report health | murakumo#116 |
| Product Value ABI v1 task failed? + peer choose-via | murakumo#118 |
| Product Value ABI v1 schedule pick-fold | murakumo#120 |
| Product Value ABI v1 rebalance classify + task pick-fold | murakumo#121 |
| optional cljs/nbb product-shell oracle load | murakumo#122 |
| cljs dual-source dash.state | murakumo#123 |
| cljs dual-source token pure + kekkai.gate | murakumo#124 |
| cljs dual-source tunnel pure | murakumo#125 |
| cljs dual-source secret pure + connect pure | murakumo#126 |
| cljs dual-source config + persist pure | murakumo#127 |
| cljs dual-source identity pure | murakumo#128 |
| cljs dual-source reconcile.plan + component-authority | murakumo#129 |
| cljs dual-source overlay keyring/peer/stream + schedule | murakumo#130 |
| cljs dual-source infer.gc/relay/moe/join | murakumo#131 |
| cljs dual-source residual infer plan/engine/rebalance/credits | murakumo#133 |
| cljs dual-source com-cloudflare product-shell pure | com-cloudflare#17 |
| cljs dual-source com-cloudflare-compat product-shell pure | com-cloudflare-compat#5 |
| cljs dual-source residual JVM clj pure shells (report/provision/cloud/crypto) | murakumo#135 |
| cljs dual-source overlay.runtime/driver + deploy.plan | murakumo#132 |
| product-shell oracle authority (fleet.inventory) | murakumo#102 |
| product-shell oracle authority (infer.join + infer.gc) | murakumo#105 |
| product-shell oracle authority (moe + rebalance + relay) | murakumo#107 |
| product-shell oracle authority (deploy + connect + component-authority) | murakumo#110 |
| product-shell oracle authority (overlay + cloud + provision) | murakumo#111 |
| product-shell oracle authority (secret) | murakumo#98 |
| bulk product-shell KIR catalog (32 cores) | murakumo#99 |
| zones query + hostname-match oracle | com-cloudflare#13 |
| clock/time/random reference-impl | capability-*-#1 |

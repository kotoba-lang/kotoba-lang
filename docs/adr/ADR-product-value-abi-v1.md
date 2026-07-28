# ADR: Product Value ABI v1 (pure-product beauty path)

- Status: accepted
- Date: 2026-07-28

## Context

W6 pure oracles on the product path (`compile-source :wasm32-kotoba-v1` →
precompiled KIR → `kotoba.kir/execute`) often used **sentinel ABIs**
(`has-sub :i64`, `ttl -1`, base-65536 packs) even though the compiler already
admits option / string / record. The worst bug: `if-some` desugared to
`[:option :i64]` only, so `[:option :string]` pure helpers type-errored.

## Decision

### Product Value ABI v1 (host ↔ pure guest)

| Kind | Guest type | Host representation |
|---|---|---|
| integers | `:i64` | `long` |
| bool | `:bool` or i64 0/1 at wire | boolean / 0\|1 |
| strings | `:string` | `String` |
| option | `[:option T]` | `nil` → none; value → some (bridge) or tagged `[[T] false]` / `[[T] true payload]` |
| records | `[:record …]` (bounded) | maps projected by host (incremental) |

### Pure-product surface (must execute on product KIR)

Admitted and proven for product path:

- `if-some` / `when-some` with **typed option locals** (any `[:option T]`)
- `match-option` with explicit type
- `string-length` (= UTF-8 byte length; same unit as `string-substring`)
- `string-from-i64` (signed decimal; desugars to wasm-safe helpers)
- `string-concat` / `string=?` / dynamic `string-substring`
- `string-byte-length` (canonical name; `string-length` is alias)

### Profile file

`lang/pure-product-profile.edn` — writeable ⊆ executable contract for pure oracles.

### Explicit non-goals (v1)

- Unbounded persistent maps / recursive values
- Killing host AES/HMAC/SSH
- Full cljs oracle resource load

## Evidence

- kotoba-kir#19 — interpreter `string-length` / `string-from-i64`
- compiler#410 — typed if-some + string surface + tests
- Follow-up: murakumo token_core without has-* sentinels

## Consequences

Pure oracles should prefer option + string-from-i64 over sentinels and digit
recursion. Host bridges centralize nil↔option coercion (`murakumo.kotoba.oracle`).

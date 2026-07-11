# ADR — 型、region、構造化並行性を safe Kotoba の次段階にする

Status: **Accepted — M3 compiler admission adapter implemented; published dependency pin pending**  
Date: 2026-07-11

## Decision

safe Kotoba の次の三つの拡張は、単一の型・effect 契約として導入する。

1. 公開関数は `{:params [...] :returns T :effects #{...}}` の署名を持てる。
2. capability は `[:cap kind resource]` として型に現れ、必要 effect は
   capability kind から導出する。resource は authority ではなく scope である。
3. region と task は lexical type とする。region reference は外へ返せず、
   child task の effect は親 scope の effect row に含まれなければならない。

これは「全値に Rust の所有権を適用する」設計ではない。通常の不変値は自由に共有し、
capability と region reference だけに制約をかける。

## First slice

`kotoba.lang.type-system` が CLJC で type expression、公開 signature、scope
obligation を fail-closed に検査する。M1 では `defn` name metadata の `:signature`
を正規化し、引数数・capability parameter metadata・effect row の一致も検査する。
M2 では lang/type-conformance/ fixture suite を追加し、独立 host も同じ accept /
reject 結果を再現できるようにする。M3 では kotoba-lang/kotoba の
kotoba.runtime/check がこの validator を遅延解決して admission gate へ接続する。
公開git pinが M2 より古い間は、:signature を持つ source を
:type-contract-unavailable として fail-closed に拒否する。Wasm lowering はまだ追加しない。特に child task への capability capture は拒否する。move を実装せずに
implicit share を許すことは ambient authority の再導入になるからである。

## Planned admission order

```text
signature/type validation
  → typed HIR (primitive + Result)
  → with-region / freeze non-escape checking
  → scope / spawn / join lowering
  → explicit affine capability move
  → Wasm host quota and cancellation wiring
```

`spawn` は detached future ではない。親 `scope` が子を join 又は cancel してから
戻る structured concurrency とし、capability は明示 `move` でのみ一子へ移送する。

## Consequences

- effect の宣言が capability と task の両方を説明できる。
- region-local mutation を将来導入しても永続 Map・global Var・serialization への参照漏洩を
  型で拒否できる。
- 初期 slice は実行時機能を偽装しない。現行 compiler が `with-region` / `scope` / `spawn`
  を実装するまで、それらは safe Kotoba source surface に追加しない。

## Relation to the capability ADR

[ADR-safe-capability-language.md](ADR-safe-capability-language.md) の S1b
(typed HIR)、S2 (capability-only affine)、S3 (effect)、S4b (capability values)
を置き換えず、その上の次の実装順を固定する。現行の capability/effect gate は引き続き
全 host call で必要であり、型情報だけを authorization と見なしてはならない。

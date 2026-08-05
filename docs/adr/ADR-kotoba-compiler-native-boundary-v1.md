# ADR — compiler の native/IO 依存境界と capability リポジトリ分離

- **Status**: Accepted
- **Date**: 2026-08-05
- **Scope**: `compiler`, `kotoba-lang`, `kotoba-kir`, `kotoba-native`, actor / io / provider 系リポジトリ
- **Related**: `ADR-safe-capability-language.md`, `ADR-portable-effect-host-profile.md`,
  `ADR-kotoba-semantic-bedrock-and-dependency-direction.md`

## Context

cljc → kotoba 言語への移行は進行中で、grammar / async 能力 / capability
契約などの主要部分は既に kotoba 言語側に寄せられた。一方で compiler が
ネイティブや provider 群の責務（ハードウェア別最適化、I/O/actor 連携、
host API へのマッピング）も大量に抱え続ける構造は、責務逆転と変更時の
リスク集中を生んでいる。

同時に `surface-status` の残件は
`:linear-resource-result-families` 1件のみで、言語能力・非決定性を中心に
ここまでの大きな移行はほぼ完了している。

## Decision

`compiler` を「言語の意味論を受ける入口」として固定し、native/provider を
直接所有しない設計を採用する。

1. `compiler` の責務を明確化する
   - 入力: kotoba AST / 既存 cljc/旧 surface からの変換
   - 守るもの: type/effect/capability admission、KIR 構築、実行可能な意味論情報
     （authority やプロバイダ権限の最終判定ではない）
   - しないもの: ハードウェア ISA/OS/API 実装の詳細、provider 発行の最適化、環境依存のライフタイム制御
   - 役割: 生成物の `target-neutral` 化、backend への明示的契約渡し

2. capability を単位とした repo 分離を採用する
   - actor / io / db / net などは能力ファミリ毎に provider 系リポジトリに分離。
   - 共通能力記述と実行契約は `kotoba-core-contracts` / `kotoba-core-contracts` 系
     コントラクト資産を通して参照。
   - 主要実装 repo 側は descriptor とテストケースを保持し、compiler は descriptor を
     解釈するだけに留める。

3. native を段階分離する
   - `kotoba-native` を「native ABI と共通 codegen 契約のレイヤ」にする。
   - ここで ABI の安定形と最小ホスト API を定義し、OS/driver/provider は
     `tender-native` 系や provider repo 側に寄せる。
   - `x86_64` / `aarch64` / その他の ISA レポジトリは、まず `kotoba-native` 契約で
     互換性が固定された後に、追加で plugin 化して分離する。
   - 当面、先に ISA 別 repo を乱立せず、同一 ABI 契約からの派生として扱う。

4. 一時運用ルール
   - 共通ライブラリは「先に kotoba-lang org 側で dependency 完結」する方向を優先。
   - migration でまだ不足する箇所は `kotoba-lang` 側に `:x` エントリとして残し、
     provider 実装が揃い次第 repo 側へ移送する。
   - 逆に OS/IO 特化ロジックは新規に compiler に追加しない。

## Consequences

- `cherry-pick` 可能な未マージ差分が増えても、compiler 本体に混入した場合の
  影響範囲が縮小する。
- 能力別の実装変更が `kotoba-native` と provider repo で独立し、テスト/監査
  の回しやすさが上がる。
- アーキテクチャ上は明確に、`compiler` → `KIR/semantics`、
  `kotoba-native` → `native-contract`、provider repo → `implementation` の
  依存方向になる。
- ISA 分離は契約が固まるまで延期するため、短期は複雑さが増えにくい。

## Non-goals

- 本 ADR では「ハードウェアごとの最適化パス」自体の設計を確定しない。
- 本 ADR は既存の `:linear-resource-result-families` の残件を解決しない。
  これは引き続き別トラックで扱う。

## Migration Next Steps

1. `compiler` の native 固定責務（target qualification / host adapter wiring）
   を `kotoba-native` へ移行し、最小契約差分のみを残す。
2. actor / io / storage などの共通 capability descriptor を `kotoba-core-contracts`
   参照に寄せ、provider 実装差分を各 repo の実装として分離。
3. ISA 導入は `kotoba-native` 契約安定後に行う。短期は共通化可能な
   backend 抽象を優先する。

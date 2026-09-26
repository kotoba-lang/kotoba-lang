# kotoba-pure-s (純S式コア & cljk 二層構文アーキテクチャ bot)

Roles & Charter:
- `.kotoba`: Canonical Pure S-expression Core の確立と純化
- `.cljk`: Clojure-shaped Friendly Surface (豊富な desugaring 糖衣) の維持と健全性管理
- 両者が同一の typed KIR / Semantic DAG / Definition CID へ収束する parity の機械検証

## 1. 二層構文の責任境界 (ADR-kotoba-pure-s-expression-core-and-cljk-surface.md 準拠)

1. **.cljk (Clojure-shaped Surface)**:
   - 既存 GitHub コーパス（8.9万 repo）の pretraining prior を最大活用する親しみやすい構文。
   - 既存の豊富な糖衣（`defn`, ベクタ分配束縛, スレッディング `->`/`->>`, `match`, `case`, `cond`, `condp`, `when-let`, `if-let`, `defdesugar` 等）を温存。
   - `.cljc` からのゼロリファクタ移行を支援する。

2. **.kotoba (Pure S-expression Core)**:
   - AI-first コンパイラ・検証ループのための極小直交純S式。
   - 方言固有の構文ノイズや不要な糖衣を削ぎ落とし、直交的な核（約20 forms: `lam`, `app`, `rel`, `query`, `perform`, `handle`, `ref`, `let`, `if`, `do` 等）へ純化。
   - エラー空間（unknown form, arity mismatch, type/effect mismatch 等）を狭小化し、Autonomous Agent の自律修復（generate → parse → check → repair）で 9.5/10 の修復安定性を担保する。

3. **共通基盤 (DefCID / KIR)**:
   - `.cljk` の脱糖後と `.kotoba` の純S式は、同一の意味論であれば同一の Definition CID を生み出さなければならない。

## 2. 作業原則

1. **境界侵犯を許さない**:
   - `.kotoba` に方言固有の糖衣（マクロ的 sugar、Clojure 固有の不規則構文）を増やさない。
   - `.cljk` から開発者の利便性を損なう形で過剰に糖衣を削らない。
2. **DefCID の同一性を機械で証明する**:
   - 同等な処理を行う `.cljk` と `.kotoba` を作成し、生成される KIR と CID が byte-identical / DAG 等価であることをテストで証明する。
3. **推測ではなく測定**:
   - 言語機能の追加や修正は、必ず `kotoba check`, `amu check --jvm-free`, KIR parity, およびベンチマーク（pass@1, generated tokens, repair iterations）で測定する。

## 3. 定期検証ルーチン

- `orgs/kotoba-lang/kotoba-lang` の `docs/adr/` と `lang/guest-grammar.edn`, `lang/q9-migration.edn` の整合性を点検。
- `kotoba-sema` および `amu` における `.kotoba` (純S式) と `.cljk` (脱糖) のパース・型検査パスの健全性を確認。
- 異常・不整合・CID ドリフトを発見した場合は最小限の repro を添えて issue/PR を起票する。

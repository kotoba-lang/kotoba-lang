# Kotoba はソフトウェア開発の課題をどれだけ解決できているか — アルゴリズム・数理モデル別の主要 11 言語比較と対応表

[English](language-problem-matrix.md) | **日本語**

- **確認日**: 2026-11-27
- **参照リビジョン（`kotoba-lang/kotoba-lang`）**: `origin/main` [`e8fcd84a537959f3df772eba70ad4569143278c8`](https://github.com/kotoba-lang/kotoba-lang/tree/e8fcd84a537959f3df772eba70ad4569143278c8)
- **参照リビジョン（`kotoba-lang/amu`）**: `HEAD` [`87ec570aff47a0a21b4876fcda4fa9d4e3b934fb`](https://github.com/kotoba-lang/amu/tree/87ec570aff47a0a21b4876fcda4fa9d4e3b934fb)（Lean 4 T2 定数加算畳込み証明ブランチ `com-junkawasaki/kotoba-963-sir-lean`: `96898e7a`）
- **参照リビジョン（`kotoba-lang/kotoba`）**: `origin/main` [`bab28344825c8a3f179f3d6217572eb778edf4e6`](https://github.com/kotoba-lang/kotoba/tree/bab28344825c8a3f179f3d6217572eb778edf4e6)
- **正直な範囲宣言**: この文書は、各リポジトリの仕様書・実装・既存の検証証拠に基づく設計評価である。新しい runtime 試験・全ターゲットの実機検証・一般停止性証明・汎用プログラムの同値性保証・リリースや性能の保証を追加するものではない。また、現行の契約とコードで**「実装済み」「部分実装」「設計済み・未実装」「意図的非採用」**を明確に分けて記述する。日付つきの解説よりも、各所有リポジトリの現在の契約を優先する。

---

## 1. 総合評価：Lisp / S-form + Unison-like でソフトウェア開発の課題をどれぐらい解決できているか

Kotoba は **「Lisp / S-form（コード即データ＋境界付き糖衣展開）」「閉じた静的型・代数的データ型」「Capability / Effect Row による権限境界」「Unison 着想の内容アドレス定義（DefCID）＋能力（Ability）」** を 1 本のパイプラインに統合している。その結果、**安全性・再現性・権限制御**に関する課題は非常に深く解決している一方、**TypeScript 的な柔軟多相・常駐 IDE/LSP 体験・セルフホストとリファレンス仕様の完全一致**には明確な未到達・部分到達が残っている。

### 1.1 強く解決できている領域（設計＋実装あり）

**1. 不正状態の排除（Invalid states unrepresentable）**

- 欠損はセンチネル値（`-1` や暗黙 `null`）ではなく `[:option T]` として表現し、暗黙の数値真理値化（number truthiness）や暗黙の数値型変換（`i64` ↔ `f64` ↔ `f32`）をすべて型エラーで拒否する。
- 失敗は隠れた例外ではなく `[:result T E]`、さらに `throw`/`try`/`catch` もコンパイル時に `[:result T E]` へ展開され、関数境界の型と `:abort` エフェクト行に記録される（`export` 境界での未捕捉 `abort` は拒否）。
- `[:variant id cases]` と `defrecord`（最大 32 フィールドの公称レコード）および網羅的な `option-match` / `result-match-of` / `variant-match` により、閉じた代数的データ型（ADT）で状態空間を閉じられる。
- キーワードマップリテラルの `nil` フィールドは文脈・モジュール内使用型から `[:option T]` に解決される（`absence-resolution` / `ck-kopt-scan`）。

**2. 副作用と権限漏洩の封じ込め（Capability-safe execution）**

- 閉じた呼び出しグラフ上の不動点計算により、関数の Effect Row を静的推論し、`:capabilities` 宣言・`{:effects #{...}}` 上限・実行ポリシーと交差させる（deny-by-default）。
- 実行時は **コンパイラの証拠 ∩ 要求操作 ∩ 委譲スコープ ∩ ローカル方針 ∩ provider の現在状態** の 5 層交差を通過した場合のみ実行される（[`logic-authority.md`](./logic-authority.md)）。
- Capability 値はアフィン消費検査（`kotoba-sema` の `affine.cljk`）を受け、複製による無制限な権限増殖や、`def` / EDN リテラルからの暗黙的な権限生成（ambient authority）を防ぐ。

**3. Unison 着想の定義同一性（Content-addressed code identity）**

- `DefCID`（payload-v2）はソース文字列や Git コミットではなく、**検査済み Typed KIR＋IF＋Effect Row＋依存 DefCID** を DAG-CBOR / SHA-256 でハッシュ化する（[`ADR-kotoba-code-identity-and-abilities.md`](../adr/ADR-kotoba-code-identity-and-abilities.md)）。
- α-リネーム（ローカル変数名変更）に対して不変であり、さらにレコード Row 多相の単相化クローンも具象型と本体のみで決まるため、異なるモジュールや手書き版と同一 `DefCID`・コンパイルキャッシュを共有できる（`ADR 0353`）。

**4. 同図像性・決定性・有界計算**

- 読込構文を閉じた EDN / S-form サブセットに制限し、左→右・値呼び（call-by-value）で評価する（[`semantics-ssot.md`](./semantics-ssot.md)）。
- 構文拡張は任意ゲストマクロを禁止し、純粋・非捕捉・深さ/ノード数上限付きの `defdesugar` に限定するため、展開結果が静的に検査可能（[`surface-matrix.md`](./surface-matrix.md)）。
- バックエンド別の Fuel 課金モデルにより資源境界を明示し、プロファイル差を [`fuel-model.md`](./fuel-model.md)（§2）と [`lang/limits.edn`](../../lang/limits.edn) で分離する。KIR・Wasm（`wasm32-kotoba-v1`）・JS（`js-kotoba-v1`）の課金単位は関数入口 1 単位（既定 512）であり、POSIX native `kexe`（AArch64 / x86-64）では決定論的 fuel 計測は既定 OFF で、opt-in（`KEXE_FUEL=n` / `--fuel n`）時に関数入口＋コンストラクタ割当 1 単位/生成を課金する（`ADR 0353` `:allocation-budget-native` 以降の 2026-10-09 実測）。

### 1.2 部分到達・ギャップが残る領域（特に TypeScript 的型・ユニオン型・LSP）

**1. TypeScript 的な型表現・型推論（Algorithm W 非採用）**

- **汎用型変数・Algorithm W（Hindley–Milner）は持たない**（[`lang/surface-status.edn`](../../lang/surface-status.edn) の `:element-type-polymorphism` は `:not-yet-implemented`）。
- その代わり **「チェッカ拒否駆動の不動点精密化（`infer-absent-parameter-types` / `KW-PINFER`）」** と **「レコード専用 Row 多相＋コンパイル時単相化（`{:k T | r}`）」**、hosted リンカでの **「モジュール型パラメータ置換（`:params` / `:with`）」** を持つ。
- **ユニオン型と狭小化**: タグ付き直和型 `[:variant ...]` / `[:option T]` / `[:result T E]` は実装済みで、Hiccup ADT 上では `string?` / `vector?` 等による Control-Flow Narrowing が `kotoba-sema` で動作する。しかし、TypeScript のような**任意の無タグユニオン `A | B`** や **任意型に対する Control-Flow Narrowing**、**`any`/`unknown` との段階的型付け**は存在しない（安全性の観点からタグ必須の閉じた直和型のみを許可）。

**2. LSP・IDE 開発体験**

- `kotoba.lsp`（診断・位置・範囲データ層）と `kotoba.lsp.rpc`（JSON-RPC 2.0 フレーミング）は M6 水平標準ライブラリとして存在する（[`ADR-kotoba-lang-foundational-stdlib.md`](../adr/ADR-kotoba-lang-foundational-stdlib.md)）。
- しかし、TypeScript（`tsserver`）や Rust（`rust-analyzer`）のような **常駐・インクリメンタル・オンデマンド再計算（Salsa 型クエリエンジン）を備えた統合 LSP サーバ**（ライブ補完・ホバー型推論表示・定義ジャンプ・型指向リファクタ）は未完成である。

**3. 仕様（`kotoba-sema`）とセルフホスト（`amu/seed`）の実装ギャップ**

- 2026-11-07 計測時点で conformance 正例 86 本のうち、セルフホスト版が受理できるのは `aarch64-macos` で 48/86、`wasm32-browser` / `js-browser` で各 29/86 に留まる（負例 26 本は全ターゲットで 26/26 拒否）。
- モジュール型パラメータ（`E6023`）、レコード/キーワード/関数型のクロスモジュール IF（`E6017`）、`defdesugar`、複数ソース `map`、`lazy-*`、`extend-protocol default` 等がセルフホスト経路では未到達。

**4. Unison 的エコシステム運用と形式検証**

- `definition-patch` / `share` のローカル交換仕様（[`ADR-kotoba-definition-patch-share.md`](../adr/ADR-kotoba-definition-patch-share.md)）はあるが、ネットワーク同期・パッチマージ・セルフホストでの `(ref "bafy...")` CID 解決は未実装。
- Lean 4 によるコンパイラ変換の意味保存証明は `KOTOBA-963`（SIR `i64` 定数加算畳込み T2 の最小スライス、`kotoba-lang/amu` ブランチ `com-junkawasaki/kotoba-963-sir-lean` `96898e7a`）が着手された段階。

### 1.3 意図的に非採用とした領域（欠陥ではなく設計制約）

- **全域停止証明**: ライスの定理の範囲を明示し（[`computability-and-safety.ja.md`](./computability-and-safety.ja.md)）、任意のプログラムに対する停止証明は要求しない（停止は有限予算・Fuel・ホストの強制停止に委ねる）。
- **Rust 流の汎用ボローチェッカ**: 全値に対するライフタイム/借用検査は持たず、アフィン検査は Capability ハンドルと `vector-assoc!` 等に限定し、通常値は不変永続データで共有する。
- **アンビエント動的実行**: 実行時文字列 `eval`、動的コード読込、アンビエント `require`、任意 JVM/JS FFI、共有メモリマルチスレッド/STM はセキュリティ不変条件（`intentional-security-constraint`）として永久排除している。

---

## 2. アルゴリズム・数理モデル別 × 課題 × 主要言語の解法 × Kotoba の対応表（14 領域）

| # | 課題ドメイン | 基礎アルゴリズム・数理モデル | 主要言語の解法（C / C++ / Java / TS / Go / Python / Ruby / Rust / Unison / Scala / OCaml 等） | Kotoba の解法と現在地 | 状態・根拠 |
|---|---|---|---|---|---|
| 1 | **メモリ安全性・寿命管理・ゼロコスト抽象化** | 線形型 / アフィン型（Substructural Logic）、RAII、リージョン推論、Tracing GC | **C**: 手動管理（UB 温床）。**C++**: RAII・スマートポインタ。**Rust**: 所有権・ライフタイム・ボローチェッカ（コンパイル時静的解放）。**Java/Go/Python/Ruby/TS/OCaml/Scala/Unison**: Tracing GC（実行時回収）。 | 一般値には汎用ボローチェッカを持たず（意図的制約）、**不変永続データ＋単一ワードハンドル＋固定容量/Arena 割当**で安全性を確保。アフィン検査（`kotoba-sema` の `affine.cljk`）は **Capability 値**と in-place `vector-assoc!` のみに限定。`typed-list-conj` のコピー負荷や大規模データ向け償却ビルダーは課題として残存。 | **実装済み（能力アフィン＋Arena） / 部分実装（大容量償却） / 意図的非採用（汎用ボローチェッカ）** — `affine.cljk`, [`surface-matrix.md`](./surface-matrix.md), `selfhost-native-gaps-20261001.md` |
| 2 | **不正状態の排除（Null 安全・直和型・網羅性・狭小化）** | 代数的データ型（ADT: 直和＋直積）、網羅性検査（Exhaustiveness Checking）、Occurrence Typing / Control-Flow Narrowing | **C/C++/Java(旧)**: Null ポインタ・未検査共用体。**TS**: `strictNullChecks`、判別共用体、Control-Flow Narrowing、無タグユニオン `A \| B`。**OCaml/Scala/Rust**: `Option`/`Result`、パターンマッチ網羅性検査（Scala 3 Union `A \| B` あり）。**Go/Python/Ruby**: 多値 `err` / `Optional`（静的網羅強制は弱い）。 | `nil` を値として許さず、マップ内 `nil` は `[:option T]` に解決。`[:option T]`・`[:result T E]`・閉じた `[:variant id cases]`・公称 `defrecord`（最大 32 フィールド）と `option-match`/`result-match-of`/`variant-match` により不正状態を排除。数値 truthiness・暗黙型変換は全廃。**差分**: TS の**任意無タグユニオン `A \| B`** は非採用。Control-Flow Narrowing は Hiccup ADT（`kotoba-sema`）のみで任意型には未拡張。 | **実装済み（Option/Result/Variant/Record） / 部分実装（Hiccup 狭小化） / 意図的非採用（無タグユニオン）** — [`semantics-ssot.md`](./semantics-ssot.md), [`lang/surface-status.edn`](../../lang/surface-status.edn), `ADR 0353` |
| 3 | **型推論・多相性** | Hindley–Milner (Algorithm W)、双方向型検査（Bidirectional Typing）、Row 多相、構造的部分型、単相化（Monomorphization） | **OCaml**: Algorithm W + Row 多相（多相バリアント/オブジェクト）+ let 多相。**Unison**: 双方向型検査 + Ability 推論。**Scala**: 局所型推論 + 高階型 + 暗黙/Given。**Rust**: 局所 HM 風推論 + Trait 単相化。**TS**: 双方向推論 + 構造的部分型（実行時消去）。**Go**: ジェネリクス単相化/GC-shape。**Python/Ruby**: 動的型＋漸進的型付け（実行後発覚あり）。 | **Algorithm W（汎用型変数・let 多相）は非採用**。(1) **拒否駆動の反復不動点精密化**: 無注釈引数を仮 `:i64` としチェッカの型エラーから要求型を読み取り不動点収束（矛盾利用は `parameter-use-conflict!` で拒否）。(2) **レコード専用 Row 多相＋単相化**: `{:k T \| r}` の `assoc`/`dissoc`/`merge`/`select-keys` を検出し呼出ごとに具体レコードへクローン単相化。(3) **モジュール型パラメータ**: hosted リンカが `:params`/`:with` をテキスト置換（`amu/seed` は `E6023` 未対応）。**ギャップ**: `:element-type-polymorphism` は `:not-yet-implemented`。 | **実装済み（拒否駆動推論＋Row 単相化） / 部分実装（`:params`） / 設計済み・未実装（汎用要素多相） / 意図的非採用（Algorithm W）** — `infer.cljk`, `row.cljk`, `amu` `seed/21-check.kotoba`, [`lang/surface-status.edn`](../../lang/surface-status.edn), `ADR 0353` |
| 4 | **エラー処理・非局所制御・副作用の制御** | 代数的エフェクトとハンドラ（Algebraic Effects）、Result/Either モナド、検査例外、閉じた呼び出しグラフ不動点解析 | **C/Go**: 戻り値エラー（無視可能）。**C++/Java/Python/Ruby/TS**: 例外（Java のみ検査例外だが高階関数と相性難）。**Rust**: `Result<T,E>` + `?`（エフェクト体系なし）。**OCaml 5**: エフェクトハンドラ（静的エフェクト行検査は未標準）。**Unison**: Abilities（型＋エフェクト検査＋ハンドラ）。 | 暗黙例外を禁止し、**Typed Abort Ability** として `throw`/`try`/`catch` を `[:result T E]` と `:abort` エフェクトに自動展開（関数ごとに単一エラー型 `E` を不動点推論、混在や未捕捉 `export` を拒否）。状態操作は one-shot 末尾再開型の `:state` ハンドラ（`defhandler`/`handle`/`resume`）と非エスケープ局所 `atom` の `let` 展開で扱う。一般の自由な継続捕捉・汎用ハンドラは意図的に持たず、外部作用は Capability へ帰着させる。 | **実装済み（Abort 展開・`:state` ハンドラ・不動点 Effect Row） / 意図的非採用（自由継続・暗黙例外）** — `effect_inference.cljk`, `infer_passes.cljk`, [`surface-matrix.md`](./surface-matrix.md), `amu` `seed/HEADS` |
| 5 | **セキュリティ・権限境界・サプライチェーン防御** | Object-Capability (OCap) モデル、権限減衰（Attenuation）、交差認可、Merkle DAG 署名ロック | **Java**: SecurityManager 廃止方向。**Rust/C/C++/Go/Python/Ruby/TS**: OS プロセス単位権限（ライブラリが環境変数・FS・Net をアンビエントに呼べる）。Deno はプロセス起動時フラグ権限。**Unison**: Capability 的 Ability 設計はあるがハードウェア/Wasm 境界強制とは別軸。 | **5 層交差の Capability 境界**: コンパイラ解析（閉じた呼び出しグラフ Effect Row）∩ 要求 ∩ 委譲チェーン ∩ ローカル方針 ∩ provider 現在状態。アンビエント I/O・環境変数・`require` を言語から排除し、名前や CID は権限を生まない（`DefCID ≠ Authority`）。パッケージは `repo RID + 署名 + DefCID/SourceCID lock` で検証。**残課題**: 署名・ネイティブポリシー（`E2170` 等）の全経路完全統合。 | **実装済み（言語/VM 境界） / 部分実装（ネイティブ拡張ポリシー）** — [`logic-authority.md`](./logic-authority.md), [`ADR-kotoba-package-cid-lock.md`](../adr/ADR-kotoba-package-cid-lock.md), `amu` `seed/21-check.kotoba` |
| 6 | **コード同一性・依存解決・リファクタ・ビルドキャッシュ** | 内容アドレス計算（Content-Addressed Code）、α-同値正規化、Merkle-DAG (DAG-CBOR / IPLD) | **ほぼ全言語 (C..Rust/TS/Scala/OCaml)**: テキストファイル＋名前＋Git SHA＋Semver（リネームで差分・ダイヤモンド依存地獄・再コンパイル）。**Unison**: AST ハッシュによる内容アドレス関数＋Codebase DB＋リネーム独立キャッシュ。 | **Unison 着想の `DefCID` (payload-v2)**: 検査済み Typed KIR＋IF＋Effect Row＋依存 DefCID の DAG-CBOR SHA-256。α-リネーム独立・`defrecord`/Row 単相化共有を実現。ソース自体は `.kotoba` / Git に残し、差分交換用に `definition-patch` / `share`（`:add`/`:replace`/`:remove`）を持つ。**ギャップ**: ネットワーク分散同期・並行パッチマージ・セルフホスト `(ref "bafy...")` 直接参照は未実装。 | **実装済み（DefCID payload-v2） / 部分実装（patch/share 同期・CID ref）** — [`ADR-kotoba-code-identity-and-abilities.md`](../adr/ADR-kotoba-code-identity-and-abilities.md), [`ADR-kotoba-definition-patch-share.md`](../adr/ADR-kotoba-definition-patch-share.md), `ADR 0411` |
| 7 | **モジュール性・多態ディスパッチ（Expression Problem）** | 型クラス（Type Classes）、Trait、Protocol、ML Functor、閉世界静的ディスパッチ | **Java/C++**: クラス継承・仮想関数表。**OCaml**: 構造的部分型＋ML Functor＋First-class Module。**Scala**: 型クラス（Given/Using）＋Trait。**Rust**: Trait（静的単相化 & `dyn`）。**Go**: 暗黙 Interface。**Unison**: Ability＋構造/レコード。 | `defprotocol` / `extend-type` / `extend-protocol` / `defmulti` / `defmethod` / `definterface` を提供。すべて **閉じたモジュール内での静的ディスパッチ**（型ごとの関数クローン・静的ディスパッチャ生成）へ展開し、実行時リフレクションやオープンな他モジュール拡張を禁止。**差分**: `amu/seed` の `20-names` は `extend-protocol` の `default` 節（`E2006`）や `definterface`（`E2003`）をまだ拒否する。 | **実装済み（`kotoba-sema` 閉世界静的ディスパッチ） / 部分実装（`amu/seed` 制約）** — [`lang/surface-status.edn`](../../lang/surface-status.edn), [`record-cookbook.md`](./record-cookbook.md), `amu` `seed/HEADS` |
| 8 | **メタプログラミング・DSL・構文表現力** | 同図像性（Homoiconicity）、衛生的マクロ（Hygienic Macros）、有界項書換え（Bounded Term Rewriting） | **C/C++**: テキストマクロ（非衛生）/ template。**Rust**: 宣言的/手続き的マクロ（コンパイル時任意 Rust コード実行可能で監査負荷）。**Scala 3**: `inline`＋Quotes/Splices（衛生マクロ）。**Python/Ruby**: 実行時メタプログラミング（静的検査困難）。**OCaml**: PPX。 | 同図像性 S 式（EDN サブセット）によるデータ＝コード表現を維持しつつ、**任意ゲストマクロ（コンパイル時任意計算実行）はセキュリティ制約として禁止**。代わりに **有界・純粋・衛生的な `defdesugar`**（登録済みテンプレート・非捕捉・ノード数/深さ上限あり）を規定し、展開後の正規形にのみ `DefCID` を付与する。**差分**: `defdesugar` は `kotoba-sema` 実装済み、`amu/seed` 未搭載。 | **実装済み（`kotoba-sema` `defdesugar`） / 設計済み・未実装（`amu/seed` 搭載） / 意図的非採用（任意マクロ）** — [`surface-matrix.md`](./surface-matrix.md), `namespace_defs.cljk` |
| 9 | **並行性・状態管理・データ競合防止** | 不変永続データ構造、CSP（Communicating Sequential Processes）、Actor / Syndicate Dataspace、STM、Affine 排他制御 | **C/C++/Java**: 共有メモリ＋ロック（データ競合リスク）。**Go**: Goroutine＋CSP チャネル（共有メモリも可で競合検出器併用）。**Rust**: `Send`/`Sync`＋所有権によるデータ競合の静的排除。**Scala/Unison**: Akka/ZIO/Unison 構造化並行。**OCaml 5**: Domain＋Effects。 | ゲストコード内の**アンビエント可変セル・スレッド・STM・任意並行プリミティブを意図的に排除**し、不変永続データと非エスケープ局所 `atom` の `let` 再束縛化でデータ競合を根絶。協調は能力ゲート付きの **Syndicate 型 Dataspace**（`assert!`/`retract!`/`observe!` と `facet-enter!`/`leave!`）および水平標準ライブラリ `async`（有界 CSP チャネル）でモデル化。ネイティブ並行スケジューラの構造化 `spawn` 仕様は未決定。 | **実装済み（不変値・局所 `atom`・CSP ライブラリ・Dataspace 展開） / 設計済み・未実装（ネイティブ構造化並行） / 意図的非採用（共有メモリ・STM）** — [`docs/reference/language.md`](../reference/language.md), [`surface-matrix.md`](./surface-matrix.md), [`ADR-kotoba-lang-foundational-stdlib.md`](../adr/ADR-kotoba-lang-foundational-stdlib.md) |
| 10 | **実行資源境界・停止性・数値決定論** | 有界計算（Fuel / Step Budgeting）、ライスの定理の適用境界、IEEE-754 厳密丸め・非 FMA 縮約 | **C/C++**: `-ffast-math` 等で非決定論・UB あり。**Rust/Go/Java/TS/Python/OCaml/Scala**: 一般停止性・命令数 Fuel は言語仕様外（Wasm エンジン側で別途計測する場合あり）。 | **バックエンド別の Fuel 課金**（全バックエンド共通規則ではない）：KIR（`kotoba.kir`）・Wasm（`wasm32-kotoba-v1`）・JS（`js-kotoba-v1`）の課金単位は**関数入口 1 単位**（既定 512、式単位課金なし、記録された KIR/Wasm 経路では自己末尾ループ再入は無課金；[`fuel-model.md`](./fuel-model.md) §2）。POSIX native `kexe`（AArch64 / x86-64）は決定論的 fuel 計測が**既定 OFF**（`:metered false`）で、opt-in（`KEXE_FUEL=n` / `--fuel n`）時に**関数入口＋コンストラクタ割当 1 単位/生成**を課金し、超過で決定論的トラップする（`ADR 0353` `:allocation-budget-native` 以降の 2026-10-09、`amu` `scripts/test-fuel-differential.cljk` による両 ISA 実測；[`lang/limits.edn`](../../lang/limits.edn) `:profile/native`）。一般停止証明はライスの定理により不可と明言。**数値決定論**: 整数ラップ mod `2^64`、シフト mod 64、`MIN quot -1` トラップ、浮動小数点の丸め固定・FMA 縮約禁止・`fast-math` 禁止を規定。 | **実装済み（バックエンド別 Fuel 課金：KIR/Wasm/JS の関数入口課金・opt-in native AArch64/x86-64 の入口＋割当課金、厳密数値規則） / 部分実装（`semantics-ssot` への整数規則転記・native 既定 OFF） / 意図的非採用（全域停止証明）** — [`computability-and-safety.ja.md`](./computability-and-safety.ja.md), [`fuel-model.md`](./fuel-model.md), [`lang/limits.edn`](../../lang/limits.edn), `ADR 0353`, `amu` `seed/HEADS` |
| 11 | **開発者体験（DX）・LSP・インクリメンタル解析・診断** | Demand-driven Incremental Computation (Salsa / Query Graph)、Language Server Protocol (LSP) | **TS (`tsserver`) / Rust (`rust-analyzer`) / Go (`gopls`) / Scala (`Metals`) / OCaml (`ocamllsp`) / Unison (`ucm`)**: 増分 AST/型 DB により編集即座に補完・ホバー型・定義ジャンプ・自動リファクタを提供。 | M6 水平標準ライブラリとして **`kotoba.lsp`**（Diagnostic/Position/Range データ層）と **`kotoba.lsp.rpc`**（JSON-RPC フレーミング）を整備済み。コンパイラ診断は位相タグ＋構造化エラーコード（`E1xxx`〜`E6xxx` / `:kotoba.error/*`）を出力。**明確な不足**: 型推論器・`DefCID` 索引と直結した**常駐インクリメンタル LSP サーバ**（IDE 補完・ホバー・ジャンプ）はまだ未構築。 | **部分実装（データ層＋RPC＋構造化診断） / 設計済み・未実装（統合 LSP サーバ）** — [`ADR-kotoba-lang-foundational-stdlib.md`](../adr/ADR-kotoba-lang-foundational-stdlib.md), `kotoba.lsp`, [`docs/generated/diagnostics.md`](../generated/diagnostics.md) |
| 12 | **ポータビリティ・ネイティブ性能・コンパイル速度** | スタック IR / SSA、線形走査レジスタ割当（Linear Scan）、Wasm Component Model (WIT)、自己ホスト不動点ビルド | **C/C++/Rust**: LLVM 等で最高速ネイティブ生成（コンパイルは重い）。**Go**: 高速ネイティブビルド。**Java/TS/Python/Ruby**: VM/JIT。**OCaml**: 高速ネイティブコンパイル。**Unison**: 独自ランタイム/JIT。 | **Amu セルフホスト・コンパイラ**（`.kotoba` 製、`seed/SIR` → AArch64 / x86-64 / Wasm32 / ESM）：C ローダ・JVM・Node に依存せず 3 世代固定点ビルドを達成。2026-10-15 の Apple M4 観測では、1 コンパイラスナップショット・各 1 サンプル・移植 19 負荷（監査済みアルゴリズム置換を含む）において **0.80× C** 実行時間を記録（リリース済みツールチェイン結果・認定スコア・普遍的速度順位ではない；[監査記事](https://kotoba-lang.org/blog/embench-c-parity-20261015/)）。ターゲット網羅率は 2026-11-06 時点で Linux/macOS 中心に下界 2.15% / 上界 8.09%（Android/Windows/iOS 等の 99% L1 実機証跡は未完）。 | **実装済み（Linux/macOS ネイティブ・Wasm・固定点） / 部分実装（99% ターゲット実機証跡・Linux バイナリ拡張）** — `ADR 0410`, `amu` `seed/README.md`, [`AGENTS.md`](../../AGENTS.md) |
| 13 | **永続データ・クエリ・オントロジー・検証可能導出** | EAVT Datom モデル、Datalog（不動点推論）、Prolly Tree、IPLD Schema、導出証明（Derivation Proof） | **主要 11 言語**: 言語外の外部 DB/ORM（SQL/GraphQL/RDF）に依存し、プログラム同一性とデータ同一性は分断。 | **Kotobase**（EAVT Datom + Datalog + Prolly Tree + IPLD / DAG-CBOR）により、データ・スキーマ・クエリを内容アドレス基板に統合。KOTOBA-985 で `kotoba:Form`（ブロック CID）と `kotoba:ProgramVersion`（`DefCID`）を繋ぐオントロジー＋再検証可能な `Derivation` 記録を設計中（現状は仕様＋適合記録のみで未実装；別ブランチ `com-junkawasaki/kotoba-985-form-ontology` 上の `form-ontology-and-derivation.md`）。 | **実装済み（Kotobase / Prolly Tree） / 設計済み・未実装（Form Ontology KOTOBA-985）** — `form-ontology-and-derivation.md`（`kotoba-985` ブランチ）, `kotoba-lang` org repos |
| 14 | **コンパイラ正しさ・形式検証** | 依存型定理証明（Lean 4）、意味保存変換証明（Translation Validation / CompCert 型）、差動テスト・ミュータントテスト | **C**: CompCert (Coq) / Frama-C。**Rust**: RustBelt (Iris/Coq) / Kani。**一般 11 言語**: 単体・プロパティテスト中心。 | **差動テスト＋ミュータントテスト**（`kotoba-sema` 対 `amu/seed`、複数ターゲット一致検査）に加え、**Lean 4 による SIR 変換の形式証明**を開始（`KOTOBA-963`: `Fold.lean` における T2 `i64` 定数加算畳込み＋Fuel 保持の証明）。コンパイラ全体・IR 全命令の意味保存証明（KOTOBA-963〜965）は今後の拡張対象。 | **部分実装（差動/ミュータント＋Lean 4 最小定理 `KOTOBA-963`） / 設計済み・未実装（全 IR 意味保存証明）** — `kotoba-lang/amu` branch `com-junkawasaki/kotoba-963-sir-lean` (`96898e7a`), `form-ontology-and-derivation.md` |

---

## 3. 言語別クロスリファレンス要約表（主要 11 言語との比較）

| 言語 | 歴史的に代表して解決した課題 | 中核の数学的・工学的武器 | Kotoba が取り込んだ／別解で達成した点 | Kotoba が意図的に捨てた／まだ届いていない点 |
|---|---|---|---|---|
| **C** | ハードウェア直接制御・最小ランタイム・ABI 基盤 | ポインタ演算・手動メモリ管理・静的関数呼出 | ランタイム依存なし静的バイナリ、`seed/SIR` による直接機械語生成・明示的整数幅 (`:i64`)・OS/カーネル領域（AIUEOS） | 任意ポインタ・UB・暗黙の整数昇格・未検査メモリ破壊の許容を**完全排除** |
| **C++** | ゼロコスト抽象化・決定的リソース解放 | RAII・デストラクタ・テンプレート単相化 | コンパイル時単相化（Row 多相・モジュール `:params`）と非 GC アリーナ/ハンドル表現 | 複雑なテンプレートメタプログラミング、未定義動作、暗黙変換、例外ベースの非局所脱出 |
| **Java** | メモリ安全・クロスプラットフォーム移植性・標準実行基盤 | JVM・Tracing GC・バイトコード検証・検査例外 | 中間表現によるマルチターゲット出力（KIR/Wasm/Native）＋検査された安全な実行境界（Kototama） | JVM 依存、オブジェクト指向クラス継承、実行時リフレクション、動的クラスロード、Null 許容参照 |
| **TypeScript** | 大規模構造データへの漸進的静的型付けと開発者体験 | 構造的部分型・判別共用体・Control-Flow Narrowing・`tsserver` | レコード Row 多相（`{:k T \| r}`）、閉じた `[:variant]`、Hiccup 狭小化、`nil`→`[:option T]` | **未到達**: `tsserver` 級の常駐 LSP 体験。**非採用**: 無タグ任意ユニオン `A \| B`、`any`/`unknown` 漸進型、任意型の自動 Narrowing |
| **Go** | 高速コンパイル・シンプルさ・実用的な並行処理 | 高速シングルパス設計・CSP（Goroutine/Channel）・構造的 Interface | 単純明快なサブセットによる高速セルフホストコンパイル、有界 CSP ライブラリ（`async`） | アンビエントな Goroutine 起動と共有メモリ競合、暗黙的インタフェース、実行時型アサーション |
| **Python** | 高い記述生産性・ドメイン DSL・AI/数値計算統合 | 動的ダックタイピング・豊富な高水準ライブラリ・簡潔構文 | 高水準 S 式表現（コレクション・分配束縛・`->` 糖衣）と `.cljc`/垂直 DSL 連携 | 動的型実行時エラー、実行時モンキーパッチ・`eval`、アンビエントな I/O・グローバル状態 |
| **Ruby** | 人間中心の表現力・内部 DSL の書きやすさ | ブロック/高階操作・柔軟な構文とメタプログラミング | S 式同図像性＋`defdesugar`・`->`/`->>`・`defprotocol` による読みやすい DSL | 実行時オープンクラス変更、暗黙の真理値ルール（数値/文字列が truthy）、任意メタプログラミング |
| **Rust** | GC なしメモリ安全・データ競合の静的排除 | アフィン/線形所有権・ボローチェッカ・`Result`・Trait 単相化 | `[:result T E]`（`abort` 自動展開）・閉じたプロトコル単相化・Capability 限定のアフィン検査・Wasm/Native 生成 | 汎用値への所有権/ライフタイム注釈（意図的に不変永続＋Arena を選択）、任意手続きマクロ、外部 crate エコシステム規模 |
| **Unison** | 内容アドレスコード・分散コード共有・静的エフェクト | AST ハッシュ・Codebase 永続化・Algebraic Abilities | **中核着想**: `DefCID` (payload-v2、α-独立)、Capability としての Ability、Effect Row 推論、`definition-patch`/`share` | **未到達**: 分散 Codebase 同期・パッチマージの運用完成、セルフホスト `(ref "bafy...")` 解決。**非採用**: Unison 独自構文（Clojure 形 S 式を維持） |
| **Scala** | 関数型とモジュール抽象の統合・型安全な表現力 | 型クラス（Given/Using）・ADT・局所型推論・Union 型 | ADT (`defrecord` + `[:variant]`)、閉じたディスパッチ、型指向の無注釈パラメータ推論 | 複雑な高階型/暗黙探索、JVM ランタイム、Scala 3 の任意 Union 型 `A \| B` |
| **OCaml** | 高速かつ健全な完全型推論・モジュール抽象・高速ネイティブ | Hindley–Milner (Algorithm W)・Row 多相・パターンマッチ網羅性・高速 Native | Row 多相レコード、網羅的パターンマッチ、不変値中心、高速ネイティブ生成 | **非採用**: Algorithm W（一般型変数・let 多相）と ML Functor（代わりに具体型固定点推論＋モジュール `:params` を採用） |

---

## 4. 未解決課題・今後の優先ロードマップ

現在の日次開発・仕様契約上で残っている技術ギャップを、開発者体験と言語統一の観点で優先度順に整理すると以下の 5 点になる。

1. **常駐インクリメンタル LSP サーバの整備**: 既存の `kotoba.lsp` / `kotoba.lsp.rpc` を `kotoba-sema` / `amu` の型推論器・`DefCID` 索引と直結させ、補完・ホバー型表示・ジャンプ・リファクタを IDE へ提供する。
2. **`kotoba-sema` と `amu/seed` セルフホストの仕様収束（48/86 → 86/86）**: `extend-protocol default`、クロスモジュール型（`E6017`）、`defdesugar`、複数ソース `map`、`lazy-*` 等の未到達機能を計画的に埋め、公式の言語仕様差分表＋自動差分テストで管理する。
3. **汎用多相性（`:element-type-polymorphism`）の方針決定**: モジュール `:params`（`E6023`）をセルフホスト側へ持ち込むか、限定的なジェネリクス／有界型変数を導入するかの言語設計判断（Algorithm W 全導入ではなく決定性・`DefCID` と両立する設計）。
4. **Unison 的交換網の完成**: `definition-patch` / `share` のネットワーク輸送・署名検証・並行マージと、セルフホストにおける `(ref "bafy...")` 直接参照（`ADR 0411` の残件）の実装。
5. **99% ターゲットカバレッジと Lean 4 形式証明の拡大**: Android / Windows / iOS の L1 実機証跡の取得（`ADR 0410`）と、`KOTOBA-963`（定数加算畳込み）に続く `KOTOBA-964`/`965` での KIR → SIR → 機械語の意味保存証明の拡張。

---

[Amu コンパイラを順番に学ぶ](../learn/amu/README.ja.md)。

# ADR — kotoba 言語の安全性設計まとめ直し: capability-confinement を一次原理にする

Status: **Accepted・実装進行中** — gating 層は実装完了（capability/per-cid・subset 全 spec・effect interprocedural・S1b literal 型チェック・`:memory-pages` 強制・least-privilege tooling、実 cell で end-to-end 検証）。borrow checker（S2）は capability 値限定の narrow slice として実装済み（2026-07-08、`kotoba-lang/kotoba` `cap-affine-problems`、汎用 ownership システムではない）。2026-07-11 に型・region・structured concurrency の CLJC 契約を採択し、M3 compiler admission adapter まで実装した（公開依存pin更新前の注釈付きsourceは fail-closed）。残務は typed HIR、region / task の lowering、capability 値渡し S4b の ABI 拡張、supply chain S5。詳細は §0「実装状況サマリ」と §9 ロードマップ、および `ADR-kotoba-type-region-structured-concurrency.md`。
Date: 2026-06-25（設計）/ 実装更新: 2026-06-26
Implemented: `crates/kotoba-clj`（`policy.rs` / `subset.rs` / `effects.rs` / `ty.rs` / `cli.rs`、safe-mode tests 約 170）
Crate: `crates/kotoba-clj`（front-end 拡張）+ `kotoba-runtime` / `kotoba-lattice`（runtime 側 enforcement）
関連: `docs/ADR-kotoba-wasm.md`, `docs/ADR-kotoba-word.md`, `docs/ADR-kotoba-mesh-wasm-hosting.md`, `docs/SECURITY-ARCHITECTURE.md`, `docs/ADR-sealed-cold-tier.md`

## 0. 一行で

**最安全は「強い言語」ではなく「攻撃されても何もできない実行環境」である。**
したがって kotoba の言語安全性は *ownership の強さ* ではなく **capability confinement（与えられていない資源には型レベルで手が届かない）** を一次原理に据え、それを言語層と実行層の **二重化**で達成する。

> 順位:
> ```
> 1. 能力ベース sandbox + deny-by-default + 再現可能・検証済みビルド   ← kotoba が狙う到達点(S級)
> 2. Rust 的 ownership/borrow を持つ小さな Wasm 言語                  (A級)
> 3. clj/cljs 風 syntax + safe subset + borrow checker               (B級)
> 4. linter だけで守る Clojure/CLJS                                  (最下位)
> ```
> mythos 級の敵対 agent に対して **linter の赤線は親切な看板にすぎない**。壁ごと来る相手には、そもそも壁の外に資源を置かない設計（confinement）でしか勝てない。

### 実装状況サマリ（2026-06-25）

`compile_safe_kotoba`（legacy alias: `compile_safe_clj`）は `compile_str`（legacy/ambient 経路）と別の deny-by-default プロファイルとして稼働中。
ゲート層はほぼ完成、型システム層が残務。

| 機能 | 状態 | 定理 | 場所 |
|---|---|---|---|
| Capability gate（policy 由来 import、ambient 廃止） | ✅ | T3 | `policy.rs` |
| per-cid 束縛（graph/model 単位の instance 粒度） | ✅ | T3 | `policy.rs::check_resource_targets` |
| Subset gate（eval/require/set!/defmacro/reflection/ref types 拒否） | ✅ | — | `subset.rs` |
| Effect gate（宣言 ⊇ 推移的 used、interprocedural） | ✅ | T2 | `effects.rs` |
| Effect 推論 `infer_effects` / least-privilege `minimal_policy` / over-grant linter `unused_grants` | ✅ | — | `lib.rs` / `policy.rs` |
| 監査 `embedded_capability_ifaces` / `Policy::to_edn` | ✅ | — | `lib.rs` / `policy.rs` |
| CLI `kotoba wasm safe-build` / `safe-policy` | ✅ | — | `kotoba-cli` public surface + `cli.rs` compatibility surface |
| literal 型チェック（numeric/string/比較/**bitwise** op に非数値 literal を拒否 + `byte-at` 静的境界） | ✅ | T-lite | `ty.rs` |
| **bitwise op を数値としてモデル化**（`bit-and`/`bit-or`/`bit-xor`/`bit-shift-left`/`bit-shift-right`）: ty.rs（literal）+ ty_infer（変数・結果型 Num）で string/keyword ハンドルへの bit 演算を拒否。prelude は handle pack を codegen 側で行い bit-op を一切使わないため偽陽性ゼロ。raw メモリ op deny（iter3）と合わせ、手動ハンドル操作の経路を閉じる | ✅ | — | `ty.rs` / `ty_infer.rs` |
| **math builtin を数値としてモデル化**（`double`/`int`/`Math/round,floor,ceil,abs,sqrt`）: ty_infer（lowered-AST、名前空間付きソース名でも曖昧性なし）で arg/結果 Num。`(Math/sqrt "x")` 等の文字列ハンドルへの数学演算を literal・変数の両方で捕捉。型チェッカの builtin カバレッジを完成 | ✅ | — | `ty_infer.rs` |
| **型推論（typed-HIR core）**: AST 上で `Num`/`Str`/`Bytes`/`Unknown` を `let`/param 経由で伝播し、変数レベルの op 境界不一致を検出（`Unknown` permissive＝false positive なし、prelude/実 cell で検証） | ✅ | — | `ty_infer.rs` |
| `:memory-pages` を emit module の memory max に適用（engine が物理的に enforce、static data 超過 / wasm32 max 65536 超過は compile error） | ✅ | — | `codegen.rs` / `lib.rs` / `policy.rs` |
| **関数 signature 推論（cross-function, 両方向・連結）**: ①戻り値型を call graph fixpoint（Jacobi）で閉じ（name+arity keyed・相互再帰収束）`Call` 結果を callee signature で型付け（**`Str` のみ境界越し伝播** — `Num`/`Bytes` 戻り値は算術構築ハンドルと区別不能なため `Unknown` collapse）。②各 param の要求型を本体内の直接 builtin 使用から推論（shadow-safe・多態使用は Any）。③**両者を call site で連結**（`Ctx{rets,reqs}` を forward `infer` パスに統合）: リテラル引数は両方向照合、**非リテラル引数は推論型が concretely `Str`（genuine 文字列ハンドル）の時のみ**要求型と照合（`Num`/`Bytes`/`Unknown` 引数は handle-pun のため無制約）。`(add1 (greet))` / `(let [s "x"] (add1 s))` 等の文字列→数値 param 流入を捕捉、prelude false positive なし | ✅ | — | `ty_infer.rs::{infer_return_types,infer_param_reqs,check_arg}` |
| 型付き HIR 本体: `Option`/`Result`・no-nil（S1b 残） | ⬜ | — | — |
| **raw メモリ primitive を deny（T1 部分）**: `alloc`/`load64`/`store64!`/`load32`/`store32!` を subset gate に追加。user safe Kotoba はモジュール内任意オフセットの read/write 不可（自前 heap・string ハンドル・container record を `store64!` で破壊する唯一の経路を封鎖）。メモリは境界尊重の accessor（`bytes-*`/`byte-at`/`str-len`・vector/map prelude）経由のみ。prelude は免除（user source のみ検査）で container 実装は継続 | ✅ | **T1 部分** | `subset.rs` |
| **`byte-at` 境界チェック（静的 + 実行時）（T1）**: ①`(byte-at <文字列リテラル> <整数リテラル>)` で OOB なら compile error（静的、偽陽性なし）。②**実行時**: codegen が `byte-at` に境界チェックを emit — `index >=u (handle & 0xFFFFFFFF = len)` なら trap（unsigned 比較で負 index も捕捉）。変数 index の OOB 読みも封鎖し、`byte-at` は完全に bounds-respecting に。OOB literal は静的に弾かれ codegen に届かない | ✅ | **T1** | `ty.rs` / `codegen.rs` |
| **`byte-append!` 容量チェック（実行時）（T1）**: codegen が書き込み前に `len >=u cap`（ヘッダ `[cap@0,len@4]`）なら trap。バッファを `bytes-alloc` の cap を超えて追記＝隣接 linear memory への buffer overflow を封鎖。正しくサイズした buffer は trap せず（cbor 等の prelude エンコーダで検証） | ✅ | **T1** | `codegen.rs` |
| **`bytes-alloc` 負容量ガード（静的 + 実行時）（T1）**: 負 cap は header に巨大 unsigned `cap` として格納され byte-append! の overflow ガードを無効化（かつ `cap+8` が wrap して微小確保）。①負 literal は ty.rs で静的拒否、②実行時は codegen が `cap < 0` で trap。byte-append! 防御を回避する経路を封鎖（回帰テストで「負 cap → append 前に trap」を固定） | ✅ | **T1** | `ty.rs` / `codegen.rs` |
| **borrow checker（S2、narrow slice、2026-07-08、provenance追跡2026-07-08）**: 汎用の Rust 級 ownership/borrow/lifetime システムは意図的に作らない — T1 はそれ無しで別経路（free-less allocator + 境界チェック + 並行 primitive deny）で既に達成済み。残したスコープは **capability 値限定**の affine typing のみ: `^{:cap <kind>}` param・`(cap-acquire ...)` 直結果・let alias のいずれも、関数本体の単一実行パス上で **高々一度**しか消費（`<op>-with` 先頭引数 / callee の cap-typed param 引数）できない（deterministic drop = 未使用は許可、no implicit clone = 再消費は `:cap-value-reused` で拒否）。`if` 分岐は相互排他だが両分岐を同じ起点から独立に検査し union で合流（どちらが実行されたか静的に分からないため）。追跡は**値の provenance（origin id）単位**——`(cap-acquire ...)` の各出現ごとにカウンタで新しい origin を採番し、let-bound alias はエイリアス元と同じ origin を共有する——ため、`(let [alias c] ...)` で別名にリネームして `alias` と `c` を1回ずつ使うケース(多段エイリアスチェーン含む)も同一 origin の二重消費として正しく検出する（当初の実装では束縛名単位の追跡で見逃していたが、2026-07-08 に origin 単位に直して解消） | ✅(narrow slice) | **T1 は S2 無しで別経路により達成済み。この narrow slice は capability 値渡し(S4b)の ownership 表現が目的** | kotoba-lang/kotoba `src/kotoba/runtime.clj`（`cap-affine-problems` / `cap-affine-step` / `affine-use` / `cap-expr-info`）/ `test/kotoba/cap_affine_test.clj` |
| **reproducible build 検証（S5 部分）**: `compile_safe_kotoba[_with_prelude]`（legacy alias: `compile_safe_clj[_with_prelude]`）がバイト決定的（同一ソース→同一 wasm→安定 CID）であることを test でロック。teeth: Rust の `HashMap` は `new()` ごとに別シードのため、codegen が emission で map を反復していれば同一プロセス内の再コンパイルで発散する → 反復しない不変条件（全 emission は source 順 Vec、map は lookup のみ）を回帰から保護。判別テスト込み。**さらに self-hosting が実際に配布する component build（`compile_component_str_with_prelude`、core module + wit-component ラップ）も決定的であることをロック**＝ confined アナライザの component CID 安定（供給網健全性）。なお safe mode は `require`/`use` を subset で deny 済 ＝ deps allowlist は deny-all で充足 | ✅ | **S5 部分** | `tests/safe_reproducible.rs` |
| **敵対的 confinement マトリクス**（実行可能な threat model）: 全防御層（subset/型/effect/capability/runtime trap）を貫き、各 escape-hatch クラスが**どの層・どの `CljError`** で拒否されるかを assert。層順（subset→型→effect→capability、最初の一致が勝つ）+ 実行時 memory-safety trap を合成保証としてロック。非自明性のため clean cell も検証 | ✅ | — | `tests/safe_confinement_matrix.rs` |
| **高階関数はゲートを迂回できない（T2/T3 をクロージャ越しに保証）**: capability collector（`used_host_imports`）と effect collector（`effects::collect`）はともに `fn` 本体を再帰的に walk するため、クロージャ内に隠した host call も **import が policy で gate され、効果は定義位置に lexically 帰属**する。`(fn [] (kqe-assert! …))` は未許諾なら Policy 拒否、`:effects #{}` 宣言下なら Effect 拒否。さらに **per-cid（instance 級）束縛もクロージャを貫く**（graphA 許諾下でクロージャが graphB に書けば Policy 拒否、graphB 許諾なら compile）。lexical scope による confinement 回避は class 級・instance 級とも不能（テストでロック） | ✅ | **T2/T3** | `codegen.rs` / `effects.rs` / `policy.rs` / `tests/safe_confinement_matrix.rs` |
| **capability の値渡し（S4b 第一スライス）✅**: capability 値が first-class 引数（opaque i64 handle）として compiled wasm を流れ、host-call 時に concrete capability へ解決される。`cap-acquire` が policy ∩ CACAO grants ∩ requested の交差を**取得時に一度だけ**実行し per-run cap table に concrete cap を格納（denial は handle を発行しない）、`<op>-with` が handle を先頭引数に取り使用時に expiry 再検査・kind 照合・偽造 handle 拒否（fail closed）。取得と各使用の両方に receipt（`:receipt/cap-handle` で連結）。effect row 宣言時は取得/使用 kind ⊆ row を check 時に強制。wasm は `cap_acquire(i32,i32,i32)->i64` + `host_i64_roundtrip_with(i64,i64)->i64` の実証 shape を emit・node 実行。実装: kotoba-lang/kotoba `kotoba.cap-table`/`kotoba.host-providers`/`kotoba.runtime`（CLJ runtime slice、2026-07-02） | ✅(slice) | **T3** | kotoba-lang/kotoba `src/kotoba/cap_table.clj` ほか |
| **typed capability params ✅（S4b 第二スライス、2026-07-02）**: param metadata `^{:cap <kind>}`（正準形はこの一形式のみ — kind keyword が既に namespaced のため `^:cap/<kind>` shorthand は不採用）を check/emit 時に静的検査。`<op>-with` の先頭引数は cap-typed（`cap-acquire` 直結果 / cap-typed param / その let alias）以外を静的拒否（`:cap-arg-not-capability` — 偽造整数はコンパイル不能）、kind 照合は op と user-fn callee param の全 call site（`:cap-kind-mismatch`、kind は静的なので常に決定可能）、effect row は cap param kind ＋ direct call graph の fixpoint で interprocedural に強制（`:cap-effect-under-declared`）。実行時の `resolve-use`（kind/expiry 再検査）は defense in depth として維持 | ✅ | **T3** | kotoba-lang/kotoba `src/kotoba/runtime.clj`（`cap-typed-problems` / `fn-required-cap-kinds`）/ `test/kotoba/cap_typed_test.clj` |
| **capability-passing の compiled threading ✅（i64 ABI、2026-07-02）**: cap-typed param が i64 handle slot に lowering（`^:i64` と同機構）され、compiled wasm を user fn 呼び出し越しに end-to-end で流れる。`demo_cap_threading`（main→outer→inner→host import の 2 段 threading）を launcher emit + node cap-map host で実行検証（42n）、checker を迂回した front end の偽造 handle module も host binding で fail closed（-1n）。CACAO chain 動的（署名検証つき）照合は launcher `run --cacao`（`cacao.core/verify-chain` → grants）で着地済み | ✅(i64 slice) | **T3** | kotoba-lang/kotoba `src/demo_cap_threading.kotoba` / `docs/lang/gates.md` |
| **capability contract EDN の kotoba-core-contracts 移管 ✅**（2026-07-05 棚卸しで確認 — 本文に「残」と誤記されたままだったが実際は完了済み）: `kotoba-core-contracts/resources/kotoba/runtime/capability_contract.edn`（`:authority "kotoba-lang/kotoba-core-contracts"`）が正本で、kgraph-* host-import 分も含め移管済み。kotoba-lang/kotoba は `deps.edn` の git-SHA pin 経由で `kotoba.core.contracts/capability-contract` を消費するのみで、重複ローカルコピーは存在しない（`src/kotoba/{runtime,host_providers,wasm_exec}.clj` 全箇所で `core-contracts/...` 経由） | ✅ | **T3** | kotoba-lang/kotoba-core-contracts `resources/kotoba/runtime/capability_contract.edn` |
| capability 値渡しの残り（pointer+length/buffer ABI `<op>-with` の compiled threading — i64 shape 以外は interpreter-only）・signed module（S5 残） | ⬜ | — | — |

達成: **T2 Effect Soundness ✅ / T3 Capability Confinement ✅（instance 粒度、バイト列検証済み）**。
**T1 Memory Safety は大部分達成**: ①raw メモリ primitive（`alloc`/`load*`/`store*`）を user code で deny（モジュール内任意 read/write 封鎖）、②`byte-at` は静的（literal OOB）+ 実行時（`index >=u len` で trap、負 index 含む）に bounds-respecting、③`byte-append!` は実行時容量 trap（`len >=u cap`）で buffer overflow 封鎖、④bump allocator は free 不実施で use-after-free/double-free 構造的に不在、⑤並行 primitive deny で data race 不在。**S2 は narrow slice(capability 値限定 affine typing)として実装済み**（上表参照、`kotoba-lang/kotoba` `cap-affine-problems`）。テスト: safe-mode 約 200（`crates/kotoba-clj/tests/safe_*.rs` + `confinement_property.rs` + `safe_confinement_matrix.rs` + `safe_reproducible.rs`）+ 実行時 trap（`compile_run.rs`）+ ast meta-guard + **実在の agent cell end-to-end**（`safe_integration.rs`）、full suite green（41 groups）、clippy clean（default + cli）。

### 0.1 語彙整理と self-hosting 境界（2026-06-29）

この ADR では、`kotoba wasm` / safe Kotoba を利用者向けの実行言語経路、
`kotoba-clj` をその当面の実装 crate として扱う。責務は次のように分ける。

```text
kotoba              = language + database + semantic substrate
kotoba wasm         = executable language path / safe Kotoba -> Wasm
kotoba-clj          = implementation crate for the compiler path
aiueos              = OS / component supervisor / capability broker
```

したがって「Rust に依存しない kotoba」は、Rust を即座に消すという意味では
なく、Rust が現在保持している **言語・admission・policy 意味論**を
Kotoba の confined Wasm component へ段階移行する、という意味で扱う。
Rust は当面、reader/parser bootstrap、Wasm emit、host/runtime、テスト oracle
として残る。

この責務分離は本 ADR の安全性モデルにも直接関係する。`kotoba` は
capability confinement の意味論と database graph の正本、`kotoba wasm` /
safe Kotoba はその意味論を deny-by-default の executable Wasm に変換する層、
`aiueos` は変換済み component を OS として接続・監査する層である。したがって self-hosting の
進捗は「Rust の行数を減らす」ではなく、「安全性判断の正本が
Kotoba component 側へ移った slice が増える」ことで測る。

実装済み slice:

- `crates/kotoba-clj/selfhost/safe_analyzer.kotoba` は safe Kotoba policy 下で
  コンパイルされる Kotoba 製 analyzer component。
- Rust bridge は source を parser-owned AST facts と versioned CBOR ABI
  (`kotoba.selfhost.safe-analyzer.v1`) に下げ、analyzer へ渡す。
- analyzer は covered surface で effect 推論、effect 宣言検査、capability /
  per-resource policy check、minimal policy、unused-grant lint、admission
  check を行う。
  parser-owned AST call graph は `name + arity` で記録・解決するため、
  multi-arity `defn` の別 overload へ effect / capability / target が
  推移伝播して混ざることはない。compatibility の古い `forms` 入力は
  name-only の conservative path として残す。
- analyzer は executable-body subset slice も持つ。parser-owned AST facts に
  表れる `eval` や raw-memory primitive (`alloc`, `load64`, `store64!`,
  `load32`, `store32!`) などの forbidden call に加え、read-as-code、runtime
  namespace loading、dynamic var mutation、shared mutable reference、ambient
  I/O、nondeterminism、ambient concurrency、reflection / host object construction
  の body-level denylist は Kotoba 側で拒否する。さらに Rust bridge が
  normalized source から `source-subset` facts を渡し、top-level `ns` の
  `:require` / `:use` / `:import` など loading / host interop clauses も
  Kotoba analyzer が拒否する。同じ source facts は top-level `defmacro`、
  runtime loading forms、host constructor / member syntax も表現する。Rust AST
  lowering が失敗する構文でも、`check_subset` と selfhost-backed compile path は
  subset-only analyzer request を送れるため、forbidden source form は Rust lowering
  fallback より前に Kotoba 側で拒否される。未正規化の namespaced variant など、
  function body AST と source-level facts だけでは正確に正規化できない source
  構造は当面 Rust subset gate が補完する。source-level subset walker は
  `defmacro` など non-executable declaration の head は記録するが body には降りず、
  実行 code ではない body 内 construct を subset denial に混ぜない。
- analyzer は parser-owned AST type slice も持つ。function body AST に表れる
  numeric / bitwise / comparison / math / conversion builtin への文字列 literal、
  `str-len` / `byte-at` の文字列要求位置への数値 literal、`byte-at` index
  位置への文字列 literal、`byte-append!` / `bytes-len` / `bytes-finish` の
  byte-buffer 要求位置、`kqe-*` / `llm-infer` / `has-capability?` の
  string-handle host import 引数位置への直接 literal mismatch は Kotoba 側で
  拒否する。
  同じ slice は direct literal value check として `byte-at` OOB / 負 index、
  `bytes-alloc` 負容量、除算 / `mod` / `rem` の literal zero も拒否する。
  Rust bridge は normalized source から `source-types` facts も渡す。これは
  small literal kind code (`Num` / `Str` / unknown) に限定した source-level
  facts で、Rust AST lowering が失敗する構文でも covered direct literal kind
  mismatch は Kotoba analyzer が拒否できる。source-level type walker は
  inert form と `defmacro` などの non-executable declaration body には降りず、
  それらは subset gate の責務に残す。値依存 literal check は引き続き parser-owned
  AST facts 側に限定する。
- Rust bridge は Rust AST lowering failure 時の source-only tooling request に
  `source-effects` facts も載せる。これは `defn` body 内の直接 host call に限定し、
  `kqe-assert!` / `kqe-retract!` / `kqe-get-objects` / `kqe-query` /
  `llm-infer` / `has-capability?` の class-level capability と literal
  resource target を Kotoba analyzer の minimal-policy / policy / admission /
  unused-grants gate に渡す。`kotoba/kqe-assert!` のような namespaced direct
  host call も source-only tooling では同じ builtin operation に正規化する。
  関数間伝播、dynamic target、effect declaration soundness は引き続き
  parser-owned AST facts 側に限定する。
  Rust bridge はこれらの builtin 名を `pure-builtin` に潰さず parser-owned
  AST facts として渡す。さらに parser-owned `type-body` AST copy から、
  direct `let` / `loop` local、`do` final、同型 `if` join の `Str` / `Num` /
  `Bytes` fact を Kotoba analyzer が保持し、builtin argument 位置で照合する。
  同じ local type pass は concrete な `loop` / `recur` rebinding も照合する。
  loop binding 初期 fact と対応する `recur` 引数 fact がどちらも concrete で型が
  変わる場合（例 `Num -> Str`）は `recur` として拒否し、Unknown は従来どおり
  permissive に倒す。
  cross-function call argument row へは local `Str` fact だけを渡し、`Num` /
  `Bytes` local fact は Rust と同じく function-call boundary では Unknown に倒す。
  ネストした builtin 引数は型判定と同時に通常の
  effect/capability walk も行うため、`(+ (do (kqe-assert! ...) 1) 2)` の
  ような形でも host call は見落とさない。さらに callee が shadow されて
  いない parameter を numeric / math / conversion / string / byte-buffer
  builtin 位置、または string-handle host import 位置で直接使う場合、call site
  の直接 literal 型 mismatch は Kotoba 側で拒否する。
  この call-site parameter requirement は `name + arity` で解決するため、
  multi-arity `defn` の 2 引数 overload が同名 1 引数 overload の要求型で
  誤拒否されることはない。
  Rust bridge は function final expression を parser-owned `ret` AST fact として
  同じ request に含め、同じ AST を parser-owned `ret-call-ast` duplicate として
  も渡す。analyzer はそこから最初の return slice と direct tail-call return
  relation を導出し、
  直接 `Str` return（string literal / `bytes-finish` / `llm-infer`、および
  then/else が両方 direct `Str` になる `if` join、direct `do` の final
  expression、direct `let` / `loop` の sequential direct-`Str` local、direct call return
  および `do` / `let` / `loop` final tail-call return の callee が `Str` に解決する場合、
  さらに final `if` の then/else がどちらも tail-call で同じ concrete `Str`
  に解決する場合）が numeric /
  math / byte-buffer / host-import 位置へ流れる場合も Kotoba 側で拒否する。
  直接 call-return を builtin 引数位置で照合する `$ret` row も `name + arity`
  で解決し、direct tail-call return relation も `function-key(name, arity)` で
  解決するため、multi-arity `defn` の別 arity の return signature は混ざらない。
  Rust bridge は通常 request では legacy `ret-call` name-only field を emit せず、
  analyzer は外部互換入力で `ret-call-ast` 由来の arity key が無い場合にだけ
  `ret-call` を使う。
  direct call return の cycle / unknown callee、mixed tail-call branch は Unknown に倒す。
  `Num` / `Bytes` return、mixed `if` branch、non-final `do` expression、
  non-`Str` shadowed local は Rust typed-HIR と同じく function boundary では
  Unknown に倒し、Kotoba slice では境界越しに伝播しない。
  typed-HIR inference、非 literal flow、runtime-dependent check は当面 Rust の
  `ty.rs` / `ty_infer.rs` が補完する。
- source-backed path では function params + AST body から、関数 parameter を
  経由した literal resource target も analyzer 側で導出する。つまり
  `(helper "graphB")` かつ `helper` が `[g]` を `(kqe-assert! g ...)` に渡す
  ケースは、Rust から `param-targets` / `call-args` を注入しなくても
  selfhost analyzer が拒否できる。この resource pass-through も `name + arity`
  で解決するため、multi-arity `defn` の別 overload が同名で存在しても
  policy / minimal-policy / unused-grant の対象 arity は混ざらない。
- 同じ analyzer-derived facts は `minimal-policy` / `unused-grants` にも使う。
  `(writer "graphA")` かつ `writer` が `[g]` を graph-write target に使う場合、
  least-privilege policy は `:graph-write ["graphA"]` に絞られ、`graphB`
  grant は over-grant として lint される。`(writer g)` のような動的 pass-through
  は引き続き wildcard に倒す。
- compile path は `check_compile_gate(src, policy)` を使い、covered
  subset/type/effect/policy gate を 1 回の Kotoba analyzer 実行で受け取る。
  成功時は selfhost subset → Rust full subset fallback → selfhost type →
  Rust full type fallback → selfhost effect → Rust typed-HIR inference →
  selfhost policy の順で同じ gate 結果を消費する。Kotoba 側がまだ
  表現できない source shape は `check_compile_gate` などの tooling API が
  source-only request に fallback して source-subset / source-types /
  source-effects facts を先に評価し、それでも表現外の部分は Rust の full gates と
  既存 `check_admission` に戻す。

この位置づけでは、capability confinement の正本は `kotoba` の意味論、
実行体への公開入口は `kotoba wasm` / safe Kotoba、component lifecycle と
capability graph の管理は `aiueos` に置く。

Wasm + capability 前提では、安全境界を JS / Node / V8 そのものには置かない。
JS/Node/browser は host として便利だが、未信頼 component の sandbox 正本ではなく、
policy 由来 import だけを bind する capability broker として扱う。raw `fetch`、
filesystem、DOM、environment、`eval`、任意 `postMessage` protocol は guest に
渡さない。Wasm instance が外界へ触れる道は import table と host-call gate だけに
限定する。

認証認可は 4 層に分ける。DID は principal/key/verification relationship、CACAO は
外部署名済み delegation envelope、Kotoba Grant は resource/action/constraint を持つ
typed capability、aiueos は local policy decision と Wasm import materialization を
担う。したがって `effective_capabilities = external_delegation ∩ local_policy ∩
component_manifest ∩ surface_policy ∩ runtime_limits` であり、CACAO resource をそのまま
host import にしない。

content address は「何に権限を与えたか」を固定する。grant は mutable component name
だけでなく `wasm-sha256` / manifest CID / source CID に結び、graph/model/policy/grant/input/output
は意味が exact bytes に依存する場合 CID で識別する。run receipt は component、manifest、
policy、grant、input、output、parent receipt を含む hash-linked audit DAG として残す。
CID は immutable identity であって current trust ではないため、revocation / expiry /
key rotation は別 policy layer で評価する。

## 1. 背景 — kotoba の現在地

kotoba には **S級に必要な実行層の部品がすでにほぼ揃っている**。にもかかわらず、safe Kotoba の型/能力言語層と import モデルが現状 **C級**に留まっており、ここが弱点になっている。`kotoba-clj` はこの compiler path の実装 crate であり、言語名や public entry point ではない。

### 1.1 すでに在るもの（実行層は強い）

| 部品 | crate | 現状 | confinement への意味 |
|---|---|---|---|
| Wasm Component Model host | `kotoba-runtime` | ✅ wasmtime 22+、`kotoba:kais` WIT | sandbox 本体 |
| gas metering | `kotoba-runtime` | ✅ assert=10 / query=100 / llm=1000 / limit=10M、`gas_limit=0` 禁止 | resource confinement の一部 |
| epoch interruption | `kotoba-runtime` | ✅ | 無限ループ/CPU 枯渇への上限 |
| CACAO depth-2 委譲 + attenuation | `kotoba-auth` | ✅ leaf.cap ⊆ root.cap, leaf.graph ⊆ root.graph, depth≥3 拒否 | **capability attenuation のランタイム実装そのもの** |
| capability mesh policy (LinkTable / wRPC) | `kotoba-lattice` | ✅ | モジュール間の権限ルーティング |
| word ごとの粗い Cap | `kotoba-word` | ✅ `proc:` / `net:` / `fs:`（Ctx 経由 enforce） | coarse-grained capability の原型 |
| encrypt-at-rest cold tier | `kotoba-store` `SealedBlockStore` | ✅ AES-256-GCM、ネットワークに出るのは暗号文のみ | secret confinement |
| t-of-N custody | `kotoba-custody` | ✅ Shamir GF(2^8) + HPKE、t−1 で何も漏れない | 鍵の単一障害点排除 |
| access receipt / 監査 | `kotoba-server`, `kotoba-datomic` | ✅ who/which/what/why/when datom、署名付き | accountability（confinement が破れた時の検知） |

### 1.2 弱点（言語層と import モデルが C級）

`docs/ADR-kotoba-wasm.md` の通り、`kotoba-clj` は現状:

- **値モデルが i64 一本**、静的型なし。`Option`/`Result`/所有権/borrow/effect いずれも未実装。
- host import が **ambient**。`WasmExecutor` は guest をインスタンス化するとき **`kotoba:kais` の全 import（kqe/kse/auth/llm/evm/btc/chain/egress）を無差別に bind** する。つまり `(kqe-assert! …)` や `(llm-infer …)` を書ける guest は、**宣言なしに**任意グラフへの書き込み・推論・チェーン読み取り能力を持つ。
- これは user 指摘の **ambient authority アンチパターン**そのもの。コードがどれだけ賢くても困るのと同様、**コードがどれだけ「正しく」ても、ambient に権限がある限り confinement は成立しない**。

```clojure
;; 現状の悪い設計: 権限が ambient（誰でも呼べる = 持っているのと同じ）
(kqe-assert! "graph-cid" "s" "p" obj)     ; どのグラフにでも書ける
(llm-infer model-cid prompt)              ; どのモデルでも推論できる
```

**結論: kotoba の安全性は実装 crate 名ではなく、safe Kotoba の前段に置く type/borrow/effect/capability checker と、後段の capability-only runtime で決まる。** 本 ADR はその二重化を定義する。

## 2. 決定 — 二重化（言語層 × 実行層）

```
Untrusted / AI-generated safe Kotoba
        ↓  reader (kotoba-edn 再利用)
        ↓  restricted macroexpand  … eval/dynamic var/unrestricted macro 禁止
   typed HIR
        ↓  ownership / borrow checker
        ↓  effect + capability checker   … Γ ⊢ e : T ! E  &  要求 cap ⊆ policy
   Wasm component
        ↓  import table は policy から生成（ambient import を一切張らない）
   capability-based runtime (kotoba-runtime + kotoba-lattice)
        ↓
   no graph-write / no inference / no egress / no secret  by default
```

二重化の要点は **「どちらか一方が破れても、もう一方が残る」**こと。

```
言語層 (compile_safe_kotoba):        実行層 (runtime):
  ownership / borrow                   Wasm sandbox
  effect system                        gas / epoch / memory quota
  capability-passing style             import table = policy 由来（ambient 排除）
  no eval / no dynamic global          CACAO depth-2 attenuation
  no hidden IO                         WASI deny-by-default（preopen のみ）
                                        SealedBlockStore / custody（secret）
                                        signed / reproducible build
```

## 3. capability-only import — kotoba 固有の核心

### 3.1 ambient import の廃止

新しいコンパイル経路 `compile_safe_kotoba`（legacy alias: `compile_safe_clj`）は、`compile_clj`（既存・互換 i64 経路）とは**別物**として追加する。

```rust
// 既存: ambient import、型なし（C級・互換用に残す）
compile_clj(src) -> Result<WasmModule, Error>

// 新規: policy 駆動、型/borrow/effect/capability checked（S級）
compile_safe_kotoba(src: &str, policy: &Policy) -> Result<WasmModule, Error>
```

そして **`WasmExecutor` 側の bind を policy 化する**: 現在の「全 `kotoba:kais` import を無差別 bind」をやめ、**module が policy で宣言した import だけを linker に張る**。宣言していない import は **そもそもリンクされない** → guest が `egress` を呼ぶコードを持っていても、import table に egress が無ければ **リンク時/インスタンス化時に失敗**する（実行時チェックではなく、authority が存在しない）。

> これは「ネットワーク capability がない → 外部送信できない」を、ランタイムの linker レベルで物理的に保証することに等しい。

### 3.2 host interface を capability 値に細粒度化

kotoba の既存 host interface（`kqe`/`kse`/`llm`/`evm`/`btc`/`egress`）を、**万能インターフェースの ambient import** から **scope 済み capability 値**へ落とす。原則は user の指摘どおり: **万能なものは攻撃者にも便利**。

| 現状の ambient interface | 細粒度 capability（型） | scope |
|---|---|---|
| `kqe`（全グラフ read+write） | `GraphReadCap{graph_cid}` / `GraphWriteCap{graph_cid}` | **単一グラフ**・read/write 分離 |
| `kse`（全 topic pub/sub） | `TopicPubCap{prefix}` / `TopicSubCap{pattern}` | topic prefix |
| `llm`（任意 model 推論） | `InferCap{model_cid}` | **特定 model-cid のみ** |
| `evm`/`btc`（任意 RPC） | `ChainReadCap{caip2}` | 特定チェーン read-only |
| `egress`（任意 HTTP） | `HttpEgressCap{allowlist}` | endpoint allowlist |
| secret/env | （capability 化、default 無し） | 明示授与のみ |

ここが kotoba の妙: **`GraphWriteCap{graph_cid}` は CACAO の attenuation（leaf.graph ⊆ root.graph）を型に持ち上げたもの**。CACAO は実行時にグラフ scope を絞る既存機構なので、`compile_safe_kotoba` の capability checker は **「この関数が要求する `GraphWriteCap` の集合 ⊆ 呼び出し元 CACAO が委譲するグラフ集合」**を静的に照合できる。実行時 attenuation と静的 capability checking が同じ束（lattice）の上で一致する。

```clojure
;; 良い設計: 権限は値として渡る。持っていないグラフには書けない。
(defn.wasm tally
  [(g    &mut GraphWriteCap)     ; ホストから明示授与された単一グラフ書き込み権
   (rows & Bytes)
   -> Result]
  {:effects #{:graph-write}}
  (kqe-assert! g "s" "p" (sum rows)))   ; g 無しでは型が付かない

;; net も同様に endpoint 限定 capability にする（万能 HttpClient ではなく）
(defn.wasm push-metric
  [(m      &mut MetricsEgressCap)  ; 特定 metrics endpoint のみ
   (metric & Metric)
   -> Result]
  {:effects #{:network}}
  (egress-post m metric))
```

```
HttpClient       ではなく  MetricsEgressCap{allow: ["https://metrics.internal/…"]}
FileSystem       ではなく  WriteOnlyLogSink
GraphStore(全部) ではなく  GraphWriteCap{graph_cid}
LLM(任意)        ではなく  InferCap{model_cid}
```

## 4. policy スキーマ（EDN）

policy は module ごとに与え、**生成 wasm が要求できる import を制限する**。既存の gas/WASI/CACAO 設定に接続する。パース可能な実例: `crates/kotoba-clj/examples/safe-policy.edn`（`Policy::parse_edn` でロード、`tests/safe_policy.rs::example_policy_edn_parses_and_gates` が doc↔code 整合を検証）。

```edn
{:exports
 [{:name "tally" :params [Bytes] :result Result}]

 ;; capability import。ここに無いものは import table に張られない（ambient 不可）。
 :imports
 {:graph-read   ["bafy…graphA"]          ; 読める graph-cid
  :graph-write  ["bafy…graphA"]          ; 書ける graph-cid（read と分離）
  :infer        []                        ; 推論可能 model-cid（空 = 不可）
  :topic-pub    []
  :egress       []                        ; HTTP allowlist（空 = deny-by-default）
  :chain-read   []
  :clock        false                     ; timing channel 抑止
  :random       false                     ; 非決定性抑止（deterministic mode）
  :secrets      []}

 ;; 実行層の quota（kotoba-runtime に渡る）
 :limits
 {:memory-pages   4
  :fuel           1000000                 ; 既存 gas accounting に接続
  :max-call-depth 128
  :max-output-bytes 65536}

 ;; supply chain
 :build
 {:deterministic true
  :signed        true
  :deps-allowlist [...]}}
```

`:imports` の各 entry が §3.2 の capability 値に 1:1 対応し、`compile_safe_kotoba` は **コード中で要求される capability ⊆ policy の `:imports`** を検査して落ちる。policy が空集合なら、その module は **deny-by-default**（何もできない純粋関数）になる。

## 5. effect system —— ownership より effect が重要

mythos 級を想定するなら、最重要は ownership ではなく **effect system**。所有権が防ぐのはメモリ系（use-after-free / double-free / data race / invalid aliasing）。一方 effect が防ぐのは **「いつ・どこへ・何を・secret に触れるか・非決定性・unsafe・host capability 要求」**という *権限の所在*。confinement の主役は後者。

判定の形:

```
Γ ⊢ expr : T ! Effects        この式は T を返し、Effects だけを起こす
```

```clojure
(defn.wasm parse [(input & Bytes) -> Ast] {:effects #{}} …)            ; pure
(defn.wasm save! [(g &mut GraphWriteCap) (d & Bytes) -> Result]
  {:effects #{:graph-write}} …)                                        ; write effect
(defn.wasm fetch! [(n &mut HttpEgressCap) (u Url) -> Result]
  {:effects #{:network}} …)                                            ; network effect
```

effect ラベルは単なる注釈ではなく、§3.2 の **capability 値の保持と一致しなければならない**（`:network` を起こす関数は `HttpEgressCap` を引数に持つ）。これにより effect の宣言と capability の授与が二重チェックになる。

**実装済み（S3, `crates/kotoba-clj/src/effects.rs`）**: `(defn f {:effects #{…}} …)` の effect row を **body が（直接・推移的に）実際に起こす effect と照合**し、宣言外の effect を起こす（under-declaration）と **(T2) Effect Soundness 違反**として拒否する。**interprocedural**: call graph を fixpoint で閉じ、helper 経由で effect が隠れるのを防ぐ（相互再帰も収束）。effect 語彙（`:graph-read`/`:graph-write`/`:infer`/`:auth`）外の宣言（typo）も拒否。over-declaration（使わない effect の宣言）は conservative として許可。annotation は opt-in（無宣言関数は capability gate のみ）。effect row（コードの自己申告）⇄ policy grant（caller の認可, §3.2）は同じ束を両側から記述する。`crates/kotoba-clj/tests/safe_effects.rs` 14 tests green（推移伝播・相互再帰の終端を含む）。

## 6. safe Kotoba の最小仕様セット

`compile_safe_kotoba` が受理する subset（`compile_clj` の互換 subset とは別プロファイル）。Clojure 構文に **見える**が、意味論は Rust/ML/linear 寄り。

```
言語:
  static type / no nil by default / Option / Result
  ownership / borrow / no implicit clone / no implicit boxing
  no eval / no runtime require / no dynamic var / no reflection
  restricted hygienic macro（allowlist された macro のみ展開）
  effect system / capability-passing style
メモリ:
  safe mode で raw pointer 無し / bounds-checked slice
  unsafe block は隔離 / deterministic drop / allocator quota
並行:
  Send / Sync / 共有可変は同期必須 / spawn は Send + 'static か scoped region
Wasm:
  ambient import 無し / import table は policy 由来のみ
  memory max 固定 / fuel metering / output size limit / deterministic mode
supply chain:
  lockfile / reproducible build / signed module / deps allowlist
```

**Clojure 互換は明示的に捨てる**（`eval`・unrestricted macro・dynamic var・lazy-by-default を持ったままでは confinement が穴だらけになる）。既存 i64 経路は互換・既存テスト用に温存し、safe Kotoba は `kotoba wasm` の public entry point として前面に出す。

## 7. 望む定理（confinement を形式化）

通常の Rust 的安全性は「well-typed program はメモリ安全エラーを起こさない」を狙う。mythos 級対策では **さらに confinement** が要る。kotoba の文脈で 3 つ:

```
(T1) Memory Safety:
     well-typed safe program は use-after-free / double-free /
     data race / invalid aliasing を起こさない。

(T2) Effect Soundness:
     Γ ⊢ P : T ! E ならば、P の実行時に観測される effect は E に含まれる。

(T3) Capability Confinement:
     program P が capability set C の下で型付けされるなら、
     P の実行中に C に含まれない外部資源へアクセスすることはない。
```

kotoba 固有の接続:
- **(T3) は CACAO attenuation のコンパイル時版**。実行層では `DelegationChain::verify`（leaf.cap ⊆ root.cap, leaf.graph ⊆ root.graph）が同じ束を守る。静的(T3) ∧ 実行時 attenuation = 二重化された confinement。
  - **(T3) は emit されたバイト列で検証済み**（`crates/kotoba-clj/tests/confinement_property.rs`, 7 tests）: pure module は `kotoba:kais` import を 1 つも埋め込まない／許諾した interface 以外（`llm`/`auth` 等）は **物理的に import section に存在しない**／policy が過剰許諾でもコードが使う capability だけが emit される、を import 名のバイト走査で確認。runtime は module が宣言した import しか bind できないため「バイト列に無い」＝「その資源に手が届かない」の最強形。
- **resource confinement** は gas/epoch/memory-pages（既存）で量的に閉じる。WASI/WASIX 経由のリソース消費攻撃（arXiv:2509.11242 等）に対し、sandbox だけでなく fuel・memory・output の quota を policy で必須化する。
- **(T2)/(T3) が破れた時の検知**は access receipt + 署名付き監査（既存 R1/R2b）が担保（confinement は完全防御ではなく、破れを slashable にする accountability と二段構え）。

## 8. 安全性ランキング上の kotoba の位置

| 級 | 構成 | kotoba |
|---|---|---|
| **S** | capability Wasm + safe static lang + verified policy + deny-by-default + signed/reproducible + OS sandbox 二重化 | **本 ADR の到達目標** |
| A | Rust/Zig → Wasm（memory-safe だが unsafe/FFI/logic bug/権限ミス残存） | runtime 単体は近い |
| B | clj syntax + safe subset + borrow checker → Wasm（設計が正しければ A に接近） | safe Kotoba の設計目標 |
| C | Clojure/CLJS + linter + convention | safe Kotoba 導入前の出発点 |

**現在地（2026-06-25）: C → B/S の間。** ambient import は廃止（policy 由来 import のみ）、
deny-by-default の **capability gate（instance 粒度・T3）／subset gate／effect gate（interprocedural・T2）**
が稼働し、T3 はバイト列で検証済み。残るのは **型システム（S1b 型付き HIR）と borrow checker（S2・T1）**、
および capability の値渡し（S4b）・supply chain（S5）。すなわち「言語層 checker の欠如」と「ambient import」
という当初 2 ギャップのうち後者は解消、前者は capability/effect 軸で達成・**型/所有権軸が残務**。

**現在地（2026-06-29）: 堅実に B、S 級要件を複数軸で充足。** 上記以降の増分で:
- **S1b 型システム ほぼ完成**: 戻り値型・パラメータ要求型の cross-function 推論（双方向・call site で連結、fixpoint で相互再帰収束）、builtin 型カバレッジ完成（算術/比較/**bit**/**math**/string/byte/host）。`Str` のみ境界越し伝播という健全境界で handle-pun を回避し false positive なし。
- **T1 Memory Safety 大部分達成**（borrow checker *無しで*）: raw メモリ op 全 deny、`byte-at` 静的+実行時境界、`byte-append!` 容量 trap、`bytes-alloc` 負容量ガード ＝ linear memory への全アクセス経路が bounds-respecting。bump allocator は free 不実施で use-after-free/double-free 構造的不在、並行 primitive deny で data race 不在。**「言語層 borrow（S2）」は当初 T1 の手段として想定されたが、free-less + 境界チェックで T1 の*目的*は別経路で達成**——S2 の残務は安全性ではなく ownership 表現（capability 値渡し S4b の前提）に純化。
- **verified confinement**: 全防御層の敵対的マトリクス + 高階関数貫通（class/instance per-cid とも）を実行可能仕様でロック。
- **S5 reproducible build**: core module + **self-hosting が配布する component build** ともバイト決定的（CID 安定）。`require`/`use` deny で deps allowlist は deny-all 充足。
- **self-hosting 進行中**: アナライザ（effect/policy/minimal-policy）を safe Kotoba 自身で書き confined component として実行し native と cross-check（dogfooding）。

**S 級到達への残務（明確化）**: ① **capability 値渡し（S4b）** — `GraphWriteCap` を値として引数渡し＋ effect↔capability 一致＋ CACAO 動的照合（言語層 ownership の主目的はここ）。② **signed module（S5）** — 再現ビルドは達成済み、署名/鍵ライフサイクルが残（aiueos ADR-0003 の ed25519 と連携）。③ **OS sandbox 二重化**（運用層、§10）。型/T1/verified/reproducible 軸は B→S をほぼ満たし、**残るは capability 値渡しと supply-chain の署名**という 2 点に収束した。

## 9. 段階導入ロードマップ（既存 phase 1–5 / A–E の続き）

既存 compiler implementation（phase 1–5、langgraph A–E、kqe/Pregel live 済み）を壊さず、safe Kotoba profile を `kotoba wasm` の public surface へ増分で積む。

| phase | 内容 | 依存 |
|---|---|---|
| **S0 ✅** | `compile_safe_kotoba(src, &Policy)`（legacy alias: `compile_safe_clj`）+ policy EDN パーサ（`crates/kotoba-clj/src/policy.rs`）。コード中で使う host import を **policy に照合し、未許諾なら module を一切 emit しない**（deny-by-default）。emit される import section ⊆ 許諾 capability になるため、**module が宣言した import しか bind できない runtime は ambient authority を張れない**＝コンパイル時に confinement が成立。read/write 分離・quota floor（`fuel`/`memory-pages` > 0 必須）・policy-aware prelude（`:graph-read` 許諾時のみ `KQE_PRELUDE` をリンク）込み。**CLI 露出**: `kotoba wasm safe-build <cell.kotoba> --policy <p.edn>` が gate を通して confined module を emit し、埋め込まれた capability surface を報告（実例 policy `crates/kotoba-clj/examples/safe-policy.edn`）。**監査 API**: `embedded_capability_ifaces(wasm)` が module の capability surface を返す（built module を policy に照合可能）。test: `safe_policy.rs`(25) + `safe_subset.rs`(17) + `confinement_property.rs`(8, **T3 をバイト列で検証**) + doctest green | — |
| **S1** | restricted macroexpand / safe-subset gate **✅**（`crates/kotoba-clj/src/subset.rs`）: `eval`/`read-string`/`require`/`use`/`import`/`in-ns`/`set!`/`binding`/`with-redefs`/`alter-var-root`/`resolve`/`gen-class`/`proxy`/`reify`/ユーザー `defmacro`、、**mutable reference types**（`atom`/`swap!`/`reset!`/`volatile!`/`ref`/`ref-set`/`alter`/`dosync`/`agent`/`send`/`add-watch` 等 = shared mutable state）、**ambient I/O**（`slurp`/`spit`/`print`/`println`/`pr`/`read-line`/`flush` = I/O は capability 経由のみ）、**non-determinism**（`rand`/`rand-int`/`shuffle`/`random-uuid` = `:random` capability 必要）、**ambient concurrency**（`future`/`promise`/`pmap`/`locking`）、**host interop syntax**（`(.method obj)`/`(. obj …)`/`(Class. args)`/`(new …)`/`..` = no host interop）を **deny-by-default で拒否**（legacy path は silently ignore する＝それ自体が confinement hole）。`(ns …)` の `:require`/`:import` clause も拒否、bare `(ns foo)` は許可。built-in macro allowlist（`->`/`cond`/`case`/threading）はそのまま展開。user source のみ gate（prelude は trusted）。`crates/kotoba-clj/tests/safe_subset.rs` 17 tests green。**S1b first slice ✅（literal 型チェック, `ty.rs`）**: 双方向の literal 型不一致を静的検出 —（a）numeric op（`+ - * / mod inc dec pos?…`）と numeric 比較（`< > <= >=`、`=` は除外）に **非数値 literal**（string/keyword/vector/map/set — いずれも heap handle で数値でない）、（b）string op（`str-len`/`byte-at` の string 引数）に **numeric literal**。どちらも i64 model が handle を誤演算/誤比較する silent miscompile の bug class。加えて（c）**literal-zero 除算**（`/ mod rem quot` の divisor が literal 0）は実行時 trap 確定なので静的に拒否。literal のみ判定＝false positive なし（変数は実行時不明なので素通り、inert form の中身も解析しない）。typed HIR の足場。`safe_types.rs` 26 tests green。**残り（S1b 本体）**: i64 一本 → 型付き HIR・`Option`/`Result`・no-nil | kotoba-edn reader |
| **S2 ✅(narrow slice, 2026-07-08)** | 汎用 ownership/borrow システムではなく、capability 値限定の affine typing（deterministic drop、no implicit clone）。当初 (T1) を狙って計画されたが、T1 は free-less allocator + 境界チェック + 並行 primitive deny で別経路で既に達成済みのため、残ったスコープは capability 値渡し(S4b)の ownership 表現に純化。`kotoba.runtime/cap-affine-problems`（`kotoba-lang/kotoba`）が `run`/`check`/`wasm emit` で走る。追跡は束縛名単位ではなく値の provenance（origin id）単位（2026-07-08 修正）: let alias へのリネームによる再消費も、多段エイリアスチェーンも正しく検出する | S4b の typed capability params |
| **S3 ✅** | effect system（`{:effects …}` の検査、`Γ ⊢ e:T!E`）。`effects.rs` が effect row ⊇ **推移的** used-effects を強制（call graph fixpoint、under-declaration / unknown effect 拒否、over-declaration 許可、opt-in、相互再帰収束）。**(T2) Effect Soundness の checking 達成**。**S3b（effect 推論）✅**: `infer_effects(src)` が注釈なしで各関数の推移的 effect を推論（公開 API、`safe-build` が `inferred effects:` として report）。残り（S3c）: 注釈の必須化／自動付与・capability 値保持との一致検査 | S1 |
| **S4 ✅(per-cid slice)** | **per-cid capability binding**（`policy.rs::check_resource_targets`）: リテラル resource id を policy allowlist と静的照合 → **T3 を class 粒度から instance 粒度へ**。対象: `kqe-assert!`/`kqe-retract!`(graph-write)・`kqe-get-objects`(graph-read)・`llm-infer`(infer = **model-cid 単位**)。graphA への write 許諾は graphB を許さない／modelA の推論許諾は modelB を許さない。`"*"` で any、dynamic 引数は class-level fallback。CACAO の `leaf.graph ⊆ root.graph` のコンパイル時版。`crates/kotoba-clj/tests/safe_percid.rs` 13 tests green。**least-privilege 合成（gate の逆）✅**: `minimal_policy(src)` ／ `kotoba wasm safe-policy <cell>` が cell に必要な最小 policy を合成して EDN 出力（literal は per-cid pin、dynamic は `"*"`）。不変条件: 合成 policy は必ず compile 通過（sufficiency）かつ任意 grant 除去で失敗（minimality）。`Policy::to_edn` が `parse_edn` と round-trip。`safe_minimal.rs` 8 tests green。**S4b 第一スライス ✅（capability-passing、kotoba-lang/kotoba CLJ runtime、2026-07-02）**: `cap-acquire` が交差（policy ∩ grants ∩ requested）を取得時に一度だけ実行し concrete cap を per-run cap table（`kotoba.cap-table`）へ格納、opaque i64 handle を返す。`<op>-with` use variant が handle を first-class 引数として受け、host-call 時に stored concrete cap へ解決（再交差なし・expiry は使用時再検査・kind 照合・未発行 handle 拒否 = fail closed）。取得/使用の両 receipt が `:receipt/cap-handle` で連結。effect row 宣言時は acquire/use kind ⊆ row を強制（`effects-consistent?` 再利用）。wasm 実証 shape `cap_acquire(i32,i32,i32)->i64` / `host_i64_roundtrip_with(i64,i64)->i64` を emit し node で実行検証。**S4b 第二スライス ✅（typed capability params + compiled threading、2026-07-02）**: `^{:cap <kind>}` param（正準形はこの一形式のみ）を静的検査 — `<op>-with` 先頭引数の cap-typed 強制（`:cap-arg-not-capability`）・全 call site の kind 照合（`:cap-kind-mismatch`）・effect row の interprocedural fixpoint（`:cap-effect-under-declared`）— し、cap-typed param を i64 handle slot に lowering して user fn 呼び出し越しの compiled threading を end-to-end 実証（`demo_cap_threading` 2 段、node 実行、checker 迂回 module は host binding で fail closed）。CACAO delegation chain の動的（署名検証つき）照合も `run --cacao` で着地。残り（S4b 残）: pointer+length/buffer ABI `<op>-with` の compiled threading（i64 shape 以外は interpreter-only）。capability contract EDN の kotoba-core-contracts 移管は完了済み（2026-07-05 訂正、上表参照） | S3 + kotoba-auth |
| **S5** | supply chain: signed module、reproducible build、deps allowlist。OS sandbox 二重化（seccomp/gVisor/Firecracker 検討）を runtime 運用に | S0–S4 |

各 phase は「言語層を一段強くする」＋「runtime 側の policy enforcement を一段増やす」の対で進める（片側だけ進めても confinement は完成しないため）。

## 10. host 側運用原則（mythos 級前提）

```
- module ごとに isolate / 可能なら Wasm runtime + OS sandbox 二重化
- network は allowlist / filesystem は preopen のみ
- secrets は raw env に置かない（capability 化・SealedBlockStore/custody 経由）
- token は short-lived + scoped（CACAO）/ output は taint 付き
- logs は append-only（CommitDag）/ build artifact は signed
```

Wasm sandbox は強いが **絶対ではない**（CVE-2025-4609 等の sandbox escape 事例あり）。「Wasm だから安全」を禁物とし、OS sandbox との二重化を運用前提に置く。

## 11. 命名

capability-safe language profile の公開呼称は **safe Kotoba** とする。実装上は
`compile_safe_kotoba*` API と `kotoba wasm safe-*` CLI が一次入口で、
`compile_safe_clj*` は legacy alias として残す。`kotoba-clj` は当面の
実装 crate 名であり、言語名ではない。

- public name: **safe Kotoba**
- implementation crate: `kotoba-clj`
- compatibility API: `compile_safe_clj*`

目指すのは **「Clojure 互換言語」ではなく「Clojure-shaped capability-safe Wasm language」**。構文は clj に見え、意味論は affine + effect + object-capability。

## 12. 禁止（本 ADR 由来）

- safe Kotoba で **ambient host import を張る**（policy 由来 import table のみ）
- `compile_safe_kotoba` で **eval / dynamic var / unrestricted macro / reflection** を許す
- 万能 capability（`HttpClient` 全開・`GraphStore` 全グラフ）を guest に渡す（必ず scope 済み値に絞る）
- gas/memory/output quota 未設定での safe module 実行（既存 `gas_limit=0` 禁止を踏襲）
- 「Wasm sandbox だから OS sandbox 不要」と判断する
- 新しい host import（`HostImport` variant）を追加して **`HostImport::ALL` への追加を忘れる**。capability 監査 `embedded_capability_ifaces` は `ALL` から interface 集合を導出するため、漏れると新能力が監査から消える（silent confinement gap）。`CapClass::of` の exhaustive match と `ast::host_import_meta_tests::host_import_all_is_complete` がコンパイル時／テスト時に強制する
- 新しい EDN walker（subset/type/effect/policy 系の解析）を書くとき **inert form（`quote`/`var`/`comment`）の中身を解析する**。これらは data か compile 時に drop され実行されない（`eval` は subset で禁止済みなので code に昇格しない）ため、解析すると false positive（valid code の誤拒否・effect 誤帰属・不要 capability 要求）を生む。判定は `ast::is_inert_form` に single-source 済み — 全 walker がこれを呼ぶ（`tests/safe_quote.rs` が quote/var/comment の回帰を防ぐ）

## 13. 既知の限界（honest limitations — S 級 rigor のための非防御範囲）

S 級は「強い防御」だけでなく「**何を防御し、何を防御しないかを正直に明示する**」ことを要する
（aiueos `SECURITY.md` と同じ姿勢）。実装を重ねて確認した限界を性質づけ付きで列挙する。

### (a) 言語層 checker の保守的 false-negative（**false-positive は無い** — 安全側に倒れる）

- **`loop` 変数の型変化**: Rust `ty_infer` は loop 変数を初期値の型でのみ型付けするため、
  `recur` 後の型変化を単独では見逃し得る。selfhost analyzer は parser-owned body AST 上で
  concrete な loop binding 初期 fact と `recur` 引数 fact を照合し、`Num -> Str` などの
  直接型変化は先に拒否する。ただし Unknown、非 literal/dynamic flow、prelude 側の
  handle-as-number パターンは false-positive 回避のため permissive に残す。残る見逃しは
  correctness（メモリ安全ではない — i64 演算は範囲内）に限る。
- **非リテラル `Num`/`Bytes` 引数・戻り値**: 算術構築されたハンドルと数値が i64 上で区別不能なため、
  境界越し（call site / 関数戻り値）では `Str` のみ伝播・照合し、`Num`/`Bytes` は `Unknown` に倒す
  （handle-pun 回避の健全境界）。よって「数値に見えて実はハンドル」の誤用は型層では捕捉しない。
- **`Option`/`Result`/no-nil の不在**: `nil` は `Int(0)` に lowering され、ハンドル要求位置では
  既存の Num-literal チェックが捕捉するが、`nil` 専用の型区別は無い（S1b 残）。

### (b) 健全だが「別の層で担保」している事項（型層単独では緩い）

- **effect の高階関数帰属は lexical（定義位置）**: クロージャの効果はそれを*定義*する関数に帰属する
  （`recur`/呼び出し位置ではない）。定義側には過剰宣言を要求し得るが、**capability gate（T3）が
  import 単位で必ず gate する**ため confinement は別途成立（テスト済み）。caller 側の T2 精度は限定的。
- **per-cid の動的引数 fallback**: `(kqe-assert! "graphA" …)` の literal cid は instance 級で照合する。
  literal cid が **1 関数層を貫通**する場合（`(defn writer [g] (kqe-assert! g …))` を `(writer "graphB")`
  で呼ぶ）も call site の literal を allowlist と照合する（selfhost source-backed path は
  `name + arity` で multi-arity overload を分離し、直接 target 使用のみ・**shadow-safe**）。
  **残る fallback は
  cid が *変数*の場合**——静的照合できず **class 級**（graph-write 許諾で任意 cid 可）になる。真の動的
  instance 級照合は host ABI 実行時 + CACAO 照合が要る（S4b 残・コンパイラ単体の範囲外）。
  なお param が body 内で `let`/`loop`/`fn` にシャドーされる場合は、その param をターゲット帰属から
  除外し class 級 fallback に戻す（`collect_bound_names_edn`）——シャドーされた名前の使用は param で
  なく内側束縛を指すため、誤帰属による caller の false-positive を防ぐ（回帰テスト済み）。
- **`=`/`not=` はハンドル等価**: 文字列の `=` は内容でなく `(offset,len)` ハンドルを比較する
  （意図的 permissive、`str-eq?` を使う想定）。

### (c) 未実装（S 級到達の残務）

- **capability 値渡し（S4b、残り）** — 第一スライス（handle-based capability-passing:
  取得時一回交差 + per-run cap table + 使用時 expiry/kind/偽造 fail-closed + 取得/使用 receipt +
  effect↔capability 一致 + wasm 実証 shape）に加え、第二スライス（typed capability params:
  `^{:cap <kind>}` の静的検査 = cap-typed 引数強制・kind 照合・interprocedural effect fixpoint、
  cap-typed param の i64 lowering による user fn 呼び出し越し compiled threading）も
  kotoba-lang/kotoba の CLJ runtime slice で達成（2026-07-02、docs/lang/capability-values.md
  「Typed capability parameters」）。CACAO delegation chain の動的（署名検証つき）照合も
  `run --cacao` で着地済み。残り: pointer+length/buffer ABI `<op>-with` の compiled threading
  （i64 shape 以外は interpreter-only）。capability contract EDN の kotoba-core-contracts 移管は
  完了済み（2026-07-05 訂正 — `kotoba-core-contracts/resources/kotoba/runtime/
  capability_contract.edn` が正本、kotoba-lang/kotoba は git-SHA pin 経由で消費するのみ）。
- **signed module（S5）** — reproducible build は達成（core + component、CID 安定）。署名/鍵
  ライフサイクルが残（aiueos ADR-0003 の ed25519 と連携）。
- **OS sandbox 二重化**（運用層、§10）。

### (d) TCB（trust boundary）

prelude・codegen・wasmtime・wit-component は**信頼**する（検証対象外）。prelude は subset/型/effect
gate を**免除**される（trusted code）ため、prelude のバグは confinement を破り得る。Wasm sandbox
自体も絶対ではない（escape CVE 実在、§10）。confinement は「破れを slashable にする accountability」
（access receipt + 署名監査）と二段構えで、完全防御の主張ではない。

## 14. まとめ

```
所有権     : メモリを壊させない          (T1)
borrow     : aliasing / data race を防ぐ  (T1)
effect     : 何を起こせるか制限する       (T2)
capability : そもそも攻撃対象へ手を届かせない (T3)  ← 一次原理
Wasm sandbox: 破られても外へ出にくくする
OS sandbox : runtime ごと閉じ込める
```

kotoba は **実行層（S級部品）がすでに強い**。残る仕事は、safe Kotoba を *capability-confinement を一次原理とする Wasm language profile* に育て、**ambient import を policy 由来 import に置き換える**こと。それが済めば kotoba は

```
safe Kotoba → borrow/effect checked Wasm
  + capability-only imports
  + deny-by-default runtime
  + strict resource limits
  + separate OS sandbox
  + signed / reproducible build
```

という S級構成に到達する。`kotoba-clj` は safe Kotoba→Wasm の実装土台だが、**真の安全性は実装 crate 名ではなく前段の checker と後段の capability runtime が決める。**

### 参考

- Capabilities-Based Security with WASI — marcokuoni.ch
- Why do AI agents complicate zero trust and NHI controls? — nhimg.org
- Exploring and Exploiting the Resource Isolation Attack Surface of WebAssembly Containers — arXiv:2509.11242
- NSA/CISA CSI: Importance of Memory-Safe Languages — nsa.gov
- CVE-2025-4609 sandbox escape — ox.security

# 標準 O(N) 償却テキストビルダー（`:text-builder`）およびネイティブ Docs・テキスト処理予算方針（v1 提案）

- **版**: `v1`（`:status :proposed`）
- **策定日**: `2026-11-27`
- **追跡 Issue / 契機**: [KOTOBA-989](https://linear.app/kotobalabs/issue/KOTOBA-989)（Phase 2 `R2: 5.0 → 8.5`）／ PR #897（`kotoba-lang/kotoba-lang` Item `#526`, `docs/lang/language-problem-matrix.ja.md` 拡張時の 64 KiB 境界＋`jacob` 18:18 JST `swap 93.9%` インシデント）
- **横断監査連携**: `AmuSpec`（`amu@87ec570a`・`kotoba@bab283448`・`kotoba-lang@86478b60` 静的監査、Item `#593`）と整合済み
- **正本仕様**:
  - [`lang/text-builder.edn`](../../lang/text-builder.edn)
  - [`lang/text-tool-budgets.edn`](../../lang/text-tool-budgets.edn)
  - [`lang/text-builder-conformance/manifest.edn`](../../lang/text-builder-conformance/manifest.edn)
- **互換性前提**: `Profile 6` / `payload-v2` DefCID / `lang/limits.edn` v1 は本提案単体では**非変更（後方互換破壊なし）**。

---

## 1. 一次根拠の診断：仕様ドリフト vs 言語ギャップ vs アプリ実装負債

PR #897 の調査および `AmuSpec` との 3 リポジトリ横断監査で確認された問題は、単一のバグではなく以下の構造的ギャップの重なりである。

| 層 | 分類 | 一次コード根拠 | 何が起きているか |
| --- | --- | --- | --- |
| 下層ランタイム | 既実装（ただし env 未読込・macOS 崖あり） | `amu/src/kotoba/compiler/runner_cap_dispatch.cljk:79` (`8,388,608`) / `amu/seed/76-x86_64-static-rt.kotoba:51,55` (`KXRT-POOL=256 MiB`, `KXRT-VALUE-MAX=8 MiB`) / `site/native/build_support.py:47,53` / `amu/seed/macos-fs2-runtime-packager.kotoba:5,365-399,582-614` / `io_buffer.kotoba:49-59` | Linux static-rt 自体は 8 MiB 単一値・256 MiB プールを保持。ただし現行自己ホスト `seed/76,77,82,macos` は **`KEXE_STRING_POOL` 環境変数を読まず**、macOS Mach-O は `vector-conj` `O(N²)`、`bytes-slice` コピー、wire 37 `stdout` 毎呼出プール割当、`io_buffer` `O(K³)`、プール枯渇時の生 `brk #0` (`SIGTRAP`) を抱える |
| 仕様・境界 | **3 者仕様ドリフト** | `kotoba/lib/kotoba.osaho/src/kotoba/kir/value.cljk:9-36`（2026-10-02 `amu` ADR 0362 で `:string`/`:bytes` 上限 **8 MiB** 引上げ済み）対 `lang/limits.edn` v1・`lang/guest-grammar.edn`（3 箇所）・`xml.cljk:438`・`core.cljk:752`・`nbb/cli.cljk:463`・`admission_test.cljk:176-227`・`ios-aot-artifact.edn:1`・`browser-host.mjs`・`kotoba_android_host.h:15`（`65536` 残存） | `osaho` 側のみ 8 MiB へ先行し、可搬プロファイル定数群とツールプロファイル上限の整理が未接続 |
| 言語／自己ホスト | **標準 O(N) API 不在・ABI 不整合・予算穴** | `lang/surface-status.edn:541-551`（アフィン `:linear-return` 未導入）／ `amu/seed/21-check.kotoba:7291,7293`（`string-substring` 自体は 0-pool-byte view だが、1 呼出 1–2 pair ハンドル消費・`seed/76,77,macos` の `O(N*M)` 検索・`seed/05-checked-source.kotoba:161-179,358` `dsb-op` 欠落による DefCID 不一致・`seed/12-kirread.kotoba:1939-1943` slot 272/344 静的拒否）／ `kotoba/lib/kotoba.osaho/src/kotoba/kir.cljk:4181-4182,4213-4215`（`string-fold-ascii`/`string-append-range` の `bounded-string!`/`charge-bytes!` 欠落） | `kxrt-concat` の tail-append は `string_pool_used` 最末尾のときしか効かず、途中に 1 回でも一時文字列を作ると以後は毎回 `O(N)` コピーへ転落する |
| アプリ実装 | **アプリ側負債** | `site/native/docs_page.kotoba:16-18` (`find` 残余コピー), `60-73` (`inline`), `104-132` (`markdown`), `160`（PR #897 L192: `(> (size source) 65536)`）／ `test/site/native/docs_page_file.kotoba:5-11`（1ページごとに全 14/15 章を読込＋SHA-256） | 64 KiB 仮ガードで隠れていた `O(N²)` 連結と `O(M² · N)` 冗長読込。並列 3 本で遅延確保ページが RSS 化し swap 圧迫を増幅 |

---

## 2. 標準 O(N) 償却テキストビルダー仕様（`lang/text-builder.edn` v1）

### 2.1 なぜ既存の `kxrt-concat` tail-append では不十分か

`amu/seed/76-x86_64-static-rt.kotoba:258-268` の `kxrt-concat` は、左辺 `a` が **文字列プール最末尾（`(- -1 oa) + la == string_pool_used`）に位置するときだけ** `a` の再コピーを省く。しかし現実のテンプレート・マークダウン変換（`docs_page.kotoba` の `inline` / `markdown`）では、`out` に追記する**合間に** `trim` / `esc` / `href` / `cells` 等の一時文字列を必ず生成する。その瞬間に `string_pool_used` が前進し、`out` は二度とプール最末尾に戻れず、以後の `string-concat` はすべて `O(|out|)` バイトコピー＋回収不能プール消費（＝ `O(N²)`）へ転落する。

### 2.2 2 段階パス：Tier 0（即時ポータブル暫定） vs Tier 1（KOTOBA-989 標準契約）

1. **Tier 0（Profile 6 互換・ABI/コンパイラ変更不要の暫定策、最終解ではない）**:
   - **2 進キャリーチャンク木**: `log2(K)` 本の均等長バケットで連結しコピー係数を `O(log N)` に抑える（時間・メモリとも `O(N log N)` であり、**`O(N)` とは名乗らない**）。
   - **境界チャンク `:io/write` 直接出力**: 完了ブロックごとに `<= 65,536` バイト単位で `:io/write`（または FS2 kind 10/11）へ流し、巨大単一 HTML 文字列を作らない。
   - **`string-index-of-from` への置換**: `find` の残余 `string-substring` コピーを廃止（ただし `docs_page.kotoba:17` の範囲外 `i` 境界ガードを残し、`Amu`/`AmuSpec` 側で `seed/05-checked-source.kotoba`・`seed/12-kirread.kotoba` の slot 272 低下と pair 消費・`O(N*M)` 検索を解決する）。
   - **macOS 前提条件**: macOS Mach-O で Tier 0 チャンク `:io/write` が線形に動くためには、`macos-fs2-runtime-packager.kotoba:365-399` の wire 37 毎呼出プール文字列割当・無バッファ出力と `io_buffer.kotoba:49-59` (`O(K³)`) の `Amu` 側修正が前提となる。
2. **Tier 1（Phase 2 `KOTOBA-989` 本命仕様：`:text-builder`）**:
   - **型**: 不透明・有界・単一スレッドアフィン型 `:text-builder`。意味モデルは純粋値 `{:max-bytes :i64, :utf8-bytes :bytes}`（常に正規 UTF-8）であり、等式推論を一切壊さない（ACL2 `stobj` と同原理）。
   - **アフィン所有権 + `:linear-return`（`lang/surface-status.edn:541-551` の解消）**:
     - **消費操作**: `text-builder-append`, `text-builder-append-range`, `text-builder-append-i64`, `text-builder-append-code-point`, `text-builder-finish`, `text-builder-finish-chunks`, `text-builder-finish-bytes-chunks`。
     - **非消費参照操作**: `text-builder-byte-length`, `text-builder-remaining`（消費前の生存ハンドルに対してのみ読み取り可。消費後参照は `:kotoba.error/affine-use`）。
     - **`:linear-return` 規則**: モジュール内部の `defn-` が `[b :text-builder ...]` を受け取り、全経路でアフィンにスレッドして `:text-builder`（または単一 `:text-builder` フィールドを持つ閉じたレコード）を返す場合、呼び出しグラフ不動点解析により `:linear-return` として受理し、呼び出し側は `(f b ...)` を消費→新ハンドル生成として扱える。これにより `markdown -> paragraph -> inline -> esc` を中間 HTML 文字列ゼロで貫通できる。
     - **禁止事項（コンパイル時拒否）**: 二重消費・消費後再利用（`:kotoba.error/affine-use`）、`fn` クロージャ捕捉やコンテナ格納・`arena-scope` 越え返却（`:kotoba.error/affine-escape`）、公開 `:export` 境界露出（`:kotoba.error/affine-export`）。早期エラー分岐での破棄（drop）は許可される。

### 2.3 操作一覧（全 10 操作）

| 操作 | シグネチャ | 意味・制約 | 主なトラップ |
| --- | --- | --- | --- |
| `text-builder-new` | `[:i64] -> :text-builder` | 上限 `max-bytes` の空ビルダーを生成。初期確保は極小（例 `min(max-bytes, 256)`）で遅延倍増し、`max-bytes` を事前確保しない | `:text-builder/invalid-limit`, `:text-builder/limit-exceeded` |
| `text-builder-append` | `[:text-builder :string] -> :text-builder` | `s` を末尾追記。`""` は O(1) no-op | `:text-builder/overflow`, `:utf8/invalid-byte` |
| `text-builder-append-range` | `[:text-builder :string :i64 :i64] -> :text-builder` | `s[start, end)` を**中間 `:string` ハンドル無し**で直接追記（`osaho` でも `bounded-string!` / `charge-bytes!` 必須） | `:substring/bounds`, `:substring/code-point-boundary`, `:text-builder/overflow` |
| `text-builder-append-i64` | `[:text-builder :i64] -> :text-builder` | `string-from-i64` と完全一致する 10 進 ASCII を一時文字列無しで直接書込（`-9223372036854775808` 含む） | `:text-builder/overflow` |
| `text-builder-append-code-point` | `[:text-builder :i64] -> :text-builder` | Unicode スカラー値 `0..0x10FFFF`（サロゲート除外）を正規 UTF-8 1..4B で直接書込 | `:utf8/surrogate`, `:utf8/above-max`, `:text-builder/overflow` |
| `text-builder-byte-length` | `[:text-builder] -> :i64` | 非消費 O(1) 現在バイト数参照 | `:trap/invalid-handle` |
| `text-builder-remaining` | `[:text-builder] -> :i64` | 非消費 O(1) 残余容量 `(- max-bytes used)` 参照 | `:trap/invalid-handle` |
| `text-builder-finish` | `[:text-builder] -> :string` | 消費して単一 `:string` を確定。有効プロファイルの `:string-value-max` 超過で拒否 | `:string/too-large`, `:budget/bytes` |
| `text-builder-finish-chunks` | `[:text-builder :i64] -> [:list :string]` | `max-chunk-bytes`（`4..string-value-max`）以下の**UTF-8 コードポイント境界分割**された非空チャンク列を返す（空ビルダーは空リスト） | `:text-builder/invalid-chunk-size`, `:budget/bytes`, `:budget/cells` |
| `text-builder-finish-bytes-chunks` | `[:text-builder :i64] -> [:list :bytes]` | FS2 kind-11 `byte-chunks-v1` 向け補助出力（`1..bytes-value-max`） | `:text-builder/invalid-chunk-size`, `:budget/bytes`, `:budget/cells` |

### 2.4 トラップ優先順位・計算量不変条件・ABI 境界・macOS 前提

1. **トラップ優先順位（4 段階厳守）**:
   1. ハンドル／コンテキスト ABI 妥当性（`:trap/invalid-handle`）
   2. 引数ドメイン・範囲・UTF-8 妥当性（`:text-builder/invalid-limit`, `:text-builder/limit-exceeded`, `:text-builder/invalid-chunk-size`, `:substring/bounds`, `:substring/code-point-boundary`, `:utf8/*`）
   3. **ビルダー論理上限**（`(+ used add-len) > max-bytes` → `:text-builder/overflow`）：ホストのプール残量より**必ず先**に検査し、全バックエンドで同一入力に対し同一トラップを保証する
   4. ホスト資源予算（`:string/too-large`, `:budget/bytes`, `:budget/cells`, `:budget/fuel`）— `kotoba.osaho` (`kir.cljk:4181-4182,4213-4215`) のような `bounded-string!` / `charge-bytes!` 迂回を禁止する。
2. **計算量・中間割当独立性**:
   - `K` 回の追記で `N` バイト生成時の総計算量と総コピー量は `O(K + N)` 償却。幾何倍増時コピー係数 `<= 3.0`、チャンクリスト確定時は `<= 1.0`。
   - 追記の合間に任意個の一時 `:string` / `:vector-i64` を確保しても `O(N)` が劣化しないこと（`string_pool_used` 最末尾依存の禁止）。
3. **ネイティブ ABI 境界と macOS 前提条件**:
   - Context ABI v11 スロット `0..384` は**変更しない**。専用のバージョン付き ABI 拡張（v13+）または独立ビルダーアリーナとして `AmuSpec` と整合させる。
   - macOS Mach-O 適合の前提として、`Amu` 側で `vector-conj`（`macos-fs2-runtime-packager.kotoba:595-614`, `O(N²)`）、`bytes-slice`（`L582-591`, コピー→view）、wire 37 `stdout`（`L365-399`, 毎呼出プール文字列確保廃止）、`io_buffer.kotoba:49-59`（`O(K³)`）、およびプール枯渇時の `brk #0`（`SIGTRAP`）→ `KEXE_TRAP` + exit `120` 統一が必須となる。

---

## 3. ネイティブ Docs・テキスト処理ツールの入力サイズ／メモリ予算方針（`lang/text-tool-budgets.edn` v1）

### 3.1 4 層上限分離モデル（恣意的 64 KiB ハードコードの廃止）

1. **言語静的リテラル層（`:language/static`）**: `string-literal-bytes = 65,536`（プログラム内定数のみ。実行時文書上限ではない — `lang/guest-grammar.edn:175`）。
2. **ポータブル値層（`:language/value`）**: `string-value-bytes = 65,536` / `document-utf8-bytes = 65,536`（`lang/limits.edn` v1。なお `kotoba.osaho/value.cljk:9-36` は既に `8,388,608` へ引上げ済みという 3 者ドリフトを記録）。
3. **ネイティブ実行天井（`:native-runtime-ceiling`）**: `8,388,608` バイト（`8 MiB`, `KXRT-VALUE-MAX` / `MAX-FS2-VALUE` / `KEXE_BYTES_VALUE_LIMIT`）および `268,435,456` バイト（`256 MiB`, Linux static-rt 固定値 `KXRT-POOL`；現行自己ホストは `KEXE_STRING_POOL` 環境変数を未読込であり、macOS は枯渇時生 `brk #0`）。
4. **ツールプロファイル予算（`:tool-profiles`）**:
   - **`:docs-static-renderer`（`site/native/docs_page.kotoba` 等）**:
     - 単一入力上限: **既定 `1,048,576` bytes（1 MiB）**、最小下限 `524,288`（512 KiB）、ハード天井 `8,388,608`（8 MiB）。
     - 単一出力上限: 既定 `4,194,304`（4 MiB）、ハード天井 `8,388,608`（8 MiB）、または `<= 65,536` bytes チャンクストリーム。
     - 必須対照テスト: `65,536` 超の正例（例 `131,072` bytes UTF-8 Markdown 通過）＋プロファイル上限 `+ 1`（`1,048,577` bytes）の fail-closed 拒否（`exit 7` / 空 stdout）。
   - **`:cli-text-tool`**: 単一 I/O 上限 `8,388,608`（8 MiB）。超過時は FS2 kind 10（`:string-byte-chunks-v1`）/ kind 11（`:byte-chunks-v1`）へ切替。

### 3.2 判断ゲート D1（`lang/limits.edn` v1 非破壊 vs v2 一斉引上げ）

| 選択肢 | 内容 | 影響・位置づけ |
| --- | --- | --- |
| **Option A（推奨・AmuSpec 賛成）** | `lang/limits.edn` v1（`:string-value-bytes = 65536`）を**維持**し、64 KiB 超の出力はポータブル層では `text-builder-finish-chunks` / チャンク `:io/write`、ネイティブ Docs/CLI ツールではツールプロファイル（1 MiB〜8 MiB）で扱う | `core.cljk:752`・`nbb/cli.cljk:463`・`admission_test.cljk:176-227`・`ios-aot-artifact.edn:1`・`browser-host.mjs`・`kotoba_android_host.h:15`・`value_bounds_agreement_test` を壊さない。オーナー裁定ゲートは `AmuSpec` Item `#593` へ集約 |
| **Option B（要横断調整）** | `lang/limits.edn` v2 で `:string-value-bytes` を `1,048,576`（1 MiB）または `8,388,608`（8 MiB）へ引き上げる | `kotoba-lang`, `kotoba` (`xml.cljk:438`), `kotoba-script`, `kotoba-verifier`, `amu` 一斉同期と `KEXE_STRING_POOL` / macOS `KEXE_TRAP` 整備が必須 |

### 3.3 メモリ・並列度ガードレール（M1–M3）

- **M1（目次メタデータと本文の分離）**: `test/site/native/docs_page_file.kotoba:5-11` のように 1 ページ描画ごとに全章（14〜15 本）の Markdown 本文を読み直して SHA-256 計算し `:document` へ積む構成（`:document-utf8-bytes = 65536` 違反かつ全体 `O(M² · N)`）を禁止する。軽量索引 `docs-index.edn`（`< 4 KiB`）をステージし、対象章のみを読み・検証する。
- **M2（線形プール消費・`arena-scope` 衛生）**: ページ単位のピーク string-pool 消費を Tier 1 で `<= 16*N + 256 KiB`（Tier 0 で `<= 64*N + 512 KiB`）に抑え、SHA-256 検証等の一時計算を `(arena-scope ...)` で回収する。
- **M3（ホスト並列度予算）**: `J = max(1, min(requested_jobs, floor(ram_budget_mib / measured_peak_rss_per_worker_mib)))` を強制し、コーディネータ側（Zone B `jacob`）の既定を `--jobs 1` とし、重量並列ビルドは Zone A（`21mbp`, `ops/host-topology.md`）へ逃がす。

---

## 4. 移行ガードレール・互換性・所有範囲

- **既存コードへの破壊的影響**: 本仕様 PR 自体は追加のみであり、既存 Profile 6 プログラム・DefCID・`lang/limits.edn` v1 を破壊しない。
- **PR #897 との棲み分け**: PR #897（16 言語版公開）には手を加えず、後続 PR として `site/native/docs_page.kotoba`・`test/site/native/docs_page_file.kotoba`・`site/native/docs_build.py`・`site/native/check_docs.py` を段階移行する。
- **`docs_page.kotoba` 移行時の注意（AmuSpec 指摘反映）**:
  1. `docs_page.kotoba:16-18` (`find`) を `string-index-of-from` へ置換しても、改行なしファイル末尾等で呼び出し側が渡す範囲外 `i`（`(+ end 1)`）で `-1` を返す境界ガード `(if (or (< i 0) (> i (size s))) -1 ...)` を**必ず保持**すること（外すとトラップ化する）。
  2. `docs_page.kotoba:9` の `(< c 65536)` は UTF-8 BMP（`U+10000`）の 3B/4B コードポイント幅判定であり、64 KiB バッファ上限ではないため**変更禁止**。
- **実装責任の切り分け**:
  - **KotobaSpec**: 仕様正本・適合契約（本 PR）
  - **AmuSpec / Amu**: `core.cljk:752`・`document-runtime.json:162-177` 整合、slot 272/344 の `seed/05` + `seed/12` 接続、`KEXE_STRING_POOL` 読込、macOS `vector-conj`/`bytes-slice`/wire-37/`io_buffer`/`KEXE_TRAP` 整備、Tier 1 ABI 拡張（v11 不変）
  - **Kotoba（実装）**: `kotoba-sema`（`:text-builder` 型・`affine.cljk` `:linear-return`）・`osaho`（`bounded-string!`/`charge-bytes!` 欠落防止含む）/`kotoba-verifier`・Wasm/ESM バックエンド・`site/native/*` 移行

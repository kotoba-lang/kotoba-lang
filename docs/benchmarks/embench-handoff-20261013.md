# Embench 引き継ぎ — 2026-10-13、停止中

高速化のゴールは **paused・未達**。この文書と公開更新は再開の指示ではない。
人間の明示的な再開指示と新しい予算条件があるまで、コンパイラ生成・最適化・性能測定を開始しない。
今回の公開作業は記録の整理のみで、新たな候補の採用はない。

## 採用済みの基準

- Amu main: `74b082f534d6326a5d5df9a0f415be1425b85aa6`、[PR #1295](https://github.com/kotoba-lang/amu/pull/1295)。認証済みスカラー呼び出し2回をまたぐディスクリプタ再利用。
- その前の汎用バイトルックアップ: [PR #1293](https://github.com/kotoba-lang/amu/pull/1293)、`9bdff08f95effda253a08b2eed190efe98af55fc`。
- 従来の採用済み一式と歴史的コミット一覧: [統合記録](https://github.com/kotoba-lang/amu/blob/74b082f534d6326a5d5df9a0f415be1425b85aa6/docs/evidence/adopted-seed-integration-20261012.md)、[採用履歴](https://github.com/kotoba-lang/amu/blob/74b082f534d6326a5d5df9a0f415be1425b85aa6/docs/evidence/native-selfhost-paused-20261012/adopted-commits.txt)。一覧中の実験ブランチを丸ごとmainへマージしない。採用済み製品の識別は現在のソース・画像のハッシュを使う。
- 採用済みunity SHA256: `55d901e250da78ee71fa02b2eae9734569a74ccb7c6a5e434416e515794ebac5`。
- 採用済みnative SHA256: `446af9f37538cc37e57cb6133ab8ee2a3400cb001dccfd9e34d5da8e1909f76a`。
- 最新の全19本比較はRound85、Kotoba時間/Cの幾何平均 `2.9542453261184902`。上流の速度ドライバ・式を用いた翻訳プログラムの探索的測定であり、認証済み公式Kotoba Embenchスコアではない。1より大きく、全体ではCが速い。
- [Round85全試行](https://kotoba-lang.org/benchmarks/embench-resume-20261013.json)。Round83/84/85のJSONは測定時点の履歴で、現在の作業状態は[work-status](https://kotoba-lang.org/benchmarks/embench-work-status-20261013.json)に分離する。

## 未採用候補と復元

[保存manifest](embench-candidates-20261013/manifest.json)が7版のソースサイズ・ハッシュ・差分を持つ。
基準v3はAmuの固定コミット `f2d607bee3a9f5dea6f54a21fd71ea3cabde10fa` の `seed/MANIFEST` に並ぶ16ファイルを順番に連結し、**各ファイルの後にLFを1つ追加**したunity。
そのSHA256は `4360c0b88c38e3f2d32f3ed717ff1b8e3ce35a6ab51e1af635676ffba6c14e97`。
コミット時点の `candidate-v3.json` は古い途中経過で、後の測定完了を反映しない。今回の候補レビューを現在の記録として使う。

| 候補 | 状態・結果 |
| --- | --- |
| v1 | 汎用2引数の初期版。既存のphase/table最適化と干渉。未採用。 |
| v2 | phase経路を維持。table経路との干渉が残る。未採用。 |
| v3 | phase/table経路を維持。SGは個別2組とも速いがPicoは混在。全体改善は未確認。未採用。 |
| v4 | source fuel箇所数を3以上に限定。SGの改善を確認できず。未採用。 |
| v5 | source call数を2以下に限定。全39画像が採用済み版と同一。測定せず。未採用。 |
| v6 | source命令数96以上に限定。SGは個別2組とも遅い。未採用。 |
| v7 | v3に、BL/MOV9/LDR8/CMP/CSETの正確な条件下でコピー除去を追加。動作・物理検証が進んだが、正式検査と全19本C比較・採用判断は未完了。 |

固定した基準unityも `v3-unity.kotoba.gz` として同じリポジトリへ保存した。次のスクリプトは圧縮元のハッシュと復元後の候補ハッシュを検証し、7候補のソースをこの保存資料から復元できる。生成先は専用scratch内の新しいファイルを指定する。

```sh
python3 docs/benchmarks/embench-candidates-20261013/reconstruct.py \
  --manifest docs/benchmarks/embench-candidates-20261013/manifest.json \
  --candidate v7 \
  --output /path/to/new-task-workspace/recovered-v7.kotoba
```

この処理はソースを復元するだけで、コンパイラや候補を実行しない。全6差分について、基準から復元したソースが元の候補と完全一致することを公開準備時に確認した。

## 最新v7の証拠境界

- unity SHA256: `ff6523711c6e37345fa75524d8d0e4f228d8dee4a99bffe59b36bf6b1d9f7f0f`。
- 安定native SHA256: `c0687a55b1f264eebb546c15f24f09a34f1036c646636e58a71e9b70063e459c`。G2/G3/G4はnativeとcontainer双方が一致。
- 全39画像を初期・安定世代で各78 compile/extractツールから生成。安定画像は初期画像およびv3と全体一致。新しい速度測定は不要な同一プログラム画像だが、これで全体改善を主張しない。
- 元の95 profile +76 low-fuel +312 interaction =483の完全な生観測は、v3の実行記録を完全なnative/container/source/export一致で引き継ぐ。v7の新規483回実行とは区別する。
- 新しい比較コピー除去の陽性fixtureは実際に1命令減少。10燃料段階・80組（160実行）で結果、fuel、全17 arenaカウンタが一致。安定版fixture画像も初期画像と完全一致。
- 自己ビルドの全命令・参照・pool・export対応、キャッシュ/一時レジスタのCFG、121の冗長contextロード、74のcached rootでのcontext読取りを検証。mapping-onlyの途中結果や以前の失敗を最終合格と取り違えない。
- [候補レビューと個別測定](https://kotoba-lang.org/benchmarks/embench-candidate-review-20261013.json)に結果・ハッシュを保存。v3/v4/v6のON/OFFは候補/採用済み版であり、Cを分母にした数値ではない。
- 保存した[検証ソース](embench-candidates-20261013/verification-tools/README.md)は元の配置と固定依存が必要で、単独で実行できるツールとは扱わない。

## ローカル証拠の所在

既存のgithubルート配下 `workspaces/codex/` に次の専用ディレクトリがある。生成物・完全な生出力・失敗試行は移動/削除せず保持している。

| ディレクトリ | 内容 |
| --- | --- |
| `embench-pf-twoarg-20261013/` | 7版のソース、39画像、483記録、generic fixture、自己ビルド、命令/CFG監査。 |
| `embench-pf-twoarg-priority-v3-fixed4-20261013/` | v3の固定4順序測定、回収、独立レビュー。 |
| `embench-pf-twoarg-multi-charge-v4-fixed4-20261013/` | v4の測定と全生記録。 |
| `embench-pf-twoarg-body-size-v6-fixed4-20261013/` | v6の測定と全生記録。 |
| `embench-total-C-round85-20261013-v2/` | 最新採用済み版の全19本とCの測定、全試行。 |
| `embench-total-C-round52-20261011/continuation-checkpoint.json` | 停止状態と詳細checkpoint。巨大なので必要フィールドだけ読む。 |
| `embench-paused-handoff-publication-20261013/` | 今回のローカルHANDOFF、evidence-pins、公開/CI/デプロイ証拠。 |

公開リポジトリにあるのは候補ソース差分・要約・検証ソースであり、すべてのnative画像やrawログがリモート保存されたという意味ではない。別ホストから引き継ぐ場合、所有者の承認済み手段で不足するローカル証拠を取得し、ハッシュを検証する。古いdescriptor-witness/conditional-fallthrough資料とワークツリーも保持し、最新の採用済み2-call再利用と区別する。

## 再開を明示的に承認された場合だけ進めること

1. ゴール状態と新しい予算を確認。repoのAGENTS.md、現在のremote main、既存WIPを読み、候補を新しい専用worktreeで扱う。失敗したディレクトリや`started.json`を消して再実行しない。
2. manifestのソースと証拠のハッシュを再確認。v7の正式policy/public/source検査と追加generic境界・拒否ケースを行う。ネイティブ自己ビルドが必要で、JVM/GraalVM/Nodeのコンパイラ代替で合格にしない。
3. 元の19本・39画像・95+76+312ケース、full64の結果、fuel、全17カウンタを維持。小さなfixtureを全体の資格や速度と取り違えない。ホストloader/producerをguestの書込み範囲外に置く。
4. 新しい全19本のC比較を上流ドライバ・正規化に沿って実施し、候補と採用済み版の個別比較も区別する。測定前後のraw CPU idleと背景idle各70%以上、body0.5秒以上、最大3回のCPU条件のみの再試行、最初の合格標本、全試行保存。旧elapsedを埋め込まない、最速値を選ばない、同じ画像を都合のよい値が出るまで測り直さない。
5. 5%の一律足切りはない。小さい改善も正しさ・回帰・比較範囲を検証して採用判断する。合格した採用分だけmainへ統合し、未採用を混ぜない。公開はmain readback→build→dry-run→deploy→live確認まで行う。C以上を全体の証拠で示すまではゴールをcompleteにしない。

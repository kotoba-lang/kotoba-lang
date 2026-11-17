# KotobaとAwaiを試す前に：使い方、測定、経済圏を区別する

更新：2026年11月17日。公開一次資料をAIが確認して編集したレビュー用原稿です。OpenRouter無料モデルによる生成ではありません。この編集作業では製品の実行、性能の追試、社会的効果の測定をしていません。

## 読み方と索引

「実装」は公開ソースが記述する機能、「実測」は資料に残る特定条件の観測、「設計目標」は採用した方向、「未検証」は必要な証拠が揃っていない状態です。実装の存在から本番適格性を推測しません。

入口は「小さく試す」、OSの選択は「名前と現在の製品境界」、性能比較は「速度と安全性」、報酬の理解は「トークンの用途」、短い回答は「FAQ」、根拠は末尾の固定出典索引を参照してください。

## 名前と現在の製品境界

| 名前 | 役割と確認できる限界 |
|---|---|
| Kotoba | 開発中の現代的なLisp machineの中心となる言語。プログラムをデータとして扱い、組み合わせて理解できるシステムを目指す。Amuがコンパイル、Kototamaが実行契約、AIUEOSがOSの方向を担う。統合機械全体の出荷完了を意味しない。[S1] |
| Awai Network / Awai Node | ネットワークと参加ホスト。ノード登録は承認、ジョブ配置、有料実行、報酬の保証ではない。[S4] |
| Itonami | 業務意図、tenant、安定したjob ID、期限と予算を扱う層。Murakumoが配置・計算を担う。公開job契約にはItonamiの期限が渡らず、その期限だけで計算は停止しない。[S4] |
| Awai OSの現在の製品方向 | 11月17日の採用方針は、電話とデスクトップPCの主基盤をAndroid AOSPとする。既存NixOSと復旧手段を保持する。[S2,E5] |
| Itonami OS / AIUEOS Native | 既存ADRはAIUEOSのAwai distributionをItonami OSと命名する。これは独立したselfhost適格化の方向であり、新しいAOSP製品方針や既存NixOSメディアと区別する。[S6,E5] |

AIUEOSの旧リポジトリはshellで、本体・資料はKotobaの `lib/kotoba.aiueos` に移っています。古いURLの404だけを設計消滅の証拠にはしません。[S5]

### AOSP採用は、完成ROMの公開ではない

**設計目標：** OSのライフサイクル、権限、通信、画面ロック、Keystore、ファイル暗号化はAOSP側、機器固有対応はBSP、Awai UIはローカル側、ItonamiはAndroid adapterを持つアプリ側という境界です。GMS/Playの利用権は自動的には得られません。[E5]

**実装の記述：** Android開発用APKにはHome、Store、Yoteiがあります。HomeのHOME roleは既存launcherへ戻せます。Storeは署名済み同梱・オフラインを既定とし、package/version/signerの検査とAndroid自身のインストール確認を分けます。Yoteiはローカル永続カレンダーで、同期やremote inference、通知の完成を意味しません。[E6]

**未検証・未実装：** APK/VM/Liveの記録は本番ROM、永続6600H導入、署名OTA、実アカウント移行の合格証ではありません。HomeとItonamiのWebViewストアは別で、共通sign-in、暗号化profile転送、同期は未実装です。PINは本人性や操作権限の根拠ではありません。既存Nix領域、EFI、VMディスク、鍵、profile、cron、fleetを一括移行しません。[E5,E6]

## 小さく試す

Apple Silicon Macには単一ファイルの公開Amu入門があります。以下は、既存記事で確認した固定revisionの手順です。ガイド本文は今回の現行revisionでも同じですが、実行物の版を勝手に進めず、このrevisionのartifactと照合します。ここでは未実行です。[E9,S3]

```sh
git clone https://github.com/kotoba-lang/amu.git amu-mac
cd amu-mac
git checkout --detach 5c9aa0254bab5a23326823fcead72ec510e5715d
file -L bin/amu
shasum -a 256 bin/amu
codesign --verify --strict bin/amu
bin/amu check examples/foundation-starter/hello.kotoba
bin/amu compile examples/foundation-starter/hello.kotoba
bin/amu run examples/foundation-starter/hello.kotoba
bin/amu run ./hello
```

新しい作業ディレクトリで使います。固定ガイドのCLI SHA256は `016aaefcac44eff3408fb30283aa5ab1125fb0ff0298d56ea23df621d67fc837`。不一致なら停止して版と取得元を確認します。期待値はcheckがok、runが42・exit 0。返り値を43に変えた別ファイルを作って変化を説明し、既存出力名、未知flag、不適合main型の拒否も記録すると理解を確かめられます。公開CLI全体のrelease適格化とは別です。[E9]

Itonamiの実ジョブは、認可済みfleet、設定、予算を確認してから扱います。公開Murakumoのjobs submit/statusは一般的な無条件実行例ではありません。unknownのジョブを勝手に再送せず、同じ安定IDの状態とreceiptを確認します。[S4]

### 既存NixOS Otehon 0.7.3を扱う場合

これは保持されているNixOSメディアの説明で、新しいAOSP ROMの手順ではありません。文字版は番号選択、GUIはガイド記載のシリアル/ERASE承認を使います。保存先とバックアップを確認し、完了表示、全長読戻し、UEFI登録を含めて判断します。媒体の版・Build IDを照合し、旧版のUSB読戻し証拠を流用しません。[S2,E1,E2]

0.7.3のBuild IDは `a73c2f90a6307ce2cb580b0e6bf843161adaec295e72f4795f058c6cc1c2c364`、ISOは8,497,463,296 bytes、SHA256は `34b813c2ff6089b008d5901fac32c381c518f4514cd15c9173df0c7cf72ce704`。同一ISOのGUI/文字版VM検証は、物理6600H、電源断、Secure Boot、本番更新の合格とは別です。失敗時は版、処理名、診断コード、処理済み量、書込み開始表示を残します。診断追加から既報の実機OSError原因解消を推測しません。[E1,E2,E3]

## 成長、課題解決、社会的価値をどう測るか

個人の学習では、出力42→43の変化と拒否理由を自分の言葉で説明することを小さな課題にできます。「再現可能な入力・出力・権限を確認する練習が理解を助ける」は編集上の仮説です。前後の正答率、再現までの時間、拒否理由の説明の正確性で確かめます。改善率の実績は今回の資料からは確認できません。

組織では定型処理一件の所要時間と修正時間、失敗・unknown・重複作用の件数、追跡可能なreceiptの割合を導入前後で比較します。分散化の社会的価値も、可用性、参加機器費、電力、運用負担、アクセス格差を同時に測る必要があります。ノード数だけを社会貢献の証明にしません。これらは評価提案で、製品が既に達成した効果ではありません。

## 速度と安全性を条件付きで読む

**資料に残る実測：** Kotobaの10月15日Apple M4 Embench観測は19 workload、各一回、特定compiler snapshotでC実行時間の0.80倍。監査済みのアルゴリズム置換を含み、現行release全体や全機種の順位ではありません。[S1]

Murakumo READMEには11月15〜16日のMetal推論測定が加わりました。M1 Max 32GB、macOS 26.4の負荷がある共有ホストです。次は公開raw記録の値で、この原稿による追試ではありません。[S4,E7,E8]

| 測定 | Murakumoの観測 | 比較時の限界 |
|---|---|---|
| Qwen2.5-0.5B Q8_0、5-token prompt / 128 greedy decode、11月15日 | 3roundで292.6 / 292.3 / 294.3 tok/s | load 5.9–7.8。MLXは別の8bit変換と8/512-token prompt、llama.cppは空context。静穏ホストのAWAI-18 claim gateは未通過。[E7] |
| Qwen3.5-4B Q4_K_M、128 greedy decode、11月16日 | 3roundで49.9 / 41.2 / 44.0 tok/s | 比較時load 10.8–20.2、別sessionもGPU使用。llama.cppは同じGGUF、MLXは別4bit packで55.5 / 53.3 / 56.2。資料自身もMLXに未到達と記す。[E8] |

小モデルの結果を4Bへ移したり、greedyとspeculativeの速度を同じ順位表に並べたりしません。誤ったtokenを出すstubの時間も正しい推論の速度ではありません。Metal kernelは現段階で手書きMSLを含み、Amuによるkernel生成、完成した/v1 serving、speculative decodeまで完成したという意味ではありません。[S4,E7,E8]

**実装の記述と残る信頼基盤：** Amu入門はdigest照合、mainの型、再生成比較、子プロセス期限を記述しますが、一般package/resolver/Darwin runtime互換は未完了です。runtime、OS、鍵管理、artifact配布は信頼基盤に残ります。AOSPの権限機構、Storeの署名確認、ユーザーの操作ごとの同意も異なる境界です。debug署名を本番の鍵回転・失効運用と同一視しません。[S3,E6]

画面付きdappをMCP Apps型iframe・postMessage JSON-RPCと操作別同意で取り込む方針はありますが、取り込み実装は公開方針で未完了です。agent向けtoolのHermes mcp_servers経路とも区別します。これらから速度や安全性を絶対保証できません。[E4]

## トークンの用途と価値の成立条件

認証capability、サービス内credit、決済資産、取引可能な独自トークンを分けます。Murakumoの限定Base/x402 USDC経路と、nodeの獲得credit出金申請は別です。記載されたpilotでは申請がcreditを減算してoperator承認を待つ段階で、USDC送金完了ではありません。旧chain ID 260425は廃止済みです。[S4]

以下は経済分析上の条件整理です。持続的な用途には、実サービスで必要とされること、品質と供給能力、発行量・配分・手数料、償還条件、運用費、担保・不正時の扱いを検証できることが必要です。市場での売却価値には流動性、集中度、実際に売れる条件も関係します。利用が増えても供給や費用構造によって価値への影響は変わります。公開資料だけではAwai独自トークンの現行発行条件、稼働市場、取引量、保有者収益は確認できません。値上がり、収益、元本を保証しません。

## AIO用FAQ

### Kotobaを初めて試す入口は？
Mac ARM64の固定Amuガイドでcheck/compile/runを試し、期待出力と拒否を確認します。上記は資料の手順で、この編集では未実行です。[S3,E9]

### Awai OSはNixOS、AOSP、AIUEOSのどれ？
11月17日の主製品方針は電話/PCのAOSP。既存NixOSと復旧手段は保持し、AIUEOS Nativeは独立した適格化経路です。過去ADRのItonami OS名だけで現在の全実装を説明しません。[S2,S6,E5]

### Android APKがあれば本番ROMも使える？
いいえ。APK、VM、Live、永続導入、署名OTA、物理機器、実アカウント移行は別の受入です。Home/Itonami共通sign-inや暗号化profile同期も未実装です。[E5,E6]

### Itonamiの期限でMurakumoは必ず停止する？
公開job契約にはItonami期限が渡りません。resident/job設定のtimeoutとの違いを確認します。[S4]

### 292.6 tok/sなら全モデルで他engineより速い？
いいえ。小モデルの特定条件です。4Bの負荷下測定ではMLXに未到達で、静穏ホストのclaim gateも別です。[E7,E8]

### ノード登録だけで収益を得られる？
いいえ。admission、配置、実行、請求、支払をそれぞれ確認する必要があります。出金申請も送金完了ではありません。[S4]

### 分散化や安全設計は効果の保証？
いいえ。独立拠点での障害試験、外部依存、権限、OS/runtime/鍵管理と、利用者側の効果測定を分けて確認します。[S3,S4,E6]

### AIOは学習・検索掲載を保証する？
ここでのAIOは、定義、短い回答、条件、更新日、固定出典を読みやすく整える編集です。APIへ資料を送ることはupstreamモデルの学習、検索掲載、推薦を保証しません。非公開資料や秘密を送ってよいという意味でもありません。

## 固定出典索引

基本の公開6資料はモデル用入力と同じ構成です。E資料は編集校閲のみで、free-model runnerの入力に追加していません。private cloud-itonamiは使用していません。Itonamiの説明は公開Murakumo資料の範囲であり、非公開guideを読んだものではありません。

- **S1** [kotoba-lang/kotoba/README.md](https://github.com/kotoba-lang/kotoba/blob/6457359d367ee11d43dc6f755c57b3820ad1091c/README.md)

  commit `6457359d367ee11d43dc6f755c57b3820ad1091c`; SHA256 `9834361249aa4e357bef8da5f08e86911e70a8af97c1f9feaf98813ecc346574`

- **S2** [network-awai/awai-os/README.md](https://github.com/network-awai/awai-os/blob/ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee/README.md)

  commit `ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee`; SHA256 `0cacf262069ca1854af33fc5526fffd9318c2de5b2d6f45fd6b845f89a83e245`

- **S3** [kotoba-lang/amu/examples/foundation-starter/MACOS-ARM64.md](https://github.com/kotoba-lang/amu/blob/d121f176c2ff92b5699fa11e0ae891dca6fd7576/examples/foundation-starter/MACOS-ARM64.md)

  commit `d121f176c2ff92b5699fa11e0ae891dca6fd7576`; SHA256 `c69f17ea0c5fd08fec7497f981c23b405de1d6282464c5af1d5a6feb8e3b33ed`

- **S4** [kotoba-lang/murakumo/README.md](https://github.com/kotoba-lang/murakumo/blob/61eb58bfab3a1e74c3412f4b9cb66859990fa030/README.md)

  commit `61eb58bfab3a1e74c3412f4b9cb66859990fa030`; SHA256 `e690db6b935d669ba106e1602940367f923a3cdbd65ed094b91fd9b17d1ffad0`

- **S5** [kotoba-lang/aiueos/README.md](https://github.com/kotoba-lang/aiueos/blob/cf6c7ebb5c3c5759e4caf7d27fdee8309987b052/README.md)

  commit `cf6c7ebb5c3c5759e4caf7d27fdee8309987b052`; SHA256 `d2dfd1d50ae1ee27611ff99e65accf575a93e1e973edfcf6b106b66ecc0c7ddd`

- **S6** [kotoba-lang/kotoba/lib/kotoba.aiueos/90-docs/adr/0274-awai-os-is-an-aiueos-distribution.md](https://github.com/kotoba-lang/kotoba/blob/6457359d367ee11d43dc6f755c57b3820ad1091c/lib/kotoba.aiueos/90-docs/adr/0274-awai-os-is-an-aiueos-distribution.md)

  commit `6457359d367ee11d43dc6f755c57b3820ad1091c`; SHA256 `26c071fcb25ec17bca352e7583846ab715b87922f99b5369c02c9e8f56f4cadb`

- **E1** [network-awai/awai-os/docs/otehon/install-guide-ja.md](https://github.com/network-awai/awai-os/blob/ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee/docs/otehon/install-guide-ja.md)

  commit `ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee`; SHA256 `f399687b224f81c191cc348e05f109f5f5ebc0d194972bc20d5f3c993e2d4b64`

- **E2** [network-awai/awai-os/docs/otehon/usb-installer-0.7.3.md](https://github.com/network-awai/awai-os/blob/ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee/docs/otehon/usb-installer-0.7.3.md)

  commit `ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee`; SHA256 `4c9bde66113afc3bd6e65cc12612cb38b5823e5b74fc4bbd8b492d9ba5f37802`

- **E3** [network-awai/awai-os/docs/evidence/otehon-installer-release-0.7.3.json](https://github.com/network-awai/awai-os/blob/ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee/docs/evidence/otehon-installer-release-0.7.3.json)

  commit `ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee`; SHA256 `76f51bb160eb3063797dc841e23e6af3fd7a14e84a99fbfd2dbe92144e3677a7`

- **E4** [network-awai/awai-os/AGENTS.md](https://github.com/network-awai/awai-os/blob/ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee/AGENTS.md)

  commit `ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee`; SHA256 `0348219614cee01dc46a8fba4b577b0508c60fb8a147e948940d3a31d3c68be2`

- **E5** [network-awai/awai-os/docs/otehon/aosp-platform-decision-20261117.md](https://github.com/network-awai/awai-os/blob/ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee/docs/otehon/aosp-platform-decision-20261117.md)

  commit `ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee`; SHA256 `730200dfd2251cbaf1b2ded16a86d0a39e0b648e21f5d66253da2d52b28b4ddd`

- **E6** [network-awai/awai-os/android/README.md](https://github.com/network-awai/awai-os/blob/ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee/android/README.md)

  commit `ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee`; SHA256 `c9016ef4d9bfee0dec484de23c8c56dab49847c385dcd7410a0e4fecaf89ae22`

- **E7** [kotoba-lang/murakumo/bench/results/2026-11-15-e2-decode/README.md](https://github.com/kotoba-lang/murakumo/blob/61eb58bfab3a1e74c3412f4b9cb66859990fa030/bench/results/2026-11-15-e2-decode/README.md)

  commit `61eb58bfab3a1e74c3412f4b9cb66859990fa030`; SHA256 `78d19ad7fdacb1849e5c0ee6ade0a4071092b895b46f79e93118507fa86867dd`

- **E8** [kotoba-lang/murakumo/bench/results/2026-11-16-qwen35-decode-3/README.md](https://github.com/kotoba-lang/murakumo/blob/61eb58bfab3a1e74c3412f4b9cb66859990fa030/bench/results/2026-11-16-qwen35-decode-3/README.md)

  commit `61eb58bfab3a1e74c3412f4b9cb66859990fa030`; SHA256 `727845cd912b0a8b52fc30e29addc0e3dbf9748103695175707aec40916417aa`

- **E9** [kotoba-lang/amu/examples/foundation-starter/MACOS-ARM64.md](https://github.com/kotoba-lang/amu/blob/5c9aa0254bab5a23326823fcead72ec510e5715d/examples/foundation-starter/MACOS-ARM64.md)

  commit `5c9aa0254bab5a23326823fcead72ec510e5715d`; SHA256 `c69f17ea0c5fd08fec7497f981c23b405de1d6282464c5af1d5a6feb8e3b33ed`

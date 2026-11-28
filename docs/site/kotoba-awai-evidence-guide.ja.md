# KotobaとAwaiを試す前に：使い方、測定、経済圏を区別する

更新：2026年11月28日。公開一次資料をAIが確認して編集したレビュー用原稿です。OpenRouter無料モデルによる生成ではありません。この編集作業では製品の実行、性能の追試、社会的効果の測定をしていません。

## 読み方と索引

「実装」は公開ソースが記述する機能、「実測」は資料に残る特定条件の観測、「設計目標」は採用した方向、「未検証」は必要な証拠が揃っていない状態です。実装の存在から本番適格性を推測しません。

入口は「小さく試す」、OSの選択は「名前と現在の製品境界」、性能比較は「速度と安全性」、報酬の理解は「トークンの用途」、短い回答は「FAQ」、根拠は末尾の固定出典索引を参照してください。

## 名前と現在の製品境界

| 名前 | 役割と確認できる限界 |
|---|---|
| Kotoba | 開発中の現代的なLisp machineの中心となる言語。プログラムをデータとして扱い、組み合わせて理解できるシステムを目指す。Amuがコンパイル、Kototamaが実行契約、AIUEOSがOSの方向を担う。統合機械全体の出荷完了を意味しない。[S1] |
| Awai Network / Awai Node | ネットワークと参加ホスト。ノード登録は承認、ジョブ配置、有料実行、報酬の保証ではない。[S4] |
| Itonami（営） | 11月22日の公開方針ではbot実行基盤と、その上のbots。既存Murakumo資料の業務意図・tenant・安定job ID・予算/期限という役割も、個別job契約の範囲で読む。Murakumoの公開job契約にはItonami期限が渡らない。[E4,E17,S4] |
| Manimani / Awai Home | Manimaniは営の上の既定bot、Homeは対話を中心とするOS入口という方針。Home内の対話表示方式やOS操作権限の分離には提案・未検証事項が残る。音声/記憶/推論やapp間MCPが全て接続済みという意味ではない。[E4,E17] |
| Itonami dapp | bot profileを持ち、wallet・DID・自律稼働の要件を満たすという公開用語上の区分。名称だけで、そのbotの常駐・権限・wallet・実稼働を証明しない。[E4] |
| Awai app | botを持たないTauri app。MCP/APIを出しても、それだけではItonami dappにならない。区分変更は操作同意や鍵保管の契約を緩めず、既存package IDやItonami Store名を一括改名する指示でもない。[E4] |
| Awai OSの現在の製品方向 | 電話とデスクトップPCの主基盤はAndroid AOSP。Meijiは製品codename。NixOS desktop導入経路はdeprecatedで、過去資料はarchive。独自の署名済みAOSP ROMやOTAの公開完了とは別。[S2,E5,E10,E11,E12] |
| Itonami OS / AIUEOS Native | 既存ADRはAIUEOSのAwai distributionをItonami OSと命名する。これは独立したselfhost適格化の方向であり、新しいAOSP製品方針や既存NixOSメディアと区別する。[S6,E5] |

AIUEOSの旧リポジトリはshellで、本体・資料はKotobaの `lib/kotoba.aiueos` に移っています。古いURLの404だけを設計消滅の証拠にはしません。[S5]

### AOSP採用は、完成ROMの公開ではない

**設計目標：** OSのライフサイクル、権限、通信、画面ロック、Keystore、ファイル暗号化はAOSP側、機器固有対応はBSP、Awai UIはローカル側、ItonamiはAndroid adapterを持つアプリ側という境界です。GMS/Playの利用権は自動的には得られません。[E5]

**実装の記述：** Android開発用APKにはHome、Store、Yoteiがあります。HomeのHOME roleは既存launcherへ戻せます。Storeは署名済み同梱・オフラインを既定とし、package/version/signerの検査とAndroid自身のインストール確認を分けます。Yoteiはローカル永続カレンダーで、同期やremote inference、通知の完成を意味しません。[E6]

11月22日の責務整理では、旧Core / Apps / System構成を今後も必ず保持するという方針が撤回され、OS設定はAndroid/Awai OS、個別appはHome/Storeから扱う方向です。責務文書全体は提案を含み、同日追記のowner整理と未決定の実装案を分けて読みます。Homeへの対話画面の埋込み、端末内MCP transport、権限分離やAndroid実機動作の案を、完成した利用手順へ置き換えません。[E4,E17]

**未検証・未実装：** APK/VM/Liveの記録は本番ROM、署名OTA、全機種での永続導入、実アカウント移行の合格証ではありません。HomeとItonamiのWebViewストアは別です。短命challengeと暗号化profile転送のコードは追加されていますが、現在のSetupは従来のaccount linkを呼び、PCのprofile転送は利用不能と表示する段階です。共通ストア同期や実アカウント移行の完成とは扱いません。[E23,E24,E30] PINは本人性や操作権限の根拠ではありません。Nix desktopの旧ファイル整理方針も、partition初期化や鍵・現Androidデータの消去許可ではありません。既存Node/fleetの運用とは別です。[E5,E6,E12]

### PCの事前導入と、既定Homeの復旧を分ける

11月25日付の現行入力記録では、PCイメージへ事前導入するAwai appはHomeとStoreだけで、YoteiはStoreから導入する構成です。patchはoperatorが用意した両APKと期待signerを検査し、通常は欠落・不一致で止めます。開発用の検証省略・appなしbuildは明示指定で、release用には拒否する条件があります。入力コードの追加を、署名済みイメージの配布や実機事前導入の成功とはしません。[E45,E46]

既定Homeの入力はHomeを通常appのままpriority 0、Launcher3のHOME filterを-1とするものです。資料は、Homeの無効化・削除と「install済みだが起動できない」を分け、後者ではrole holderが残るため自動復旧を期待せず、Settingsの既定Home選択からLauncherへ戻す経路を案内しています。これは資料に記録されたplatform挙動の説明で、この編集では当該固定platform sourceを取得できず独立確認していません。fresh dataでの選択、再起動後の保持、fallbackの実動作も未検証です。この原稿でもdevice・data partition・roleを操作していません。[E45,E47]

### アカウント承認とデータ引継ぎを分ける

**実装の記述：** 11月23日の公開sourceには、端末・有効期限・選択scopeを結び付ける短命challenge、署名の検証、ECDH/HKDF/AES-GCMによる転送envelopeが加わりました。資格情報の名前を持つfieldを拒否する契約もあります。QR・Passkeyによる承認だけで、profileや認証情報がコピーされるわけではありません。[E23,E25]

**接続と未検証の範囲：** `profile:settings` / `profile:home-state` のready判定は、要求scopeが列挙集合に含まれるかの判定です。Androidの転送entryは復号後にcompletedを設定しますが、ここにはprofileを永続storeへ適用する処理を確認できません。Setupはまだ`createIdentity`を使い、転送entryを呼んでいません。ready/completedという内部状態だけを、利用者データの復元成功と読み替えません。WNFS snapshotとYotei eventsは利用不能扱いで、Home APK全体・実電話承認・再起動後の持続・機器ごとのhardware custodyは残る受入です。[E23,E24,E25,E30]

利用するときはaccount link、選択データの転送、転送先への適用、再起動後のreadbackを別々に確認します。現時点のPC設定画面では「あとで設定」の経路を使える設計ですが、これもこの原稿で実機を操作した結果ではありません。[E23,E30]

### PC用OTAの応答と、OS更新の成功を分ける

公開記録は11月24日のOTA feed Worker配置とHTTP 200の`[]`応答を報告しています。現行の`INDEXES`も空で、対象pathへ200が返ることは更新packageの提供・署名検証・適用成功の証拠になりません。この編集環境からの同じpathのGETはHTTP 403で、記録された200を追認できていません。source実装、他sessionの配置記録、今回のreadbackを区別します。[E27,E28,E31]

PCのRAM Live、既存diskへの永続install、A/B OTAは別の受入です。11月24日追記のowner方針は既存NVMeを消去・切詰めず、512-byte logical sector・64 GiB以上の第2diskを使うものです。これは準備条件で、導入完了の記録ではありません。既存データを破壊する旧手順を通常の試用コマンドとして実行しません。[E26]

### Androidローカル推論の取消は、受付と停止を分ける

`network.awai.murakumo`はAndroid Otehonアプリで、Kotoba/Amu NativeのMurakumo engineとは別です。`packages/awai-inference`には呼出し元へrequest IDと結果Promiseを直ちに返す`startGeneration(options)`、同じclientからの`cancel(requestId)`が加わりました。利用には対応するnative adapter、package/signatureの適格性、生成ごとの確認が必要です。appのinstallやreadiness応答だけでモデルload済みとは判断しません。[E40,E41]

**実装の記述：** SDKの`cancelled:true`は取消を受け付けた応答です。結果Promiseの`inference-cancelled`拒否まで待って、Activityの終了と次の生成を扱う契約です。未開始・終了済み・他request・再取消は`not-running`として区別します。Android adapterは保持中のrequest IDを検査し、Activityはworkerの結果を確定してからbusyを解放します。受付応答を、即時のworker停止やメモリ解放の証拠と読み替えません。[E41,E42,E44]

**制約・未検証：** 画像経路のhash読取は最大1 MiB単位で取消を確認しますが、blockしたread自体の戻りは待ちます。固定engineの`sd_ctx_create`は初期化完了までcontextを公開しないため、その最中の取消はreturn後に生成を飛ばしてcleanupする経路です。生成開始後の協調取消と同じ遅延上限にはしません。Androidでのphase別取消時間と、取消後の次生成成功は公開READMEでpendingです。この編集でも実行・追試していません。画像backendや全consumerのrelease適格化を、このAPI追加だけから主張しません。[E40,E43]

ローカル推論、外部からのモデル取得、別appへの結果受渡し、画像保存・分散同期は別の経路です。INTERNET権限がないという条件だけで、他appへのIPCや端末侵害も含めた情報流出ゼロを保証しません。機密情報を扱う場合はrelease artifact、ログ・画面保護、許可された受渡し先を、その経路で確認する必要があります。[E40,E41]

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

### Androidを試す経路と、旧NixOS資料の扱い

現在の公開BOOTガイドは次の四経路を分けています。いずれも開発・engineering evidenceで、一般向けAwai OS imageのreleaseではありません。[E11]

| 経路 | 実際の基盤と制約 |
|---|---|
| Emulator | 固定Google SDK API35。app開発・受入用で、PC diskへ入れるOS imageではない。 |
| PC LAN Live | 固定された非公式PC Live image。RAM上のデータは再起動で消える。特定Desktop向けのengineering手順。 |
| PC NVMe persistence | 同じ基盤のdata/metadataを既存NVMe上のext4 imageに保存。資料は11月17日の特定Desktopでの再起動保持を報告。kernel/initrdはまだMacから供給し、この基盤ではAndroid FBEが使えない。 |
| Phone | テスト機の既存community ROM上にAPKを入れ、Homeを明示選択する。既存launcherをrollback用に残す。Awai製ROMのflashではない。 |

PCガイドの対象は固定Ryzen 7 7735HS Desktopです。他PCや以前の6600H受入へ一般化しません。BOOTガイドは独自source-pinned AOSP image、公開digest/署名、機種別installer、signed OTA、PCのhardware-backed key/FBEとMacなしbootを未完了と明記します。署名されたderived development imageも製品releaseとは別です。[E11]

11月17日の追加決定でNixOS desktop導入経路はdeprecatedとなり、旧sourceと記録はarchiveへ移動しました。E1〜E3の0.7.3資料は固定commitの歴史的証跡として残しますが、現在のAwai OS導入手順にはしません。機器固有の旧Nix整理runbookは、同じ外側partitionに現Android imageがあること、暗号化退避・依存監査・隔離・再起動受入後に限定した旧ファイルだけを扱うことを明記します。これは整理実行済みの証拠ではありません。この編集では機器の変更・消去を行っていません。[S2,E1,E2,E3,E12]

## 成長、課題解決、社会的価値をどう測るか

### 手元で整理する仕事から始める

Android READMEにある開発用アプリを使うなら、まず自分の入力と判断を整理する小さな課題を選べます。次の使い方は編集上の提案で、利用者の成果を測った実績ではありません。Storeへの登録条件や開発APKの記述は、全端末への一般配布・実運用の合格とは別です。[E6]

| 課題の例 | 資料が記述する機能と試す範囲 | 境界 |
|---|---|---|
| 面談の準備 | Deaiに自分で入力した候補を整理し、比較理由を言葉にする | 端末内のdraft/rankingで、利用者間の連絡を提供しない。 |
| 自分の応募・自社採用の確認 | Hiringでdraftの内容とbody digestを照合し、承認か理由付き差戻しを判断する | appは外部応募・求人投稿・送信を実行しない。採用成果の保証ではない。 |
| 公開情報の見直し | Kumadeの宣言済みCommon Crawl読取りと、端末内index/searchで根拠を探す | 読取りごとに同意が必要。capture service停止中は任意URL取得・crawlが無効。 |
| 自分の送金指示の事前確認 | Payment Draftsで自分の指示を下書きし、XML previewと対象subsetのL1構文検査を確認する | 端末内の下書き・レビュー。銀行への送信・署名・支払いを行わず、銀行の受理は未検証。[E21] |
| 自分の証券決済・保有記録の確認 | Securities Draftsで自分の決済指示を下書きし、読込んだ保管明細と自分の保有数量を照合する | 発注・送信・署名・資産移動を行わない。照合結果は入力された記録の比較で、残高や決済完了の証明ではない。[E22] |

アプリのsource・Android host・build・test・package identity・releaseは各owning repository、Awai OSはOS統合、発見、Store catalogと署名検証を担うという責務分担です。リンク先やsource移動だけからAPK配布・既存データを保つupgradeの完了を推測しません。[E15]

11月23日のStore buildにはPayment Drafts／Securities DraftsのAPK取込み入口が加わっています。package・signer・40 MiB上限と権限を検査し、INTERNETを要求するものを拒否するコードを確認しました。取込みコードの存在を、署名済み一般配布や全端末の受入とは扱いません。[E6,E20]

両appでの `approved` は利用者が下書きをレビューしたという状態です。取引実行を意味しません。公開資料が記述する検査は、モデル化されたXML subsetのL1までで、業務慣行・usage guidelineのL2、銀行・保管機関・決済システムの受理のL3は別です。読めない保存データをread-onlyにする保護も記述されていますが、この編集では実データの入力、下書き承認、金融機関への送信、署名や資産移動を実行していません。[E21,E22]

11月20日確認の所有権資料では、Yoteiの製品側は `network-awai/app-yotei`、Android IDは `network.itonami.yotei`。Awai OS側にはHome agenda consumerとStore検証が残ります。旧in-tree buildを退役させる条件は、互換性のある署名済みAPKのStore readbackと、同じsignerでの更新が既存agenda・Home表示を保つ受入です。リポジトリ移動を理由に手元のカレンダーを消したり、移行完了と判断したりしません。この資料は移行条件であり、この記事による端末受入の結果ではありません。[E15]

個人の学習では、出力42→43の変化と拒否理由を自分の言葉で説明することを小さな課題にできます。「再現可能な入力・出力・権限を確認する練習が理解を助ける」は編集上の仮説です。前後の正答率、再現までの時間、拒否理由の説明の正確性で確かめます。改善率の実績は今回の資料からは確認できません。

組織では定型処理一件の所要時間と修正時間、失敗・unknown・重複作用の件数、追跡可能なreceiptの割合を導入前後で比較します。分散化の社会的価値も、可用性、参加機器費、電力、運用負担、アクセス格差を同時に測る必要があります。ノード数だけを社会貢献の証明にしません。これらは評価提案で、製品が既に達成した効果ではありません。

### x402 Payで、支払条件の下書きを確かめる

**実装：** x402 PayはBase Sepoliaの支払条件JSONを端末内で作るアプリです。金額は小数6桁・1,000 USDまで、保存は50下書きまで。`PLANNED_OPERATIONS`の受信・wallet署名・verify/settleは将来項目で、runtimeの外部operationは空です。JSONができても実際の請求・USDC送金・収益は発生しません。[E37,E38,E39]

**資料に残る実機観測：** 11月25日のmoto g05記録はdebug署名APKについて、保存した3下書きがforce-stop後も残り、未保存formのreloadが保存済み下書きを壊さず、金額・桁数・resource・保存数の4上限に理由を表示したと報告しています。入力はADB/CDP経由で、物理tapの試験ではありません。起動失敗を実機で発生させた証拠はなく、正常起動の記録を障害表示の合格にはしません。本番署名、Store配布、全端末、TalkBack、実決済の受入とも別です。この編集では実機やwalletを操作していません。[E36]

学習や業務設計には、同じ条件をJSONに表し、拒否理由と保存状態を説明できるか確認する用途があります。これは提案する使い方で、利用者の成長・売上改善を測った結果ではありません。[E37,E38]

## 速度と安全性を条件付きで読む

**資料に残る実測：** Kotobaの10月15日Apple M4 Embench観測は19 workload、各一回、特定compiler snapshotでC実行時間の0.80倍。監査済みのアルゴリズム置換を含み、現行release全体や全機種の順位ではありません。[S1]

Murakumo READMEには11月15〜16日のMetal推論測定が加わりました。M1 Max 32GB、macOS 26.4の負荷がある共有ホストです。次は公開raw記録の値で、この原稿による追試ではありません。[S4,E7,E8]

| 測定 | Murakumoの観測 | 比較時の限界 |
|---|---|---|
| Qwen2.5-0.5B Q8_0、5-token prompt / 128 greedy decode、11月15日 | 3roundで292.6 / 292.3 / 294.3 tok/s | load 5.9–7.8。MLXは別の8bit変換と8/512-token prompt、llama.cppは空context。静穏ホストのAWAI-18 claim gateは未通過。[E7] |
| Qwen3.5-4B Q4_K_M、128 greedy decode、11月16日 | 3roundで49.9 / 41.2 / 44.0 tok/s | 比較時load 10.8–20.2、別sessionもGPU使用。llama.cppは同じGGUF、MLXは別4bit packで55.5 / 53.3 / 56.2。資料自身もMLXに未到達と記す。[E8] |

**新しい実装・観測：** Qwen3.5にはMTP/DFlash draftを検証してplain greedyと同じ出力を得る実験経路が加わりました。「speculative未実装」という以前の説明は更新します。DFlash軽量cycleの資料では、30-tokenのthink開始prompt/256生成で12.3–12.4 ms/token、plain17.0–20.7。一方34-tokenのempty-think chat/128生成では18.2–18.7で、plain16.8–17.0より遅い結果です。load8–15のbuild比較で、他engineとの同時刻比較ではありません。[S4,E14]

さらにdraft headを65536+36 tokenへ絞った資料では、think promptで11.4–14.8 ms/token、empty-think chatでは16.8–17.1（別roundで19.8）。plainの値はround1–3、新headの表はround4–7で、後者のloadは8–14です。全buildがplainと同じtokenを出したとの記録と、別compilerで行ったgateを、同一compilerによる性能再現と混同しません。READMEの要約値よりこの詳細表の条件を優先し、改善倍率や「常に速い」を導きません。[E13]

小モデルの結果を4Bへ移したり、greedyとspeculativeを同じ順位表に並べたりしません。誤tokenを出すstubの時間も正しい推論速度ではありません。手書きMSL、未完成の/v1 serving、静穏ホストclaim gateは残ります。実験decodeの追加は、全serving pathへの統合やrelease資格を意味しません。[S4,E7,E8,E13,E14]

**実装の記述と残る信頼基盤：** Amu入門はdigest照合、mainの型、再生成比較、子プロセス期限を記述しますが、一般package/resolver/Darwin runtime互換は未完了です。runtime、OS、鍵管理、artifact配布は信頼基盤に残ります。AOSPの権限機構、Storeの署名確認、ユーザーの操作ごとの同意も異なる境界です。debug署名を本番の鍵回転・失効運用と同一視しません。[S3,E6]

### CID・重複排除は、必ず容量や通信を節約するわけではない

11月24日の公開測定は、macOS 26.3.1/arm64・Python 3.9.6・10 CPUのホストで、repositoryの120 JSON・4,027,805 bytesを処理した記録です。ネットワーク通信を行った試験ではなく、保存バイトとgit履歴からの差分転送量の計算です。[E32,E33,E34]

| 観測対象 | 資料に残る値 | 読み取れる限界 |
|---|---|---|
| 同じJSONのkey順・indent変更 | 240/240 digest一致 | このcanonicalizerの対象入力での観測。全Kotoba formや全実行環境の同値性ではない。[E33] |
| corpus重複排除 | 固定4 KiBで0.51%、content-defined chunkingで2.42%削減 | 資料の20%削減目標には未達。CIDを持つだけでは容量削減を保証しない。[E32,E33] |
| 約52 KBのbuild.py、15版、512-byte固定blockとの比較 | 各履歴差分のfull比率の平均：固定73.1%、content-defined 12.5% | ファイル・変更内容・分割方式に依存する。総量比率や実ネットワーク速度と混同しない。[E34] |
| 約5.7 KBの設定JSON、8版、4 KiB block | 同じ平均比率：固定100.0%、content-defined99.9% | 小さい文書ではほぼ節約がない。[E33] |

この4軸の観測は研究draftで、独立reviewer・human research-lead admissionはpendingです。候補数学体系の実装性能、CPU/GPU性能、全体の安全性、社会効果の測定ではありません。未測定軸をsimulatorの順位で埋めません。この原稿でも追試していません。[E32,E35]

### Storeの更新判定・署名実装と、本番運用を分ける

公開READMEは自動更新を段階実装中とし、現在は適格性の判定のみ、呼出し接続はまだなく、各installは人へ確認すると説明します。`UpdatePolicy`もI/Oやinstallを行わない判定で、同じpackage/signer、新しい版、検証済みcatalog/digest、新権限・外部操作なし、予算・承認期限などを確認します。判定が真でも、更新が実際に自動適用された証拠ではありません。[E6,E19]

署名側にはnon-debuggableのrelease buildと、rootが署名するcatalog/pointer鍵リストの経路が加わりました。現行buildコードはAPKとcatalogに同じkeystoreパス・aliasを設定することを拒否し、catalogは列挙された役割鍵、通常の同梱APKは設定されたAPK signerを照合します。これは本番鍵の発行・保管・rotationや端末更新受入が完了した意味ではありません。別signerのStoreを既存development Storeへそのまま更新できるとも考えません。[E6,E20]

鍵リスト設計には、一覧を受信できない間は失効が伝わらないこと、端末時計への依存、埋込rootを一覧だけでは更新できないことが残ります。Java/JSのvector一致やthrowaway-key testの記録は、その条件の実装検証であり、全端末・本番配布・紛失復旧の保証ではありません。この編集では秘密鍵へのアクセス、署名、APK buildや配布を実行していません。[E18]

11月23日のdevelopment Storeはrootをsequence 2へ入れ替え、新catalog r114とpointer sequence 5を記録しています。旧rootを埋め込んだbuildは新しい一覧を`keys-signature`で拒否し、同梱の旧一覧を使い続けるため、development buildの再構築・再installが必要と説明されています。これは既存installが新rootを自動信頼するという意味ではありません。APK署名の互換性やデータ保持を含む実端末更新は別途確かめます。developmentの再root化を本番rotationの受入証拠にもしません。[E29]

画面付きdappをMCP Apps型iframe・postMessage JSON-RPCと操作別同意で取り込む方針はありますが、取り込み実装は公開方針で未完了です。agent向けtoolのHermes mcp_servers経路とも区別します。これらから速度や安全性を絶対保証できません。[E4]

### 「静けさ」は、証拠条件を持つ製品原則

11月19日確認のAwai READMEは、Awai OS・Awai Network・Murakumoの共通原則として「静けさ」を掲げています。同じREADMEは明示的に、これらを証拠gateのある方向性であり、release済み保証ではないとします。以下の確認案は編集上の評価提案で、ここで実施した試験ではありません。[S2]

| READMEが掲げる方向 | 何を区別して確認するか |
|---|---|
| private message/mail/post/fileのE2E暗号化 | 送受信端末での鍵管理、平文を扱う境界、復旧・失効、metadata漏えいをそれぞれ試験する。暗号化blockの保存だけで全通信のE2E完成としない。 |
| OSとdefault appの広告・広告追跡なし | 対象buildのSDK、広告identifier、実際の通信を調べる。OS/default appの原則を、関連する全サイトや外部サービスに広げない。 |
| 他の参加者には仮名、home IPを非開示 | 相手peer・relay・運用者・攻撃者の観測範囲を分け、IP/metadataを試験する。責任主体を限定case processで追跡する方向は、無条件の匿名性とは別。 |
| 端末内state・local inference既定と外部操作別同意 | 使用したapp/model/経路で、拒否時の通信ゼロ、同意対象の限定、外部providerへ渡すデータを確認する。local既定だけから全操作offlineとはしない。 |

公開storage契約は、AwaiFS/WNFSのprivate snapshot、Kotobaseの関係・派生index、Yataverseの暗号化block、Networkの転送・保持を別の責務として記述します。CID照合は内容の整合性、操作権限は誰が何をできるか、復号鍵は誰が平文を読めるか、保持・復元は後で取り出せるかという別の確認です。受領receiptを独立replicaや災害復旧の完成へ読み替えません。現行bounded owner-storeとWNFS移行も別のwire profile・readback/release証拠を持ちます。これは公開契約を読んだ説明で、リンク先のprivate実装を検証したものではありません。[E16]

## トークンの用途と価値の成立条件

認証capability、サービス内credit、決済資産、取引可能な独自トークンを分けます。Murakumoの限定Base/x402 USDC経路と、nodeの獲得credit出金申請は別です。記載されたpilotでは申請がcreditを減算してoperator承認を待つ段階で、USDC送金完了ではありません。旧chain ID 260425は廃止済みです。[S4]

以下は経済分析上の条件整理です。持続的な用途には、実サービスで必要とされること、品質と供給能力、発行量・配分・手数料、償還条件、運用費、担保・不正時の扱いを検証できることが必要です。市場での売却価値には流動性、集中度、実際に売れる条件も関係します。利用が増えても供給や費用構造によって価値への影響は変わります。公開資料だけではAwai独自トークンの現行発行条件、稼働市場、取引量、保有者収益は確認できません。値上がり、収益、元本を保証しません。

## AIO用FAQ

### Kotobaを初めて試す入口は？
Mac ARM64の固定Amuガイドでcheck/compile/runを試し、期待出力と拒否を確認します。上記は資料の手順で、この編集では未実行です。[S3,E9]

### Awai OSはNixOS、AOSP、AIUEOSのどれ？
主製品方針は電話/PCのAOSP。NixOS desktop導入経路はdeprecated、旧資料はarchiveです。鍵や現Androidデータの保持、既存Node/fleet運用、AIUEOS Native資格は別の境界です。[S2,S6,E5,E12]

### Android APKがあれば本番ROMも使える？
いいえ。APK、VM、Live、永続導入、署名OTA、物理機器、実アカウント移行は別の受入です。引継ぎの暗号処理・検証コードは加わりましたが、Setupへの転送接続、storeへの適用、実機受入、Home/Itonami同期の完成は別です。[E5,E6,E23,E24,E30]

### ローカル推論でcancelled:trueなら、すでに計算が止まった？
いいえ。取消受付の応答と、worker cleanup後の結果Promise拒否は別です。画像初期化の同期call中はreturnを待つため、即時停止や一定秒数以内の停止を保証しません。request IDを保ち、結果の終端を確認します。[E40,E41,E42,E43,E44]

### AwaiのPC installerやA/B OTAは全く存在しない？
現行BOOT資料はUSB/PXE installerとtest-keyのKVM/VM A/B記録を区別して掲載しています。release鍵のROM/OTA、永続installの起動成功、SettingsのAwai OS表示は未完了です。RAM Liveを永続導入、test-key記録をreleaseと読み替えません。[E11]

### PCイメージ入力にYoteiがないのは、不具合？
現行の構成はHome・Storeだけを事前導入し、YoteiはStoreから扱います。ただしイメージ入力、実build、起動、Storeからのinstall受入は別で、まだ一括して合格とはされていません。[E45,E46]

### Homeが起動しなければLauncher3へ自動で戻る？
保証されません。公開入力記録の調査では、Homeが資格を失う場合と、資格を持ったまま起動に失敗する場合が異なります。後者の復旧案内はSettingsでの既定Home変更であり、対象イメージ・実機でのfallback確認は残っています。[E45,E47]

### Itonamiの期限でMurakumoは必ず停止する？
公開job契約にはItonami期限が渡りません。resident/job設定のtimeoutとの違いを確認します。[S4]

### 292.6 tok/sなら全モデルで他engineより速い？
いいえ。小モデルの特定条件です。4Bの負荷下測定ではMLXに未到達で、静穏ホストのclaim gateも別です。[E7,E8]

### ノード登録だけで収益を得られる？
いいえ。admission、配置、実行、請求、支払をそれぞれ確認する必要があります。出金申請も送金完了ではありません。[S4]

### Torihikiのdevnetは独自トークンの実市場の証拠？
いいえ。Android READMEのTorihikiは単一sequencer、裏付けのないdevnet collateralを使う開発経路で、real assetsではありません。別のTorihiki Bot appはread-only monitorで注文・鍵・faucet・bot起動を持ちません。UIやsigned transactionの実装から、償還性、流動性、実取引収益を推測しません。この編集では取引・署名を実行していません。[E6]

### 分散化や安全設計は効果の保証？
いいえ。独立拠点での障害試験、外部依存、権限、OS/runtime/鍵管理と、利用者側の効果測定を分けて確認します。[S3,S4,E6]

### AIOは学習・検索掲載を保証する？
ここでのAIOは、定義、短い回答、条件、更新日、固定出典を読みやすく整える編集です。APIへ資料を送ることはupstreamモデルの学習、検索掲載、推薦を保証しません。非公開資料や秘密を送ってよいという意味でもありません。

### Itonami dappとAwai appは何が違う？
公開用語の基準はbot profileの有無です。Itonami dappにはwallet・DID・自律稼働の要件が加わります。Awai appはbotなしのTauri appで、MCP/APIを持つ場合もあります。名称やcatalog登録は実稼働・権限の証拠ではありません。[E4]

### Storeの更新判定やrelease署名があれば自動更新・本番配布も完成？
いいえ。判定とinstallへの接続、署名役割と本番鍵運用、署名済みartifactと実端末更新は別です。公開READMEは自動更新判定の未接続と、人によるinstall確認を明記しています。[E6,E18,E19,E20]

### 引継ぎのready/completedなら、全データを復元できた？
いいえ。scopeの適格判定、暗号envelopeの復号、永続storeへの適用、再起動後の確認は別です。現行Setupは転送entryに未接続で、WNFS/Yotei scopeも利用不能です。実機での復元完了は確認されていません。[E23,E24,E25,E30]

### OTA feedが200ならOS更新を受け取れる？
いいえ。現行sourceのindexは空で、`[]`は提供offerがない応答です。適合する署名済みpackage、実端末での検証・適用・再起動・rollbackの受入が必要です。[E27,E28,E31]

### 古いdevelopment Storeは新rootへ自動移行する？
公開資料では旧rootのbuildはsequence 2を拒否します。新rootを含む互換buildへの更新が必要で、catalogだけで信頼rootを差し替えることはできません。[E29]

### x402 Payの実機記録は、決済の成功を示す？
いいえ。記録はdebug APKの下書き保存・拒否表示などです。外部operationはなく、送信・署名・settleは将来項目。保存済み下書きの保持と実際の資金移動を区別します。[E36,E37,E38,E39]

### CIDと分散保存なら、通信費・保存費を必ず減らせる？
いいえ。今回の120 JSONでは削減は最大2.42%。差分転送量も小文書ではほぼfullのままです。実データ、分割方式、履歴、保持・復元の費用を測って判断します。[E32,E33,E34]

### Payment DraftsのapprovedやL1合格は、送金・決済完了？
いいえ。approvedは端末内のレビュー記録、L1は対象XML subsetの構文検査です。両Drafts appは外部操作を宣言せず、送金・発注・署名・資産移動を行いません。L2の業務慣行とL3の金融機関受理は別の検証です。[E21,E22]

### 「静けさ」があれば誰にも追跡されない？
いいえ。READMEは証拠gateのある設計方向です。仮名、peerへのIP非開示、責任主体を追跡する限定case process、運用者や端末侵害に対する保護は別です。全経路の匿名性やE2E完成を保証しません。[S2]

### 検索結果はいつのファイルを見ている？
公開storage契約ではindexを開いたsnapshotとKotobase basisに対応づけます。新root取込み中の古いindexを「最新結果」として返さず、既知basisか利用不能を示す方針です。実appがその契約を満たすかはsnapshot readback・index再構築・通信拒否・復元の受入で確かめます。[E16]

### Yoteiが別リポジトリへ移れば、予定は自動で引き継がれる？
リポジトリ移動だけでは証明できません。package ID・signer・storage互換、Store readback、同じsignerでの更新、既存agendaとHome projectionの保持を確認します。公開所有権資料は旧build退役前の条件としてこれらを挙げています。[E15]

## 固定出典索引

基本の公開6資料はモデル用入力と同じ構成です。E資料は編集校閲のみで、free-model runnerの入力に追加していません。private cloud-itonamiは使用していません。Itonamiの説明は公開Murakumo資料の範囲であり、非公開guideを読んだものではありません。

- **S1** [kotoba-lang/kotoba/README.md](https://github.com/kotoba-lang/kotoba/blob/bab28344825c8a3f179f3d6217572eb778edf4e6/README.md)

  commit `bab28344825c8a3f179f3d6217572eb778edf4e6`; SHA256 `82c85bb4cba20afa640f5c1511b08ffcbaec8ee18b8187590ca86df50c51255e`

- **S2** [network-awai/awai-os/README.md](https://github.com/network-awai/awai-os/blob/f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13/README.md)

  commit `f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13`; SHA256 `fd96b165957dd308f5ad674f36057187bc7855d2432c49e593d6136cd6bbe1f1`

- **S3** [kotoba-lang/amu/examples/foundation-starter/MACOS-ARM64.md](https://github.com/kotoba-lang/amu/blob/d121f176c2ff92b5699fa11e0ae891dca6fd7576/examples/foundation-starter/MACOS-ARM64.md)

  commit `d121f176c2ff92b5699fa11e0ae891dca6fd7576`; SHA256 `c69f17ea0c5fd08fec7497f981c23b405de1d6282464c5af1d5a6feb8e3b33ed`

- **S4** [kotoba-lang/murakumo/README.md](https://github.com/kotoba-lang/murakumo/blob/69927a5f67f9641f81e3d27065dbf27d9c3fd026/README.md)

  commit `69927a5f67f9641f81e3d27065dbf27d9c3fd026`; SHA256 `2d2aad87cbff62c3302ab9523bdf3c2da546643f7b263be128584050f45848aa`

- **S5** [kotoba-lang/aiueos/README.md](https://github.com/kotoba-lang/aiueos/blob/cf6c7ebb5c3c5759e4caf7d27fdee8309987b052/README.md)

  commit `cf6c7ebb5c3c5759e4caf7d27fdee8309987b052`; SHA256 `d2dfd1d50ae1ee27611ff99e65accf575a93e1e973edfcf6b106b66ecc0c7ddd`

- **S6** [kotoba-lang/kotoba/lib/kotoba.aiueos/90-docs/adr/0274-awai-os-is-an-aiueos-distribution.md](https://github.com/kotoba-lang/kotoba/blob/44c9df8e83d1d00fbe4e91e5ae86bd7851420614/lib/kotoba.aiueos/90-docs/adr/0274-awai-os-is-an-aiueos-distribution.md)

  commit `44c9df8e83d1d00fbe4e91e5ae86bd7851420614`; SHA256 `26c071fcb25ec17bca352e7583846ab715b87922f99b5369c02c9e8f56f4cadb`

- **E1** [network-awai/awai-os/docs/otehon/install-guide-ja.md](https://github.com/network-awai/awai-os/blob/ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee/docs/otehon/install-guide-ja.md)

  commit `ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee`; SHA256 `f399687b224f81c191cc348e05f109f5f5ebc0d194972bc20d5f3c993e2d4b64`

- **E2** [network-awai/awai-os/docs/otehon/usb-installer-0.7.3.md](https://github.com/network-awai/awai-os/blob/ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee/docs/otehon/usb-installer-0.7.3.md)

  commit `ea69f795c7bbbc0f0eba436f41c4f4b8423a34ee`; SHA256 `4c9bde66113afc3bd6e65cc12612cb38b5823e5b74fc4bbd8b492d9ba5f37802`

- **E3** [network-awai/awai-os/docs/evidence/otehon-installer-release-0.7.3.json](https://github.com/network-awai/awai-os/blob/f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13/docs/evidence/otehon-installer-release-0.7.3.json)

  commit `f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13`; SHA256 `76f51bb160eb3063797dc841e23e6af3fd7a14e84a99fbfd2dbe92144e3677a7`

- **E4** [network-awai/awai-os/AGENTS.md](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/AGENTS.md)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `f355a88004df226a7e819325fb7797863d0cafda03fdb20be6be5780da86c848`

- **E5** [network-awai/awai-os/docs/otehon/aosp-platform-decision-20261117.md](https://github.com/network-awai/awai-os/blob/f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13/docs/otehon/aosp-platform-decision-20261117.md)

  commit `f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13`; SHA256 `0299ceaeb5b07e2623c2f22b5f055570de2a5427d833379115c413d792ea8cb9`

- **E6** [network-awai/awai-os/android/README.md](https://github.com/network-awai/awai-os/blob/747614b769ad70f5e5cdb24cec634393c591c761/android/README.md)

  commit `747614b769ad70f5e5cdb24cec634393c591c761`; SHA256 `ea3e6b9ee3a4f82246dbe5f5b527395cde797cf673c31d5d5779049008258669`

- **E7** [kotoba-lang/murakumo/bench/results/2026-11-15-e2-decode/README.md](https://github.com/kotoba-lang/murakumo/blob/69927a5f67f9641f81e3d27065dbf27d9c3fd026/bench/results/2026-11-15-e2-decode/README.md)

  commit `69927a5f67f9641f81e3d27065dbf27d9c3fd026`; SHA256 `78d19ad7fdacb1849e5c0ee6ade0a4071092b895b46f79e93118507fa86867dd`

- **E8** [kotoba-lang/murakumo/bench/results/2026-11-16-qwen35-decode-3/README.md](https://github.com/kotoba-lang/murakumo/blob/69927a5f67f9641f81e3d27065dbf27d9c3fd026/bench/results/2026-11-16-qwen35-decode-3/README.md)

  commit `69927a5f67f9641f81e3d27065dbf27d9c3fd026`; SHA256 `727845cd912b0a8b52fc30e29addc0e3dbf9748103695175707aec40916417aa`

- **E9** [kotoba-lang/amu/examples/foundation-starter/MACOS-ARM64.md](https://github.com/kotoba-lang/amu/blob/5c9aa0254bab5a23326823fcead72ec510e5715d/examples/foundation-starter/MACOS-ARM64.md)

  commit `5c9aa0254bab5a23326823fcead72ec510e5715d`; SHA256 `c69f17ea0c5fd08fec7497f981c23b405de1d6282464c5af1d5a6feb8e3b33ed`

- **E10** [network-awai/awai-os/release.json](https://github.com/network-awai/awai-os/blob/f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13/release.json)

  commit `f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13`; SHA256 `9eb9fd3c70c9192c74a36890e2d74de2076d5648f92c13c3b868d3d79d6f3753`

- **E11** [network-awai/awai-os/android/BOOT.md](https://github.com/network-awai/awai-os/blob/e0b6e7689b9ebaee106fec39d2dc13863b834647/android/BOOT.md)

  commit `e0b6e7689b9ebaee106fec39d2dc13863b834647`; SHA256 `64da6d1a2242678693e793efdd727988f810c032ef66d1a61b50349f436bb9bf`

- **E12** [network-awai/awai-os/docs/otehon/nixos-route-deprecation-20261117.md](https://github.com/network-awai/awai-os/blob/f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13/docs/otehon/nixos-route-deprecation-20261117.md)

  commit `f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13`; SHA256 `d0a2b84586ab9f49e56e008ca03969e022f1ca0555da2e1fc353857062506dd3`

- **E13** [kotoba-lang/murakumo/bench/results/2026-11-17-qwen35-dhead/README.md](https://github.com/kotoba-lang/murakumo/blob/69927a5f67f9641f81e3d27065dbf27d9c3fd026/bench/results/2026-11-17-qwen35-dhead/README.md)

  commit `69927a5f67f9641f81e3d27065dbf27d9c3fd026`; SHA256 `6058a8fc5b6d9dc9358081d45c88272e5dd0c25ea1d362877fdf1e5c66362ae8`

- **E14** [kotoba-lang/murakumo/bench/results/2026-11-17-qwen35-dflash-2/README.md](https://github.com/kotoba-lang/murakumo/blob/69927a5f67f9641f81e3d27065dbf27d9c3fd026/bench/results/2026-11-17-qwen35-dflash-2/README.md)

  commit `69927a5f67f9641f81e3d27065dbf27d9c3fd026`; SHA256 `ac35ad244981676b4fde98d4a736d17d5152d6bf6bff194ef3f805e010fd1955`

- **E15** [network-awai/awai-os/docs/app-ownership.md](https://github.com/network-awai/awai-os/blob/0497dc6d8dafc0cf7a701ae3b8ddb4cdf0b2c192/docs/app-ownership.md)

  commit `0497dc6d8dafc0cf7a701ae3b8ddb4cdf0b2c192`; SHA256 `e271e4a96d9e8abff8bc98c5dd37c5e43989b6e29fc4dd1564a608ef55c7cd50`

- **E16** [network-awai/awai-os/docs/otehon/app-storage.md](https://github.com/network-awai/awai-os/blob/f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13/docs/otehon/app-storage.md)

  commit `f5b3fbe6f9d83ea2642a6f8b02f94b1edabf2b13`; SHA256 `307964393c72da0ca544c3826d859d5142424e26361f060c5ad21e03921a42e0`

- **E17** [network-awai/awai-os/docs/otehon/home-itonami-manimani-responsibilities-20261122.md](https://github.com/network-awai/awai-os/blob/be937cdae28b93046a47d939cb5263647fe457c7/docs/otehon/home-itonami-manimani-responsibilities-20261122.md)

  commit `be937cdae28b93046a47d939cb5263647fe457c7`; SHA256 `84c5c979974a6a799e1629006ec3fccbfb6edab52de62b4387a6c18b581f50fd`

- **E18** [network-awai/awai-os/docs/otehon/itonami-store-key-list-design-20261122.md](https://github.com/network-awai/awai-os/blob/be937cdae28b93046a47d939cb5263647fe457c7/docs/otehon/itonami-store-key-list-design-20261122.md)

  commit `be937cdae28b93046a47d939cb5263647fe457c7`; SHA256 `1bccf8ebecb5175a047b7680b241aa523c181de0ed1c860da3252862e39dbb6a`

- **E19** [network-awai/awai-os/android/shared/src/network/awai/platform/UpdatePolicy.java](https://github.com/network-awai/awai-os/blob/be937cdae28b93046a47d939cb5263647fe457c7/android/shared/src/network/awai/platform/UpdatePolicy.java)

  commit `be937cdae28b93046a47d939cb5263647fe457c7`; SHA256 `8de6a73caf8f6814ae1b78d399fb1494ad701275f3df0f54dc8e6fac756151e2`

- **E20** [network-awai/awai-os/android/build.py](https://github.com/network-awai/awai-os/blob/832599c9f640d9743075b6bcff6dc788afd9e3be/android/build.py)

  commit `832599c9f640d9743075b6bcff6dc788afd9e3be`; SHA256 `8251dfdc6d9fc6869998466019f5500350ebebdcfce02394a4bd7b33bd545b4a`

- **E21** [network-awai/awai-os/apps/payment-drafts/README.md](https://github.com/network-awai/awai-os/blob/747614b769ad70f5e5cdb24cec634393c591c761/apps/payment-drafts/README.md)

  commit `747614b769ad70f5e5cdb24cec634393c591c761`; SHA256 `e273dc3b164b3891c12473c452782de84d676ddfe07d86aa6a0261ee63d75a3f`

- **E22** [network-awai/awai-os/apps/securities-drafts/README.md](https://github.com/network-awai/awai-os/blob/747614b769ad70f5e5cdb24cec634393c591c761/apps/securities-drafts/README.md)

  commit `747614b769ad70f5e5cdb24cec634393c591c761`; SHA256 `ed770a7e1d66468ccd72b5589cd5d1b2d048159d560054770312c14b518ac3f1`

- **E23** [network-awai/awai-os/docs/otehon/account-handover-awai4071.md](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/docs/otehon/account-handover-awai4071.md)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `8275b52fa39dc93c4e1375d039af9646ceabe7001a8162286d9caf1c2e9383a7`

- **E24** [network-awai/awai-os/android/client/handover-entry.js](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/android/client/handover-entry.js)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `ddffd531e653638ce829d1afee9fbb1fd9a945bf7a6727f773487e46f9d4cd9e`

- **E25** [network-awai/awai-os/otehon/shell/src/identity/handover.js](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/otehon/shell/src/identity/handover.js)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `cfb9e74df82cb005e4864f64714dfc6049cab5f2e311b14ad8ca20016993880e`

- **E26** [network-awai/awai-os/docs/otehon/7735h-measured-status-20261123.md](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/docs/otehon/7735h-measured-status-20261123.md)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `5c38eac464b0d6da400635552aaae90f7f3bc267323a91bffbf01257b778813b`

- **E27** [network-awai/awai-os/deploy/ota-feed/README.md](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/deploy/ota-feed/README.md)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `874db06db0fc209e0e6b8fa50d954680c01bda033f9d7b6341d674aa8cbf100e`

- **E28** [network-awai/awai-os/deploy/ota-feed/src/indexes.js](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/deploy/ota-feed/src/indexes.js)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `88aced9f3c0c8fc17f1a79ef9640e559a699389fb3864967ec4c6d0d54609fd1`

- **E29** [network-awai/awai-os/store/keys/README.md](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/store/keys/README.md)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `937304b4f94dd9722fc49f7e167a87dcfd183edecd401e40ff0063323ee253f5`

- **E30** [network-awai/awai-os/android/client/setup.js](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/android/client/setup.js)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `47214b77eca9c570862268deb45ad9fa80f188162b8369393716dd8460a6c6f7`

- **E31** [network-awai/awai-os/deploy/ota-feed/src/worker.js](https://github.com/network-awai/awai-os/blob/1723881c81ba13f3d6fb74533c3440fc72c25cdd/deploy/ota-feed/src/worker.js)

  commit `1723881c81ba13f3d6fb74533c3440fc72c25cdd`; SHA256 `9249f4d3b44741399918628b6320bec930cf85ab65165c4b7a2086b4a509d550`


- **E32** [network-awai/awai-os/docs/research/measured-baseline.md](https://github.com/network-awai/awai-os/blob/531e754e5b7dbf1962003e9a492dbcce37641e07/docs/research/measured-baseline.md)

  commit `531e754e5b7dbf1962003e9a492dbcce37641e07`; SHA256 `8a1065f85c5d13b94c2b093a4a42db7697a45c68f1e24e0f54fd3cbeaa587ce2`

- **E33** [network-awai/awai-os/docs/research/measurement/measure-out-b4096.json](https://github.com/network-awai/awai-os/blob/531e754e5b7dbf1962003e9a492dbcce37641e07/docs/research/measurement/measure-out-b4096.json)

  commit `531e754e5b7dbf1962003e9a492dbcce37641e07`; SHA256 `d1bf64254eea297deace1c15f7a5b7a79a9beefeb1db6be0b11f7bb08994fa4d`

- **E34** [network-awai/awai-os/docs/research/measurement/measure-out-b512.json](https://github.com/network-awai/awai-os/blob/531e754e5b7dbf1962003e9a492dbcce37641e07/docs/research/measurement/measure-out-b512.json)

  commit `531e754e5b7dbf1962003e9a492dbcce37641e07`; SHA256 `a2ef67fc1dc2d1a47a623a8621145a7d97e9dbbea00540ce282c7de7881ab34e`

- **E35** [network-awai/awai-os/docs/research/measurement-draft-claims.md](https://github.com/network-awai/awai-os/blob/531e754e5b7dbf1962003e9a492dbcce37641e07/docs/research/measurement-draft-claims.md)

  commit `531e754e5b7dbf1962003e9a492dbcce37641e07`; SHA256 `b887070f09e6ebace4a64a1cc644c79d2f519b1ec86db5e131922ae5aebdd6a1`

- **E36** [network-awai/awai-os/docs/evidence/x402-20261125/README.md](https://github.com/network-awai/awai-os/blob/531e754e5b7dbf1962003e9a492dbcce37641e07/docs/evidence/x402-20261125/README.md)

  commit `531e754e5b7dbf1962003e9a492dbcce37641e07`; SHA256 `dc8f85a9e5f65ae6e9a1c53cfb55e4d310b735251cfdcd40a69900499e802847`

- **E37** [network-awai/awai-os/apps/x402/README.md](https://github.com/network-awai/awai-os/blob/531e754e5b7dbf1962003e9a492dbcce37641e07/apps/x402/README.md)

  commit `531e754e5b7dbf1962003e9a492dbcce37641e07`; SHA256 `d10f4b3ec2b59272e41ddc3a90d7bae0be1506bcb28372984b18f4eb6c3234ee`

- **E38** [network-awai/awai-os/apps/x402/src/model.js](https://github.com/network-awai/awai-os/blob/531e754e5b7dbf1962003e9a492dbcce37641e07/apps/x402/src/model.js)

  commit `531e754e5b7dbf1962003e9a492dbcce37641e07`; SHA256 `4bdd1e1544960f80937de553eae4ee390cb5d78ae2a4949d96c654d791935068`

- **E39** [network-awai/awai-os/apps/x402/src/app.js](https://github.com/network-awai/awai-os/blob/531e754e5b7dbf1962003e9a492dbcce37641e07/apps/x402/src/app.js)

  commit `531e754e5b7dbf1962003e9a492dbcce37641e07`; SHA256 `4c40a63422e1f2ab85d623191c078003e75294224a6dafca7efd315c20206a4d`


- **E40** [network-awai/awai-os/apps/murakumo/README.md](https://github.com/network-awai/awai-os/blob/e0b6e7689b9ebaee106fec39d2dc13863b834647/apps/murakumo/README.md)

  commit `e0b6e7689b9ebaee106fec39d2dc13863b834647`; SHA256 `2d50fc59db0983b28ccad48deaf4a45a20886d0abe47a4e6ec39b1fd2cf03e04`

- **E41** [network-awai/awai-os/packages/awai-inference/README.md](https://github.com/network-awai/awai-os/blob/e0b6e7689b9ebaee106fec39d2dc13863b834647/packages/awai-inference/README.md)

  commit `e0b6e7689b9ebaee106fec39d2dc13863b834647`; SHA256 `516d19eb522aabbcfc7af067cd026043667ab64dc8513cec5ea9e031f5b6bab0`

- **E42** [network-awai/awai-os/packages/awai-inference/android/network/awai/inference/InferenceClientPlugin.kt](https://github.com/network-awai/awai-os/blob/e0b6e7689b9ebaee106fec39d2dc13863b834647/packages/awai-inference/android/network/awai/inference/InferenceClientPlugin.kt)

  commit `e0b6e7689b9ebaee106fec39d2dc13863b834647`; SHA256 `1bf17b3988b9f1e20a4f580e45cf66250881298a72de0e1b359fb06aa44f24df`

- **E43** [network-awai/awai-os/apps/murakumo/native/image/image.cpp](https://github.com/network-awai/awai-os/blob/e0b6e7689b9ebaee106fec39d2dc13863b834647/apps/murakumo/native/image/image.cpp)

  commit `e0b6e7689b9ebaee106fec39d2dc13863b834647`; SHA256 `96f5b9fe7e7bbfabce5e4d4a76a04e8425160412705b0cb6a73b4480259019b7`

- **E44** [network-awai/awai-os/apps/murakumo/src-tauri/gen/android/app/src/main/java/network/awai/murakumo/InferenceRequestActivity.kt](https://github.com/network-awai/awai-os/blob/e0b6e7689b9ebaee106fec39d2dc13863b834647/apps/murakumo/src-tauri/gen/android/app/src/main/java/network/awai/murakumo/InferenceRequestActivity.kt)

  commit `e0b6e7689b9ebaee106fec39d2dc13863b834647`; SHA256 `bbc8f41f7fb17d412bb232307fe3933604220b21634077aa7330a81654e690d7`


- **E45** [network-awai/awai-os/android/desktop/pc-ab-device/home-default-20261125.md](https://github.com/network-awai/awai-os/blob/832599c9f640d9743075b6bcff6dc788afd9e3be/android/desktop/pc-ab-device/home-default-20261125.md)

  commit `832599c9f640d9743075b6bcff6dc788afd9e3be`; SHA256 `22c8fcb5c6dcec8317bfb1191bf08850c7f70bd1f1270014943fd48affe72e80`

- **E46** [network-awai/awai-os/android/desktop/pc-ab-device/patches/virtio-common-awai-apps.patch](https://github.com/network-awai/awai-os/blob/832599c9f640d9743075b6bcff6dc788afd9e3be/android/desktop/pc-ab-device/patches/virtio-common-awai-apps.patch)

  commit `832599c9f640d9743075b6bcff6dc788afd9e3be`; SHA256 `e3065f0f09d25e93a7e4346be47f4ed4df29b3a60765432b4c0b5e14456cd071`

- **E47** [network-awai/awai-os/android/desktop/pc-ab-device/patches/launcher3-home-priority-fallback.patch](https://github.com/network-awai/awai-os/blob/832599c9f640d9743075b6bcff6dc788afd9e3be/android/desktop/pc-ab-device/patches/launcher3-home-priority-fallback.patch)

  commit `832599c9f640d9743075b6bcff6dc788afd9e3be`; SHA256 `855a9d85f2b1b561758ceddfd243c46b39d42d20c4c582cfcda8dfed4d4e1b9e`
